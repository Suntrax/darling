package com.blissless.anime.data

import android.util.Log
import com.blissless.anime.BuildConfig
import com.blissless.anime.api.AniwatchService
import com.blissless.anime.api.AniwatchStreamResult
import com.blissless.anime.api.EpisodeStreams
import com.blissless.anime.data.models.*
import com.blissless.anime.network.GraphQLClient
import com.blissless.anime.network.GraphQLConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/**
 * Handles all API calls and data fetching.
 * Optimized to use GraphQLClient for high-performance AniList requests.
 */
class AnimeRepository(
    private val userPreferences: UserPreferences,
    private val cacheManager: CacheManager
) {

    companion object {
        private const val TAG = "AnimeRepository"
        private val CLIENT_IDS = listOf(BuildConfig.CLIENT_ID_ANILIST, BuildConfig.CLIENT_ID_ANILIST2)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // High-performance GraphQL client
    private val graphQLClient = GraphQLClient(
        config = GraphQLConfig(
            maxConcurrentRequests = 4,
            minRequestIntervalMs = 500L,
            cacheDurationMs = 5 * 60 * 1000L // 5 minutes
        )
    )

    // ============================================
    // GraphQL Requests (Optimized via GraphQLClient)
    // ============================================

    suspend fun graphqlRequest(query: String, variables: Map<String, Any?>): String? {
        val token = userPreferences.authToken.value ?: return null
        
        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = true,
            authToken = token,
            clientIds = CLIENT_IDS,
            useCache = true,
            parser = { it } // Return raw string for existing parsing logic
        )

        return result.data
    }

    suspend fun publicGraphqlRequest(query: String, variables: Map<String, Any?>): String? {
        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = false,
            clientIds = CLIENT_IDS,
            useCache = true,
            parser = { it }
        )

        return result.data
    }

    // ============================================
    // User Operations
    // ============================================

    suspend fun fetchUser(): ViewerResponse? {
        val query = """
            query {
                Viewer {
                    id
                    name
                    avatar { medium }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, emptyMap())?.let {
            try {
                json.decodeFromString<ViewerResponse>(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse user response", e)
                null
            }
        }
    }

    // ============================================
    // Anime Lists
    // ============================================

    suspend fun fetchMediaLists(userId: Int): MediaListResponse? {
        val query = """
            query (${'$'}userId: Int) {
                MediaListCollection(userId: ${'$'}userId, type: ANIME) {
                    lists {
                        name
                        status
                        entries {
                            id
                            mediaId
                            progress
                            status
                            media {
                                id
                                idMal
                                title { romaji english }
                                coverImage { large medium }
                                bannerImage
                                episodes
                                nextAiringEpisode { episode airingAt }
                                status
                                averageScore
                                genres
                                seasonYear
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("userId" to userId))?.let {
            try {
                json.decodeFromString<MediaListResponse>(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse lists response", e)
                null
            }
        }
    }

    // ============================================
    // Explore Data
    // ============================================

    suspend fun fetchBatchedExplore(): BatchedExploreResponse? {
        val query = """
            query {
                featured: Page(page: 1, perPage: 10) {
                    media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                seasonal: Page(page: 1, perPage: 20) {
                    media(type: ANIME, sort: POPULARITY_DESC, status: RELEASING) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                topSeries: Page(page: 1, perPage: 20) {
                    media(type: ANIME, format: TV, sort: SCORE_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                topMovies: Page(page: 1, perPage: 20) {
                    media(type: ANIME, format: MOVIE, sort: SCORE_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                action: Page(page: 1, perPage: 15) {
                    media(type: ANIME, genre: "Action", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                romance: Page(page: 1, perPage: 15) {
                    media(type: ANIME, genre: "Romance", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                comedy: Page(page: 1, perPage: 15) {
                    media(type: ANIME, genre: "Comedy", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                fantasy: Page(page: 1, perPage: 15) {
                    media(type: ANIME, genre: "Fantasy", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                scifi: Page(page: 1, perPage: 15) {
                    media(type: ANIME, genre: "Sci-Fi", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, emptyMap())?.let {
            try {
                json.decodeFromString<BatchedExploreResponse>(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse batched explore response", e)
                null
            }
        }
    }

    // ============================================
    // Airing Schedule
    // ============================================

    suspend fun fetchAiringSchedule(): List<AiringScheduleEntry> {
        val currentTime = System.currentTimeMillis() / 1000
        val startTime = currentTime - (24 * 60 * 60)
        val endTime = currentTime + (8 * 24 * 60 * 60)

        val query = """
            query (${'$'}page: Int, ${'$'}startTime: Int, ${'$'}endTime: Int) {
                Page(page: ${'$'}page, perPage: 50) {
                    airingSchedules(airingAt_greater: ${'$'}startTime, airingAt_lesser: ${'$'}endTime, sort: TIME) {
                        id
                        airingAt
                        episode
                        timeUntilAiring
                        mediaId
                        media {
                            id
                            idMal
                            title { romaji english }
                            coverImage { large }
                            episodes
                            status
                            averageScore
                            genres
                            seasonYear
                        }
                    }
                }
            }
        """.trimIndent()

        val allSchedules = mutableListOf<AiringScheduleEntry>()
        var page = 1
        var hasMore = true

        while (hasMore && page <= 5) {
            val response = publicGraphqlRequest(
                query,
                mapOf("page" to page, "startTime" to startTime, "endTime" to endTime)
            )

            if (response == null) break

            try {
                val data = json.decodeFromString<AiringScheduleResponse>(response)
                val pageSchedules = data.data.Page.airingSchedules

                if (pageSchedules.isEmpty()) {
                    hasMore = false
                } else {
                    allSchedules.addAll(pageSchedules)
                    hasMore = pageSchedules.size == 50
                    page++
                }
            } catch (_: Exception) {
                break
            }
        }

        return allSchedules
    }

    // ============================================
    // Search
    // ============================================

    suspend fun searchAnime(searchQuery: String): List<ExploreMedia> {
        if (searchQuery.isBlank()) return emptyList()

        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 20) {
                    media(search: ${'$'}search, type: ANIME, sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("search" to searchQuery))?.let {
            try {
                val data = json.decodeFromString<ExploreResponse>(it)
                data.data.Page.media
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse search results", e)
                emptyList()
            }
        } ?: emptyList()
    }

    // ============================================
    // Detailed Anime
    // ============================================

    suspend fun fetchDetailedAnime(animeId: Int): DetailedAnimeMedia? {
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id, type: ANIME) {
                    id
                    title { romaji english native }
                    coverImage { large }
                    bannerImage
                    description(asHtml: false)
                    episodes
                    duration
                    status
                    averageScore
                    popularity
                    favourites
                    genres
                    season
                    seasonYear
                    format
                    source
                    studios(isMain: true) { nodes { id name } }
                    startDate { year month day }
                    endDate { year month day }
                    nextAiringEpisode { episode airingAt }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let {
            try {
                val data = json.decodeFromString<DetailedAnimeResponse>(it)
                data.data.Media
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse detailed anime", e)
                null
            }
        }
    }

    // ============================================
    // Mutations
    // ============================================

    suspend fun updateProgress(mediaId: Int, progress: Int): Boolean {
        cacheManager.invalidateUserCache()
        graphQLClient.clearCache() // Invalidate high-performance client cache too

        val query = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
                SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) {
                    id
                    progress
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("mediaId" to mediaId, "progress" to progress)) != null
    }

    suspend fun updateStatus(mediaId: Int, status: String, progress: Int? = null): Boolean {
        cacheManager.invalidateUserCache()
        graphQLClient.clearCache()

        val query = """
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus${if (progress != null) ", ${'$'}progress: Int" else ""}) {
                SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status${if (progress != null) ", progress: ${'$'}progress" else ""}) {
                    id
                    status
                }
            }
        """.trimIndent()

        val variables = mutableMapOf<String, Any?>("mediaId" to mediaId, "status" to status)
        if (progress != null) variables["progress"] = progress

        return graphqlRequest(query, variables) != null
    }

    suspend fun deleteListEntry(entryId: Int): Boolean {
        cacheManager.invalidateUserCache()
        graphQLClient.clearCache()

        val query = """
            mutation (${'$'}id: Int) {
                DeleteMediaListEntry(id: ${'$'}id) {
                    deleted
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("id" to entryId)) != null
    }

    suspend fun updateScore(mediaId: Int, score: Int): Boolean {
        cacheManager.invalidateUserCache()
        graphQLClient.clearCache()

        val query = """
            mutation (${'$'}mediaId: Int, ${'$'}score: Int) {
                SaveMediaListEntry(mediaId: ${'$'}mediaId, score: ${'$'}score) {
                    id
                    score
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("mediaId" to mediaId, "score" to score)) != null
    }

    // ============================================
    // Stream Operations
    // ============================================

    suspend fun tryAllServersWithFallback(
        animeName: String,
        episodeNumber: Int,
        animeId: Int,
        latestAiredEpisode: Int = Int.MAX_VALUE,
        preferredCategory: String
    ): StreamFetchResult = withContext(Dispatchers.IO) {
        val key = "${animeId}_$episodeNumber"

        if (latestAiredEpisode > 0 && latestAiredEpisode < Int.MAX_VALUE && episodeNumber > latestAiredEpisode) {
            return@withContext StreamFetchResult(null, false, preferredCategory, preferredCategory)
        }

        cacheManager.getCachedStream(key)?.let { cachedStream ->
            return@withContext StreamFetchResult(
                cachedStream,
                cachedStream.category != preferredCategory,
                preferredCategory,
                cachedStream.category
            )
        }

        val epInfo = AniwatchService.getEpisodeInfo(animeName, episodeNumber) ?: return@withContext StreamFetchResult(null, false, preferredCategory, preferredCategory)
        cacheManager.cacheEpisodeInfo(key, epInfo)

        val preferredServers = if (preferredCategory == "dub") epInfo.dubServers else epInfo.subServers
        val fallbackServers = if (preferredCategory == "dub") epInfo.subServers else epInfo.dubServers
        val fallbackCategory = if (preferredCategory == "dub") "sub" else "dub"

        for (server in preferredServers) {
            val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, server.name, preferredCategory)
            if (result != null) {
                cacheManager.cacheStream(key, result)
                return@withContext StreamFetchResult(result, false, preferredCategory, preferredCategory)
            }
        }

        for (server in fallbackServers) {
            val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, server.name, fallbackCategory)
            if (result != null) {
                cacheManager.cacheStream(key, result)
                return@withContext StreamFetchResult(result, true, preferredCategory, fallbackCategory)
            }
        }

        StreamFetchResult(null, false, preferredCategory, preferredCategory)
    }

    suspend fun getStreamForServer(
        animeName: String,
        episodeNumber: Int,
        serverName: String,
        category: String,
        animeId: Int
    ): AniwatchStreamResult? = withContext(Dispatchers.IO) {
        val key = "${animeId}_${episodeNumber}_${serverName}_$category"
        cacheManager.getCachedStream(key)?.let { return@withContext it }

        val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, serverName, category)
        if (result != null) cacheManager.cacheStream(key, result)
        result
    }

    suspend fun getEpisodeInfo(
        animeName: String,
        episodeNumber: Int,
        animeId: Int,
        latestAiredEpisode: Int = Int.MAX_VALUE
    ): EpisodeStreams? {
        val key = "${animeId}_$episodeNumber"
        cacheManager.getCachedEpisodeInfo(key)?.let { return it }

        val result = AniwatchService.getEpisodeInfo(animeName, episodeNumber)
        if (result != null) cacheManager.cacheEpisodeInfo(key, result)
        return result
    }

    // ============================================
    // TMDB Operations
    // ============================================

    private val tmdbBearerToken = BuildConfig.TMDB_API_KEY
    private val episodeOffsetCache = ConcurrentHashMap<Int, Int>()

    suspend fun fetchTmdbEpisodes(
        animeTitle: String,
        animeId: Int,
        animeYear: Int? = null,
        latestAiredEpisode: Int = Int.MAX_VALUE
    ): List<TmdbEpisode> = withContext(Dispatchers.IO) {
        try {
            val baseTitle = extractBaseTitle(animeTitle)
            var searchResults = searchTmdb(baseTitle)
            if (searchResults.isEmpty()) searchResults = searchTmdb(animeTitle)
            if (searchResults.isEmpty()) return@withContext emptyList()

            val bestMatch = findBestMatch(searchResults, animeTitle, animeYear) ?: return@withContext emptyList()
            val tvDetails = fetchTvDetails(bestMatch.id) ?: return@withContext emptyList()

            val (episodeOffset, maxEpisodes) = calculateEpisodeOffset(tvDetails, animeTitle, animeId)
            fetchAllSeasons(tvDetails, episodeOffset, latestAiredEpisode, maxEpisodes)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB episodes", e)
            emptyList()
        }
    }

    private fun searchTmdb(title: String): List<TmdbSearchResult> {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val url = URL("https://api.themoviedb.org/3/search/tv?query=$encodedTitle")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
            connection.setRequestProperty("accept", "application/json")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<TmdbSearchResponse>(response).results
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun fetchTvDetails(tmdbId: Int): TmdbTvDetails? {
        return try {
            val url = URL("https://api.themoviedb.org/3/tv/$tmdbId?language=en-US")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<TmdbTvDetails>(response)
            } else null
        } catch (e: Exception) { null }
    }

    private fun fetchSeason(tvId: Int, seasonNumber: Int): TmdbSeasonDetails? {
        return try {
            val url = URL("https://api.themoviedb.org/3/tv/$tvId/season/$seasonNumber?language=en-US")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<TmdbSeasonDetails>(response)
            } else null
        } catch (e: Exception) { null }
    }

    private fun fetchAllSeasons(
        tvDetails: TmdbTvDetails,
        episodeOffset: Int,
        latestAiredEpisode: Int,
        maxEpisodes: Int = 0
    ): List<TmdbEpisode> {
        val allEpisodes = mutableListOf<TmdbEpisode>()
        var currentEpisodeNumber = 1 - episodeOffset
        var displayNumber = 1

        val sortedSeasons = tvDetails.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }

        for (season in sortedSeasons) {
            val seasonDetails = fetchSeason(tvDetails.id, season.season_number) ?: continue
            for (episode in seasonDetails.episodes) {
                if (episodeOffset == 0 && maxEpisodes > 0 && displayNumber > maxEpisodes) return allEpisodes
                
                val continuousEpisodeNum = currentEpisodeNumber + episodeOffset
                if (continuousEpisodeNum > episodeOffset) {
                    val hasAired = latestAiredEpisode == Int.MAX_VALUE || displayNumber <= latestAiredEpisode
                    val episodeTitle = if (hasAired && episode.name != null && episode.name != "Episode ${episode.episode_number}") {
                        episode.name
                    } else "Episode $displayNumber"

                    allEpisodes.add(TmdbEpisode(
                        episode = displayNumber,
                        title = episodeTitle,
                        description = if (hasAired) (episode.overview ?: "") else "",
                        image = if (hasAired) episode.still_path?.let { "https://image.tmdb.org/t/p/w500$it" } else null
                    ))
                    displayNumber++
                }
                currentEpisodeNumber++
            }
        }
        return allEpisodes
    }

    private fun findBestMatch(results: List<TmdbSearchResult>, originalTitle: String, year: Int?): TmdbSearchResult? {
        val normalizedOriginal = normalizeTitle(originalTitle)
        return results.maxByOrNull { result ->
            val name = result.name ?: result.original_name ?: ""
            val normalizedName = normalizeTitle(name)
            var score = 0
            if (normalizedName == normalizedOriginal) score += 100
            if (normalizedName.contains(normalizedOriginal) || normalizedOriginal.contains(normalizedName)) score += 50
            if (year != null) {
                val resultYear = result.first_air_date?.take(4)?.toIntOrNull()
                if (resultYear == year) score += 30
            }
            if (result.original_language == "ja") score += 20
            score
        }
    }

    private fun normalizeTitle(title: String): String = title.lowercase().replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()

    private fun extractBaseTitle(title: String): String {
        var baseTitle = title
        val suffixesToRemove = listOf(
            Regex("""\s+\d+(?:st|nd|rd|th)\s*[Ss]eason.*$"""),
            Regex("""\s+[Ss]eason\s*\d+.*$"""),
            Regex("""\s+[Pp]art\s*\d+.*$"""),
            Regex("""\s+II+$"""),
            Regex("""\s+\d+$""")
        )
        for (pattern in suffixesToRemove) baseTitle = baseTitle.replace(pattern, "")
        return baseTitle.replace(Regex("""[\s:－-]+$"""), "").trim()
    }

    private suspend fun calculateEpisodeOffset(tvDetails: TmdbTvDetails, animeTitle: String, animeId: Int): Pair<Int, Int> {
        val (offset, totalEpisodes) = fetchEpisodeOffsetFromAniwatch(animeTitle, tvDetails)
        if (offset >= 0 && totalEpisodes > 0) {
            episodeOffsetCache[animeId] = offset
            return Pair(offset, totalEpisodes)
        }
        return Pair(episodeOffsetCache[animeId] ?: 0, 0)
    }

    private suspend fun fetchEpisodeOffsetFromAniwatch(animeTitle: String, tvDetails: TmdbTvDetails): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            var result = searchAniwatchAndMatch(animeTitle, tvDetails)
            if (result.first < 0) {
                val tmdbName = tvDetails.name ?: tvDetails.original_name
                if (tmdbName != null && tmdbName != animeTitle) result = searchAniwatchAndMatch(tmdbName, tvDetails)
            }
            result
        }
    }

    private suspend fun searchAniwatchAndMatch(searchTitle: String, tvDetails: TmdbTvDetails): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedTitle = URLEncoder.encode(searchTitle, "UTF-8")
                val searchUrl = URL("https://aniwatch-cxjn.vercel.app/api/v2/hianime/search?q=$encodedTitle&page=1")
                val searchConnection = searchUrl.openConnection() as HttpsURLConnection
                if (searchConnection.responseCode != 200) return@withContext Pair(-1, 0)
                
                val searchResponse = searchConnection.inputStream.bufferedReader().readText()
                val searchJson = json.parseToJsonElement(searchResponse)
                val animes = searchJson.jsonObject["data"]?.jsonObject?.get("animes")?.jsonArray ?: return@withContext Pair(-1, 0)

                val bestMatch = animes.firstOrNull()?.jsonObject ?: return@withContext Pair(-1, 0)
                val aniwatchId = bestMatch["id"]?.jsonPrimitive?.content ?: return@withContext Pair(-1, 0)

                val episodesUrl = URL("https://aniwatch-cxjn.vercel.app/api/v2/hianime/anime/$aniwatchId/episodes")
                val episodesConnection = episodesUrl.openConnection() as HttpsURLConnection
                if (episodesConnection.responseCode != 200) return@withContext Pair(-1, 0)

                val episodesResponse = episodesConnection.inputStream.bufferedReader().readText()
                val episodesJson = json.parseToJsonElement(episodesResponse).jsonObject["data"]?.jsonObject
                val totalEpisodes = episodesJson?.get("totalEpisodes")?.jsonPrimitive?.int ?: 0
                val firstEpisodeTitle = episodesJson?.get("episodes")?.jsonArray?.firstOrNull()?.jsonObject?.get("title")?.jsonPrimitive?.content ?: return@withContext Pair(-1, 0)

                Pair(findTmdbEpisodeOffset(tvDetails, firstEpisodeTitle), totalEpisodes)
            } catch (e: Exception) { Pair(-1, 0) }
        }
    }

    private fun findTmdbEpisodeOffset(tvDetails: TmdbTvDetails, targetTitle: String): Int {
        var episodeIndex = 0
        val normalizedTarget = normalizeTitle(targetTitle)
        for (season in tvDetails.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }) {
            val seasonDetails = fetchSeason(tvDetails.id, season.season_number) ?: continue
            for (episode in seasonDetails.episodes) {
                val normalizedEpisode = normalizeTitle(episode.name ?: "")
                if (normalizedTarget == normalizedEpisode || normalizedEpisode.contains(normalizedTarget)) return episodeIndex
                episodeIndex++
            }
        }
        return -1
    }

    suspend fun fetchUserActivity(userId: Int): List<UserActivity>? {
        val query = """
            query (${'$'}userId: Int) {
                Page(page: 1, perPage: 20) {
                    activities(userId: ${'$'}userId, type: ANIME_LIST, sort: ID_DESC) {
                        ... on ListActivity {
                            createdAt
                            status
                            progress
                            media {
                                title { romaji }
                                coverImage { large }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("userId" to userId))?.let {
            try {
                val data = json.decodeFromString<SimpleActivityResponse>(it)
                data.data.Page.activities.mapIndexedNotNull { index, activity ->
                    if (activity.media != null) {
                        UserActivity(
                            id = index,
                            type = "ANIME_LIST",
                            status = activity.status ?: "",
                            progress = activity.progress,
                            createdAt = activity.createdAt,
                            mediaId = 0,
                            mediaTitle = activity.media.title.romaji ?: "Unknown",
                            mediaCover = activity.media.coverImage?.large ?: "",
                            episodes = null,
                            averageScore = null,
                            year = null
                        )
                    } else null
                }
            } catch (e: Exception) { null }
        }
    }
}
