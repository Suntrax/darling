package com.blissless.anime

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Job
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.blissless.anime.api.myanimelist.LoginProvider
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.DetailedAnimeData
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.data.models.LocalAnimeEntry
import com.blissless.anime.data.models.QualityOption
import com.blissless.anime.data.models.toDetailedAnimeData
import com.blissless.anime.ui.screens.cast.AllCastScreen
import com.blissless.anime.ui.screens.cast.AllStaffScreen
import com.blissless.anime.ui.screens.character.CharacterScreen
import com.blissless.anime.ui.screens.details.DetailedAnimeScreen
import com.blissless.anime.ui.screens.explore.ExploreScreen
import com.blissless.anime.ui.screens.home.HomeScreen
import com.blissless.anime.ui.screens.player.PlayerScreen
import com.blissless.anime.ui.screens.airing.ScheduleScreen
import com.blissless.anime.ui.screens.settings.SettingsScreen
import com.blissless.anime.ui.screens.character.StaffScreen
import com.blissless.anime.ui.screens.relations.AllRelationsScreen
import com.blissless.anime.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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
            val showAnimeCardButtons by mainViewModel.showAnimeCardButtons.collectAsState()
            val preferEnglishTitles by mainViewModel.preferEnglishTitles.collectAsState()
            val preventScheduleSync by mainViewModel.preventScheduleSync.collectAsState()

            var isLoggedIn by remember { mutableStateOf(savedToken != null) }
            val token by mainViewModel.authToken.collectAsState()
            val loginProvider by mainViewModel.loginProvider.collectAsState()
            var showLocalSyncDialog by remember { mutableStateOf(false) }
            val localAnimeStatus by mainViewModel.localAnimeStatus.collectAsState()
            
            LaunchedEffect(token, loginProvider) {
                val isAnyLoggedIn = token != null || loginProvider != LoginProvider.NONE
                if (isAnyLoggedIn && !isLoggedIn && localAnimeStatus.isNotEmpty()) {
                    showLocalSyncDialog = true
                }
                isLoggedIn = isAnyLoggedIn
            }

            // Always enable high refresh rate on supported devices
            LaunchedEffect(Unit) {
                enableHighRefreshRate()
            }
            
            // Observe toast messages
            val toastContext = LocalContext.current
            LaunchedEffect(Unit) {
                mainViewModel.toastMessage.collect { message ->
                    Toast.makeText(toastContext, message, Toast.LENGTH_SHORT).show()
                }
            }
            
            // Observe logout events to reset auth flags
            LaunchedEffect(Unit) {
                mainViewModel.logoutEvent.collect {
                    (toastContext as? MainActivity)?.resetAuthFlags()
                }
            }

            if (showLocalSyncDialog) {
                AlertDialog(
                    onDismissRequest = { showLocalSyncDialog = false },
                    containerColor = Color(0xFF1A1A1A),
                    title = { 
                        Text(
                            "Sync Local Changes",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        ) 
                    },
                    text = {
                        Column {
                            Text(
                                "You have ${localAnimeStatus.size} anime tracked offline.",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "Choose how to sync:",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                "1. Discard Local Changes",
                                color = Color(0xFFF44336),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Remove all offline changes. AniList data will remain unchanged.",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                "2. Add New Anime Only",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Add new anime from offline to AniList. Won't overwrite existing entries.",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                "3. Overwrite AniList",
                                color = Color(0xFF2196F3),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Replace all matching anime on AniList with your offline changes.",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLocalSyncDialog = false
                                mainViewModel.discardLocalChanges()
                            }
                        ) {
                            Text("Discard", color = Color(0xFFF44336))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    showLocalSyncDialog = false
                                    mainViewModel.addLocalToAniListOnlyNew()
                                }
                            ) {
                                Text("Add New Only", color = Color(0xFF4CAF50))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    showLocalSyncDialog = false
                                    mainViewModel.overwriteAniListWithLocal()
                                }
                            ) {
                                Text("Overwrite", color = Color(0xFF2196F3))
                            }
                        }
                    }
                )
            }

            AppTheme(useOled = isOled, useMonochrome = disableMaterialColors) {
                MainScreen(
                    viewModel = mainViewModel,
                    isOled = isOled,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    preferEnglishTitles = preferEnglishTitles,
                    preventScheduleSync = preventScheduleSync,
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

    override fun onResume() {
        super.onResume()
        // Check for any pending auth callback when returning to the app
        handleAuthCallback(intent)
    }

    private var isMalAuthHandled = false
    private var isAniListAuthHandled = false
    
    private fun handleAuthCallback(intent: Intent?) {
        if (intent == null) {
            return
        }
        
        val uriString = intent.dataString
        if (uriString == null) {
            return
        }
        
        // Check if it's MAL auth (contains code= parameter)
        if (!isMalAuthHandled && uriString.contains("code=") && uriString.startsWith("animescraper://success")) {
            isMalAuthHandled = true
            mainViewModel.handleMalAuthAuthCode(uriString)
        }
        // Check if it's AniList auth (contains access_token=)
        else if (!isAniListAuthHandled && uriString.contains("access_token=") && uriString.startsWith("animescraper://success")) {
            isAniListAuthHandled = true
            mainViewModel.handleAuthRedirect(intent)
        }
    }
    
    fun resetAuthFlags() {
        isMalAuthHandled = false
        isAniListAuthHandled = false
    }
}

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    showStatusColors: Boolean,
    showAnimeCardButtons: Boolean,
    preferEnglishTitles: Boolean,
    preventScheduleSync: Boolean,
    isLoggedIn: Boolean
) {
    val hideNavbar by viewModel.hideNavbar.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val startupScreen by viewModel.startupScreen.collectAsState()
    val pagerState = rememberPagerState(initialPage = startupScreen, pageCount = { 4 })

    var committedPage by remember { mutableIntStateOf(startupScreen) }

    var preloadedPages by remember { mutableStateOf(setOf(1)) }

    var screenNavigationStack by remember { mutableStateOf<List<Int>>(emptyList()) }

    val onNavigateBack: () -> Unit = {
        val stack = screenNavigationStack.toMutableList()
        if (stack.isNotEmpty()) {
            val prevPage = stack.removeLast()
            screenNavigationStack = stack
            scope.launch { pagerState.animateScrollToPage(prevPage) }
        }
    }

    val detailedAnimeScreenData by viewModel.detailedAnimeScreenData.collectAsState()
    val richEpisodeScreenAnime by viewModel.richEpisodeScreenAnime.collectAsState()

    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()
    val prefetchedStreams by viewModel.prefetchedStreams.collectAsState()
    val prefetchedEpisodeInfo by viewModel.prefetchedEpisodeInfo.collectAsState()

    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)

    val simplifyEpisodeMenu by viewModel.simplifyEpisodeMenu.collectAsState(initial = true)
    val hideAdultContent by viewModel.hideAdultContent.collectAsState(initial = false)

    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val aniListFavoriteIds = remember(aniListFavorites) { aniListFavorites.map { it.id }.toSet() }
    val malFavorites by viewModel.malFavorites.collectAsState()
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
    val showBufferIndicator by viewModel.showBufferIndicator.collectAsState(initial = true)
    val bufferAheadSeconds by viewModel.bufferAheadSeconds.collectAsState(initial = 30)

    LaunchedEffect(currentlyWatching) {
        if (currentlyWatching.isNotEmpty()) {
            // Auto-prefetch disabled for now
            // currentlyWatching.take(3).forEach { anime ->
            //     viewModel.prefetchCurrentEpisodeStream(anime)
            // }
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
    var loadingJob by remember { mutableStateOf<Job?>(null) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var currentServerAttempt by remember { mutableStateOf<String?>(null) }
    var currentServerAttemptIsFallback by remember { mutableStateOf(false) }

    var currentEpisodeInfo by remember { mutableStateOf<EpisodeStreams?>(null) }
    var currentEpisodeTitle by remember { mutableStateOf<String?>(null) }
    var currentCategory by remember { mutableStateOf("sub") }
    var currentServerName by remember { mutableStateOf("") }
    var currentServerIndex by remember { mutableIntStateOf(0) }

    var isFallbackStream by remember { mutableStateOf(false) }
    var requestedCategory by remember { mutableStateOf("sub") }
    var actualCategory by remember { mutableStateOf("sub") }
    var isManualServerChange by remember { mutableStateOf(false) }
    var isChangingEpisode by remember { mutableStateOf(false) }

    // Quality state
    var currentQualityOptions by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var currentQuality by remember { mutableStateOf("Auto") }

    var savedPlaybackPosition by remember { mutableLongStateOf(0L) }

    var overlayState by remember { mutableStateOf<OverlayState>(OverlayState.None) }
    
    var scheduleDialogOpen by remember { mutableStateOf(false) }
    
    var detailedAnimeFromMal by remember { mutableStateOf<DetailedAnimeData?>(null) }
    
    // Callback to show detailed anime from MAL history using AniList API
    val onShowDetailedAnimeFromMal: (Int) -> Unit = { malId ->
        kotlinx.coroutines.MainScope().launch {
            val detailedData = viewModel.fetchDetailedAnimeDataByMalId(malId)
            detailedAnimeFromMal = detailedData
        }
    }
    
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
        // Clear the card bounds when opening the detailed screen to hide the source card
        viewModel.clearExploreAnimeCardBounds()
        viewModel.clearHomeAnimeCardBounds()
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
            val prefetchedEpInfo = prefetchedEpisodeInfo[epInfoKey]
            currentEpisodeInfo = prefetchedEpInfo
            currentServerIndex = 0

            // If episode info wasn't prefetched, fetch it now for server list
            if (prefetchedEpInfo == null) {
                scope.launch {
                    val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                    currentEpisodeInfo = viewModel.getEpisodeInfo(getScrapingName(anime), episode, anime.id, latestAired)
                }
            }

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
        currentServerAttempt = null
        currentServerAttemptIsFallback = false
        loadingJob = scope.launch {
            val latestAired = anime.latestEpisode ?: anime.totalEpisodes

            val epInfo = viewModel.getEpisodeInfo(getScrapingName(anime), episode, anime.id, latestAired)
            currentEpisodeInfo = epInfo

            val result = viewModel.tryAllServersWithFallback(
                getScrapingName(anime),
                episode,
                anime.id,
                latestAired,
                preferredCategory = preferredCategory,
                onServerAttempt = { serverName, category, isFallback ->
                    currentServerAttempt = serverName
                    currentServerAttemptIsFallback = isFallback
                }
            )

            if (result.stream != null) {
                streamError = null
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
                streamError = "Stream not found: Ep $episode"
            }

            isLoadingStream = false
        }
    }

    suspend fun getTmdbEpisodeTitle(anime: AnimeMedia, episode: Int): String {
        // Try cache first
        val cachedEpisodes = viewModel.getCachedTmdbEpisodes(anime.id)
        if (cachedEpisodes != null) {
            val title = cachedEpisodes.find { it.episode == episode }?.title
            if (!title.isNullOrEmpty() && !title.startsWith("Episode", ignoreCase = true)) {
                return title
            }
        }
        // Fallback to fetching
        return try {
            val tmdbEpisodes = viewModel.fetchTmdbEpisodes(anime.title, anime.id, anime.year, anime.format)
            val title = tmdbEpisodes.find { it.episode == episode }?.title
            if (!title.isNullOrEmpty() && !title.startsWith("Episode", ignoreCase = true)) title else "Episode $episode"
        } catch (e: Exception) {
            "Episode $episode"
        }
    }

    val onPlayEpisode: (AnimeMedia, Int, String?) -> Unit = { anime, episode, title ->
        if (title == null) {
            scope.launch {
                currentEpisodeTitle = getTmdbEpisodeTitle(anime, episode)
                loadAndPlayEpisode(anime, episode)
            }
        } else {
            currentEpisodeTitle = title
            loadAndPlayEpisode(anime, episode)
        }
    }

    val onPreviousEpisode: () -> Unit = {
        if (!isChangingEpisode && currentAnime != null) {
            isChangingEpisode = true
            
            val anime = currentAnime!!
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

                    val epInfoKey = "${anime.id}_$prevEp"
                    val prefetchedEpInfo = prefetchedEpisodeInfo[epInfoKey]
                    currentEpisodeInfo = prefetchedEpInfo

                    // If episode info wasn't prefetched, fetch it now for server list
                    if (prefetchedEpInfo == null) {
                        scope.launch {
                            val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                            currentEpisodeInfo = viewModel.getEpisodeInfo(getScrapingName(anime), prevEp, anime.id, latestAired)
                        }
                    }

                    scope.launch {
                        val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                        viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), prevEp, anime.id, latestAired)
                        currentEpisodeTitle = getTmdbEpisodeTitle(anime, prevEp)
                        isChangingEpisode = false
                    }

                    if (isFallbackStream) {
                        val message = if (requestedCategory == "dub") {
                            "Dub not available, playing sub"
                        } else {
                            "Sub not available, playing dub"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    isLoadingStream = true
                    currentServerAttempt = null
                    currentServerAttemptIsFallback = false
                    loadingJob = scope.launch {
                        val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                        val epInfo = viewModel.getEpisodeInfo(getScrapingName(anime), prevEp, anime.id, latestAired)
                        currentEpisodeInfo = epInfo

                        val result = viewModel.tryAllServersWithFallback(
                            getScrapingName(anime), prevEp, anime.id, latestAired,
                            preferredCategory = preferredCategory,
                            onServerAttempt = { serverName, category, isFallback ->
                                currentServerAttempt = serverName
                                currentServerAttemptIsFallback = isFallback
                            }
                        )
                        if (result.stream != null) {
                            streamError = null
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
                            currentEpisodeTitle = getTmdbEpisodeTitle(anime, prevEp)
                            isChangingEpisode = false

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
                            isChangingEpisode = false
                        }
                        isLoadingStream = false
                    }
                }
            } else {
                isChangingEpisode = false
            }
        }
    }

    val onNextEpisode: () -> Unit = {
        if (!isChangingEpisode && currentAnime != null) {
            isChangingEpisode = true
            
            val anime = currentAnime!!
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
                        currentEpisodeTitle = getTmdbEpisodeTitle(anime, nextEp)
                        isChangingEpisode = false
                    }

                    if (isFallbackStream) {
                        val message = if (requestedCategory == "dub") {
                            "Dub not available, playing sub"
                        } else {
                            "Sub not available, playing dub"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    isLoadingStream = true
                    currentServerAttempt = null
                    currentServerAttemptIsFallback = false
                    loadingJob = scope.launch {
                        val latestAired = anime.latestEpisode ?: anime.totalEpisodes
                        val epInfo = viewModel.getEpisodeInfo(getScrapingName(anime), nextEp, anime.id, latestAired)
                        currentEpisodeInfo = epInfo

                        val result = viewModel.tryAllServersWithFallback(
                            getScrapingName(anime), nextEp, anime.id, latestAired,
                            preferredCategory = preferredCategory,
                            onServerAttempt = { serverName, category, isFallback ->
                                currentServerAttempt = serverName
                                currentServerAttemptIsFallback = isFallback
                            }
                        )
                        if (result.stream != null) {
                            streamError = null
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

                            animekaiIntroStart = result.stream.introStart
                            animekaiIntroEnd = result.stream.introEnd
                            animekaiOutroStart = result.stream.outroStart
                            animekaiOutroEnd = result.stream.outroEnd

                            viewModel.prefetchAdjacentEpisodes(getScrapingName(anime), nextEp, anime.id, latestAired)
                            currentEpisodeTitle = getTmdbEpisodeTitle(anime, nextEp)
                            isChangingEpisode = false

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
                            isChangingEpisode = false
                        }
                        isLoadingStream = false
                    }
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
            val selectedServer = servers?.find { it.name == serverName }
            val serverExists = selectedServer != null

            if (!serverExists) {
                // Try the other category
                val fallbackServers = if (category == "sub") cachedEpInfo.dubServers else cachedEpInfo.subServers
                val fallbackExists = fallbackServers.any { it.name == serverName }
                if (!fallbackExists) {
                    Toast.makeText(context, "Server not found: $serverName", Toast.LENGTH_SHORT).show()
                    return@let
                }
            }

            val serverUrl = selectedServer?.url ?: servers?.find { it.name == serverName }?.url

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
            loadingJob = scope.launch {
                val result = viewModel.getStreamForServer(
                    getScrapingName(anime),
                    currentEpisode,
                    serverName,
                    category,
                    anime.id,
                    serverUrl
                )
                if (result != null) {
                    streamError = null
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
        val isAnimeFavorite = aniListFavoriteIds.contains(exploreDialog.anime.id)
        DetailedAnimeScreen(
            anime = exploreDialog.anime.toDetailedAnimeData(),
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = animeStatusMap[exploreDialog.anime.id],
            isFavorite = isAnimeFavorite,
            initialCardBounds = viewModel.exploreAnimeCardBounds.value,
            onDismiss = {
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
            onPlayEpisode = { episode, _ ->
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
                onPlayEpisode(animeMedia, episode, null)
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
                if (viewModel.loginProvider.value == LoginProvider.MAL) {
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
                    viewModel.toggleMalFavorite(animeMedia)
                } else {
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
                    viewModel.toggleAniListFavorite(exploreDialog.anime.id, animeMedia)
                }
            },
            localStatus = localAnimeStatus[exploreDialog.anime.id]?.status,
            isLocalFavorite = localFavoriteIds.contains(exploreDialog.anime.id),
            onToggleLocalFavorite = { id ->
                viewModel.toggleLocalFavorite(id, exploreDialog.anime.title, exploreDialog.anime.cover, exploreDialog.anime.banner, exploreDialog.anime.year, exploreDialog.anime.averageScore)
            },
            onUpdateLocalStatus = { status ->
                val currentEntry = localAnimeStatus[exploreDialog.anime.id]
                if (status != null) {
                    viewModel.setLocalAnimeStatus(
                        exploreDialog.anime.id,
                        LocalAnimeEntry(
                            id = exploreDialog.anime.id,
                            status = status,
                            progress = currentEntry?.progress ?: 0,
                            totalEpisodes = exploreDialog.anime.episodes
                        )
                    )
                } else {
                    viewModel.setLocalAnimeStatus(exploreDialog.anime.id, null)
                }
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
                                viewModel.clearExploreAnimeCardBounds()
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
            },
            onCharacterClick = { characterId ->
                overlayState = OverlayState.CharacterDialog(
                    characterId = characterId, 
                    animeId = exploreDialog.anime.id, 
                    previousAnime = exploreDialog.anime,
                    previousFirstAnime = exploreDialog.firstAnime,
                    previousIsFirstOpen = exploreDialog.isFirstOpen
                )
            },
            onStaffClick = { staffId ->
                overlayState = OverlayState.StaffDialog(
                    staffId = staffId, 
                    animeId = exploreDialog.anime.id,
                    previousAnime = exploreDialog.anime,
                    previousFirstAnime = exploreDialog.firstAnime,
                    previousIsFirstOpen = exploreDialog.isFirstOpen
                )
            },
            onViewAllCast = {
                overlayState = OverlayState.AllCastDialog(
                    animeId = exploreDialog.anime.id, 
                    animeTitle = exploreDialog.anime.title,
                    previousAnime = exploreDialog.anime,
                    previousFirstAnime = exploreDialog.firstAnime,
                    previousIsFirstOpen = exploreDialog.isFirstOpen
                )
            },
            onViewAllStaff = {
                overlayState = OverlayState.AllStaffDialog(
                    animeId = exploreDialog.anime.id, 
                    animeTitle = exploreDialog.anime.title,
                    previousAnime = exploreDialog.anime,
                    previousFirstAnime = exploreDialog.firstAnime,
                    previousIsFirstOpen = exploreDialog.isFirstOpen
                )
            },
            onViewAllRelations = { animeId, title ->
                android.util.Log.d("DEBUG", ">>> MainActivity onViewAllRelations lambda triggered: animeId=$animeId, title=$title")
                android.util.Log.d("ALL_RELATIONS", ">>> onViewAllRelations called with animeId=$animeId, title=$title")
                overlayState = OverlayState.AllRelationsDialog(
                    animeId = animeId, 
                    animeTitle = title,
                    previousAnime = exploreDialog.anime,
                    previousFirstAnime = exploreDialog.firstAnime,
                    previousIsFirstOpen = exploreDialog.isFirstOpen
                )
                android.util.Log.d("DEBUG", ">>> overlayState is now: $overlayState")
            }
        )
    }

    // Character Screen
    val characterDialog = overlayState as? OverlayState.CharacterDialog
    if (characterDialog != null) {
        CharacterScreen(
            characterId = characterDialog.characterId,
            viewModel = viewModel,
            isOled = isOled,
            onDismiss = { 
                val previousAnime = characterDialog.previousAnime
                if (previousAnime != null) {
                    overlayState = OverlayState.ExploreAnimeDialog(
                        anime = previousAnime,
                        firstAnime = characterDialog.previousFirstAnime,
                        isFirstOpen = characterDialog.previousIsFirstOpen
                    )
                } else {
                    overlayState = OverlayState.None
                }
            },
            onAnimeClick = { animeId ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(animeId)
                    if (detailedData != null) {
                        val newAnime = ExploreAnime(
                            id = detailedData.id,
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
                        overlayState = OverlayState.ExploreAnimeDialog(anime = newAnime, firstAnime = newAnime, isFirstOpen = false)
                    } else {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Staff Screen
    val staffDialog = overlayState as? OverlayState.StaffDialog
    if (staffDialog != null) {
        StaffScreen(
            staffId = staffDialog.staffId,
            viewModel = viewModel,
            isOled = isOled,
            onDismiss = { 
                val previousAnime = staffDialog.previousAnime
                if (previousAnime != null) {
                    overlayState = OverlayState.ExploreAnimeDialog(
                        anime = previousAnime,
                        firstAnime = staffDialog.previousFirstAnime,
                        isFirstOpen = staffDialog.previousIsFirstOpen
                    )
                } else {
                    overlayState = OverlayState.None
                }
            },
            onAnimeClick = { animeId ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(animeId)
                    if (detailedData != null) {
                        val newAnime = ExploreAnime(
                            id = detailedData.id,
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
                        overlayState = OverlayState.ExploreAnimeDialog(anime = newAnime, firstAnime = newAnime, isFirstOpen = false)
                    } else {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // All Cast Screen
    val allCastDialog = overlayState as? OverlayState.AllCastDialog
    if (allCastDialog != null) {
        android.util.Log.d("MAIN_DEBUG", "Showing AllCastScreen for animeId=${allCastDialog.animeId}")
        AllCastScreen(
            animeId = allCastDialog.animeId,
            animeTitle = allCastDialog.animeTitle,
            viewModel = viewModel,
            isOled = isOled,
            onDismiss = { 
                val previousAnime = allCastDialog.previousAnime
                if (previousAnime != null) {
                    overlayState = OverlayState.ExploreAnimeDialog(
                        anime = previousAnime,
                        firstAnime = allCastDialog.previousFirstAnime,
                        isFirstOpen = allCastDialog.previousIsFirstOpen
                    )
                } else {
                    overlayState = OverlayState.None
                }
            },
            onCharacterClick = { characterId ->
                val previousAnime = allCastDialog.previousAnime
                overlayState = OverlayState.CharacterDialog(
                    characterId = characterId, 
                    animeId = allCastDialog.animeId, 
                    previousAnime = previousAnime,
                    previousFirstAnime = allCastDialog.previousFirstAnime,
                    previousIsFirstOpen = allCastDialog.previousIsFirstOpen
                )
            },
            onAnimeClick = { animeId ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(animeId)
                    if (detailedData != null) {
                        val newAnime = ExploreAnime(
                            id = detailedData.id,
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
                        overlayState = OverlayState.ExploreAnimeDialog(anime = newAnime, firstAnime = newAnime, isFirstOpen = false)
                    } else {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // All Staff Screen
    val allStaffDialog = overlayState as? OverlayState.AllStaffDialog
    if (allStaffDialog != null) {
        android.util.Log.d("MAIN_DEBUG", "Showing AllStaffScreen for animeId=${allStaffDialog.animeId}")
        AllStaffScreen(
            animeId = allStaffDialog.animeId,
            animeTitle = allStaffDialog.animeTitle,
            viewModel = viewModel,
            isOled = isOled,
            onDismiss = { 
                val previousAnime = allStaffDialog.previousAnime
                if (previousAnime != null) {
                    overlayState = OverlayState.ExploreAnimeDialog(
                        anime = previousAnime,
                        firstAnime = allStaffDialog.previousFirstAnime,
                        isFirstOpen = allStaffDialog.previousIsFirstOpen
                    )
                } else {
                    overlayState = OverlayState.None
                }
            },
            onStaffClick = { staffId ->
                val previousAnime = allStaffDialog.previousAnime
                overlayState = OverlayState.StaffDialog(
                    staffId = staffId, 
                    animeId = allStaffDialog.animeId,
                    previousAnime = previousAnime,
                    previousFirstAnime = allStaffDialog.previousFirstAnime,
                    previousIsFirstOpen = allStaffDialog.previousIsFirstOpen
                )
            },
            onAnimeClick = { animeId ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(animeId)
                    if (detailedData != null) {
                        val newAnime = ExploreAnime(
                            id = detailedData.id,
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
                        overlayState = OverlayState.ExploreAnimeDialog(anime = newAnime, firstAnime = newAnime, isFirstOpen = false)
                    } else {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // All Relations Screen
    val allRelationsDialog = overlayState as? OverlayState.AllRelationsDialog
    if (allRelationsDialog != null) {
        android.util.Log.d("MAIN_DEBUG", "Showing AllRelationsScreen for animeId=${allRelationsDialog.animeId}")
        android.util.Log.d("MAIN_DEBUG", ">>> About to call AllRelationsScreen")
        AllRelationsScreen(
            animeId = allRelationsDialog.animeId,
            animeTitle = allRelationsDialog.animeTitle,
            viewModel = viewModel,
            isOled = isOled,
            onDismiss = { 
                val previousAnime = allRelationsDialog.previousAnime
                if (previousAnime != null) {
                    overlayState = OverlayState.ExploreAnimeDialog(
                        anime = previousAnime,
                        firstAnime = allRelationsDialog.previousFirstAnime,
                        isFirstOpen = allRelationsDialog.previousIsFirstOpen
                    )
                } else {
                    overlayState = OverlayState.None
                }
            },
            onAnimeClick = { animeId ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(animeId)
                    if (detailedData != null) {
                        val newAnime = ExploreAnime(
                            id = detailedData.id,
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
                        overlayState = OverlayState.ExploreAnimeDialog(anime = newAnime, firstAnime = newAnime, isFirstOpen = false)
                    } else {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
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
                episodeTitle = currentEpisodeTitle,
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
                disableMaterialColors = disableMaterialColors,
                showBufferIndicator = showBufferIndicator,
                bufferAheadSeconds = bufferAheadSeconds,
                onGetCacheDataSourceFactory = { referer -> viewModel.getCacheDataSourceFactory(referer) },
                onBackClick = { 
                    showPlayer = false
                    currentVideoUrl = null
                }
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
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 2,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val isCurrentPage = pagerState.currentPage == page
                    val isScheduleVisible = pagerState.currentPage == 0

                    when (page) {
                        0 -> ScheduleScreen(
                            viewModel = viewModel,
                            isOled = isOled,
                            isVisible = isScheduleVisible,
                            preventAutoSync = preventScheduleSync,
                            showStatusColors = showStatusColors,
                            disableMaterialColors = disableMaterialColors,
                            hideAdultContent = hideAdultContent,
                            preferEnglishTitles = preferEnglishTitles,
                            isLoggedIn = isLoggedIn,
                            onPlayEpisode = onPlayEpisode,
                            onShowAnimeDialog = onShowAnimeDialog,
                            onClearAnimeStack = onClearAnimeStack,
                            onAnimeDialogOpen = { isOpen -> scheduleDialogOpen = isOpen },
                            onCharacterClick = { characterId ->
                                overlayState = OverlayState.CharacterDialog(characterId = characterId, animeId = 0, previousAnime = null)
                            },
                            onStaffClick = { staffId ->
                                overlayState = OverlayState.StaffDialog(staffId = staffId, animeId = 0, previousAnime = null)
                            },
                            onViewAllCast = { animeId, animeTitle ->
                                overlayState = OverlayState.AllCastDialog(animeId = animeId, animeTitle = animeTitle, previousAnime = null)
                            },
                            onViewAllStaff = { animeId, animeTitle ->
                                overlayState = OverlayState.AllStaffDialog(animeId = animeId, animeTitle = animeTitle, previousAnime = null)
                            },
                            onViewAllRelations = { animeId, animeTitle ->
                                overlayState = OverlayState.AllRelationsDialog(animeId = animeId, animeTitle = animeTitle, previousAnime = null)
                            }
                        )
                        1 -> ExploreScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            showStatusColors = showStatusColors,
                            showAnimeCardButtons = showAnimeCardButtons,
                            preferEnglishTitles = preferEnglishTitles,
                            hideAdultContent = hideAdultContent,
                            favoriteIds = if (viewModel.loginProvider.value == LoginProvider.MAL) malFavorites.map { it.id }.toSet() else aniListFavoriteIds,
                            onToggleFavorite = { anime -> 
                                val animeMedia = AnimeMedia(
                                    id = anime.id,
                                    title = anime.title,
                                    titleEnglish = anime.titleEnglish,
                                    cover = anime.cover,
                                    banner = anime.banner,
                                    progress = 0,
                                    totalEpisodes = anime.episodes,
                                    latestEpisode = anime.latestEpisode,
                                    status = "",
                                    averageScore = anime.averageScore,
                                    genres = anime.genres,
                                    listStatus = "",
                                    listEntryId = 0,
                                    year = anime.year,
                                    malId = anime.malId
                                )
                                if (viewModel.loginProvider.value == LoginProvider.MAL) {
                                    viewModel.toggleMalFavorite(animeMedia)
                                } else {
                                    viewModel.toggleAniListFavorite(anime.id, animeMedia)
                                }
                            },
                            onPlayEpisode = onPlayEpisode,
                            currentlyWatching = currentlyWatching,
                            planningToWatch = planningToWatch,
                            completed = completed,
                            onHold = onHold,
                            dropped = dropped,
                            isVisible = isCurrentPage,
                            onShowAnimeDialog = onShowAnimeDialog,
                            onClearAnimeStack = onClearAnimeStack,
                            onCharacterClick = { characterId ->
                                overlayState = OverlayState.CharacterDialog(characterId = characterId, animeId = 0)
                            },
                            onStaffClick = { staffId ->
                                overlayState = OverlayState.StaffDialog(staffId = staffId, animeId = 0, previousAnime = null)
                            },
                            onViewAllCast = { animeId, animeTitle ->
                                overlayState = OverlayState.AllCastDialog(animeId = animeId, animeTitle = animeTitle, previousAnime = null)
                            },
                            onViewAllStaff = { animeId, animeTitle ->
                                overlayState = OverlayState.AllStaffDialog(animeId = animeId, animeTitle = animeTitle, previousAnime = null)
                            },
                            onViewAllRelations = { animeId, animeTitle ->
                                android.util.Log.d("DEBUG", ">>> MainActivity ExploreScreen onViewAllRelations: animeId=$animeId")
                                overlayState = OverlayState.AllRelationsDialog(animeId = animeId, animeTitle = animeTitle, previousAnime = null)
                            },
                            localAnimeStatus = localAnimeStatus
                        )
                        2 -> HomeScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            showStatusColors = showStatusColors,
                            simplifyEpisodeMenu = simplifyEpisodeMenu,
                            preferEnglishTitles = preferEnglishTitles,
                            hideAdultContent = hideAdultContent,
                            favoriteIds = if (viewModel.loginProvider.value == LoginProvider.MAL) malFavorites.map { it.id }.toSet() else aniListFavoriteIds,
                            onToggleLocalFavorite = { animeId -> viewModel.toggleLocalFavorite(animeId) },
                            onToggleFavorite = { anime -> 
                                if (viewModel.loginProvider.value == LoginProvider.MAL) {
                                    viewModel.toggleMalFavorite(anime)
                                } else {
                                    viewModel.toggleAniListFavorite(anime.id, anime)
                                }
                            },
                            onPlayEpisode = onPlayEpisode,
                            onLoginClick = { viewModel.loginWithAniList() },
                            onShowAnimeDialog = onShowAnimeDialog,
                            onShowDetailedAnimeFromMal = onShowDetailedAnimeFromMal,
                            onCharacterClick = { characterId ->
                                overlayState = OverlayState.CharacterDialog(characterId = characterId, animeId = 0)
                            },
                            onStaffClick = { staffId ->
                                overlayState = OverlayState.StaffDialog(staffId = staffId, animeId = 0)
                            },
                            onViewAllCast = { animeId, animeTitle ->
                                overlayState = OverlayState.AllCastDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllStaff = { animeId, animeTitle ->
                                overlayState = OverlayState.AllStaffDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllRelations = { animeId, animeTitle ->
                                android.util.Log.d("DEBUG", ">>> MainActivity HomeScreen onViewAllRelations: animeId=$animeId")
                                overlayState = OverlayState.AllRelationsDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            playbackPositions = playbackPositions
                        )
                        3 -> SettingsScreen(
                            viewModel = viewModel,
                            isOled = isOled,
                            isLoggedIn = isLoggedIn,
                            showStatusColors = showStatusColors,
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
                            if (currentServerAttempt != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (currentServerAttemptIsFallback) "Trying $currentServerAttempt (fallback)..." else "Trying $currentServerAttempt...",
                                    color = Color.Yellow,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = {
                                    loadingJob?.cancel()
                                    isLoadingStream = false
                                    loadingJob = null
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                val isOledTheme = isOled
                val primaryColor = MaterialTheme.colorScheme.primary
                val surfaceColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface
                val onSurfaceColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .offset(y = (-16).dp)
                ) {
                    if (!hideNavbar && !isLoadingStream) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 4.dp, start = 48.dp, end = 48.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = surfaceColor.copy(alpha = 0.95f),
                            tonalElevation = 4.dp,
                            shadowElevation = 8.dp,
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                        ) {
                            val selectedIndex = pagerState.targetPage

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val items = listOf("Schedule", "Explore", "Home", "Settings")
                                val icons = listOf(Icons.Default.CalendarMonth, Icons.Default.Explore, Icons.Default.Home, Icons.Default.Settings)

                            items.forEachIndexed { index, item ->
                                val isSelected = index == selectedIndex

                                Box(
                                    modifier = Modifier
                                        .weight(if (isSelected) 0.67f else 0.25f)
                                        .animateContentSize(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                        .height(56.dp)
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    if (event.changes.any { it.pressed }) {
                                                        scope.launch {
                                                            pagerState.scrollToPage(index)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val alpha by animateFloatAsState(
                                        targetValue = if (isSelected) 1f else 0f,
                                        animationSpec = tween(
                                            durationMillis = 200,
                                            easing = LinearEasing
                                        ),
                                        label = "alpha_$index"
                                    )

                                    if (isSelected) {
                                        val pillColor = if (disableMaterialColors) {
                                            Color.White.copy(alpha = 0.2f)
                                        } else {
                                            primaryContainerColor
                                        }
                                        val pillTextColor = if (disableMaterialColors) {
                                            Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        }

                                        Surface(
                                            shape = MaterialTheme.shapes.extraLarge,
                                            color = pillColor,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .padding(vertical = 5.dp)
                                                .fillMaxWidth(0.95f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    icons[index],
                                                    contentDescription = item,
                                                    tint = pillTextColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    item,
                                                    color = pillTextColor,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    } else {
                                        Icon(
                                            icons[index],
                                            contentDescription = item,
                                            tint = if (isOledTheme) Color.White.copy(alpha = 0.6f) else onSurfaceColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

streamError?.let { error ->
                    LaunchedEffect(error) {
                        delay(3500)
                        streamError = null
                    }

                    AlertDialog(
                        onDismissRequest = { streamError = null },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        title = { 
                            Text(
                                text = "Stream Error",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        text = { 
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                modifier = Modifier.width(250.dp)
                            )
                        },
                        confirmButton = { 
                            TextButton(onClick = { streamError = null }) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
},
                        dismissButton = null
                    )
                }
            }
        }
    }
}
