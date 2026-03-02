package com.blissless.anime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
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
import com.blissless.anime.api.ServerInfo
import com.blissless.anime.ui.screens.ExploreScreen
import com.blissless.anime.ui.screens.HomeScreen
import com.blissless.anime.ui.screens.PlayerScreen
import com.blissless.anime.ui.screens.SettingsScreen
import com.blissless.anime.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    companion object {
        const val PREFS_NAME = "anilist_prefs"
        const val TOKEN_KEY = "auth_token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Check if user is logged in SYNCHRONOUSLY using SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasToken = prefs.getString(TOKEN_KEY, null) != null

        Log.d("MainActivity", "Has saved token: $hasToken")

        // Initialize ViewModel with the token state
        mainViewModel.init(applicationContext, hasToken)

        handleAuthCallback(intent)

        setContent {
            val isOled by mainViewModel.isOled.collectAsState()
            val token by mainViewModel.authToken.collectAsState(initial = if (hasToken) "loading" else null)

            // User is logged in if they had a token OR if token is now set
            val isLoggedIn = hasToken || token != null

            AppTheme(useOled = isOled) {
                MainScreen(
                    viewModel = mainViewModel,
                    isOled = isOled,
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

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    isLoggedIn: Boolean
) {
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val scope = rememberCoroutineScope()

    // Collect user's anime lists
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()
    val prefetchedStreams by viewModel.prefetchedStreams.collectAsState()
    val prefetchedEpisodeInfo by viewModel.prefetchedEpisodeInfo.collectAsState()

    // Pre-fetch streams for currently watching when lists load
    LaunchedEffect(currentlyWatching) {
        if (currentlyWatching.isNotEmpty()) {
            currentlyWatching.take(3).forEach { anime ->
                viewModel.prefetchCurrentEpisodeStream(anime)
            }
        }
    }

    // Player state
    var showPlayer by remember { mutableStateOf(false) }
    var currentVideoUrl by remember { mutableStateOf<String?>(null) }
    var currentReferer by remember { mutableStateOf("https://megacloud.tv/") }
    var currentSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var currentAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    var currentEpisode by remember { mutableIntStateOf(0) }
    var totalEpisodes by remember { mutableIntStateOf(0) }
    var isLoadingStream by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }

    // Server/track selection state
    var currentEpisodeInfo by remember { mutableStateOf<EpisodeStreams?>(null) }
    var currentCategory by remember { mutableStateOf("sub") }
    var currentServerName by remember { mutableStateOf("") }
    var currentServerIndex by remember { mutableIntStateOf(0) }

    // Helper function to load and play an episode
    fun loadAndPlayEpisode(anime: AnimeMedia, episode: Int) {
        currentAnime = anime
        currentEpisode = episode
        totalEpisodes = anime.totalEpisodes
        streamError = null
        showPlayer = false

        // Check if we have cached stream
        val cacheKey = "${anime.id}_$episode"
        prefetchedStreams[cacheKey]?.let { cached ->
            if (cached != null) {
                Log.d("MainActivity", "Using cached stream for ${anime.title} ep $episode")
                currentVideoUrl = cached.url
                currentReferer = cached.headers?.get("Referer") ?: "https://megacloud.tv/"
                currentSubtitleUrl = cached.subtitleUrl
                currentServerName = cached.serverName
                currentCategory = cached.category
                showPlayer = true

                prefetchedEpisodeInfo[cacheKey]?.let { currentEpisodeInfo = it }
                currentServerIndex = 0
                viewModel.prefetchAdjacentEpisodes(anime.title, episode, anime.id)
                return
            }
        }

        // No cache, fetch new
        isLoadingStream = true
        scope.launch {
            Log.d("MainActivity", "Fetching stream for ${anime.title} ep $episode")

            // First get episode info for server list
            val epInfo = viewModel.getEpisodeInfo(anime.title, episode, anime.id)
            currentEpisodeInfo = epInfo

            val result = viewModel.getStreamLinkWithCache(anime.title, episode, anime.id)

            if (result != null) {
                Log.d("MainActivity", "Stream URL found: ${result.url.take(50)}...")
                currentVideoUrl = result.url
                currentReferer = result.headers?.get("Referer") ?: "https://megacloud.tv/"
                currentSubtitleUrl = result.subtitleUrl
                currentServerName = result.serverName
                currentCategory = result.category
                currentServerIndex = 0
                showPlayer = true
                viewModel.prefetchAdjacentEpisodes(anime.title, episode, anime.id)
            } else {
                Log.e("MainActivity", "Failed to get stream for ${anime.title} ep $episode")
                streamError = "Could not find stream for ${anime.title} episode $episode"
            }

            isLoadingStream = false
        }
    }

    // Handle play episode
    val onPlayEpisode: (AnimeMedia, Int) -> Unit = { anime, episode ->
        Log.d("MainActivity", "onPlayEpisode: ${anime.title} ep $episode")
        loadAndPlayEpisode(anime, episode)
    }

    // Handle previous episode
    val onPreviousEpisode: () -> Unit = {
        currentAnime?.let { anime ->
            if (currentEpisode > 1) {
                val prevEp = currentEpisode - 1
                isLoadingStream = true
                scope.launch {
                    val epInfo = viewModel.getEpisodeInfo(anime.title, prevEp, anime.id)
                    currentEpisodeInfo = epInfo

                    val result = viewModel.getStreamLinkWithCache(anime.title, prevEp, anime.id)
                    if (result != null) {
                        currentVideoUrl = result.url
                        currentReferer = result.headers?.get("Referer") ?: "https://megacloud.tv/"
                        currentSubtitleUrl = result.subtitleUrl
                        currentEpisode = prevEp
                        currentServerName = result.serverName
                        currentCategory = result.category
                        currentServerIndex = 0
                        viewModel.prefetchAdjacentEpisodes(anime.title, prevEp, anime.id)
                    } else {
                        streamError = "Could not find stream for episode $prevEp"
                    }
                    isLoadingStream = false
                }
            }
        }
    }

    // Handle next episode
    val onNextEpisode: () -> Unit = {
        currentAnime?.let { anime ->
            if (totalEpisodes == 0 || currentEpisode < totalEpisodes) {
                val nextEp = currentEpisode + 1
                isLoadingStream = true
                scope.launch {
                    val epInfo = viewModel.getEpisodeInfo(anime.title, nextEp, anime.id)
                    currentEpisodeInfo = epInfo

                    val result = viewModel.getStreamLinkWithCache(anime.title, nextEp, anime.id)
                    if (result != null) {
                        currentVideoUrl = result.url
                        currentReferer = result.headers?.get("Referer") ?: "https://megacloud.tv/"
                        currentSubtitleUrl = result.subtitleUrl
                        currentEpisode = nextEp
                        currentServerName = result.serverName
                        currentCategory = result.category
                        currentServerIndex = 0
                        viewModel.prefetchAdjacentEpisodes(anime.title, nextEp, anime.id)
                    } else {
                        streamError = "Could not find stream for episode $nextEp"
                    }
                    isLoadingStream = false
                }
            }
        }
    }

    // Handle server change
    fun changeServer(serverName: String, category: String) {
        currentAnime?.let { anime ->
            isLoadingStream = true
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

                    // Update server index
                    val servers = if (category == "sub") currentEpisodeInfo?.subServers else currentEpisodeInfo?.dubServers
                    currentServerIndex = servers?.indexOfFirst { it.name == serverName } ?: 0
                }
                isLoadingStream = false
            }
        }
    }

    // Handle playback error - auto try next server
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

    // Show player
    if (showPlayer && currentVideoUrl != null) {
        currentAnime?.let { anime ->
            PlayerScreen(
                videoUrl = currentVideoUrl!!,
                referer = currentReferer,
                subtitleUrl = currentSubtitleUrl,
                currentEpisode = currentEpisode,
                totalEpisodes = totalEpisodes,
                isLoadingStream = isLoadingStream,
                episodeInfo = currentEpisodeInfo,
                currentServerName = currentServerName,
                currentCategory = currentCategory,
                onProgressUpdate = { percentage ->
                    val trackingPercent = viewModel.trackingPercentage.value
                    if (percentage >= trackingPercent && anime.id > 0) {
                        viewModel.updateAnimeProgress(anime.id, currentEpisode)
                    }
                },
                onPreviousEpisode = if (currentEpisode > 1) onPreviousEpisode else null,
                onNextEpisode = if (totalEpisodes == 0 || currentEpisode < totalEpisodes) onNextEpisode else null,
                onServerChange = { server, category -> changeServer(server, category) },
                onPlaybackError = { onPlaybackError() }
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
                    contentColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                ) {
                    val items = listOf("Explore", "Home", "Settings")
                    val icons = listOf(Icons.Default.Explore, Icons.Default.Home, Icons.Default.Settings)

                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    icons[index],
                                    contentDescription = item,
                                    tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            label = { Text(item, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
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
                    userScrollEnabled = true,
                    pageSpacing = 8.dp,
                    beyondViewportPageCount = 1
                ) { page ->
                    when (page) {
                        0 -> ExploreScreen(
                            viewModel = viewModel,
                            onAnimeClick = { },
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            onPlayEpisode = onPlayEpisode,
                            currentlyWatching = currentlyWatching,
                            planningToWatch = planningToWatch,
                            completed = completed,
                            onHold = onHold,
                            dropped = dropped
                        )
                        1 -> HomeScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            onPlayEpisode = onPlayEpisode,
                            onLoginClick = { viewModel.loginWithAniList() }
                        )
                        2 -> SettingsScreen(viewModel = viewModel, isOled = isOled, isLoggedIn = isLoggedIn)
                    }
                }

                // Loading overlay
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

                // Error dialog
                streamError?.let { error ->
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
