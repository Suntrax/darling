package com.blissless.anime.stream.scrapers.zenime

import com.blissless.anime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import com.blissless.anime.data.models.AniwatchStreamResult
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.ServerInfo

/**
 * Data class representing the result of a stream fetch operation from Zenime.
 */
data class ZenimeStreamResult(
    val url: String,
    val isDirectStream: Boolean = true,
    val headers: Map<String, String>?,
    val subtitleUrl: String?,
    val serverName: String = "",
    val category: String = "sub"
)

/**
 * Data class representing server information from Zenime.
 */
data class ZenimeServerInfo(
    val name: String,
    val url: String = "",
    val type: String = "sub"
)

/**
 * Data class representing episode streams from Zenime.
 */
data class ZenimeEpisodeStreams(
    val subServers: List<ZenimeServerInfo>,
    val dubServers: List<ZenimeServerInfo>,
    val animeId: String,
    val episodeId: String
)

/**
 * Zenime scraper service for fetching anime streams.
 */
object ZenimeScraper {
    private const val TAG = "ZenimeScraper"
    private const val API_BASE = BuildConfig.ZENIME_API_BASE_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Retry helper function for network operations.
     */
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
     * Search for anime by name and return matching results.
     */
    suspend fun searchAnime(animeName: String): List<Pair<String, String>>? = withContext(Dispatchers.IO) {
        retry {
            val encodedName = URLEncoder.encode(animeName, "UTF-8")
            val searchUrl = "$API_BASE/search?keyword=$encodedName"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val searchResponse = client.newCall(searchRequest).execute()

            if (!searchResponse.isSuccessful) {
                return@retry null
            }

            val searchData = JSONObject(searchResponse.body.string())
            val results = searchData.getJSONObject("results")
            val dataArray = results.optJSONArray("data")

            if (dataArray == null || dataArray.length() == 0) {
                return@retry null
            }

            val animeList = mutableListOf<Pair<String, String>>()
            for (i in 0 until dataArray.length()) {
                val anime = dataArray.getJSONObject(i)
                val id = anime.getString("id")
                val title = anime.getString("title")
                animeList.add(Pair(id, title))
            }

            animeList
        }
    }

    /**
     * Get episode list for an anime.
     */
    suspend fun getEpisodes(animeId: String): List<Pair<String, Int>>? = withContext(Dispatchers.IO) {
        retry {
            val episodesUrl = "$API_BASE/episodes/$animeId"

            val request = Request.Builder()
                .url(episodesUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@retry null
            }

            val data = JSONObject(response.body.string())
            val results = data.getJSONObject("results")
            val episodesArray = results.optJSONArray("episodes")

            if (episodesArray == null || episodesArray.length() == 0) {
                return@retry null
            }

            val episodes = mutableListOf<Pair<String, Int>>()
            for (i in 0 until episodesArray.length()) {
                val ep = episodesArray.getJSONObject(i)
                val epId = ep.getString("id")
                val epNum = ep.optInt("number", i + 1)
                episodes.add(Pair(epId, epNum))
            }

            episodes
        }
    }

    /**
     * Get available servers for an episode (both sub and dub).
     */
    suspend fun getServers(episodeId: String): ZenimeEpisodeStreams? = withContext(Dispatchers.IO) {
        retry {
            val serversUrl = "$API_BASE/servers/$episodeId"

            val request = Request.Builder()
                .url(serversUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@retry null
            }

            val data = JSONObject(response.body.string())
            val resultsArray = data.optJSONArray("results")

            if (resultsArray == null || resultsArray.length() == 0) {
                return@retry null
            }

            val subServers = mutableListOf<ZenimeServerInfo>()
            val dubServers = mutableListOf<ZenimeServerInfo>()

            for (i in 0 until resultsArray.length()) {
                val server = resultsArray.getJSONObject(i)
                val serverName = server.getString("serverName")
                val type = server.optString("type", "sub")

                val serverInfo = ZenimeServerInfo(
                    name = serverName,
                    type = type
                )

                if (type == "sub") {
                    subServers.add(serverInfo)
                } else if (type == "dub") {
                    dubServers.add(serverInfo)
                }
            }


            ZenimeEpisodeStreams(
                subServers = subServers,
                dubServers = dubServers,
                animeId = "",
                episodeId = episodeId
            )
        }
    }

    /**
     * Get stream URL for a specific server and type.
     */
    suspend fun getStream(
        episodeId: String,
        serverName: String,
        type: String = "sub"
    ): ZenimeStreamResult? = withContext(Dispatchers.IO) {
        retry {
            val streamUrl = "$API_BASE/stream?id=$episodeId&server=$serverName&type=$type"

            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@retry null
            }

            val data = JSONObject(response.body.string())
            val results = data.optJSONObject("results")

            if (results == null) {
                return@retry null
            }

            val streamingLink = results.optJSONObject("streamingLink")

            if (streamingLink == null) {
                return@retry null
            }

            val linkObj = streamingLink.optJSONObject("link")
            val streamUrlStr = linkObj?.optString("file")

            if (streamUrlStr.isNullOrEmpty()) {
                return@retry null
            }


            // Find English subtitle track
            var englishSubtitle: String? = null
            val tracksArray = streamingLink.optJSONArray("tracks")

            if (tracksArray != null) {
                for (i in 0 until tracksArray.length()) {
                    val track = tracksArray.getJSONObject(i)
                    val kind = track.optString("kind", "")
                    val label = track.optString("label", "")
                    val file = track.optString("file", "")

                    // Look for English captions/subtitles
                    if (kind == "captions" && (label == "English" || label.contains("English", ignoreCase = true))) {
                        if (file.endsWith(".vtt") || file.endsWith(".srt")) {
                            englishSubtitle = file
                            break
                        }
                    }
                }

                // Fallback: if no English found, try first caption track
                if (englishSubtitle == null) {
                    for (i in 0 until tracksArray.length()) {
                        val track = tracksArray.getJSONObject(i)
                        val kind = track.optString("kind", "")
                        val file = track.optString("file", "")

                        if (kind == "captions" && (file.endsWith(".vtt") || file.endsWith(".srt"))) {
                            englishSubtitle = file
                            break
                        }
                    }
                }
            }

            // Headers for the stream - use correct Referer based on server
            val headers = getHeadersForServer(serverName)


            ZenimeStreamResult(
                url = streamUrlStr,
                isDirectStream = true,
                headers = headers,
                subtitleUrl = englishSubtitle,
                serverName = serverName,
                category = type
            )
        }
    }

    /**
     * Get episode info including servers for pre-fetching.
     * Main entry point for the app's episode info flow.
     */
    suspend fun getEpisodeInfo(animeName: String, episodeNumber: Int): ZenimeEpisodeStreams? = withContext(Dispatchers.IO) {
        retry {
            // 1. Search for anime
            val searchResults = searchAnime(animeName)
            if (searchResults.isNullOrEmpty()) {
                return@retry null
            }

            // Find exact match or use first result
            var animeId: String? = null
            for ((id, title) in searchResults) {
                if (title.equals(animeName, ignoreCase = true)) {
                    animeId = id
                    break
                }
            }

            if (animeId == null) {
                animeId = searchResults[0].first
            }

            // 2. Get episodes
            val episodes = getEpisodes(animeId)
            if (episodes.isNullOrEmpty()) {
                return@retry null
            }

            // Find the specific episode
            var episodeId: String? = null
            for ((epId, epNum) in episodes) {
                if (epNum == episodeNumber) {
                    episodeId = epId
                    break
                }
            }

            // If episode number not found, try by index
            if (episodeId == null && episodeNumber <= episodes.size) {
                episodeId = episodes[episodeNumber - 1].first
            }

            if (episodeId == null) {
                return@retry null
            }

            // 3. Get servers
            val servers = getServers(episodeId)
            if (servers != null) {
                servers.copy(animeId = animeId)
            } else {
                null
            }
        }
    }

    /**
     * Get stream with fallback logic.
     * Tries preferred category first, then falls back to the other.
     */
    suspend fun getStreamWithFallback(
        animeName: String,
        episodeNumber: Int,
        preferredCategory: String = "sub",
        serverName: String? = null
    ): ZenimeStreamResult? = withContext(Dispatchers.IO) {
        // Get episode info
        val episodeInfo = getEpisodeInfo(animeName, episodeNumber)
        if (episodeInfo == null) {
            return@withContext null
        }

        // Determine which servers to try based on preference
        val preferredServers = if (preferredCategory == "dub") {
            episodeInfo.dubServers
        } else {
            episodeInfo.subServers
        }

        val fallbackServers = if (preferredCategory == "dub") {
            episodeInfo.subServers
        } else {
            episodeInfo.dubServers
        }

        val fallbackCategory = if (preferredCategory == "dub") "sub" else "dub"

        // Try preferred servers first
        if (preferredServers.isNotEmpty()) {
            val targetServer = if (serverName != null) {
                preferredServers.find { it.name.equals(serverName, ignoreCase = true) } ?: preferredServers[0]
            } else {
                preferredServers[0]
            }

            val stream = getStream(episodeInfo.episodeId, targetServer.name, preferredCategory)
            if (stream != null) {
                return@withContext stream
            }
        }

        // Fall back to other category
        if (fallbackServers.isNotEmpty()) {
            val fallbackServer = fallbackServers[0]
            val stream = getStream(episodeInfo.episodeId, fallbackServer.name, fallbackCategory)
            if (stream != null) {
                return@withContext stream
            }
        }

        null
    }

    /**
     * Get stream for a specific server.
     * Used when user manually selects a server.
     */
    suspend fun getStreamForServer(
        animeName: String,
        episodeNumber: Int,
        serverName: String,
        category: String
    ): ZenimeStreamResult? = withContext(Dispatchers.IO) {
        val episodeInfo = getEpisodeInfo(animeName, episodeNumber)
        if (episodeInfo == null) {
            return@withContext null
        }

        getStream(episodeInfo.episodeId, serverName, category)
    }

    /**
     * Get appropriate headers for a streaming server.
     * Different servers require different Referer headers to allow playback.
     */
    private fun getHeadersForServer(serverName: String): Map<String, String> {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        val referer = when (serverName.lowercase()) {
            "vidsrc" -> "https://vidsrc.cc/"
            "megacloud", "mega cloud" -> "https://megacloud.tv/"
            "t-cloud", "tcloud" -> "https://2embed.org/"
            else -> "https://vidsrc.cc/" // Default to VidSrc referer as it's most common
        }

        return mapOf(
            "User-Agent" to userAgent,
            "Referer" to referer,
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Origin" to referer.trimEnd('/'),
            "Connection" to "keep-alive"
        )
    }

    /**
     * Convert ZenimeStreamResult to AniwatchStreamResult for compatibility.
     */
    fun toAniwatchStreamResult(zenimeResult: ZenimeStreamResult): AniwatchStreamResult {
        return AniwatchStreamResult(
            url = zenimeResult.url,
            isDirectStream = zenimeResult.isDirectStream,
            headers = zenimeResult.headers,
            subtitleUrl = zenimeResult.subtitleUrl,
            serverName = zenimeResult.serverName,
            category = zenimeResult.category
        )
    }

    /**
     * Convert ZenimeEpisodeStreams to EpisodeStreams for compatibility.
     */
    fun toEpisodeStreams(zenimeStreams: ZenimeEpisodeStreams): EpisodeStreams {
        return EpisodeStreams(
            subServers = zenimeStreams.subServers.map { ServerInfo(it.name, it.url) },
            dubServers = zenimeStreams.dubServers.map { ServerInfo(it.name, it.url) },
            animeId = zenimeStreams.animeId,
            episodeId = zenimeStreams.episodeId
        )
    }
}
