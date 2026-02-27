package com.blissless.anime.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class AniwatchStreamResult(
    val url: String,
    val isDirectStream: Boolean,
    val headers: Map<String, String>?,
    val subtitleUrl: String?
)

object AniwatchService {
    private const val API_BASE = "http://aniwatch-cxjn.vercel.app/api/v2/hianime"
    private val client = OkHttpClient()

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

    suspend fun getStreamLink(animeName: String, episodeNumber: Int): AniwatchStreamResult? = withContext(Dispatchers.IO) {
        retry {
            // 1. SEARCH
            val encodedName = URLEncoder.encode(animeName, "UTF-8")
            val searchRequest = Request.Builder().url("$API_BASE/search?q=$encodedName").build()
            val searchResponse = client.newCall(searchRequest).execute()
            val searchData = JSONObject(searchResponse.body.string())

            if (searchData.getInt("status") != 200) return@retry null
            val animes = searchData.getJSONObject("data").getJSONArray("animes")
            if (animes.length() == 0) return@retry null

            var animeId: String? = null
            for (i in 0 until animes.length()) {
                val anime = animes.getJSONObject(i)
                if (anime.getString("name").equals(animeName, ignoreCase = true)) {
                    animeId = anime.getString("id")
                    break
                }
            }
            if (animeId == null) animeId = animes.getJSONObject(0).getString("id")

            // 2. GET EPISODES
            val epRequest = Request.Builder().url("$API_BASE/anime/$animeId/episodes").build()
            val epResponse = client.newCall(epRequest).execute()
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
            if (episodeId == null) return@retry null

            // 3. GET SOURCES
            val servers = listOf("hd-1", "megacloud", "hd-2")
            for (pass in 1..2) {
                for (server in servers) {
                    val watchUrl = "$API_BASE/episode/sources?animeEpisodeId=$episodeId&server=$server&category=sub"
                    val watchRequest = Request.Builder().url(watchUrl).build()
                    val watchRes = client.newCall(watchRequest).execute()
                    val watchData = JSONObject(watchRes.body.string())

                    if (watchRes.isSuccessful && watchData.getInt("status") == 200) {
                        val data = watchData.getJSONObject("data")
                        val sources = data.getJSONArray("sources")
                        if (sources.length() > 0) {
                            val streamUrl = sources.getJSONObject(0).getString("url")

                            // Subtitles
                            var englishSub: String? = null
                            val tracks = data.optJSONArray("tracks")
                            if (tracks != null) {
                                for (j in 0 until tracks.length()) {
                                    val track = tracks.getJSONObject(j)
                                    if (track.optString("lang") == "English") {
                                        englishSub = track.getString("url")
                                        break
                                    }
                                }
                            }

                            val headers = mutableMapOf(
                                "User-Agent" to "Mozilla/5.0...",
                                "Referer" to "https://megacloud.tv/",
                                "Origin" to "https://megacloud.tv/"
                            )

                            data.optJSONObject("headers")?.let { apiHeaders ->
                                apiHeaders.keys().forEach { key ->
                                    headers[key] = apiHeaders.get(key).toString()
                                }
                            }

                            return@retry AniwatchStreamResult(
                                url = streamUrl,
                                isDirectStream = true,
                                headers = headers,
                                subtitleUrl = englishSub
                            )
                        }
                    }
                }
                if (pass == 1) delay(1000)
            }
            null
        }
    }
}