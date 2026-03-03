package com.blissless.anime

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.blissless.anime.api.AniwatchService
import com.blissless.anime.api.AniwatchStreamResult
import com.blissless.anime.api.EpisodeStreams

private val Context.dataStore by preferencesDataStore(name = "settings")

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val CLIENT_ID = "36313"
        private const val PREFS_NAME = "anilist_prefs"
        private const val TOKEN_KEY = "auth_token"

        // Cache duration in milliseconds (24 hours)
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L

        // Cache keys
        private const val CACHE_EXPLORE_TIME = "cache_explore_time"
        private const val CACHE_HOME_TIME = "cache_home_time"
        private const val CACHE_EXPLORE_DATA = "cache_explore_data"
        private const val CACHE_HOME_DATA = "cache_home_data"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _isOled = MutableStateFlow(false)
    val isOled: StateFlow<Boolean> = _isOled.asStateFlow()

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

    private val _userId = MutableStateFlow<Int?>(null)
    val userId: StateFlow<Int?> = _userId.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userAvatar = MutableStateFlow<String?>(null)
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    // Loading states
    private val _isLoadingExplore = MutableStateFlow(false)
    val isLoadingExplore: StateFlow<Boolean> = _isLoadingExplore.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    // Explore data
    private val _featuredAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val featuredAnime: StateFlow<List<ExploreAnime>> = _featuredAnime.asStateFlow()

    private val _seasonalAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val seasonalAnime: StateFlow<List<ExploreAnime>> = _seasonalAnime.asStateFlow()

    private val _topSeries = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val topSeries: StateFlow<List<ExploreAnime>> = _topSeries.asStateFlow()

    private val _topMovies = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val topMovies: StateFlow<List<ExploreAnime>> = _topMovies.asStateFlow()

    // Genre recommendations
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

    // Pre-fetched stream cache
    private val _prefetchedStreams = MutableStateFlow<Map<String, AniwatchStreamResult?>>(emptyMap())
    val prefetchedStreams: StateFlow<Map<String, AniwatchStreamResult?>> = _prefetchedStreams.asStateFlow()

    // Pre-fetched episode info (servers list)
    private val _prefetchedEpisodeInfo = MutableStateFlow<Map<String, EpisodeStreams?>>(emptyMap())
    val prefetchedEpisodeInfo: StateFlow<Map<String, EpisodeStreams?>> = _prefetchedEpisodeInfo.asStateFlow()

    private var context: Context? = null
    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context, hasToken: Boolean) {
        this.context = context
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // If we know user has token from MainActivity's synchronous check, set it now
        if (hasToken) {
            val token = sharedPreferences.getString(TOKEN_KEY, null)
            _authToken.value = token
            Log.d(TAG, "Token loaded from SharedPreferences: ${token?.take(20)}...")
        }

        // Load other settings
        _isOled.value = sharedPreferences.getBoolean("oled_mode", false)
        _showStatusColors.value = sharedPreferences.getBoolean("show_status_colors", false)
        _trackingPercentage.value = sharedPreferences.getInt("tracking_percentage", 85)
        _forwardSkipSeconds.value = sharedPreferences.getInt("forward_skip_seconds", 10)
        _backwardSkipSeconds.value = sharedPreferences.getInt("backward_skip_seconds", 10)
        _forceHighRefreshRate.value = sharedPreferences.getBoolean("force_high_refresh_rate", false)

        // Fetch data asynchronously with cache check
        viewModelScope.launch {
            if (hasToken) {
                loadHomeDataWithCache()
            }
            loadExploreDataWithCache()
        }
    }

    private fun isCacheValid(cacheKey: String): Boolean {
        val cacheTime = sharedPreferences.getLong(cacheKey, 0)
        val now = System.currentTimeMillis()
        return (now - cacheTime) < CACHE_DURATION_MS
    }

    private fun setCacheTime(cacheKey: String) {
        sharedPreferences.edit().putLong(cacheKey, System.currentTimeMillis()).apply()
    }

    private suspend fun loadHomeDataWithCache() {
        // Try to load from cache first
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
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached home data", e)
            }
        }

        // Cache expired or doesn't exist, fetch fresh data
        _isLoadingHome.value = true
        fetchUser()
        fetchLists()
        _isLoadingHome.value = false
    }

    private suspend fun loadExploreDataWithCache() {
        // Try to load from cache first
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

        // Cache expired or doesn't exist, fetch fresh data
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

    private val _hideNavbarText = MutableStateFlow(false)
    val hideNavbarText: StateFlow<Boolean> = _hideNavbarText.asStateFlow()

    fun setHideNavbarText(enabled: Boolean) {
        _hideNavbarText.value = enabled
        sharedPreferences.edit().putBoolean("hide_navbar_text", enabled).apply()
        Log.d(TAG, "Hide navbar text set to: $enabled")
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
                // Save to SharedPreferences for synchronous loading on next launch
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
        // Clear from SharedPreferences
        sharedPreferences.edit()
            .remove(TOKEN_KEY)
            .remove(CACHE_HOME_DATA)
            .remove(CACHE_HOME_TIME)
            .remove(CACHE_EXPLORE_DATA)
            .remove(CACHE_EXPLORE_TIME)
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
    }

    private suspend fun graphqlRequest(query: String, variables: Map<String, Any?>): String? = withContext(Dispatchers.IO) {
        val token = _authToken.value ?: return@withContext null
        val url = URL("https://graphql.anilist.co")
        val connection = url.openConnection() as HttpsURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
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
                connection.inputStream.bufferedReader().readText()
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
        val url = URL("https://graphql.anilist.co")
        val connection = url.openConnection() as HttpsURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

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
            val body = "{\"query\":${Json.encodeToString(query)},\"variables\":$variablesJson}"

            connection.outputStream.use { it.write(body.toByteArray()) }

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
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
                                title { romaji english }
                                coverImage { large medium }
                                bannerImage
                                episodes
                                nextAiringEpisode { episode airingAt }
                                status
                                averageScore
                                genres
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
                            listEntryId = entry.id
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

                // Save to cache after successful fetch
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

    fun fetchExploreData() {
        Log.d(TAG, "Fetching explore data...")
        viewModelScope.launch {
            _isLoadingExplore.value = true

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

            _isLoadingExplore.value = false

            // Save to cache after successful fetch
            saveExploreDataToCache()
        }
    }

    private suspend fun fetchFeaturedAnime() {
        val query = """
            query {
                Page(page: 1, perPage: 10) {
                    media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                        id
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
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
                        genres = media.genres ?: emptyList()
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
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
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
                        genres = media.genres ?: emptyList()
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
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
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
                        genres = media.genres ?: emptyList()
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
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
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
                        genres = media.genres ?: emptyList()
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
                        title { romaji english }
                        coverImage { large medium }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
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
                        genres = media.genres ?: emptyList()
                    )
                }

                stateFlow.value = animeList.filter { (it.averageScore ?: 0) >= 60 }
                Log.d(TAG, "$genre anime loaded: ${stateFlow.value.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch/parse $genre anime", e)
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            _isLoadingHome.value = true
            fetchLists()
            _isLoadingHome.value = false
        }
    }

    fun forceRefreshExplore() {
        sharedPreferences.edit().remove(CACHE_EXPLORE_TIME).apply()
        fetchExploreData()
    }

    // Pre-fetch stream for currently watching anime (next episode)
    fun prefetchCurrentEpisodeStream(anime: AnimeMedia) {
        val nextEp = anime.progress + 1
        val key = "${anime.id}_$nextEp"

        if (_prefetchedStreams.value.containsKey(key)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Pre-fetching stream for ${anime.title} ep $nextEp")
                val result = AniwatchService.getStreamLink(anime.title, nextEp)
                _prefetchedStreams.value = _prefetchedStreams.value + (key to result)

                // Also cache episode info for server selection
                val episodeInfo = AniwatchService.getEpisodeInfo(anime.title, nextEp)
                _prefetchedEpisodeInfo.value = _prefetchedEpisodeInfo.value + (key to episodeInfo)

                Log.d(TAG, "Pre-fetch complete for ${anime.title} ep $nextEp")
            } catch (e: Exception) {
                Log.e(TAG, "Pre-fetch failed for ${anime.title}", e)
            }
        }
    }

    // Get cached stream or fetch new one
    suspend fun getStreamLinkWithCache(animeName: String, episodeNumber: Int, animeId: Int): AniwatchStreamResult? {
        val key = "${animeId}_$episodeNumber"

        _prefetchedStreams.value[key]?.let {
            Log.d(TAG, "Using cached stream for $animeName ep $episodeNumber")
            return it
        }

        Log.d(TAG, "Fetching new stream for $animeName ep $episodeNumber")
        val result = AniwatchService.getStreamLink(animeName, episodeNumber)

        _prefetchedStreams.value = _prefetchedStreams.value + (key to result)

        return result
    }

    // Get stream for specific server
    suspend fun getStreamForServer(
        animeName: String,
        episodeNumber: Int,
        serverName: String,
        category: String,
        animeId: Int
    ): AniwatchStreamResult? {
        val key = "${animeId}_${episodeNumber}_${serverName}_$category"

        _prefetchedStreams.value[key]?.let { return it }

        val result = AniwatchService.getStreamFromServer(animeName, episodeNumber, serverName, category)
        _prefetchedStreams.value = _prefetchedStreams.value + (key to result)

        return result
    }

    // Pre-fetch adjacent episodes (previous and next)
    fun prefetchAdjacentEpisodes(animeName: String, currentEpisode: Int, animeId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // Pre-fetch previous
            if (currentEpisode > 1) {
                val prevKey = "${animeId}_${currentEpisode - 1}"
                if (!_prefetchedStreams.value.containsKey(prevKey)) {
                    Log.d(TAG, "Pre-fetching previous episode: ${currentEpisode - 1}")
                    val result = AniwatchService.getStreamLink(animeName, currentEpisode - 1)
                    _prefetchedStreams.value = _prefetchedStreams.value + (prevKey to result)

                    val episodeInfo = AniwatchService.getEpisodeInfo(animeName, currentEpisode - 1)
                    _prefetchedEpisodeInfo.value = _prefetchedEpisodeInfo.value + (prevKey to episodeInfo)
                }
            }

            // Pre-fetch next
            val nextKey = "${animeId}_${currentEpisode + 1}"
            if (!_prefetchedStreams.value.containsKey(nextKey)) {
                Log.d(TAG, "Pre-fetching next episode: ${currentEpisode + 1}")
                val result = AniwatchService.getStreamLink(animeName, currentEpisode + 1)
                _prefetchedStreams.value = _prefetchedStreams.value + (nextKey to result)

                val episodeInfo = AniwatchService.getEpisodeInfo(animeName, currentEpisode + 1)
                _prefetchedEpisodeInfo.value = _prefetchedEpisodeInfo.value + (nextKey to episodeInfo)
            }
        }
    }

    // Get episode info for server selection
    suspend fun getEpisodeInfo(animeName: String, episodeNumber: Int, animeId: Int): EpisodeStreams? {
        val key = "${animeId}_$episodeNumber"
        _prefetchedEpisodeInfo.value[key]?.let { return it }

        val result = AniwatchService.getEpisodeInfo(animeName, episodeNumber)
        _prefetchedEpisodeInfo.value = _prefetchedEpisodeInfo.value + (key to result)
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

    suspend fun searchAnime(query: String): List<ExploreAnime> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for: $query")

            val searchQuery = """
                query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
                    Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                        media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                            id
                            title {
                                romaji
                                english
                            }
                            coverImage {
                                large
                                medium
                            }
                            bannerImage
                            episodes
                            nextAiringEpisode {
                                episode
                                airingAt
                            }
                            status
                            averageScore
                            genres
                        }
                    }
                }
            """.trimIndent()

            val response = publicGraphqlRequest(
                searchQuery,
                mapOf("search" to query, "page" to 1, "perPage" to 20)
            )

            if (response != null) {
                val data = json.decodeFromString<ExploreResponse>(response)
                data.data.Page.media.map { media ->
                    ExploreAnime(
                        id = media.id,
                        title = media.title.romaji ?: media.title.english ?: "Unknown",
                        cover = media.coverImage?.large ?: media.coverImage?.medium ?: "",
                        banner = media.bannerImage,
                        episodes = media.episodes ?: 0,
                        latestEpisode = media.nextAiringEpisode?.episode,
                        averageScore = media.averageScore,
                        genres = media.genres ?: emptyList()
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }

    fun removeAnimeFromList(mediaId: Int) {
        Log.d(TAG, "removeAnimeFromList: mediaId=$mediaId")

        // Check all lists for the entry ID
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

        Log.d(TAG, "Found entryId=$entryId for mediaId=$mediaId")

        viewModelScope.launch {
            val query = """
                mutation (${'$'}id: Int) {
                    DeleteMediaListEntry(id: ${'$'}id) {
                        deleted
                    }
                }
            """.trimIndent()

            val response = graphqlRequest(query, mapOf("id" to entryId))
            if (response != null) {
                Log.d(TAG, "Remove from list SUCCESS")
                fetchLists()
            } else {
                Log.e(TAG, "Remove from list FAILED")
            }
        }
    }
}

// Cache data classes for serialization
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

// Data classes
@Serializable
data class ExploreAnime(
    val id: Int,
    val title: String,
    val cover: String,
    val banner: String?,
    val episodes: Int,
    val latestEpisode: Int?,
    val averageScore: Int?,
    val genres: List<String>
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
    val listEntryId: Int? = null
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
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val bannerImage: String?,
    val episodes: Int?,
    val nextAiringEpisode: NextAiringEpisode?,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?
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
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val bannerImage: String?,
    val episodes: Int?,
    val nextAiringEpisode: NextAiringEpisode?,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?
)

@Serializable
data class MediaTitle(
    val romaji: String?,
    val english: String?
)

@Serializable
data class MediaCoverImage(
    val large: String?,
    val medium: String?
)

@Serializable
data class NextAiringEpisode(
    val episode: Int,
    val airingAt: Long
)
