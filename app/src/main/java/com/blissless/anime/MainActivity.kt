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
import com.blissless.anime.ui.screens.ExploreScreen
import com.blissless.anime.ui.screens.HomeScreen
import com.blissless.anime.ui.screens.PlayerScreen
import com.blissless.anime.ui.screens.SettingsScreen
import com.blissless.anime.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        mainViewModel.init(applicationContext)
        handleAuthCallback(intent)

        setContent {
            val isOled by mainViewModel.isOled.collectAsState(initial = false)
            val token by mainViewModel.authToken.collectAsState(initial = null)
            val isLoggedIn = token != null

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

    // Helper function to load and play an episode
    fun loadAndPlayEpisode(anime: AnimeMedia, episode: Int) {
        currentAnime = anime
        currentEpisode = episode
        totalEpisodes = anime.totalEpisodes
        isLoadingStream = true
        streamError = null
        showPlayer = false

        scope.launch {
            val result = viewModel.getStreamLink(anime.title, episode)

            if (result != null) {
                Log.d("MainActivity", "Stream URL: ${result.url.take(50)}...")
                currentVideoUrl = result.url
                currentReferer = result.headers?.get("Referer") ?: "https://megacloud.tv/"
                currentSubtitleUrl = result.subtitleUrl
                showPlayer = true
            } else {
                Log.e("MainActivity", "Failed to get stream")
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

    // Handle previous episode - scrape on demand
    val onPreviousEpisode: () -> Unit = {
        currentAnime?.let { anime ->
            if (currentEpisode > 1) {
                val prevEp = currentEpisode - 1
                isLoadingStream = true

                scope.launch {
                    val result = viewModel.getStreamLink(anime.title, prevEp)

                    if (result != null) {
                        Log.d("MainActivity", "Previous episode stream URL: ${result.url.take(50)}...")
                        currentVideoUrl = result.url
                        currentReferer = result.headers?.get("Referer") ?: "https://megacloud.tv/"
                        currentSubtitleUrl = result.subtitleUrl
                        currentEpisode = prevEp
                    } else {
                        Log.e("MainActivity", "Failed to get stream for previous episode")
                        streamError = "Could not find stream for episode $prevEp"
                    }

                    isLoadingStream = false
                }
            }
        }
    }

    // Handle next episode - scrape on demand
    val onNextEpisode: () -> Unit = {
        currentAnime?.let { anime ->
            // Allow next episode if we don't know total (0) or if there are more episodes
            if (totalEpisodes == 0 || currentEpisode < totalEpisodes) {
                val nextEp = currentEpisode + 1
                isLoadingStream = true

                scope.launch {
                    val result = viewModel.getStreamLink(anime.title, nextEp)

                    if (result != null) {
                        Log.d("MainActivity", "Next episode stream URL: ${result.url.take(50)}...")
                        currentVideoUrl = result.url
                        currentReferer = result.headers?.get("Referer") ?: "https://megacloud.tv/"
                        currentSubtitleUrl = result.subtitleUrl
                        currentEpisode = nextEp
                    } else {
                        Log.e("MainActivity", "Failed to get stream for next episode")
                        streamError = "Could not find stream for episode $nextEp"
                    }

                    isLoadingStream = false
                }
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
                onProgressUpdate = { percentage ->
                    val trackingPercent = viewModel.trackingPercentage.value
                    if (percentage >= trackingPercent && anime.id > 0) {
                        viewModel.updateAnimeProgress(anime.id, currentEpisode)
                    }
                },
                onPreviousEpisode = if (currentEpisode > 1) onPreviousEpisode else null,
                onNextEpisode = if (totalEpisodes == 0 || currentEpisode < totalEpisodes) onNextEpisode else null
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
                HorizontalPager(state = pagerState, userScrollEnabled = true) { page ->
                    when (page) {
                        0 -> ExploreScreen(
                            viewModel = viewModel,
                            onAnimeClick = { },
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            onPlayEpisode = onPlayEpisode,
                            currentlyWatching = currentlyWatching,
                            planningToWatch = planningToWatch
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