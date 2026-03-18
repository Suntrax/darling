package com.blissless.anime.data

import com.blissless.anime.BuildConfig
import com.blissless.anime.api.AniwatchService
import com.blissless.anime.data.models.AniwatchStreamResult
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.*
import com.blissless.anime.network.GraphQLClient
import com.blissless.anime.network.GraphQLConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
            maxConcurrentRequests = 5,
            minRequestIntervalMs = 100L,
            cacheDurationMs = 5 * 60 * 1000L, // 5 minutes for public data
            userDataCacheDurationMs = 60 * 60 * 1000L // 1 hour for user data
        )
    )

    // Use longer cache for authenticated requests
    private val authCacheDuration get() = graphQLClient.getConfig().userDataCacheDurationMs

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
            cacheDurationMs = authCacheDuration, // Use longer cache for user data
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
                    relations {
                        edges {
                            relationType
                            node {
                                id
                                title { romaji english }
                                coverImage { large }
                                episodes
                                averageScore
                                format
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let {
            try {
                val data = json.decodeFromString<DetailedAnimeResponse>(it)
                data.data.Media
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun fetchAnimeRelations(animeId: Int): List<AnimeRelation>? {
        val query = GraphqlQueries.GET_ANIME_RELATIONS

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let {
            try {
                val data = json.decodeFromString<AnimeRelationsResponse>(it)
                data.data.Media.relations?.edges?.mapNotNull { edge ->
                    edge.node?.let { node ->
                        AnimeRelation(
                            id = node.id,
                            title = node.title?.english ?: node.title?.romaji ?: "Unknown",
                            cover = node.coverImage?.large ?: "",
                            episodes = node.episodes,
                            averageScore = node.averageScore,
                            format = node.format,
                            relationType = edge.relationType ?: "UNKNOWN"
                        )
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun fetchAnimeRelationsForOffset(animeId: Int): AnimeRelationsMedia? {
        val query = """
            query (${'$'}id: Int!) {
                Media(id: ${'$'}id, type: ANIME) {
                    id
                    title { romaji english }
                    episodes
                    format
                    relations {
                        edges {
                            relationType
                            node {
                                id
                                title { romaji english }
                                episodes
                                type
                                format
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let {
            try {
                json.decodeFromString<AnimeRelationsResponse>(it).data.Media
            } catch (e: Exception) { null }
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
        preferredCategory: String,
        englishTitle: String? = null
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
        animeFormat: String? = null,
        latestAiredEpisode: Int = Int.MAX_VALUE
    ): List<TmdbEpisode> = withContext(Dispatchers.IO) {
        try {
            // Detect format from title if not provided
            val detectedFormat = animeFormat ?: detectFormatFromTitle(animeTitle)
            val baseTitle = extractBaseTitle(animeTitle)
            var searchResults = searchTmdb(baseTitle, detectedFormat, animeYear)
            if (searchResults.isEmpty()) searchResults = searchTmdb(animeTitle, detectedFormat, animeYear)
            // Also try searching with year if available
            if (searchResults.isEmpty() && animeYear != null) {
                searchResults = searchTmdb("$animeTitle ${animeYear}", detectedFormat)
            }
            if (searchResults.isEmpty()) return@withContext emptyList()

            val bestMatch = findBestMatch(searchResults, animeTitle, animeYear) ?: return@withContext emptyList()
            
            // Check if this is a movie (has title field) vs TV show (has name field)
            val isMovieSearch = bestMatch.title != null
            
            if (isMovieSearch) {
                // For movies, return a single "episode" - just fetch basic info
                return@withContext listOf(TmdbEpisode(
                    episode = 1,
                    title = bestMatch.title ?: "Movie",
                    description = bestMatch.overview ?: "",
                    image = bestMatch.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                ))
            }
            
            // Continue with TV show logic
            val tvDetails = fetchTvDetails(bestMatch.id) ?: return@withContext emptyList()
            
            
            // Check if this looks like anime vs live action for Chinese titles
            val isChineseTitle = animeTitle.toCharArray().any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
            val totalEpisodes = tvDetails.number_of_episodes ?: 0
            
            // If it's a Chinese title with very few episodes (like 12), try to find one with more episodes
            if (isChineseTitle && totalEpisodes in 1..24 && searchResults.size > 1) {
                // Find result with highest ID (likely animation, higher ID = newer)
                val betterMatch = searchResults
                    .filter { it.id != bestMatch.id }
                    .maxByOrNull { it.id }
                if (betterMatch != null) {
                    val altTvDetails = fetchTvDetails(betterMatch.id)
                    if (altTvDetails != null && (altTvDetails.number_of_episodes ?: 0) > totalEpisodes) {
                        // Use offset 0 since we already picked the correct entry (Season 1)
                        val betterSortedSeasons = altTvDetails.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
                        val betterMaxEps = altTvDetails.number_of_episodes ?: 0
                        val betterAllSeasonDetails = coroutineScope {
                            betterSortedSeasons.map { season ->
                                async { fetchSeason(altTvDetails.id, season.season_number) }
                            }.awaitAll().filterNotNull()
                        }
                        
                        val result = buildEpisodesFromPool(betterAllSeasonDetails, 0, latestAiredEpisode, betterMaxEps)
                        return@withContext result
                    }
                }
            }

            // Fetch all seasons in parallel to speed up and prevent timeouts
            val sortedSeasons = tvDetails.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
            val allSeasonDetails = coroutineScope {
                sortedSeasons.map { season ->
                    async { fetchSeason(tvDetails.id, season.season_number) }
                }.awaitAll().filterNotNull()
            }

            val (episodeOffset, maxEpisodes) = calculateEpisodeOffset(tvDetails, allSeasonDetails, animeTitle, animeId, bestMatch.name, searchResults.size)

            val result = buildEpisodesFromPool(allSeasonDetails, episodeOffset, latestAiredEpisode, maxEpisodes)
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchTmdb(title: String, format: String? = null, year: Int? = null): List<TmdbSearchResult> {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            // Detect movie based on format or title patterns
            val isMovie = format == "MOVIE" || format == "OVA" || format == "ONA" || format == "SPECIAL" ||
                          title.contains("Movie", ignoreCase = true) ||
                          title.contains("OVA", ignoreCase = true) ||
                          title.contains("ONA", ignoreCase = true) ||
                          title.contains("Special", ignoreCase = true) ||
                          title.contains("Film", ignoreCase = true)
            
            val results = mutableListOf<TmdbSearchResult>()
            
            if (isMovie) {
                // Use search/movie endpoint for movies
                val movieUrl = URL("https://api.themoviedb.org/3/search/movie?query=$encodedTitle")
                val movieConnection = (movieUrl.openConnection() as HttpsURLConnection).apply {
                    readTimeout = 15000
                    connectTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
                    setRequestProperty("accept", "application/json")
                }
                if (movieConnection.responseCode == 200) {
                    val response = movieConnection.inputStream.bufferedReader().readText()
                    val searchResponse = json.decodeFromString<TmdbSearchResponse>(response)
                    results.addAll(searchResponse.results)
                    searchResponse.results.take(3).forEach { 
                    }
                } else {
                }
                movieConnection.disconnect()
                
                // If no movie results, try TV endpoint as fallback
                if (results.isEmpty()) {
                    val tvUrl = URL("https://api.themoviedb.org/3/search/tv?query=$encodedTitle")
                    val tvConnection = (tvUrl.openConnection() as HttpsURLConnection).apply {
                        readTimeout = 15000
                        connectTimeout = 15000
                        requestMethod = "GET"
                        setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
                        setRequestProperty("accept", "application/json")
                    }
                    if (tvConnection.responseCode == 200) {
                        val response = tvConnection.inputStream.bufferedReader().readText()
                        results.addAll(json.decodeFromString<TmdbSearchResponse>(response).results)
                    }
                    tvConnection.disconnect()
                }
            } else {
                // Use search/tv endpoint for TV series - this searches by title properly
                val tvUrl = URL("https://api.themoviedb.org/3/search/tv?query=$encodedTitle")
                val tvConnection = (tvUrl.openConnection() as HttpsURLConnection).apply {
                    readTimeout = 15000
                    connectTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
                    setRequestProperty("accept", "application/json")
                }
                if (tvConnection.responseCode == 200) {
                    val response = tvConnection.inputStream.bufferedReader().readText()
                    results.addAll(json.decodeFromString<TmdbSearchResponse>(response).results)
                }
                tvConnection.disconnect()
                
                // If no TV results, try movie endpoint as fallback
                if (results.isEmpty()) {
                    val movieUrl = URL("https://api.themoviedb.org/3/search/movie?query=$encodedTitle")
                    val movieConnection = (movieUrl.openConnection() as HttpsURLConnection).apply {
                        readTimeout = 15000
                        connectTimeout = 15000
                        requestMethod = "GET"
                        setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
                        setRequestProperty("accept", "application/json")
                    }
                    if (movieConnection.responseCode == 200) {
                        val response = movieConnection.inputStream.bufferedReader().readText()
                        results.addAll(json.decodeFromString<TmdbSearchResponse>(response).results)
                    }
                    movieConnection.disconnect()
                }
            }
            
            results
        } catch (e: Exception) { 
            emptyList() 
        }
    }

    private fun fetchTvDetails(tmdbId: Int): TmdbTvDetails? {
        return try {
            val url = URL("https://api.themoviedb.org/3/tv/$tmdbId?language=en-US")
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                readTimeout = 15000
                connectTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<TmdbTvDetails>(response)
            } else null
        } catch (e: Exception) { null }
    }

    private suspend fun fetchSeason(tvId: Int, seasonNumber: Int): TmdbSeasonDetails? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.themoviedb.org/3/tv/$tvId/season/$seasonNumber?language=en-US")
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                readTimeout = 15000
                connectTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<TmdbSeasonDetails>(response)
            } else null
        } catch (e: Exception) { null }
    }

    private fun buildEpisodesFromPool(
        allSeasonDetails: List<TmdbSeasonDetails>,
        episodeOffset: Int,
        latestAiredEpisode: Int,
        maxEpisodes: Int
    ): List<TmdbEpisode> {
        val allEpisodes = mutableListOf<TmdbEpisode>()
        var absoluteIndex = 1

        for (season in allSeasonDetails) {
            for (episode in season.episodes) {
                val isTarget = if (maxEpisodes > 0) {
                    absoluteIndex > episodeOffset && absoluteIndex <= (episodeOffset + maxEpisodes)
                } else {
                    absoluteIndex > episodeOffset
                }

                if (isTarget) {
                    val relativeNum = absoluteIndex - episodeOffset
                    val hasAired = latestAiredEpisode == Int.MAX_VALUE || relativeNum <= latestAiredEpisode

                    val title = if (hasAired && episode.name != null && !episode.name.startsWith("Episode", ignoreCase = true)) {
                        episode.name
                    } else "Episode $relativeNum"

                    allEpisodes.add(TmdbEpisode(
                        episode = relativeNum,
                        title = title,
                        description = if (hasAired) (episode.overview ?: "") else "",
                        image = if (hasAired) episode.still_path?.let { "https://image.tmdb.org/t/p/w500$it" } else null
                    ))
                }
                absoluteIndex++
            }
        }
        return allEpisodes
    }

    private suspend fun calculateEpisodeOffset(
        tvDetails: TmdbTvDetails,
        allSeasonDetails: List<TmdbSeasonDetails>,
        animeTitle: String,
        animeId: Int,
        tmdbName: String?,
        tmdbResultsCount: Int = 1
    ): Pair<Int, Int> {
        
        // If TMDB name exactly matches the original title, skip Aniwatch fallback and use offset 0
        val normalizedOriginal = normalizeTitle(animeTitle)
        val normalizedTmdbName = normalizeTitle(tmdbName ?: "")
        if (normalizedTmdbName == normalizedOriginal) {
            return Pair(0, tvDetails.number_of_episodes ?: 0)
        }
        
        // If there were multiple TMDB results, assume the best match (highest ID) is correct
        if (tmdbResultsCount > 1) {
            return Pair(0, tvDetails.number_of_episodes ?: 0)
        }

        // 1. Recursive AniList search (Most reliable for multi-season shows)
        val recursiveOffset = calculateRecursiveOffset(animeId)
        val aniListMedia = fetchAnimeRelationsForOffset(animeId)
        val totalEps = aniListMedia?.episodes ?: 0

        if (recursiveOffset > 0) {
            return Pair(recursiveOffset, totalEps)
        }

        // 2. Title matching via Aniwatch first episode title
        val (aniwatchOffset, hianimeCount) = fetchEpisodeOffsetFromAniwatch(animeTitle, allSeasonDetails)
        if (aniwatchOffset >= 0) {
            return Pair(aniwatchOffset, if (totalEps > 0) totalEps else hianimeCount)
        }

        return Pair(0, totalEps)
    }

    private val visitedOffsetIds = mutableSetOf<Int>()

    private suspend fun calculateRecursiveOffset(animeId: Int): Int {
        visitedOffsetIds.clear()
        val offset = getPrequelEpisodesSum(animeId)
        return offset
    }

    private suspend fun getPrequelEpisodesSum(animeId: Int): Int {
        if (visitedOffsetIds.contains(animeId)) return 0
        visitedOffsetIds.add(animeId)

        val media = fetchAnimeRelationsForOffset(animeId) ?: return 0

        // Find ALL PREQUEL relations.
        val prequels = media.relations?.edges?.filter {
            it.relationType == "PREQUEL" && it.node.type == "ANIME"
        } ?: emptyList()

        var totalOffset = 0
        for (edge in prequels) {
            val node = edge.node
            // Only add episodes for Series formats (TV, ONA, TV_SHORT)
            // But ALWAYS recurse, even into Movies/Specials, to find older seasons
            val isSeriesFormat = node.format == "TV" || node.format == "ONA" || node.format == "TV_SHORT"
            val episodes = if (isSeriesFormat) (node.episodes ?: 0) else 0

            if (episodes > 0) {
            } else {
            }

            totalOffset += episodes + getPrequelEpisodesSum(node.id)
        }

        return totalOffset
    }

    suspend fun fetchAnimeRelationsList(animeId: Int): List<AnimeRelation>? {
        val query = GraphqlQueries.GET_ANIME_RELATIONS

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let {
            try {
                val data = json.decodeFromString<AnimeRelationsResponse>(it)
                data.data.Media.relations?.edges?.mapNotNull { edge ->
                    edge.node?.let { node ->
                        AnimeRelation(
                            id = node.id,
                            title = node.title?.english ?: node.title?.romaji ?: "Unknown",
                            cover = node.coverImage?.large ?: "",
                            episodes = node.episodes,
                            averageScore = node.averageScore,
                            format = node.format,
                            relationType = edge.relationType ?: "UNKNOWN"
                        )
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun fetchEpisodeOffsetFromAniwatch(
        animeTitle: String,
        allSeasonDetails: List<TmdbSeasonDetails>
    ): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedTitle = URLEncoder.encode(animeTitle, "UTF-8")
                val url = URL("https://aniwatch-cxjn.vercel.app/api/v2/hianime/search?q=$encodedTitle&page=1")
                val connection = (url.openConnection() as HttpsURLConnection).apply {
                    readTimeout = 15000
                    connectTimeout = 15000
                }
                if (connection.responseCode != 200) return@withContext Pair(-1, 0)

                val response = connection.inputStream.bufferedReader().readText()
                val searchJson = json.parseToJsonElement(response)
                val animes = searchJson.jsonObject["data"]?.jsonObject?.get("animes")?.jsonArray ?: return@withContext Pair(-1, 0)

                val bestMatch = animes.firstOrNull()?.jsonObject ?: return@withContext Pair(-1, 0)
                val aniwatchId = bestMatch["id"]?.jsonPrimitive?.content ?: return@withContext Pair(-1, 0)

                val episodesUrl = URL("https://aniwatch-cxjn.vercel.app/api/v2/hianime/anime/$aniwatchId/episodes")
                val epConnection = (episodesUrl.openConnection() as HttpsURLConnection).apply {
                    readTimeout = 15000
                    connectTimeout = 15000
                }
                if (epConnection.responseCode != 200) return@withContext Pair(-1, 0)

                val epResponse = epConnection.inputStream.bufferedReader().readText()
                val epJson = json.parseToJsonElement(epResponse).jsonObject["data"]?.jsonObject
                val totalEps = epJson?.get("totalEpisodes")?.jsonPrimitive?.int ?: 0
                val firstEpTitle = epJson?.get("episodes")?.jsonArray?.firstOrNull()?.jsonObject?.get("title")?.jsonPrimitive?.content ?: return@withContext Pair(-1, 0)

                Pair(findTmdbEpisodeOffsetByTitle(allSeasonDetails, firstEpTitle), totalEps)
            } catch (e: Exception) { Pair(-1, 0) }
        }
    }

    private fun findTmdbEpisodeOffsetByTitle(allSeasonDetails: List<TmdbSeasonDetails>, targetTitle: String): Int {
        val normalizedTarget = normalizeTitle(targetTitle)
        if (normalizedTarget.startsWith("episode") && normalizedTarget.length < 12) return -1

        var absoluteIndex = 0
        for (season in allSeasonDetails) {
            for (episode in season.episodes) {
                val normalizedEpisode = normalizeTitle(episode.name ?: "")
                if (normalizedTarget == normalizedEpisode || (normalizedEpisode.isNotEmpty() && normalizedTarget.contains(normalizedEpisode))) {
                    return absoluteIndex
                }
                absoluteIndex++
            }
        }
        return -1
    }

    private fun findBestMatch(results: List<TmdbSearchResult>, originalTitle: String, year: Int?): TmdbSearchResult? {
        val normalizedOriginal = normalizeTitle(originalTitle)
        
        // Check if original title might be Chinese (contains CJK characters)
        val isChineseTitle = originalTitle.toCharArray().any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        
        // First, try a quick match without fetching genres
        val quickMatch = results.maxByOrNull { result ->
            val name = result.name ?: result.title ?: result.original_name ?: ""
            val normalizedName = normalizeTitle(name)
            var score = 0
            
            // Skip invalid results
            if (name.isEmpty() || name.length < 3) {
                return@maxByOrNull -1000
            }
            
            // Skip Western cartoons for anime searches
            val lowerName = name.lowercase()
            if (lowerName == "family guy" || lowerName == "the simpsons" || lowerName == "american dad") {
                return@maxByOrNull -500
            }
            
            // Exact match - highest priority
            if (normalizedName == normalizedOriginal) score += 500
            
            // When names are equal, prefer higher ID
            if (normalizedName == normalizedOriginal) {
                score += result.id / 1000
            }
            
            // Partial match
            if (normalizedName.length > 2 && normalizedOriginal.length > 2) {
                if (normalizedOriginal in normalizedName || normalizedName in normalizedOriginal) {
                    score += 100
                }
            }
            
            score
        }
        
        // If we have only one result, use it
        if (results.size == 1) {
            return quickMatch
        }
        
        // Check if there are multiple exact matches (need to differentiate by genre)
        val exactMatches = results.filter { result ->
            val name = result.name ?: result.title ?: ""
            normalizeTitle(name) == normalizedOriginal
        }
        
        // If there's exactly one exact match, use it
        if (exactMatches.size == 1) {
            return exactMatches.first()
        }
        
        // If there are multiple exact matches (like Bartender anime vs live action),
        // or no exact match at all, fetch genres to differentiate
        if (results.size > 1) {
            // Fetch details for each result to check genres - do this in parallel
            val resultsWithGenres = results.mapNotNull { result ->
                val details = fetchTvDetails(result.id)
                if (details != null) {
                    result to details
                } else null
            }
            
            // Check if any result has Animation genre
            val animationResults = resultsWithGenres.filter { (_, details) ->
                details.genres.any { it.name == "Animation" }
            }
            
            if (animationResults.isNotEmpty()) {
                // Prefer animation result that matches the original title best
                return animationResults.maxByOrNull { (result, _) ->
                    val name = result.name ?: result.title ?: ""
                    val normalizedName = normalizeTitle(name)
                    var score = 0
                    if (normalizedName == normalizedOriginal) score += 500
                    score += result.id / 1000
                    score
                }?.first
            }
            
            // For Chinese titles, also try higher ID as fallback
            if (isChineseTitle) {
                return results.maxByOrNull { it.id }
            }
        }
        
        return quickMatch
    }

    private fun normalizeTitle(title: String): String = title.lowercase().replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()

    private fun detectFormatFromTitle(title: String): String? {
        val lowerTitle = title.lowercase()
        return when {
            lowerTitle.contains("movie") || lowerTitle.contains("film") -> "MOVIE"
            lowerTitle.contains("ova") -> "OVA"
            lowerTitle.contains("ona") -> "ONA"
            lowerTitle.contains("special") -> "SPECIAL"
            lowerTitle.contains("season") -> "TV"
            else -> null
        }
    }

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
