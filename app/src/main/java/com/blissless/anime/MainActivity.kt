package com.blissless.anime

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
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
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.QualityOption
import com.blissless.anime.data.models.AniwatchStreamResult
import com.blissless.anime.data.models.CachedStream
import com.blissless.anime.data.models.AnimeRelation
import com.blissless.anime.ui.screens.DetailedAnimeScreen
import com.blissless.anime.dialogs.ExploreAnimeDialog
import com.blissless.anime.ui.screens.ExploreScreen
import com.blissless.anime.ui.screens.HomeScreen
import com.blissless.anime.ui.screens.PlayerScreen
import com.blissless.anime.ui.screens.ScheduleScreen
import com.blissless.anime.ui.screens.SettingsScreen
import com.blissless.anime.data.models.toDetailedAnimeData
import com.blissless.anime.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.OverlayState
import android.widget.Toast

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
        val savedToken = prefs.getString(TOKEN_KEY, null)

        mainViewModel.init(applicationContext, hasToken)

        handleAuthCallback(intent)

        setContent {
            val activity = this@MainActivity
            val isOled by mainViewModel.isOled.collectAsState()
            val disableMaterialColors by mainViewModel.disableMaterialColors.collectAsState()
            val showStatusColors by mainViewModel.showStatusColors.collectAsState()

            var isLoggedIn by remember { mutableStateOf(savedToken != null) }
            val token by mainViewModel.authToken.collectAsState()
            LaunchedEffect(token) {
                isLoggedIn = token != null
            }

            // Always enable high refresh rate on supported devices
            LaunchedEffect(Unit) {
                enableHighRefreshRate()
            }

            AppTheme(useOled = isOled, useMonochrome = disableMaterialColors) {
                MainScreen(
                    viewModel = mainViewModel,
                    isOled = isOled,
                    showStatusColors = showStatusColors,
                    isLoggedIn = isLoggedIn
                )
            }
        }
    }

    /**
     * Enable the highest supported refresh rate and request high frame rate for animations.
     */
    private fun enableHighRefreshRate() {
        try {
            // Step 1: Set the preferred display mode to the highest refresh rate available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.let { disp ->
                    val modes = disp.supportedModes
                    var bestMode: android.view.Display.Mode? = null
                    var highestRefreshRate = 60f

                    modes?.forEach { mode ->
                        if (mode.refreshRate > highestRefreshRate) {
                            highestRefreshRate = mode.refreshRate
                            bestMode = mode
                        }
                    }

                    bestMode?.let { mode ->
                        val params = window.attributes
                        params.preferredDisplayModeId = mode.modeId
                        window.attributes = params
                    }
                }
            }

            // Step 2: Request high frame rate for the window
            // This tells the system we want animations to run at the display's native refresh rate
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    // Request the surface to render at high frame rate
                    preferredRefreshRate = 120f // Request up to 120Hz
                }
                
                // Try to set frame rate directly on the decor view's surface (API 30+)
                try {
                    val decorView = window.decorView
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        decorView.viewTreeObserver.addOnPreDrawListener {
                            // Keep requesting high frame rate
                            decorView.postInvalidateOnAnimation()
                            true
                        }
                    }
                } catch (e: Exception) {
                    // Ignore - this is optional enhancement
                }
            }

        } catch (e: Exception) {
            // Gracefully handle any errors - fallback to system default
            e.printStackTrace()
        }
    }

    /**
     * Reset to default refresh rate behavior.
     */
    private fun disableHighRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Reset to default display mode (0)
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = 0
                    preferredRefreshRate = 0f // Let system decide
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "animescraper" && uri.host == "success") {
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
    isLoggedIn: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val startupScreen by viewModel.startupScreen.collectAsState()
    val pagerState = rememberPagerState(initialPage = startupScreen, pageCount = { 4 })

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
    val hideAdultContent by viewModel.hideAdultContent.collectAsState(initial = false)

    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val aniListFavoriteIds = remember(aniListFavorites) { aniListFavorites.map { it.id }.toSet() }
    val localFavorites by viewModel.localFavorites.collectAsState()
    val localFavoriteIds = remember(localFavorites) { localFavorites.keys }
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
    val isFavoriteRateLimited by viewModel.isFavoriteRateLimited.collectAsState()
    val playbackPositions by viewModel.playbackPositions.collectAsState()

    LaunchedEffect(isFavoriteRateLimited) {
        if (isFavoriteRateLimited) {
            Toast.makeText(context, "Please wait before toggling again", Toast.LENGTH_SHORT).show()
        }
    }

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

    var isLoggedInKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(isLoggedIn) {
        isLoggedInKey++
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

    // Quality state
    var currentQualityOptions by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var currentQuality by remember { mutableStateOf("Auto") }

    var savedPlaybackPosition by remember { mutableLongStateOf(0L) }

    var overlayState by remember { mutableStateOf<OverlayState>(OverlayState.None) }
    
    var scheduleDialogOpen by remember { mutableStateOf(false) }
    
    // Animekai timestamps (PRIMARY source)
    var animekaiIntroStart by remember { mutableStateOf<Int?>(null) }
    var animekaiIntroEnd by remember { mutableStateOf<Int?>(null) }
    var animekaiOutroStart by remember { mutableStateOf<Int?>(null) }
    var animekaiOutroEnd by remember { mutableStateOf<Int?>(null) }

    val animeStatusMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        val map = mutableMapOf<Int, String>()
        currentlyWatching.forEach { map[it.id] = "CURRENT" }
        planningToWatch.forEach { map[it.id] = "PLANNING" }
        completed.forEach { map[it.id] = "COMPLETED" }
        onHold.forEach { map[it.id] = "PAUSED" }
        dropped.forEach { map[it.id] = "DROPPED" }
        map
    }

    val onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit = { anime, previousAnime ->
        val currentDialog = overlayState as? OverlayState.ExploreAnimeDialog
        val firstAnime = currentDialog?.firstAnime ?: previousAnime ?: anime
        val isFirstOpen = currentDialog == null
        overlayState = OverlayState.ExploreAnimeDialog(anime = anime, firstAnime = firstAnime, isFirstOpen = isFirstOpen)
    }
    
    // Wrapper for callbacks that expect single parameter
    val onShowAnimeDialogSingle: (ExploreAnime) -> Unit = { anime -> 
        onShowAnimeDialog(anime, null) 
    }

    // Callback to clear the first anime (when closing from ExploreScreen inline dialog)
    val onClearAnimeStack: () -> Unit = {
        val current = overlayState as? OverlayState.ExploreAnimeDialog
        if (current != null) {
            if (!current.isFirstOpen && current.firstAnime != null && current.anime.id != current.firstAnime.id) {
                overlayState = OverlayState.ExploreAnimeDialog(anime = current.firstAnime, firstAnime = current.firstAnime, isFirstOpen = false)
            } else {
                overlayState = OverlayState.None
            }
        }
    }

    // Helper to get English title for scraping (Animekai works better with English titles)
    fun getScrapingName(anime: AnimeMedia): String {
        return anime.titleEnglish ?: anime.title
    }

    fun loadAndPlayEpisode(anime: AnimeMedia, episode: Int) {
        currentAnime = anime
        currentEpisode = episode
        totalEpisodes = anime.totalEpisodes
        streamError = null
        showPlayer = false

        // Reset timestamps for new episode
        animekaiIntroStart = null
        animekaiIntroEnd = null
        animekaiOutroStart = null
        animekaiOutroEnd = null

        savedPlaybackPosition = viewModel.getPlaybackPosition(anime.id, episode)

        val cachedStream = viewModel.getCachedStreamImmediate(anime.id, episode, preferredCategory)

        if (cachedStream != null) {
            currentVideoUrl = cachedStream.url
            currentReferer = cachedStream.headers?.get("Referer") ?: "https://megacloud.tv/"
            currentSubtitleUrl = cachedStream.subtitleUrl
            currentServerName = cachedStream.serverName
            currentCategory = cachedStream.category
            actualCategory = cachedStream.category
            requestedCategory = preferredCategory
            isFallbackStream = cachedStream.category != preferredCategory
            isLoadingStream = false

            currentQualityOptions = cachedStream.qualities.map {
                QualityOption(quality = it.quality, url = it.url, width = it.width)
            }
            currentQuality = "Auto"

            // Extract Animekai timestamps from CachedStream (PRIMARY)
            animekaiIntroStart = cachedStream.introStart
            animekaiIntroEnd = cachedStream.introEnd
            animekaiOutroStart = cachedStream.outroStart
            animekaiOutroEnd = cachedStream.outroEnd

            val epInfoKey = "${anime.id}_$episode"
            prefetchedEpisodeInfo[epInfoKey]?.let { currentEpisodeInfo = it }
            currentServerIndex = 0

            showPlayer = true

            val latestAiredForPrefetch = anime.latestEpisode ?: anime.totalEpisodes
            viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), episode, anime.id, latestAiredForPrefetch)

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

        isLoadingStream = true
        scope.launch {
            val latestAired = anime.latestEpisode ?: anime.totalEpisodes

            val epInfo = viewModel.getEpisodeInfo(getScrapingName(anime), episode, anime.id, latestAired)
            currentEpisodeInfo = epInfo

            val result = viewModel.tryAllServersWithFallback(getScrapingName(anime), episode, anime.id, latestAired)

            if (result.stream != null) {
                currentVideoUrl = result.stream.url
                currentReferer = result.stream.headers?.get("Referer") ?: "https://megacloud.tv/"
                currentSubtitleUrl = result.stream.subtitleUrl
                currentServerName = result.stream.serverName
                currentCategory = result.actualCategory
                currentServerIndex = 0

                isFallbackStream = result.isFallback
                requestedCategory = result.requestedCategory
                actualCategory = result.actualCategory

                currentQualityOptions = result.stream.qualities
                currentQuality = "Auto"

                // Extract Animekai timestamps from AniwatchStreamResult (PRIMARY)
                animekaiIntroStart = result.stream.introStart
                animekaiIntroEnd = result.stream.introEnd
                animekaiOutroStart = result.stream.outroStart
                animekaiOutroEnd = result.stream.outroEnd

                showPlayer = true
                viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), episode, anime.id, latestAired)

                if (result.isFallback) {
                    val message = if (result.requestedCategory == "dub") {
                        "Dub not available, playing sub"
                    } else {
                        "Sub not available, playing dub"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } else {
                streamError = "Could not find stream for ${anime.title} episode $episode"
            }

            isLoadingStream = false
        }
    }

    val onPlayEpisode: (AnimeMedia, Int) -> Unit = { anime, episode ->
        loadAndPlayEpisode(anime, episode)
    }

    val onPreviousEpisode: () -> Unit = {
        currentAnime?.let { anime ->
            if (currentEpisode > 1) {
                val prevEp = currentEpisode - 1

                // Reset timestamps for previous episode
                animekaiIntroStart = null
                animekaiIntroEnd = null
                animekaiOutroStart = null
                animekaiOutroEnd = null

                val cachedStream = viewModel.getCachedStreamImmediate(anime.id, prevEp, preferredCategory)

                if (cachedStream != null) {
                    savedPlaybackPosition = viewModel.getPlaybackPosition(anime.id, prevEp)
                    currentVideoUrl = cachedStream.url
                    currentReferer = cachedStream.headers?.get("Referer") ?: "https://megacloud.tv/"
                    currentSubtitleUrl = cachedStream.subtitleUrl
                    currentEpisode = prevEp
                    currentServerName = cachedStream.serverName
                    currentCategory = cachedStream.category
                    currentServerIndex = 0

                    isFallbackStream = cachedStream.category != preferredCategory
                    requestedCategory = preferredCategory
                    actualCategory = cachedStream.category

                    currentQualityOptions = cachedStream.qualities.map {
                        QualityOption(quality = it.quality, url = it.url, width = it.width)
                    }
                    currentQuality = "Auto"

                    // Extract Animekai timestamps from CachedStream (PRIMARY)
                    animekaiIntroStart = cachedStream.introStart
                    animekaiIntroEnd = cachedStream.introEnd
                    animekaiOutroStart = cachedStream.outroStart
                    animekaiOutroEnd = cachedStream.outroEnd

                    scope.launch {
                        val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                        val epInfo = viewModel.getEpisodeInfo(getScrapingName(anime), prevEp, anime.id, latestAired)
                        currentEpisodeInfo = epInfo
                        viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), prevEp, anime.id, latestAired)
                    }

                    if (isFallbackStream) {
                        val message = if (requestedCategory == "dub") {
                            "Dub not available, playing sub"
                        } else {
                            "Sub not available, playing dub"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    return@let
                }

                isLoadingStream = true
                scope.launch {
                    val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                    val epInfo = viewModel.getEpisodeInfo(getScrapingName(anime), prevEp, anime.id, latestAired)
                    currentEpisodeInfo = epInfo

                    val result = viewModel.tryAllServersWithFallback(getScrapingName(anime), prevEp, anime.id, latestAired)
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

                        currentQualityOptions = result.stream.qualities
                        currentQuality = "Auto"

                        // Extract Animekai timestamps from AniwatchStreamResult (PRIMARY)
                        animekaiIntroStart = result.stream.introStart
                        animekaiIntroEnd = result.stream.introEnd
                        animekaiOutroStart = result.stream.outroStart
                        animekaiOutroEnd = result.stream.outroEnd

                        viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), prevEp, anime.id, latestAired)

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

                // Reset timestamps for next episode
                animekaiIntroStart = null
                animekaiIntroEnd = null
                animekaiOutroStart = null
                animekaiOutroEnd = null

                val cachedStream = viewModel.getCachedStreamImmediate(anime.id, nextEp, preferredCategory)

                if (cachedStream != null) {
                    savedPlaybackPosition = viewModel.getPlaybackPosition(anime.id, nextEp)
                    currentVideoUrl = cachedStream.url
                    currentReferer = cachedStream.headers?.get("Referer") ?: "https://megacloud.tv/"
                    currentSubtitleUrl = cachedStream.subtitleUrl
                    currentEpisode = nextEp
                    currentServerName = cachedStream.serverName
                    currentCategory = cachedStream.category
                    currentServerIndex = 0

                    isFallbackStream = cachedStream.category != preferredCategory
                    requestedCategory = preferredCategory
                    actualCategory = cachedStream.category

                    currentQualityOptions = cachedStream.qualities.map {
                        QualityOption(quality = it.quality, url = it.url, width = it.width)
                    }
                    currentQuality = "Auto"

                    // Extract Animekai timestamps from CachedStream (PRIMARY)
                    animekaiIntroStart = cachedStream.introStart
                    animekaiIntroEnd = cachedStream.introEnd
                    animekaiOutroStart = cachedStream.outroStart
                    animekaiOutroEnd = cachedStream.outroEnd

                    scope.launch {
                        val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                        val epInfo = viewModel.getEpisodeInfo(getScrapingName(anime), nextEp, anime.id, latestAired)
                        currentEpisodeInfo = epInfo
                        viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), nextEp, anime.id, latestAired)
                    }

                    if (isFallbackStream) {
                        val message = if (requestedCategory == "dub") {
                            "Dub not available, playing sub"
                        } else {
                            "Sub not available, playing dub"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    return@let
                }

                isLoadingStream = true
                scope.launch {
                    val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                    val epInfo = viewModel.getEpisodeInfo(getScrapingName(anime), nextEp, anime.id, latestAired)
                    currentEpisodeInfo = epInfo

                    val result = viewModel.tryAllServersWithFallback(getScrapingName(anime), nextEp, anime.id, latestAired)
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

                        currentQualityOptions = result.stream.qualities
                        currentQuality = "Auto"

                        // Extract Animekai timestamps from AniwatchStreamResult (PRIMARY)
                        animekaiIntroStart = result.stream.introStart
                        animekaiIntroEnd = result.stream.introEnd
                        animekaiOutroStart = result.stream.outroStart
                        animekaiOutroEnd = result.stream.outroEnd

                        viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), nextEp, anime.id, latestAired)

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
            // Reset timestamps when changing server
            animekaiIntroStart = null
            animekaiIntroEnd = null
            animekaiOutroStart = null
            animekaiOutroEnd = null

            // Check if we have episode info cached with server list
            val epInfoKey = "${anime.id}_$currentEpisode"
            val cachedEpInfo = prefetchedEpisodeInfo[epInfoKey] ?: currentEpisodeInfo

            if (cachedEpInfo == null) {
                Toast.makeText(context, "No server info available", Toast.LENGTH_SHORT).show()
                return@let
            }

            // Find the server in the list to verify it exists
            val servers = if (category == "sub") cachedEpInfo.subServers else cachedEpInfo.dubServers
            val serverExists = servers.any { it.name == serverName }

            if (!serverExists) {
                // Try the other category
                val fallbackServers = if (category == "sub") cachedEpInfo.dubServers else cachedEpInfo.subServers
                val fallbackExists = fallbackServers.any { it.name == serverName }
                if (!fallbackExists) {
                    Toast.makeText(context, "Server not found: $serverName", Toast.LENGTH_SHORT).show()
                    return@let
                }
            }

            val cachedStream = viewModel.getCachedStreamImmediate(anime.id, currentEpisode, category)

            if (cachedStream != null && cachedStream.serverName == serverName) {
                currentVideoUrl = cachedStream.url
                currentReferer = cachedStream.headers?.get("Referer") ?: "https://megacloud.tv/"
                currentSubtitleUrl = cachedStream.subtitleUrl
                currentServerName = cachedStream.serverName
                currentCategory = cachedStream.category
                requestedCategory = category
                actualCategory = cachedStream.category
                isFallbackStream = cachedStream.category != category

                currentQualityOptions = cachedStream.qualities.map {
                    QualityOption(quality = it.quality, url = it.url, width = it.width)
                }
                currentQuality = "Auto"

                // Extract Animekai timestamps from CachedStream (PRIMARY)
                animekaiIntroStart = cachedStream.introStart
                animekaiIntroEnd = cachedStream.introEnd
                animekaiOutroStart = cachedStream.outroStart
                animekaiOutroEnd = cachedStream.outroEnd

                currentServerIndex = servers?.indexOfFirst { it.name == serverName } ?: 0
                return@let
            }

            isLoadingStream = true
            isManualServerChange = true
            scope.launch {
                val result = viewModel.getStreamForServer(
                    getScrapingName(anime),
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

                    currentQualityOptions = result.qualities
                    currentQuality = "Auto"

                    // Extract Animekai timestamps from AniwatchStreamResult (PRIMARY)
                    animekaiIntroStart = result.introStart
                    animekaiIntroEnd = result.introEnd
                    animekaiOutroStart = result.outroStart
                    animekaiOutroEnd = result.outroEnd

                    val servers = if (category == "sub") currentEpisodeInfo?.subServers else currentEpisodeInfo?.dubServers
                    currentServerIndex = servers?.indexOfFirst { it.name == serverName } ?: 0

                    requestedCategory = category
                    actualCategory = result.category
                    isFallbackStream = result.category != category
                } else {
                    Toast.makeText(context, "Failed to load $serverName", Toast.LENGTH_SHORT).show()
                }
                isLoadingStream = false
                isManualServerChange = false
            }
        }
    }

    fun changeQuality(qualityUrl: String, qualityName: String) {
        currentVideoUrl = qualityUrl
        currentQuality = qualityName
    }

    fun invalidateCurrentStreamCache() {
        currentAnime?.let { anime ->
            viewModel.invalidateStreamCache(anime.id, currentEpisode, currentCategory)
        }
    }

    fun onPlaybackError() {
        // Invalidate the cache for this stream
        invalidateCurrentStreamCache()

        currentAnime?.let { anime ->
            val servers = if (currentCategory == "sub") currentEpisodeInfo?.subServers else currentEpisodeInfo?.dubServers

            if (servers != null && servers.size > 1) {
                val nextIndex = (currentServerIndex + 1) % servers.size
                val nextServer = servers[nextIndex]

                changeServer(nextServer.name, currentCategory)
            }
        }
    }

    val exploreDialog = overlayState as? OverlayState.ExploreAnimeDialog
    if (exploreDialog != null) {
        if (simplifyAnimeDetails) {
            val isAnimeFavorite = aniListFavoriteIds.contains(exploreDialog.anime.id)
            ExploreAnimeDialog(
                anime = exploreDialog.anime,
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = animeStatusMap[exploreDialog.anime.id],
                isFavorite = isAnimeFavorite,
                onToggleFavorite = {
                    viewModel.toggleAniListFavorite(exploreDialog.anime.id)
                },
                onDismiss = {
                    overlayState = OverlayState.None
                },
                onAddToPlanning = {
                    viewModel.addExploreAnimeToList(exploreDialog.anime, "PLANNING")
                },
                onAddToDropped = {
                    viewModel.addExploreAnimeToList(exploreDialog.anime, "DROPPED")
                },
                onAddToOnHold = {
                    viewModel.addExploreAnimeToList(exploreDialog.anime, "PAUSED")
                },
                onRemoveFromList = {
                    viewModel.removeAnimeFromList(exploreDialog.anime.id)
                },
                onStartWatching = { episode ->
                    val animeMedia = AnimeMedia(
                        id = exploreDialog.anime.id,
                        title = exploreDialog.anime.title,
                        titleEnglish = exploreDialog.anime.titleEnglish,
                        cover = exploreDialog.anime.cover,
                        banner = exploreDialog.anime.banner,
                        progress = 0,
                        totalEpisodes = exploreDialog.anime.episodes,
                        latestEpisode = exploreDialog.anime.latestEpisode,
                        status = "",
                        averageScore = exploreDialog.anime.averageScore,
                        genres = exploreDialog.anime.genres,
                        listStatus = "",
                        listEntryId = 0,
                        year = exploreDialog.anime.year,
                        malId = exploreDialog.anime.malId
                    )
                    viewModel.addExploreAnimeToList(exploreDialog.anime, "CURRENT")
                    onPlayEpisode(animeMedia, episode)
                    overlayState = OverlayState.None
                },
                isLoggedIn = isLoggedIn,
                onLoginClick = { viewModel.loginWithAniList() },
                onRelationClick = { relation ->
                    try {
                        scope.launch {
                            try {
                                delay(100)
                                val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                                if (detailedData != null) {
                                    val newAnime = ExploreAnime(
                                        id = relation.id,
                                        title = detailedData.title,
                                        titleEnglish = detailedData.titleEnglish,
                                        cover = detailedData.cover,
                                        banner = detailedData.banner,
                                        episodes = detailedData.episodes,
                                        latestEpisode = detailedData.latestEpisode,
                                        averageScore = detailedData.averageScore,
                                        genres = detailedData.genres,
                                        year = detailedData.year,
                                        format = detailedData.format
                                    )
                                    val firstAnime = exploreDialog.firstAnime ?: exploreDialog.anime
                                    overlayState = OverlayState.ExploreAnimeDialog(anime = newAnime, firstAnime = firstAnime, isFirstOpen = false)
                                } else {
                                    Toast.makeText(context, "Anime not found - ID: ${relation.id}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            val isAnimeFavorite = aniListFavoriteIds.contains(exploreDialog.anime.id)
            DetailedAnimeScreen(
                anime = exploreDialog.anime.toDetailedAnimeData(),
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = animeStatusMap[exploreDialog.anime.id],
                isFavorite = isAnimeFavorite,
                onDismiss = {
                    // Go back to first anime if we've navigated, otherwise close
                    val firstAnime = exploreDialog.firstAnime
                    if (firstAnime != null && exploreDialog.anime.id != firstAnime.id) {
                        overlayState = OverlayState.ExploreAnimeDialog(
                            anime = firstAnime,
                            firstAnime = firstAnime,
                            isFirstOpen = false
                        )
                    } else {
                        overlayState = OverlayState.None
                    }
                },
                onSwipeToClose = { overlayState = OverlayState.None },
                onPlayEpisode = { episode ->
                    val animeMedia = AnimeMedia(
                        id = exploreDialog.anime.id,
                        title = exploreDialog.anime.title,
                        titleEnglish = exploreDialog.anime.titleEnglish,
                        cover = exploreDialog.anime.cover,
                        banner = exploreDialog.anime.banner,
                        progress = 0,
                        totalEpisodes = exploreDialog.anime.episodes,
                        latestEpisode = exploreDialog.anime.latestEpisode,
                        status = "",
                        averageScore = exploreDialog.anime.averageScore,
                        genres = exploreDialog.anime.genres,
                        listStatus = "",
                        listEntryId = 0,
                        year = exploreDialog.anime.year,
                        malId = exploreDialog.anime.malId
                    )
                    viewModel.addExploreAnimeToList(exploreDialog.anime, "CURRENT")
                    onPlayEpisode(animeMedia, episode)
                    overlayState = OverlayState.None
                },
                onUpdateStatus = { status ->
                    if (status != null) {
                        viewModel.addExploreAnimeToList(exploreDialog.anime, status)
                    }
                },
                onRemove = {
                    viewModel.removeAnimeFromList(exploreDialog.anime.id)
                },
                onToggleFavorite = { _ ->
                    viewModel.toggleAniListFavorite(exploreDialog.anime.id)
                },
                localStatus = localAnimeStatus[exploreDialog.anime.id],
                isLocalFavorite = localFavoriteIds.contains(exploreDialog.anime.id),
                onToggleLocalFavorite = { id ->
                    viewModel.toggleLocalFavorite(id, exploreDialog.anime.title, exploreDialog.anime.cover, exploreDialog.anime.banner, exploreDialog.anime.year, exploreDialog.anime.averageScore)
                },
                onUpdateLocalStatus = { status ->
                    viewModel.setLocalAnimeStatus(exploreDialog.anime.id, status)
                },
                onRemoveLocalStatus = {
                    viewModel.setLocalAnimeStatus(exploreDialog.anime.id, null)
                },
                isLoggedIn = isLoggedIn,
                onLoginClick = { viewModel.loginWithAniList() },
                onRelationClick = { relation ->
                    try {
                        scope.launch {
                            try {
                                delay(100)
                                val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                                if (detailedData != null) {
                                    val newAnime = ExploreAnime(
                                        id = relation.id,
                                        title = detailedData.title,
                                        titleEnglish = detailedData.titleEnglish,
                                        cover = detailedData.cover,
                                        banner = detailedData.banner,
                                        episodes = detailedData.episodes,
                                        latestEpisode = detailedData.latestEpisode,
                                        averageScore = detailedData.averageScore,
                                        genres = detailedData.genres,
                                        year = detailedData.year,
                                        format = detailedData.format
                                    )
                                    // Keep firstAnime unchanged, update current anime
                                    val firstAnime = exploreDialog.firstAnime ?: exploreDialog.anime
                                    overlayState = OverlayState.ExploreAnimeDialog(
                                        anime = newAnime,
                                        firstAnime = firstAnime,
                                        isFirstOpen = false
                                    )
                                } else {
                                    Toast.makeText(context, "Anime not found - ID: ${relation.id}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }


    if (showPlayer && currentVideoUrl != null) {
        currentAnime?.let { anime ->
            val released = anime.latestEpisode?.let { it - 1 } ?: anime.totalEpisodes
            PlayerScreen(
                videoUrl = currentVideoUrl!!,
                referer = currentReferer,
                subtitleUrl = currentSubtitleUrl,
                currentEpisode = currentEpisode,
                totalEpisodes = totalEpisodes,
                latestAiredEpisode = released,
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
                qualityOptions = currentQualityOptions,
                currentQuality = currentQuality,
                // Animekai timestamps (PRIMARY source)
                animekaiIntroStart = animekaiIntroStart,
                animekaiIntroEnd = animekaiIntroEnd,
                animekaiOutroStart = animekaiOutroStart,
                animekaiOutroEnd = animekaiOutroEnd,
                onSavePosition = { position ->
                    viewModel.savePlaybackPosition(anime.id, currentEpisode, position)
                },
                onPositionSaved = { position ->
                    savedPlaybackPosition = position
                },
                onProgressUpdate = { percentage ->
                    val trackingPercent = viewModel.trackingPercentage.value
                    if (percentage >= trackingPercent && anime.id > 0) {
                        viewModel.updateAnimeProgress(anime.id, currentEpisode)
                    }
                },
                onPreviousEpisode = if (currentEpisode > 1) onPreviousEpisode else null,
                onNextEpisode = if (currentEpisode < released) onNextEpisode else null,
                isLatestEpisode = currentEpisode >= released && released > 0,
                onServerChange = { server, category -> changeServer(server, category) },
                onQualityChange = { qualityUrl, qualityName -> changeQuality(qualityUrl, qualityName) },
                onPlaybackError = { onPlaybackError() },
                onInvalidateStreamCache = { invalidateCurrentStreamCache() },
                autoSkipOpening = autoSkipOpening,
                autoSkipEnding = autoSkipEnding,
                autoPlayNextEpisode = autoPlayNextEpisode,
                onPrefetchAdjacent = {
                    viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), currentEpisode, anime.id, released)
                },
                disableMaterialColors = disableMaterialColors
            )
        }

        androidx.activity.compose.BackHandler {
            when {
                scheduleDialogOpen -> {
                    scheduleDialogOpen = false
                }
                overlayState !is OverlayState.None -> {
                    onClearAnimeStack()
                }
                else -> {
                    showPlayer = false
                    currentVideoUrl = null
                }
            }
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
                            disableMaterialColors = disableMaterialColors,
                            simplifyAnimeDetails = simplifyAnimeDetails,
                            hideAdultContent = hideAdultContent,
                            isLoggedIn = isLoggedIn,
                            onPlayEpisode = onPlayEpisode,
                            onShowAnimeDialog = onShowAnimeDialog,
                            onClearAnimeStack = onClearAnimeStack,
                            onAnimeDialogOpen = { isOpen -> scheduleDialogOpen = isOpen }
                        )
                        1 -> ExploreScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            showStatusColors = showStatusColors,
                            simplifyAnimeDetails = simplifyAnimeDetails,
                            favoriteIds = aniListFavoriteIds,
                            onToggleFavorite = { anime -> viewModel.toggleAniListFavorite(anime.id) },
                            onPlayEpisode = onPlayEpisode,
                            currentlyWatching = currentlyWatching,
                            planningToWatch = planningToWatch,
                            completed = completed,
                            onHold = onHold,
                            dropped = dropped,
                            isVisible = isCurrentPage,
                            onShowAnimeDialog = onShowAnimeDialog,
                            onClearAnimeStack = onClearAnimeStack
                        )
                        2 -> HomeScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            showStatusColors = showStatusColors,
                            simplifyEpisodeMenu = simplifyEpisodeMenu,
                            simplifyAnimeDetails = simplifyAnimeDetails,
                            hideAdultContent = hideAdultContent,
                            favoriteIds = aniListFavoriteIds,
                            localFavorites = localFavorites,
                            onToggleLocalFavorite = { animeId -> viewModel.toggleLocalFavorite(animeId) },
                            onToggleFavorite = { anime -> viewModel.toggleAniListFavorite(anime.id) },
                            onPlayEpisode = onPlayEpisode,
                            onLoginClick = { viewModel.loginWithAniList() },
                            onShowAnimeDialog = onShowAnimeDialog,
                            currentScreenIndex = pagerState.currentPage,
                            playbackPositions = playbackPositions
                        )
                        3 -> SettingsScreen(
                            viewModel = viewModel,
                            isOled = isOled,
                            isLoggedIn = isLoggedIn,
                            showStatusColors = showStatusColors,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .pointerInput(Unit) { },
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
