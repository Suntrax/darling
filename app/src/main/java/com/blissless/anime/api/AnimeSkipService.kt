package com.blissless.anime.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Service for fetching anime opening/ending timestamps from multiple sources.
 */
class AnimeSkipService(private val context: Context? = null) {

    companion object {
        private const val TAG = "AnimeSkipService"
        private const val API_URL = "https://api.aniskip.com/v2/skip-times"
        private const val DEFAULT_EPISODE_LENGTH = 1440
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val animeThemesService: AnimeThemesService by lazy {
        AnimeThemesService()
    }

    private val timestampCache: TimestampCache? by lazy {
        context?.let { TimestampCache(it) }
    }

    private val audioFingerprinter: AudioFingerprinter? by lazy {
        context?.let { AudioFingerprinter(it) }
    }

    suspend fun getSkipTimestampsWithFallback(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Int = DEFAULT_EPISODE_LENGTH,
        animeName: String = "",
        animeYear: Int? = null,
        animeId: Int = 0,
        episodePath: String? = null
    ): EpisodeTimestamps? {
        // 1. Check local cache first
        timestampCache?.let { cache ->
            val cached = if (malId > 0) cache.getTimestamp(malId, episodeNumber) else cache.getTimestampByName(animeName, episodeNumber)
            if (cached != null) return cache.toEpisodeTimestamps(cached)
        }

        // 2. Try AniSkip API
        val aniskipResult = getSkipTimestamps(malId, episodeNumber, episodeLength)
        if (aniskipResult != null && aniskipResult.hasTimestamps()) {
            timestampCache?.saveFromEpisodeTimestamps(malId, animeName, episodeNumber, aniskipResult, "aniskip")
            return aniskipResult
        }

        // 3. Try AnimeThemes fallback
        if (animeName.isNotEmpty()) {
            val fingerprintResult = tryAnimeThemesFallback(animeName, animeYear, episodeNumber, episodeLength, episodePath)
            if (fingerprintResult != null && fingerprintResult.hasTimestamps()) {
                timestampCache?.saveFromEpisodeTimestamps(animeId, animeName, episodeNumber, fingerprintResult, "animethemes")
                return fingerprintResult
            }
        }

        return null
    }

    suspend fun tryAnimeThemesFallback(
        animeName: String,
        animeYear: Int?,
        episodeNumber: Int,
        episodeLength: Int,
        episodePath: String?
    ): EpisodeTimestamps? = withContext(Dispatchers.IO) {
        try {
            val themesResult = animeThemesService.searchAnimeThemes(animeName, animeYear) ?: return@withContext null
            val firstOp = themesResult.openings.firstOrNull()
            val firstEd = themesResult.endings.firstOrNull()
            val opDuration = (firstOp?.duration ?: 90).coerceAtMost(90)
            val edDuration = (firstEd?.duration ?: 90).coerceAtMost(90)

            var introStart: Long? = if (firstOp != null) 0L else null
            var introEnd: Long? = if (firstOp != null) opDuration.toLong() else null
            var creditsStart: Long? = if (firstEd != null) (episodeLength - edDuration).toLong().coerceAtLeast(0) else null
            var creditsEnd: Long? = if (firstEd != null) episodeLength.toLong() else null

            if (introStart != null || creditsStart != null) {
                EpisodeTimestamps(
                    episodeNumber = episodeNumber,
                    introStart = introStart, introEnd = introEnd,
                    creditsStart = creditsStart, creditsEnd = creditsEnd,
                    recapStart = null, recapEnd = null,
                    allTimestamps = buildList {
                        if (introStart != null) add(Timestamp(introStart.toDouble(), "op", "op"))
                        if (creditsStart != null) add(Timestamp(creditsStart.toDouble(), "ed", "ed"))
                    }
                )
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun getSkipTimestamps(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Int = DEFAULT_EPISODE_LENGTH
    ): EpisodeTimestamps? {
        if (malId <= 0) return null
        return try {
            val url = "$API_URL/$malId/$episodeNumber?types[]=op&types[]=ed&episodeLength=$episodeLength"
            executeGetRequest(url)?.let { parseAniSkipResponse(it, episodeNumber) }
        } catch (e: Exception) { null }
    }

    suspend fun getSkipTimestampsByName(
        animeName: String,
        episodeNumber: Int,
        episodeLength: Int = DEFAULT_EPISODE_LENGTH,
        year: Int? = null
    ): EpisodeTimestamps? {
        if (animeName.isEmpty()) return null
        val malId = searchMalId(animeName, year)
        return if (malId != null) getSkipTimestamps(malId, episodeNumber, episodeLength) else null
    }

    private suspend fun searchMalId(animeName: String, targetYear: Int? = null): Int? = withContext(Dispatchers.IO) {
        try {
            val encodedName = URLEncoder.encode(animeName, "UTF-8")
            val url = "https://api.jikan.moe/v4/anime?q=$encodedName&limit=10"
            executeGetRequest(url)?.let { response ->
                val data = json.decodeFromString<JikanSearchResponse>(response)
                val candidates = data.data
                if (candidates.isEmpty()) return@withContext null

                val normalizedQuery = animeName.lowercase().trim()
                var bestMatch: JikanAnime? = null
                var highestScore = -1

                for (candidate in candidates) {
                    var score = 0
                    fun scoreTitle(title: String?) {
                        if (title.isNullOrBlank()) return
                        val nt = title.lowercase().trim()
                        if (nt == normalizedQuery) score = maxOf(score, 100)
                        else if (nt.contains(normalizedQuery) || normalizedQuery.contains(nt)) score = maxOf(score, 50)
                    }
                    scoreTitle(candidate.title)
                    candidate.titles?.forEach { scoreTitle(it.title) }
                    if (targetYear != null && candidate.startYear == targetYear) score += 20
                    if (score > highestScore) { highestScore = score; bestMatch = candidate }
                }
                bestMatch?.malId ?: candidates.firstOrNull()?.malId
            }
        } catch (e: Exception) { null }
    }

    private fun parseAniSkipResponse(response: String, episodeNumber: Int): EpisodeTimestamps? {
        return try {
            val data = json.decodeFromString<AniSkipResponse>(response)
            if (!data.found || data.results.isEmpty()) return null

            var introStart: Long? = null
            var introEnd: Long? = null
            var creditsStart: Long? = null
            var creditsEnd: Long? = null

            data.results.forEach { result ->
                val startTime = result.interval.startTime.toLong()
                val endTime = result.interval.endTime.toLong()
                when (result.skipType.lowercase()) {
                    "op" -> { introStart = startTime; introEnd = endTime }
                    "ed" -> { creditsStart = startTime; creditsEnd = endTime }
                }
            }

            EpisodeTimestamps(
                episodeNumber = episodeNumber,
                introStart = introStart, introEnd = introEnd,
                creditsStart = creditsStart, creditsEnd = creditsEnd,
                recapStart = null, recapEnd = null,
                allTimestamps = data.results.map { Timestamp(it.interval.startTime, it.skipType, it.skipType) }
            )
        } catch (e: Exception) { null }
    }

    private suspend fun executeGetRequest(urlString: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(urlString).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) { null }
    }
}

@Serializable
data class AniSkipResponse(val found: Boolean, val results: List<AniSkipResult>)

@Serializable
data class AniSkipResult(val skipType: String, val interval: AniSkipInterval)

@Serializable
data class AniSkipInterval(val startTime: Double, val endTime: Double)

@Serializable
data class JikanSearchResponse(val data: List<JikanAnime>)

@Serializable
data class JikanAnime(
    @SerialName("mal_id") val malId: Int,
    val title: String,
    val titles: List<JikanTitle>? = null,
    val year: Int? = null,
    val aired: JikanAired? = null
) {
    val startYear: Int? get() = year ?: aired?.from?.take(4)?.toIntOrNull()
}

@Serializable
data class JikanAired(val from: String? = null)

@Serializable
data class JikanTitle(val type: String, val title: String)

data class EpisodeTimestamps(
    val episodeNumber: Int,
    val introStart: Long?,
    val introEnd: Long?,
    val creditsStart: Long?,
    val creditsEnd: Long?,
    val recapStart: Long?,
    val recapEnd: Long?,
    val allTimestamps: List<Timestamp>
) {
    fun hasTimestamps(): Boolean = introStart != null || creditsStart != null || recapStart != null
}

data class Timestamp(val at: Double, val typeName: String, val typeId: String)
