package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.blissless.anime.MainViewModel
import com.blissless.anime.ui.components.HomeAnimeHorizontalList
import com.blissless.anime.dialogs.HomeAnimeInfoDialog
import com.blissless.anime.dialogs.HomeAnimeStatusDialog
import com.blissless.anime.dialogs.UserProfileDialog
import com.blissless.anime.ui.components.HomeStatusColors
import com.blissless.anime.ui.components.LoadingSkeleton
import com.blissless.anime.ui.components.RichEpisodeScreen
import com.blissless.anime.ui.components.EpisodeSelectionDialog
import com.blissless.anime.ui.components.SearchOverlay
import com.blissless.anime.ui.components.SectionHeader
import com.blissless.anime.data.models.toDetailedAnimeData
import com.blissless.anime.data.models.StoredFavorite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isLoggedIn: Boolean,
    isOled: Boolean = false,
    showStatusColors: Boolean = true,
    simplifyEpisodeMenu: Boolean = true,
    simplifyAnimeDetails: Boolean = true,
    localFavorites: Map<Int, StoredFavorite> = emptyMap(),
    canAddFavorite: Boolean = true,
    onToggleFavorite: (AnimeMedia) -> Unit = {},
    onPlayerStateChange: (Boolean) -> Unit = {},
    onPlayEpisode: (AnimeMedia, Int) -> Unit = { _, _ -> },
    onLoginClick: () -> Unit = {},
    onShowAnimeDialog: (ExploreAnime) -> Unit = {},
    currentScreenIndex: Int = 0
) {
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()
    val isLoading by viewModel.isLoadingHome.collectAsState()

    val userName by viewModel.userName.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()
    val context = LocalContext.current

    var selectedAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    var showAnimeInfoDialog by remember { mutableStateOf(false) }

    val allListsEmpty = currentlyWatching.isEmpty() && planningToWatch.isEmpty() && completed.isEmpty() && onHold.isEmpty() && dropped.isEmpty()
    var isRefreshing by remember { mutableStateOf(false) }
    val canAddFavoriteLocal = remember(localFavorites) { localFavorites.size < 10 }

    var previousScreenIndex by remember { mutableIntStateOf(currentScreenIndex) }

    val disableMaterialColors by viewModel.disableMaterialColors.collectAsState(initial = false)

    val authToken by viewModel.authToken.collectAsState()
    val actuallyLoggedIn = authToken != null

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
            Column(modifier = Modifier.fillMaxSize().background(if (isOled) Color.Black else MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
                // Header - Corrected vertical padding
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 16.dp)
                        .clickable(enabled = isLoggedIn) { showUserProfileDialog = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoggedIn && userAvatar != null) { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(userAvatar).crossfade(true).build(), contentDescription = "User Avatar", contentScale = ContentScale.Crop, modifier = Modifier.size(42.dp).clip(CircleShape)); Spacer(modifier = Modifier.width(12.dp)) }
                    else if (isLoggedIn) { Icon(Icons.Default.AccountCircle, contentDescription = "User", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(42.dp)); Spacer(modifier = Modifier.width(12.dp)) }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isLoggedIn) (userName ?: "My Anime") else "My Anime", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                        if (isLoggedIn) { Text("Tap to view profile", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (isLoggedIn) { IconButton(onClick = { showSearchOverlay = true }) { Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground) } }
                }

                if (!isLoggedIn) {
                    // Login Card
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)))).padding(24.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
                                Spacer(modifier = Modifier.height(16.dp)); Text("Welcome to Darling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(8.dp)); Text("Sign in with AniList to sync your anime list and track your progress", style = MaterialTheme.typography.bodyMedium, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF02A9FF))) { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Login with AniList", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                Spacer(modifier = Modifier.height(12.dp)); Text("Don't have an account? Sign up for free at anilist.co", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (isLoading && allListsEmpty) {
                    LoadingSkeleton(isOled)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        // Anime Lists - Passing isLoggedIn
                        if (currentlyWatching.isNotEmpty()) { item(key = "header_current") {
                            SectionHeader(
                                title = "Currently Watching",
                                icon = Icons.Default.PlayArrow,
                                count = currentlyWatching.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("CURRENT")
                            )
                        } }
                        if (currentlyWatching.isNotEmpty()) { item(key = "list_current") {
                            HomeAnimeHorizontalList(
                                animeList = currentlyWatching,
                                listType = "CURRENT",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime; showEpisodeSheet = true
                                },
                                onPlayClick = { anime ->
                                    // Logic to check if episode has aired
                                    val nextEp = anime.progress + 1
                                    val released =
                                        anime.latestEpisode?.let { it - 1 } ?: anime.totalEpisodes
                                    if (anime.latestEpisode != null && nextEp > released) {
                                        Toast.makeText(
                                            context,
                                            "Episode not aired yet",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        onPlayEpisode(anime, nextEp)
                                    }
                                },
                                onStatusClick = { anime ->
                                    selectedAnime = anime; showStatusDialog = true
                                },
                                onInfoClick = { anime ->
                                    selectedAnime = anime; showAnimeInfoDialog = true
                                })
                        } }

                        if (planningToWatch.isNotEmpty()) { item(key = "header_planning") {
                            SectionHeader(
                                title = "Planning to Watch",
                                icon = Icons.Default.Bookmark,
                                count = planningToWatch.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("PLANNING")
                            )
                        } }
                        if (planningToWatch.isNotEmpty()) { item(key = "list_planning") {
                            HomeAnimeHorizontalList(
                                animeList = planningToWatch,
                                listType = "PLANNING",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime; showEpisodeSheet = true
                                },
                                onPlayClick = { anime -> onPlayEpisode(anime, 1) },
                                onStatusClick = { anime ->
                                    selectedAnime = anime; showStatusDialog = true
                                },
                                onInfoClick = { anime ->
                                    selectedAnime = anime; showAnimeInfoDialog = true
                                })
                        } }

                        if (completed.isNotEmpty()) { item(key = "header_completed") {
                            SectionHeader(
                                title = "Completed",
                                icon = Icons.Default.Check,
                                count = completed.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("COMPLETED")
                            )
                        } }
                        if (completed.isNotEmpty()) { item(key = "list_completed") {
                            HomeAnimeHorizontalList(
                                animeList = completed,
                                listType = "COMPLETED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime; showEpisodeSheet = true
                                },
                                onPlayClick = { anime -> onPlayEpisode(anime, 1) },
                                onStatusClick = { anime ->
                                    selectedAnime = anime; showStatusDialog = true
                                },
                                onInfoClick = { anime ->
                                    selectedAnime = anime; showAnimeInfoDialog = true
                                })
                        } }

                        if (onHold.isNotEmpty()) { item(key = "header_onhold") {
                            SectionHeader(
                                title = "On Hold",
                                icon = Icons.Default.Pause,
                                count = onHold.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("PAUSED")
                            )
                        } }
                        if (onHold.isNotEmpty()) { item(key = "list_onhold") {
                            HomeAnimeHorizontalList(
                                animeList = onHold,
                                listType = "PAUSED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime; showEpisodeSheet = true
                                },
                                onPlayClick = { anime ->
                                    // Logic to check if episode has aired
                                    val nextEp = anime.progress + 1
                                    val released =
                                        anime.latestEpisode?.let { it - 1 } ?: anime.totalEpisodes
                                    if (anime.latestEpisode != null && nextEp > released) {
                                        Toast.makeText(
                                            context,
                                            "Episode not aired yet",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        onPlayEpisode(anime, nextEp)
                                    }
                                },
                                onStatusClick = { anime ->
                                    selectedAnime = anime; showStatusDialog = true
                                },
                                onInfoClick = { anime ->
                                    selectedAnime = anime; showAnimeInfoDialog = true
                                })
                        } }

                        if (dropped.isNotEmpty()) { item(key = "header_dropped") {
                            SectionHeader(
                                title = "Dropped",
                                icon = Icons.Default.Delete,
                                count = dropped.size,
                                isOled = isOled,
                                iconTint = HomeStatusColors.getColor("DROPPED")
                            )
                        } }
                        if (dropped.isNotEmpty()) { item(key = "list_dropped") {
                            HomeAnimeHorizontalList(
                                animeList = dropped,
                                listType = "DROPPED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                isLoggedIn = isLoggedIn,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime; showEpisodeSheet = true
                                },
                                onPlayClick = { anime -> onPlayEpisode(anime, 1) },
                                onStatusClick = { anime ->
                                    selectedAnime = anime; showStatusDialog = true
                                },
                                onInfoClick = { anime ->
                                    selectedAnime = anime; showAnimeInfoDialog = true
                                })
                        } }

                        if (allListsEmpty) {
                            item(key = "empty_state") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Card(colors = CardDefaults.cardColors(containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant)) {
                                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Your lists are empty", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                                            Text("Check out the Explore tab to discover anime!", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSearchOverlay,
            enter = slideInVertically(animationSpec = tween(durationMillis = 250), initialOffsetY = { -it }) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(animationSpec = tween(durationMillis = 250), targetOffsetY = { -it }) + fadeOut(animationSpec = tween(250))
        ) {
            SearchOverlay(
                viewModel = viewModel,
                isOled = isOled,
                simplifyAnimeDetails = simplifyAnimeDetails,
                currentlyWatching = currentlyWatching,
                planningToWatch = planningToWatch,
                completed = completed,
                onHold = onHold,
                dropped = dropped,
                localFavorites = localFavorites,
                onToggleFavorite = onToggleFavorite,
                onClose = { showSearchOverlay = false },
                onPlayEpisode = onPlayEpisode
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
                onEpisodeSelect = { episode ->
                    onPlayEpisode(selectedAnime!!, episode)
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
                onEpisodeSelect = { episode ->
                    onPlayEpisode(selectedAnime!!, episode)
                    showEpisodeSheet = false
                }
            )
        }
    }

    if (showStatusDialog && selectedAnime != null) {
        val isAnimeFavorite = localFavorites.containsKey(selectedAnime!!.id)
        HomeAnimeStatusDialog(
            anime = selectedAnime!!,
            isOled = isOled,
            showStatusColors = showStatusColors,
            isFavorite = isAnimeFavorite,
            canAddFavorite = canAddFavoriteLocal || isAnimeFavorite,
            onToggleFavorite = { onToggleFavorite(selectedAnime!!) },
            onDismiss = { showStatusDialog = false },
            onRemove = {
                viewModel.removeAnimeFromList(selectedAnime!!.id); showStatusDialog =
                false; viewModel.refreshHome()
            },
            onUpdate = { status, progress ->
                if (progress != null) viewModel.updateAnimeStatus(
                    selectedAnime!!.id,
                    status,
                    progress
                ) else viewModel.updateAnimeStatus(selectedAnime!!.id, status); showStatusDialog =
                false; viewModel.refreshHome()
            })
    }

    if (showAnimeInfoDialog && selectedAnime != null) {
        val exploreAnime = ExploreAnime(id = selectedAnime!!.id, title = selectedAnime!!.title, cover = selectedAnime!!.cover, banner = selectedAnime!!.banner, episodes = selectedAnime!!.totalEpisodes, latestEpisode = selectedAnime!!.latestEpisode, averageScore = selectedAnime!!.averageScore, genres = selectedAnime!!.genres, year = selectedAnime!!.year)
        val isAnimeFavorite = localFavorites.containsKey(selectedAnime!!.id)
        if (simplifyAnimeDetails) {
            HomeAnimeInfoDialog(
                anime = selectedAnime!!,
                isOled = isOled,
                isFavorite = isAnimeFavorite,
                canAddFavorite = canAddFavoriteLocal || isAnimeFavorite,
                onToggleFavorite = { onToggleFavorite(selectedAnime!!) },
                onDismiss = { showAnimeInfoDialog = false },
                onPlayEpisode = { episode ->
                    onPlayEpisode(
                        selectedAnime!!,
                        episode
                    ); showAnimeInfoDialog = false
                },
                onUpdateStatus = { status ->
                    viewModel.updateAnimeStatus(
                        selectedAnime!!.id,
                        status
                    ); selectedAnime = selectedAnime!!.copy(listStatus = status)
                },
                onRemove = {
                    viewModel.removeAnimeFromList(selectedAnime!!.id); showAnimeInfoDialog = false
                })
        } else {
            DetailedAnimeScreen(anime = exploreAnime.toDetailedAnimeData(), viewModel = viewModel, isOled = isOled, currentStatus = selectedAnime!!.listStatus, isFavorite = isAnimeFavorite, onToggleFavorite = { _ -> onToggleFavorite(selectedAnime!!) }, onDismiss = { showAnimeInfoDialog = false }, onPlayEpisode = { episode -> onPlayEpisode(selectedAnime!!, episode); showAnimeInfoDialog = false }, onUpdateStatus = { status -> if (status != null) viewModel.updateAnimeStatus(selectedAnime!!.id, status) }, onRemove = { viewModel.removeAnimeFromList(selectedAnime!!.id); showAnimeInfoDialog = false }, isLoggedIn = true)
        }
    }

    if (showUserProfileDialog) {
        UserProfileDialog(
            viewModel = viewModel,
            isOled = isOled,
            onDismiss = { showUserProfileDialog = false },
            onShowAnimeDialog = onShowAnimeDialog,
            planningToWatch = planningToWatch,
            onHold = onHold,
            dropped = dropped
        )
    }

    LaunchedEffect(isLoading) { if (!isLoading && isRefreshing) isRefreshing = false }
}
