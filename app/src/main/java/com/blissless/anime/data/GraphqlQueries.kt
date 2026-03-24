package com.blissless.anime.data

/**
 * Optimized GraphQL queries for AniList API
 * 
 * Design principles:
 * 1. Request only fields we actually use
 * 2. Batch related queries when possible
 * 3. Use fragments for consistency
 * 4. Minimize payload size
 */
object GraphqlQueries {

    // ============================================
    // FRAGMENTS - Reusable field selections
    // ============================================
    
    /**
     * Minimal media fields for lists (reduces payload by ~60%)
     */
    const val MEDIA_LIST_FRAGMENT = """
        fragment MediaListFields on Media {
            id
            idMal
            title { romaji english }
            coverImage { large }
            bannerImage
            episodes
            status
            averageScore
            genres
            seasonYear
            nextAiringEpisode { episode airingAt }
        }
    """

    /**
     * Minimal fields for explore/grid displays
     */
    const val MEDIA_EXPLORE_FRAGMENT = """
        fragment MediaExploreFields on Media {
            id
            idMal
            title { romaji english }
            coverImage { large medium }
            bannerImage
            episodes
            status
            averageScore
            genres
            seasonYear
            isAdult
            startDate { year }
            nextAiringEpisode { episode airingAt }
        }
    """

    /**
     * Minimal fields for airing schedule
     */
    const val AIRING_FRAGMENT = """
        fragment AiringFields on AiringSchedule {
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
                isAdult
            }
        }
    """

    // ============================================
    // USER QUERIES
    // ============================================

    /**
     * Get user info - minimal fields
     */
    const val GET_VIEWER = """
        query {
            Viewer {
                id
                name
                avatar { medium }
            }
        }
    """

    /**
     * Get user's anime lists
     * Optimized: Only essential fields for tracking
     */
    const val GET_MEDIA_LISTS = $$"""
        query ($userId: Int) {
            MediaListCollection(userId: $userId, type: ANIME) {
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
                            status
                            averageScore
                            genres
                            seasonYear
                            nextAiringEpisode { episode airingAt }
                        }
                    }
                }
            }
        }
    """

    // ============================================
    // EXPLORE QUERIES - BATCHED
    // ============================================

    /**
     * Batched explore query - fetches all sections in ONE request
     * This replaces 9 individual requests with 1 request
     * 
     * Optimizations:
     * - Single network round-trip
     * - Reduced field selection
     * - Appropriate page sizes
     */
    const val GET_EXPLORE_BATCHED = """
        query {
            featured: Page(page: 1, perPage: 10) {
                media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
            seasonal: Page(page: 1, perPage: 20) {
                media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
            topSeries: Page(page: 1, perPage: 20) {
                media(type: ANIME, format: TV, sort: SCORE_DESC) {
                    ...MediaExploreFields
                }
            }
            topMovies: Page(page: 1, perPage: 20) {
                media(type: ANIME, format: MOVIE, sort: SCORE_DESC) {
                    ...MediaExploreFields
                }
            }
            action: Page(page: 1, perPage: 15) {
                media(type: ANIME, genre: "Action", sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
            romance: Page(page: 1, perPage: 15) {
                media(type: ANIME, genre: "Romance", sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
            comedy: Page(page: 1, perPage: 15) {
                media(type: ANIME, genre: "Comedy", sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
            fantasy: Page(page: 1, perPage: 15) {
                media(type: ANIME, genre: "Fantasy", sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
            scifi: Page(page: 1, perPage: 15) {
                media(type: ANIME, genre: "Sci-Fi", sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
        }
        
        $MEDIA_EXPLORE_FRAGMENT
    """

    /**
     * Individual explore section query (fallback)
     */
    const val GET_FEATURED = """
        query {
            Page(page: 1, perPage: 10) {
                media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
        }
        
        $MEDIA_EXPLORE_FRAGMENT
    """

    const val GET_SEASONAL = """
        query {
            Page(page: 1, perPage: 20) {
                media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
        }
        
        $MEDIA_EXPLORE_FRAGMENT
    """

    const val GET_TOP_SERIES = """
        query {
            Page(page: 1, perPage: 20) {
                media(type: ANIME, format: TV, sort: SCORE_DESC) {
                    ...MediaExploreFields
                }
            }
        }
        
        $MEDIA_EXPLORE_FRAGMENT
    """

    const val GET_TOP_MOVIES = """
        query {
            Page(page: 1, perPage: 20) {
                media(type: ANIME, format: MOVIE, sort: SCORE_DESC) {
                    ...MediaExploreFields
                }
            }
        }
        
        $MEDIA_EXPLORE_FRAGMENT
    """

    const val GET_BY_GENRE = $$"""
        query ($genre: String, $perPage: Int) {
            Page(page: 1, perPage: $perPage) {
                media(type: ANIME, genre: $genre, sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
        }
        
        $$MEDIA_EXPLORE_FRAGMENT
    """

    // ============================================
    // AIRING SCHEDULE
    // ============================================

    /**
     * Get airing schedule for next 7 days
     * Optimized with pagination
     */
    const val GET_AIRING_SCHEDULE = $$"""
        query ($page: Int, $startTime: Int, $endTime: Int) {
            Page(page: $page, perPage: 50) {
                airingSchedules(
                    airingAt_greater: $startTime,
                    airingAt_lesser: $endTime,
                    sort: TIME
                ) {
                    ...AiringFields
                }
            }
        }
        
        $$AIRING_FRAGMENT
    """

    /**
     * Get airing schedule for specific anime
     */
    const val GET_AIRING_FOR_ANIME = $$"""
        query ($mediaId: Int) {
            AiringSchedule(mediaId: $mediaId, sort: TIME_DESC) {
                ...AiringFields
            }
        }
        
        $$AIRING_FRAGMENT
    """

    // ============================================
    // SEARCH
    // ============================================

    /**
     * Search anime
     */
    const val SEARCH_ANIME = $$"""
        query ($search: String, $perPage: Int) {
            Page(page: 1, perPage: $perPage) {
                media(search: $search, type: ANIME, sort: POPULARITY_DESC) {
                    ...MediaExploreFields
                }
            }
        }
        
        $$MEDIA_EXPLORE_FRAGMENT
    """

    // ============================================
    // DETAILED ANIME
    // ============================================

    /**
     * Get detailed anime data with relations
     * Optimized: Only essential fields
     */
    val GET_DETAILED_ANIME = """
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

    const val GET_MINIMAL_ANIME = $$"""
        query ($id: Int) {
            Media(id: $id, type: ANIME) {
                id
                title { romaji english }
                coverImage { large }
                episodes
                status
                averageScore
                nextAiringEpisode { episode airingAt }
            }
        }
    """

    // ============================================
    // MUTATIONS
    // ============================================

    /**
     * Update anime progress
     */
    const val UPDATE_PROGRESS = $$"""
        mutation ($mediaId: Int, $progress: Int) {
            SaveMediaListEntry(mediaId: $mediaId, progress: $progress) {
                id
                progress
            }
        }
    """

    /**
     * Update anime status
     */
    const val UPDATE_STATUS = $$"""
        mutation ($mediaId: Int, $status: MediaListStatus, $progress: Int) {
            SaveMediaListEntry(
                mediaId: $mediaId,
                status: $status,
                progress: $progress
            ) {
                id
                status
                progress
            }
        }
    """

    /**
     * Update score
     */
    const val UPDATE_SCORE = $$"""
        mutation ($mediaId: Int, $score: Int) {
            SaveMediaListEntry(mediaId: $mediaId, score: $score) {
                id
                score
            }
        }
    """

    /**
     * Delete from list
     */
    const val DELETE_LIST_ENTRY = $$"""
        mutation ($id: Int) {
            DeleteMediaListEntry(id: $id) {
                deleted
            }
        }
    """

    /**
     * Toggle favorite
     */
    const val TOGGLE_FAVORITE = $$"""
        mutation ($animeId: Int) {
            ToggleFavourite(animeId: $animeId) {
                anime { nodes { id } }
            }
        }
    """

    // ============================================
    // USER ACTIVITY
    // ============================================

    /**
     * Get user activity
     */
    const val GET_USER_ACTIVITY = $$"""
        query ($userId: Int, $perPage: Int) {
            Page(page: 1, perPage: $perPage) {
                activities(
                    userId: $userId,
                    type: ANIME_LIST,
                    sort: ID_DESC
                ) {
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
    """

    /**
     * Get user favorites
     */
    const val GET_USER_FAVORITES = $$"""
        query ($userId: Int) {
            User(id: $userId) {
                favourites {
                    anime(page: 1, perPage: 10) {
                        nodes {
                            id
                            title { romaji english }
                            coverImage { large }
                            episodes
                            averageScore
                            genres
                            seasonYear
                        }
                    }
                }
            }
        }
    """

    // ============================================
    // BATCH QUERIES - For efficiency
    // ============================================

    /**
     * Get multiple anime by IDs
     * Useful for refreshing progress on multiple anime
     */
    const val GET_MULTIPLE_ANIME = $$"""
        query ($ids: [Int]) {
            Page(page: 1, perPage: 50) {
                media(id_in: $ids, type: ANIME) {
                    id
                    title { romaji english }
                    episodes
                    status
                    nextAiringEpisode { episode airingAt }
                }
            }
        }
    """

    /**
     * Get releasing anime progress (for currently watching)
     * Minimal fields for progress tracking
     */
    const val GET_RELEASING_PROGRESS = $$"""
        query ($ids: [Int]) {
            Page(page: 1, perPage: 50) {
                media(id_in: $ids, type: ANIME, status: RELEASING) {
                    id
                    nextAiringEpisode { episode airingAt }
                    episodes
                    status
                }
            }
        }
    """

    // ============================================
    // ANIME RELATIONS
    // ============================================

    /**
     * Get anime relations (sequels, prequels, adaptations, etc.)
     */
    val GET_ANIME_RELATIONS = """
        query (${'$'}id: Int!) {
            Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english }
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
                            nextAiringEpisode { episode }
                        }
                    }
                }
            }
        }
    """.trimIndent()
}
