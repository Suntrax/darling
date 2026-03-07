package com.blissless.anime.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Service for fetching anime opening/ending timestamps from aniskip.com API
 * API: https://api.aniskip.com/v2/skip-times/{mal_id}/{episode_number}
 */
class AnimeSkipService {

    companion object {
        private const val API_URL = "https://api.aniskip.com/v2/skip-times"
        private const val DEFAULT_EPISODE_LENGTH = 1440
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Get skip timestamps using MAL ID
     */
    suspend fun getSkipTimestamps(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Int = DEFAULT_EPISODE_LENGTH
    ): EpisodeTimestamps? {
        if (malId <= 0) return null

        return try {
            val url = "$API_URL/$malId/$episodeNumber?types[]=op&types[]=ed&episodeLength=$episodeLength"
            val response = executeGetRequest(url)

            if (response != null) {
                parseAniSkipResponse(response, episodeNumber)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get skip timestamps by searching for MAL ID using anime name
     */
    suspend fun getSkipTimestampsByName(
        animeName: String,
        episodeNumber: Int,
        episodeLength: Int = DEFAULT_EPISODE_LENGTH
    ): EpisodeTimestamps? {
        if (animeName.isEmpty()) return null

        val malId = searchMalId(animeName)

        return if (malId != null) {
            getSkipTimestamps(malId, episodeNumber, episodeLength)
        } else {
            null
        }
    }

    /**
     * Search for MAL ID using Jikan API
     */
    private suspend fun searchMalId(animeName: String): Int? {
        return try {
            val encodedName = URLEncoder.encode(animeName, "UTF-8")
            val url = "https://api.jikan.moe/v4/anime?q=$encodedName&limit=1"
            val response = executeGetRequest(url)

            if (response != null) {
                val data = json.decodeFromString<JikanSearchResponse>(response)
                data.data.firstOrNull()?.mal_id
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse AniSkip API response
     */
    private fun parseAniSkipResponse(response: String, episodeNumber: Int): EpisodeTimestamps? {
        return try {
            val data = json.decodeFromString<AniSkipResponse>(response)

            if (!data.found || data.results.isEmpty()) {
                return null
            }

            var introStart: Long? = null
            var introEnd: Long? = null
            var creditsStart: Long? = null
            var creditsEnd: Long? = null

            data.results.forEach { result ->
                val skipType = result.skipType.lowercase()
                val startTime = result.interval.startTime.toLong()
                val endTime = result.interval.endTime.toLong()

                when (skipType) {
                    "op" -> {
                        introStart = startTime
                        introEnd = endTime
                    }
                    "ed" -> {
                        creditsStart = startTime
                        creditsEnd = endTime
                    }
                }
            }

            EpisodeTimestamps(
                episodeNumber = episodeNumber,
                introStart = introStart,
                introEnd = introEnd,
                creditsStart = creditsStart,
                creditsEnd = creditsEnd,
                recapStart = null,
                recapEnd = null,
                allTimestamps = data.results.map {
                    Timestamp(
                        at = it.interval.startTime,
                        typeName = it.skipType,
                        typeId = it.skipType
                    )
                }
            )

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Execute a GET request
     */
    private suspend fun executeGetRequest(urlString: String): String? = try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpsURLConnection

        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        val responseCode = connection.responseCode

        if (responseCode == 200) {
            connection.inputStream.use { it ->
                it.bufferedReader().use { br ->
                    br.readText()
                }
            }
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Data classes
@Serializable
data class AniSkipResponse(
    val found: Boolean,
    val results: List<AniSkipResult>
)

@Serializable
data class AniSkipResult(
    val skipType: String,
    val interval: AniSkipInterval,
    val episodeLength: Double? = null
)

@Serializable
data class AniSkipInterval(
    val startTime: Double,
    val endTime: Double
)

@Serializable
data class JikanSearchResponse(
    val data: List<JikanAnime>
)

@Serializable
data class JikanAnime(
    val mal_id: Int,
    val title: String,
    val titles: List<JikanTitle>? = null
)

@Serializable
data class JikanTitle(
    val type: String,
    val title: String
)

data class EpisodeTimestamps(
    val episodeNumber: Int,
    val introStart: Long?,
    val introEnd: Long?,
    val creditsStart: Long?,
    val creditsEnd: Long?,
    val recapStart: Long?,
    val recapEnd: Long?,
    val allTimestamps: List<Timestamp>
)

data class Timestamp(
    val at: Double,
    val typeName: String,
    val typeId: String
) {
    val isIntro: Boolean get() = typeId.lowercase() == "op"
    val isCredits: Boolean get() = typeId.lowercase() == "ed"
    val isRecap: Boolean get() = typeId.lowercase() == "recap"
}
