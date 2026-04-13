package com.blissless.anime.stream.scrapers.aniwatch

import com.blissless.anime.BuildConfig
import com.blissless.anime.data.models.AniwatchStreamResult
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.QualityOption
import com.blissless.anime.data.models.ServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object AniwatchService {
    private const val TAG = "AniwatchService"
    private const val API_BASE = BuildConfig.ANIWATCH_API_BASE_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun <T> retry(retries: Int = 3, block: suspend () -> T?): T? {
        var attempt = 0
        while (attempt < retries) {
            try {
                return block()
            } catch (e: Exception) {
                attempt++
                if (attempt >= retries) return null
                delay(500L * attempt)
            }
        }
        return null
    }

    /**
     * Parse M3U8 master playlist to extract all quality options.
     */
    private fun parseM3U8Qualities(masterUrl: String, masterContent: String): List<QualityOption> {
        val baseUrl = masterUrl.substringBeforeLast("/") + "/"
        val lines = masterContent.split("\n")
        val qualities = mutableListOf<QualityOption>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.contains("RESOLUTION=")) {
                val resolutionMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
                if (resolutionMatch != null && i + 1 < lines.size) {
                    val width = resolutionMatch.groupValues[1].toIntOrNull() ?: 0
                    val height = resolutionMatch.groupValues[2].toIntOrNull() ?: 0
                    val qualityName = "${height}p"
                    val streamUrl = if (lines[i + 1].trim().startsWith("http")) {
                        lines[i + 1].trim()
                    } else {
                        baseUrl + lines[i + 1].trim()
                    }

                    qualities.add(
                        QualityOption(
                            quality = qualityName,
                            url = streamUrl,
                            width = width
                        )
                    )
                }
            }
        }

        // Sort by quality (highest first)
        qualities.sortByDescending { it.width }

        return qualities
    }

    /**
     * Fetch master playlist and extract all qualities.
     */
    private suspend fun fetchAllQualities(masterUrl: String): List<QualityOption> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(masterUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                    .build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }

                val content = response.body.string()
                parseM3U8Qualities(masterUrl, content)
            } catch (e: Exception) {
                emptyList()
            }
        }

    // Get anime ID and episode ID for pre-fetching
    suspend fun getEpisodeInfo(animeName: String, episodeNumber: Int): EpisodeStreams? =
        withContext(Dispatchers.IO) {
            retry {
                // 1. SEARCH
                val encodedName = URLEncoder.encode(animeName, "UTF-8")
                val searchUrl = "$API_BASE/search?q=$encodedName"

                val searchRequest = Request.Builder()
                    .url(searchUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                    .build()
                val searchResponse = client.newCall(searchRequest).execute()

                if (!searchResponse.isSuccessful) {
                    return@retry null
                }

                val searchData = JSONObject(searchResponse.body.string())

                if (searchData.getInt("status") != 200) return@retry null
                val animes = searchData.getJSONObject("data").getJSONArray("animes")
                if (animes.length() == 0) {
                    return@retry null
                }

                var animeId: String? = null
                for (i in 0 until animes.length()) {
                    val anime = animes.getJSONObject(i)
                    val name = anime.getString("name")
                    if (name.equals(animeName, ignoreCase = true)) {
                        animeId = anime.getString("id")
                        break
                    }
                }
                if (animeId == null) {
                    animeId = animes.getJSONObject(0).getString("id")
                }

                // 2. GET EPISODES
                val epUrl = "$API_BASE/anime/$animeId/episodes"

                val epRequest = Request.Builder()
                    .url(epUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                    .build()
                val epResponse = client.newCall(epRequest).execute()

                if (!epResponse.isSuccessful) {
                    return@retry null
                }

                val epData = JSONObject(epResponse.body.string())
                val episodes = epData.getJSONObject("data").getJSONArray("episodes")

                var episodeId: String? = null
                for (i in 0 until episodes.length()) {
                    val ep = episodes.getJSONObject(i)
                    val epNum = ep.get("number").toString()
                    if (epNum == episodeNumber.toString()) {
                        episodeId = ep.getString("episodeId")
                        break
                    }
                }
                if (episodeId == null) {
                    return@retry null
                }

                // 3. GET SERVERS - single call returns both sub and dub
                val subServers = mutableListOf<ServerInfo>()
                val dubServers = mutableListOf<ServerInfo>()

                // Fetch servers (no category param - returns both)
                val serversUrl = "$API_BASE/episode/servers?animeEpisodeId=$episodeId"

                val serversRequest = Request.Builder()
                    .url(serversUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                    .build()
                val serversResponse = client.newCall(serversRequest).execute()

                if (serversResponse.isSuccessful) {
                    val serversData = JSONObject(serversResponse.body.string())

                    if (serversData.getInt("status") == 200) {
                        val dataObj = serversData.getJSONObject("data")

                        // Parse sub servers - uses "serverName" field
                        val subArray = dataObj.optJSONArray("sub")
                        if (subArray != null) {
                            for (i in 0 until subArray.length()) {
                                val server = subArray.getJSONObject(i)
                                val serverName = server.getString("serverName")
                                subServers.add(
                                    ServerInfo(
                                        name = serverName,
                                        url = "" // URL not provided in servers list
                                    )
                                )
                            }
                        }

                        // Parse dub servers - uses "serverName" field
                        val dubArray = dataObj.optJSONArray("dub")
                        if (dubArray != null) {
                            for (i in 0 until dubArray.length()) {
                                val server = dubArray.getJSONObject(i)
                                val serverName = server.getString("serverName")
                                dubServers.add(
                                    ServerInfo(
                                        name = serverName,
                                        url = ""
                                    )
                                )
                            }
                        }

                    }
                }

                EpisodeStreams(
                    subServers = subServers,
                    dubServers = dubServers,
                    animeId = animeId,
                    episodeId = episodeId
                )
            }
        }

    // Get stream from specific server
    suspend fun getStreamFromServer(
        animeName: String,
        episodeNumber: Int,
        serverName: String? = null,
        category: String = "sub"
    ): AniwatchStreamResult? = withContext(Dispatchers.IO) {
        retry {
            // 1. SEARCH
            val encodedName = URLEncoder.encode(animeName, "UTF-8")
            val searchUrl = "$API_BASE/search?q=$encodedName"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                .build()
            val searchResponse = client.newCall(searchRequest).execute()

            if (!searchResponse.isSuccessful) {
                return@retry null
            }

            val searchData = JSONObject(searchResponse.body.string())

            if (searchData.getInt("status") != 200) {
                return@retry null
            }

            val animes = searchData.getJSONObject("data").getJSONArray("animes")
            if (animes.length() == 0) {
                return@retry null
            }

            var animeId: String? = null
            for (i in 0 until animes.length()) {
                val anime = animes.getJSONObject(i)
                if (anime.getString("name").equals(animeName, ignoreCase = true)) {
                    animeId = anime.getString("id")
                    break
                }
            }
            if (animeId == null) {
                animeId = animes.getJSONObject(0).getString("id")
            }

            // 2. GET EPISODES
            val epUrl = "$API_BASE/anime/$animeId/episodes"

            val epRequest = Request.Builder()
                .url(epUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                .build()
            val epResponse = client.newCall(epRequest).execute()

            if (!epResponse.isSuccessful) {
                return@retry null
            }

            val epData = JSONObject(epResponse.body.string())
            val episodes = epData.getJSONObject("data").getJSONArray("episodes")

            var episodeId: String? = null
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                if (ep.get("number").toString() == episodeNumber.toString()) {
                    episodeId = ep.getString("episodeId")
                    break
                }
            }
            if (episodeId == null) {
                return@retry null
            }

            // 3. GET SERVERS - fetch both sub and dub
            val serversUrl = "$API_BASE/episode/servers?animeEpisodeId=$episodeId"

            val serversRequest = Request.Builder()
                .url(serversUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                .build()
            val serversResponse = client.newCall(serversRequest).execute()

            if (!serversResponse.isSuccessful) {
                return@retry null
            }

            val serversData = JSONObject(serversResponse.body.string())

            if (serversData.getInt("status") != 200) {
                return@retry null
            }

            val dataObj = serversData.getJSONObject("data")

            // Get servers based on category
            val serversArray = if (category == "dub") {
                dataObj.optJSONArray("dub")
            } else {
                dataObj.optJSONArray("sub")
            }

            if (serversArray == null || serversArray.length() == 0) {
                return@retry null
            }


            // Find the server or use first available
            var targetServerName = ""

            if (serverName != null) {
                for (i in 0 until serversArray.length()) {
                    val server = serversArray.getJSONObject(i)
                    val sName = server.getString("serverName")
                    if (sName.equals(serverName, ignoreCase = true)) {
                        targetServerName = sName
                        break
                    }
                }
            }

            if (targetServerName.isEmpty() && serversArray.length() > 0) {
                targetServerName = serversArray.getJSONObject(0).getString("serverName")
            }

            if (targetServerName.isEmpty()) {
                return@retry null
            }

            // 4. GET SOURCES from the server
            val sourceUrl =
                "$API_BASE/episode/sources?animeEpisodeId=$episodeId&server=$targetServerName&category=$category"

            val sourceRequest = Request.Builder()
                .url(sourceUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                .build()
            val sourceResponse = client.newCall(sourceRequest).execute()

            if (!sourceResponse.isSuccessful) {
                return@retry null
            }

            val sourceBody = sourceResponse.body.string()

            val sourceData = JSONObject(sourceBody)

            if (sourceData.getInt("status") != 200) {
                return@retry null
            }

            val data = sourceData.getJSONObject("data")
            val sources = data.optJSONArray("sources")

            if (sources == null || sources.length() == 0) {
                return@retry null
            }

            // Get the first source (m3u8 URL)
            val streamUrl = sources.getJSONObject(0).getString("url")

            // Fetch all quality options from master playlist
            val qualities = if (streamUrl.contains(".m3u8")) {
                fetchAllQualities(streamUrl)
            } else {
                emptyList()
            }


            // Subtitles - find English track
            var englishSub: String? = null
            val tracks = data.optJSONArray("tracks")
            if (tracks != null) {
                for (j in 0 until tracks.length()) {
                    val track = tracks.getJSONObject(j)
                    val lang = track.optString("lang", "")
                    if (lang == "English") {
                        englishSub = track.getString("url")
                        break
                    }
                }
            }

            // Get headers from response, especially Referer
            val headers = mutableMapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )

            data.optJSONObject("headers")?.let { apiHeaders ->
                apiHeaders.keys().forEach { key ->
                    headers[key] = apiHeaders.get(key).toString()
                }
            }

            // Ensure Referer is set
            if (!headers.containsKey("Referer")) {
                headers["Referer"] = "https://megacloud.tv/"
            }

            AniwatchStreamResult(
                url = streamUrl,
                isDirectStream = true,
                headers = headers,
                subtitleUrl = englishSub,
                serverName = targetServerName,
                category = category,
                qualities = qualities
            )
        }
    }
}