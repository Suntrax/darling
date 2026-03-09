package com.blissless.anime.api
import com.blissless.anime.BuildConfig

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class AniwatchStreamResult(
    val url: String,
    val isDirectStream: Boolean,
    val headers: Map<String, String>?,
    val subtitleUrl: String?,
    val serverName: String = "",
    val category: String = "sub"
)

data class ServerInfo(
    val name: String,
    val url: String
)

data class EpisodeStreams(
    val subServers: List<ServerInfo>,
    val dubServers: List<ServerInfo>,
    val animeId: String,
    val episodeId: String
)

// Result for anime search with episode count
data class AniwatchAnimeInfo(
    val id: String,
    val name: String,
    val totalEpisodes: Int
)

object AniwatchService {
    private const val TAG = "AniwatchService"
    private const val API_BASE = BuildConfig.API_BASE_URL

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
                Log.w(TAG, "Retry attempt ${attempt + 1} failed", e)
                attempt++
                if (attempt >= retries) return null
                delay(500L * attempt)
            }
        }
        return null
    }

    // NEW: Search for anime and return info with episode count
    suspend fun searchAnimeInfo(animeName: String): AniwatchAnimeInfo? = withContext(Dispatchers.IO) {
        retry {
            val encodedName = URLEncoder.encode(animeName, "UTF-8")
            val searchUrl = "$API_BASE/search?q=$encodedName"
            Log.d(TAG, "Searching for anime info: $searchUrl")

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val searchResponse = client.newCall(searchRequest).execute()

            if (!searchResponse.isSuccessful) {
                Log.e(TAG, "Search failed: ${searchResponse.code}")
                return@retry null
            }

            val searchData = JSONObject(searchResponse.body.string())

            if (searchData.getInt("status") != 200) return@retry null
            val animes = searchData.getJSONObject("data").getJSONArray("animes")
            if (animes.length() == 0) return@retry null

            // Find best match
            var bestMatch: JSONObject? = null
            for (i in 0 until animes.length()) {
                val anime = animes.getJSONObject(i)
                val name = anime.getString("name")
                if (name.equals(animeName, ignoreCase = true)) {
                    bestMatch = anime
                    break
                }
            }
            if (bestMatch == null) {
                bestMatch = animes.getJSONObject(0)
            }

            val animeId = bestMatch.getString("id")
            val name = bestMatch.getString("name")

            // Get episode count
            val epUrl = "$API_BASE/anime/$animeId/episodes"
            Log.d(TAG, "Fetching episode count: $epUrl")

            val epRequest = Request.Builder()
                .url(epUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val epResponse = client.newCall(epRequest).execute()

            var totalEpisodes = 0
            if (epResponse.isSuccessful) {
                val epData = JSONObject(epResponse.body.string())
                val episodes = epData.getJSONObject("data").getJSONArray("episodes")
                totalEpisodes = episodes.length()
            }

            Log.d(TAG, "Found anime: $name with $totalEpisodes episodes")
            AniwatchAnimeInfo(id = animeId, name = name, totalEpisodes = totalEpisodes)
        }
    }

    // Get anime ID and episode ID for pre-fetching
    suspend fun getEpisodeInfo(animeName: String, episodeNumber: Int): EpisodeStreams? = withContext(Dispatchers.IO) {
        retry {
            // 1. SEARCH
            val encodedName = URLEncoder.encode(animeName, "UTF-8")
            val searchUrl = "$API_BASE/search?q=$encodedName"
            Log.d(TAG, "Searching: $searchUrl")

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val searchResponse = client.newCall(searchRequest).execute()

            if (!searchResponse.isSuccessful) {
                Log.e(TAG, "Search failed: ${searchResponse.code}")
                return@retry null
            }

            val searchData = JSONObject(searchResponse.body.string())
            Log.d(TAG, "Search response status: ${searchData.optInt("status", -1)}")

            if (searchData.getInt("status") != 200) return@retry null
            val animes = searchData.getJSONObject("data").getJSONArray("animes")
            if (animes.length() == 0) {
                Log.e(TAG, "No anime found for: $animeName")
                return@retry null
            }

            var animeId: String? = null
            for (i in 0 until animes.length()) {
                val anime = animes.getJSONObject(i)
                val name = anime.getString("name")
                Log.d(TAG, "Found anime: $name (id: ${anime.getString("id")})")
                if (name.equals(animeName, ignoreCase = true)) {
                    animeId = anime.getString("id")
                    break
                }
            }
            if (animeId == null) {
                animeId = animes.getJSONObject(0).getString("id")
                Log.d(TAG, "Using first result: $animeId")
            }

            // 2. GET EPISODES
            val epUrl = "$API_BASE/anime/$animeId/episodes"
            Log.d(TAG, "Fetching episodes: $epUrl")

            val epRequest = Request.Builder()
                .url(epUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val epResponse = client.newCall(epRequest).execute()

            if (!epResponse.isSuccessful) {
                Log.e(TAG, "Episodes fetch failed: ${epResponse.code}")
                return@retry null
            }

            val epData = JSONObject(epResponse.body.string())
            val episodes = epData.getJSONObject("data").getJSONArray("episodes")
            Log.d(TAG, "Found ${episodes.length()} episodes")

            var episodeId: String? = null
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                val epNum = ep.get("number").toString()
                if (epNum == episodeNumber.toString()) {
                    episodeId = ep.getString("episodeId")
                    Log.d(TAG, "Found episode $episodeNumber with ID: $episodeId")
                    break
                }
            }
            if (episodeId == null) {
                Log.e(TAG, "Episode $episodeNumber not found")
                return@retry null
            }

            // 3. GET SERVERS - single call returns both sub and dub
            val subServers = mutableListOf<ServerInfo>()
            val dubServers = mutableListOf<ServerInfo>()

            // Fetch servers (no category param - returns both)
            val serversUrl = "$API_BASE/episode/servers?animeEpisodeId=$episodeId"
            Log.d(TAG, "Fetching servers: $serversUrl")

            val serversRequest = Request.Builder()
                .url(serversUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val serversResponse = client.newCall(serversRequest).execute()

            if (serversResponse.isSuccessful) {
                val serversData = JSONObject(serversResponse.body.string())
                Log.d(TAG, "Servers response: ${serversData.toString().take(500)}")

                if (serversData.getInt("status") == 200) {
                    val dataObj = serversData.getJSONObject("data")

                    // Parse sub servers - uses "serverName" field
                    val subArray = dataObj.optJSONArray("sub")
                    if (subArray != null) {
                        for (i in 0 until subArray.length()) {
                            val server = subArray.getJSONObject(i)
                            val serverName = server.getString("serverName")
                            subServers.add(ServerInfo(
                                name = serverName,
                                url = "" // URL not provided in servers list
                            ))
                            Log.d(TAG, "Sub server: $serverName")
                        }
                    }

                    // Parse dub servers - uses "serverName" field
                    val dubArray = dataObj.optJSONArray("dub")
                    if (dubArray != null) {
                        for (i in 0 until dubArray.length()) {
                            val server = dubArray.getJSONObject(i)
                            val serverName = server.getString("serverName")
                            dubServers.add(ServerInfo(
                                name = serverName,
                                url = ""
                            ))
                            Log.d(TAG, "Dub server: $serverName")
                        }
                    }

                    Log.d(TAG, "Found ${subServers.size} sub servers, ${dubServers.size} dub servers")
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
            Log.d(TAG, "Searching for anime: $animeName")

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val searchResponse = client.newCall(searchRequest).execute()

            if (!searchResponse.isSuccessful) {
                Log.e(TAG, "Search request failed: ${searchResponse.code}")
                return@retry null
            }

            val searchData = JSONObject(searchResponse.body.string())

            if (searchData.getInt("status") != 200) {
                Log.e(TAG, "Search returned non-200 status")
                return@retry null
            }

            val animes = searchData.getJSONObject("data").getJSONArray("animes")
            if (animes.length() == 0) {
                Log.e(TAG, "No anime found in search results")
                return@retry null
            }

            var animeId: String? = null
            for (i in 0 until animes.length()) {
                val anime = animes.getJSONObject(i)
                if (anime.getString("name").equals(animeName, ignoreCase = true)) {
                    animeId = anime.getString("id")
                    Log.d(TAG, "Found matching anime with ID: $animeId")
                    break
                }
            }
            if (animeId == null) {
                animeId = animes.getJSONObject(0).getString("id")
                Log.d(TAG, "Using first anime result with ID: $animeId")
            }

            // 2. GET EPISODES
            val epUrl = "$API_BASE/anime/$animeId/episodes"
            Log.d(TAG, "Fetching episodes from: $epUrl")

            val epRequest = Request.Builder()
                .url(epUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val epResponse = client.newCall(epRequest).execute()

            if (!epResponse.isSuccessful) {
                Log.e(TAG, "Episodes request failed: ${epResponse.code}")
                return@retry null
            }

            val epData = JSONObject(epResponse.body.string())
            val episodes = epData.getJSONObject("data").getJSONArray("episodes")
            Log.d(TAG, "Found ${episodes.length()} episodes")

            var episodeId: String? = null
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                if (ep.get("number").toString() == episodeNumber.toString()) {
                    episodeId = ep.getString("episodeId")
                    Log.d(TAG, "Found episode $episodeNumber with ID: $episodeId")
                    break
                }
            }
            if (episodeId == null) {
                Log.e(TAG, "Episode $episodeNumber not found in episode list")
                return@retry null
            }

            // 3. GET SERVERS - fetch both sub and dub
            val serversUrl = "$API_BASE/episode/servers?animeEpisodeId=$episodeId"
            Log.d(TAG, "Fetching servers from: $serversUrl")

            val serversRequest = Request.Builder()
                .url(serversUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val serversResponse = client.newCall(serversRequest).execute()

            if (!serversResponse.isSuccessful) {
                Log.e(TAG, "Servers request failed: ${serversResponse.code}")
                return@retry null
            }

            val serversData = JSONObject(serversResponse.body.string())
            Log.d(TAG, "Servers response: ${serversData.toString().take(500)}")

            if (serversData.getInt("status") != 200) {
                Log.e(TAG, "Servers returned non-200 status")
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
                Log.e(TAG, "No $category servers available")
                return@retry null
            }

            Log.d(TAG, "Found ${serversArray.length()} $category servers")

            // Find the server or use first available
            var targetServerName = ""

            if (serverName != null) {
                for (i in 0 until serversArray.length()) {
                    val server = serversArray.getJSONObject(i)
                    val sName = server.getString("serverName")
                    if (sName.equals(serverName, ignoreCase = true)) {
                        targetServerName = sName
                        Log.d(TAG, "Found requested server: $targetServerName")
                        break
                    }
                }
            }

            if (targetServerName.isEmpty() && serversArray.length() > 0) {
                targetServerName = serversArray.getJSONObject(0).getString("serverName")
                Log.d(TAG, "Using first available server: $targetServerName")
            }

            if (targetServerName.isEmpty()) {
                Log.e(TAG, "No servers available")
                return@retry null
            }

            // 4. GET SOURCES from the server
            val sourceUrl = "$API_BASE/episode/sources?animeEpisodeId=$episodeId&server=$targetServerName&category=$category"
            Log.d(TAG, "Fetching sources from: $sourceUrl")

            val sourceRequest = Request.Builder()
                .url(sourceUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val sourceResponse = client.newCall(sourceRequest).execute()

            if (!sourceResponse.isSuccessful) {
                Log.e(TAG, "Sources request failed: ${sourceResponse.code}")
                return@retry null
            }

            val sourceBody = sourceResponse.body.string()
            Log.d(TAG, "Sources response: ${sourceBody.take(500)}...")

            val sourceData = JSONObject(sourceBody)

            if (sourceData.getInt("status") != 200) {
                Log.e(TAG, "Sources returned non-200 status: ${sourceData.optInt("status")}")
                return@retry null
            }

            val data = sourceData.getJSONObject("data")
            val sources = data.optJSONArray("sources")

            if (sources == null || sources.length() == 0) {
                Log.e(TAG, "No sources in response")
                return@retry null
            }

            // Get the first source (m3u8 URL)
            val streamUrl = sources.getJSONObject(0).getString("url")
            Log.d(TAG, "Stream URL: ${streamUrl.take(80)}...")

            // Subtitles - find English track
            var englishSub: String? = null
            val tracks = data.optJSONArray("tracks")
            if (tracks != null) {
                for (j in 0 until tracks.length()) {
                    val track = tracks.getJSONObject(j)
                    val lang = track.optString("lang", "")
                    if (lang == "English") {
                        englishSub = track.getString("url")
                        Log.d(TAG, "Found English subtitle: $englishSub")
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
                Log.d(TAG, "Got headers from response: ${headers.keys}")
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
                category = category
            )
        }
    }

    // Keep backward compatibility
    suspend fun getStreamLink(animeName: String, episodeNumber: Int): AniwatchStreamResult? {
        return getStreamFromServer(animeName, episodeNumber, null, "sub")
    }
}
