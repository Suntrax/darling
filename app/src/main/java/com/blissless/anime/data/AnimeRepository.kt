package com.blissless.anime.data

import com.blissless.anime.BuildConfig
import com.blissless.anime.api.miruro.MiruroService
import com.blissless.anime.api.miruro.MiruroEpisodeInfo
import com.blissless.anime.stream.scrapers.aniwatch.AniwatchService
import com.blissless.anime.data.models.AiringScheduleEntry
import com.blissless.anime.data.models.AiringScheduleResponse
import com.blissless.anime.data.models.AllCharactersResponse
import com.blissless.anime.data.models.AllStaffResponse
import com.blissless.anime.data.models.AnimeRelation
import com.blissless.anime.data.models.AnimeRelationsMedia
import com.blissless.anime.data.models.AnimeRelationsResponse
import com.blissless.anime.data.models.AniwatchStreamResult
import com.blissless.anime.data.models.BatchedExploreResponse
import com.blissless.anime.data.models.CharacterData
import com.blissless.anime.data.models.CharacterResponse
import com.blissless.anime.data.models.DetailedAnimeMedia
import com.blissless.anime.data.models.DetailedAnimeResponse
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.ExploreMedia
import com.blissless.anime.data.models.ExploreResponse
import com.blissless.anime.data.models.MediaListResponse
import com.blissless.anime.data.models.SimpleActivityResponse
import com.blissless.anime.data.models.StaffData
import com.blissless.anime.data.models.StaffResponse
import com.blissless.anime.data.models.StreamFetchResult
import com.blissless.anime.data.models.TmdbEpisode
import com.blissless.anime.data.models.TmdbSearchResponse
import com.blissless.anime.data.models.TmdbSearchResult
import com.blissless.anime.data.models.TmdbSeasonDetails
import com.blissless.anime.data.models.TmdbTvDetails
import com.blissless.anime.data.models.UserActivity
import com.blissless.anime.data.models.UserFavoritesResponse
import com.blissless.anime.data.models.UserStatsResponse
import com.blissless.anime.data.models.ViewerResponse
import com.blissless.anime.network.GraphQLClient
import com.blissless.anime.network.GraphQLConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

        // Use all available providers (validated by HTTP check in MiruroService)
        val PRIORITY_PROVIDERS = emptyList<String>()
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
            cacheDurationMs = 60 * 60 * 1000L, // 1 hour for public data
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

    suspend fun graphqlMutation(query: String, variables: Map<String, Any?>): String? {
        val token = userPreferences.authToken.value ?: return null

        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = true,
            authToken = token,
            clientIds = CLIENT_IDS,
            useCache = false, // Mutations should never be cached
            parser = { it }
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
            cacheDurationMs = 60 * 60 * 1000L, // 1 hour for public data
            parser = { it }
        )

        if (result.error != null) {
            android.util.Log.e("REPO_DEBUG", "GraphQL error: ${result.error}")
        }
        if (result.data == null) {
            android.util.Log.e("REPO_DEBUG", "GraphQL returned no data")
        }
        
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
                    about
                    avatar { medium large }
                    bannerImage
                    siteUrl
                    createdAt
                    statistics {
                        anime {
                            count
                            episodesWatched
                            minutesWatched
                            meanScore
                        }
                    }
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

    suspend fun fetchUserStats(userId: Int): UserStatsResponse? {
        val query = """
            query (${'$'}userId: Int) {
                User(id: ${'$'}userId) {
                    statistics {
                        anime {
                            count
                            episodesWatched
                            minutesWatched
                            meanScore
                        }
                    }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("userId" to userId))?.let {
            try {
                json.decodeFromString<UserStatsResponse>(it)
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
                                coverImage { extraLarge }
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
                        coverImage { extraLarge }
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
                        coverImage { extraLarge }
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
                        coverImage { extraLarge }
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
                        coverImage { extraLarge }
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
                action: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Action", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                romance: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Romance", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                comedy: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Comedy", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                fantasy: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Fantasy", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                scifi: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Sci-Fi", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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

    data class ExploreResult(val response: BatchedExploreResponse?, val error: String?)

    suspend fun fetchBatchedExploreWithError(useCache: Boolean = true): ExploreResult {
        val query = """
            query {
                featured: Page(page: 1, perPage: 10) {
                    media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                        coverImage { extraLarge }
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
                        coverImage { extraLarge }
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
                        coverImage { extraLarge }
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
                action: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Action", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                romance: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Romance", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                comedy: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Comedy", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                fantasy: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Fantasy", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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
                scifi: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Sci-Fi", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
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

        val rawResult = publicGraphqlRequestWithError(query, emptyMap(), useCache)
        return if (rawResult.data != null) {
            try {
                val response = json.decodeFromString<BatchedExploreResponse>(rawResult.data)
                ExploreResult(response, null)
            } catch (e: Exception) {
                ExploreResult(null, "JSON parse error: ${e.message}")
            }
        } else {
            ExploreResult(null, rawResult.error ?: "Unknown error")
        }
    }

    suspend fun publicGraphqlRequestWithError(query: String, variables: Map<String, Any?> = emptyMap(), useCache: Boolean = true): PublicGraphqlResult {
        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = false,
            clientIds = CLIENT_IDS,
            useCache = useCache,
            parser = { it }
        )

        return if (result.data != null) {
            PublicGraphqlResult(result.data, null)
        } else {
            PublicGraphqlResult(null, result.error?.message ?: "Unknown GraphQL error")
        }
    }

    data class PublicGraphqlResult(val data: String?, val error: String?)

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
                            coverImage { extraLarge }
                            episodes
                            status
                            averageScore
                            genres
                            seasonYear
                            isAdult
                        }
                    }
                }
            }
        """.trimIndent()

        val allSchedules = mutableListOf<AiringScheduleEntry>()
        var page = 1
        var hasMore = true

        while (hasMore && page <= 5) {
            val result = publicGraphqlRequestWithError(
                query,
                mapOf("page" to page, "startTime" to startTime, "endTime" to endTime)
            )

            if (result.data == null) {
                break
            }

            try {
                val data = json.decodeFromString<AiringScheduleResponse>(result.data)
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
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        isAdult
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
    
    suspend fun findAnimeByMalId(malId: Int): ExploreMedia? {
        val query = """
            query (${'$'}malId: Int) {
                Page(page: 1, perPage: 1) {
                    media(type: ANIME, idMal: ${'$'}malId) {
                        id
                        idMal
                        title { romaji english native }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        isAdult
                        startDate { year }
                        format
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("malId" to malId))?.let {
            try {
                val data = json.decodeFromString<ExploreResponse>(it)
                data.data.Page.media.firstOrNull()
            } catch (e: Exception) {
                android.util.Log.e("ANILIST_DEBUG", "Error finding anime by MAL ID: ${e.message}")
                null
            }
        }
    }

    // ============================================
    // Detailed Anime
    // ============================================

    suspend fun fetchDetailedAnime(animeId: Int): DetailedAnimeMedia? {
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id, type: ANIME) {
                    id
                    idMal
                    title { romaji english native }
                    coverImage { extraLarge }
                    bannerImage
                    description(asHtml: false)
                    episodes
                    duration
                    status
                    averageScore
                    popularity
                    favourites
                    genres
                    tags {
                        name
                        rank
                        isMediaSpoiler
                        description
                    }
                    season
                    seasonYear
                    format
                    source
                    studios(isMain: true) { nodes { id name } }
                    startDate { year month day }
                    endDate { year month day }
                    nextAiringEpisode { episode airingAt }
                    isAdult
                    characters(perPage: 10) {
                        nodes {
                            id
                            name { full }
                            image { large }
                        }
                    }
                    trailer {
                        id
                        site
                    }
                    staff(perPage: 10) {
                        edges {
                            node {
                                id
                                name { full }
                                image { large }
                            }
                            role
                        }
                    }
                    relations {
                        edges {
                            relationType
                            node {
                                id
                                title { romaji english }
                                coverImage { extraLarge }
                                episodes
                                averageScore
                                format
                                nextAiringEpisode { episode }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        android.util.Log.d("REPO_DEBUG", "Executing fetchDetailedAnime for id=$animeId")
        
        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let { response ->
            android.util.Log.d("REPO_DEBUG", "Raw response length: ${response.length}")
            try {
                val data = json.decodeFromString<DetailedAnimeResponse>(response)
                android.util.Log.d("REPO_DEBUG", "Parsed detailed anime: id=${data.data.Media?.id}, title=${data.data.Media?.title?.romaji}")
                data.data.Media
            } catch (e: Exception) {
                android.util.Log.e("REPO_DEBUG", "Failed to parse detailed anime: ${e.message}")
                android.util.Log.e("REPO_DEBUG", "Response preview: ${response.take(500)}")
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
                            cover = node.coverImage?.extraLarge ?: "",
                            episodes = node.episodes,
                            latestEpisode = node.nextAiringEpisode?.episode?.let { it - 1 },
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
                    nextAiringEpisode { episode }
                    relations {
                        edges {
                            relationType
                            node {
                                id
                                title { romaji english }
                                episodes
                                type
                                format
                                nextAiringEpisode { episode }
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

    suspend fun fetchCharacter(characterId: Int): CharacterData? {
        val query = """
            query (${'$'}id: Int!) {
                Character(id: ${'$'}id) {
                    id
                    name { full native }
                    image { large medium }
                    description(asHtml: false)
                    anime: media(perPage: 10, sort: POPULARITY_DESC) {
                        nodes {
                            id
                            title { romaji english }
                            coverImage { extraLarge }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to characterId))?.let { response ->
            try {
                val data = json.decodeFromString<CharacterResponse>(response)
                data.data.Character
            } catch (e: Exception) {
                android.util.Log.e("REPO_DEBUG", "Failed to parse character: ${e.message}")
                null
            }
        }
    }

    suspend fun fetchStaff(staffId: Int): StaffData? {
        val query = """
            query (${'$'}id: Int!) {
                Staff(id: ${'$'}id) {
                    id
                    name { full native }
                    image { large medium }
                    description(asHtml: false)
                    anime: staffMedia(perPage: 10, sort: POPULARITY_DESC) {
                        nodes {
                            id
                            title { romaji english }
                            coverImage { extraLarge }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to staffId))?.let { response ->
            try {
                val data = json.decodeFromString<StaffResponse>(response)
                data.data.Staff
            } catch (e: Exception) {
                android.util.Log.e("REPO_DEBUG", "Failed to parse staff: ${e.message}")
                null
            }
        }
    }

    suspend fun fetchAllCharacters(animeId: Int): List<CharacterData>? {
        val query = """
            query (${'$'}id: Int!) {
                Media(id: ${'$'}id, type: ANIME) {
                    characters(perPage: 50) {
                        nodes {
                            id
                            name { full native }
                            image { large medium }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let { response ->
            try {
                val data = json.decodeFromString<AllCharactersResponse>(response)
                val characters = data.data.Media?.characters?.nodes
                android.util.Log.d("REPO_DEBUG", "fetchAllCharacters: got ${characters?.size ?: 0} characters")
                characters?.distinctBy { it.id }
            } catch (e: Exception) {
                android.util.Log.e("REPO_DEBUG", "Failed to parse all characters: ${e.message}")
                null
            }
        }
    }

    suspend fun fetchAllStaff(animeId: Int): List<StaffData>? {
        val query = """
            query (${'$'}id: Int!) {
                Media(id: ${'$'}id, type: ANIME) {
                    staff(perPage: 50) {
                        nodes {
                            id
                            name { full native }
                            image { large }
                            primaryOccupations
                        }
                    }
                }
            }
        """.trimIndent()

        val response = publicGraphqlRequest(query, mapOf("id" to animeId))
        return response?.let {
            try {
                val data = json.decodeFromString<AllStaffResponse>(it)
                val staff = data.data.Media?.staff?.nodes
                staff?.distinctBy { it.id }
            } catch (e: Exception) {
                android.util.Log.e("REPO_DEBUG", "Failed to parse all staff: ${e.message}")
                null
            }
        }
    }

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

    typealias ServerAttemptCallback = (serverName: String, category: String, isFallback: Boolean, failed: Boolean) -> Unit

    suspend fun tryAllServersWithFallback(
        animeName: String,
        episodeNumber: Int,
        animeId: Int,
        latestAiredEpisode: Int = Int.MAX_VALUE,
        preferredCategory: String,
        englishTitle: String? = null,
        onServerAttempt: (String, String, Boolean) -> Unit = { _, _, _ -> }
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

        val subServers = epInfo.subServers
        val dubServers = epInfo.dubServers

        // Filter to only priority providers (arc, zoro, kiwi) and sort by priority order
        val prioritySubServers = subServers
            .filter { PRIORITY_PROVIDERS.contains(it.name) }
            .sortedBy { server -> PRIORITY_PROVIDERS.indexOf(server.name).let { if (it >= 0) it else Int.MAX_VALUE } }

        val priorityDubServers = dubServers
            .filter { PRIORITY_PROVIDERS.contains(it.name) }
            .sortedBy { server -> PRIORITY_PROVIDERS.indexOf(server.name).let { if (it >= 0) it else Int.MAX_VALUE } }

        // Also get fallback servers (non-priority) for backup
        val fallbackSubServers = subServers.filter { !PRIORITY_PROVIDERS.contains(it.name) }
        val fallbackDubServers = dubServers.filter { !PRIORITY_PROVIDERS.contains(it.name) }

        val preferredServers = if (preferredCategory == "dub") priorityDubServers else prioritySubServers
        val fallbackServers = if (preferredCategory == "dub") prioritySubServers else priorityDubServers
        val fallbackCategory = if (preferredCategory == "dub") "sub" else "dub"

        android.util.Log.d("REPO_DEBUG", "=== REPO tryAllServersWithFallback (Priority: arc, zoro, kiwi) ===")
        android.util.Log.d("REPO_DEBUG", "preferredCategory=$preferredCategory")
        android.util.Log.d("REPO_DEBUG", "prioritySubServers (${prioritySubServers.size}): ${prioritySubServers.joinToString { it.name }}")
        android.util.Log.d("REPO_DEBUG", "priorityDubServers (${priorityDubServers.size}): ${priorityDubServers.joinToString { it.name }}")

        // Try priority providers first (arc, zoro, kiwi in order)
        for ((i, server) in prioritySubServers.withIndex()) {
            android.util.Log.d("REPO_DEBUG", "Trying priority sub server ${i+1}: ${server.name}")
            onServerAttempt(server.name, "sub", false)
            val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, server.name, "sub")
            if (result != null) {
                cacheManager.cacheStream(key, result)
                return@withContext StreamFetchResult(result, false, preferredCategory, "sub")
            }
        }

        // Try priority dub servers
        for ((i, server) in priorityDubServers.withIndex()) {
            android.util.Log.d("REPO_DEBUG", "Trying priority dub server ${i+1}: ${server.name}")
            onServerAttempt(server.name, "dub", false)
            val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, server.name, "dub")
            if (result != null) {
                cacheManager.cacheStream(key, result)
                return@withContext StreamFetchResult(result, preferredCategory == "dub", preferredCategory, "dub")
            }
        }

        // Then try fallback category with priority providers
        for ((i, server) in fallbackServers.withIndex()) {
            android.util.Log.d("REPO_DEBUG", "Trying fallback category server ${i+1}: ${server.name}")
            onServerAttempt(server.name, fallbackCategory, true)
            val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, server.name, fallbackCategory)
            if (result != null) {
                cacheManager.cacheStream(key, result)
                return@withContext StreamFetchResult(result, true, preferredCategory, fallbackCategory)
            }
        }

        // Try non-priority (fallback) providers as last resort
        val allFallbackServers = if (preferredCategory == "dub") fallbackDubServers else fallbackSubServers
        for ((i, server) in allFallbackServers.withIndex()) {
            android.util.Log.d("REPO_DEBUG", "Trying fallback provider ${i+1}: ${server.name}")
            onServerAttempt(server.name, preferredCategory, true)
            val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, server.name, preferredCategory)
            if (result != null) {
                cacheManager.cacheStream(key, result)
                return@withContext StreamFetchResult(result, true, preferredCategory, preferredCategory)
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
    // Miruro Operations (Episode Details)
    // ============================================

    private val miruroService = MiruroService

    suspend fun fetchMiruroEpisodes(
        animeId: Int,
        latestAiredEpisode: Int = Int.MAX_VALUE
    ): List<TmdbEpisode> = withContext(Dispatchers.IO) {
        try {
            val miruroEpisodes = miruroService.getAnimeEpisodes(animeId) ?: return@withContext emptyList()
            
            val episodes = miruroEpisodes.episodes.map { miruroEp: MiruroEpisodeInfo ->
                val hasAired = latestAiredEpisode == Int.MAX_VALUE || miruroEp.number <= latestAiredEpisode
                
                TmdbEpisode(
                    episode = miruroEp.number,
                    title = miruroEp.title ?: "Episode ${miruroEp.number}",
                    description = if (hasAired) miruroEp.description ?: "" else "Not yet aired",
                    image = miruroEp.image
                )
            }
            
            episodes
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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

        // First, collect all episodes from TMDB
        val tmdbEpisodes = mutableListOf<Triple<Int, String?, String?>>() // (relativeNum, title, description)
        
        for (season in allSeasonDetails) {
            for (episode in season.episodes) {
                val isTarget = if (maxEpisodes > 0) {
                    absoluteIndex > episodeOffset && absoluteIndex <= (episodeOffset + maxEpisodes)
                } else {
                    absoluteIndex > episodeOffset
                }

                if (isTarget) {
                    val relativeNum = absoluteIndex - episodeOffset
                    val title = if (episode.name != null && !episode.name.startsWith("Episode", ignoreCase = true)) {
                        episode.name
                    } else null
                    
                    tmdbEpisodes.add(Triple(relativeNum, title, episode.overview))
                }
                absoluteIndex++
            }
        }
        
        // Calculate how many episodes TMDB returned
        val tmdbEpisodeCount = tmdbEpisodes.size
        val expectedEpisodeCount = if (maxEpisodes > 0) maxEpisodes else tmdbEpisodeCount
        
        // Add TMDB episodes with proper airing status
        for ((relativeNum, title, description) in tmdbEpisodes) {
            val hasAired = latestAiredEpisode == Int.MAX_VALUE || relativeNum <= latestAiredEpisode
            
            allEpisodes.add(TmdbEpisode(
                episode = relativeNum,
                title = title ?: "Episode $relativeNum",
                description = if (hasAired) (description ?: "") else "",
                image = null // TMDB episode images are not stored to keep memory low
            ))
        }
        
        // If TMDB doesn't have enough episodes, generate placeholders for long-running series
        if (tmdbEpisodeCount < expectedEpisodeCount && maxEpisodes > 0) {
            val startEpisode = tmdbEpisodeCount + 1
            val endEpisode = expectedEpisodeCount
            
            for (epNum in startEpisode..endEpisode) {
                val relativeNum = epNum
                val hasAired = latestAiredEpisode == Int.MAX_VALUE || relativeNum <= latestAiredEpisode
                
                allEpisodes.add(TmdbEpisode(
                    episode = relativeNum,
                    title = "Episode $relativeNum",
                    description = if (hasAired) "" else "Not yet aired",
                    image = null
                ))
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
        
        // Always fetch AniList episode count first - it's the most reliable for anime
        val recursiveOffset = calculateRecursiveOffset(animeId)
        val aniListMedia = fetchAnimeRelationsForOffset(animeId)
        val totalEps = aniListMedia?.episodes ?: 0
        
        // If AniList has episode count, use it (more reliable for long-running anime like Detective Conan)
        if (totalEps > 0) {
            return Pair(recursiveOffset, totalEps)
        }
        
        // Fallback to TMDB only if AniList doesn't have episode count
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

        // Title matching via Aniwatch first episode title
        val (aniwatchOffset, hianimeCount) = fetchEpisodeOffsetFromAniwatch(animeTitle, allSeasonDetails)
        if (aniwatchOffset >= 0) {
            return Pair(aniwatchOffset, if (hianimeCount > 0) hianimeCount else (tvDetails.number_of_episodes ?: 0))
        }

        return Pair(0, tvDetails.number_of_episodes ?: 0)
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
                            cover = node.coverImage?.extraLarge ?: "",
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

    suspend fun fetchUserActivity(userId: Int, perPage: Int = 50): List<UserActivity>? {
        val query = """
            query (${'$'}userId: Int) {
                Page(page: 1, perPage: $perPage) {
                    activities(userId: ${'$'}userId, type: ANIME_LIST, sort: ID_DESC) {
                        ... on ListActivity {
                            createdAt
                            status
                            progress
                            media {
                                id
                                title { romaji english }
                                coverImage { extraLarge }
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
                            mediaId = activity.media?.id ?: 0,
                            mediaTitle = activity.media?.title?.romaji ?: activity.media?.title?.english ?: "Unknown",
                            mediaTitleEnglish = activity.media?.title?.english,
                            mediaCover = activity.media?.coverImage?.extraLarge ?: "",
                            episodes = null,
                            averageScore = null,
                            year = null
                        )
                    } else null
                }
            } catch (e: Exception) { null }
        }
    }

    suspend fun fetchUserFavorites(userId: Int): UserFavoritesResponse? {
        val query = """
            query (${'$'}userId: Int) {
                User(id: ${'$'}userId) {
                    favourites {
                        anime(page: 1, perPage: 30) {
                            nodes {
                                id
                                title { romaji english }
                                coverImage { extraLarge }
                                episodes
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
                val response = json.decodeFromString<UserFavoritesResponse>(it)
                android.util.Log.d("REPO_DEBUG", "UserFavorites response: ${response.data.User.favourites.anime.nodes.size} items")
                response
            } catch (e: Exception) {
                android.util.Log.e("REPO_DEBUG", "Failed to parse user favorites: ${e.message}")
                null
            }
        }
    }

    suspend fun toggleAniListFavorite(mediaId: Int): Boolean {
        graphQLClient.clearCache() // Clear cache to ensure fresh data
        
        val mutation = """
            mutation (${'$'}mediaId: Int) {
                ToggleFavourite(animeId: ${'$'}mediaId) {
                    anime { nodes { id } }
                }
            }
        """.trimIndent()

        val response = graphqlMutation(mutation, mapOf("mediaId" to mediaId))
        return response != null && response.isNotEmpty()
    }
    
    suspend fun addAniListFavorite(mediaId: Int): Boolean {
        android.util.Log.d("AniListFavorite", "addAniListFavorite called: mediaId=$mediaId")
        
        val mutation = """
            mutation (${'$'}mediaId: Int) {
                ToggleFavourite(animeId: ${'$'}mediaId) {
                    anime { nodes { id } }
                }
            }
        """.trimIndent()
        
        // Check if already favorited first
        val checkQuery = """
            query (${'$'}mediaId: Int) {
                Media(id: ${'$'}mediaId) {
                    id
                    isFavourite
                }
            }
        """.trimIndent()
        
        val result = graphqlRequest(checkQuery, mapOf("mediaId" to mediaId))
        if (result?.contains("\"isFavourite\":true") == true || result?.contains("\"isFavourite\": true") == true) {
            android.util.Log.d("AniListFavorite", "  Already favorited, skipping")
            return true // Already favorited
        }
        
        android.util.Log.d("AniListFavorite", "  Executing ToggleFavourite mutation")
        val success = graphqlMutation(mutation, mapOf("mediaId" to mediaId)) != null
        android.util.Log.d("AniListFavorite", "  Mutation result: $success")
        return success
    }
    
    suspend fun removeAniListFavorite(mediaId: Int): Boolean {
        android.util.Log.d("AniListFavorite", "removeAniListFavorite called: mediaId=$mediaId")
        
        val mutation = """
            mutation (${'$'}mediaId: Int) {
                ToggleFavourite(animeId: ${'$'}mediaId) {
                    anime { nodes { id } }
                }
            }
        """.trimIndent()
        
        // Check if not favorited first
        val checkQuery = """
            query (${'$'}mediaId: Int) {
                Media(id: ${'$'}mediaId) {
                    id
                    isFavourite
                }
            }
        """.trimIndent()
        
        val result = graphqlRequest(checkQuery, mapOf("mediaId" to mediaId))
        if (result?.contains("\"isFavourite\":false") == true || result?.contains("\"isFavourite\": false") == true) {
            android.util.Log.d("AniListFavorite", "  Already not favorited, skipping")
            return true // Already not favorited
        }
        
        android.util.Log.d("AniListFavorite", "  Executing ToggleFavourite mutation")
        val success = graphqlMutation(mutation, mapOf("mediaId" to mediaId)) != null
        android.util.Log.d("AniListFavorite", "  Mutation result: $success")
        return success
    }
    
    suspend fun toggleAniListFavorite(mediaId: Int, addFavorite: Boolean): Boolean {
        android.util.Log.d("AniListFavorite", "toggleAniListFavorite called: mediaId=$mediaId, addFavorite=$addFavorite")
        
        if (addFavorite) {
            // Check if already favorited
            val checkQuery = """
                query (${'$'}mediaId: Int) {
                    Media(id: ${'$'}mediaId) {
                        id
                        isFavourite
                    }
                }
            """.trimIndent()
            
            val result = graphqlRequest(checkQuery, mapOf("mediaId" to mediaId))
            android.util.Log.d("AniListFavorite", "Check result: $result")
            
            if (result?.contains("\"isFavourite\":true") == true || result?.contains("\"isFavourite\": true") == true) {
                android.util.Log.d("AniListFavorite", "Already favorited, skipping")
                return true // Already favorited
            }
        }
        
        val mutation = """
            mutation (${'$'}mediaId: Int) {
                ToggleFavourite(animeId: ${'$'}mediaId) {
                    anime { nodes { id } }
                }
            }
        """.trimIndent()
        
        android.util.Log.d("AniListFavorite", "Executing toggle mutation for mediaId=$mediaId")
        val success = graphqlMutation(mutation, mapOf("mediaId" to mediaId)) != null
        android.util.Log.d("AniListFavorite", "Toggle result: $success")
        return success
    }
}
