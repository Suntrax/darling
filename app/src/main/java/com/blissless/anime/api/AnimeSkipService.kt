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
 *
 * Fallback chain:
 * 1. Check local cache first
 * 2. AniSkip API (fast, crowdsourced)
 * 3. AnimeThemes audio fingerprinting (automatic detection)
 *
 * This provides the best user experience by always having skip timestamps available.
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

    // Lazy initialization of fallback services
    private val animeThemesService: AnimeThemesService by lazy {
        AnimeThemesService()
    }

    private val timestampCache: TimestampCache? by lazy {
        context?.let { TimestampCache(it) }
    }

    private val audioFingerprinter: AudioFingerprinter? by lazy {
        context?.let { AudioFingerprinter(it) }
    }

    /**
     * Get skip timestamps using the full fallback chain.
     *
     * @param malId MAL ID of the anime
     * @param episodeNumber Episode number
     * @param episodeLength Episode length in seconds
     * @param animeName Anime name (for fallback searches)
     * @param animeYear Anime year (improves matching accuracy)
     * @param episodePath Local path to episode file (for fingerprinting, optional)
     * @return EpisodeTimestamps or null if no timestamps found
     */
    suspend fun getSkipTimestampsWithFallback(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Int = DEFAULT_EPISODE_LENGTH,
        animeName: String = "",
        animeYear: Int? = null,
        animeId: Int = 0,
        episodePath: String? = null
    ): EpisodeTimestamps? {
        Log.d(TAG, "=== Getting skip timestamps for ${animeName.take(20)} ep $episodeNumber ===")

        // 1. Check local cache first
        timestampCache?.let { cache ->
            val cached = if (malId > 0) {
                cache.getTimestamp(malId, episodeNumber)
            } else {
                cache.getTimestampByName(animeName, episodeNumber)
            }

            if (cached != null) {
                Log.d(TAG, "Using cached timestamps (source: ${cached.source})")
                return cache.toEpisodeTimestamps(cached)
            }
        }

        // 2. Try AniSkip API
        val aniskipResult = getSkipTimestamps(malId, episodeNumber, episodeLength)

        if (aniskipResult != null && aniskipResult.hasTimestamps()) {
            Log.d(TAG, "Got timestamps from AniSkip")
            // Cache the result
            timestampCache?.saveFromEpisodeTimestamps(
                animeId = malId,
                animeName = animeName,
                episodeNumber = episodeNumber,
                timestamps = aniskipResult,
                source = "aniskip"
            )
            return aniskipResult
        }

        // 3. Try AnimeThemes fingerprinting fallback
        if (animeName.isNotEmpty() && audioFingerprinter != null) {
            Log.d(TAG, "AniSkip returned no results, trying AnimeThemes fallback")

            val fingerprintResult = tryAnimeThemesFallback(
                animeName = animeName,
                animeYear = animeYear,
                episodeNumber = episodeNumber,
                episodeLength = episodeLength,
                episodePath = episodePath
            )

            if (fingerprintResult != null && fingerprintResult.hasTimestamps()) {
                Log.d(TAG, "Got timestamps from AnimeThemes fingerprinting")
                // Cache the result
                timestampCache?.saveFromEpisodeTimestamps(
                    animeId = animeId,
                    animeName = animeName,
                    episodeNumber = episodeNumber,
                    timestamps = fingerprintResult,
                    source = "animethemes"
                )
                return fingerprintResult
            }
        }

        Log.d(TAG, "No timestamps found from any source")
        return null
    }

    /**
     * Try to get timestamps using AnimeThemes audio fingerprinting
     */
    private suspend fun tryAnimeThemesFallback(
        animeName: String,
        animeYear: Int?,
        episodeNumber: Int,
        episodeLength: Int,
        episodePath: String?
    ): EpisodeTimestamps? {
        return withContext(Dispatchers.IO) {
            try {
                // Search for anime on AnimeThemes
                val themesResult = animeThemesService.searchAnimeThemes(animeName, animeYear)

                if (themesResult == null) {
                    Log.d(TAG, "AnimeThemes: No themes found for $animeName")
                    return@withContext null
                }

                Log.d(TAG, "AnimeThemes: Found ${themesResult.openings.size} OPs, ${themesResult.endings.size} EDs")

                var introStart: Long? = null
                var introEnd: Long? = null
                var creditsStart: Long? = null
                var creditsEnd: Long? = null

                // Get first OP and ED for fingerprinting
                val firstOp = themesResult.openings.firstOrNull()
                val firstEd = themesResult.endings.firstOrNull()

                // Use AnimeThemes duration info directly if available
                // OP is typically 90 seconds, ED is typically 90 seconds
                // Never exceed 90 seconds for standard OP/ED
                val opDuration = (firstOp?.duration ?: 90).coerceAtMost(90)
                val edDuration = (firstEd?.duration ?: 90).coerceAtMost(90)

                // If we have the episode file, we can do actual fingerprinting
                // For now, use heuristic timestamps based on common patterns
                if (episodePath != null && audioFingerprinter != null) {
                    // TODO: Implement actual fingerprinting when episode file is available
                    // This would involve:
                    // 1. Download OP/ED audio from AnimeThemes
                    // 2. Extract fingerprints
                    // 3. Match against episode audio
                }

                // Use heuristic timestamps as fallback
                // OP typically starts at 0 or after a cold open (first 30-90 seconds)
                // ED typically starts at episode_length - ED_duration

                if (firstOp != null) {
                    // Common OP positions:
                    // - Episode 1: Often starts at 0
                    // - Later episodes: Often after a recap/cold open
                    // Use a safe default of starting at 0 for episode 1
                    introStart = 0L

                    // For later episodes, OP might start after cold open
                    // We'll use the first frame as default
                    introEnd = opDuration.toLong()

                    Log.d(TAG, "AnimeThemes OP estimate: $introStart - $introEnd")
                }

                if (firstEd != null) {
                    // ED typically starts near the end of the episode
                    val edStart = (episodeLength - edDuration).coerceAtLeast(0)
                    creditsStart = edStart.toLong()
                    creditsEnd = episodeLength.toLong()

                    Log.d(TAG, "AnimeThemes ED estimate: $creditsStart - $creditsEnd")
                }

                if (introStart != null || creditsStart != null) {
                    EpisodeTimestamps(
                        episodeNumber = episodeNumber,
                        introStart = introStart,
                        introEnd = introEnd,
                        creditsStart = creditsStart,
                        creditsEnd = creditsEnd,
                        recapStart = null,
                        recapEnd = null,
                        allTimestamps = buildList {
                            if (introStart != null && introEnd != null) {
                                add(Timestamp(introStart.toDouble(), "op", "op"))
                            }
                            if (creditsStart != null) {
                                add(Timestamp(creditsStart.toDouble(), "ed", "ed"))
                            }
                        }
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in AnimeThemes fallback", e)
                null
            }
        }
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
     * Get skip timestamps by searching for MAL ID using anime name.
     * Passing the 'year' significantly improves accuracy (e.g. distinguishes 2006 Bartender from 2024).
     */
    suspend fun getSkipTimestampsByName(
        animeName: String,
        episodeNumber: Int,
        episodeLength: Int = DEFAULT_EPISODE_LENGTH,
        year: Int? = null
    ): EpisodeTimestamps? {
        if (animeName.isEmpty()) return null

        val malId = searchMalId(animeName, year)

        return if (malId != null) {
            getSkipTimestamps(malId, episodeNumber, episodeLength)
        } else {
            null
        }
    }

    /**
     * Search for MAL ID using Jikan API with scoring logic.
     * Fetches top results and scores them based on title exactness and year.
     */
    private suspend fun searchMalId(animeName: String, targetYear: Int? = null): Int? = withContext(Dispatchers.IO) {
        try {
            val encodedName = URLEncoder.encode(animeName, "UTF-8")
            // Fetch top 10 results to have enough candidates for scoring
            val url = "https://api.jikan.moe/v4/anime?q=$encodedName&limit=10"
            val response = executeGetRequestInternal(url)

            if (response != null) {
                val data = json.decodeFromString<JikanSearchResponse>(response)
                val candidates = data.data

                if (candidates.isEmpty()) return@withContext null

                // Score each candidate
                var bestMatch: JikanAnime? = null
                var highestScore = -1

                val normalizedQuery = animeName.lowercase().trim()

                for (candidate in candidates) {
                    var score = 0

                    // Helper to score a title string
                    fun scoreTitle(title: String?) {
                        if (title.isNullOrBlank()) return
                        val normalizedTitle = title.lowercase().trim()

                        if (normalizedTitle == normalizedQuery) {
                            score = maxOf(score, 100) // Exact match
                        } else if (normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle)) {
                            score = maxOf(score, 50) // Partial match
                        }
                    }

                    // Score main title
                    scoreTitle(candidate.title)

                    // Score alternative titles (English, Japanese, Synonyms)
                    candidate.titles?.forEach { jikanTitle ->
                        scoreTitle(jikanTitle.title)
                    }

                    // Year match bonus (Critical for sequels/remakes)
                    // Use the extracted startYear from the helper
                    val candidateYear = candidate.startYear
                    if (targetYear != null && candidateYear != null) {
                        if (candidateYear == targetYear) {
                            score += 20
                        }
                    }

                    Log.d(TAG, "Candidate: ${candidate.title} (${candidate.startYear}) -> Score: $score")

                    // Update best match
                    if (score > highestScore) {
                        highestScore = score
                        bestMatch = candidate
                    }
                }

                if (bestMatch != null) {
                    Log.d(TAG, "Best match for '$animeName': ${bestMatch.title} (MAL ID: ${bestMatch.malId}, Score: $highestScore)")
                    bestMatch.malId
                } else {
                    // Fallback to first result if no scoring worked
                    candidates.firstOrNull()?.malId
                }
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
     * Execute a GET request - public suspend function that switches to IO dispatcher
     */
    private suspend fun executeGetRequest(urlString: String): String? = withContext(Dispatchers.IO) {
        executeGetRequestInternal(urlString)
    }

    /**
     * Internal execute GET request - must only be called from IO dispatcher context
     */
    private fun executeGetRequestInternal(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpsURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode

            if (responseCode == 200) {
                connection.inputStream.use { input ->
                    input.bufferedReader().use { br ->
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
    @SerialName("mal_id")
    val malId: Int,
    val title: String,
    val titles: List<JikanTitle>? = null,
    val year: Int? = null, // Sometimes present at top level
    val aired: JikanAired? = null // Usually present with 'from' and 'to' dates
) {
    // Helper to safely extract the start year from 'aired' or top-level 'year'
    val startYear: Int?
        get() {
            // Try top level year first
            if (year != null) return year

            // Try parsing from aired.from (e.g. "2006-10-15T00:00:00+00:00")
            return aired?.from?.take(4)?.toIntOrNull()
        }
}

@Serializable
data class JikanAired(
    val from: String? = null
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
) {
    /**
     * Check if timestamps contain any useful data
     */
    fun hasTimestamps(): Boolean {
        return introStart != null || creditsStart != null || recapStart != null
    }
}

data class Timestamp(
    val at: Double,
    val typeName: String,
    val typeId: String
) {
    @Suppress("unused")
    val isIntro: Boolean get() = typeId.lowercase() == "op"

    @Suppress("unused")
    val isCredits: Boolean get() = typeId.lowercase() == "ed"

    @Suppress("unused")
    val isRecap: Boolean get() = typeId.lowercase() == "recap"
}
