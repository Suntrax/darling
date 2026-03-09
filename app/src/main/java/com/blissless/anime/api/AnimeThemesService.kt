package com.blissless.anime.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Service for fetching anime opening/ending videos from AnimeThemes API
 * API: https://api.animethemes.moe/anime/{slug}?include=animethemes.animethemeentries.videos
 */
class AnimeThemesService {

    companion object {
        private const val TAG = "AnimeThemesService"
        private const val API_BASE = "https://api.animethemes.moe"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Search for anime themes by anime name
     * Uses direct slug lookup: /anime/{slug}?include=animethemes.animethemeentries.videos
     */
    suspend fun searchAnimeThemes(animeName: String, year: Int? = null): AnimeThemesResult? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching AnimeThemes for: $animeName (year: $year)")

                // Convert anime name to slug format
                val slug = convertToSlug(animeName)
                Log.d(TAG, "Trying slug: $slug")

                // Try direct lookup by slug
                val result = getAnimeThemesBySlug(slug)
                if (result != null) return@withContext result

                // Try variations of the slug
                val variations = generateSlugVariations(animeName, year)
                for (variation in variations) {
                    Log.d(TAG, "Trying slug variation: $variation")
                    val varResult = getAnimeThemesBySlug(variation)
                    if (varResult != null) return@withContext varResult
                }

                Log.d(TAG, "No themes found for any slug variation")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error searching AnimeThemes", e)
                null
            }
        }
    }

    /**
     * Get anime themes by slug - direct URL lookup
     * URL format: https://api.animethemes.moe/anime/{slug}?include=animethemes.animethemeentries.videos
     */
    suspend fun getAnimeThemesBySlug(slug: String): AnimeThemesResult? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE/anime/$slug?include=animethemes.animethemeentries.videos"

                Log.d(TAG, "AnimeThemes URL: $url")
                val response = executeGetRequest(url)

                if (response != null) {
                    Log.d(TAG, "AnimeThemes response length: ${response.length}")
                    parseAnimeResponse(response)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching anime themes by slug: $slug", e)
                null
            }
        }
    }

    /**
     * Convert anime name to URL-friendly slug
     */
    private fun convertToSlug(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    /**
     * Generate slug variations to try
     */
    private fun generateSlugVariations(name: String, year: Int?): List<String> {
        val variations = mutableListOf<String>()
        val baseSlug = convertToSlug(name)

        // Try with year suffix
        if (year != null) {
            variations.add("$baseSlug-$year")
        }

        // Try removing common suffixes
        var cleanedName = name
            .replace(Regex(": season \\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("season \\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex(": part \\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("part \\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex(" \\(\\d{4}\\)"), "")
            .replace(Regex(": tv", RegexOption.IGNORE_CASE), "")
            .replace(Regex(": the movie", RegexOption.IGNORE_CASE), "")
            .replace(Regex(" - .+"), "")
            .replace(Regex(": .+"), "")
            .trim()

        if (cleanedName != name) {
            val cleanedSlug = convertToSlug(cleanedName)
            if (cleanedSlug != baseSlug) {
                variations.add(cleanedSlug)
                if (year != null) variations.add("$cleanedSlug-$year")
            }
        }

        // Try Roman numeral variations
        val romanNumerals = mapOf(
            " i" to "-1", " ii" to "-2", " iii" to "-3", " iv" to "-4", " v" to "-5",
            " vi" to "-6", " vii" to "-7", " viii" to "-8", " ix" to "-9", " x" to "-10"
        )
        var romanName = name.lowercase()
        for ((roman, replacement) in romanNumerals) {
            romanName = romanName.replace(roman, replacement)
        }
        val romanSlug = convertToSlug(romanName)
        if (romanSlug != baseSlug) {
            variations.add(romanSlug)
        }

        return variations.distinct()
    }

    /**
     * Parse anime response
     */
    private fun parseAnimeResponse(response: String): AnimeThemesResult? {
        return try {
            val data = json.decodeFromString<AnimeThemesApiResponse>(response)
            val anime = data.anime

            Log.d(TAG, "Found anime: ${anime.name} with ${anime.animethemes?.size ?: 0} themes")
            extractThemesFromAnime(anime)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AnimeThemes response", e)
            null
        }
    }

    /**
     * Extract themes from anime data
     */
    private fun extractThemesFromAnime(anime: AnimeThemesAnime): AnimeThemesResult {
        val openings = mutableListOf<ThemeEntry>()
        val endings = mutableListOf<ThemeEntry>()

        anime.animethemes?.forEach { theme ->
            val isOp = theme.type.equals("OP", ignoreCase = true) ||
                    theme.slug?.startsWith("OP", ignoreCase = true) == true
            val isEd = theme.type.equals("ED", ignoreCase = true) ||
                    theme.slug?.startsWith("ED", ignoreCase = true) == true

            theme.animethemeentries?.forEach { entry ->
                entry.videos?.forEach { video ->
                    val themeEntry = ThemeEntry(
                        type = theme.type ?: "Unknown",
                        sequence = theme.sequence,
                        songTitle = null,
                        artist = null,
                        videoUrl = video.link,
                        audioUrl = null,
                        duration = estimateDuration(video.size),
                        size = video.size,
                        resolution = video.resolution
                    )

                    if (isOp) {
                        openings.add(themeEntry)
                        Log.d(TAG, "Found OP: ${video.filename} (${video.link})")
                    } else if (isEd) {
                        endings.add(themeEntry)
                        Log.d(TAG, "Found ED: ${video.filename} (${video.link})")
                    }
                }
            }
        }

        Log.d(TAG, "Extracted ${openings.size} OPs and ${endings.size} EDs")

        return AnimeThemesResult(
            animeName = anime.name,
            animeSlug = anime.slug,
            year = anime.year,
            openings = openings,
            endings = endings
        )
    }

    /**
     * Estimate duration from file size (rough estimate)
     * Standard anime OP/ED are typically 90 seconds (1.5 minutes)
     * Some can be shorter (60s) or longer (up to 120s), but 180s is way too long
     */
    private fun estimateDuration(sizeBytes: Int?): Int? {
        if (sizeBytes == null) return null
        // File size estimation is unreliable for OP/ED
        // Most OP/ED are around 90 seconds, just return that as default
        return 90
    }

    /**
     * Execute HTTP GET request
     */
    private fun executeGetRequest(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpsURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.d(TAG, "AnimeThemes HTTP ${connection.responseCode} for $urlString")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing AnimeThemes request", e)
            null
        }
    }
}

// API response data classes - field names match API exactly

@Serializable
data class AnimeThemesApiResponse(
    val anime: AnimeThemesAnime
)

@Serializable
data class AnimeThemesAnime(
    val id: Int? = null,
    val name: String = "",
    val slug: String = "",
    val year: Int? = null,
    val season: String? = null,
    val animethemes: List<AnimeTheme>? = null
)

@Serializable
data class AnimeTheme(
    val id: Int? = null,
    val type: String? = null,
    val slug: String? = null,
    val sequence: Int? = null,
    val animethemeentries: List<AnimeThemeEntry>? = null
)

@Serializable
data class AnimeThemeEntry(
    val id: Int? = null,
    val version: Int? = null,
    val videos: List<AnimeThemeVideo>? = null
)

@Serializable
data class AnimeThemeVideo(
    val id: Int? = null,
    val filename: String? = null,
    val link: String? = null,
    val resolution: Int? = null,
    val size: Int? = null
)

data class AnimeThemesResult(
    val animeName: String,
    val animeSlug: String,
    val year: Int?,
    val openings: List<ThemeEntry>,
    val endings: List<ThemeEntry>
)

data class ThemeEntry(
    val type: String,
    val sequence: Int?,
    val songTitle: String?,
    val artist: String?,
    val videoUrl: String?,
    val audioUrl: String?,
    val duration: Int?,
    val size: Int?,
    val resolution: Int?
)
