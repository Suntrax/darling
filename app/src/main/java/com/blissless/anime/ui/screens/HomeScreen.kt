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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.data.models.StoredFavorite
import com.blissless.anime.MainViewModel
import com.blissless.anime.ui.components.HomeAnimeHorizontalList
import com.blissless.anime.dialogs.HomeAnimeInfoDialog
import com.blissless.anime.dialogs.HomeAnimeStatusDialog
import com.blissless.anime.dialogs.UserProfileDialog
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
    simplifyAnimeDetails: Boolean = true,
    hideAdultContent: Boolean = false,
    favoriteIds: Set<Int> = emptySet(),
    localFavorites: Map<Int, com.blissless.anime.data.models.StoredFavorite> = emptyMap(),
    onToggleLocalFavorite: (Int) -> Unit = {},
    onToggleFavorite: (AnimeMedia) -> Unit = {},
    onPlayerStateChange: (Boolean) -> Unit = {},
    onPlayEpisode: (AnimeMedia, Int) -> Unit = { _, _ -> },
    onLoginClick: () -> Unit = {},
    onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit = { _, _ -> },
    currentScreenIndex: Int = 0,
    playbackPositions: Map<String, Long> = emptyMap()
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
    var showDetailedAnimeScreen by remember { mutableStateOf(false) }
    
    // Track first anime for back navigation
    var firstAnime by remember { mutableStateOf<AnimeMedia?>(null) }

    val allListsEmpty = currentlyWatching.isEmpty() && planningToWatch.isEmpty() && completed.isEmpty() && onHold.isEmpty() && dropped.isEmpty()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                if (isLoggedIn) {
                    // Header - profile area in rounded card, search button separate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .offset(x = (-4).dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            onClick = { if (isLoggedIn) showUserProfileDialog = true },
                            enabled = isLoggedIn
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 0.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLoggedIn && userAvatar != null) { 
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(userAvatar).crossfade(true).build(), 
                                        contentDescription = "User Avatar", 
                                        contentScale = ContentScale.Crop, 
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                    ); 
                                    Spacer(modifier = Modifier.width(12.dp)) 
                                }
                                else if (isLoggedIn) { 
                                    Icon(Icons.Default.AccountCircle, contentDescription = "User", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(40.dp)); 
                                    Spacer(modifier = Modifier.width(12.dp)) 
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    if (isLoggedIn) {
                                        Text(userName ?: "My Anime", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                                        Text("Tap to view profile", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (isLoggedIn) { 
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { showSearchOverlay = true }) { 
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground) 
                            } 
                        }
                    }
                }

                if (!isLoggedIn) {
                    // Offline header with app icon and favorites count
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = com.blissless.anime.R.mipmap.ic_launcher_round,
                            contentDescription = "App",
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("My Anime", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                            Text("${localFavorites.size} favorites", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showSearchOverlay = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    // Login Card - centered on screen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)))).padding(24.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(model = com.blissless.anime.R.mipmap.ic_launcher_round, contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape))
                                Spacer(modifier = Modifier.height(16.dp)); Text("Welcome to Darling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(8.dp)); Text("Sign in with AniList to sync your anime list and track your progress", style = MaterialTheme.typography.bodyMedium, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { 
                                    Icon(
                                        painter = painterResource(id = com.blissless.anime.R.drawable.ic_anilist),
                                        contentDescription = "AniList",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Login with AniList", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) 
                                }
                                Spacer(modifier = Modifier.height(12.dp)); Text("Don't have an account? Sign up for free at anilist.co", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
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
                                    selectedAnime = anime
                                    if (simplifyAnimeDetails) {
                                        showAnimeInfoDialog = true
                                    } else {
                                        if (firstAnime == null) firstAnime = anime
                                        showDetailedAnimeScreen = true
                                    }
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
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime; showEpisodeSheet = true
                                },
                                onPlayClick = { anime -> onPlayEpisode(anime, 1) },
                                onStatusClick = { anime ->
                                    selectedAnime = anime; showStatusDialog = true
                                },
                                onInfoClick = { anime ->
                                    selectedAnime = anime
                                    if (simplifyAnimeDetails) {
                                        showAnimeInfoDialog = true
                                    } else {
                                        if (firstAnime == null) firstAnime = anime
                                        showDetailedAnimeScreen = true
                                    }
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
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime; showEpisodeSheet = true
                                },
                                onPlayClick = { anime -> onPlayEpisode(anime, 1) },
                                onStatusClick = { anime ->
                                    selectedAnime = anime; showStatusDialog = true
                                },
                                onInfoClick = { anime ->
                                    selectedAnime = anime
                                    if (simplifyAnimeDetails) {
                                        showAnimeInfoDialog = true
                                    } else {
                                        if (firstAnime == null) firstAnime = anime
                                        showDetailedAnimeScreen = true
                                    }
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
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
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
                                    selectedAnime = anime
                                    if (simplifyAnimeDetails) {
                                        showAnimeInfoDialog = true
                                    } else {
                                        showDetailedAnimeScreen = true
                                    }
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
                                playbackPositions = playbackPositions,
                                disableMaterialColors = disableMaterialColors,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime; showEpisodeSheet = true
                                },
                                onPlayClick = { anime -> onPlayEpisode(anime, 1) },
                                onStatusClick = { anime ->
                                    selectedAnime = anime; showStatusDialog = true
                                },
                                onInfoClick = { anime ->
                                    selectedAnime = anime
                                    if (simplifyAnimeDetails) {
                                        showAnimeInfoDialog = true
                                    } else {
                                        if (firstAnime == null) firstAnime = anime
                                        showDetailedAnimeScreen = true
                                    }
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
                isLoggedIn = isLoggedIn,
                simplifyAnimeDetails = simplifyAnimeDetails,
                currentlyWatching = currentlyWatching,
                planningToWatch = planningToWatch,
                completed = completed,
                onHold = onHold,
                dropped = dropped,
                favoriteIds = favoriteIds,
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
        val isAnimeFavorite = favoriteIds.contains(selectedAnime!!.id)
        HomeAnimeStatusDialog(
            anime = selectedAnime!!,
            isOled = isOled,
            showStatusColors = showStatusColors,
            isFavorite = isAnimeFavorite,
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
        // Set first anime on first open
        if (firstAnime == null) {
            firstAnime = selectedAnime
        }
        
        val exploreAnime = ExploreAnime(id = selectedAnime!!.id, title = selectedAnime!!.title, cover = selectedAnime!!.cover, banner = selectedAnime!!.banner, episodes = selectedAnime!!.totalEpisodes, latestEpisode = selectedAnime!!.latestEpisode, averageScore = selectedAnime!!.averageScore, genres = selectedAnime!!.genres, year = selectedAnime!!.year, format = selectedAnime!!.format)
        val isAnimeFavorite = favoriteIds.contains(selectedAnime!!.id)
        ExploreAnimeDialog(
            anime = exploreAnime,
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = selectedAnime!!.listStatus,
            isFavorite = isAnimeFavorite,
            onToggleFavorite = { onToggleFavorite(selectedAnime!!) },
            onDismiss = { showAnimeInfoDialog = false },
            onAddToPlanning = { viewModel.addExploreAnimeToList(exploreAnime, "PLANNING") },
            onAddToDropped = { viewModel.addExploreAnimeToList(exploreAnime, "DROPPED") },
            onAddToOnHold = { viewModel.addExploreAnimeToList(exploreAnime, "PAUSED") },
            onRemoveFromList = { viewModel.removeAnimeFromList(selectedAnime!!.id); showAnimeInfoDialog = false },
            onStartWatching = { episode -> onPlayEpisode(selectedAnime!!, episode); showAnimeInfoDialog = false },
            isLoggedIn = isLoggedIn,
            onLoginClick = onLoginClick,
            onRelationClick = { relation ->
                scope.launch {
                    try {
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
            }
        )
    }

    if (showDetailedAnimeScreen && selectedAnime != null) {
        val detailedAnimeData = selectedAnime!!.toDetailedAnimeData()
        DetailedAnimeScreen(
            anime = detailedAnimeData,
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = selectedAnime!!.listStatus,
            isLoggedIn = isLoggedIn,
            isFavorite = favoriteIds.contains(selectedAnime!!.id),
            onDismiss = {
                // Go back to first anime if navigated, otherwise close
                if (firstAnime != null && selectedAnime?.id != firstAnime?.id) {
                    selectedAnime = firstAnime
                } else {
                    showDetailedAnimeScreen = false
                    firstAnime = null
                }
            },
            onSwipeToClose = {
                showDetailedAnimeScreen = false
                firstAnime = null
            },
            onPlayEpisode = { episode ->
                onPlayEpisode(selectedAnime!!, episode)
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
            }
        )
    }

    if (showUserProfileDialog) {
        UserProfileDialog(
            viewModel = viewModel,
            isOled = isOled,
            onDismiss = { showUserProfileDialog = false },
            onShowAnimeDialog = { anime, _ -> onShowAnimeDialog(anime, null) },
            planningToWatch = planningToWatch,
            onHold = onHold,
            dropped = dropped
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
