package com.blissless.anime

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blissless.anime.api.ZenimeScraper
import com.blissless.anime.api.AnimekaiScraper
import com.blissless.anime.data.AnimeRepository
import com.blissless.anime.data.CacheManager
import com.blissless.anime.data.UserPreferences
import com.blissless.anime.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val CLIENT_ID = BuildConfig.CLIENT_ID_ANILIST
    }

    private lateinit var userPreferences: UserPreferences
    private lateinit var cacheManager: CacheManager
    private lateinit var repository: AnimeRepository
    private lateinit var context: Context

    // UI State
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

    private val _completedSearchResults = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val completedSearchResults: StateFlow<List<AnimeMedia>> = _completedSearchResults.asStateFlow()

    // Preferences Delegations
    val authToken: StateFlow<String?> get() = userPreferences.authToken
    val isOled: StateFlow<Boolean> get() = userPreferences.isOled
    val disableMaterialColors: StateFlow<Boolean> get() = userPreferences.disableMaterialColors
    val preferredCategory: StateFlow<String> get() = userPreferences.preferredCategory
    val showStatusColors: StateFlow<Boolean> get() = userPreferences.showStatusColors
    val trackingPercentage: StateFlow<Int> get() = userPreferences.trackingPercentage
    val forwardSkipSeconds: StateFlow<Int> get() = userPreferences.forwardSkipSeconds
    val backwardSkipSeconds: StateFlow<Int> get() = userPreferences.backwardSkipSeconds
    val forceHighRefreshRate: StateFlow<Boolean> get() = userPreferences.forceHighRefreshRate
    val hideNavbarText: StateFlow<Boolean> get() = userPreferences.hideNavbarText
    val simplifyEpisodeMenu: StateFlow<Boolean> get() = userPreferences.simplifyEpisodeMenu
    val simplifyAnimeDetails: StateFlow<Boolean> get() = userPreferences.simplifyAnimeDetails
    val autoSkipOpening: StateFlow<Boolean> get() = userPreferences.autoSkipOpening
    val autoSkipEnding: StateFlow<Boolean> get() = userPreferences.autoSkipEnding
    val autoPlayNextEpisode: StateFlow<Boolean> get() = userPreferences.autoPlayNextEpisode
    val localFavorites: StateFlow<Map<Int, StoredFavorite>> get() = userPreferences.localFavorites
    val localFavoriteIds: Set<Int> get() = userPreferences.localFavoriteIds
    val preferredScraper: StateFlow<String> get() = userPreferences.preferredScraper

    // Cache Delegations
    val prefetchedStreams: StateFlow<Map<String, AniwatchStreamResult?>> get() = cacheManager.prefetchedStreams
    val prefetchedEpisodeInfo: StateFlow<Map<String, EpisodeStreams?>> get() = cacheManager.prefetchedEpisodeInfo
    val playbackPositions: StateFlow<Map<String, Long>> get() = cacheManager.playbackPositions
    val detailedAnimeCache: StateFlow<Map<Int, DetailedAnimeData>> get() = cacheManager.detailedAnimeCache

    fun init(context: Context, hasToken: Boolean) {
        this.context = context
        userPreferences = UserPreferences(context)
        cacheManager = CacheManager(userPreferences.getSharedPreferences())
        repository = AnimeRepository(userPreferences, cacheManager)

        userPreferences.loadPreferences(hasToken)
        cacheManager.loadStreamCache()
        cacheManager.loadPlaybackPositions()
        loadAiringScheduleCache()

        viewModelScope.launch {
            if (hasToken) loadHomeDataWithCache()
            loadExploreDataWithCache()
            fetchAiringSchedule()
        }
    }

    private suspend fun loadHomeDataWithCache() {
        cacheManager.loadHomeDataFromCache()?.let { updateHomeState(it) }
        _isLoadingHome.value = true
        fetchUser()
        fetchLists()
        _isLoadingHome.value = false
        refreshReleasingAnimeProgress()
        // Prefetch streams for currently watching anime (next episode to watch)
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
        cacheManager.loadExploreDataFromCache()?.let { updateExploreState(it) }
        fetchExploreData()
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
    suspend fun fetchUser() {
        repository.fetchUser()?.let {
            _userId.value = it.data.Viewer.id
            _userName.value = it.data.Viewer.name
            _userAvatar.value = it.data.Viewer.avatar?.medium
        }
    }

    suspend fun fetchLists() {
        val userId = _userId.value ?: return
        val response = repository.fetchMediaLists(userId) ?: return

        val grouped = response.data.MediaListCollection.lists.flatMap { list ->
            list.entries.map { entry ->
                val anime = AnimeMedia(
                    id = entry.mediaId,
                    title = entry.media.title.romaji ?: entry.media.title.english ?: "Unknown",
                    titleEnglish = entry.media.title.english,
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
                (list.status ?: list.name) to anime
            }
        }.groupBy({ it.first }, { it.second })

        _currentlyWatching.value = grouped["CURRENT"] ?: grouped["Watching"] ?: emptyList()
        _planningToWatch.value = grouped["PLANNING"] ?: grouped["Plan to Watch"] ?: emptyList()
        _completed.value = grouped["COMPLETED"] ?: emptyList()
        _onHold.value = grouped["PAUSED"] ?: emptyList()
        _dropped.value = grouped["DROPPED"] ?: emptyList()
        saveHomeDataToCache()
    }

    fun fetchExploreData() {
        viewModelScope.launch {
            _isLoadingExplore.value = true
            repository.fetchBatchedExplore()?.let { response ->
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
            }
            _isLoadingExplore.value = false
        }
    }

    private fun mapExploreMedia(media: ExploreMedia): ExploreAnime = ExploreAnime(
        id = media.id,
        title = media.title.romaji ?: media.title.english ?: "Unknown",
        titleEnglish = media.title.english,
        cover = media.coverImage?.large ?: media.coverImage?.medium ?: "",
        banner = media.bannerImage,
        episodes = media.episodes ?: 0,
        latestEpisode = media.nextAiringEpisode?.episode,
        averageScore = media.averageScore,
        genres = media.genres ?: emptyList(),
        year = media.startDate?.year ?: media.seasonYear,
        malId = media.idMal
    )

    fun fetchAiringSchedule() {
        viewModelScope.launch {
            _isLoadingSchedule.value = true
            val schedules = repository.fetchAiringSchedule()
            val airingList = schedules.filter { it.media != null }.map { schedule ->
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
            }.sortedBy { it.airingAt }

            val scheduleByDay = airingList.groupBy { anime ->
                val calendar = Calendar.getInstance().apply { timeInMillis = anime.airingAt * 1000L }
                calendar.get(Calendar.DAY_OF_WEEK) - 1
            }

            _airingSchedule.value = scheduleByDay
            _airingAnimeList.value = airingList
            cacheManager.saveAiringScheduleCache(scheduleByDay, airingList)
            _isLoadingSchedule.value = false
        }
    }

    fun updateAnimeProgress(mediaId: Int, progress: Int) {
        viewModelScope.launch { if (repository.updateProgress(mediaId, progress)) fetchLists() }
    }

    fun updateAnimeStatus(mediaId: Int, status: String, progress: Int? = null) {
        viewModelScope.launch { if (repository.updateStatus(mediaId, status, progress)) fetchLists() }
    }

    fun removeAnimeFromList(mediaId: Int) {
        val entryId = (currentlyWatching.value + planningToWatch.value + completed.value + onHold.value + dropped.value)
            .find { it.id == mediaId }?.listEntryId ?: return
        viewModelScope.launch { if (repository.deleteListEntry(entryId)) fetchLists() }
    }

    // Settings
    fun setOledMode(enabled: Boolean) = userPreferences.setOledMode(enabled)
    fun setDisableMaterialColors(enabled: Boolean) = userPreferences.setDisableMaterialColors(enabled)
    fun setPreferredCategory(category: String) = userPreferences.setPreferredCategory(category)
    fun setShowStatusColors(enabled: Boolean) = userPreferences.setShowStatusColors(enabled)
    fun setTrackingPercentage(percentage: Int) = userPreferences.setTrackingPercentage(percentage)
    fun setForwardSkipSeconds(seconds: Int) = userPreferences.setForwardSkipSeconds(seconds)
    fun setBackwardSkipSeconds(seconds: Int) = userPreferences.setBackwardSkipSeconds(seconds)
    fun setForceHighRefreshRate(enabled: Boolean) = userPreferences.setForceHighRefreshRate(enabled)
    fun setHideNavbarText(enabled: Boolean) = userPreferences.setHideNavbarText(enabled)
    fun setSimplifyEpisodeMenu(enabled: Boolean) = userPreferences.setSimplifyEpisodeMenu(enabled)
    fun setSimplifyAnimeDetails(enabled: Boolean) = userPreferences.setSimplifyAnimeDetails(enabled)
    fun setAutoSkipOpening(enabled: Boolean) = userPreferences.setAutoSkipOpening(enabled)
    fun setAutoSkipEnding(enabled: Boolean) = userPreferences.setAutoSkipEnding(enabled)
    fun setAutoPlayNextEpisode(enabled: Boolean) = userPreferences.setAutoPlayNextEpisode(enabled)
    fun setEnableThumbnailPreview(enabled: Boolean) = userPreferences.setEnableThumbnailPreview(enabled)
    fun setPreferredScraper(scraper: String) = userPreferences.setPreferredScraper(scraper)

    // Favorites
    fun toggleLocalFavorite(mediaId: Int, title: String, cover: String, banner: String?, year: Int?, averageScore: Int?) = userPreferences.toggleLocalFavorite(mediaId, title, cover, banner, year, averageScore)
    fun toggleLocalFavorite(anime: ExploreAnime) = userPreferences.toggleLocalFavorite(anime.id, anime.title, anime.cover, anime.banner, anime.year, anime.averageScore)
    fun toggleLocalFavorite(anime: AnimeMedia) = userPreferences.toggleLocalFavorite(anime.id, anime.title, anime.cover, anime.banner, anime.year, anime.averageScore)
    fun toggleLocalFavorite(anime: DetailedAnimeData) = userPreferences.toggleLocalFavorite(anime.id, anime.title, anime.cover, anime.banner, anime.year, anime.averageScore)
    fun isLocalFavorite(mediaId: Int) = userPreferences.isLocalFavorite(mediaId)
    fun canAddFavorite() = userPreferences.canAddFavorite()
    fun getLocalFavoriteCount() = userPreferences.getLocalFavoriteCount()

    // Playback
    fun savePlaybackPosition(animeId: Int, episode: Int, position: Long) = cacheManager.savePlaybackPosition(animeId, episode, position)
    fun getPlaybackPosition(animeId: Int, episode: Int) = cacheManager.getPlaybackPosition(animeId, episode)
    fun clearPlaybackPosition(animeId: Int, episode: Int) = cacheManager.clearPlaybackPosition(animeId, episode)
    fun clearAllPlaybackPositionsForAnime(animeId: Int) = cacheManager.clearAllPlaybackPositionsForAnime(animeId)

    // Stream cache invalidation
    fun invalidateStreamCache(animeId: Int, episode: Int, category: String) {
        cacheManager.invalidateStreamCache(animeId, episode, category)
    }

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
        Log.d(TAG, "getStreamForServer: server=$serverName category=$category")

        // Use the new method that actually resolves the specific server
        val animekaiResult = AnimekaiScraper.getStreamForSpecificServer(animeName, episodeNumber, serverName, category)
        animekaiResult?.let {
            val aniwatchResult = AnimekaiScraper.toAniwatchStreamResult(it)
            // Cache with the actual category returned
            val actualKey = "${animeId}_${episodeNumber}_${it.category}"
            cacheManager.cacheStream(actualKey, aniwatchResult)
            Log.d(TAG, "Cached Animekai stream for $actualKey with server ${it.serverName}")
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
            Log.d(TAG, "Episode $ep not yet released (latest: $latest), skipping fetch")
            return null
        }

        // Episode info key doesn't include category since it contains both sub and dub
        val epKey = "${id}_$ep"

        // Check cache first
        cacheManager.getCachedEpisodeInfo(epKey)?.let { cached ->
            Log.d(TAG, "Returning cached episode info for $epKey")
            return cached
        }

        // Only use Animekai
        val animekaiInfo = AnimekaiScraper.getEpisodeInfo(name, ep)
        val result = animekaiInfo?.let { AnimekaiScraper.toEpisodeStreams(it) }

        // Cache the result
        result?.let {
            cacheManager.cacheEpisodeInfo(epKey, it)
            Log.d(TAG, "Cached episode info for $epKey: subServers=${it.subServers.size}, dubServers=${it.dubServers.size}")
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
     * Main stream fetching function with fallback.
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
            Log.d(TAG, "Episode $episodeNumber not yet released (latest: $latestAiredEpisode), skipping fetch")
            return@withContext StreamFetchResult(null, false, preferredCategory, preferredCategory)
        }

        Log.d(TAG, "Fetching stream for $animeName ep $episodeNumber ($preferredCategory)")

        // Construct the cache key - include category so sub/dub are cached separately
        val key = "${animeId}_${episodeNumber}_$preferredCategory"

        // Check CacheManager first (Persistent Cache)
        cacheManager.getCachedStream(key)?.let { cachedStream ->
            Log.d(TAG, "Stream found in CacheManager for $key with category ${cachedStream.category}")
            return@withContext StreamFetchResult(cachedStream, false, preferredCategory, cachedStream.category)
        }

        // Try Animekai first (primary scraper)
        val animekaiResult = AnimekaiScraper.getStreamWithFallback(animeName, episodeNumber, preferredCategory)
        if (animekaiResult != null) {
            val result = AnimekaiScraper.toAniwatchStreamResult(animekaiResult)
            val categoryKey = "${animeId}_${episodeNumber}_${animekaiResult.category}"
            cacheManager.cacheStream(categoryKey, result)
            Log.d(TAG, "Saved Animekai stream to CacheManager for $categoryKey with category ${animekaiResult.category}")
            return@withContext StreamFetchResult(result, result.category != preferredCategory, preferredCategory, result.category)
        }

        // Fallback to Hianime if Animekai fails completely
        Log.d(TAG, "Animekai failed, falling back to Hianime")
        val hianimeResult = repository.tryAllServersWithFallback(animeName, episodeNumber, animeId, latestAiredEpisode, preferredCategory)
        if (hianimeResult.stream != null) {
            val hianimeKey = "${animeId}_${episodeNumber}_${hianimeResult.actualCategory}"
            cacheManager.cacheStream(hianimeKey, hianimeResult.stream)
        }
        return@withContext hianimeResult
    }

    suspend fun fetchDetailedAnimeData(animeId: Int): DetailedAnimeData? {
        cacheManager.getCachedDetailedAnime(animeId)?.let { return it }
        val media = repository.fetchDetailedAnime(animeId) ?: return null
        val detailedData = DetailedAnimeData(
            id = media.id,
            title = media.title?.romaji ?: media.title?.english ?: "Unknown",
            titleRomaji = media.title?.romaji, titleEnglish = media.title?.english, titleNative = media.title?.native,
            cover = media.coverImage?.large ?: "", banner = media.bannerImage, description = media.description,
            episodes = media.episodes ?: 0, duration = media.duration, status = media.status,
            averageScore = media.averageScore, popularity = media.popularity, favourites = media.favourites,
            genres = media.genres ?: emptyList(), season = media.season, year = media.seasonYear ?: media.startDate?.year,
            format = media.format, source = media.source,
            studios = media.studios?.nodes?.map { StudioData(it.id ?: 0, it.name ?: "") } ?: emptyList(),
            startDate = media.startDate?.let { "${it.year}-${it.month}-${it.day}" },
            endDate = media.endDate?.let { "${it.year}-${it.month}-${it.day}" },
            nextAiringEpisode = media.nextAiringEpisode?.episode, nextAiringTime = media.nextAiringEpisode?.airingAt
        )
        cacheManager.cacheDetailedAnime(animeId, detailedData)
        return detailedData
    }

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
    fun updateAnimeRating(mediaId: Int, score: Int) {
        viewModelScope.launch { if (repository.updateScore(mediaId, score)) fetchLists() }
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
            Log.d(TAG, "Prefetching stream for $scrapingName ep $nextEp ($category)")
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
    fun prefetchAdjacentEpisodeStreams(
        animeName: String,
        currentEpisode: Int,
        animeId: Int,
        latestAired: Int,
        category: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Prefetch BOTH sub and dub for previous and next episodes
            listOf("sub", "dub").forEach { prefetchCategory ->
                // Prefetch previous episode stream
                if (currentEpisode > 1) {
                    val prevKey = "${animeId}_${currentEpisode - 1}_$prefetchCategory"
                    if (!cacheManager.hasStream(prevKey)) {
                        Log.d(TAG, "Prefetching previous episode stream: $animeName ep ${currentEpisode - 1} ($prefetchCategory)")
                        tryAllScrapersWithFallback(animeName, currentEpisode - 1, animeId, latestAired, prefetchCategory)
                    }
                }

                // Prefetch next episode stream - only if released
                val nextEp = currentEpisode + 1
                val isReleased = latestAired <= 0 || nextEp <= latestAired
                val isValidNext = isReleased && nextEp <= 24  // Cap at 24 for safety
                if (isValidNext) {
                    val nextKey = "${animeId}_${nextEp}_$prefetchCategory"
                    if (!cacheManager.hasStream(nextKey)) {
                        Log.d(TAG, "Prefetching next episode stream: $animeName ep $nextEp ($prefetchCategory)")
                        tryAllScrapersWithFallback(animeName, nextEp, animeId, latestAired, prefetchCategory)
                    }
                }
            }
        }
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
     * Skips unreleased episodes.
     */
    private fun prefetchContinueWatchingStreams() {
        val watchingList = _currentlyWatching.value
        if (watchingList.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Prefetching streams for ${watchingList.size} currently watching anime (both sub and dub)")
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
                        Log.d(TAG, "Prefetching: $scrapingName ep $nextEp ($category)")
                        tryAllScrapersWithFallback(scrapingName, nextEp, anime.id, latest, category)
                    }
                }
            }
        }
    }

    private suspend fun refreshReleasingAnimeProgress() {
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

    fun refreshHome() {
        cacheManager.invalidateUserCache()
        viewModelScope.launch {
            _isLoadingHome.value = true
            fetchLists()
            _isLoadingHome.value = false
            // Prefetch streams for continue watching after refresh
            prefetchContinueWatchingStreams()
        }
    }

    fun forceRefreshExplore() = fetchExploreData()

    fun loginWithAniList() {
        val url = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    fun handleAuthRedirect(intent: Intent?) {
        intent?.dataString?.takeIf { it.startsWith("animescraper://success") }?.let { uri ->
            uri.replace("#", "?").toUri().getQueryParameter("access_token")?.let { token ->
                userPreferences.saveToken(token)
                viewModelScope.launch {
                    _isLoadingHome.value = true
                    fetchUser()
                    fetchLists()
                    _isLoadingHome.value = false
                    // Prefetch streams for continue watching after login
                    prefetchContinueWatchingStreams()
                }
            }
        }
    }

    fun logout() {
        userPreferences.clearAllUserData()
        cacheManager.clearAllCaches()
        _userId.value = null; _userName.value = null; _userAvatar.value = null
        _currentlyWatching.value = emptyList(); _planningToWatch.value = emptyList(); _completed.value = emptyList(); _onHold.value = emptyList(); _dropped.value = emptyList()
    }

    suspend fun fetchTmdbEpisodes(title: String, id: Int, year: Int? = null, latest: Int = Int.MAX_VALUE) = repository.fetchTmdbEpisodes(title, id, year, latest)
    fun addExploreAnimeToList(anime: ExploreAnime, status: String) = updateAnimeStatus(anime.id, status, if (status == "CURRENT") 0 else null)
}
