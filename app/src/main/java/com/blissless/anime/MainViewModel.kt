package com.blissless.anime

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blissless.anime.BuildConfig
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.blissless.anime.api.AniwatchService
import com.blissless.anime.api.AniwatchAnimeInfo
import java.util.Calendar
import com.blissless.anime.api.AniwatchStreamResult
import com.blissless.anime.api.EpisodeStreams
import com.blissless.anime.api.ServerInfo
import com.blissless.anime.ui.screens.DetailedAnimeData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Deferred
import java.net.URLEncoder

private val DayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

private val Context.dataStore by preferencesDataStore(name = "settings")

// Result wrapper for stream fetching with fallback info
data class StreamFetchResult(
    val stream: AniwatchStreamResult?,
    val isFallback: Boolean,
    val requestedCategory: String,
    val actualCategory: String
)

// In-memory GraphQL response cache entry
data class GraphQLCacheEntry(
    val response: String,
    val timestamp: Long
)

// Playback position cache entry
@Serializable
data class PlaybackPositionCache(
    val positions: Map<String, Long>
)

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val CLIENT_ID = BuildConfig.CLIENT_ID_ANILIST
        private const val CLIENT_ID2 = BuildConfig.CLIENT_ID_ANILIST2
        private const val PREFS_NAME = "anilist_prefs"
        private const val TOKEN_KEY = "auth_token"

        // Cache duration in milliseconds (7 days for anime data - only refetch airing)
        private const val CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L
        // Airing cache duration (1 hour - refresh more frequently)
        private const val AIRING_CACHE_DURATION_MS = 1 * 60 * 60 * 1000L
        // Stream cache duration (7 days)
        private const val STREAM_CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L
        // GraphQL in-memory cache duration (5 minutes for short-term deduplication)
        private const val GRAPHQL_CACHE_DURATION_MS = 5 * 60 * 1000L

        // Cache keys
        private const val CACHE_EXPLORE_TIME = "cache_explore_time"
        private const val CACHE_HOME_TIME = "cache_home_time"
        private const val CACHE_EXPLORE_DATA = "cache_explore_data"
        private const val CACHE_HOME_DATA = "cache_home_data"
        private const val CACHE_STREAM_DATA = "cache_stream_data"
        private const val CACHE_AIRING_TIME = "cache_airing_time"
        private const val CACHE_AIRING_DATA = "cache_airing_data"
        private const val CACHE_PLAYBACK_POSITIONS = "cache_playback_positions"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ============================================
    // STRATEGY 1: Dual Client ID Rotation
    // ============================================
    private val clientIdCounter = AtomicInteger(0)

    private fun getNextClientId(): String {
        val index = clientIdCounter.getAndIncrement() % 2
        val clientId = if (index == 0) CLIENT_ID else CLIENT_ID2
        Log.d(TAG, "Using CLIENT_ID${index + 1} for request")
        return clientId
    }

    // ============================================
    // STRATEGY 3: In-Memory GraphQL Request Cache
    // ============================================
    private val graphqlCache = ConcurrentHashMap<String, GraphQLCacheEntry>()

    private fun generateCacheKey(query: String, variables: Map<String, Any?>): String {
        val varsStr = variables.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return "${query.hashCode()}_$varsStr"
    }

    private fun getCachedGraphqlResponse(cacheKey: String): String? {
        val entry = graphqlCache[cacheKey] ?: return null
        val now = System.currentTimeMillis()
        if (now - entry.timestamp < GRAPHQL_CACHE_DURATION_MS) {
            Log.d(TAG, "Using in-memory cached GraphQL response")
            return entry.response
        }
        graphqlCache.remove(cacheKey)
        return null
    }

    private fun cacheGraphqlResponse(cacheKey: String, response: String) {
        graphqlCache[cacheKey] = GraphQLCacheEntry(
            response = response,
            timestamp = System.currentTimeMillis()
        )
        if (graphqlCache.size > 100) {
            val sortedKeys = graphqlCache.entries
                .sortedBy { it.value.timestamp }
                .take(50)
                .map { it.key }
            sortedKeys.forEach { graphqlCache.remove(it) }
        }
    }

    // Pending requests for deduplication
    private val pendingRequests = ConcurrentHashMap<String, Deferred<String?>>()

    // ============================================
    // STRATEGY 4: Request Throttling & Rate Limiting
    // ============================================
    private var lastRequestTime = 0L
    private val minRequestIntervalMs = 700L
    private val requestMutex = java.util.concurrent.Semaphore(1)

    private fun invalidateUserCache() {
        val keysToRemove = graphqlCache.keys.filter { key ->
            key.contains("userId=") ||
                    key.contains("status=") ||
                    key.contains("progress=") ||
                    key.contains("mediaId=")
        }
        keysToRemove.forEach { graphqlCache.remove(it) }
        Log.d(TAG, "Invalidated ${keysToRemove.size} user-specific cache entries (total cache: ${graphqlCache.size})")

        sharedPreferences.edit()
            .remove(CACHE_HOME_DATA)
            .remove(CACHE_HOME_TIME)
            .apply()
    }

    private suspend fun throttleRequest() {
        requestMutex.acquire()
        try {
            val now = System.currentTimeMillis()
            val timeSinceLastRequest = now - lastRequestTime
            if (timeSinceLastRequest < minRequestIntervalMs) {
                val delayMs = minRequestIntervalMs - timeSinceLastRequest
                Log.d(TAG, "Throttling: waiting ${delayMs}ms before request")
                kotlinx.coroutines.delay(delayMs)
            }
            lastRequestTime = System.currentTimeMillis()
        } finally {
            requestMutex.release()
        }
    }

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _isOled = MutableStateFlow(false)
    val isOled: StateFlow<Boolean> = _isOled.asStateFlow()

    private val _disableMaterialColors = MutableStateFlow(false)
    val disableMaterialColors: StateFlow<Boolean> = _disableMaterialColors.asStateFlow()

    private val _preferredCategory = MutableStateFlow("sub")
    val preferredCategory: StateFlow<String> = _preferredCategory.asStateFlow()

    private val _showStatusColors = MutableStateFlow(true)
    val showStatusColors: StateFlow<Boolean> = _showStatusColors.asStateFlow()

    private val _trackingPercentage = MutableStateFlow(85)
    val trackingPercentage: StateFlow<Int> = _trackingPercentage.asStateFlow()

    private val _forwardSkipSeconds = MutableStateFlow(10)
    val forwardSkipSeconds: StateFlow<Int> = _forwardSkipSeconds.asStateFlow()

    private val _backwardSkipSeconds = MutableStateFlow(10)
    val backwardSkipSeconds: StateFlow<Int> = _backwardSkipSeconds.asStateFlow()

    private val _forceHighRefreshRate = MutableStateFlow(false)
    val forceHighRefreshRate: StateFlow<Boolean> = _forceHighRefreshRate.asStateFlow()

    private val _hideNavbarText = MutableStateFlow(false)
    val hideNavbarText: StateFlow<Boolean> = _hideNavbarText.asStateFlow()

    private val _simplifyEpisodeMenu = MutableStateFlow(true)
    val simplifyEpisodeMenu: StateFlow<Boolean> = _simplifyEpisodeMenu.asStateFlow()

    private val _simplifyAnimeDetails = MutableStateFlow(true)
    val simplifyAnimeDetails: StateFlow<Boolean> = _simplifyAnimeDetails.asStateFlow()

    private val _autoSkipOpening = MutableStateFlow(false)
    val autoSkipOpening: StateFlow<Boolean> = _autoSkipOpening.asStateFlow()

    private val _autoSkipEnding = MutableStateFlow(false)
    val autoSkipEnding: StateFlow<Boolean> = _autoSkipEnding.asStateFlow()

    private val _autoPlayNextEpisode = MutableStateFlow(false)
    val autoPlayNextEpisode: StateFlow<Boolean> = _autoPlayNextEpisode.asStateFlow()

    private val _userId = MutableStateFlow<Int?>(null)
    val userId: StateFlow<Int?> = _userId.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userAvatar = MutableStateFlow<String?>(null)
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    private val _isLoadingExplore = MutableStateFlow(false)
    val isLoadingExplore: StateFlow<Boolean> = _isLoadingExplore.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    private val _featuredAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val featuredAnime: StateFlow<List<ExploreAnime>> = _featuredAnime.asStateFlow()

    private val _seasonalAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val seasonalAnime: StateFlow<List<ExploreAnime>> = _seasonalAnime.asStateFlow()

    private val _topSeries = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val topSeries: StateFlow<List<ExploreAnime>> = _topSeries.asStateFlow()

    private val _topMovies = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val topMovies: StateFlow<List<ExploreAnime>> = _topMovies.asStateFlow()

    private val _actionAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val actionAnime: StateFlow<List<ExploreAnime>> = _actionAnime.asStateFlow()

    private val _romanceAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val romanceAnime: StateFlow<List<ExploreAnime>> = _romanceAnime.asStateFlow()

    private val _comedyAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val comedyAnime: StateFlow<List<ExploreAnime>> = _comedyAnime.asStateFlow()

    private val _fantasyAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val fantasyAnime: StateFlow<List<ExploreAnime>> = _fantasyAnime.asStateFlow()

    private val _scifiAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val scifiAnime: StateFlow<List<ExploreAnime>> = _scifiAnime.asStateFlow()

    private val _currentlyWatching = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val currentlyWatching: StateFlow<List<AnimeMedia>> = _currentlyWatching.asStateFlow()

    private val _planningToWatch = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val planningToWatch: StateFlow<List<AnimeMedia>> = _planningToWatch.asStateFlow()

    private val _completed = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val completed: StateFlow<List<AnimeMedia>> = _completed.asStateFlow()

    private val _onHold = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val onHold: StateFlow<List<AnimeMedia>> = _onHold.asStateFlow()

    private val _dropped = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val dropped: StateFlow<List<AnimeMedia>> = _dropped.asStateFlow()

    private val _airingSchedule = MutableStateFlow<Map<Int, List<AiringScheduleAnime>>>(emptyMap())
    val airingSchedule: StateFlow<Map<Int, List<AiringScheduleAnime>>> = _airingSchedule.asStateFlow()

    private val _airingAnimeList = MutableStateFlow<List<AiringScheduleAnime>>(emptyList())
    val airingAnimeList: StateFlow<List<AiringScheduleAnime>> = _airingAnimeList.asStateFlow()

    private val _isLoadingSchedule = MutableStateFlow(false)
    val isLoadingSchedule: StateFlow<Boolean> = _isLoadingSchedule.asStateFlow()

    private val _prefetchedStreams = MutableStateFlow<Map<String, AniwatchStreamResult?>>(emptyMap())
    val prefetchedStreams: StateFlow<Map<String, AniwatchStreamResult?>> = _prefetchedStreams.asStateFlow()

    private val _prefetchedEpisodeInfo = MutableStateFlow<Map<String, EpisodeStreams?>>(emptyMap())
    val prefetchedEpisodeInfo: StateFlow<Map<String, EpisodeStreams?>> = _prefetchedEpisodeInfo.asStateFlow()

    private val _detailedAnimeCache = MutableStateFlow<Map<Int, DetailedAnimeData>>(emptyMap())
    val detailedAnimeCache: StateFlow<Map<Int, DetailedAnimeData>> = _detailedAnimeCache.asStateFlow()

    private val _playbackPositions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val playbackPositions: StateFlow<Map<String, Long>> = _playbackPositions.asStateFlow()

    private var context: Context? = null
    private lateinit var sharedPreferences: SharedPreferences

    // TMDB season calculation cache
    private val calculatedSeasons = ConcurrentHashMap<Int, Int>()
    // NEW: Episode offset cache for Aniwatch-based calculation
    private val episodeOffsetCache = ConcurrentHashMap<Int, Int>()

    fun init(context: Context, hasToken: Boolean) {
        this.context = context
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (hasToken) {
            val token = sharedPreferences.getString(TOKEN_KEY, null)
            _authToken.value = token
            Log.d(TAG, "Token loaded from SharedPreferences: ${token?.take(20)}...")
        }

        _isOled.value = sharedPreferences.getBoolean("oled_mode", false)
        _disableMaterialColors.value = sharedPreferences.getBoolean("disable_material_colors", false)
        _preferredCategory.value = sharedPreferences.getString("preferred_category", "sub") ?: "sub"
        _showStatusColors.value = sharedPreferences.getBoolean("show_status_colors", false)
        _trackingPercentage.value = sharedPreferences.getInt("tracking_percentage", 85)
        _forwardSkipSeconds.value = sharedPreferences.getInt("forward_skip_seconds", 10)
        _backwardSkipSeconds.value = sharedPreferences.getInt("backward_skip_seconds", 10)
        _forceHighRefreshRate.value = sharedPreferences.getBoolean("force_high_refresh_rate", false)
        _hideNavbarText.value = sharedPreferences.getBoolean("hide_navbar_text", false)
        _simplifyEpisodeMenu.value = sharedPreferences.getBoolean("simplify_episode_menu", true)
        _simplifyAnimeDetails.value = sharedPreferences.getBoolean("simplify_anime_details", true)
        _autoSkipOpening.value = sharedPreferences.getBoolean("auto_skip_opening", false)
        _autoSkipEnding.value = sharedPreferences.getBoolean("auto_skip_ending", false)
        _autoPlayNextEpisode.value = sharedPreferences.getBoolean("auto_play_next_episode", false)

        loadLocalFavorites()
        loadStreamCache()
        loadAiringScheduleCache()
        loadPlaybackPositions()

        viewModelScope.launch {
            if (hasToken) {
                loadHomeDataWithCache()
            }
            loadExploreDataWithCache()
            fetchAiringSchedule()
        }
    }

    private fun isCacheValid(cacheKey: String, customDuration: Long = CACHE_DURATION_MS): Boolean {
        val cacheTime = sharedPreferences.getLong(cacheKey, 0)
        val now = System.currentTimeMillis()
        return (now - cacheTime) < customDuration
    }

    private fun setCacheTime(cacheKey: String) {
        sharedPreferences.edit().putLong(cacheKey, System.currentTimeMillis()).apply()
    }

    private fun loadPlaybackPositions() {
        try {
            val cachedData = sharedPreferences.getString(CACHE_PLAYBACK_POSITIONS, null)
            if (cachedData != null) {
                val cacheData = json.decodeFromString<PlaybackPositionCache>(cachedData)
                _playbackPositions.value = cacheData.positions
                Log.d(TAG, "Loaded ${cacheData.positions.size} playback positions from cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load playback positions", e)
        }
    }

    private fun savePlaybackPositions() {
        try {
            val cacheData = PlaybackPositionCache(_playbackPositions.value)
            val jsonString = json.encodeToString(PlaybackPositionCache.serializer(), cacheData)
            sharedPreferences.edit()
                .putString(CACHE_PLAYBACK_POSITIONS, jsonString)
                .apply()
            Log.d(TAG, "Saved ${_playbackPositions.value.size} playback positions to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save playback positions", e)
        }
    }

    fun savePlaybackPosition(animeId: Int, episode: Int, position: Long) {
        val key = "${animeId}_$episode"
        val currentPositions = _playbackPositions.value.toMutableMap()
        currentPositions[key] = position
        _playbackPositions.value = currentPositions
        savePlaybackPositions()
        Log.d(TAG, "Saved playback position for anime $animeId ep $episode: ${position}ms")
    }

    fun getPlaybackPosition(animeId: Int, episode: Int): Long {
        val key = "${animeId}_$episode"
        val position = _playbackPositions.value[key] ?: 0L
        Log.d(TAG, "Retrieved playback position for anime $animeId ep $episode: ${position}ms")
        return position
    }

    fun clearPlaybackPosition(animeId: Int, episode: Int) {
        val key = "${animeId}_$episode"
        val currentPositions = _playbackPositions.value.toMutableMap()
        currentPositions.remove(key)
        _playbackPositions.value = currentPositions
        savePlaybackPositions()
        Log.d(TAG, "Cleared playback position for anime $animeId ep $episode")
    }

    fun clearAllPlaybackPositionsForAnime(animeId: Int) {
        val currentPositions = _playbackPositions.value.toMutableMap()
        val keysToRemove = currentPositions.keys.filter { it.startsWith("${animeId}_") }
        keysToRemove.forEach { currentPositions.remove(it) }
        _playbackPositions.value = currentPositions
        savePlaybackPositions()
        Log.d(TAG, "Cleared all playback positions for anime $animeId")
    }

    private fun loadStreamCache() {
        try {
            val cachedData = sharedPreferences.getString(CACHE_STREAM_DATA, null)
            if (cachedData != null) {
                val cacheData = json.decodeFromString<StreamCacheData>(cachedData)
                val now = System.currentTimeMillis()

                val validEntries = cacheData.entries.filter { (_, entry) ->
                    (now - entry.timestamp) < STREAM_CACHE_DURATION_MS
                }

                val streamMap = mutableMapOf<String, AniwatchStreamResult?>()
                val episodeMap = mutableMapOf<String, EpisodeStreams?>()

                validEntries.forEach { (key, entry) ->
                    streamMap[key] = entry.stream?.let {
                        AniwatchStreamResult(
                            url = it.url,
                            isDirectStream = it.isDirectStream,
                            headers = it.headers,
                            subtitleUrl = it.subtitleUrl,
                            serverName = it.serverName,
                            category = it.category
                        )
                    }
                    entry.episodeInfo?.let { epInfo ->
                        episodeMap[key] = EpisodeStreams(
                            subServers = epInfo.subServers.map { s -> ServerInfo(s.name, s.url) },
                            dubServers = epInfo.dubServers.map { s -> ServerInfo(s.name, s.url) },
                            animeId = epInfo.animeId,
                            episodeId = epInfo.episodeId
                        )
                    }
                }

                _prefetchedStreams.value = streamMap
                _prefetchedEpisodeInfo.value = episodeMap
                Log.d(TAG, "Loaded ${streamMap.size} cached streams from persistence")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load stream cache", e)
        }
    }

    private fun loadAiringScheduleCache() {
        try {
            val cachedData = sharedPreferences.getString(CACHE_AIRING_DATA, null)
            if (cachedData != null && isCacheValid(CACHE_AIRING_TIME, AIRING_CACHE_DURATION_MS)) {
                val cacheData = json.decodeFromString<AiringCacheData>(cachedData)

                val scheduleByDay = mutableMapOf<Int, List<AiringScheduleAnime>>()
                for (i in 0..6) {
                    scheduleByDay[i] = cacheData.scheduleByDay[i] ?: emptyList()
                }

                _airingSchedule.value = scheduleByDay
                _airingAnimeList.value = cacheData.airingAnimeList
                Log.d(TAG, "Loaded cached airing schedule with ${cacheData.airingAnimeList.size} entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load airing schedule cache", e)
        }
    }

    private fun saveAiringScheduleCache() {
        viewModelScope.launch {
            try {
                val cacheData = AiringCacheData(
                    scheduleByDay = _airingSchedule.value,
                    airingAnimeList = _airingAnimeList.value
                )
                val jsonString = json.encodeToString(AiringCacheData.serializer(), cacheData)
                sharedPreferences.edit()
                    .putString(CACHE_AIRING_DATA, jsonString)
                    .apply()
                setCacheTime(CACHE_AIRING_TIME)
                Log.d(TAG, "Airing schedule saved to cache")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save airing schedule cache", e)
            }
        }
    }

    private fun saveStreamCache() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val entries = mutableMapOf<String, StreamCacheEntry>()

                _prefetchedStreams.value.forEach { (key, stream) ->
                    val epInfo = _prefetchedEpisodeInfo.value[key]

                    entries[key] = StreamCacheEntry(
                        stream = stream?.let {
                            CachedStream(
                                url = it.url,
                                isDirectStream = it.isDirectStream,
                                headers = it.headers,
                                subtitleUrl = it.subtitleUrl,
                                serverName = it.serverName,
                                category = it.category
                            )
                        },
                        episodeInfo = epInfo?.let {
                            CachedEpisodeInfo(
                                subServers = it.subServers.map { s -> CachedServer(s.name, s.url) },
                                dubServers = it.dubServers.map { s -> CachedServer(s.name, s.url) },
                                animeId = it.animeId,
                                episodeId = it.episodeId
                            )
                        },
                        timestamp = now
                    )
                }

                val cacheData = StreamCacheData(entries)
                val jsonString = json.encodeToString(StreamCacheData.serializer(), cacheData)
                sharedPreferences.edit()
                    .putString(CACHE_STREAM_DATA, jsonString)
                    .apply()

                Log.d(TAG, "Saved ${entries.size} streams to cache")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save stream cache", e)
            }
        }
    }

    private suspend fun loadHomeDataWithCache() {
        val cachedHomeData = sharedPreferences.getString(CACHE_HOME_DATA, null)

        if (cachedHomeData != null && isCacheValid(CACHE_HOME_TIME)) {
            Log.d(TAG, "Loading home data from cache")
            try {
                val cacheData = json.decodeFromString<HomeCacheData>(cachedHomeData)
                _currentlyWatching.value = cacheData.currentlyWatching
                _planningToWatch.value = cacheData.planningToWatch
                _completed.value = cacheData.completed
                _onHold.value = cacheData.onHold
                _dropped.value = cacheData.dropped
                _userId.value = cacheData.userId
                _userName.value = cacheData.userName
                _userAvatar.value = cacheData.userAvatar

                refreshReleasingAnimeProgress()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached home data", e)
            }
        }

        _isLoadingHome.value = true
        fetchUser()
        fetchLists()
        _isLoadingHome.value = false
    }

    private suspend fun refreshReleasingAnimeProgress() {
        val watching = _currentlyWatching.value.filter { it.status == "RELEASING" || it.latestEpisode != null }
        if (watching.isEmpty()) return

        Log.d(TAG, "Refreshing progress for ${watching.size} releasing anime")

        watching.forEach { anime ->
            try {
                val detailed = fetchDetailedAnimeData(anime.id)
                if (detailed != null && detailed.nextAiringEpisode != null) {
                    val updatedAnime = anime.copy(
                        latestEpisode = detailed.nextAiringEpisode
                    )
                    _currentlyWatching.value = _currentlyWatching.value.map {
                        if (it.id == anime.id) updatedAnime else it
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh ${anime.title}", e)
            }
        }

        saveHomeDataToCache()
    }

    private suspend fun loadExploreDataWithCache() {
        val cachedExploreData = sharedPreferences.getString(CACHE_EXPLORE_DATA, null)

        if (cachedExploreData != null && isCacheValid(CACHE_EXPLORE_TIME)) {
            Log.d(TAG, "Loading explore data from cache")
            try {
                val cacheData = json.decodeFromString<ExploreCacheData>(cachedExploreData)
                _featuredAnime.value = cacheData.featuredAnime
                _seasonalAnime.value = cacheData.seasonalAnime
                _topSeries.value = cacheData.topSeries
                _topMovies.value = cacheData.topMovies
                _actionAnime.value = cacheData.actionAnime
                _romanceAnime.value = cacheData.romanceAnime
                _comedyAnime.value = cacheData.comedyAnime
                _fantasyAnime.value = cacheData.fantasyAnime
                _scifiAnime.value = cacheData.scifiAnime
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached explore data", e)
            }
        }

        fetchExploreData()
    }

    private fun saveHomeDataToCache() {
        viewModelScope.launch {
            try {
                val cacheData = HomeCacheData(
                    currentlyWatching = _currentlyWatching.value,
                    planningToWatch = _planningToWatch.value,
                    completed = _completed.value,
                    onHold = _onHold.value,
                    dropped = _dropped.value,
                    userId = _userId.value,
                    userName = _userName.value,
                    userAvatar = _userAvatar.value
                )
                val jsonString = json.encodeToString(HomeCacheData.serializer(), cacheData)
                sharedPreferences.edit()
                    .putString(CACHE_HOME_DATA, jsonString)
                    .apply()
                setCacheTime(CACHE_HOME_TIME)
                Log.d(TAG, "Home data saved to cache")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save home data to cache", e)
            }
        }
    }

    private fun saveExploreDataToCache() {
        viewModelScope.launch {
            try {
                val cacheData = ExploreCacheData(
                    featuredAnime = _featuredAnime.value,
                    seasonalAnime = _seasonalAnime.value,
                    topSeries = _topSeries.value,
                    topMovies = _topMovies.value,
                    actionAnime = _actionAnime.value,
                    romanceAnime = _romanceAnime.value,
                    comedyAnime = _comedyAnime.value,
                    fantasyAnime = _fantasyAnime.value,
                    scifiAnime = _scifiAnime.value
                )
                val jsonString = json.encodeToString(ExploreCacheData.serializer(), cacheData)
                sharedPreferences.edit()
                    .putString(CACHE_EXPLORE_DATA, jsonString)
                    .apply()
                setCacheTime(CACHE_EXPLORE_TIME)
                Log.d(TAG, "Explore data saved to cache")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save explore data to cache", e)
            }
        }
    }

    fun setOledMode(enabled: Boolean) {
        _isOled.value = enabled
        sharedPreferences.edit().putBoolean("oled_mode", enabled).apply()
    }

    fun setDisableMaterialColors(enabled: Boolean) {
        _disableMaterialColors.value = enabled
        sharedPreferences.edit().putBoolean("disable_material_colors", enabled).apply()
    }

    fun setPreferredCategory(category: String) {
        _preferredCategory.value = category
        sharedPreferences.edit().putString("preferred_category", category).apply()
        Log.d(TAG, "Preferred category set to: $category")
    }

    fun setShowStatusColors(enabled: Boolean) {
        _showStatusColors.value = enabled
        sharedPreferences.edit().putBoolean("show_status_colors", enabled).apply()
    }

    fun setTrackingPercentage(percentage: Int) {
        _trackingPercentage.value = percentage
        sharedPreferences.edit().putInt("tracking_percentage", percentage).apply()
        Log.d(TAG, "Tracking percentage set to: $percentage%")
    }

    fun setForwardSkipSeconds(seconds: Int) {
        _forwardSkipSeconds.value = seconds
        sharedPreferences.edit().putInt("forward_skip_seconds", seconds).apply()
        Log.d(TAG, "Forward skip set to: $seconds seconds")
    }

    fun setBackwardSkipSeconds(seconds: Int) {
        _backwardSkipSeconds.value = seconds
        sharedPreferences.edit().putInt("backward_skip_seconds", seconds).apply()
        Log.d(TAG, "Backward skip set to: $seconds seconds")
    }

    fun setForceHighRefreshRate(enabled: Boolean) {
        _forceHighRefreshRate.value = enabled
        sharedPreferences.edit().putBoolean("force_high_refresh_rate", enabled).apply()
        Log.d(TAG, "Force high refresh rate set to: $enabled")
    }

    fun setHideNavbarText(enabled: Boolean) {
        _hideNavbarText.value = enabled
        sharedPreferences.edit().putBoolean("hide_navbar_text", enabled).apply()
        Log.d(TAG, "Hide navbar text set to: $enabled")
    }

    fun setSimplifyEpisodeMenu(enabled: Boolean) {
        _simplifyEpisodeMenu.value = enabled
        sharedPreferences.edit().putBoolean("simplify_episode_menu", enabled).apply()
        Log.d(TAG, "Simplify episode menu set to: $enabled")
    }

    fun setSimplifyAnimeDetails(enabled: Boolean) {
        _simplifyAnimeDetails.value = enabled
        sharedPreferences.edit().putBoolean("simplify_anime_details", enabled).apply()
        Log.d(TAG, "Simplify anime details set to: $enabled")
    }

    fun setAutoSkipOpening(enabled: Boolean) {
        _autoSkipOpening.value = enabled
        sharedPreferences.edit().putBoolean("auto_skip_opening", enabled).apply()
        Log.d(TAG, "Auto skip opening set to: $enabled")
    }

    fun setAutoSkipEnding(enabled: Boolean) {
        _autoSkipEnding.value = enabled
        sharedPreferences.edit().putBoolean("auto_skip_ending", enabled).apply()
        Log.d(TAG, "Auto skip ending set to: $enabled")
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        _autoPlayNextEpisode.value = enabled
        sharedPreferences.edit().putBoolean("auto_play_next_episode", enabled).apply()
        Log.d(TAG, "Auto play next episode set to: $enabled")
    }

    fun loginWithAniList() {
        val url = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"
        Log.d(TAG, "Opening auth URL: $url")
        context?.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun handleAuthRedirect(intent: Intent?) {
        val uriString = intent?.dataString
        Log.d(TAG, "handleAuthRedirect: uriString=$uriString")

        if (uriString != null && uriString.startsWith("animescraper://success")) {
            val token = uriString.replace("#", "?").toUri().getQueryParameter("access_token")
            Log.d(TAG, "Parsed token: ${token?.take(20)}...")

            if (token != null) {
                sharedPreferences.edit().putString(TOKEN_KEY, token).apply()
                _authToken.value = token

                viewModelScope.launch {
                    _isLoadingHome.value = true
                    fetchUser()
                    fetchLists()
                    _isLoadingHome.value = false
                }
            }
        }
    }

    fun logout() {
        sharedPreferences.edit()
            .remove(TOKEN_KEY)
            .remove(CACHE_HOME_DATA)
            .remove(CACHE_HOME_TIME)
            .remove(CACHE_EXPLORE_DATA)
            .remove(CACHE_EXPLORE_TIME)
            .remove(CACHE_AIRING_DATA)
            .remove(CACHE_AIRING_TIME)
            .apply()

        _authToken.value = null
        _userId.value = null
        _userName.value = null
        _userAvatar.value = null
        _currentlyWatching.value = emptyList()
        _planningToWatch.value = emptyList()
        _completed.value = emptyList()
        _onHold.value = emptyList()
        _dropped.value = emptyList()
        _prefetchedStreams.value = emptyMap()
        _prefetchedEpisodeInfo.value = emptyMap()

        graphqlCache.clear()
        pendingRequests.clear()
        _detailedAnimeCache.value = emptyMap()
    }

    private suspend fun graphqlRequest(query: String, variables: Map<String, Any?>): String? = withContext(Dispatchers.IO) {
        val token = _authToken.value ?: return@withContext null

        val cacheKey = generateCacheKey(query, variables)
        getCachedGraphqlResponse(cacheKey)?.let {
            return@withContext it
        }

        throttleRequest()

        val url = URL("https://graphql.anilist.co")
        val connection = url.openConnection() as HttpsURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-Client-Id", getNextClientId())
            connection.doOutput = true

            val variablesJson = variables.entries.joinToString(",", "{", "}") { (key, value) ->
                "\"$key\":${when (value) {
                    is String -> "\"$value\""
                    is Number -> value.toString()
                    is Boolean -> value.toString()
                    null -> "null"
                    else -> "\"$value\""
                }}"
            }
            val body = "{\"query\":${Json.encodeToString(query)},\"variables\":$variablesJson}"

            connection.outputStream.use { it.write(body.toByteArray()) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                cacheGraphqlResponse(cacheKey, response)
                response
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "GraphQL error: ${connection.responseCode} - $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GraphQL request failed", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun publicGraphqlRequest(query: String, variables: Map<String, Any?>): String? = withContext(Dispatchers.IO) {
        // STRATEGY 3: Check in-memory cache first
        val cacheKey = generateCacheKey(query, variables)
        getCachedGraphqlResponse(cacheKey)?.let {
            return@withContext it
        }

        // STRATEGY 3: Request deduplication - check if same request is already pending
        val existingPending = pendingRequests[cacheKey]
        if (existingPending != null) {
            Log.d(TAG, "Waiting for pending duplicate request")
            return@withContext existingPending.await()
        }

        // STRATEGY 4: Throttle requests to avoid rate limiting
        throttleRequest()

        // Create new deferred request
        val deferred = async {
            val url = URL("https://graphql.anilist.co")
            val connection = url.openConnection() as HttpsURLConnection

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                // STRATEGY 1: Add Client-ID header for rate limit tracking
                connection.setRequestProperty("X-Client-Id", getNextClientId())
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val variablesJson = if (variables.isEmpty()) "{}" else {
                    variables.entries.joinToString(",", "{", "}") { (key, value) ->
                        "\"$key\":${when (value) {
                            is String -> "\"$value\""
                            is Number -> value.toString()
                            is Boolean -> value.toString()
                            null -> "null"
                            else -> "\"$value\""
                        }}"
                    }
                }

                val escapedQuery = query
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")

                // FIX: Added closing brace "}" at the end of the JSON string
                val body = "{\"query\":\"$escapedQuery\",\"variables\":$variablesJson}"

                connection.outputStream.use { it.write(body.toByteArray()) }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    // STRATEGY 3: Cache the response
                    cacheGraphqlResponse(cacheKey, response)
                    response
                } else {
                    val error = connection.errorStream?.bufferedReader()?.readText()
                    Log.e(TAG, "Public GraphQL error: ${connection.responseCode} - $error")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Public GraphQL request failed", e)
                null
            } finally {
                connection.disconnect()
            }
        }

        // Register pending request atomically
        pendingRequests[cacheKey] = deferred

        // Wait for result and cleanup
        val result = deferred.await()
        pendingRequests.remove(cacheKey)

        result
    }

    suspend fun fetchUser() {
        Log.d(TAG, "Fetching user data...")
        val query = """
            query {
                Viewer {
                    id
                    name
                    avatar { medium }
                }
            }
        """.trimIndent()

        val response = graphqlRequest(query, emptyMap())
        response?.let {
            try {
                val data = json.decodeFromString<ViewerResponse>(it)
                _userId.value = data.data.Viewer.id
                _userName.value = data.data.Viewer.name
                _userAvatar.value = data.data.Viewer.avatar?.medium
                Log.d(TAG, "User fetched: ${data.data.Viewer.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse user response", e)
            }
        }
    }

    suspend fun fetchLists() {
        val userId = _userId.value ?: return
        Log.d(TAG, "Fetching lists for user: $userId")

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

        val response = graphqlRequest(query, mapOf("userId" to userId))
        response?.let {
            try {
                val data = json.decodeFromString<MediaListResponse>(it)

                val currentlyWatchingList = mutableListOf<AnimeMedia>()
                val planningList = mutableListOf<AnimeMedia>()
                val completedList = mutableListOf<AnimeMedia>()
                val onHoldList = mutableListOf<AnimeMedia>()
                val droppedList = mutableListOf<AnimeMedia>()

                data.data.MediaListCollection.lists.forEach { list ->
                    list.entries.forEach { entry ->
                        val anime = AnimeMedia(
                            id = entry.mediaId,
                            title = entry.media.title.romaji ?: entry.media.title.english ?: "Unknown",
                            cover = entry.media.coverImage?.large ?: entry.media.coverImage?.medium ?: "",
                            banner = entry.media.bannerImage,
                            progress = entry.progress ?: 0,
                            totalEpisodes = entry.media.episodes ?: 0,
                            latestEpisode = entry.media.nextAiringEpisode?.episode,
                            status = entry.media.status ?: "",
                            averageScore = entry.media.averageScore,
                            genres = entry.media.genres ?: emptyList(),
                            listStatus = list.status ?: list.name,
                            listEntryId = entry.id,
                            year = entry.media.seasonYear,
                            malId = entry.media.idMal
                        )

                        when (list.status ?: list.name) {
                            "CURRENT", "Watching" -> currentlyWatchingList.add(anime)
                            "PLANNING", "Plan to Watch" -> planningList.add(anime)
                            "COMPLETED" -> completedList.add(anime)
                            "PAUSED" -> onHoldList.add(anime)
                            "DROPPED" -> droppedList.add(anime)
                        }
                    }
                }

                _currentlyWatching.value = currentlyWatchingList
                _planningToWatch.value = planningList
                _completed.value = completedList
                _onHold.value = onHoldList
                _dropped.value = droppedList
                Log.d(TAG, "Fetched ${currentlyWatchingList.size} watching, ${planningList.size} planning, ${completedList.size} completed, ${onHoldList.size} on hold, ${droppedList.size} dropped")

                saveHomeDataToCache()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse lists response", e)
            }
        }
    }

    fun updateAnimeProgress(mediaId: Int, progress: Int) {
        Log.d(TAG, "updateAnimeProgress: mediaId=$mediaId, progress=$progress")
        viewModelScope.launch {
            val query = """
                mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) {
                        id
                        progress
                    }
                }
            """.trimIndent()

            invalidateUserCache()

            val response = graphqlRequest(query, mapOf("mediaId" to mediaId, "progress" to progress))
            if (response != null) {
                Log.d(TAG, "Update progress SUCCESS")
                fetchLists()
            } else {
                Log.e(TAG, "Update progress FAILED")
            }
        }
    }

    fun updateAnimeStatus(mediaId: Int, status: String, progress: Int? = null) {
        Log.d(TAG, "updateAnimeStatus: mediaId=$mediaId, status=$status")
        viewModelScope.launch {
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

            invalidateUserCache()

            val response = graphqlRequest(query, variables)
            if (response != null) {
                Log.d(TAG, "Update status SUCCESS")
                fetchLists()
            } else {
                Log.e(TAG, "Update status FAILED")
            }
        }
    }

    fun addExploreAnimeToList(anime: ExploreAnime, status: String) {
        Log.d(TAG, "addExploreAnimeToList: ${anime.title}, status=$status")
        updateAnimeStatus(anime.id, status, if (status == "CURRENT") 0 else null)
    }

    // ============================================
    // STRATEGY 2: Batched Explore Data Fetch
    // ============================================
    fun fetchExploreData() {
        Log.d(TAG, "Fetching explore data with batched request...")
        viewModelScope.launch {
            _isLoadingExplore.value = true

            try {
                val batchedQuery = """
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

                val response = publicGraphqlRequest(batchedQuery, emptyMap())

                if (response != null) {
                    try {
                        val data = json.decodeFromString<BatchedExploreResponse>(response)

                        _featuredAnime.value = data.data.featured.media.map { mapExploreMedia(it) }
                        _seasonalAnime.value = data.data.seasonal.media.map { mapExploreMedia(it) }

                        val seriesList = data.data.topSeries.media.map { mapExploreMedia(it) }
                        _topSeries.value = if (seriesList.size > 10) {
                            seriesList.filter { (it.averageScore ?: 0) >= 70 }
                        } else {
                            seriesList
                        }

                        val moviesList = data.data.topMovies.media.map { mapExploreMedia(it) }
                        _topMovies.value = if (moviesList.size > 10) {
                            moviesList.filter { (it.averageScore ?: 0) >= 70 }
                        } else {
                            moviesList
                        }

                        _actionAnime.value = data.data.action.media.map { mapExploreMedia(it) }
                            .filter { (it.averageScore ?: 0) >= 60 }
                        _romanceAnime.value = data.data.romance.media.map { mapExploreMedia(it) }
                            .filter { (it.averageScore ?: 0) >= 60 }
                        _comedyAnime.value = data.data.comedy.media.map { mapExploreMedia(it) }
                            .filter { (it.averageScore ?: 0) >= 60 }
                        _fantasyAnime.value = data.data.fantasy.media.map { mapExploreMedia(it) }
                            .filter { (it.averageScore ?: 0) >= 60 }
                        _scifiAnime.value = data.data.scifi.media.map { mapExploreMedia(it) }
                            .filter { (it.averageScore ?: 0) >= 60 }

                        Log.d(TAG, "Batched explore data loaded successfully - 9 queries in 1 request!")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse batched explore response", e)
                        fetchExploreDataIndividually()
                    }
                } else {
                    fetchExploreDataIndividually()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Batched explore request failed", e)
                fetchExploreDataIndividually()
            }

            _isLoadingExplore.value = false
            saveExploreDataToCache()
        }
    }

    private fun mapExploreMedia(media: ExploreMedia): ExploreAnime {
        return ExploreAnime(
            id = media.id,
            title = media.title.romaji ?: media.title.english ?: "Unknown",
            cover = media.coverImage?.large ?: media.coverImage?.medium ?: "",
            banner = media.bannerImage,
            episodes = media.episodes ?: 0,
            latestEpisode = media.nextAiringEpisode?.episode,
            averageScore = media.averageScore,
            genres = media.genres ?: emptyList(),
            year = media.startDate?.year ?: media.seasonYear,
            malId = media.idMal
        )
    }

    private suspend fun fetchExploreDataIndividually() = coroutineScope {
        Log.d(TAG, "Falling back to individual explore requests")

        val deferredFeatured = async { fetchFeaturedAnime() }
        val deferredSeasonal = async { fetchSeasonalAnime() }
        val deferredSeries = async { fetchTopSeries() }
        val deferredMovies = async { fetchTopMovies() }
        val deferredAction = async { fetchGenreAnime("Action", _actionAnime) }
        val deferredRomance = async { fetchGenreAnime("Romance", _romanceAnime) }
        val deferredComedy = async { fetchGenreAnime("Comedy", _comedyAnime) }
        val deferredFantasy = async { fetchGenreAnime("Fantasy", _fantasyAnime) }
        val deferredScifi = async { fetchGenreAnime("Sci-Fi", _scifiAnime) }

        awaitAll(deferredFeatured, deferredSeasonal, deferredSeries, deferredMovies,
            deferredAction, deferredRomance, deferredComedy, deferredFantasy, deferredScifi)
    }

    private suspend fun fetchFeaturedAnime() {
        val query = """
            query {
                Page(page: 1, perPage: 10) {
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
            }
        """.trimIndent()

        val response = publicGraphqlRequest(query, emptyMap())
        response?.let {
            try {
                val data = json.decodeFromString<ExploreResponse>(it)
                _featuredAnime.value = data.data.Page.media.map { media ->
                    ExploreAnime(
                        id = media.id,
                        title = media.title.romaji ?: media.title.english ?: "Unknown",
                        cover = media.coverImage?.large ?: "",
                        banner = media.bannerImage,
                        episodes = media.episodes ?: 0,
                        latestEpisode = media.nextAiringEpisode?.episode,
                        averageScore = media.averageScore,
                        genres = media.genres ?: emptyList(),
                        year = media.startDate?.year ?: media.seasonYear,
                        malId = media.idMal
                    )
                }
                Log.d(TAG, "Featured anime loaded: ${_featuredAnime.value.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse featured anime", e)
            }
        }
    }

    private suspend fun fetchSeasonalAnime() {
        val query = """
            query {
                Page(page: 1, perPage: 20) {
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
            }
        """.trimIndent()

        val response = publicGraphqlRequest(query, emptyMap())
        response?.let {
            try {
                val data = json.decodeFromString<ExploreResponse>(it)
                _seasonalAnime.value = data.data.Page.media.map { media ->
                    ExploreAnime(
                        id = media.id,
                        title = media.title.romaji ?: media.title.english ?: "Unknown",
                        cover = media.coverImage?.large ?: "",
                        banner = media.bannerImage,
                        episodes = media.episodes ?: 0,
                        latestEpisode = media.nextAiringEpisode?.episode,
                        averageScore = media.averageScore,
                        genres = media.genres ?: emptyList(),
                        year = media.startDate?.year ?: media.seasonYear,
                        malId = media.idMal
                    )
                }
                Log.d(TAG, "Seasonal anime loaded: ${_seasonalAnime.value.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse seasonal anime", e)
            }
        }
    }

    private suspend fun fetchTopSeries() {
        val query = """
            query {
                Page(page: 1, perPage: 20) {
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
            }
        """.trimIndent()

        try {
            val response = publicGraphqlRequest(query, emptyMap())
            if (response != null) {
                val data = json.decodeFromString<ExploreResponse>(response)

                val seriesList = data.data.Page.media.map { media ->
                    ExploreAnime(
                        id = media.id,
                        title = media.title.romaji ?: media.title.english ?: "Unknown",
                        cover = media.coverImage?.large ?: media.coverImage?.medium ?: "",
                        banner = media.bannerImage,
                        episodes = media.episodes ?: 0,
                        latestEpisode = media.nextAiringEpisode?.episode,
                        averageScore = media.averageScore,
                        genres = media.genres ?: emptyList(),
                        year = media.startDate?.year ?: media.seasonYear,
                        malId = media.idMal
                    )
                }

                _topSeries.value = if (seriesList.size > 10) {
                    seriesList.filter { (it.averageScore ?: 0) >= 70 }
                } else {
                    seriesList
                }

                Log.d(TAG, "Top series loaded: ${_topSeries.value.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch/parse top series", e)
        }
    }

    private suspend fun fetchTopMovies() {
        val query = """
            query {
                Page(page: 1, perPage: 20) {
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
            }
        """.trimIndent()

        try {
            val response = publicGraphqlRequest(query, emptyMap())
            if (response != null) {
                val data = json.decodeFromString<ExploreResponse>(response)

                val moviesList = data.data.Page.media.map { media ->
                    ExploreAnime(
                        id = media.id,
                        title = media.title.romaji ?: media.title.english ?: "Unknown",
                        cover = media.coverImage?.large ?: media.coverImage?.medium ?: "",
                        banner = media.bannerImage,
                        episodes = media.episodes ?: 0,
                        latestEpisode = media.nextAiringEpisode?.episode,
                        averageScore = media.averageScore,
                        genres = media.genres ?: emptyList(),
                        year = media.startDate?.year ?: media.seasonYear,
                        malId = media.idMal
                    )
                }

                _topMovies.value = if (moviesList.size > 10) {
                    moviesList.filter { (it.averageScore ?: 0) >= 70 }
                } else {
                    moviesList
                }

                Log.d(TAG, "Top movies loaded: ${_topMovies.value.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch/parse top movies", e)
        }
    }

    private suspend fun fetchGenreAnime(genre: String, stateFlow: MutableStateFlow<List<ExploreAnime>>) {
        val query = """
            query {
                Page(page: 1, perPage: 15) {
                    media(type: ANIME, genre: "$genre", sort: POPULARITY_DESC) {
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

        try {
            val response = publicGraphqlRequest(query, emptyMap())
            if (response != null) {
                val data = json.decodeFromString<ExploreResponse>(response)

                val animeList = data.data.Page.media.map { media ->
                    ExploreAnime(
                        id = media.id,
                        title = media.title.romaji ?: media.title.english ?: "Unknown",
                        cover = media.coverImage?.large ?: media.coverImage?.medium ?: "",
                        banner = media.bannerImage,
                        episodes = media.episodes ?: 0,
                        latestEpisode = media.nextAiringEpisode?.episode,
                        averageScore = media.averageScore,
                        genres = media.genres ?: emptyList(),
                        year = media.startDate?.year ?: media.seasonYear,
                        malId = media.idMal
                    )
                }

                stateFlow.value = animeList.filter { (it.averageScore ?: 0) >= 60 }
                Log.d(TAG, "$genre anime loaded: ${stateFlow.value.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch/parse $genre anime", e)
        }
    }

    fun fetchAiringSchedule() {
        viewModelScope.launch {
            try {
                _isLoadingSchedule.value = true

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
                    } catch (e: Exception) {
                        break
                    }
                }

                val airingList = allSchedules
                    .filter { it.media != null }
                    .map { schedule ->
                        AiringScheduleAnime(
                            id = schedule.media!!.id,
                            title = schedule.media.title.romaji ?: schedule.media.title.english ?: "Unknown",
                            cover = schedule.media.coverImage?.large ?: "",
                            episodes = schedule.media.episodes ?: 0,
                            airingEpisode = schedule.episode,
                            airingAt = schedule.airingAt,
                            timeUntilAiring = schedule.timeUntilAiring,
                            averageScore = schedule.media.averageScore,
                            genres = schedule.media.genres ?: emptyList(),
                            year = schedule.media.seasonYear,
                            malId = schedule.media.idMal
                        )
                    }
                    .sortedBy { it.airingAt }

                val scheduleByDay = mutableMapOf<Int, MutableList<AiringScheduleAnime>>()
                for (i in 0..6) scheduleByDay[i] = mutableListOf()

                airingList.forEach { anime ->
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = anime.airingAt * 1000L
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                    scheduleByDay[dayOfWeek]?.add(anime)
                }

                _airingSchedule.value = scheduleByDay.toMap()
                _airingAnimeList.value = airingList

                saveAiringScheduleCache()

                checkAndRefreshAiringAnime(airingList)

            } catch (e: Exception) {
                Log.e(TAG, "Airing fetch error", e)
            } finally {
                _isLoadingSchedule.value = false
            }
        }
    }

    private fun checkAndRefreshAiringAnime(airingList: List<AiringScheduleAnime>) {
        val currentTime = System.currentTimeMillis() / 1000
        val recentlyAired = airingList.filter {
            it.timeUntilAiring != null && it.timeUntilAiring < 300 && it.timeUntilAiring > -3600
        }

        if (recentlyAired.isNotEmpty()) {
            Log.d(TAG, "Found ${recentlyAired.size} recently aired anime, refreshing progress")
            viewModelScope.launch {
                refreshReleasingAnimeProgress()
            }
        }
    }

    fun refreshHome() {
        invalidateUserCache()

        viewModelScope.launch {
            _isLoadingHome.value = true
            fetchLists()
            _isLoadingHome.value = false
        }
    }

    fun forceRefreshExplore() {
        sharedPreferences.edit()
            .remove(CACHE_EXPLORE_TIME)
            .remove(CACHE_EXPLORE_DATA)
            .apply()
        graphqlCache.keys.removeAll { it.contains("featured") || it.contains("seasonal") || it.contains("topSeries") || it.contains("topMovies") }
        fetchExploreData()
    }

    suspend fun searchAnime(query: String): List<ExploreAnime> {
        if (query.isBlank()) return emptyList()

        Log.d(TAG, "Searching for: $query")

        return withContext(Dispatchers.IO) {
            try {
                val searchQuery = """
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

                Log.d(TAG, "Sending GraphQL query with search: $query")
                val response = publicGraphqlRequest(searchQuery, mapOf("search" to query))

                if (response != null) {
                    Log.d(TAG, "Search response received: ${response.take(100)}...")
                    val data = json.decodeFromString<ExploreResponse>(response)
                    val results = data.data.Page.media.map { media ->
                        ExploreAnime(
                            id = media.id,
                            title = media.title.romaji ?: media.title.english ?: "Unknown",
                            cover = media.coverImage?.large ?: media.coverImage?.medium ?: "",
                            banner = media.bannerImage,
                            episodes = media.episodes ?: 0,
                            latestEpisode = media.nextAiringEpisode?.episode,
                            averageScore = media.averageScore,
                            genres = media.genres ?: emptyList(),
                            year = media.startDate?.year ?: media.seasonYear,
                            malId = media.idMal
                        )
                    }
                    Log.d(TAG, "Search found ${results.size} results for: $query")
                    results
                } else {
                    Log.e(TAG, "Search returned null response for: $query")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for: $query", e)
                emptyList()
            }
        }
    }

    // Fetch detailed anime data from AniList - also caches the result
    suspend fun fetchDetailedAnimeData(animeId: Int): DetailedAnimeData? {
        // Check cache first
        _detailedAnimeCache.value[animeId]?.let {
            Log.d(TAG, "Using cached detailed data for anime $animeId")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
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

                val response = publicGraphqlRequest(query, mapOf("id" to animeId))

                if (response != null) {
                    val data = json.decodeFromString<DetailedAnimeResponse>(response)
                    val media = data.data.Media

                    val detailedData = DetailedAnimeData(
                        id = media.id,
                        title = media.title?.romaji ?: media.title?.english ?: "Unknown",
                        titleRomaji = media.title?.romaji,
                        titleEnglish = media.title?.english,
                        titleNative = media.title?.native,
                        cover = media.coverImage?.large ?: "",
                        banner = media.bannerImage,
                        description = media.description,
                        episodes = media.episodes ?: 0,
                        duration = media.duration,
                        status = media.status,
                        averageScore = media.averageScore,
                        popularity = media.popularity,
                        favourites = media.favourites,
                        genres = media.genres ?: emptyList(),
                        season = media.season,
                        year = media.seasonYear ?: media.startDate?.year,
                        format = media.format,
                        source = media.source,
                        studios = media.studios?.nodes?.map { node ->
                            com.blissless.anime.ui.screens.StudioData(
                                id = node.id ?: 0,
                                name = node.name ?: "",
                                isAnimationStudio = true
                            )
                        } ?: emptyList(),
                        startDate = media.startDate?.let { "${it.year ?: 0}-${it.month ?: 1}-${it.day ?: 1}" },
                        endDate = media.endDate?.let { "${it.year ?: 0}-${it.month ?: 1}-${it.day ?: 1}" },
                        nextAiringEpisode = media.nextAiringEpisode?.episode,
                        nextAiringTime = media.nextAiringEpisode?.airingAt
                    )

                    // Cache the result (in-memory only)
                    _detailedAnimeCache.value = _detailedAnimeCache.value + (animeId to detailedData)

                    detailedData
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch detailed anime data", e)
                null
            }
        }
    }

    // Try all servers with fallback - returns result with fallback info
    // ADDED: latestAiredEpisode check to prevent scraping unaired content
    suspend fun tryAllServersWithFallback(
        animeName: String,
        episodeNumber: Int,
        animeId: Int,
        latestAiredEpisode: Int = Int.MAX_VALUE // Default to max if not provided
    ): StreamFetchResult = withContext(Dispatchers.IO) {
        val preferredCategory = _preferredCategory.value
        val key = "${animeId}_$episodeNumber"

        // CHECK: Prevent scraping if episode hasn't aired
        // Log the values for debugging
        Log.d(TAG, "tryAllServersWithFallback: animeName=$animeName, ep=$episodeNumber, animeId=$animeId, latestAired=$latestAiredEpisode")

        // BLOCK if we know the aired limit and episode exceeds it
        if (latestAiredEpisode > 0 && latestAiredEpisode < Int.MAX_VALUE && episodeNumber > latestAiredEpisode) {
            Log.w(TAG, "BLOCKED: Episode $episodeNumber hasn't aired yet (latest: $latestAiredEpisode)")
            return@withContext StreamFetchResult(
                stream = null,
                isFallback = false,
                requestedCategory = preferredCategory,
                actualCategory = preferredCategory
            )
        }

        // SAFETY: If we have no episode info (latestAiredEpisode=0 or MAX_VALUE),
        // block episodes > 24 to prevent fetching unreasonable episode numbers
        // Most seasonal anime have <= 13-26 episodes
        if ((latestAiredEpisode <= 0 || latestAiredEpisode == Int.MAX_VALUE) && episodeNumber > 24) {
            Log.w(TAG, "BLOCKED: Episode $episodeNumber too high with no episode info (latest: $latestAiredEpisode)")
            return@withContext StreamFetchResult(
                stream = null,
                isFallback = false,
                requestedCategory = preferredCategory,
                actualCategory = preferredCategory
            )
        }

        // Check cache first
        _prefetchedStreams.value[key]?.let { cachedStream ->
            Log.d(TAG, "Using cached stream for $animeName ep $episodeNumber")
            val actualCategory = cachedStream?.category ?: preferredCategory
            return@withContext StreamFetchResult(
                stream = cachedStream,
                isFallback = cachedStream != null && actualCategory != preferredCategory,
                requestedCategory = preferredCategory,
                actualCategory = actualCategory
            )
        }

        // Get episode info (servers list)
        val epInfo = AniwatchService.getEpisodeInfo(animeName, episodeNumber)
        if (epInfo == null) {
            Log.e(TAG, "Failed to get episode info for $animeName ep $episodeNumber")
            return@withContext StreamFetchResult(
                stream = null,
                isFallback = false,
                requestedCategory = preferredCategory,
                actualCategory = preferredCategory
            )
        }

        // Cache episode info
        _prefetchedEpisodeInfo.value = _prefetchedEpisodeInfo.value + (key to epInfo)

        // Get servers for preferred category
        val preferredServers = if (preferredCategory == "dub") epInfo.dubServers else epInfo.subServers
        val fallbackServers = if (preferredCategory == "dub") epInfo.subServers else epInfo.dubServers
        val fallbackCategory = if (preferredCategory == "dub") "sub" else "dub"

        // Try preferred category servers first
        if (preferredServers.isNotEmpty()) {
            Log.d(TAG, "Trying ${preferredServers.size} $preferredCategory servers for $animeName ep $episodeNumber")

            for (server in preferredServers) {
                Log.d(TAG, "Trying server: ${server.name}")
                val result = AniwatchService.getStreamFromServer(
                    animeName,
                    episodeNumber,
                    server.name,
                    preferredCategory
                )

                if (result != null) {
                    Log.d(TAG, "Success with preferred server: ${server.name}")
                    _prefetchedStreams.value = _prefetchedStreams.value + (key to result)
                    saveStreamCache()
                    return@withContext StreamFetchResult(
                        stream = result,
                        isFallback = false,
                        requestedCategory = preferredCategory,
                        actualCategory = preferredCategory
                    )
                }
            }
        }

        // Preferred category failed or empty - try fallback category
        if (fallbackServers.isNotEmpty()) {
            Log.d(TAG, "Preferred $preferredCategory failed/empty, falling back to $fallbackCategory")

            for (server in fallbackServers) {
                Log.d(TAG, "Trying fallback server: ${server.name}")
                val result = AniwatchService.getStreamFromServer(
                    animeName,
                    episodeNumber,
                    server.name,
                    fallbackCategory
                )

                if (result != null) {
                    Log.d(TAG, "Success with fallback server: ${server.name}")
                    _prefetchedStreams.value = _prefetchedStreams.value + (key to result)
                    saveStreamCache()
                    return@withContext StreamFetchResult(
                        stream = result,
                        isFallback = true,
                        requestedCategory = preferredCategory,
                        actualCategory = fallbackCategory
                    )
                }
            }
        }

        Log.e(TAG, "All servers failed for $animeName ep $episodeNumber")
        StreamFetchResult(
            stream = null,
            isFallback = false,
            requestedCategory = preferredCategory,
            actualCategory = preferredCategory
        )
    }

    // Legacy method for backward compatibility - returns just the stream
    suspend fun tryAllServersInCategory(
        animeName: String,
        episodeNumber: Int,
        animeId: Int,
        latestAiredEpisode: Int = Int.MAX_VALUE
    ): AniwatchStreamResult? {
        val result = tryAllServersWithFallback(animeName, episodeNumber, animeId, latestAiredEpisode)
        return result.stream
    }

    // Get cached stream or fetch new one (with multi-server fallback)
    suspend fun getStreamLinkWithCache(animeName: String, episodeNumber: Int, animeId: Int, latestAiredEpisode: Int = Int.MAX_VALUE): AniwatchStreamResult? {
        return tryAllServersInCategory(animeName, episodeNumber, animeId, latestAiredEpisode)
    }

    // Get stream for specific server
    suspend fun getStreamForServer(
        animeName: String,
        episodeNumber: Int,
        serverName: String,
        category: String,
        animeId: Int
    ): AniwatchStreamResult? = withContext(Dispatchers.IO) {
        val key = "${animeId}_${episodeNumber}_${serverName}_$category"

        _prefetchedStreams.value[key]?.let { return@withContext it }

        val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, serverName, category)
        if (result != null) {
            _prefetchedStreams.value = _prefetchedStreams.value + (key to result)
            saveStreamCache()
        }

        result
    }

    // Pre-fetch stream for currently watching anime (next episode)
    fun prefetchCurrentEpisodeStream(anime: AnimeMedia) {
        val nextEp = anime.progress + 1
        val key = "${anime.id}_$nextEp"

        // Don't prefetch if next episode hasn't aired or totalEpisodes is unknown
        val latestAired = anime.latestEpisode ?: anime.totalEpisodes

        // BLOCK if we know the limit and next episode exceeds it
        if (latestAired > 0 && nextEp > latestAired) {
            Log.d(TAG, "Skipping pre-fetch for ${anime.title} ep $nextEp (not aired yet, latest: $latestAired)")
            return
        }

        // Don't prefetch if progress is unreasonably high compared to known total
        if (anime.totalEpisodes > 0 && nextEp > anime.totalEpisodes) {
            Log.d(TAG, "Skipping pre-fetch for ${anime.title} ep $nextEp (exceeds total: ${anime.totalEpisodes})")
            return
        }

        // SAFETY: If we have no episode info (totalEpisodes=0, latestEpisode=null),
        // only prefetch if next episode is reasonable (typically movies/specials have 1 episode)
        // Don't prefetch episode 2+ if we have no episode info
        if (latestAired <= 0 && nextEp > 1) {
            Log.d(TAG, "Skipping pre-fetch for ${anime.title} ep $nextEp (unknown episode count, only ep 1 allowed)")
            return
        }

        if (_prefetchedStreams.value.containsKey(key)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Pre-fetching stream for ${anime.title} ep $nextEp")
                val result = tryAllServersWithFallback(anime.title, nextEp, anime.id, latestAired)
                if (result.stream != null) {
                    Log.d(TAG, "Pre-fetch complete for ${anime.title} ep $nextEp")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pre-fetch failed for ${anime.title}", e)
            }
        }
    }

    // Pre-fetch adjacent episodes (previous and next)
    fun prefetchAdjacentEpisodes(animeName: String, currentEpisode: Int, animeId: Int, latestAiredEpisode: Int = Int.MAX_VALUE) {
        viewModelScope.launch(Dispatchers.IO) {
            // SAFETY: Determine if we have valid episode info
            val hasValidEpisodeInfo = latestAiredEpisode > 0 && latestAiredEpisode < Int.MAX_VALUE

            // Pre-fetch previous
            if (currentEpisode > 1) {
                val prevKey = "${animeId}_${currentEpisode - 1}"
                if (!_prefetchedStreams.value.containsKey(prevKey)) {
                    Log.d(TAG, "Pre-fetching previous episode: ${currentEpisode - 1}")
                    tryAllServersInCategory(animeName, currentEpisode - 1, animeId, latestAiredEpisode)
                }
            }

            // Pre-fetch next - only if it has aired
            val nextEp = currentEpisode + 1
            val shouldPrefetchNext = when {
                // We know the limit and next episode is within it
                hasValidEpisodeInfo && nextEp <= latestAiredEpisode -> true
                // No episode info, but next episode is reasonable (1-24 range)
                !hasValidEpisodeInfo && nextEp <= 24 -> true
                // Otherwise don't prefetch
                else -> false
            }

            if (shouldPrefetchNext) {
                val nextKey = "${animeId}_$nextEp"
                if (!_prefetchedStreams.value.containsKey(nextKey)) {
                    Log.d(TAG, "Pre-fetching next episode: $nextEp")
                    tryAllServersInCategory(animeName, nextEp, animeId, latestAiredEpisode)
                }
            } else {
                Log.d(TAG, "Skipping pre-fetch for ep $nextEp (not aired yet or no episode info, latest: $latestAiredEpisode)")
            }
        }
    }

    // Get episode info for server selection
    // ADDED: latestAiredEpisode check to prevent fetching unaired episodes
    suspend fun getEpisodeInfo(animeName: String, episodeNumber: Int, animeId: Int, latestAiredEpisode: Int = Int.MAX_VALUE): EpisodeStreams? {
        // Check if episode has aired (only if we have valid episode info)
        val hasValidEpisodeInfo = latestAiredEpisode > 0 && latestAiredEpisode < Int.MAX_VALUE

        if (hasValidEpisodeInfo && episodeNumber > latestAiredEpisode) {
            Log.w(TAG, "Episode $episodeNumber hasn't aired yet (latest: $latestAiredEpisode), skipping fetch")
            return null
        }

        // SAFETY: Block unreasonably high episode numbers when we have no episode info
        if (!hasValidEpisodeInfo && episodeNumber > 24) {
            Log.w(TAG, "Episode $episodeNumber too high with no episode info (latest: $latestAiredEpisode), skipping fetch")
            return null
        }

        val key = "${animeId}_$episodeNumber"
        _prefetchedEpisodeInfo.value[key]?.let { return it }

        val result = AniwatchService.getEpisodeInfo(animeName, episodeNumber)
        if (result != null) {
            _prefetchedEpisodeInfo.value = _prefetchedEpisodeInfo.value + (key to result)
        }
        return result
    }

    // Legacy method for backward compatibility
    suspend fun getStreamLink(animeTitle: String, episodeNumber: Int): AniwatchStreamResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting stream link for $animeTitle episode $episodeNumber")
            AniwatchService.getStreamLink(animeTitle, episodeNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stream link", e)
            null
        }
    }

    fun removeAnimeFromList(mediaId: Int) {
        Log.d(TAG, "removeAnimeFromList: mediaId=$mediaId")

        val watchingEntry = _currentlyWatching.value.find { it.id == mediaId }?.listEntryId
        val planningEntry = _planningToWatch.value.find { it.id == mediaId }?.listEntryId
        val completedEntry = _completed.value.find { it.id == mediaId }?.listEntryId
        val onHoldEntry = _onHold.value.find { it.id == mediaId }?.listEntryId
        val droppedEntry = _dropped.value.find { it.id == mediaId }?.listEntryId
        val entryId = watchingEntry ?: planningEntry ?: completedEntry ?: onHoldEntry ?: droppedEntry

        if (entryId == null) {
            Log.e(TAG, "Could not find list entry ID for mediaId=$mediaId")
            return
        }

        viewModelScope.launch {
            val query = """
                mutation (${'$'}id: Int) {
                    DeleteMediaListEntry(id: ${'$'}id) {
                        deleted
                    }
                }
            """.trimIndent()

            invalidateUserCache()

            val response = graphqlRequest(query, mapOf("id" to entryId))
            if (response != null) {
                Log.d(TAG, "Remove from list SUCCESS")
                _currentlyWatching.value = _currentlyWatching.value.filter { it.id != mediaId }
                _planningToWatch.value = _planningToWatch.value.filter { it.id != mediaId }
                _completed.value = _completed.value.filter { it.id != mediaId }
                _onHold.value = _onHold.value.filter { it.id != mediaId }
                _dropped.value = _dropped.value.filter { it.id != mediaId }
                fetchLists()
            } else {
                Log.e(TAG, "Remove from list FAILED")
            }
        }
    }

    // User favorites - LOCAL ONLY with stored anime data
    @Serializable
    data class StoredFavorite(
        val id: Int,
        val title: String,
        val cover: String,
        val banner: String? = null,
        val year: Int? = null,
        val averageScore: Int? = null
    )

    private val _localFavorites = MutableStateFlow<Map<Int, StoredFavorite>>(emptyMap())
    val localFavorites: StateFlow<Map<Int, StoredFavorite>> = _localFavorites.asStateFlow()

    val localFavoriteIds: Set<Int> get() = _localFavorites.value.keys

    private val _userFavorites = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val userFavorites: StateFlow<List<ExploreAnime>> = _userFavorites.asStateFlow()

    private val _isLoadingFavorites = MutableStateFlow(false)
    val isLoadingFavorites: StateFlow<Boolean> = _isLoadingFavorites.asStateFlow()

    fun toggleLocalFavorite(mediaId: Int, title: String = "", cover: String = "", banner: String? = null, year: Int? = null, averageScore: Int? = null) {
        val currentFavorites = _localFavorites.value.toMutableMap()
        val existingFavorite = currentFavorites[mediaId]

        if (existingFavorite != null) {
            if (existingFavorite.title.isEmpty() && title.isNotEmpty()) {
                currentFavorites[mediaId] = StoredFavorite(mediaId, title, cover, banner, year, averageScore)
            } else {
                currentFavorites.remove(mediaId)
            }
        } else {
            if (currentFavorites.size >= 10) return

            if (title.isEmpty()) {
                val allAnime = _currentlyWatching.value + _completed.value + _planningToWatch.value + _onHold.value + _dropped.value
                val anime = allAnime.find { it.id == mediaId }
                if (anime != null) {
                    currentFavorites[mediaId] = StoredFavorite(mediaId, anime.title, anime.cover, anime.banner, anime.year, anime.averageScore)
                }
            } else {
                currentFavorites[mediaId] = StoredFavorite(mediaId, title, cover, banner, year, averageScore)
            }
        }
        _localFavorites.value = currentFavorites
        saveLocalFavorites(currentFavorites)
    }

    fun toggleLocalFavorite(anime: ExploreAnime) {
        toggleLocalFavorite(
            mediaId = anime.id,
            title = anime.title,
            cover = anime.cover,
            banner = anime.banner,
            year = anime.year,
            averageScore = anime.averageScore
        )
    }

    fun toggleLocalFavorite(anime: AnimeMedia) {
        toggleLocalFavorite(
            mediaId = anime.id,
            title = anime.title,
            cover = anime.cover,
            banner = anime.banner,
            year = anime.year,
            averageScore = anime.averageScore
        )
    }

    fun toggleLocalFavorite(anime: DetailedAnimeData) {
        toggleLocalFavorite(
            mediaId = anime.id,
            title = anime.title,
            cover = anime.cover,
            banner = anime.banner,
            year = anime.year,
            averageScore = anime.averageScore
        )
    }

    fun isLocalFavorite(mediaId: Int): Boolean = _localFavorites.value.containsKey(mediaId)

    fun canAddFavorite(): Boolean = _localFavorites.value.size < 10

    private fun saveLocalFavorites(favorites: Map<Int, StoredFavorite>) {
        val json = Json { ignoreUnknownKeys = true }
        val favoritesJson = favorites.values.map { fav ->
            json.encodeToString(serializer = StoredFavorite.serializer(), value = fav)
        }.toSet()
        sharedPreferences.edit()
            .putStringSet("local_favorites_v2", favoritesJson)
            .apply()
    }

    private fun loadLocalFavorites() {
        val json = Json { ignoreUnknownKeys = true }
        val savedV2 = sharedPreferences.getStringSet("local_favorites_v2", null)
        if (savedV2 != null) {
            val favorites = mutableMapOf<Int, StoredFavorite>()
            savedV2.forEach { favJson ->
                try {
                    val fav = json.decodeFromString(StoredFavorite.serializer(), favJson)
                    favorites[fav.id] = fav
                } catch (e: Exception) {
                }
            }
            _localFavorites.value = favorites
            return
        }

        val saved = sharedPreferences.getStringSet("local_favorites", emptySet()) ?: emptySet()
        val favorites = mutableMapOf<Int, StoredFavorite>()
        saved.mapNotNull { it.toIntOrNull() }.forEach { id ->
            favorites[id] = StoredFavorite(id, "", "")
        }
        _localFavorites.value = favorites
    }

    fun getLocalFavoriteCount(): Int = _localFavorites.value.size

    fun fetchUserFavorites() {
        val userId = _userId.value ?: return
        viewModelScope.launch {
            _isLoadingFavorites.value = true

            val query = """
                query (${'$'}userId: Int) {
                    User(id: ${'$'}userId) {
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
            """.trimIndent()

            try {
                val response = graphqlRequest(query, mapOf("userId" to userId))
                if (response != null) {
                    val data = json.decodeFromString<UserFavoritesResponse>(response)
                    _userFavorites.value = data.data.User.favourites.anime.nodes.map { media ->
                        ExploreAnime(
                            id = media.id,
                            title = media.title.romaji ?: media.title.english ?: "Unknown",
                            cover = media.coverImage?.large ?: "",
                            banner = null,
                            episodes = media.episodes ?: 0,
                            latestEpisode = null,
                            averageScore = media.averageScore,
                            genres = media.genres ?: emptyList(),
                            year = media.seasonYear
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch favorites", e)
            }

            _isLoadingFavorites.value = false
        }
    }

    private val _userActivity = MutableStateFlow<List<UserActivity>>(emptyList())
    val userActivity: StateFlow<List<UserActivity>> = _userActivity.asStateFlow()

    fun fetchUserActivity() {
        val userId = _userId.value ?: return

        viewModelScope.launch {
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

            try {
                val response = graphqlRequest(query, mapOf("userId" to userId))

                if (response != null) {
                    try {
                        val data = json.decodeFromString<SimpleActivityResponse>(response)
                        val activities = data.data.Page.activities

                        _userActivity.value = activities.mapIndexedNotNull { index, activity ->
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
                    } catch (parseError: Exception) {
                        Log.e(TAG, "Activity parse error", parseError)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Activity network error", e)
            }
        }
    }

    fun updateAnimeRating(mediaId: Int, score: Int) {
        viewModelScope.launch {
            val query = """
                mutation (${'$'}mediaId: Int, ${'$'}score: Int) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, score: ${'$'}score) {
                        id
                        score
                    }
                }
            """.trimIndent()

            val response = graphqlRequest(query, mapOf("mediaId" to mediaId, "score" to score))
            if (response != null) {
                Log.d(TAG, "Rating updated successfully")
                fetchLists()
            }
        }
    }

    fun toggleAnimeFavorite(mediaId: Int, isCurrentlyFavorite: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val query = if (isCurrentlyFavorite) {
                """
                mutation (${'$'}mediaId: Int) {
                    DeleteFavourite(anime: ${'$'}mediaId) {
                        deleted
                    }
                }
                """.trimIndent()
            } else {
                """
                mutation (${'$'}mediaId: Int) {
                    ToggleFavourite(animeId: ${'$'}mediaId) {
                        anime {
                            nodes { id }
                        }
                    }
                }
                """.trimIndent()
            }

            val response = graphqlRequest(query, mapOf("mediaId" to mediaId))
            if (response != null) {
                fetchUserFavorites()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun getFavoriteCount(): Int = _userFavorites.value.size

    private val _completedSearchResults = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val completedSearchResults: StateFlow<List<AnimeMedia>> = _completedSearchResults.asStateFlow()

    fun searchCompletedAnime(query: String) {
        val completedList = _completed.value
        if (query.isEmpty()) {
            _completedSearchResults.value = completedList
        } else {
            _completedSearchResults.value = completedList.filter {
                it.title.contains(query, ignoreCase = true)
            }
        }
    }

    fun loadAllCompletedAnime() {
        _completedSearchResults.value = _completed.value
    }

    // ============================================================================
    // TMDB API for episode details - IMPROVED WITH ANIWATCH EPISODE OFFSET
    // ============================================================================
    private val tmdbBearerToken = BuildConfig.TMDB_API_KEY

    private fun extractSeasonInfo(title: String): Pair<String, Int> {
        val lower = title.lowercase().trim()

        // Specific Keyword Mappings for common anime
        when {
            lower.contains("culling game") -> return Pair("Jujutsu Kaisen", 3)
            lower.contains("shibuya incident") -> return Pair("Jujutsu Kaisen", 2)
            lower.contains("hidden inventory") -> return Pair("Jujutsu Kaisen", 2)
            lower.contains("shimetsu kaiyuu") -> return Pair("Jujutsu Kaisen", 3)
            lower.contains("the final season") && lower.contains("part 3") -> return Pair("Attack on Titan", 4)
            lower.contains("the final season") && lower.contains("part 2") -> return Pair("Attack on Titan", 4)
            lower.contains("the final season") -> return Pair("Attack on Titan", 4)
            lower.contains("swordsmith village") -> return Pair("Demon Slayer: Kimetsu no Yaiba", 3)
            lower.contains("entertainment district") -> return Pair("Demon Slayer: Kimetsu no Yaiba", 2)
            lower.contains("mugen train") -> return Pair("Demon Slayer: Kimetsu no Yaiba", 2)
            lower.contains("cour 2") -> return Pair("SPY×FAMILY", 2)
        }

        // Regex for Season/Part/Cour patterns
        val patterns = listOf(
            Regex("season\\s*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("(\\d+)(?:st|nd|rd|th)\\s*season", RegexOption.IGNORE_CASE),
            Regex("part\\s*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("cour\\s*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("s(\\d+)(?:\\s|$|-)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(title)?.let { matchResult ->
                val seasonNum = matchResult.groupValues[1].toIntOrNull() ?: 1
                val matchStartIndex = matchResult.range.first
                val cleanTitle = title.substring(0, matchStartIndex)
                    .trim()
                    .removeSuffix(":")
                    .removeSuffix("-")
                    .removeSuffix("(")
                    .trim()
                return Pair(cleanTitle.ifEmpty { title }, seasonNum)
            }
        }

        // NEW: Fallback - extract base title by splitting on common delimiters
        // This handles cases like "Jujutsu Kaisen: Shimetsu Kaiyuu - Zenpen"
        // Split on ":", "-", or " - " and take the first part as base title
        val delimiters = listOf(" - ", ": ", " -", ":")
        for (delimiter in delimiters) {
            if (title.contains(delimiter)) {
                val baseTitle = title.substringBefore(delimiter).trim()
                if (baseTitle.length >= 3) {
                    Log.d(TAG, "Extracted base title '$baseTitle' from '$title' using delimiter '$delimiter'")
                    return Pair(baseTitle, 1)
                }
            }
        }

        return Pair(title, 1)
    }

    // Episode offset calculation
    // Returns the number of episodes that come BEFORE this anime in the franchise
    private suspend fun calculateEpisodeOffset(animeId: Int, animeTitle: String): Int {
        Log.d(TAG, "=== calculateEpisodeOffset for $animeTitle ===")

        // Check cache first
        episodeOffsetCache[animeId]?.let {
            Log.d(TAG, "Using cached offset: $it")
            return it
        }

        // Extract season info to determine if this is a sequel
        val (baseTitle, detectedSeason) = extractSeasonInfo(animeTitle)

        if (detectedSeason <= 1) {
            Log.d(TAG, "Season 1 or standalone - no offset needed")
            return 0
        }

        // KNOWN OFFSETS: Hardcoded offsets for popular anime franchises
        // This is the most reliable method for sequels
        val lower = animeTitle.lowercase()
        val knownOffsets = when {
            // Jujutsu Kaisen: S1 = 24 eps, S2 = 23 eps
            lower.contains("jujutsu kaisen") && (lower.contains("shimetsu") || lower.contains("culling game")) -> 47
            lower.contains("jujutsu kaisen") && (lower.contains("shibuya") || lower.contains("hidden inventory")) -> 24
            lower.contains("jujutsu kaisen") && detectedSeason == 2 -> 24
            lower.contains("jujutsu kaisen") && detectedSeason == 3 -> 47

            // Attack on Titan: S1=25, S2=12, S3=22, S4P1=16, S4P2=12, S4P3=2
            lower.contains("attack on titan") && lower.contains("final season") && lower.contains("part 3") -> 87
            lower.contains("attack on titan") && lower.contains("final season") && lower.contains("part 2") -> 75
            lower.contains("attack on titan") && lower.contains("final season") -> 59
            lower.contains("attack on titan") && detectedSeason == 4 -> 59
            lower.contains("attack on titan") && detectedSeason == 3 -> 37
            lower.contains("attack on titan") && detectedSeason == 2 -> 25

            // Demon Slayer: S1=26, Mugen=7 (movie/eps), Entertainment=11
            lower.contains("demon slayer") && lower.contains("swordsmith") -> 44
            lower.contains("demon slayer") && lower.contains("entertainment") -> 33
            lower.contains("demon slayer") && lower.contains("mugen train") -> 26
            lower.contains("demon slayer") && detectedSeason == 3 -> 44
            lower.contains("demon slayer") && detectedSeason == 2 -> 26

            // SPY×FAMILY: S1=25, S2=12
            lower.contains("spy") && lower.contains("family") && detectedSeason == 2 -> 25

            // My Hero Academia: S1=13, S2=25, S3=25, S4=25, S5=25, S6=25, S7=?
            lower.contains("my hero academia") && detectedSeason == 2 -> 13
            lower.contains("my hero academia") && detectedSeason == 3 -> 38
            lower.contains("my hero academia") && detectedSeason == 4 -> 63
            lower.contains("my hero academia") && detectedSeason == 5 -> 88
            lower.contains("my hero academia") && detectedSeason == 6 -> 113
            lower.contains("my hero academia") && detectedSeason == 7 -> 138

            // One Punch Man: S1=12, S2=12
            lower.contains("one punch") && detectedSeason == 2 -> 12

            // Mob Psycho 100: S1=12, S2=13, S3=12
            lower.contains("mob psycho") && detectedSeason == 2 -> 12
            lower.contains("mob psycho") && detectedSeason == 3 -> 25

            else -> null
        }

        if (knownOffsets != null) {
            Log.d(TAG, "Using known offset for $animeTitle: $knownOffsets")
            episodeOffsetCache[animeId] = knownOffsets
            return knownOffsets
        }

        // ESTIMATE: Use typical season lengths based on detected season
        // Most anime seasons are either 12-13 (1 cour) or 24-26 (2 cour)
        // For estimation, assume 24 episodes per season (2 cour average)
        val estimatedOffset = (detectedSeason - 1) * 24
        Log.d(TAG, "Using estimated offset: $estimatedOffset (season $detectedSeason, 24 eps/season)")
        episodeOffsetCache[animeId] = estimatedOffset
        return estimatedOffset
    }

    // FIXED: TMDB scraper with proper fallback strategy
    // 1. First try exact anime title on TMDB
    // 2. If found with episodes, use it directly
    // 3. If not found, calculate offset from prequels and search for base series name
    // 4. Filter out unaired episodes based on latestAiredEpisode parameter
    suspend fun fetchTmdbEpisodes(animeTitle: String, animeId: Int, animeYear: Int? = null, latestAiredEpisode: Int = Int.MAX_VALUE): List<TmdbEpisode> = withContext(Dispatchers.IO) {
        // Local data class for TMDB results - defined FIRST before helper functions
        data class TvResult(val id: Int, val name: String, val year: Int?)

        try {
            Log.d(TAG, "=== fetchTmdbEpisodes ===")
            Log.d(TAG, "Anime: $animeTitle (ID: $animeId, Year: $animeYear)")

            // Helper function to search TMDB
            fun searchTmdb(query: String): List<TvResult> {
                val searchUrl = "https://api.themoviedb.org/3/search/multi?query=${URLEncoder.encode(query, "UTF-8")}&include_adult=false&language=en-US&page=1"

                val searchConnection = URL(searchUrl).openConnection() as HttpsURLConnection
                searchConnection.requestMethod = "GET"
                searchConnection.setRequestProperty("accept", "application/json")
                searchConnection.setRequestProperty("Authorization", "Bearer $tmdbBearerToken")

                if (searchConnection.responseCode != 200) {
                    Log.e(TAG, "TMDB search failed: HTTP ${searchConnection.responseCode}")
                    return emptyList()
                }

                val searchResponse = searchConnection.inputStream.bufferedReader().readText()
                val searchJson = json.parseToJsonElement(searchResponse).jsonObject
                val results = searchJson["results"]?.jsonArray ?: return emptyList()

                return results.mapNotNull { result ->
                    val resultObj = result.jsonObject
                    if (resultObj["media_type"]?.jsonPrimitive?.content == "tv") {
                        val id = resultObj["id"]?.jsonPrimitive?.int
                        val name = resultObj["name"]?.jsonPrimitive?.content
                        val dateStr = resultObj["first_air_date"]?.jsonPrimitive?.content
                        val year = dateStr?.split("-")?.firstOrNull()?.toIntOrNull()
                        if (id != null && name != null) TvResult(id, name, year) else null
                    } else null
                }
            }

            // Helper function to fetch TV show details (number of seasons)
            fun fetchTvDetails(id: Int): Int {
                val detailsUrl = "https://api.themoviedb.org/3/tv/$id"
                val detailsConn = URL(detailsUrl).openConnection() as HttpsURLConnection
                detailsConn.requestMethod = "GET"
                detailsConn.setRequestProperty("accept", "application/json")
                detailsConn.setRequestProperty("Authorization", "Bearer $tmdbBearerToken")

                if (detailsConn.responseCode != 200) return 1

                val detailsResp = detailsConn.inputStream.bufferedReader().readText()
                val detailsJson = json.parseToJsonElement(detailsResp).jsonObject
                return detailsJson["number_of_seasons"]?.jsonPrimitive?.int ?: 1
            }

            // Helper function to fetch a specific season
            fun fetchSeason(id: Int, sNum: Int): List<TmdbEpisode>? {
                val sUrl = "https://api.themoviedb.org/3/tv/$id/season/$sNum"
                val sConn = URL(sUrl).openConnection() as HttpsURLConnection
                sConn.requestMethod = "GET"
                sConn.setRequestProperty("accept", "application/json")
                sConn.setRequestProperty("Authorization", "Bearer $tmdbBearerToken")

                if (sConn.responseCode != 200) return null

                val sResp = sConn.inputStream.bufferedReader().readText()
                val sJson = json.parseToJsonElement(sResp).jsonObject
                val eps = sJson["episodes"]?.jsonArray ?: return emptyList()

                return eps.map { ep ->
                    val epObj = ep.jsonObject
                    val stillPath = epObj["still_path"]?.jsonPrimitive?.contentOrNull
                    TmdbEpisode(
                        episode = epObj["episode_number"]?.jsonPrimitive?.int ?: 0,
                        title = epObj["name"]?.jsonPrimitive?.content ?: "",
                        description = epObj["overview"]?.jsonPrimitive?.content ?: "",
                        image = if (stillPath != null) "https://image.tmdb.org/t/p/w500$stillPath" else null
                    )
                }.takeIf { it.isNotEmpty() }
            }

            // Helper to fetch ALL seasons with continuous episode numbering
            fun fetchAllSeasons(id: Int): List<TmdbEpisode> {
                val numSeasons = fetchTvDetails(id)
                Log.d(TAG, "TV show has $numSeasons seasons")

                val allEpisodes = mutableListOf<TmdbEpisode>()
                var episodeCounter = 1

                for (sNum in 1..numSeasons) {
                    val seasonEps = fetchSeason(id, sNum) ?: continue
                    // Renumber episodes continuously across seasons
                    seasonEps.forEach { ep ->
                        allEpisodes.add(ep.copy(episode = episodeCounter++))
                    }
                }

                Log.d(TAG, "Fetched ${allEpisodes.size} total episodes from $numSeasons seasons")
                return allEpisodes
            }

            // Helper to find best match from results
            fun findBestMatch(results: List<TvResult>, query: String, year: Int?): TvResult? {
                var bestMatch: TvResult? = null
                var highestScore = -1
                val normalizedQuery = query.lowercase().trim()

                for (result in results) {
                    var score = 0
                    val normalizedName = result.name.lowercase().trim()

                    if (normalizedName == normalizedQuery) score += 100
                    else if (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)) score += 10
                    if (year != null && result.year == year) score += 50

                    if (score > highestScore) {
                        highestScore = score
                        bestMatch = result
                    }
                }
                return bestMatch ?: results.firstOrNull()
            }

            // STEP 1: Try exact anime title first
            Log.d(TAG, "Step 1: Trying exact title: $animeTitle")
            var tvResults = searchTmdb(animeTitle)
            var bestMatch = findBestMatch(tvResults, animeTitle, animeYear)

            if (bestMatch != null) {
                Log.d(TAG, "Found match for exact title: ${bestMatch.name} (ID: ${bestMatch.id})")
                val episodes = fetchSeason(bestMatch.id, 1)

                if (episodes != null && episodes.isNotEmpty()) {
                    // Filter out unaired episodes
                    val filteredEpisodes = if (latestAiredEpisode < Int.MAX_VALUE) {
                        episodes.filter { it.episode <= latestAiredEpisode }.also {
                            if (it.size < episodes.size) {
                                Log.d(TAG, "Filtered to ${it.size} aired episodes (was ${episodes.size}, latest: $latestAiredEpisode)")
                            }
                        }
                    } else {
                        episodes
                    }
                    Log.d(TAG, "Got ${filteredEpisodes.size} episodes directly from exact match")
                    return@withContext filteredEpisodes
                }
            }

            // STEP 2: Exact title didn't work - fallback to base series with offset
            Log.d(TAG, "Step 2: Exact title not found, using offset calculation...")

            // Extract base title for searching
            val (baseTitle, detectedSeason) = extractSeasonInfo(animeTitle)
            Log.d(TAG, "Extracted: baseTitle='$baseTitle', detectedSeason=$detectedSeason")

            // Search for base series
            tvResults = searchTmdb(baseTitle)
            bestMatch = findBestMatch(tvResults, baseTitle, null)

            if (bestMatch == null) {
                Log.w(TAG, "No TV shows found in TMDB for base title: $baseTitle")
                return@withContext emptyList()
            }

            val tvId = bestMatch.id
            Log.d(TAG, "Selected base series: ${bestMatch.name} (ID: $tvId)")

            // Fetch ALL seasons with continuous episode numbering
            var episodes = fetchAllSeasons(tvId)

            if (episodes.isEmpty()) {
                Log.w(TAG, "No episodes found for $baseTitle")
                return@withContext emptyList()
            }

            Log.d(TAG, "Fetched ${episodes.size} total episodes from TMDB with continuous numbering")

            // Calculate episode offset for this sequel/season
            val episodeOffset = calculateEpisodeOffset(animeId, animeTitle)
            Log.d(TAG, "Episode offset: $episodeOffset")

            // Apply episode offset logic:
            // If offset is 47, TMDB episodes 48+ correspond to this anime's episodes 1, 2, 3...
            if (episodeOffset > 0) {
                val startEpisode = episodeOffset + 1
                episodes = episodes
                    .filter { it.episode >= startEpisode }
                    .map { ep ->
                        ep.copy(episode = ep.episode - episodeOffset)
                    }
                Log.d(TAG, "Mapped TMDB episodes $startEpisode+ to local episodes 1+ (offset: $episodeOffset, got ${episodes.size} episodes)")
            }

            // Filter out unaired episodes based on latestAiredEpisode
            val filteredEpisodes = if (latestAiredEpisode < Int.MAX_VALUE) {
                episodes.filter { it.episode <= latestAiredEpisode }.also {
                    if (it.size < episodes.size) {
                        Log.d(TAG, "Filtered to ${it.size} aired episodes (was ${episodes.size}, latest: $latestAiredEpisode)")
                    }
                }
            } else {
                episodes
            }

            Log.d(TAG, "Fetched ${filteredEpisodes.size} episodes from TMDB (season $detectedSeason, offset: $episodeOffset)")
            filteredEpisodes

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB episodes for $animeTitle", e)
            emptyList()
        }
    }
}

// Data classes for cache and response models
@Serializable
data class StreamCacheData(val entries: Map<String, StreamCacheEntry>)

@Serializable
data class StreamCacheEntry(
    val stream: CachedStream?,
    val episodeInfo: CachedEpisodeInfo?,
    val timestamp: Long
)

@Serializable
data class CachedStream(
    val url: String,
    val isDirectStream: Boolean,
    val headers: Map<String, String>?,
    val subtitleUrl: String?,
    val serverName: String,
    val category: String
)

@Serializable
data class CachedEpisodeInfo(
    val subServers: List<CachedServer>,
    val dubServers: List<CachedServer>,
    val animeId: String,
    val episodeId: String
)

@Serializable
data class CachedServer(val name: String, val url: String)

@Serializable
data class AiringCacheData(
    val scheduleByDay: Map<Int, List<AiringScheduleAnime>>,
    val airingAnimeList: List<AiringScheduleAnime>
)

@Serializable
data class ExploreCacheData(
    val featuredAnime: List<ExploreAnime>,
    val seasonalAnime: List<ExploreAnime>,
    val topSeries: List<ExploreAnime>,
    val topMovies: List<ExploreAnime>,
    val actionAnime: List<ExploreAnime>,
    val romanceAnime: List<ExploreAnime>,
    val comedyAnime: List<ExploreAnime>,
    val fantasyAnime: List<ExploreAnime>,
    val scifiAnime: List<ExploreAnime>
)

@Serializable
data class HomeCacheData(
    val currentlyWatching: List<AnimeMedia>,
    val planningToWatch: List<AnimeMedia>,
    val completed: List<AnimeMedia>,
    val onHold: List<AnimeMedia>,
    val dropped: List<AnimeMedia>,
    val userId: Int?,
    val userName: String?,
    val userAvatar: String?
)

data class TmdbEpisode(
    val episode: Int,
    val title: String,
    val description: String,
    val image: String?
)

@Serializable
data class ExploreAnime(
    val id: Int,
    val title: String,
    val cover: String,
    val banner: String?,
    val episodes: Int,
    val latestEpisode: Int?,
    val averageScore: Int?,
    val genres: List<String>,
    val year: Int? = null,
    val malId: Int? = null
)

@Serializable
data class AnimeMedia(
    val id: Int,
    val title: String,
    val cover: String,
    val banner: String? = null,
    val progress: Int = 0,
    val totalEpisodes: Int = 0,
    val latestEpisode: Int? = null,
    val status: String = "",
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val listStatus: String = "",
    val listEntryId: Int? = null,
    val year: Int? = null,
    val malId: Int? = null
)

@Serializable
data class ViewerResponse(val data: ViewerData)

@Serializable
data class ViewerData(val Viewer: Viewer)

@Serializable
data class Viewer(
    val id: Int,
    val name: String,
    val avatar: Avatar?
)

@Serializable
data class Avatar(val medium: String)

@Serializable
data class MediaListResponse(val data: MediaListData)

@Serializable
data class MediaListData(val MediaListCollection: MediaListCollection)

@Serializable
data class MediaListCollection(val lists: List<MediaList>)

@Serializable
data class MediaList(
    val name: String,
    val status: String?,
    val entries: List<MediaListEntry>
)

@Serializable
data class MediaListEntry(
    val id: Int,
    val mediaId: Int,
    val progress: Int?,
    val status: String?,
    val media: MediaEntryMedia
)

@Serializable
data class MediaEntryMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val bannerImage: String?,
    val episodes: Int?,
    val nextAiringEpisode: NextAiringEpisode?,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?,
    val seasonYear: Int? = null
)

@Serializable
data class ExploreResponse(val data: ExploreData)

@Serializable
data class ExploreData(val Page: ExplorePage)

@Serializable
data class ExplorePage(val media: List<ExploreMedia>)

@Serializable
data class ExploreMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val bannerImage: String?,
    val episodes: Int?,
    val nextAiringEpisode: NextAiringEpisode? = null,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?,
    val seasonYear: Int? = null,
    val startDate: FuzzyDate? = null
)

@Serializable
data class BatchedExploreResponse(val data: BatchedExploreData)

@Serializable
data class BatchedExploreData(
    val featured: ExplorePage,
    val seasonal: ExplorePage,
    val topSeries: ExplorePage,
    val topMovies: ExplorePage,
    val action: ExplorePage,
    val romance: ExplorePage,
    val comedy: ExplorePage,
    val fantasy: ExplorePage,
    val scifi: ExplorePage
)

@Serializable
data class FuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class MediaTitle(
    val romaji: String?,
    val english: String?
)

@Serializable
data class MediaCoverImage(
    val large: String? = null,
    val medium: String? = null
)

@Serializable
data class NextAiringEpisode(
    val episode: Int? = null,
    val airingAt: Long? = null,
    val timeUntilAiring: Long? = null
)

@Serializable
data class AiringScheduleAnime(
    val id: Int,
    val title: String,
    val cover: String,
    val episodes: Int = 0,
    val airingEpisode: Int = 0,
    val airingAt: Long = 0,
    val timeUntilAiring: Long? = null,
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val malId: Int? = null
)

@Serializable
data class AiringScheduleResponse(val data: AiringScheduleData)

@Serializable
data class AiringScheduleData(val Page: AiringSchedulePage)

@Serializable
data class AiringSchedulePage(val airingSchedules: List<AiringScheduleEntry>)

@Serializable
data class AiringScheduleEntry(
    val id: Int,
    val airingAt: Long,
    val episode: Int,
    val timeUntilAiring: Long? = null,
    val mediaId: Int,
    val media: AiringScheduleMedia?
)

@Serializable
data class AiringScheduleMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val episodes: Int?,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?,
    val seasonYear: Int? = null
)

@Serializable
data class UserActivity(
    val id: Int,
    val type: String,
    val status: String,
    val progress: String?,
    val createdAt: Long,
    val mediaId: Int,
    val mediaTitle: String,
    val mediaCover: String,
    val episodes: Int?,
    val averageScore: Int?,
    val year: Int? = null
)

@Serializable
data class UserFavoritesResponse(val data: UserFavoritesData)

@Serializable
data class UserFavoritesData(val User: UserFavoritesUser)

@Serializable
data class UserFavoritesUser(val favourites: UserFavorites)

@Serializable
data class UserFavorites(val anime: UserFavoritesAnime)

@Serializable
data class UserFavoritesAnime(val nodes: List<UserFavoriteAnime>)

@Serializable
data class UserFavoriteAnime(
    val id: Int,
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val episodes: Int?,
    val averageScore: Int?,
    val genres: List<String>?,
    val seasonYear: Int? = null
)

@Serializable
data class SimpleActivityResponse(val data: SimpleActivityData)

@Serializable
data class SimpleActivityData(val Page: SimpleActivityPage)

@Serializable
data class SimpleActivityPage(val activities: List<SimpleActivityEntry>)

@Serializable
data class SimpleActivityEntry(
    val createdAt: Long,
    val status: String?,
    val progress: String?,
    val media: SimpleActivityMedia?
)

@Serializable
data class SimpleActivityMedia(
    val title: SimpleActivityTitle,
    val coverImage: MediaCoverImage?
)

@Serializable
data class SimpleActivityTitle(
    val romaji: String?
)

@Serializable
data class DetailedAnimeResponse(val data: DetailedAnimeDataWrapper)

@Serializable
data class DetailedAnimeDataWrapper(val Media: DetailedAnimeMedia)

@Serializable
data class DetailedAnimeMedia(
    val id: Int,
    val title: DetailedAnimeTitle? = null,
    val coverImage: MediaCoverImage? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val genres: List<String>? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val format: String? = null,
    val source: String? = null,
    val studios: DetailedAnimeStudios? = null,
    val startDate: FuzzyDate? = null,
    val endDate: FuzzyDate? = null,
    val nextAiringEpisode: NextAiringEpisode? = null
)

@Serializable
data class DetailedAnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null
)

@Serializable
data class DetailedAnimeStudios(
    val nodes: List<DetailedAnimeStudioNode>
)

@Serializable
data class DetailedAnimeStudioNode(
    val id: Int? = null,
    val name: String? = null
)