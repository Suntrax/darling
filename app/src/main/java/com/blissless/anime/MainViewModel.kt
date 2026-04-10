package com.blissless.anime

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blissless.anime.api.ZenimeScraper
import com.blissless.anime.api.AnimekaiScraper
import com.blissless.anime.data.AnimeRepository
import com.blissless.anime.data.CacheManager
import com.blissless.anime.data.LoginProvider
import com.blissless.anime.data.MalApiService
import com.blissless.anime.data.UserPreferences
import com.blissless.anime.data.JikanService
import com.blissless.anime.data.JikanUserFavorites
import com.blissless.anime.data.JikanUserHistory
import com.blissless.anime.data.models.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val CLIENT_ID = BuildConfig.CLIENT_ID_ANILIST
        private const val MIN_REFRESH_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val SYNC_DEBOUNCE_MS = 2000L // 2 seconds debounce for API sync
    }

    private lateinit var userPreferences: UserPreferences
    private lateinit var cacheManager: CacheManager
    private lateinit var repository: AnimeRepository
    private lateinit var context: Context
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            _isOffline.value = false
        }

        override fun onLost(network: android.net.Network) {
            _isOffline.value = true
        }

        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _isOffline.value = !hasInternet
        }
    }
    
    private var apiRetryJob: Job? = null
    
    private fun startApiRetryLoop() {
        apiRetryJob?.cancel()
        apiRetryJob = viewModelScope.launch {
            while (true) {
                delay(MIN_REFRESH_INTERVAL_MS)
                if (!_isOffline.value && _apiError.value != null) {
                    fetchExploreData(force = true)
                }
            }
        }
    }

    // Sync queue for debounced AniList API calls
    private data class PendingSync(val type: String, val mediaId: Int, val malId: Int? = null, val status: String? = null, val progress: Int? = null, val score: Int? = null, val entryId: Int? = null, val favoriteAdded: Boolean? = null)
    private val pendingSyncs = mutableMapOf<Int, PendingSync>() // mediaId -> pending sync
    private var syncJob: kotlinx.coroutines.Job? = null

    private fun queueSync(mediaId: Int, type: String, malId: Int? = null, status: String? = null, progress: Int? = null, score: Int? = null, entryId: Int? = null, favoriteAdded: Boolean? = null) {
        val existingSync = pendingSyncs[mediaId]
        val resolvedMalId = malId ?: existingSync?.malId ?: cacheManager.detailedAnimeCache.value[mediaId]?.malId
        
        android.util.Log.d("MAL_DEBUG", "queueSync called: mediaId=$mediaId, type=$type, malId=$malId, resolvedMalId=$resolvedMalId, status=$status")
        
        pendingSyncs[mediaId] = PendingSync(type, mediaId, resolvedMalId, status ?: existingSync?.status, progress ?: existingSync?.progress, score ?: existingSync?.score, entryId ?: existingSync?.entryId, favoriteAdded ?: existingSync?.favoriteAdded)
        
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(SYNC_DEBOUNCE_MS)
            executePendingSyncs()
        }
    }

    private suspend fun executePendingSyncs() {
        val syncsToExecute = pendingSyncs.toMap()
        pendingSyncs.clear()
        var hasFavoritesSync = false
        
        for ((_, sync) in syncsToExecute) {
            when (sync.type) {
                "status" -> {
                    sync.status?.let { 
                        if (_loginProvider.value == LoginProvider.MAL) {
                            val malId = sync.malId
                            android.util.Log.d("MAL_DEBUG", "MAL status sync: mediaId=${sync.mediaId}, malId=$malId, status=$it")
                            if (malId != null) {
                                val malStatus = mapToMalStatus(it)
                                android.util.Log.d("MAL_DEBUG", "MAL status mapped: $malStatus")
                                if (malStatus != null) {
                                    val result = malApiService.updateAnimeStatus(malId, malStatus, sync.score, sync.progress)
                                    android.util.Log.d("MAL_DEBUG", "MAL API update result: $result")
                                }
                            } else {
                                android.util.Log.d("MAL_DEBUG", "MAL ID is null, cannot update!")
                            }
                        } else {
                            repository.updateStatus(sync.mediaId, it, sync.progress)
                        }
                    }
                }
                "progress" -> {
                    sync.progress?.let { 
                        if (_loginProvider.value == LoginProvider.MAL) {
                            val malId = sync.malId
                            android.util.Log.d("MAL_DEBUG", "MAL progress sync: mediaId=${sync.mediaId}, malId=$malId, progress=$it")
                            if (malId != null) {
                                malApiService.updateAnimeStatus(malId, null, null, it)
                            } else {
                                android.util.Log.d("MAL_DEBUG", "MAL ID is null, cannot update progress!")
                            }
                        } else {
                            repository.updateProgress(sync.mediaId, it)
                        }
                    }
                }
                "score" -> {
                    sync.score?.let { 
                        if (_loginProvider.value == LoginProvider.MAL) {
                            val malId = sync.malId
                            android.util.Log.d("MAL_DEBUG", "MAL score sync: mediaId=${sync.mediaId}, malId=$malId, score=$it")
                            if (malId != null) {
                                malApiService.updateAnimeStatus(malId, null, it, null)
                            }
                        } else {
                            repository.updateScore(sync.mediaId, it)
                        }
                    }
                }
                "delete" -> {
                    sync.entryId?.let { 
                        if (_loginProvider.value == LoginProvider.MAL) {
                            val malId = sync.malId
                            android.util.Log.d("MAL_DEBUG", "MAL delete sync: mediaId=${sync.mediaId}, malId=$malId")
                            if (malId != null) {
                                malApiService.deleteAnimeFromList(malId)
                            }
                        } else {
                            repository.deleteListEntry(it)
                        }
                    }
                }
                "favorite" -> {
                    // Execute the queued action directly - don't check current state
                    // The local state was already updated, now sync with API
                    val shouldBeFavorited = sync.favoriteAdded == true
                    android.util.Log.d("AniListFavorite", "executePendingSyncs favorite: mediaId=${sync.mediaId}, shouldBeFavorited=$shouldBeFavorited")
                    if (shouldBeFavorited) {
                        android.util.Log.d("AniListFavorite", "  Calling addAniListFavorite API")
                        repository.addAniListFavorite(sync.mediaId)
                    } else {
                        android.util.Log.d("AniListFavorite", "  Calling removeAniListFavorite API")
                        repository.removeAniListFavorite(sync.mediaId)
                    }
                    // Don't set hasFavoritesSync - no need to refetch, UI already updated
                }
            }
        }
        
        if (syncsToExecute.isNotEmpty()) {
            if (_loginProvider.value == LoginProvider.MAL) {
                fetchMalList()
            } else {
                fetchLists()
            }
        }
        // Don't fetch AniList favorites after sync - local-first approach means UI is already updated
    }
    
    private fun mapToMalStatus(status: String): String? {
        return when (status) {
            "CURRENT" -> "watching"
            "PLANNING" -> "plan_to_watch"
            "COMPLETED" -> "completed"
            "PAUSED" -> "on_hold"
            "DROPPED" -> "dropped"
            else -> null
        }
    }
    
    private fun mapFromMalStatus(malStatus: String?): String {
        return when (malStatus) {
            "watching" -> "CURRENT"
            "plan_to_watch" -> "PLANNING"
            "completed" -> "COMPLETED"
            "on_hold" -> "PAUSED"
            "dropped" -> "DROPPED"
            else -> "PLANNING"
        }
    }
    
    private suspend fun fetchMalList() {
        if (_loginProvider.value != LoginProvider.MAL) return
        
        val entries = malApiService.getAnimeList()
        
        val currentlyWatching = mutableListOf<AnimeMedia>()
        val planningToWatch = mutableListOf<AnimeMedia>()
        val completed = mutableListOf<AnimeMedia>()
        val onHold = mutableListOf<AnimeMedia>()
        val dropped = mutableListOf<AnimeMedia>()
        
        entries.forEach { entry ->
            val malId = entry.node.id
            val status = entry.list_status?.status
            val progress = entry.list_status?.num_episodes_watched ?: 0
            val score = entry.list_status?.score ?: 0
            
            // Find matching anime from cache by MAL ID
            val cachedAnime = cacheManager.detailedAnimeCache.value.values.find { 
                it.malId == malId || it.id == malId 
            }
            
            val anime = if (cachedAnime != null) {
                AnimeMedia(
                    id = cachedAnime.id,
                    title = cachedAnime.title,
                    titleEnglish = cachedAnime.titleEnglish,
                    cover = cachedAnime.cover,
                    banner = cachedAnime.banner,
                    progress = progress,
                    totalEpisodes = cachedAnime.episodes,
                    latestEpisode = cachedAnime.nextAiringEpisode,
                    status = cachedAnime.status ?: "",
                    averageScore = cachedAnime.averageScore,
                    genres = cachedAnime.genres,
                    listStatus = mapFromMalStatus(status),
                    malId = malId,
                    format = cachedAnime.format
                )
            } else {
                AnimeMedia(
                    id = malId,
                    title = entry.node.title,
                    titleEnglish = entry.node.title,
                    cover = entry.node.main_picture?.large ?: entry.node.main_picture?.medium ?: "",
                    progress = progress,
                    totalEpisodes = entry.node.num_episodes,
                    listStatus = mapFromMalStatus(status),
                    malId = malId
                )
            }
            
            when (mapFromMalStatus(status)) {
                "CURRENT" -> currentlyWatching.add(anime)
                "PLANNING" -> planningToWatch.add(anime)
                "COMPLETED" -> completed.add(anime)
                "PAUSED" -> onHold.add(anime)
                "DROPPED" -> dropped.add(anime)
                else -> planningToWatch.add(anime) // Default to planning for unknown status
            }
        }
        
        _currentlyWatching.value = currentlyWatching.sortedByDescending { it.averageScore ?: 0 }
        _planningToWatch.value = planningToWatch.sortedByDescending { it.averageScore ?: 0 }
        _completed.value = completed.sortedByDescending { it.averageScore ?: 0 }
        _onHold.value = onHold.sortedByDescending { it.averageScore ?: 0 }
        _dropped.value = dropped.sortedByDescending { it.averageScore ?: 0 }
        
        loadMalFavoritesFromCache()
    }
    
    fun toggleMalFavorite(anime: AnimeMedia) {
        val currentFavorites = _malFavorites.value.toMutableList()
        val existingIndex = currentFavorites.indexOfFirst { it.id == anime.id || it.malId == anime.malId }
        if (existingIndex >= 0) {
            currentFavorites.removeAt(existingIndex)
            userPreferences.toggleLocalFavorite(anime.id)
            // Also remove from AniList if logged in via AniList
            if (_loginProvider.value == LoginProvider.ANILIST && anime.id > 0) {
                _aniListFavorites.value = _aniListFavorites.value.filter { it.id != anime.id }
                userPreferences.toggleAniListFavorite(anime.id)
                queueSync(anime.id, "favorite", favoriteAdded = false)
            }
        } else {
            currentFavorites.add(anime)
            userPreferences.toggleLocalFavorite(anime.id, anime.title, anime.cover, anime.banner, anime.year, anime.averageScore)
            // Also add to AniList if logged in via AniList
            if (_loginProvider.value == LoginProvider.ANILIST && anime.id > 0) {
                val userFavorite = UserFavoriteAnime(
                    id = anime.id,
                    title = MediaTitle(romaji = anime.title, english = anime.titleEnglish),
                    coverImage = MediaCoverImage(large = anime.cover, medium = anime.cover),
                    episodes = anime.totalEpisodes,
                    averageScore = anime.averageScore,
                    genres = anime.genres,
                    seasonYear = anime.year
                )
                _aniListFavorites.value = _aniListFavorites.value + userFavorite
                userPreferences.toggleAniListFavorite(anime.id)
                queueSync(anime.id, "favorite", favoriteAdded = true)
            }
        }
        _malFavorites.value = currentFavorites
        userPreferences.saveMalFavorites(currentFavorites.map { it.id })
    }
    
    private fun toggleMalFavoriteById(mediaId: Int) {
        val currentFavorites = _malFavorites.value.toMutableList()
        val existingIndex = currentFavorites.indexOfFirst { it.id == mediaId || it.malId == mediaId }
        if (existingIndex >= 0) {
            currentFavorites.removeAt(existingIndex)
        } else {
            // Try to find the anime in existing lists
            val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
            val anime = allAnime.find { it.id == mediaId || it.malId == mediaId }
            if (anime != null) {
                currentFavorites.add(anime)
            }
        }
        _malFavorites.value = currentFavorites
        userPreferences.saveMalFavorites(currentFavorites.map { it.id })
    }
    
    fun isMalFavorite(mediaId: Int): Boolean {
        return _malFavorites.value.any { it.id == mediaId || it.malId == mediaId }
    }
    
    fun getMalFavoriteAnime(): List<AnimeMedia> {
        return _malFavorites.value
    }
    
    fun removeMalFavorite(mediaId: Int) {
        val currentFavorites = _malFavorites.value.toMutableList()
        currentFavorites.removeAll { it.id == mediaId || it.malId == mediaId }
        _malFavorites.value = currentFavorites
        userPreferences.saveMalFavorites(currentFavorites.map { it.id })
    }
    
    private fun loadMalFavoritesFromCache() {
        val favoriteIds = userPreferences.getMalFavorites()
        val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
        val favoriteAnimeList = mutableListOf<AnimeMedia>()
        for (id in favoriteIds) {
            val anime = allAnime.find { it.id == id || it.malId == id }
            if (anime != null) {
                favoriteAnimeList.add(anime)
            }
        }
        _malFavorites.value = favoriteAnimeList
    }

    // Track last refresh time to prevent rapid re-fetches (persisted to survive app restarts)
    private var lastHomeRefreshTime: Long
        get() = userPreferences.getLastHomeRefreshTime()
        set(value) = userPreferences.setLastHomeRefreshTime(value)

    private var lastExploreRefreshTime: Long
        get() = userPreferences.getLastExploreRefreshTime()
        set(value) = userPreferences.setLastExploreRefreshTime(value)

    // UI State
    private val _userId = MutableStateFlow<Int?>(null)
    val userId: StateFlow<Int?> = _userId.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userAvatar = MutableStateFlow<String?>(null)
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    private val _userBanner = MutableStateFlow<String?>(null)
    val userBanner: StateFlow<String?> = _userBanner.asStateFlow()

    private val _userBio = MutableStateFlow<String?>(null)
    val userBio: StateFlow<String?> = _userBio.asStateFlow()

    private val _userSiteUrl = MutableStateFlow<String?>(null)
    val userSiteUrl: StateFlow<String?> = _userSiteUrl.asStateFlow()

    private val _userCreatedAt = MutableStateFlow<Long?>(null)
    val userCreatedAt: StateFlow<Long?> = _userCreatedAt.asStateFlow()

    private val _isLoadingExplore = MutableStateFlow(false)
    val isLoadingExplore: StateFlow<Boolean> = _isLoadingExplore.asStateFlow()
    
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()
    
    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    private val _isLoadingSchedule = MutableStateFlow(false)
    val isLoadingSchedule: StateFlow<Boolean> = _isLoadingSchedule.asStateFlow()

    // Anime lists
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

    // Offline anime lists (for logged-out users)
    private val _offlineCurrentlyWatching = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlineCurrentlyWatching: StateFlow<List<AnimeMedia>> = _offlineCurrentlyWatching.asStateFlow()

    private val _offlinePlanningToWatch = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlinePlanningToWatch: StateFlow<List<AnimeMedia>> = _offlinePlanningToWatch.asStateFlow()

    private val _offlineCompleted = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlineCompleted: StateFlow<List<AnimeMedia>> = _offlineCompleted.asStateFlow()

    private val _offlineOnHold = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlineOnHold: StateFlow<List<AnimeMedia>> = _offlineOnHold.asStateFlow()

    private val _offlineDropped = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlineDropped: StateFlow<List<AnimeMedia>> = _offlineDropped.asStateFlow()

    // Explore data
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

    // Schedule
    private val _airingSchedule = MutableStateFlow<Map<Int, List<AiringScheduleAnime>>>(emptyMap())
    val airingSchedule: StateFlow<Map<Int, List<AiringScheduleAnime>>> = _airingSchedule.asStateFlow()

    private val _airingAnimeList = MutableStateFlow<List<AiringScheduleAnime>>(emptyList())
    val airingAnimeList: StateFlow<List<AiringScheduleAnime>> = _airingAnimeList.asStateFlow()

    // Other UI state
    private val _userActivity = MutableStateFlow<List<UserActivity>>(emptyList())
    val userActivity: StateFlow<List<UserActivity>> = _userActivity.asStateFlow()

    private val _userStats = MutableStateFlow<UserAnimeStats?>(null)
    val userStats: StateFlow<UserAnimeStats?> = _userStats.asStateFlow()

    // AniList Favorites
    private val _aniListFavorites = MutableStateFlow<List<UserFavoriteAnime>>(emptyList())
    val aniListFavorites: StateFlow<List<UserFavoriteAnime>> = _aniListFavorites.asStateFlow()
    val aniListFavoriteIds: StateFlow<Set<Int>> get() = userPreferences.aniListFavorites
    
    // Jikan (MAL) Favorites and History
    private val _jikanFavorites = MutableStateFlow<JikanUserFavorites?>(null)
    val jikanFavorites: StateFlow<JikanUserFavorites?> = _jikanFavorites.asStateFlow()
    
    private val _jikanHistory = MutableStateFlow<JikanUserHistory?>(null)
    val jikanHistory: StateFlow<JikanUserHistory?> = _jikanHistory.asStateFlow()
    
    private var jikanService: JikanService? = null
    private var malUsername: String? = null
    private val _malUsername = MutableStateFlow<String?>(null)
    val malUsernameFlow: StateFlow<String?> = _malUsername.asStateFlow()
    
    suspend fun getJikanAnimeCover(malId: Int): String? = jikanService?.getAnimeCover(malId)
    
    private var lastFavoriteToggleTime = 0L
    private val favoriteToggleCooldownMs = 3000L // 3 second cooldown
    private val _isFavoriteRateLimited = MutableStateFlow(false)
    val isFavoriteRateLimited: StateFlow<Boolean> = _isFavoriteRateLimited.asStateFlow()

    private val _completedSearchResults = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val completedSearchResults: StateFlow<List<AnimeMedia>> = _completedSearchResults.asStateFlow()

    // Preferences Delegations
    val authToken: StateFlow<String?> get() = userPreferences.authToken
    val isOled: StateFlow<Boolean> get() = userPreferences.isOled
    val disableMaterialColors: StateFlow<Boolean> get() = userPreferences.disableMaterialColors
    val preferredCategory: StateFlow<String> get() = userPreferences.preferredCategory
    val showStatusColors: StateFlow<Boolean> get() = userPreferences.showStatusColors
    val showAnimeCardButtons: StateFlow<Boolean> get() = userPreferences.showAnimeCardButtons
    val preferEnglishTitles: StateFlow<Boolean> get() = userPreferences.preferEnglishTitles
    val preventScheduleSync: StateFlow<Boolean> get() = userPreferences.preventScheduleSync
    val trackingPercentage: StateFlow<Int> get() = userPreferences.trackingPercentage
    val forwardSkipSeconds: StateFlow<Int> get() = userPreferences.forwardSkipSeconds
    val backwardSkipSeconds: StateFlow<Int> get() = userPreferences.backwardSkipSeconds
    val hideNavbarText: StateFlow<Boolean> get() = userPreferences.hideNavbarText
    val simplifyEpisodeMenu: StateFlow<Boolean> get() = userPreferences.simplifyEpisodeMenu
    val simplifyAnimeDetails: StateFlow<Boolean> get() = userPreferences.simplifyAnimeDetails
    val autoSkipOpening: StateFlow<Boolean> get() = userPreferences.autoSkipOpening
    val autoSkipEnding: StateFlow<Boolean> get() = userPreferences.autoSkipEnding
    val autoPlayNextEpisode: StateFlow<Boolean> get() = userPreferences.autoPlayNextEpisode
    val localFavorites: StateFlow<Map<Int, StoredFavorite>> get() = userPreferences.localFavorites
    val localFavoriteIds: Set<Int> get() = userPreferences.localFavoriteIds
    val localAnimeStatus: StateFlow<Map<Int, LocalAnimeEntry>> get() = userPreferences.localAnimeStatus
    val preferredScraper: StateFlow<String> get() = userPreferences.preferredScraper
    val hideAdultContent: StateFlow<Boolean> get() = userPreferences.hideAdultContent
    val startupScreen: StateFlow<Int> get() = userPreferences.startupScreen
    
    // Buffer Settings
    val bufferAheadSeconds: StateFlow<Int> get() = userPreferences.bufferAheadSeconds
    val bufferSizeMb: StateFlow<Int> get() = userPreferences.bufferSizeMb
    val showBufferIndicator: StateFlow<Boolean> get() = userPreferences.showBufferIndicator

    // Cache Delegations
    val prefetchedStreams: StateFlow<Map<String, AniwatchStreamResult?>> get() = cacheManager.prefetchedStreams
    val prefetchedEpisodeInfo: StateFlow<Map<String, EpisodeStreams?>> get() = cacheManager.prefetchedEpisodeInfo
    val playbackPositions: StateFlow<Map<String, Long>> get() = cacheManager.playbackPositions
    val detailedAnimeCache: StateFlow<Map<Int, DetailedAnimeData>> get() = cacheManager.detailedAnimeCache
    
    // Get cache data source factory for disk caching
    fun getCacheDataSourceFactory(referer: String) = cacheManager.getCacheDataSourceFactory(referer)
    
    // Check if video is fully cached
    fun isVideoFullyCached(videoUrl: String) = cacheManager.isVideoFullyCached(videoUrl)
    
    // Get cache progress
    fun getCacheProgress(videoUrl: String) = cacheManager.getCacheProgress(videoUrl)
    
    // MAL API Service
    private lateinit var malApiService: MalApiService
    
    // Login provider tracking
    private val _loginProvider = MutableStateFlow(LoginProvider.NONE)
    val loginProvider: StateFlow<LoginProvider> = _loginProvider.asStateFlow()
    
    // MAL favorites (stored locally with full anime data)
    private val _malFavorites = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val malFavorites: StateFlow<List<AnimeMedia>> = _malFavorites.asStateFlow()
    
    // Toast messages for UI feedback
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()
    
    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    // Card bounds for shared element transition
    data class CardBounds(val animeId: Int, val coverUrl: String, val bounds: android.graphics.RectF)
    private val _exploreAnimeCardBounds = MutableStateFlow<CardBounds?>(null)
    val exploreAnimeCardBounds: StateFlow<CardBounds?> = _exploreAnimeCardBounds.asStateFlow()
    
    private val _homeAnimeCardBounds = MutableStateFlow<CardBounds?>(null)
    val homeAnimeCardBounds: StateFlow<CardBounds?> = _homeAnimeCardBounds.asStateFlow()

    fun setExploreAnimeCardBounds(animeId: Int, coverUrl: String, bounds: android.graphics.RectF?) {
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            _exploreAnimeCardBounds.value = CardBounds(animeId, coverUrl, bounds)
        }
    }
    
    fun setHomeAnimeCardBounds(animeId: Int, coverUrl: String, bounds: android.graphics.RectF?) {
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            _homeAnimeCardBounds.value = CardBounds(animeId, coverUrl, bounds)
        }
    }
    
    fun clearExploreAnimeCardBounds() {
        _exploreAnimeCardBounds.value = null
    }
    
    fun clearHomeAnimeCardBounds() {
        _homeAnimeCardBounds.value = null
    }
    
    // Is logged in (either AniList or MAL)
    val isLoggedIn: Boolean get() = _loginProvider.value != LoginProvider.NONE

    fun init(context: Context, hasToken: Boolean) {
        this.context = context
        userPreferences = UserPreferences(context)
        cacheManager = CacheManager(userPreferences.getSharedPreferences())
        repository = AnimeRepository(userPreferences, cacheManager)
        malApiService = MalApiService(context)
        jikanService = JikanService(context)

        // Initialize video cache for offline playback
        cacheManager.initializeVideoCache(context)
        
        // Check connectivity and register callback for auto-detection
        checkConnectivity()
        registerConnectivityCallback()
        startApiRetryLoop()

        userPreferences.loadPreferences(hasToken)
        cacheManager.loadStreamCache()
        cacheManager.loadPlaybackPositions()
        loadAiringScheduleCache()
        updateOfflineLists()
        
        // Check login provider
        if (hasToken) {
            _loginProvider.value = LoginProvider.ANILIST
        } else if (malApiService.getAuthManager().isLoggedIn) {
            _loginProvider.value = LoginProvider.MAL
            loadMalUserData()
        }

        viewModelScope.launch {
            if (hasToken || _loginProvider.value != LoginProvider.NONE) {
                loadHomeDataWithCache()
                // Load AniList favorites from storage AFTER home data is loaded (so lists are available)
                if (hasToken) {
                    loadAniListFavoritesFromStorage()
                }
            } else {
                prefetchOfflineWatchingStreams()
            }
            loadExploreDataWithCache()
            fetchAiringSchedule()
            
            // Fetch AniList favorites from API in background after initial load
            if (hasToken) {
                fetchAniListFavorites()
            }
        }
    }
    
    private fun checkConnectivity() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            val isConnected = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            _isOffline.value = !isConnected
        } catch (e: Exception) {
            _isOffline.value = false
        }
    }
    
    fun refreshConnectivity() {
        checkConnectivity()
    }
    
    private fun registerConnectivityCallback() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityCallback = networkCallback
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to register connectivity callback: ${e.message}")
        }
    }
    
    private fun loadMalUserData() {
        val malAuth = malApiService.getAuthManager()
        val userInfo = malAuth.userInfo.value
        if (userInfo != null) {
            _userName.value = userInfo.name
            _userAvatar.value = userInfo.picture
            malUsername = userInfo.name
            _malUsername.value = userInfo.name
            fetchJikanUserData()
        }
    }
    
    fun fetchJikanUserData() {
        android.util.Log.d("JIKAN_DEBUG", "fetchJikanUserData called")
        val username = malUsername
        android.util.Log.d("JIKAN_DEBUG", "malUsername: $username, jikanService: ${jikanService != null}")
        if (username == null) {
            android.util.Log.e("JIKAN_DEBUG", "malUsername is null, cannot fetch Jikan data")
            return
        }
        viewModelScope.launch {
            android.util.Log.d("JIKAN_DEBUG", "Fetching Jikan data for username: $username")
            _jikanFavorites.value = jikanService?.getUserFavorites(username)
            android.util.Log.d("JIKAN_DEBUG", "Favorites fetched: ${_jikanFavorites.value?.anime?.size ?: 0} anime")
            _jikanHistory.value = jikanService?.getUserHistory(username)
            android.util.Log.d("JIKAN_DEBUG", "History fetched: ${_jikanHistory.value?.anime?.size ?: 0} entries")
        }
    }
    
    fun loginWithMal() {
        android.util.Log.d("MAL_LOGIN", "loginWithMal called")
        if (BuildConfig.MAL_CLIENT_ID.isBlank()) {
            android.util.Log.e("MAL_LOGIN", "MAL_CLIENT_ID is blank!")
            return
        }
        val uri = malApiService.getAuthUrl(BuildConfig.MAL_CLIENT_ID)
        android.util.Log.d("MAL_LOGIN", "Generated auth URL: $uri")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
    
    fun handleMalAuthRedirect(intent: Intent?) {
        android.util.Log.d("MAL_LOGIN", "handleMalAuthRedirect called, data: ${intent?.dataString}")
        handleMalAuthAuthCode(intent?.dataString ?: "")
    }
    
    fun handleMalAuthAuthCode(uriString: String) {
        android.util.Log.d("MAL_LOGIN", "handleMalAuthAuthCode called with: $uriString")
        
        if (uriString.isEmpty()) {
            android.util.Log.e("MAL_LOGIN", "URI string is empty!")
            return
        }
        
        if (!uriString.startsWith("animescraper://success?code=") && !uriString.startsWith("animescraper://success")) {
            android.util.Log.e("MAL_LOGIN", "URI doesn't match expected format!")
            return
        }
        
        val code = uriString.substringAfter("code=").substringBefore("&")
        android.util.Log.d("MAL_LOGIN", "Extracted code: ${code.take(20)}...")
        
        if (code.isEmpty()) {
            android.util.Log.e("MAL_LOGIN", "Code is empty!")
            viewModelScope.launch { _toastMessage.emit("MAL login failed: No auth code received") }
            return
        }
        
        viewModelScope.launch {
            _toastMessage.emit("Completing MAL login...")
            android.util.Log.d("MAL_LOGIN", "Calling exchangeCodeForToken...")
            val success = malApiService.exchangeCodeForToken(code, BuildConfig.MAL_CLIENT_ID, null)
            android.util.Log.d("MAL_LOGIN", "exchangeCodeForToken result: $success")
            if (success) {
                userPreferences.clearToken()
                _loginProvider.value = LoginProvider.MAL
                loadMalUserData()
                fetchMalList()
                prefetchOfflineWatchingStreams()
                _toastMessage.emit("Successfully logged into MyAnimeList!")
            } else {
                _toastMessage.emit("MAL login failed: Token exchange error")
            }
        }
    }
    
    fun loginWithAniList() {
        // Clear MAL data if switching
        if (_loginProvider.value == LoginProvider.MAL) {
            malApiService.getAuthManager().clearToken()
            _malFavorites.value = emptyList()
        }
        
        val url = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
    
    fun handleAuthRedirect(intent: Intent?) {
        intent?.dataString?.takeIf { it.startsWith("animescraper://success") }?.let { uri ->
            uri.replace("#", "?").toUri().getQueryParameter("access_token")?.let { token ->
                userPreferences.saveToken(token)
                _loginProvider.value = LoginProvider.ANILIST
                viewModelScope.launch {
                    _isLoadingHome.value = true
                    fetchUser()
                    fetchLists()
                    _isLoadingHome.value = false
                    prefetchContinueWatchingStreams()
                }
            }
        }
    }
    
    fun logout() {
        when (_loginProvider.value) {
            LoginProvider.ANILIST -> {
                userPreferences.clearAllUserData()
                userPreferences.clearToken()
            }
            LoginProvider.MAL -> {
                malApiService.getAuthManager().clearToken()
                userPreferences.clearMalFavorites()
                _malFavorites.value = emptyList()
                _jikanFavorites.value = null
                _jikanHistory.value = null
                malUsername = null
                _malUsername.value = null
            }
            LoginProvider.NONE -> {}
        }
        
        cacheManager.clearAllCaches()
        _loginProvider.value = LoginProvider.NONE
        _userId.value = null; _userName.value = null; _userAvatar.value = null
        _userBanner.value = null; _userBio.value = null; _userSiteUrl.value = null; _userCreatedAt.value = null
        _currentlyWatching.value = emptyList(); _planningToWatch.value = emptyList(); _completed.value = emptyList(); _onHold.value = emptyList(); _dropped.value = emptyList()
        _aniListFavorites.value = emptyList()
        _isLoadingHome.value = false
        
        viewModelScope.launch {
            _logoutEvent.emit(Unit)
        }
    }

    fun updateOfflineLists() {
        val statusMap = userPreferences.getAllLocalAnimeStatus()
        val cache = cacheManager.detailedAnimeCache.value
        val favorites = userPreferences.localFavorites.value

        val currentlyWatching = mutableListOf<AnimeMedia>()
        val planningToWatch = mutableListOf<AnimeMedia>()
        val completed = mutableListOf<AnimeMedia>()
        val onHold = mutableListOf<AnimeMedia>()
        val dropped = mutableListOf<AnimeMedia>()

        statusMap.forEach { (id, entry) ->
            val cachedAnime = cache[id]
            val favorite = favorites[id]

            val anime = if (cachedAnime != null) {
                AnimeMedia(
                    id = cachedAnime.id,
                    title = cachedAnime.title,
                    titleEnglish = cachedAnime.titleEnglish,
                    cover = cachedAnime.cover,
                    banner = cachedAnime.banner,
                    progress = entry.progress,
                    totalEpisodes = cachedAnime.episodes,
                    latestEpisode = cachedAnime.nextAiringEpisode,
                    status = cachedAnime.status ?: "",
                    averageScore = cachedAnime.averageScore,
                    genres = cachedAnime.genres,
                    listStatus = entry.status,
                    year = cachedAnime.year,
                    malId = cachedAnime.malId,
                    format = cachedAnime.format
                )
            } else if (favorite != null) {
                AnimeMedia(
                    id = favorite.id,
                    title = favorite.title,
                    cover = favorite.cover,
                    banner = favorite.banner,
                    progress = entry.progress,
                    totalEpisodes = entry.totalEpisodes,
                    listStatus = entry.status,
                    year = favorite.year,
                    averageScore = favorite.averageScore
                )
            } else if (entry.title.isNotEmpty()) {
                // Use data stored in LocalAnimeEntry itself
                AnimeMedia(
                    id = entry.id,
                    title = entry.title,
                    cover = entry.cover,
                    banner = entry.banner,
                    progress = entry.progress,
                    totalEpisodes = entry.totalEpisodes,
                    listStatus = entry.status,
                    year = entry.year,
                    averageScore = entry.averageScore
                )
            } else {
                null
            }

            if (anime != null) {
                when (entry.status) {
                    "CURRENT" -> currentlyWatching.add(anime)
                    "PLANNING" -> planningToWatch.add(anime)
                    "COMPLETED" -> completed.add(anime)
                    "PAUSED" -> onHold.add(anime)
                    "DROPPED" -> dropped.add(anime)
                }
            }
        }

        _offlineCurrentlyWatching.value = currentlyWatching.sortedByDescending { it.averageScore ?: 0 }
        _offlinePlanningToWatch.value = planningToWatch.sortedByDescending { it.averageScore ?: 0 }
        _offlineCompleted.value = completed.sortedByDescending { it.averageScore ?: 0 }
        _offlineOnHold.value = onHold.sortedByDescending { it.averageScore ?: 0 }
        _offlineDropped.value = dropped.sortedByDescending { it.averageScore ?: 0 }
        
        // Prefetch streams for offline "Continue Watching" list
        prefetchOfflineWatchingStreams()
    }

    private suspend fun loadHomeDataWithCache() {
        cacheManager.loadHomeDataFromCache()?.let {
            updateHomeState(it)
        }
        
        val now = System.currentTimeMillis()
        if (now - lastHomeRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            _isLoadingHome.value = false
            refreshReleasingAnimeProgress()
            prefetchContinueWatchingStreams()
            return
        }
        
        _isLoadingHome.value = true
        val userSuccess = fetchUser()
        val listsSuccess = fetchLists()
        _isLoadingHome.value = false
        
        if (userSuccess && listsSuccess) {
            lastHomeRefreshTime = System.currentTimeMillis()
        }
        
        refreshReleasingAnimeProgress()
        prefetchContinueWatchingStreams()
    }

    private fun updateHomeState(data: HomeCacheData) {
        _currentlyWatching.value = data.currentlyWatching
        _planningToWatch.value = data.planningToWatch
        _completed.value = data.completed
        _onHold.value = data.onHold
        _dropped.value = data.dropped
        _userId.value = data.userId
        _userName.value = data.userName
        _userAvatar.value = data.userAvatar
    }

    private suspend fun loadExploreDataWithCache() {
        val cachedData = cacheManager.loadExploreDataFromCache()
        if (cachedData != null) {
            updateExploreState(cachedData)
        } else {
            fetchExploreData(force = true)
        }
    }

    private fun updateExploreState(data: ExploreCacheData) {
        _featuredAnime.value = data.featuredAnime
        _seasonalAnime.value = data.seasonalAnime
        _topSeries.value = data.topSeries
        _topMovies.value = data.topMovies
        _actionAnime.value = data.actionAnime
        _romanceAnime.value = data.romanceAnime
        _comedyAnime.value = data.comedyAnime
        _fantasyAnime.value = data.fantasyAnime
        _scifiAnime.value = data.scifiAnime
    }

    private fun loadAiringScheduleCache() {
        cacheManager.loadAiringScheduleCache()?.let {
            _airingSchedule.value = it.scheduleByDay
            _airingAnimeList.value = it.airingAnimeList
        }
    }

    // API calls
    suspend fun fetchUser(): Boolean {
        val result = repository.fetchUser()?.let {
            _userId.value = it.data.Viewer.id
            _userName.value = it.data.Viewer.name
            _userAvatar.value = it.data.Viewer.avatar?.large ?: it.data.Viewer.avatar?.medium
            _userBanner.value = it.data.Viewer.bannerImage
            android.util.Log.d("UserProfile", "API returned banner: ${it.data.Viewer.bannerImage}")
            _userBio.value = it.data.Viewer.about
            _userSiteUrl.value = it.data.Viewer.siteUrl
            _userCreatedAt.value = it.data.Viewer.createdAt
            it.data.Viewer.statistics?.anime?.let { stats ->
                _userStats.value = stats
            }
            true
        } ?: false
        return result
    }

    suspend fun fetchLists(): Boolean {
        val userId = _userId.value
        if (userId == null) {
            return false
        }
        val response = repository.fetchMediaLists(userId)
        if (response == null) {
            return false
        }

        val grouped = response.data.MediaListCollection.lists.flatMap { list ->
            list.entries.map { entry ->
                val anime = AnimeMedia(
                    id = entry.mediaId,
                    title = entry.media.title.romaji ?: entry.media.title.english ?: "Unknown",
                    titleEnglish = entry.media.title.english,
                    cover = entry.media.coverImage?.extraLarge ?: "",
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
                (list.status ?: list.name) to anime
            }
        }.groupBy({ it.first }, { it.second })

        _currentlyWatching.value = grouped["CURRENT"] ?: grouped["Watching"] ?: emptyList()
        _planningToWatch.value = grouped["PLANNING"] ?: grouped["Plan to Watch"] ?: emptyList()
        _completed.value = grouped["COMPLETED"] ?: emptyList()
        _onHold.value = grouped["PAUSED"] ?: emptyList()
        _dropped.value = grouped["DROPPED"] ?: emptyList()
        saveHomeDataToCache()
        return true
    }

    fun fetchExploreData(force: Boolean = false) {
        val now = System.currentTimeMillis()
        
        if (!force && now - lastExploreRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            return
        }
        
        viewModelScope.launch {
            _isLoadingExplore.value = true
            val (response, error) = repository.fetchBatchedExploreWithError(useCache = !force)
            if (response == null) {
                _isLoadingExplore.value = false
                _apiError.value = error ?: "Failed to load content"
                return@launch
            }
            
            val success = try {
                _featuredAnime.value = response.data.featured.media.map { mapExploreMedia(it) }
                _seasonalAnime.value = response.data.seasonal.media.map { mapExploreMedia(it) }
                _topSeries.value = response.data.topSeries.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 70 }
                _topMovies.value = response.data.topMovies.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 70 }
                _actionAnime.value = response.data.action.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                _romanceAnime.value = response.data.romance.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                _comedyAnime.value = response.data.comedy.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                _fantasyAnime.value = response.data.fantasy.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                _scifiAnime.value = response.data.scifi.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                saveExploreDataToCache()
                _apiError.value = null
                true
            } catch (e: Exception) {
                _apiError.value = e.message ?: "Failed to load content"
                false
            }
            _isLoadingExplore.value = false
            if (success) {
                lastExploreRefreshTime = System.currentTimeMillis()
            }
        }
    }

    private fun mapExploreMedia(media: ExploreMedia): ExploreAnime {
        val title = media.title.romaji ?: media.title.english ?: "Unknown"
        val episodes = media.episodes ?: 0
        val latestEpisode = media.nextAiringEpisode?.episode?.let { it - 1 }

        return ExploreAnime(
            id = media.id,
            title = title,
            titleEnglish = media.title.english,
            cover = media.coverImage?.extraLarge ?: "",
            banner = media.bannerImage,
            episodes = episodes,
            latestEpisode = latestEpisode,
            averageScore = media.averageScore,
            genres = media.genres ?: emptyList(),
            year = media.startDate?.year ?: media.seasonYear,
            malId = media.idMal,
            isAdult = media.isAdult
        )
    }

    fun fetchAiringSchedule(force: Boolean = false) {
        val now = System.currentTimeMillis()

        val cached = cacheManager.loadAiringScheduleCache()
        if (cached != null && !force && now - lastExploreRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            return
        }

        val shouldForce = force || cached == null

        viewModelScope.launch {
            _isLoadingSchedule.value = true
            val schedules = repository.fetchAiringSchedule()

            val airingList = schedules.filter { it.media != null }.map { schedule ->
                val media = schedule.media!!
                val title = media.title.romaji ?: media.title.english ?: "Unknown"
                val episodes = media.episodes ?: 0

                if (title.contains("JoJo", ignoreCase = true) || title.contains("jojo", ignoreCase = true)) {
                    android.util.Log.d("JOJO_DEBUG", "JoJo in airing schedule: $title")
                    android.util.Log.d("JOJO_DEBUG", "  - episodes: $episodes")
                    android.util.Log.d("JOJO_DEBUG", "  - airingEpisode: ${schedule.episode}")
                    android.util.Log.d("JOJO_DEBUG", "  - status: ${media.status}")
                }

                AiringScheduleAnime(
                    id = media.id,
                    title = title,
                    cover = schedule.media.coverImage?.extraLarge ?: "",
                    episodes = episodes,
                    airingEpisode = schedule.episode,
                    airingAt = schedule.airingAt,
                    timeUntilAiring = schedule.timeUntilAiring,
                    averageScore = media.averageScore,
                    genres = media.genres ?: emptyList(),
                    year = media.seasonYear,
                    malId = media.idMal,
                    isAdult = media.isAdult
                )
            }.sortedBy { it.airingAt }

            val scheduleByDay = airingList.groupBy { anime ->
                val calendar = Calendar.getInstance().apply { timeInMillis = anime.airingAt * 1000L }
                calendar.get(Calendar.DAY_OF_WEEK) - 1
            }

            _airingSchedule.value = scheduleByDay
            _airingAnimeList.value = airingList
            cacheManager.saveAiringScheduleCache(scheduleByDay, airingList)
            _isLoadingSchedule.value = false
            lastExploreRefreshTime = System.currentTimeMillis()
        }
    }

    fun updateAnimeProgress(mediaId: Int, progress: Int) {
        val currentEntry = userPreferences.getLocalAnimeStatus(mediaId)
        if (currentEntry != null) {
            val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
            userPreferences.updateLocalAnimeProgress(mediaId, progress, cachedAnime?.episodes ?: currentEntry.totalEpisodes)
            updateOfflineLists()
        }
        
        // Immediately update progress in logged-in lists
        updateProgressInLists(mediaId, progress)
        
        val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
        val malId = cachedAnime?.malId
        
        if (_loginProvider.value == LoginProvider.MAL && malId == null) {
            android.util.Log.d("MAL_DEBUG", "MAL ID not in cache for progress update, fetching anime details for mediaId=$mediaId")
            viewModelScope.launch {
                cacheManager.clearDetailedAnimeCache(mediaId)
                val details = fetchDetailedAnimeData(mediaId)
                var resolvedMalId = details?.malId
                android.util.Log.d("MAL_DEBUG", "Fetched from AniList: malId=$resolvedMalId")
                
                if (resolvedMalId == null && details == null) {
                    val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
                    val animeFromList = allAnime.find { it.id == mediaId }
                    if (animeFromList != null) {
                        resolvedMalId = jikanService?.searchAnimeByTitle(animeFromList.title)
                        android.util.Log.d("MAL_DEBUG", "Jikan search by list title returned: malId=$resolvedMalId")
                    }
                }
                
                if (resolvedMalId != null) {
                    queueSync(mediaId, "progress", malId = resolvedMalId, progress = progress)
                }
            }
            return
        }
        
        queueSync(mediaId, "progress", malId = malId, progress = progress)
    }
    
    private fun updateProgressInLists(mediaId: Int, progress: Int) {
        val updateInList: (MutableStateFlow<List<AnimeMedia>>, (AnimeMedia) -> AnimeMedia) -> Unit = { list, updater ->
            list.value = list.value.map { if (it.id == mediaId) updater(it) else it }
        }
        
        updateInList(_currentlyWatching) { it.copy(progress = progress) }
        updateInList(_planningToWatch) { it.copy(progress = progress) }
        updateInList(_completed) { it.copy(progress = progress) }
        updateInList(_onHold) { it.copy(progress = progress) }
        updateInList(_dropped) { it.copy(progress = progress) }
    }

    fun updateAnimeStatus(mediaId: Int, status: String, progress: Int? = null) {
        val currentEntry = userPreferences.getLocalAnimeStatus(mediaId)
        val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
        val malId = cachedAnime?.malId
        
        if (_loginProvider.value == LoginProvider.MAL && malId == null) {
            android.util.Log.d("MAL_DEBUG", "MAL ID not in cache, fetching fresh anime details for mediaId=$mediaId")
            viewModelScope.launch {
                cacheManager.clearDetailedAnimeCache(mediaId)
                val details = fetchDetailedAnimeData(mediaId)
                var resolvedMalId = details?.malId
                android.util.Log.d("MAL_DEBUG", "Fetched from AniList: malId=$resolvedMalId, details=${if(details != null) details.title else "null"}")
                
                if (resolvedMalId == null && details == null) {
                    val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
                    val animeFromList = allAnime.find { it.id == mediaId }
                    if (animeFromList != null) {
                        android.util.Log.d("MAL_DEBUG", "Found anime in user's list: ${animeFromList.title}")
                        resolvedMalId = jikanService?.searchAnimeByTitle(animeFromList.title)
                        android.util.Log.d("MAL_DEBUG", "Jikan search by list title returned: malId=$resolvedMalId")
                    }
                } else if (resolvedMalId == null && details != null) {
                    android.util.Log.d("MAL_DEBUG", "Trying Jikan API fallback for: ${details.title}")
                    resolvedMalId = jikanService?.searchAnimeByTitle(details.title)
                    android.util.Log.d("MAL_DEBUG", "Jikan returned: malId=$resolvedMalId")
                }
                
                if (resolvedMalId == null) {
                    android.util.Log.d("MAL_DEBUG", "Could not find MAL ID for this anime!")
                }
                
                setLocalAnimeStatus(
                    mediaId,
                    LocalAnimeEntry(
                        id = mediaId,
                        status = status,
                        progress = progress ?: currentEntry?.progress ?: 0,
                        totalEpisodes = details?.episodes ?: currentEntry?.totalEpisodes ?: 0
                    )
                )
                moveAnimeBetweenLists(mediaId, status, progress)
                queueSync(mediaId, "status", malId = resolvedMalId, status = status, progress = progress)
            }
            return
        }
        
        setLocalAnimeStatus(
            mediaId,
            LocalAnimeEntry(
                id = mediaId,
                status = status,
                progress = progress ?: currentEntry?.progress ?: 0,
                totalEpisodes = cachedAnime?.episodes ?: currentEntry?.totalEpisodes ?: 0
            )
        )
        
        // Immediately update logged-in lists for instant visual feedback
        moveAnimeBetweenLists(mediaId, status, progress)
        
        queueSync(mediaId, "status", malId = malId, status = status, progress = progress)
    }
    
    private fun moveAnimeBetweenLists(mediaId: Int, newStatus: String, newProgress: Int?) {
        // Find the anime in the current list
        val allLists = listOf(
            _currentlyWatching.value to { l: List<AnimeMedia> -> _currentlyWatching.value = l },
            _planningToWatch.value to { l: List<AnimeMedia> -> _planningToWatch.value = l },
            _completed.value to { l: List<AnimeMedia> -> _completed.value = l },
            _onHold.value to { l: List<AnimeMedia> -> _onHold.value = l },
            _dropped.value to { l: List<AnimeMedia> -> _dropped.value = l }
        )
        
        var anime: AnimeMedia? = null
        var sourceListIndex = -1
        
        for ((index, pair) in allLists.withIndex()) {
            val (list, _) = pair
            val found = list.find { it.id == mediaId }
            if (found != null) {
                anime = found
                sourceListIndex = index
                break
            }
        }
        
        // If anime not found in any list, create a new entry from cached/local data
        if (anime == null) {
            val localEntry = userPreferences.getLocalAnimeStatus(mediaId)
            val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
            
            anime = if (cachedAnime != null) {
                AnimeMedia(
                    id = cachedAnime.id,
                    title = cachedAnime.title,
                    titleEnglish = cachedAnime.titleEnglish,
                    cover = cachedAnime.cover,
                    banner = cachedAnime.banner,
                    progress = newProgress ?: 0,
                    totalEpisodes = cachedAnime.episodes,
                    latestEpisode = cachedAnime.nextAiringEpisode,
                    status = cachedAnime.status ?: "",
                    averageScore = cachedAnime.averageScore,
                    genres = cachedAnime.genres,
                    listStatus = newStatus,
                    year = cachedAnime.year,
                    malId = cachedAnime.malId,
                    format = cachedAnime.format
                )
            } else if (localEntry != null) {
                AnimeMedia(
                    id = localEntry.id,
                    title = localEntry.title.ifEmpty { "Unknown" },
                    cover = localEntry.cover,
                    banner = localEntry.banner,
                    progress = newProgress ?: localEntry.progress,
                    totalEpisodes = localEntry.totalEpisodes,
                    listStatus = newStatus,
                    year = localEntry.year,
                    averageScore = localEntry.averageScore
                )
            } else {
                // No data available, skip
                return
            }
        }
        
        // If anime was found in a source list, remove from it first
        if (sourceListIndex >= 0) {
            val (sourceList, sourceSetter) = allLists[sourceListIndex]
            sourceSetter(sourceList.filter { it.id != mediaId })
        }
        
        // Update the anime with new status and progress
        val updatedAnime = anime.copy(
            listStatus = newStatus,
            progress = newProgress ?: anime.progress
        )
        
        // Add to target list
        val targetList = when (newStatus) {
            "CURRENT" -> _currentlyWatching
            "PLANNING" -> _planningToWatch
            "COMPLETED" -> _completed
            "PAUSED" -> _onHold
            "DROPPED" -> _dropped
            else -> return
        }
        
        targetList.value = targetList.value + updatedAnime
    }

    fun removeAnimeFromList(mediaId: Int) {
        val entryId = (currentlyWatching.value + planningToWatch.value + completed.value + onHold.value + dropped.value)
            .find { it.id == mediaId }?.listEntryId
        
        // Immediately remove from all lists for instant feedback
        _currentlyWatching.value = _currentlyWatching.value.filter { it.id != mediaId }
        _planningToWatch.value = _planningToWatch.value.filter { it.id != mediaId }
        _completed.value = _completed.value.filter { it.id != mediaId }
        _onHold.value = _onHold.value.filter { it.id != mediaId }
        _dropped.value = _dropped.value.filter { it.id != mediaId }
        
        setLocalAnimeStatus(mediaId, null)
        if (entryId != null) {
            queueSync(mediaId, "delete", entryId = entryId)
        }
    }

    fun hasLocalAnimeChanges(): Boolean {
        return localAnimeStatus.value.isNotEmpty()
    }

    fun discardLocalChanges() {
        userPreferences.clearLocalAnimeStatus()
        updateOfflineLists()
    }

    fun addLocalToAniListOnlyNew() {
        viewModelScope.launch {
            val localStatus = localAnimeStatus.value
            val allAniListEntries = currentlyWatching.value + planningToWatch.value + completed.value + onHold.value + dropped.value
            
            for ((mediaId, entry) in localStatus) {
                if (allAniListEntries.none { it.id == mediaId }) {
                    repository.updateStatus(mediaId, entry.status, entry.progress)
                }
            }
            userPreferences.clearLocalAnimeStatus()
            updateOfflineLists()
            fetchLists()
        }
    }

    fun overwriteAniListWithLocal() {
        viewModelScope.launch {
            val localStatus = localAnimeStatus.value
            val allAniListEntries = currentlyWatching.value + planningToWatch.value + completed.value + onHold.value + dropped.value
            
            for (entry in allAniListEntries) {
                val localEntry = localStatus[entry.id]
                if (localEntry != null) {
                    repository.updateStatus(entry.id, localEntry.status, localEntry.progress)
                }
            }
            
            for ((mediaId, entry) in localStatus) {
                if (allAniListEntries.none { it.id == mediaId }) {
                    repository.updateStatus(mediaId, entry.status, entry.progress)
                }
            }
            
            userPreferences.clearLocalAnimeStatus()
            updateOfflineLists()
            fetchLists()
        }
    }

    // Settings
    fun setOledMode(enabled: Boolean) = userPreferences.setOledMode(enabled)
    fun setDisableMaterialColors(enabled: Boolean) = userPreferences.setDisableMaterialColors(enabled)
    fun setPreferredCategory(category: String) = userPreferences.setPreferredCategory(category)
    fun setShowStatusColors(enabled: Boolean) = userPreferences.setShowStatusColors(enabled)
    fun setShowAnimeCardButtons(enabled: Boolean) = userPreferences.setShowAnimeCardButtons(enabled)
    fun setPreferEnglishTitles(enabled: Boolean) = userPreferences.setPreferEnglishTitles(enabled)
    fun setPreventScheduleSync(enabled: Boolean) = userPreferences.setPreventScheduleSync(enabled)
    fun setTrackingPercentage(percentage: Int) = userPreferences.setTrackingPercentage(percentage)
    fun setForwardSkipSeconds(seconds: Int) = userPreferences.setForwardSkipSeconds(seconds)
    fun setBackwardSkipSeconds(seconds: Int) = userPreferences.setBackwardSkipSeconds(seconds)
    fun setHideNavbarText(enabled: Boolean) = userPreferences.setHideNavbarText(enabled)
    fun setSimplifyEpisodeMenu(enabled: Boolean) = userPreferences.setSimplifyEpisodeMenu(enabled)
    fun setSimplifyAnimeDetails(enabled: Boolean) = userPreferences.setSimplifyAnimeDetails(enabled)
    fun setAutoSkipOpening(enabled: Boolean) = userPreferences.setAutoSkipOpening(enabled)
    fun setAutoSkipEnding(enabled: Boolean) = userPreferences.setAutoSkipEnding(enabled)
    fun setAutoPlayNextEpisode(enabled: Boolean) = userPreferences.setAutoPlayNextEpisode(enabled)
    fun setEnableThumbnailPreview(enabled: Boolean) = userPreferences.setEnableThumbnailPreview(enabled)
    fun setPreferredScraper(scraper: String) = userPreferences.setPreferredScraper(scraper)
    fun setHideAdultContent(enabled: Boolean) = userPreferences.setHideAdultContent(enabled)
    fun setStartupScreen(screen: Int) = userPreferences.setStartupScreen(screen)
    fun setBufferAheadSeconds(seconds: Int) = userPreferences.setBufferAheadSeconds(seconds)
    fun setBufferSizeMb(sizeMb: Int) = userPreferences.setBufferSizeMb(sizeMb)
    fun setShowBufferIndicator(show: Boolean) = userPreferences.setShowBufferIndicator(show)

    // Favorites
    fun toggleLocalFavorite(mediaId: Int) {
        userPreferences.toggleLocalFavorite(mediaId)
        updateOfflineLists()
    }
    fun toggleLocalFavorite(mediaId: Int, title: String, cover: String, banner: String?, year: Int?, averageScore: Int?) {
        userPreferences.toggleLocalFavorite(mediaId, title, cover, banner, year, averageScore)
        updateOfflineLists()
    }
    fun toggleLocalFavorite(anime: ExploreAnime) {
        userPreferences.toggleLocalFavorite(anime.id, anime.title, anime.cover, anime.banner, anime.year, anime.averageScore)
        updateOfflineLists()
    }
    fun toggleLocalFavorite(anime: AnimeMedia) {
        userPreferences.toggleLocalFavorite(anime.id, anime.title, anime.cover, anime.banner, anime.year, anime.averageScore)
        updateOfflineLists()
    }
    fun toggleLocalFavorite(anime: DetailedAnimeData) {
        userPreferences.toggleLocalFavorite(anime.id, anime.title, anime.cover, anime.banner, anime.year, anime.averageScore)
        updateOfflineLists()
    }
    fun toggleOfflineFavorite(animeId: Int, title: String, cover: String, banner: String?, year: Int?, averageScore: Int?) {
        userPreferences.toggleLocalFavorite(animeId, title, cover, banner, year, averageScore)
        updateOfflineLists()
    }
    fun isLocalFavorite(mediaId: Int) = userPreferences.isLocalFavorite(mediaId)
    fun canAddFavorite() = userPreferences.canAddFavorite()
    fun getLocalFavoriteCount() = userPreferences.getLocalFavoriteCount()

    // Local Anime Status (for offline users)
    fun getLocalAnimeStatus(mediaId: Int): LocalAnimeEntry? = userPreferences.getLocalAnimeStatus(mediaId)
    fun setLocalAnimeStatus(mediaId: Int, entry: LocalAnimeEntry?) {
        userPreferences.setLocalAnimeStatus(mediaId, entry)
        updateOfflineLists()
    }
    fun updateLocalAnimeProgress(mediaId: Int, progress: Int, totalEpisodes: Int) {
        userPreferences.updateLocalAnimeProgress(mediaId, progress, totalEpisodes)
        updateOfflineLists()
    }

    // Playback
    fun savePlaybackPosition(animeId: Int, episode: Int, position: Long) = cacheManager.savePlaybackPosition(animeId, episode, position)
    fun getPlaybackPosition(animeId: Int, episode: Int) = cacheManager.getPlaybackPosition(animeId, episode)
    fun clearPlaybackPosition(animeId: Int, episode: Int) = cacheManager.clearPlaybackPosition(animeId, episode)
    fun clearAllPlaybackPositionsForAnime(animeId: Int) = cacheManager.clearAllPlaybackPositionsForAnime(animeId)

    // Stream cache invalidation
    fun invalidateStreamCache(animeId: Int, episode: Int, category: String) {
        cacheManager.invalidateStreamCache(animeId, episode, category)
    }

    // Cache management
    fun getVideoCacheSize(context: Context): Long = cacheManager.getVideoCacheSize(context)
    fun clearVideoCache(context: Context): Long = cacheManager.clearVideoCache(context)
    fun clearNonEssentialCaches(context: Context) = cacheManager.clearNonEssentialCaches(context)

    /**
     * Get the best title for Animekai scraping.
     * Prefers English title for better search results on Animekai.
     * Falls back to romaji/native title if English is not available.
     */
    private fun getScrapingName(anime: AnimeMedia): String {
        // Use English title for Animekai scraping - it matches better
        return anime.titleEnglish ?: anime.title
    }

    /**
     * Get the best title for Animekai scraping from DetailedAnimeData.
     */
    private fun getScrapingName(anime: DetailedAnimeData): String {
        return anime.titleEnglish ?: anime.title
    }

    suspend fun tryAllServersWithFallback(name: String, ep: Int, id: Int, latest: Int) =
        tryAllScrapersWithFallback(name, ep, id, latest, preferredCategory.value)

    /**
     * Get stream for a specific server.
     * Animekai only - no Animepahe fallback.
     */
    suspend fun getStreamForServer(
        animeName: String,
        episodeNumber: Int,
        serverName: String,
        category: String,
        animeId: Int
    ): AniwatchStreamResult? = withContext(Dispatchers.IO) {

        // Use the new method that actually resolves the specific server
        val animekaiResult = AnimekaiScraper.getStreamForSpecificServer(animeName, episodeNumber, serverName, category)
        animekaiResult?.let {
            val aniwatchResult = AnimekaiScraper.toAniwatchStreamResult(it)
            // Cache with the actual category returned
            val actualKey = "${animeId}_${episodeNumber}_${it.category}"
            cacheManager.cacheStream(actualKey, aniwatchResult)
            aniwatchResult
        }
    }

    /**
     * Get episode info (servers list).
     * Animekai only.
     * Returns null if episode is not yet released (episode > latestAired).
     */
    suspend fun getEpisodeInfo(name: String, ep: Int, id: Int, latest: Int): EpisodeStreams? {
        // Check if episode is released
        if (latest > 0 && ep > latest) {
            return null
        }

        // Episode info key doesn't include category since it contains both sub and dub
        val epKey = "${id}_$ep"

        // Check cache first
        cacheManager.getCachedEpisodeInfo(epKey)?.let { cached ->
            return cached
        }

        // Only use Animekai
        val animekaiInfo = AnimekaiScraper.getEpisodeInfo(name, ep)
        val result = animekaiInfo?.let { AnimekaiScraper.toEpisodeStreams(it) }

        // Cache the result
        result?.let {
            cacheManager.cacheEpisodeInfo(epKey, it)
        }

        return result
    }

    /**
     * Check if a specific category is available for an episode.
     * Returns true if the category has at least one server.
     */
    fun isCategoryAvailable(episodeInfo: EpisodeStreams?, category: String): Boolean {
        if (episodeInfo == null) return false
        return if (category == "dub") {
            episodeInfo.dubServers.isNotEmpty()
        } else {
            episodeInfo.subServers.isNotEmpty()
        }
    }

    /**
     * Animekai only - Animepahe is disabled. Hianime only as last resort fallback.
     * Returns null if episode is not yet released.
     */
    private suspend fun tryAllScrapersWithFallback(
        animeName: String,
        episodeNumber: Int,
        animeId: Int,
        latestAiredEpisode: Int = Int.MAX_VALUE,
        preferredCategory: String
    ): StreamFetchResult = withContext(Dispatchers.IO) {
        // Check if episode is released
        if (latestAiredEpisode > 0 && episodeNumber > latestAiredEpisode) {
            return@withContext StreamFetchResult(null, false, preferredCategory, preferredCategory)
        }


        // Construct the cache key - include category so sub/dub are cached separately
        val key = "${animeId}_${episodeNumber}_$preferredCategory"

        // Check CacheManager first (Persistent Cache)
        cacheManager.getCachedStream(key)?.let { cachedStream ->
            return@withContext StreamFetchResult(cachedStream, false, preferredCategory, cachedStream.category)
        }

        // Try Animekai first (primary scraper)
        val animekaiResult = AnimekaiScraper.getStreamWithFallback(animeName, episodeNumber, preferredCategory)
        if (animekaiResult != null) {
            val result = AnimekaiScraper.toAniwatchStreamResult(animekaiResult)
            val categoryKey = "${animeId}_${episodeNumber}_${animekaiResult.category}"
            cacheManager.cacheStream(categoryKey, result)
            return@withContext StreamFetchResult(result, result.category != preferredCategory, preferredCategory, result.category)
        }

        // Fallback to Hianime if Animekai fails completely
        val hianimeResult = repository.tryAllServersWithFallback(animeName, episodeNumber, animeId, latestAiredEpisode, preferredCategory)
        if (hianimeResult.stream != null) {
            val hianimeKey = "${animeId}_${episodeNumber}_${hianimeResult.actualCategory}"
            cacheManager.cacheStream(hianimeKey, hianimeResult.stream)
        }
        return@withContext hianimeResult
    }

    suspend fun fetchDetailedAnimeData(animeId: Int): DetailedAnimeData? {
        android.util.Log.d("ANILIST_DEBUG", "fetchDetailedAnimeData called for animeId=$animeId")
        android.util.Log.d("ANILIST_DEBUG", "Not in cache, fetching from AniList API...")
        val media = repository.fetchDetailedAnime(animeId)
        if (media == null) {
            android.util.Log.e("ANILIST_DEBUG", "AniList API returned null for animeId=$animeId")
            return null
        }
        android.util.Log.d("ANILIST_DEBUG", "AniList API returned: id=${media.id}, idMal=${media.idMal}, title=${media.title?.romaji}, isAdult=${media.isAdult}, hasCharacters=${media.characters != null}, hasStaff=${media.staff != null}")
        val relationsList = media.relations?.edges?.mapNotNull { edge ->
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
        } ?: emptyList()
        val detailedData = DetailedAnimeData(
            id = media.id,
            malId = media.idMal,
            title = media.title?.romaji ?: media.title?.english ?: "Unknown",
            titleRomaji = media.title?.romaji, titleEnglish = media.title?.english, titleNative = media.title?.native,
            cover = media.coverImage?.extraLarge ?: "", banner = media.bannerImage, description = media.description,
            episodes = media.episodes ?: 0, duration = media.duration, status = media.status,
            averageScore = media.averageScore, popularity = media.popularity, favourites = media.favourites,
            genres = media.genres ?: emptyList(), tags = media.tags ?: emptyList(), season = media.season, year = media.seasonYear ?: media.startDate?.year,
            format = media.format, source = media.source,
            studios = media.studios?.nodes?.map { StudioData(it.id ?: 0, it.name ?: "") } ?: emptyList(),
            startDate = media.startDate?.let { "${it.year}-${it.month}-${it.day}" },
            endDate = media.endDate?.let { "${it.year}-${it.month}-${it.day}" },
            latestEpisode = media.nextAiringEpisode?.episode?.let { it - 1 },
            nextAiringEpisode = media.nextAiringEpisode?.episode, nextAiringTime = media.nextAiringEpisode?.airingAt,
            relations = relationsList,
            isAdult = media.isAdult ?: false,
            characters = media.characters,
            trailerUrl = media.trailer?.let { 
                if (it.site == "youtube") "https://www.youtube.com/watch?v=${it.id}" 
                else if (it.site == "dailymotion") "https://www.dailymotion.com/video/${it.id}"
                else null 
            },
            trailerThumbnail = media.trailer?.let {
                if (it.site == "youtube" && it.id != null) {
                    "https://img.youtube.com/vi/${it.id}/maxresdefault.jpg"
                } else null
            },
            staff = media.staff
        )
        cacheManager.cacheDetailedAnime(animeId, detailedData)
        return detailedData
    }

    suspend fun fetchAnimeRelations(animeId: Int): List<AnimeRelation>? {
        return repository.fetchAnimeRelationsList(animeId)
    }
    
    suspend fun fetchDetailedAnimeDataByMalId(malId: Int): DetailedAnimeData? {
        android.util.Log.d("ANILIST_DEBUG", "fetchDetailedAnimeDataByMalId called for malId=$malId")
        val media = repository.findAnimeByMalId(malId)
        if (media == null) {
            android.util.Log.e("ANILIST_DEBUG", "Could not find anime with MAL ID=$malId on AniList")
            return null
        }
        android.util.Log.d("ANILIST_DEBUG", "Found anime on AniList: id=${media.id}, title=${media.title?.romaji ?: media.title?.english}")
        return fetchDetailedAnimeData(media.id)
    }

    suspend fun fetchCharacter(characterId: Int) = repository.fetchCharacter(characterId)
    suspend fun fetchStaff(staffId: Int) = repository.fetchStaff(staffId)
    suspend fun fetchAllCharacters(animeId: Int) = repository.fetchAllCharacters(animeId)
    suspend fun fetchAllStaff(animeId: Int) = repository.fetchAllStaff(animeId)

    // Search & Activity
    suspend fun searchAnime(query: String) = repository.searchAnime(query).map { mapExploreMedia(it) }
    fun searchCompletedAnime(query: String) {
        _completedSearchResults.value = if (query.isEmpty()) _completed.value else _completed.value.filter { it.title.contains(query, ignoreCase = true) }
    }
    fun loadAllCompletedAnime() { _completedSearchResults.value = _completed.value }
    fun fetchUserActivity() {
        val userId = _userId.value ?: return
        viewModelScope.launch { repository.fetchUserActivity(userId)?.let { _userActivity.value = it } }
    }
    fun fetchUserStats() {
        val userId = _userId.value ?: return
        viewModelScope.launch { 
            repository.fetchUserStats(userId)?.let { 
                _userStats.value = it.data.User.statistics.anime
            } 
        }
    }
    fun fetchAniListFavorites() {
        val userId = _userId.value ?: return
        viewModelScope.launch {
            android.util.Log.d("AniListFavorite", "fetchAniListFavorites: fetching for userId=$userId")
            repository.fetchUserFavorites(userId)?.let { response ->
                android.util.Log.d("AniListFavorite", "fetchAniListFavorites: got ${response.data.User.favourites.anime.nodes.size} favorites")
                val apiFavorites = response.data.User.favourites.anime.nodes
                // Merge API favorites with locally stored favorites to preserve offline additions
                val localFavoriteIds = userPreferences.aniListFavorites.value
                val mergedFavorites = apiFavorites.map { apiFav ->
                    val isLocalFavorite = localFavoriteIds.contains(apiFav.id)
                    if (isLocalFavorite) {
                        // Keep local version which might have more up-to-date info
                        val localFav = _aniListFavorites.value.find { it.id == apiFav.id }
                        localFav ?: apiFav
                    } else {
                        apiFav
                    }
                }.toMutableList()
                
                // Add any favorites that were added locally but not on API yet
                localFavoriteIds.forEach { localId ->
                    if (mergedFavorites.none { it.id == localId }) {
                        val localOnly = _aniListFavorites.value.find { it.id == localId }
                        if (localOnly != null) {
                            mergedFavorites.add(localOnly)
                        }
                    }
                }
                
                _aniListFavorites.value = mergedFavorites
            } ?: run {
                android.util.Log.d("AniListFavorite", "fetchAniListFavorites: API returned null, keeping local favorites")
            }
        }
    }

    fun loadAniListFavoritesFromStorage() {
        // Load favorites from UserPreferences (IDs only)
        val favoriteIds = userPreferences.aniListFavorites.value
        android.util.Log.d("AniListFavorite", "loadAniListFavoritesFromStorage: found ${favoriteIds.size} favorites")
        android.util.Log.d("AniListFavorite", "  detailedAnimeCache keys: ${cacheManager.detailedAnimeCache.value.keys.take(10)}")
        
        if (favoriteIds.isEmpty()) {
            _aniListFavorites.value = emptyList()
            return
        }
        
        // Convert IDs to UserFavoriteAnime placeholders (will be enriched by detailedAnimeCache if available)
        val favorites = favoriteIds.mapNotNull { id ->
            android.util.Log.d("AniListFavorite", "  Processing favorite id: $id")
            val cached = cacheManager.detailedAnimeCache.value[id]
            if (cached != null) {
                android.util.Log.d("AniListFavorite", "    Found in detailedAnimeCache: ${cached.title}")
                UserFavoriteAnime(
                    id = cached.id,
                    title = MediaTitle(romaji = cached.title, english = cached.titleEnglish),
                    coverImage = MediaCoverImage(large = cached.cover, medium = cached.cover),
                    episodes = cached.episodes,
                    averageScore = cached.averageScore,
                    genres = cached.genres,
                    seasonYear = cached.year
                )
            } else {
                // Try to find in currently watching lists
                val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
                android.util.Log.d("AniListFavorite", "    Not in cache, searching lists among ${allAnime.size} items")
                val anime = allAnime.find { it.id == id }
                if (anime != null) {
                    android.util.Log.d("AniListFavorite", "    Found in list: ${anime.title}")
                    UserFavoriteAnime(
                        id = anime.id,
                        title = MediaTitle(romaji = anime.title, english = anime.titleEnglish),
                        coverImage = MediaCoverImage(large = anime.cover, medium = anime.cover),
                        episodes = anime.totalEpisodes,
                        averageScore = anime.averageScore,
                        genres = anime.genres,
                        seasonYear = anime.year
                    )
                } else {
                    android.util.Log.d("AniListFavorite", "    No anime found for id $id, showing placeholder")
                    // Just use ID as placeholder
                    UserFavoriteAnime(
                        id = id,
                        title = MediaTitle(romaji = "Loading...", english = null),
                        coverImage = MediaCoverImage(large = "", medium = ""),
                        episodes = null,
                        averageScore = null,
                        genres = emptyList(),
                        seasonYear = null
                    )
                }
            }
        }
        _aniListFavorites.value = favorites
        android.util.Log.d("AniListFavorite", "Loaded ${favorites.size} favorites into UI")
    }
    fun toggleAniListFavorite(mediaId: Int, anime: AnimeMedia? = null): Boolean {
        android.util.Log.d("AniListFavorite", "toggleAniListFavorite called: mediaId=$mediaId, anime=${anime?.title}, loginProvider=${_loginProvider.value}")
        
        if (_loginProvider.value == LoginProvider.MAL) {
            // Toggle MAL favorite using the ID-based method
            toggleMalFavoriteById(mediaId)
        } else {
            // Toggle AniList favorite - local-first with persistence
            val isFavorite = userPreferences.isAniListFavorite(mediaId)
            val willBeAdded = !isFavorite
            
            // Update persisted storage
            userPreferences.toggleAniListFavorite(mediaId)
            
            // Update UI list
            if (isFavorite) {
                _aniListFavorites.value = _aniListFavorites.value.filter { it.id != mediaId }
            } else {
                if (anime != null) {
                    val userFavorite = UserFavoriteAnime(
                        id = anime.id,
                        title = MediaTitle(romaji = anime.title, english = anime.titleEnglish),
                        coverImage = MediaCoverImage(large = anime.cover, medium = anime.cover),
                        episodes = anime.totalEpisodes,
                        averageScore = anime.averageScore,
                        genres = anime.genres,
                        seasonYear = anime.year
                    )
                    _aniListFavorites.value = _aniListFavorites.value + userFavorite
                } else {
                    val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
                    val placeholder = UserFavoriteAnime(
                        id = mediaId,
                        title = MediaTitle(romaji = cachedAnime?.title ?: "Loading...", english = cachedAnime?.titleEnglish),
                        coverImage = MediaCoverImage(large = cachedAnime?.cover ?: "", medium = cachedAnime?.cover ?: ""),
                        episodes = cachedAnime?.episodes,
                        averageScore = cachedAnime?.averageScore,
                        genres = cachedAnime?.genres ?: emptyList(),
                        seasonYear = cachedAnime?.year
                    )
                    _aniListFavorites.value = _aniListFavorites.value + placeholder
                }
            }
            
            // Queue the API call for debounced sync with the desired state
            queueSync(mediaId, "favorite", favoriteAdded = willBeAdded)
            return true
        }
        return true
    }
    fun updateAnimeRating(mediaId: Int, score: Int) {
        // Immediately update rating in lists
        updateRatingInLists(mediaId, score)
        queueSync(mediaId, "score", score = score)
    }
    
    private fun updateRatingInLists(mediaId: Int, score: Int) {
        val updateInList: (MutableStateFlow<List<AnimeMedia>>, (AnimeMedia) -> AnimeMedia) -> Unit = { list, updater ->
            list.value = list.value.map { if (it.id == mediaId) updater(it) else it }
        }
        
        updateInList(_currentlyWatching) { it.copy(userScore = score) }
        updateInList(_planningToWatch) { it.copy(userScore = score) }
        updateInList(_completed) { it.copy(userScore = score) }
        updateInList(_onHold) { it.copy(userScore = score) }
        updateInList(_dropped) { it.copy(userScore = score) }
    }

    // ============================================
    // PREFETCHING - Streams for adjacent episodes
    // ============================================

    /**
     * Check if a stream is already cached (synchronous, instant check).
     * Use this to avoid showing loading indicator for cached streams.
     */
    fun isStreamCached(animeId: Int, episode: Int, category: String): Boolean {
        val key = "${animeId}_${episode}_$category"
        return cacheManager.hasStream(key)
    }

    /**
     * Get cached stream immediately if available (synchronous).
     * Returns null if not cached - caller should then use suspend function.
     * This is the KEY method for instant playback - no loading indicator shown.
     */
    fun getCachedStreamImmediate(animeId: Int, episode: Int, category: String): AniwatchStreamResult? {
        val key = "${animeId}_${episode}_$category"
        return cacheManager.getCachedStream(key)
    }

    /**
     * Prefetch stream for the next episode to watch (progress + 1).
     * Called when viewing home screen for currently watching anime.
     */
    fun prefetchCurrentEpisodeStream(anime: AnimeMedia) {
        val nextEp = anime.progress + 1
        val latest = anime.latestEpisode ?: anime.totalEpisodes
        if ((latest > 0 && nextEp > latest) || (anime.totalEpisodes > 0 && nextEp > anime.totalEpisodes)) return
        val category = preferredCategory.value
        if (cacheManager.hasStream("${anime.id}_${nextEp}_$category")) return
        val scrapingName = getScrapingName(anime)
        viewModelScope.launch(Dispatchers.IO) {
            tryAllScrapersWithFallback(scrapingName, nextEp, anime.id, latest, category)
        }
    }

    /**
     * Prefetch streams for adjacent episodes (previous and next) during playback.
     * This preloads the actual stream URLs so episode transitions are instant.
     * Called from PlayerScreen during playback.
     * Fetches BOTH sub and dub for each adjacent episode.
     * Skips unreleased episodes.
     */
    fun prefetchNextEpisodeStream(
        animeName: String,
        currentEpisode: Int,
        animeId: Int,
        latestAired: Int,
        category: String? = null
    ) {
        android.util.Log.d("PREFETCH", "prefNextEpisode: anime='$animeName', currentEp=$currentEpisode, animeId=$animeId, latestAired=$latestAired")
        
        viewModelScope.launch(Dispatchers.IO) {
            // Prefetch BOTH sub and dub for NEXT episode only
            listOf("sub", "dub").forEach { prefetchCategory ->
                // Prefetch next episode stream - only if released
                val nextEp = currentEpisode + 1
                // nextEp is released if latestAired <= 0 (unknown) OR nextEp <= latestAired
                val isReleased = latestAired <= 0 || nextEp <= latestAired
                
                if (isReleased) {
                    val nextKey = "${animeId}_${nextEp}_$prefetchCategory"
                    if (!cacheManager.hasStream(nextKey)) {
                        android.util.Log.d("PREFETCH", "  Fetching next Ep $nextEp ($prefetchCategory)")
                        tryAllScrapersWithFallback(animeName, nextEp, animeId, latestAired, prefetchCategory)
                    } else {
                        android.util.Log.d("PREFETCH", "  Next Ep $nextEp ($prefetchCategory) - already cached")
                    }
                } else {
                    android.util.Log.d("PREFETCH", "  Next Ep $nextEp - skipped (not released yet, latestAired=$latestAired)")
                }
            }
        }
    }

    fun prefetchAdjacentEpisodeStreams(
        animeName: String,
        currentEpisode: Int,
        animeId: Int,
        latestAired: Int,
        category: String? = null
    ) {
        // Delegate to the new method
        prefetchNextEpisodeStream(animeName, currentEpisode, animeId, latestAired, category)
    }

    /**
     * Legacy function name - now calls the stream prefetching function.
     * Kept for backward compatibility with existing code.
     */
    fun prefetchAdjacentEpisodes(name: String, current: Int, id: Int, latest: Int) {
        prefetchAdjacentEpisodeStreams(name, current, id, latest)
    }

    /**
     * Prefetch streams for all anime in "Continue Watching" (Currently Watching list).
     * This preloads the next episode to watch so playback starts instantly.
     * Called after fetching lists on app start.
     * Fetches BOTH sub and dub to ensure instant playback regardless of preference.
     * Also prefetches TMDB episode info for episode titles.
     * Skips unreleased episodes.
     */
    private fun prefetchContinueWatchingStreams() {
        val watchingList = _currentlyWatching.value
        if (watchingList.isEmpty()) return

        android.util.Log.d("PREFETCH", "Starting prefetch for ${watchingList.size} anime in Continue Watching")

        viewModelScope.launch(Dispatchers.IO) {
            watchingList.forEach { anime ->
                // Prefetch TMDB episode info if not cached
                if (cacheManager.getCachedTmdbEpisodes(anime.id) == null) {
                    android.util.Log.d("PREFETCH", "Prefetching TMDB episodes for '${anime.title}' (id=${anime.id})")
                    val tmdbEpisodes = repository.fetchTmdbEpisodes(anime.title, anime.id, anime.year, anime.format, anime.totalEpisodes)
                    if (tmdbEpisodes.isNotEmpty()) {
                        cacheManager.cacheTmdbEpisodes(anime.id, tmdbEpisodes)
                        android.util.Log.d("PREFETCH", "  Cached ${tmdbEpisodes.size} TMDB episodes")
                    }
                }

                val nextEp = anime.progress + 1
                val latest = anime.latestEpisode ?: anime.totalEpisodes

                // Skip if no more episodes to watch OR if episode not yet released
                if ((latest > 0 && nextEp > latest) || (anime.totalEpisodes > 0 && nextEp > anime.totalEpisodes)) {
                    return@forEach
                }

                // Use English title for scraping
                val scrapingName = getScrapingName(anime)
                android.util.Log.d("PREFETCH", "Prefetching '${anime.title}' (Ep $nextEp, category: sub+dub)")

                // Prefetch BOTH sub and dub
                listOf("sub", "dub").forEach { category ->
                    val cacheKey = "${anime.id}_${nextEp}_$category"
                    if (!cacheManager.hasStream(cacheKey)) {
                        android.util.Log.d("PREFETCH", "  Fetching $category for Ep $nextEp")
                        tryAllScrapersWithFallback(scrapingName, nextEp, anime.id, latest, category)
                    } else {
                        android.util.Log.d("PREFETCH", "  $category for Ep $nextEp - already cached")
                    }
                    // Also pre-scrape all servers for this episode and category
                    prefetchAllServersForEpisode(scrapingName, nextEp, anime.id, latest, category)
                }
            }
            android.util.Log.d("PREFETCH", "Completed prefetch for Continue Watching")
        }
    }

    private suspend fun prefetchAllServersForEpisode(
        animeName: String,
        episodeNumber: Int,
        animeId: Int,
        latestAiredEpisode: Int,
        category: String
    ) {
        if (latestAiredEpisode > 0 && episodeNumber > latestAiredEpisode) return
        
        android.util.Log.d("PREFETCH", "  Prefetching all servers for Ep $episodeNumber ($category)")
        
        // Cache episode info first (contains server list for UI)
        val epKey = "${animeId}_$episodeNumber"
        if (!cacheManager.hasEpisodeInfo(epKey)) {
            val episodeInfo = AnimekaiScraper.getEpisodeInfo(animeName, episodeNumber) ?: run {
                android.util.Log.d("PREFETCH", "  No episode info found for Ep $episodeNumber")
                return
            }
            val streams = AnimekaiScraper.toEpisodeStreams(episodeInfo)
            if (streams != null) {
                cacheManager.cacheEpisodeInfo(epKey, streams)
                android.util.Log.d("PREFETCH", "  Episode info cached")
            }
        } else {
            android.util.Log.d("PREFETCH", "  Episode info already cached")
        }
        
        // Get episode info from cache for server list
        val cachedInfo = cacheManager.getCachedEpisodeInfo(epKey) ?: return
        val servers = if (category == "dub") cachedInfo.dubServers else cachedInfo.subServers
        android.util.Log.d("PREFETCH", "  Found ${servers.size} servers for $category")
        
        servers.forEach { server ->
            val key = "${animeId}_${episodeNumber}_$category"
            if (!cacheManager.hasStream(key)) {
                try {
                    android.util.Log.d("PREFETCH", "    Fetching server: ${server.name} ($category)")
                    val result = AnimekaiScraper.getStreamForSpecificServer(animeName, episodeNumber, server.name, category)
                    result?.let {
                        val aniwatchResult = AnimekaiScraper.toAniwatchStreamResult(it)
                        cacheManager.cacheStream(key, aniwatchResult)
                        android.util.Log.d("PREFETCH", "    Server ${server.name} cached successfully")
                    } ?: android.util.Log.d("PREFETCH", "    Server ${server.name} returned no stream")
                } catch (e: Exception) {
                    android.util.Log.e("PREFETCH", "    Server ${server.name} failed: ${e.message}")
                }
            } else {
                android.util.Log.d("PREFETCH", "    Server ${server.name} - already cached")
            }
        }
    }
    
    private fun prefetchOfflineWatchingStreams() {
        val watchingList = _offlineCurrentlyWatching.value
        if (watchingList.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            watchingList.forEach { anime ->
                val nextEp = anime.progress + 1
                val latest = anime.latestEpisode ?: anime.totalEpisodes

                // Skip if no more episodes to watch OR if episode not yet released
                if ((latest > 0 && nextEp > latest) || (anime.totalEpisodes > 0 && nextEp > anime.totalEpisodes)) {
                    return@forEach
                }

                // Use English title for scraping
                val scrapingName = getScrapingName(anime)

                // Prefetch BOTH sub and dub
                listOf("sub", "dub").forEach { category ->
                    val cacheKey = "${anime.id}_${nextEp}_$category"
                    if (!cacheManager.hasStream(cacheKey)) {
                        tryAllScrapersWithFallback(scrapingName, nextEp, anime.id, latest, category)
                    }
                }
            }
        }
    }

    private suspend fun refreshReleasingAnimeProgress() {
        if (_loginProvider.value == LoginProvider.MAL) {
            return
        }
        
        val releasing = _currentlyWatching.value.filter { it.status == "RELEASING" }
        if (releasing.isEmpty()) return
        releasing.chunked(3).forEach { chunk ->
            chunk.map { anime ->
                viewModelScope.async {
                    repository.fetchDetailedAnime(anime.id)?.let { media ->
                        if (media.nextAiringEpisode?.episode != anime.latestEpisode) return@async anime.copy(latestEpisode = media.nextAiringEpisode?.episode)
                    }
                    null
                }
            }.awaitAll().filterNotNull().forEach { updated ->
                _currentlyWatching.value = _currentlyWatching.value.map { if (it.id == updated.id) updated else it }
            }
        }
        saveHomeDataToCache()
    }

    // Misc
    private fun saveHomeDataToCache() = cacheManager.saveHomeDataToCache(HomeCacheData(_currentlyWatching.value, _planningToWatch.value, _completed.value, _onHold.value, _dropped.value, _userId.value, _userName.value, _userAvatar.value))
    private fun saveExploreDataToCache() = cacheManager.saveExploreDataToCache(ExploreCacheData(_featuredAnime.value, _seasonalAnime.value, _topSeries.value, _topMovies.value, _actionAnime.value, _romanceAnime.value, _comedyAnime.value, _fantasyAnime.value, _scifiAnime.value))

    fun refreshHome(force: Boolean = false) {
        val now = System.currentTimeMillis()
        
        // Skip if recently refreshed (unless forced)
        if (!force && now - lastHomeRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            return
        }
        
        lastHomeRefreshTime = now
        cacheManager.invalidateUserCache()
        viewModelScope.launch {
            _isLoadingHome.value = true
            if (_loginProvider.value == LoginProvider.MAL) {
                fetchMalList()
                fetchJikanUserData()
            } else {
                fetchLists()
            }
            _isLoadingHome.value = false
            prefetchContinueWatchingStreams()
        }
    }

    fun forceRefreshExplore() = fetchExploreData(force = true)

    suspend fun fetchTmdbEpisodes(title: String, id: Int, year: Int? = null, format: String? = null, latest: Int = Int.MAX_VALUE) = repository.fetchTmdbEpisodes(title, id, year, format, latest)

    fun getCachedTmdbEpisodes(animeId: Int): List<TmdbEpisode>? = cacheManager.getCachedTmdbEpisodes(animeId)
    
    fun addExploreAnimeToList(anime: ExploreAnime, status: String) {
        android.util.Log.d("MAL_DEBUG", "addExploreAnimeToList: anime.id=${anime.id}, malId=${anime.malId}, status=$status")
        queueSync(anime.id, "status", malId = anime.malId, status = status, progress = if (status == "CURRENT") 0 else null)
        updateAnimeStatus(anime.id, status, if (status == "CURRENT") 0 else null)
    }
    
    override fun onCleared() {
        super.onCleared()
        connectivityCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to unregister connectivity callback: ${e.message}")
            }
        }
    }
}
