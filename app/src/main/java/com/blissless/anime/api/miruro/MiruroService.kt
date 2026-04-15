package com.blissless.anime.api.miruro

import android.util.Log
import com.blissless.anime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

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

class MiruroService {

    companion object {
        private const val TIMEOUT_MS = 20000
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    suspend fun getAnimeEpisodes(anilistId: Int): MiruroAnimeEpisodes? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.MIRURO_SCRAPER_BASE_URL
            if (baseUrl.isBlank()) {
                Log.e("MiruroService", "MIRURO_SCRAPER_BASE_URL is not configured")
                return@withContext null
            }

            val url = URL("$baseUrl/episodes/$anilistId")
            Log.d("MiruroService", "Fetching episodes from: $url")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            Log.d("MiruroService", "Response code: $responseCode")
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("MiruroService", "Failed to fetch episodes: HTTP $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            Log.d("MiruroService", "Response length: ${response.length} chars")
            connection.disconnect()

            parseMiruroResponse(response)
        } catch (e: Exception) {
            Log.e("MiruroService", "Error fetching episodes: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun parseMiruroResponse(response: String): MiruroAnimeEpisodes? {
        return try {
            Log.d("MiruroService", "Raw API response: ${response.take(1000)}")
            val parsed = json.decodeFromString<MiruroApiResponse>(response)
            
            val providers = parsed.providers ?: return null.also {
                Log.w("MiruroService", "No providers found in response")
            }
            
            Log.d("MiruroService", "Providers available: ${providers.keys()}")
            
            val providerKey: String
            val episodesArray: List<MiruroEpisodeItem>
            
            // Prefer zoro, then kiwi, then first available
            when {
                providers.zoro != null && providers.zoro.episodes != null -> {
                    providerKey = "zoro"
                    episodesArray = providers.zoro.episodes.sub
                    Log.d("MiruroService", "Using zoro provider with ${episodesArray.size} episodes")
                }
                providers.kiwi != null && providers.kiwi.episodes != null -> {
                    providerKey = "kiwi"
                    episodesArray = providers.kiwi.episodes.sub
                    Log.d("MiruroService", "Using kiwi provider with ${episodesArray.size} episodes")
                }
                providers.hop != null && providers.hop.episodes != null -> {
                    providerKey = "hop"
                    episodesArray = providers.hop.episodes.sub
                }
                providers.arc != null && providers.arc.episodes != null -> {
                    providerKey = "arc"
                    episodesArray = providers.arc.episodes.sub
                }
                providers.dune != null && providers.dune.episodes != null -> {
                    providerKey = "dune"
                    episodesArray = providers.dune.episodes.sub
                }
                providers.jet != null && providers.jet.episodes != null -> {
                    providerKey = "jet"
                    episodesArray = providers.jet.episodes.sub
                }
                else -> {
                    Log.w("MiruroService", "No episodes found in expected providers")
                    return null
                }
            }

            if (episodesArray.isEmpty()) {
                Log.w("MiruroService", "Episode list is empty")
                return null
            }

            val totalEpisodes = episodesArray.size
            val episodeInfos = episodesArray.mapIndexed { index: Int, ep: MiruroEpisodeItem ->
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
            }

            Log.d("MiruroService", "Parsed $totalEpisodes episodes from $providerKey provider")
            MiruroAnimeEpisodes(episodes = episodeInfos, totalEpisodes = totalEpisodes)
        } catch (e: Exception) {
            Log.e("MiruroService", "Error parsing response: ${e.message}")
            e.printStackTrace()
            null
        }
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
) {
    fun keys() = listOfNotNull(
        kiwi?.let { "kiwi" to it },
        zoro?.let { "zoro" to it },
        hop?.let { "hop" to it },
        arc?.let { "arc" to it },
        dune?.let { "dune" to it },
        jet?.let { "jet" to it },
        crunchyroll?.let { "crunchyroll" to it }
    ).toMap().keys
}

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
    val dub: List<MiruroEpisodeItem> = emptyList()
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
