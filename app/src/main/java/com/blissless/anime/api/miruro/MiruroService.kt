package com.blissless.anime.api.miruro

import android.util.Log
import com.blissless.anime.BuildConfig
import com.blissless.anime.data.models.QualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

const val TIMEOUT_MS = 20000

data class MiruroEpisodeInfo(
    val id: String,
    val number: Int,
    val title: String?,
    val description: String?,
    val image: String?,
    val duration: Int?,
    val airDate: String?,
    val filler: Boolean,
    val fillerType: String?
)

data class MiruroAnimeEpisodes(
    val episodes: List<MiruroEpisodeInfo>,
    val totalEpisodes: Int
)

data class MiruroStreamResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val serverName: String = "animekai",
    val category: String = "sub",
    val qualities: List<QualityOption> = emptyList(),
    val introStart: Int? = null,
    val introEnd: Int? = null,
    val outroStart: Int? = null,
    val outroEnd: Int? = null,
    val downloadUrl: String? = null,
    val thumbnailUrl: String? = null,
    val subtitleUrl: String? = null
)

data class MiruroProviderInfo(
    val name: String,
    val category: String,
    val watchPath: String
)

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

object MiruroService {
    
    private val miruroBaseUrl: String
        get() = BuildConfig.MIRURO_SCRAPER_BASE_URL

    fun getBaseUrl(): String = miruroBaseUrl

    private val providersCache = mutableMapOf<Int, List<MiruroProviderInfo>>()
    private val episodesCache = mutableMapOf<Int, MiruroAnimeEpisodes?>()

suspend fun getAnimeEpisodes(anilistId: Int): MiruroAnimeEpisodes? = withContext(Dispatchers.IO) {
        episodesCache[anilistId]?.let { return@withContext it }
        
        try {
            if (miruroBaseUrl.isBlank()) {
                Log.e("MiruroService", "MIRURO_SCRAPER_BASE_URL not configured")
                return@withContext null
            }

            val url = URL("$miruroBaseUrl/episodes/$anilistId")
            Log.d("MiruroService", ">>> API ENDPOINT: $url")
            Log.d("MiruroService", ">>> GET EPISODES LIST")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("MiruroService", "Failed: HTTP $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val result = parseEpisodesResponse(response)
            episodesCache[anilistId] = result
            result
        } catch (e: Exception) {
            Log.e("MiruroService", "Error: ${e.message}")
            null
        }
    }

    private fun parseEpisodesResponse(response: String): MiruroAnimeEpisodes? {
        return try {
            val parsed = json.decodeFromString<MiruroApiResponse>(response)
            val firstProvider = parsed.providers?.arc ?: parsed.providers?.zoro ?: parsed.providers?.jet
            
            val episodes = firstProvider?.episodes?.sub?.map { ep ->
                MiruroEpisodeInfo(
                    id = ep.id,
                    number = ep.number.toInt(),
                    title = ep.title,
                    description = ep.description,
                    image = ep.image,
                    duration = ep.duration,
                    airDate = ep.airDate,
                    filler = ep.filler,
                    fillerType = ep.fillerType
                )
            } ?: emptyList()

            MiruroAnimeEpisodes(
                episodes = episodes,
                totalEpisodes = episodes.size
            )
        } catch (e: Exception) {
            Log.e("MiruroService", "Parse episodes error: ${e.message}")
            null
        }
    }

    suspend fun getStream(anilistId: Int, episode: Int, category: String = "sub"): MiruroStreamResult? {
        val watchPath = "$miruroBaseUrl/watch/arc/$anilistId/$category/animekai-$episode"
        Log.d("MiruroService", ">>> API ENDPOINT: $watchPath")
        Log.d("MiruroService", ">>> GET STREAM [arc] ep=$episode cat=$category")
        return getStreamFromPath(watchPath, category, "animekai")
    }

    suspend fun getStreamFromPath(watchPath: String, category: String, providerName: String? = null): MiruroStreamResult? = withContext(Dispatchers.IO) {
        try {
            if (miruroBaseUrl.isBlank()) {
                Log.e("MiruroService", "MIRURO_SCRAPER_BASE_URL not configured")
                return@withContext null
            }

            Log.d("MiruroService", ">>> API ENDPOINT: $watchPath")
            Log.d("MiruroService", ">>> GET STREAM")

            val url = URL(watchPath)
            Log.d("MiruroService", "Fetching stream: $url")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("MiruroService", "Failed: HTTP $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            Log.d("MiruroService", ">>> RESPONSE: ${response.take(200)}")

            parseStreamResponse(response, category, providerName)
        } catch (e: Exception) {
            Log.e("MiruroService", "Error: ${e.message}")
            null
        }
    }

    private fun parseStreamResponse(response: String, category: String, providerName: String? = null): MiruroStreamResult? {
        return try {
            // Check for error responses before parsing
            if (response.contains("\"detail\"") && (response.contains("Pipe request failed") || response.contains("Not Found") || response.contains("error"))) {
                Log.w("MiruroService", "Provider returned error: ${response.take(100)}")
                return null
            }
            
            Log.d("MiruroService", "Response: ${response.take(300)}")
            val parsed = json.decodeFromString<MiruroStreamApiResponse>(response)
            
            // Check both "streams" and "ssub" fields
            val streams = parsed.streams ?: parsed.ssub?.streams ?: emptyList()
            val hlsStream = streams.find { it.type == "hls" }
            val embedStream = streams.find { it.type == "embed" }
            
            val streamUrl = hlsStream?.url ?: embedStream?.url
            if (streamUrl.isNullOrBlank()) return null

            val referer = hlsStream?.referer ?: embedStream?.referer ?: ""
            val headers = if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()

            // Get default English subtitle from either streams or ssub
            val allSubtitles = parsed.subtitles ?: parsed.ssub?.subtitles
            val subtitleUrl = allSubtitles?.find { it.default == true || it.label?.contains("English", ignoreCase = true) == true }?.file

            MiruroStreamResult(
                url = streamUrl,
                headers = headers,
                serverName = providerName ?: "animekai",
                category = category,
                qualities = listOf(QualityOption(quality = "Auto", url = streamUrl, width = 0)),
                introStart = parsed.intro?.start,
                introEnd = parsed.intro?.end,
                outroStart = parsed.outro?.start,
                outroEnd = parsed.outro?.end,
                downloadUrl = parsed.download,
                thumbnailUrl = parsed.thumbnail,
                subtitleUrl = subtitleUrl
            )
        } catch (e: Exception) {
            Log.e("MiruroService", "Parse error: ${e.message}")
            null
        }
    }

    suspend fun getProvidersForEpisode(anilistId: Int, episode: Int): List<MiruroProviderInfo> = withContext(Dispatchers.IO) {
        try {
            if (miruroBaseUrl.isBlank()) return@withContext emptyList()

            providersCache[anilistId]?.let { return@withContext it }

            val url = URL("$miruroBaseUrl/episodes/$anilistId")
            Log.d("MiruroService", ">>> API ENDPOINT: $url")
            Log.d("MiruroService", ">>> GET PROVIDERS FOR EPISODE $episode")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext emptyList()
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val providers = parseProvidersResponse(response, episode, anilistId)
            if (providers.isNotEmpty()) {
                providersCache[anilistId] = providers
            }
            providers
        } catch (e: Exception) {
            Log.e("MiruroService", "Providers error: ${e.message}")
            emptyList()
        }
    }

    private fun parseProvidersResponse(response: String, episode: Int, anilistId: Int): List<MiruroProviderInfo> {
        return try {
            val parsed = json.decodeFromString<MiruroApiResponse>(response)
            val result = mutableListOf<MiruroProviderInfo>()

            val providers = parsed.providers
            if (providers == null) return emptyList()

            val providerList = listOf(
                "arc" to providers.arc,
                "jet" to providers.jet,
                "kiwi" to providers.kiwi,
                "zoro" to providers.zoro,
                "hop" to providers.hop,
                "dune" to providers.dune
            )

            providerList.forEach { (name, data) ->
                data?.episodes?.let { eps ->
                    val subList = findEpisodeInSections(eps.sub, eps.sections, episode, "sub")
                    val dubList = findEpisodeInSections(eps.dub, eps.sections, episode, "dub")
                    
                    subList?.let { ep ->
                        Log.d("MiruroService", ">>> FOUND EP $episode for ${name}_sub: id=${ep.id}, number=${ep.number}")
                        result.add(MiruroProviderInfo(
                            name = name,
                            category = "sub",
                            watchPath = "$miruroBaseUrl/${ep.id}"
                        ))
                    }
                    dubList?.let { ep ->
                        Log.d("MiruroService", ">>> FOUND EP $episode for ${name}_dub: id=${ep.id}, number=${ep.number}")
                        result.add(MiruroProviderInfo(
                            name = name,
                            category = "dub",
                            watchPath = "$miruroBaseUrl/${ep.id}"
                        ))
                    }
                }
            }

            result
        } catch (e: Exception) {
            Log.e("MiruroService", "Parse providers error: ${e.message}")
            emptyList()
        }
    }

    private fun findEpisodeInSections(
        episodes: List<MiruroEpisodeItem>,
        sections: List<MiruroEpisodeSection>?,
        targetEpisode: Int,
        searchCategory: String
    ): MiruroEpisodeItem? {
        if (episodes.isEmpty()) return null
        
        // If no sections, just find directly by episode number (matches 1-based)
        if (sections.isNullOrEmpty()) {
            return episodes.find { it.number.toInt() == targetEpisode }
        }
        
        // Find which section contains the target episode
        for (section in sections) {
            if (targetEpisode >= section.start && targetEpisode <= section.end) {
                val indexInSection = targetEpisode - section.start
                return episodes.getOrNull(indexInSection)
            }
        }
        
        // Fallback: try direct match
        return episodes.find { it.number.toInt() == targetEpisode }
    }
}

@Serializable
data class MiruroApiResponse(
    val mappings: MiruroMappings? = null,
    val providers: MiruroProviders? = null
)

@Serializable
data class MiruroMappings(
    val id: Int? = null,
    val title: String? = null,
    val type: String? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val malId: Int? = null,
    val aniId: Int? = null
)

@Serializable
data class MiruroProviders(
    val kiwi: MiruroProviderData? = null,
    val zoro: MiruroProviderData? = null,
    val hop: MiruroProviderData? = null,
    val arc: MiruroProviderData? = null,
    val dune: MiruroProviderData? = null,
    val jet: MiruroProviderData? = null,
    val crunchyroll: MiruroProviderData? = null
)

@Serializable
data class MiruroProviderData(
    val meta: MiruroMeta? = null,
    val episodes: MiruroEpisodesContainer? = null
)

@Serializable
data class MiruroMeta(
    val id: String? = null,
    val title: String? = null,
    val altTitle: String? = null,
    val japanese: String? = null,
    val description: String? = null,
    val image: String? = null
)

@Serializable
data class MiruroEpisodesContainer(
    val sub: List<MiruroEpisodeItem> = emptyList(),
    val dub: List<MiruroEpisodeItem> = emptyList(),
    val sections: List<MiruroEpisodeSection>? = null
)

@Serializable
data class MiruroEpisodeSection(
    val title: String? = null,
    val start: Int = 0,
    val end: Int = 0
)

@Serializable
data class MiruroEpisodeItem(
    val id: String = "",
    val number: Double = 0.0,
    val title: String? = null,
    val url: String? = null,
    val duration: Int? = null,
    val audio: String? = null,
    val description: String? = null,
    val filler: Boolean = false,
    val uncensored: Boolean = false,
    val image: String? = null,
    val airDate: String? = null,
    val fillerType: String? = null
)

@Serializable
data class MiruroStreamApiResponse(
    val streams: List<MiruroStreamData>? = null,
    val ssub: MiruroStreamContainer? = null,
    val intro: MiruroSkipTimestamp? = null,
    val outro: MiruroSkipTimestamp? = null,
    val download: String? = null,
    val thumbnail: String? = null,
    val subtitles: List<MiruroSubtitle>? = null
)

@Serializable
data class MiruroStreamContainer(
    val streams: List<MiruroStreamData>? = null,
    val subtitles: List<MiruroSubtitle>? = null
)

@Serializable
data class MiruroSkipTimestamp(
    val start: Int? = null,
    val end: Int? = null
)

@Serializable
data class MiruroSubtitle(
    val file: String? = null,
    val label: String? = null,
    val kind: String? = null,
    val default: Boolean? = null,
    val language: String? = null
)

@Serializable
data class MiruroStreamData(
    val url: String = "",
    val type: String = "hls",
    val referer: String = ""
)