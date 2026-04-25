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

    private val providersCache = mutableMapOf<String, List<MiruroProviderInfo>>()
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
            Log.d("MiruroService", ">>> GET STREAM category=$category provider=$providerName")

            val url = URL(watchPath)
            Log.d("MiruroService", "Fetching stream: $url")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            Log.d("MiruroService", ">>> HTTP RESPONSE: $responseCode")
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("MiruroService", ">>> HTTP FAILED: $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            Log.d("MiruroService", ">>> RESPONSE LEN: ${response.length}")

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
                Log.w("MiruroService", ">>> ERROR RESPONSE: ${response.take(150)}")
                return null
            }

            Log.d("MiruroService", ">>> PARSING STREAM response (${response.length} chars)")
            val parsed = json.decodeFromString<MiruroStreamApiResponse>(response)

            // Check both "streams" and "ssub" fields
            val streams = parsed.streams ?: parsed.ssub?.streams ?: emptyList()
            Log.d("MiruroService", ">>> STREAMS COUNT: ${streams.size}")

            // Find all HLS streams and sort by quality (best first)
            val hlsStreams = streams.filter { it.type == "hls" && !it.url.isNullOrBlank() }
                .sortedByDescending { stream ->
                    // Parse quality number from quality string like "800p", "534p"
                    stream.quality?.replace("p", "")?.toIntOrNull() ?: 0
                }

            if (hlsStreams.isEmpty()) {
                Log.w("MiruroService", ">>> NO HLS/M3U8 URL found")
                return null
            }

            // Get the best quality HLS stream
            val bestHls = hlsStreams.first()
            val streamUrl = bestHls.url
            val quality = bestHls.quality ?: "Auto"

            Log.d("MiruroService", ">>> HLS OPTIONS: ${hlsStreams.map { "${it.quality}: ${it.url?.take(40)}" }}")
            Log.d("MiruroService", ">>> BEST: $quality url=${streamUrl?.take(80)}")

            val referer = bestHls.referer ?: ""
            val headers = if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()

            // Get default English subtitle from either streams or ssub
            val allSubtitles = parsed.subtitles ?: parsed.ssub?.subtitles
            val subtitleUrl = allSubtitles?.find { it.default == true || it.label?.contains("English", ignoreCase = true) == true }?.file

            Log.d("MiruroService", ">>> PARSED: provider=$providerName, cat=$category, quality=$quality")
            Log.d("MiruroService", ">>> REFERRER: $referer")

            MiruroStreamResult(
                url = streamUrl,
                headers = headers,
                serverName = providerName ?: "animekai",
                category = category,
                qualities = listOf(QualityOption(quality = quality, url = streamUrl, width = quality.replace("p", "").toIntOrNull() ?: 0)),
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

    private const val PROVIDER_CHECK_TIMEOUT_MS = 10000

    suspend fun getProvidersForEpisode(anilistId: Int, episode: Int, preferredCategory: String = "sub"): List<MiruroProviderInfo> = withContext(Dispatchers.IO) {
        try {
            if (miruroBaseUrl.isBlank()) return@withContext emptyList()

            val cacheKey = "${anilistId}_$episode"
            providersCache[cacheKey]?.let { return@withContext it }

            val url = URL("$miruroBaseUrl/episodes/$anilistId")
            Log.d("MiruroService", ">>> API ENDPOINT: $url")
            Log.d("MiruroService", ">>> GET PROVIDERS FOR EPISODE $episode (validating streams)")

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

            // Don't cache - always re-validate providers
            val providers = parseAndValidateProviders(response, episode, anilistId, preferredCategory)
            providers
        } catch (e: Exception) {
            Log.e("MiruroService", "Providers error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun parseAndValidateProviders(
        response: String,
        episode: Int,
        anilistId: Int,
        preferredCategory: String
    ): List<MiruroProviderInfo> = withContext(Dispatchers.IO) {
        try {
            val parsed = json.decodeFromString<MiruroApiResponse>(response)
            val result = mutableListOf<MiruroProviderInfo>()

            val providers = parsed.providers
            if (providers == null) return@withContext emptyList()

            // Include all providers from the API response
            val providerList = listOf(
                "arc" to providers.arc,
                "jet" to providers.jet,
                "kiwi" to providers.kiwi,
                "zoro" to providers.zoro,
                "hop" to providers.hop,
                "dune" to providers.dune,
                "crunchyroll" to providers.crunchyroll
            )

            // Validate each provider's stream URL with HTTP check
            for ((name, data) in providerList) {
                if (data?.episodes == null) continue

                val hasSub = data.episodes?.sub?.isNotEmpty() == true
                val hasDub = data.episodes?.dub?.isNotEmpty() == true
                if (!hasSub && !hasDub) continue

                val eps = data.episodes
                val subEp = findEpisodeInSections(eps.sub, eps.sections, episode, "sub")
                val dubEp = findEpisodeInSections(eps.dub, eps.sections, episode, "dub")

                // Validate and add sub
                subEp?.let { ep ->
                    val watchPath = "$miruroBaseUrl/${ep.id}"
                    if (validateHttp200(watchPath)) {
                        Log.d("MiruroService", ">>> ADD sub: $name ep${ep.number}")
                        result.add(MiruroProviderInfo(name = name, category = "sub", watchPath = watchPath))
                    } else {
                        Log.w("MiruroService", ">>> SKIP $name sub: HTTP error")
                    }
                }

                // Validate and add dub
                dubEp?.let { ep ->
                    val watchPath = "$miruroBaseUrl/${ep.id}"
                    if (validateHttp200(watchPath)) {
                        Log.d("MiruroService", ">>> ADD dub: $name ep${ep.number}")
                        result.add(MiruroProviderInfo(name = name, category = "dub", watchPath = watchPath))
                    } else {
                        Log.w("MiruroService", ">>> SKIP $name dub: HTTP error")
                    }
                }
            }

            Log.d("MiruroService", ">>> PROVIDERS: ${result.map { "${it.name}_${it.category}" }}")
            result
        } catch (e: Exception) {
            Log.e("MiruroService", "Parse error: ${e.message}")
            emptyList()
        }
    }

    private fun validateHttp200(watchPath: String): Boolean {
        return try {
            val url = URL(watchPath)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val code = connection.responseCode
            connection.disconnect()
            code == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        }
    }

    private fun findEpisodeInSections(
        episodes: List<MiruroEpisodeItem>,
        sections: List<MiruroEpisodeSection>?,
        targetEpisode: Int,
        searchCategory: String
    ): MiruroEpisodeItem? {
        if (episodes.isEmpty()) return null

        if (sections.isNullOrEmpty()) {
            return episodes.find { it.number.toInt() == targetEpisode }
        }

        for (section in sections) {
            if (targetEpisode >= section.start && targetEpisode <= section.end) {
                val indexInSection = targetEpisode - section.start
                return episodes.getOrNull(indexInSection)
            }
        }

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
    val referer: String = "",
    val quality: String? = null
)