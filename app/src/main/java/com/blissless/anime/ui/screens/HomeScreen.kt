package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.data.models.StoredFavorite
import com.blissless.anime.MainViewModel
import com.blissless.anime.ui.components.HomeAnimeCardBounds
import com.blissless.anime.ui.components.HomeAnimeHorizontalList
import com.blissless.anime.dialogs.HomeAnimeStatusDialog
import com.blissless.anime.dialogs.OfflineFavoritesDialog
import com.blissless.anime.dialogs.UserProfileScreen
import com.blissless.anime.dialogs.ExploreAnimeDialog
import com.blissless.anime.ui.components.HomeStatusColors
import com.blissless.anime.ui.components.LoadingSkeleton
import com.blissless.anime.ui.components.RichEpisodeScreen
import com.blissless.anime.ui.components.EpisodeSelectionDialog
import com.blissless.anime.ui.components.SearchOverlay
import com.blissless.anime.ui.components.SectionHeader
import com.blissless.anime.data.models.toDetailedAnimeData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isLoggedIn: Boolean,
    isOled: Boolean = false,
    showStatusColors: Boolean = true,
    simplifyEpisodeMenu: Boolean = true,
    hideAdultContent: Boolean = false,
    favoriteIds: Set<Int> = emptySet(),
    onToggleLocalFavorite: (Int) -> Unit = {},
    onToggleFavorite: (AnimeMedia) -> Unit = {},
    onPlayerStateChange: (Boolean) -> Unit = {},
    onPlayEpisode: (AnimeMedia, Int, String?) -> Unit = { _, _, _ -> },
    onLoginClick: () -> Unit = {},
    onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit = { _, _ -> },
    onShowDetailedAnimeFromMal: (Int) -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onViewAllCast: (Int, String) -> Unit = { _, _ -> },
    onViewAllStaff: (Int, String) -> Unit = { _, _ -> },
    currentScreenIndex: Int = 0,
    playbackPositions: Map<String, Long> = emptyMap()
) {    
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()
    val isLoading by viewModel.isLoadingHome.collectAsState()

    val offlineCurrentlyWatching by viewModel.offlineCurrentlyWatching.collectAsState()
    val offlinePlanningToWatch by viewModel.offlinePlanningToWatch.collectAsState()
    val offlineCompleted by viewModel.offlineCompleted.collectAsState()
    val offlineOnHold by viewModel.offlineOnHold.collectAsState()
    val offlineDropped by viewModel.offlineDropped.collectAsState()

    val localFavorites by viewModel.localFavorites.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()

    val userName by viewModel.userName.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()
    val context = LocalContext.current

    var selectedAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showOfflineFavoritesDialog by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    var showDetailedAnimeScreen by remember { mutableStateOf(false) }
    
    // Track first anime for back navigation
    var firstAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    
    // Card bounds for shared element transition
    var currentCardBounds by remember { mutableStateOf<MainViewModel.CardBounds?>(null) }

    val effectiveCurrentlyWatching = if (isLoggedIn) currentlyWatching else offlineCurrentlyWatching
    val effectivePlanningToWatch = if (isLoggedIn) planningToWatch else offlinePlanningToWatch
    val effectiveCompleted = if (isLoggedIn) completed else offlineCompleted
    val effectiveOnHold = if (isLoggedIn) onHold else offlineOnHold
    val effectiveDropped = if (isLoggedIn) dropped else offlineDropped

    val allListsEmpty = effectiveCurrentlyWatching.isEmpty() && effectivePlanningToWatch.isEmpty() && effectiveCompleted.isEmpty() && effectiveOnHold.isEmpty() && effectiveDropped.isEmpty()

    val hasOfflineContent = !isLoggedIn && (localFavorites.isNotEmpty() || viewModel.localAnimeStatus.value.isNotEmpty())
    val showWelcomeCard = !isLoggedIn && allListsEmpty && !hasOfflineContent

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var previousScreenIndex by remember { mutableIntStateOf(currentScreenIndex) }

    // Force recomposition when lists change by tracking a version counter
    var listVersion by remember { mutableIntStateOf(0) }
    
    // Update listVersion when lists change to trigger recomposition
    LaunchedEffect(currentlyWatching, planningToWatch, completed, onHold, dropped, localAnimeStatus) {
        listVersion++
    }

    val homeScrollState = rememberScrollState()

    val disableMaterialColors by viewModel.disableMaterialColors.collectAsState(initial = false)

    val authToken by viewModel.authToken.collectAsState()
    val actuallyLoggedIn = authToken != null
    
    val apiError by viewModel.apiError.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    LaunchedEffect(currentScreenIndex) {
        if (currentScreenIndex != previousScreenIndex) {
            showSearchOverlay = false
            previousScreenIndex = currentScreenIndex
        }
    }

    androidx.activity.compose.BackHandler(enabled = showSearchOverlay) { showSearchOverlay = false }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; viewModel.refreshHome() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Error/Offline Banner
                if (apiError != null || isOffline) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isOffline) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isOffline) Icons.Default.SignalWifiOff else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isOffline) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isOffline) "No internet connection" else "AniList is currently unavailable",
                                color = if (isOffline) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                if (isLoggedIn) {
                    // Header - profile area in rounded card, search button separate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            modifier = Modifier.offset(x = (-4).dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOled) Color(0xFF1A1A1A).copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                            ),
                            onClick = { if (isLoggedIn) showUserProfileDialog = true },
                            enabled = isLoggedIn
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLoggedIn && userAvatar != null) { 
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(userAvatar).crossfade(true).build(), 
                                        contentDescription = "User Avatar", 
                                        contentScale = ContentScale.Crop, 
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                    ); 
                                    Spacer(modifier = Modifier.width(8.dp)) 
                                }
                                else if (isLoggedIn) { 
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.AccountCircle, contentDescription = "User", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(40.dp)); 
                                    Spacer(modifier = Modifier.width(8.dp)) 
                                }

                                Column {
                                    if (isLoggedIn) {
                                        Text(userName ?: "My Anime", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                                        Text("Tap to view profile", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (isLoggedIn) { 
                            Spacer(modifier = Modifier.width(8.dp))
                            Card(
                                modifier = Modifier.height(IntrinsicSize.Min),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF1A1A1A).copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                ),
                                onClick = { showSearchOverlay = true }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Search", style = MaterialTheme.typography.bodyLarge, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }

                if (!isLoggedIn) {
                    // Header - same style as logged in, but with app name "Darling"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            modifier = Modifier.offset(x = (-4).dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOled) Color(0xFF1A1A1A).copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                            ),
                            onClick = { showOfflineFavoritesDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(4.dp))
                                AsyncImage(
                                    model = com.blissless.anime.R.mipmap.ic_launcher_round,
                                    contentDescription = "App",
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Darling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                                    Text("${localFavorites.size} favorites", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOled) Color(0xFF1A1A1A).copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                            ),
                            onClick = { showSearchOverlay = true }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Search", style = MaterialTheme.typography.bodyLarge, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                if (showWelcomeCard) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (isOled) Color(0xFF1A1A1A).copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))) {
                            Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)))).padding(24.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AsyncImage(model = com.blissless.anime.R.mipmap.ic_launcher_round, contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape))
                                    Spacer(modifier = Modifier.height(16.dp)); Text("Welcome to Darling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp)); Text("Your lists are empty. Sign in with AniList to sync your anime list and track your progress, or start exploring!", style = MaterialTheme.typography.bodyMedium, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { 
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data("https://anilist.co/img/icons/favicon-32x32.png")
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "AniList",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Login with AniList", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) 
                                    }
                                    Spacer(modifier = Modifier.height(12.dp)); Text("Don't have an account? Sign up for free at anilist.co", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (isLoading && allListsEmpty) {
                    LoadingSkeleton(isOled)
                } else {
                    val onAnimeClick: (AnimeMedia, com.blissless.anime.ui.components.HomeAnimeCardBounds?) -> Unit = { anime, _ -> selectedAnime = anime; showEpisodeSheet = true }
                    val onPlayClick: (AnimeMedia, String) -> Unit = { anime, listType ->
                        if (listType == "CURRENT") {
                            val nextEp = anime.progress + 1
                            val released = anime.latestEpisode?.let { it - 1 } ?: anime.totalEpisodes
                            if (anime.latestEpisode != null && nextEp > released) {
                                Toast.makeText(context, "Episode not aired yet", Toast.LENGTH_SHORT).show()
                            } else {
                                onPlayEpisode(anime, nextEp, null)
                            }
                        } else {
                            onPlayEpisode(anime, 1, null)
                        }
                    }
                    val onStatusClick: (AnimeMedia) -> Unit = { anime -> selectedAnime = anime; showStatusDialog = true }
                    val onInfoClick: (AnimeMedia, com.blissless.anime.ui.components.HomeAnimeCardBounds?) -> Unit = { anime, bounds ->
                        val cardBounds = bounds?.let {
                            MainViewModel.CardBounds(anime.id, anime.cover, it.bounds)
                        }
                        currentCardBounds = cardBounds
                        viewModel.clearExploreAnimeCardBounds()
                        selectedAnime = anime
                        if (firstAnime == null) firstAnime = anime
                        showDetailedAnimeScreen = true
                    }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(homeScrollState),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (effectiveCurrentlyWatching.isNotEmpty()) {
                            SectionHeader(
                                title = "Currently Watching",
                                icon = Icons.Default.PlayArrow,
                                count = effectiveCurrentlyWatching.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("CURRENT")
                            )
                            HomeAnimeHorizontalList(
                                animeList = effectiveCurrentlyWatching,
                                listType = "CURRENT",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
                                onAnimeClick = onAnimeClick,
                                onPlayClick = { anime -> onPlayClick(anime, "CURRENT") },
                                onStatusClick = onStatusClick,
                                onInfoClick = onInfoClick,
                                listIndex = 0,
                                screenKey = "home",
                                isVisible = currentScreenIndex == 0,
                                viewModel = viewModel
                            )
                        }

                        if (effectivePlanningToWatch.isNotEmpty()) {
                            SectionHeader(
                                title = "Planning to Watch",
                                icon = Icons.Default.Bookmark,
                                count = effectivePlanningToWatch.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("PLANNING")
                            )
                            HomeAnimeHorizontalList(
                                animeList = effectivePlanningToWatch,
                                listType = "PLANNING",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
                                onAnimeClick = onAnimeClick,
                                onPlayClick = { anime -> onPlayClick(anime, "PLANNING") },
                                onStatusClick = onStatusClick,
                                onInfoClick = onInfoClick,
                                listIndex = 1,
                                screenKey = "home",
                                isVisible = currentScreenIndex == 0,
                                viewModel = viewModel
                            )
                        }

                        if (effectiveCompleted.isNotEmpty()) {
                            SectionHeader(
                                title = "Completed",
                                icon = Icons.Default.Check,
                                count = effectiveCompleted.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("COMPLETED")
                            )
                            HomeAnimeHorizontalList(
                                animeList = effectiveCompleted,
                                listType = "COMPLETED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
                                onAnimeClick = onAnimeClick,
                                onPlayClick = { anime -> onPlayClick(anime, "COMPLETED") },
                                onStatusClick = onStatusClick,
                                onInfoClick = onInfoClick,
                                listIndex = 2,
                                screenKey = "home",
                                isVisible = currentScreenIndex == 0,
                                viewModel = viewModel
                            )
                        }

                        if (effectiveOnHold.isNotEmpty()) {
                            SectionHeader(
                                title = "On Hold",
                                icon = Icons.Default.Pause,
                                count = effectiveOnHold.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("PAUSED")
                            )
                            HomeAnimeHorizontalList(
                                animeList = effectiveOnHold,
                                listType = "PAUSED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
                                onAnimeClick = onAnimeClick,
                                onPlayClick = { anime -> onPlayClick(anime, "PAUSED") },
                                onStatusClick = onStatusClick,
                                onInfoClick = onInfoClick,
                                listIndex = 3,
                                screenKey = "home",
                                isVisible = currentScreenIndex == 0,
                                viewModel = viewModel
                            )
                        }

                        if (effectiveDropped.isNotEmpty()) {
                            SectionHeader(
                                title = "Dropped",
                                icon = Icons.Default.Delete,
                                count = effectiveDropped.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("DROPPED")
                            )
                            HomeAnimeHorizontalList(
                                animeList = effectiveDropped,
                                listType = "DROPPED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
                                onAnimeClick = onAnimeClick,
                                onPlayClick = { anime -> onPlayClick(anime, "DROPPED") },
                                onStatusClick = onStatusClick,
                                onInfoClick = onInfoClick,
                                listIndex = 4,
                                screenKey = "home",
                                isVisible = currentScreenIndex == 0,
                                viewModel = viewModel
                            )
                        }

                        if (allListsEmpty && !showWelcomeCard) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Card(colors = CardDefaults.cardColors(containerColor = if (isOled) Color(0xFF1A1A1A).copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))) {
                                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Your lists are empty", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                                        Text("Check out the Explore tab to discover anime!", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSearchOverlay,
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                ),
                initialOffsetY = { fullHeight -> -(fullHeight * 0.1f).toInt() }
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = 0,
                    easing = FastOutSlowInEasing
                )
            ),
            exit = slideOutVertically(
                animationSpec = tween(
                    durationMillis = 250,
                    easing = FastOutSlowInEasing
                ),
                targetOffsetY = { fullHeight -> -(fullHeight * 0.1f).toInt() }
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 250,
                    easing = FastOutSlowInEasing
                )
            )
        ) {
            SearchOverlay(
                viewModel = viewModel,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                hideAdultContent = hideAdultContent,
                currentlyWatching = currentlyWatching,
                planningToWatch = planningToWatch,
                completed = completed,
                onHold = onHold,
                dropped = dropped,
                localAnimeStatus = viewModel.localAnimeStatus.value,
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                onClose = { showSearchOverlay = false },
                onPlayEpisode = onPlayEpisode,
                onCharacterClick = onCharacterClick,
                onStaffClick = onStaffClick,
                onViewAllCast = onViewAllCast,
                onViewAllStaff = onViewAllStaff
            )
        }
    }

    // Dialogs
    if (showEpisodeSheet && selectedAnime != null) {
        if (simplifyEpisodeMenu) {
            EpisodeSelectionDialog(
                anime = selectedAnime!!,
                isOled = isOled,
                disableMaterialColors = disableMaterialColors,
                onDismiss = { showEpisodeSheet = false },
                onEpisodeSelect = { episode, title ->
                    onPlayEpisode(selectedAnime!!, episode, title)
                    showEpisodeSheet = false
                }
            )
        } else {
            RichEpisodeScreen(
                anime = selectedAnime!!,
                viewModel = viewModel,
                isOled = isOled,
                disableMaterialColors = disableMaterialColors,
                onDismiss = { showEpisodeSheet = false },
                onEpisodeSelect = { episode, title ->
                    onPlayEpisode(selectedAnime!!, episode, title)
                    showEpisodeSheet = false
                }
            )
        }
    }

    if (showStatusDialog && selectedAnime != null) {
        val isAnimeFavorite = favoriteIds.contains(selectedAnime!!.id)
        HomeAnimeStatusDialog(
            anime = selectedAnime!!,
            isOled = isOled,
            showStatusColors = showStatusColors,
            isFavorite = isAnimeFavorite,
            onToggleFavorite = { onToggleFavorite(selectedAnime!!) },
            onDismiss = { showStatusDialog = false },
            onRemove = {
                viewModel.removeAnimeFromList(selectedAnime!!.id)
                showStatusDialog = false
            },
            onUpdate = { status, progress ->
                if (progress != null) viewModel.updateAnimeStatus(
                    selectedAnime!!.id,
                    status,
                    progress
                ) else viewModel.updateAnimeStatus(selectedAnime!!.id, status)
                showStatusDialog = false
            })
    }

    // Collect card bounds from ViewModel
    val viewModelCardBounds by viewModel.exploreAnimeCardBounds.collectAsState()
    val viewModelHomeCardBounds by viewModel.homeAnimeCardBounds.collectAsState()
    val effectiveCardBounds = viewModelHomeCardBounds ?: viewModelCardBounds
    
    LaunchedEffect(effectiveCardBounds) {
        if (effectiveCardBounds != null && currentCardBounds == null && showDetailedAnimeScreen) {
            currentCardBounds = effectiveCardBounds
        }
    }

    if (showDetailedAnimeScreen && selectedAnime != null) {
        val detailedAnimeData = selectedAnime!!.toDetailedAnimeData()
        val currentStatus by remember(listVersion, selectedAnime!!.id) {
            derivedStateOf { selectedAnime?.listStatus }
        }
        DetailedAnimeScreen(
            anime = detailedAnimeData,
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = currentStatus,
            isLoggedIn = isLoggedIn,
            isFavorite = favoriteIds.contains(selectedAnime!!.id),
            initialCardBounds = currentCardBounds,
            onDismiss = {
                currentCardBounds = null
                // Go back to first anime if navigated, otherwise close
                if (firstAnime != null && selectedAnime?.id != firstAnime?.id) {
                    selectedAnime = firstAnime
                } else {
                    showDetailedAnimeScreen = false
                    firstAnime = null
                }
            },
            onSwipeToClose = {
                currentCardBounds = null
                showDetailedAnimeScreen = false
                firstAnime = null
            },
            onPlayEpisode = { episode, _ ->
                onPlayEpisode(selectedAnime!!, episode, null)
                showDetailedAnimeScreen = false
            },
            onUpdateStatus = { status ->
                if (status != null) {
                    viewModel.addExploreAnimeToList(
                        ExploreAnime(
                            id = selectedAnime!!.id,
                            title = selectedAnime!!.title,
                            cover = selectedAnime!!.cover,
                            banner = selectedAnime!!.banner,
                            episodes = selectedAnime!!.totalEpisodes,
                            latestEpisode = selectedAnime!!.latestEpisode,
                            averageScore = selectedAnime!!.averageScore,
                            genres = selectedAnime!!.genres,
                            year = selectedAnime!!.year,
                            format = selectedAnime!!.format
                        ),
                        status
                    )
                }
            },
            onRemove = {
                viewModel.removeAnimeFromList(selectedAnime!!.id)
                showDetailedAnimeScreen = false
            },
            onToggleFavorite = { _ ->
                onToggleFavorite(selectedAnime!!)
            },
            onLoginClick = onLoginClick,
            onRelationClick = { relation ->
                scope.launch {
                    try {
                        currentCardBounds = null
                        viewModel.clearHomeAnimeCardBounds()
                        val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                        if (detailedData != null) {
                            selectedAnime = AnimeMedia(
                                id = detailedData.id,
                                title = detailedData.title,
                                titleEnglish = detailedData.titleEnglish,
                                cover = detailedData.cover,
                                banner = detailedData.banner,
                                progress = selectedAnime!!.progress,
                                totalEpisodes = detailedData.episodes,
                                latestEpisode = detailedData.latestEpisode,
                                status = detailedData.status ?: "",
                                averageScore = detailedData.averageScore,
                                genres = detailedData.genres,
                                listStatus = selectedAnime!!.listStatus,
                                listEntryId = selectedAnime!!.listEntryId,
                                year = detailedData.year,
                                malId = detailedData.malId
                            )
                        } else {
                            Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCharacterClick = onCharacterClick,
            onStaffClick = onStaffClick,
            onViewAllCast = { onViewAllCast(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllStaff = { onViewAllStaff(selectedAnime!!.id, selectedAnime!!.title) }
        )
    }

    if (showOfflineFavoritesDialog) {
        OfflineFavoritesDialog(
            favorites = localFavorites,
            isOled = isOled,
            onDismiss = { showOfflineFavoritesDialog = false },
            onAnimeClick = { anime -> onShowAnimeDialog(anime, null) },
            onRemoveFavorite = { id -> viewModel.toggleLocalFavorite(id) }
        )
    }

    if (showUserProfileDialog) {
        UserProfileScreen(
            viewModel = viewModel,
            isOled = isOled,
            onDismiss = { showUserProfileDialog = false },
            onShowAnimeDialog = { anime, _ -> onShowAnimeDialog(anime, null) },
            onShowDetailedAnimeFromMal = onShowDetailedAnimeFromMal
        )
    }

    // Stop refreshing when loading completes or after timeout
    LaunchedEffect(isLoading, isRefreshing) {
        if (isRefreshing) {
            // Use a timeout to ensure refreshing stops even if loading state gets stuck
            kotlinx.coroutines.delay(15000)
            isRefreshing = false
        }
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }
}
