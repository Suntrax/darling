package com.blissless.anime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import android.util.Log
import com.blissless.anime.api.EpisodeStreams
import com.blissless.anime.ui.screens.DetailedAnimeScreen
import com.blissless.anime.dialogs.ExploreAnimeDialog
import com.blissless.anime.ui.screens.ExploreScreen
import com.blissless.anime.ui.screens.HomeScreen
import com.blissless.anime.ui.screens.PlayerScreen
import com.blissless.anime.ui.screens.ScheduleScreen
import com.blissless.anime.ui.screens.SettingsScreen
import com.blissless.anime.data.models.toDetailedAnimeData
import com.blissless.anime.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.widget.Toast
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.ExploreAnime

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    companion object {
        const val PREFS_NAME = "anilist_prefs"
        const val TOKEN_KEY = "auth_token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasToken = prefs.getString(TOKEN_KEY, null) != null

        Log.d("MainActivity", "Has saved token: $hasToken")

        mainViewModel.init(applicationContext, hasToken)

        handleAuthCallback(intent)

        setContent {
            val isOled by mainViewModel.isOled.collectAsState()
            val disableMaterialColors by mainViewModel.disableMaterialColors.collectAsState()
            val showStatusColors by mainViewModel.showStatusColors.collectAsState()
            val forceHighRefreshRate by mainViewModel.forceHighRefreshRate.collectAsState()

            val token by mainViewModel.authToken.collectAsState(initial = if (hasToken) "loading" else null)
            val isLoggedIn = token != null && token != "loading"

            LaunchedEffect(forceHighRefreshRate) {
                if (forceHighRefreshRate) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val display = display
                        display?.let {
                            val modes = it.supportedModes
                            var highestRefreshRateMode: android.view.Display.Mode? = null
                            var highestRefreshRate = 60f

                            modes?.forEach { mode ->
                                if (mode.refreshRate > highestRefreshRate) {
                                    highestRefreshRate = mode.refreshRate
                                    highestRefreshRateMode = mode
                                }
                            }

                            highestRefreshRateMode?.let { mode ->
                                val params = window.attributes
                                params.preferredDisplayModeId = mode.modeId
                                window.attributes = params
                                Log.d("MainActivity", "Set preferred display mode to ${mode.refreshRate}Hz")
                            }
                        }
                    }
                }
            }

            AppTheme(useOled = isOled, useMonochrome = disableMaterialColors) {
                MainScreen(
                    viewModel = mainViewModel,
                    isOled = isOled,
                    showStatusColors = showStatusColors,
                    forceHighRefreshRate = forceHighRefreshRate,
                    isLoggedIn = isLoggedIn
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            Log.d("MainActivity", "Received intent with URI: $uri")
            if (uri.scheme == "animescraper" && uri.host == "success") {
                Log.d("MainActivity", "Detected OAuth callback")
                mainViewModel.handleAuthRedirect(intent)
            }
        }
    }
}

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    showStatusColors: Boolean,
    forceHighRefreshRate: Boolean,
    isLoggedIn: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 2, pageCount = { 4 })

    var preloadedPages by remember { mutableStateOf(setOf(1)) }

    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()
    val prefetchedStreams by viewModel.prefetchedStreams.collectAsState()
    val prefetchedEpisodeInfo by viewModel.prefetchedEpisodeInfo.collectAsState()

    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)
    val hideNavbarText by viewModel.hideNavbarText.collectAsState(initial = false)

    val simplifyEpisodeMenu by viewModel.simplifyEpisodeMenu.collectAsState(initial = true)
    val simplifyAnimeDetails by viewModel.simplifyAnimeDetails.collectAsState(initial = true)

    val localFavorites by viewModel.localFavorites.collectAsState()
    val canAddFavorite = remember(localFavorites) { viewModel.canAddFavorite() }

    val autoSkipOpening by viewModel.autoSkipOpening.collectAsState(initial = false)
    val autoSkipEnding by viewModel.autoSkipEnding.collectAsState(initial = false)
    val autoPlayNextEpisode by viewModel.autoPlayNextEpisode.collectAsState(initial = false)

    val disableMaterialColors by viewModel.disableMaterialColors.collectAsState(initial = false)
    val preferredCategory by viewModel.preferredCategory.collectAsState(initial = "sub")

    LaunchedEffect(currentlyWatching) {
        if (currentlyWatching.isNotEmpty()) {
            currentlyWatching.take(3).forEach { anime ->
                viewModel.prefetchCurrentEpisodeStream(anime)
            }
        }
    }

    val isLoadingHome by viewModel.isLoadingHome.collectAsState()
    LaunchedEffect(isLoadingHome) {
        if (!isLoadingHome && 0 !in preloadedPages) {
            preloadedPages = preloadedPages + 0
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.currentPageOffsetFraction) {
        val currentPage = pagerState.currentPage
        val offset = pagerState.currentPageOffsetFraction

        val approachingPage = when {
            offset > 0.3f -> currentPage + 1
            offset < -0.3f -> currentPage - 1
            else -> currentPage
        }

        if (approachingPage in 0..3 && approachingPage !in preloadedPages) {
            preloadedPages = preloadedPages + approachingPage
            when (approachingPage) {
                0 -> { viewModel.fetchAiringSchedule() }
                1 -> { }
                2 -> { viewModel.refreshHome() }
                3 -> { }
            }
        }
    }

    var showPlayer by remember { mutableStateOf(false) }
    var currentVideoUrl by remember { mutableStateOf<String?>(null) }
    var currentReferer by remember { mutableStateOf("https://megacloud.tv/") }
    var currentSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var currentAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    var currentEpisode by remember { mutableIntStateOf(0) }
    var totalEpisodes by remember { mutableIntStateOf(0) }
    var isLoadingStream by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }

    var currentEpisodeInfo by remember { mutableStateOf<EpisodeStreams?>(null) }
    var currentCategory by remember { mutableStateOf("sub") }
    var currentServerName by remember { mutableStateOf("") }
    var currentServerIndex by remember { mutableIntStateOf(0) }

    var isFallbackStream by remember { mutableStateOf(false) }
    var requestedCategory by remember { mutableStateOf("sub") }
    var actualCategory by remember { mutableStateOf("sub") }
    var isManualServerChange by remember { mutableStateOf(false) }

    var savedPlaybackPosition by remember { mutableLongStateOf(0L) }

    var selectedExploreAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showExploreDialog by remember { mutableStateOf(false) }

    val animeStatusMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        val map = mutableMapOf<Int, String>()
        currentlyWatching.forEach { map[it.id] = "CURRENT" }
        planningToWatch.forEach { map[it.id] = "PLANNING" }
        completed.forEach { map[it.id] = "COMPLETED" }
        onHold.forEach { map[it.id] = "PAUSED" }
        dropped.forEach { map[it.id] = "DROPPED" }
        map
    }

    val onShowAnimeDialog: (ExploreAnime) -> Unit = { anime ->
        selectedExploreAnime = anime
        showExploreDialog = true
    }

    fun loadAndPlayEpisode(anime: AnimeMedia, episode: Int) {
        currentAnime = anime
        currentEpisode = episode
        totalEpisodes = anime.totalEpisodes
        streamError = null
        showPlayer = false

        savedPlaybackPosition = viewModel.getPlaybackPosition(anime.id, episode)

        val cacheKey = "${anime.id}_$episode"
        prefetchedStreams[cacheKey]?.let { cached ->
            if (cached != null) {
                Log.d("MainActivity", "Using cached stream for ${anime.title} ep $episode")
                currentVideoUrl = cached.url
                currentReferer = cached.headers?.get("Referer") ?: "https://megacloud.tv/"
                currentSubtitleUrl = cached.subtitleUrl
                currentServerName = cached.serverName
                currentCategory = cached.category
                actualCategory = cached.category
                requestedCategory = preferredCategory
                isFallbackStream = cached.category != preferredCategory
                showPlayer = true

                prefetchedEpisodeInfo[cacheKey]?.let { currentEpisodeInfo = it }
                currentServerIndex = 0
                val latestAiredForPrefetch = anime.latestEpisode ?: anime.totalEpisodes
                viewModel.prefetchAdjacentEpisodes(anime.title, episode, anime.id, latestAiredForPrefetch)

                if (isFallbackStream) {
                    val message = if (requestedCategory == "dub") {
                        "Dub not available, playing sub"
                    } else {
                        "Sub not available, playing dub"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                return
            }
        }

        isLoadingStream = true
        scope.launch {
            Log.d("MainActivity", "Fetching stream for ${anime.title} ep $episode")

            // Get latest aired episode to prevent fetching unaired episodes
            val latestAired = anime.latestEpisode ?: anime.totalEpisodes

            val epInfo = viewModel.getEpisodeInfo(anime.title, episode, anime.id, latestAired)
            currentEpisodeInfo = epInfo

            val result = viewModel.tryAllServersWithFallback(anime.title, episode, anime.id, latestAired)

            if (result.stream != null) {
                Log.d("MainActivity", "Stream URL found: ${result.stream.url.take(50)}...")
                currentVideoUrl = result.stream.url
                currentReferer = result.stream.headers?.get("Referer") ?: "https://megacloud.tv/"
                currentSubtitleUrl = result.stream.subtitleUrl
                currentServerName = result.stream.serverName
                currentCategory = result.actualCategory
                currentServerIndex = 0

                isFallbackStream = result.isFallback
                requestedCategory = result.requestedCategory
                actualCategory = result.actualCategory

                showPlayer = true
                viewModel.prefetchAdjacentEpisodes(anime.title, episode, anime.id, latestAired)

                if (result.isFallback) {
                    val message = if (result.requestedCategory == "dub") {
                        "Dub not available, playing sub"
                    } else {
                        "Sub not available, playing dub"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("MainActivity", "Failed to get stream for ${anime.title} ep $episode")
                streamError = "Could not find stream for ${anime.title} episode $episode"
            }

            isLoadingStream = false
        }
    }

    val onPlayEpisode: (AnimeMedia, Int) -> Unit = { anime, episode ->
        Log.d("MainActivity", "onPlayEpisode: ${anime.title} ep $episode")
        loadAndPlayEpisode(anime, episode)
    }

    val onPreviousEpisode: () -> Unit = {
        currentAnime?.let { anime ->
            if (currentEpisode > 1) {
                val prevEp = currentEpisode - 1
                isLoadingStream = true
                scope.launch {
                    val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                    val epInfo = viewModel.getEpisodeInfo(anime.title, prevEp, anime.id, latestAired)
                    currentEpisodeInfo = epInfo

                    val result = viewModel.tryAllServersWithFallback(anime.title, prevEp, anime.id, latestAired)
                    if (result.stream != null) {
                        savedPlaybackPosition = viewModel.getPlaybackPosition(anime.id, prevEp)
                        currentVideoUrl = result.stream.url
                        currentReferer = result.stream.headers?.get("Referer") ?: "https://megacloud.tv/"
                        currentSubtitleUrl = result.stream.subtitleUrl
                        currentEpisode = prevEp
                        currentServerName = result.stream.serverName
                        currentCategory = result.actualCategory
                        currentServerIndex = 0

                        isFallbackStream = result.isFallback
                        requestedCategory = result.requestedCategory
                        actualCategory = result.actualCategory

                        viewModel.prefetchAdjacentEpisodes(anime.title, prevEp, anime.id, latestAired)

                        if (result.isFallback) {
                            val message = if (result.requestedCategory == "dub") {
                                "Dub not available, playing sub"
                            } else {
                                "Sub not available, playing dub"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        streamError = "Could not find stream for episode $prevEp"
                    }
                    isLoadingStream = false
                }
            }
        }
    }

    val onNextEpisode: () -> Unit = {
        currentAnime?.let { anime ->
            if (totalEpisodes == 0 || currentEpisode < totalEpisodes) {
                val nextEp = currentEpisode + 1
                isLoadingStream = true
                scope.launch {
                    val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                    val epInfo = viewModel.getEpisodeInfo(anime.title, nextEp, anime.id, latestAired)
                    currentEpisodeInfo = epInfo

                    val result = viewModel.tryAllServersWithFallback(anime.title, nextEp, anime.id, latestAired)
                    if (result.stream != null) {
                        savedPlaybackPosition = viewModel.getPlaybackPosition(anime.id, nextEp)
                        currentVideoUrl = result.stream.url
                        currentReferer = result.stream.headers?.get("Referer") ?: "https://megacloud.tv/"
                        currentSubtitleUrl = result.stream.subtitleUrl
                        currentEpisode = nextEp
                        currentServerName = result.stream.serverName
                        currentCategory = result.actualCategory
                        currentServerIndex = 0

                        isFallbackStream = result.isFallback
                        requestedCategory = result.requestedCategory
                        actualCategory = result.actualCategory

                        viewModel.prefetchAdjacentEpisodes(anime.title, nextEp, anime.id, latestAired)

                        if (result.isFallback) {
                            val message = if (result.requestedCategory == "dub") {
                                "Dub not available, playing sub"
                            } else {
                                "Sub not available, playing dub"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        streamError = "Could not find stream for episode $nextEp"
                    }
                    isLoadingStream = false
                }
            }
        }
    }

    fun changeServer(serverName: String, category: String) {
        currentAnime?.let { anime ->
            isLoadingStream = true
            isManualServerChange = true
            scope.launch {
                val result = viewModel.getStreamForServer(
                    anime.title,
                    currentEpisode,
                    serverName,
                    category,
                    anime.id
                )
                if (result != null) {
                    currentVideoUrl = result.url
                    currentReferer = result.headers?.get("Referer") ?: "https://megacloud.tv/"
                    currentSubtitleUrl = result.subtitleUrl
                    currentServerName = result.serverName
                    currentCategory = result.category

                    val servers = if (category == "sub") currentEpisodeInfo?.subServers else currentEpisodeInfo?.dubServers
                    currentServerIndex = servers?.indexOfFirst { it.name == serverName } ?: 0

                    actualCategory = result.category
                    isFallbackStream = result.category != preferredCategory
                }
                isLoadingStream = false
                isManualServerChange = false
            }
        }
    }

    fun onPlaybackError() {
        currentAnime?.let { anime ->
            val servers = if (currentCategory == "sub") currentEpisodeInfo?.subServers else currentEpisodeInfo?.dubServers

            if (servers != null && servers.size > 1) {
                val nextIndex = (currentServerIndex + 1) % servers.size
                val nextServer = servers[nextIndex]

                Log.d("MainActivity", "Playback error, trying next server: ${nextServer.name}")
                changeServer(nextServer.name, currentCategory)
            }
        }
    }


    // Show Anime Dialog
    if (showExploreDialog && selectedExploreAnime != null) {
        if (simplifyAnimeDetails) {
            val isAnimeFavorite = localFavorites.containsKey(selectedExploreAnime!!.id)
            ExploreAnimeDialog(
                anime = selectedExploreAnime!!,
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = animeStatusMap[selectedExploreAnime!!.id],
                isFavorite = isAnimeFavorite,
                canAddFavorite = canAddFavorite || isAnimeFavorite,
                onToggleFavorite = {
                    viewModel.toggleLocalFavorite(selectedExploreAnime!!)
                },
                onDismiss = {
                    showExploreDialog = false
                    selectedExploreAnime = null
                },
                onAddToPlanning = {
                    viewModel.addExploreAnimeToList(selectedExploreAnime!!, "PLANNING")
                },
                onAddToDropped = {
                    viewModel.addExploreAnimeToList(selectedExploreAnime!!, "DROPPED")
                },
                onAddToOnHold = {
                    viewModel.addExploreAnimeToList(selectedExploreAnime!!, "PAUSED")
                },
                onRemoveFromList = {
                    viewModel.removeAnimeFromList(selectedExploreAnime!!.id)
                },
                onStartWatching = { episode ->
                    val animeMedia = AnimeMedia(
                        id = selectedExploreAnime!!.id,
                        title = selectedExploreAnime!!.title,
                        cover = selectedExploreAnime!!.cover,
                        banner = selectedExploreAnime!!.banner,
                        progress = 0,
                        totalEpisodes = selectedExploreAnime!!.episodes,
                        latestEpisode = selectedExploreAnime!!.latestEpisode,
                        status = "",
                        averageScore = selectedExploreAnime!!.averageScore,
                        genres = selectedExploreAnime!!.genres,
                        listStatus = "",
                        listEntryId = 0,
                        year = selectedExploreAnime!!.year,
                        malId = selectedExploreAnime!!.malId
                    )
                    viewModel.addExploreAnimeToList(selectedExploreAnime!!, "CURRENT")
                    onPlayEpisode(animeMedia, episode)
                    showExploreDialog = false
                },
                isLoggedIn = isLoggedIn
            )
        } else {
            val isAnimeFavorite = localFavorites.containsKey(selectedExploreAnime!!.id)
            DetailedAnimeScreen(
                anime = selectedExploreAnime!!.toDetailedAnimeData(),
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = animeStatusMap[selectedExploreAnime!!.id],
                isFavorite = isAnimeFavorite,
                canAddFavorite = canAddFavorite || isAnimeFavorite,
                onDismiss = {
                    showExploreDialog = false
                    selectedExploreAnime = null
                },
                onPlayEpisode = { episode ->
                    val animeMedia = AnimeMedia(
                        id = selectedExploreAnime!!.id,
                        title = selectedExploreAnime!!.title,
                        cover = selectedExploreAnime!!.cover,
                        banner = selectedExploreAnime!!.banner,
                        progress = 0,
                        totalEpisodes = selectedExploreAnime!!.episodes,
                        latestEpisode = selectedExploreAnime!!.latestEpisode,
                        status = "",
                        averageScore = selectedExploreAnime!!.averageScore,
                        genres = selectedExploreAnime!!.genres,
                        listStatus = "",
                        listEntryId = 0,
                        year = selectedExploreAnime!!.year,
                        malId = selectedExploreAnime!!.malId
                    )
                    viewModel.addExploreAnimeToList(selectedExploreAnime!!, "CURRENT")
                    onPlayEpisode(animeMedia, episode)
                    showExploreDialog = false
                },
                onUpdateStatus = { status ->
                    if (status != null) {
                        viewModel.addExploreAnimeToList(selectedExploreAnime!!, status)
                    }
                },
                onRemove = {
                    viewModel.removeAnimeFromList(selectedExploreAnime!!.id)
                },
                onToggleFavorite = { _ ->
                    viewModel.toggleLocalFavorite(selectedExploreAnime!!)
                },
                isLoggedIn = isLoggedIn
            )
        }
    }

    // Show player
    if (showPlayer && currentVideoUrl != null) {
        currentAnime?.let { anime ->
            PlayerScreen(
                videoUrl = currentVideoUrl!!,
                referer = currentReferer,
                subtitleUrl = currentSubtitleUrl,
                currentEpisode = currentEpisode,
                totalEpisodes = totalEpisodes,
                animeName = anime.title,
                animeId = anime.id,
                malId = anime.malId ?: 0,
                animeYear = anime.year,
                isLoadingStream = isLoadingStream,
                episodeInfo = currentEpisodeInfo,
                currentServerName = currentServerName,
                currentCategory = currentCategory,
                isFallbackStream = isFallbackStream && !isManualServerChange,
                requestedCategory = requestedCategory,
                actualCategory = actualCategory,
                forwardSkipSeconds = forwardSkipSeconds,
                backwardSkipSeconds = backwardSkipSeconds,
                savedPosition = savedPlaybackPosition,
                onSavePosition = { position ->
                    viewModel.savePlaybackPosition(anime.id, currentEpisode, position)
                },
                onProgressUpdate = { percentage ->
                    val trackingPercent = viewModel.trackingPercentage.value
                    if (percentage >= trackingPercent && anime.id > 0) {
                        viewModel.updateAnimeProgress(anime.id, currentEpisode)
                    }
                },
                onPreviousEpisode = if (currentEpisode > 1) onPreviousEpisode else null,
                onNextEpisode = if (totalEpisodes == 0 || currentEpisode < totalEpisodes) onNextEpisode else null,
                onServerChange = { server, category -> changeServer(server, category) },
                onPlaybackError = { onPlaybackError() },
                autoSkipOpening = autoSkipOpening,
                autoSkipEnding = autoSkipEnding,
                autoPlayNextEpisode = autoPlayNextEpisode
            )
        }

        androidx.activity.compose.BackHandler {
            showPlayer = false
            currentVideoUrl = null
        }
    } else {
        Scaffold(
            containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface,
                    contentColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = if (hideNavbarText) Modifier.height(64.dp) else Modifier
                ) {
                    val items = listOf("Schedule", "Explore", "Home", "Settings")
                    val icons = listOf(Icons.Default.CalendarMonth, Icons.Default.Explore, Icons.Default.Home, Icons.Default.Settings)

                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    icons[index],
                                    contentDescription = item,
                                    tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = if (hideNavbarText) Modifier.size(26.dp) else Modifier
                                )
                            },
                            label = if (hideNavbarText) null else {
                                { Text(item, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) }
                            },
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (isOled) Color.White else MaterialTheme.colorScheme.primary,
                                unselectedIconColor = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = if (isOled) Color.White else MaterialTheme.colorScheme.primary,
                                unselectedTextColor = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = if (isOled) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 2
                ) { page ->
                    val isCurrentPage = pagerState.currentPage == page
                    val isScheduleVisible = pagerState.currentPage == 0

                    when (page) {
                        0 -> ScheduleScreen(
                            viewModel = viewModel,
                            isOled = isOled,
                            isVisible = isScheduleVisible,
                            onPlayEpisode = onPlayEpisode,
                            onShowAnimeDialog = onShowAnimeDialog
                        )
                        1 -> ExploreScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            showStatusColors = showStatusColors,
                            simplifyAnimeDetails = simplifyAnimeDetails,
                            localFavorites = localFavorites,
                            onToggleFavorite = { anime -> viewModel.toggleLocalFavorite(anime) },
                            onPlayEpisode = onPlayEpisode,
                            currentlyWatching = currentlyWatching,
                            planningToWatch = planningToWatch,
                            completed = completed,
                            onHold = onHold,
                            dropped = dropped,
                            isVisible = isCurrentPage
                        )
                        2 -> HomeScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            showStatusColors = showStatusColors,
                            simplifyEpisodeMenu = simplifyEpisodeMenu,
                            simplifyAnimeDetails = simplifyAnimeDetails,
                            localFavorites = localFavorites,
                            canAddFavorite = canAddFavorite,
                            onToggleFavorite = { anime -> viewModel.toggleLocalFavorite(anime) },
                            onPlayEpisode = onPlayEpisode,
                            onLoginClick = { viewModel.loginWithAniList() },
                            onShowAnimeDialog = onShowAnimeDialog,
                            currentScreenIndex = pagerState.currentPage
                        )
                        3 -> SettingsScreen(
                            viewModel = viewModel,
                            isOled = isOled,
                            isLoggedIn = isLoggedIn,
                            showStatusColors = showStatusColors,
                            forceHighRefreshRate = forceHighRefreshRate,
                            hideNavbarText = hideNavbarText,
                            autoSkipOpening = autoSkipOpening,
                            autoSkipEnding = autoSkipEnding,
                            autoPlayNextEpisode = autoPlayNextEpisode,
                            disableMaterialColors = disableMaterialColors,
                            preferredCategory = preferredCategory
                        )
                    }
                }

                if (isLoadingStream) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading stream...", color = Color.White)
                        }
                    }
                }

                streamError?.let { error ->
                    LaunchedEffect(error) {
                        delay(3000)
                        streamError = null
                    }

                    AlertDialog(
                        onDismissRequest = { streamError = null },
                        title = { Text("Stream Error") },
                        text = { Text(error) },
                        confirmButton = { TextButton(onClick = { streamError = null }) { Text("OK") } }
                    )
                }
            }
        }
    }
}
