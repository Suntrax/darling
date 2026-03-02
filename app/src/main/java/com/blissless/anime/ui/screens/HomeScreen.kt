package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.imageLoader
import com.blissless.anime.AnimeMedia
import com.blissless.anime.ExploreAnime
import com.blissless.anime.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isLoggedIn: Boolean,
    isOled: Boolean = false,
    showStatusColors: Boolean = true,
    onPlayerStateChange: (Boolean) -> Unit = {},
    onPlayEpisode: (AnimeMedia, Int) -> Unit = { _, _ -> },
    onLoginClick: () -> Unit = {}
) {
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()
    val isLoading by viewModel.isLoadingHome.collectAsState()

    var selectedAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }

    // Check if all lists are empty
    val allListsEmpty = currentlyWatching.isEmpty() && planningToWatch.isEmpty() &&
            completed.isEmpty() && onHold.isEmpty() && dropped.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "My Anime",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isLoggedIn) {
                    IconButton(onClick = { showSearchOverlay = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            if (!isLoggedIn) {
                // Redesigned Login Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // AniList Icon/Logo
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "Welcome to Darling",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Sign in with AniList to sync your anime list and track your progress",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Login Button
                            Button(
                                onClick = onLoginClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF02A9FF) // AniList blue
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Login with AniList",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                "Don't have an account? Sign up for free at anilist.co",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (isLoading && allListsEmpty) {
                LoadingSkeleton(isOled)
            } else {
                // Use LazyColumn with weight to make it scrollable
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Currently Watching
                    if (currentlyWatching.isNotEmpty()) {
                        item(key = "header_current") {
                            SectionHeader(
                                title = "Currently Watching",
                                icon = Icons.Default.PlayArrow,
                                count = currentlyWatching.size,
                                isOled = isOled,
                                iconTint = Color(0xFF2196F3) // Blue
                            )
                        }
                        item(key = "list_current") {
                            HomeAnimeHorizontalList(
                                animeList = currentlyWatching,
                                listType = "CURRENT",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime
                                    showEpisodeSheet = true
                                },
                                onPlayClick = { anime ->
                                    val nextEp = anime.progress + 1
                                    onPlayEpisode(anime, nextEp)
                                },
                                onStatusClick = { anime ->
                                    selectedAnime = anime
                                    showStatusDialog = true
                                }
                            )
                        }
                    }

                    // Planning to Watch
                    if (planningToWatch.isNotEmpty()) {
                        item(key = "header_planning") {
                            SectionHeader(
                                title = "Planning to Watch",
                                icon = Icons.Default.Bookmark,
                                count = planningToWatch.size,
                                isOled = isOled,
                                iconTint = Color(0xFF9C27B0) // Purple
                            )
                        }
                        item(key = "list_planning") {
                            HomeAnimeHorizontalList(
                                animeList = planningToWatch,
                                listType = "PLANNING",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime
                                    showEpisodeSheet = true
                                },
                                onPlayClick = { anime ->
                                    onPlayEpisode(anime, 1)
                                },
                                onStatusClick = { anime ->
                                    selectedAnime = anime
                                    showStatusDialog = true
                                }
                            )
                        }
                    }

                    // Completed
                    if (completed.isNotEmpty()) {
                        item(key = "header_completed") {
                            SectionHeader(
                                title = "Completed",
                                icon = Icons.Default.Check,
                                count = completed.size,
                                isOled = isOled,
                                iconTint = Color(0xFF4CAF50)
                            )
                        }
                        item(key = "list_completed") {
                            HomeAnimeHorizontalList(
                                animeList = completed,
                                listType = "COMPLETED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime
                                    showEpisodeSheet = true
                                },
                                onPlayClick = { anime ->
                                    onPlayEpisode(anime, 1)
                                },
                                onStatusClick = { anime ->
                                    selectedAnime = anime
                                    showStatusDialog = true
                                }
                            )
                        }
                    }

                    // On Hold
                    if (onHold.isNotEmpty()) {
                        item(key = "header_onhold") {
                            SectionHeader(
                                title = "On Hold",
                                icon = Icons.Default.Pause,
                                count = onHold.size,
                                isOled = isOled,
                                iconTint = Color(0xFFFFC107)
                            )
                        }
                        item(key = "list_onhold") {
                            HomeAnimeHorizontalList(
                                animeList = onHold,
                                listType = "PAUSED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime
                                    showEpisodeSheet = true
                                },
                                onPlayClick = { anime ->
                                    val nextEp = anime.progress + 1
                                    onPlayEpisode(anime, nextEp)
                                },
                                onStatusClick = { anime ->
                                    selectedAnime = anime
                                    showStatusDialog = true
                                }
                            )
                        }
                    }

                    // Dropped
                    if (dropped.isNotEmpty()) {
                        item(key = "header_dropped") {
                            SectionHeader(
                                title = "Dropped",
                                icon = Icons.Default.Delete,
                                count = dropped.size,
                                isOled = isOled,
                                iconTint = Color(0xFFF44336)
                            )
                        }
                        item(key = "list_dropped") {
                            HomeAnimeHorizontalList(
                                animeList = dropped,
                                listType = "DROPPED",
                                isOled = isOled,
                                showStatusColors = showStatusColors,
                                onAnimeClick = { anime ->
                                    selectedAnime = anime
                                    showEpisodeSheet = true
                                },
                                onPlayClick = { anime ->
                                    onPlayEpisode(anime, 1)
                                },
                                onStatusClick = { anime ->
                                    selectedAnime = anime
                                    showStatusDialog = true
                                }
                            )
                        }
                    }

                    // Empty state
                    if (allListsEmpty && !isLoading) {
                        item(key = "empty_state") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Your lists are empty",
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Check out the Explore tab to discover anime!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Bottom spacer
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        // Search Overlay
        if (showSearchOverlay) {
            SearchOverlay(
                viewModel = viewModel,
                isOled = isOled,
                currentlyWatching = currentlyWatching,
                planningToWatch = planningToWatch,
                completed = completed,
                onHold = onHold,
                dropped = dropped,
                onClose = { showSearchOverlay = false },
                onPlayEpisode = onPlayEpisode
            )
        }
    }

    // Episode Selection Dialog
    if (showEpisodeSheet && selectedAnime != null) {
        EpisodeSelectionDialog(
            anime = selectedAnime!!,
            isOled = isOled,
            onDismiss = { showEpisodeSheet = false },
            onEpisodeSelect = { episode ->
                onPlayEpisode(selectedAnime!!, episode)
                showEpisodeSheet = false
            }
        )
    }

    // Status Dialog
    if (showStatusDialog && selectedAnime != null) {
        HomeAnimeStatusDialog(
            anime = selectedAnime!!,
            isOled = isOled,
            onDismiss = { showStatusDialog = false },
            onUpdate = { status, progress ->
                if (progress != null) {
                    viewModel.updateAnimeStatus(selectedAnime!!.id, status, progress)
                } else {
                    viewModel.updateAnimeStatus(selectedAnime!!.id, status)
                }
                showStatusDialog = false
                viewModel.refreshHome()
            }
        )
    }
}

@Composable
private fun LoadingSkeleton(isOled: Boolean) {
    Column {
        Text(
            "Currently Watching",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) {
                Card(
                    modifier = Modifier
                        .width(130.dp)
                        .height(220.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Planning to Watch",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) {
                Card(
                    modifier = Modifier
                        .width(130.dp)
                        .height(220.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    count: Int,
    isOled: Boolean,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isOled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun HomeAnimeHorizontalList(
    animeList: List<AnimeMedia>,
    listType: String,
    isOled: Boolean,
    showStatusColors: Boolean = false,
    onAnimeClick: (AnimeMedia) -> Unit,
    onPlayClick: (AnimeMedia) -> Unit,
    onStatusClick: (AnimeMedia) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = animeList,
            key = { "${listType}_${it.id}" }
        ) { anime ->
            HomeAnimeCard(
                anime = anime,
                listType = listType,
                isOled = isOled,
                showStatusColors = showStatusColors,
                onClick = { onAnimeClick(anime) },
                onPlayClick = { onPlayClick(anime) },
                onStatusClick = { onStatusClick(anime) }
            )
        }
    }
}

@Composable
fun HomeAnimeCard(
    anime: AnimeMedia,
    listType: String,
    isOled: Boolean,
    showStatusColors: Boolean = false,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onStatusClick: () -> Unit
) {
    val context = LocalContext.current

    // Status colors matching ExploreScreen
    val statusColor = when (listType) {
        "CURRENT" -> Color(0xFF2196F3)    // Blue - Watching
        "PLANNING" -> Color(0xFF9C27B0)   // Purple - Planning
        "COMPLETED" -> Color(0xFF4CAF50)  // Green - Completed
        "PAUSED" -> Color(0xFFFFC107)     // Amber - On Hold
        "DROPPED" -> Color(0xFFF44336)    // Red - Dropped
        else -> Color.Transparent
    }

    val total = anime.totalEpisodes
    val released = anime.latestEpisode?.let { it - 1 } ?: total
    val isFinished = total in 1..released

    val progressText = when (listType) {
        "CURRENT" -> {
            when {
                isFinished -> "${anime.progress} / $total"
                total > 0 -> "${anime.progress} / $released / $total"
                released > 0 -> "${anime.progress} / $released"
                else -> "${anime.progress}"
            }
        }
        "COMPLETED" -> {
            if (total > 0) "$total eps" else "${anime.progress} eps"
        }
        else -> {
            when {
                total > 0 -> "$released / $total"
                released > 0 -> "$released / ??"
                else -> "??"
            }
        }
    }

    // Cached image request for performance
    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context)
            .data(anime.cover)
            .memoryCacheKey(anime.cover)
            .diskCacheKey(anime.cover)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = Modifier.width(130.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(185.dp),
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(70.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                // Status indicator bar at top (only if showStatusColors is enabled)
                if (showStatusColors) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(statusColor)
                    )
                }

                FilledTonalIconButton(
                    onClick = onStatusClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit Status",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    // Play button for CURRENT and PAUSED lists
                    if (listType == "CURRENT" || listType == "PAUSED") {
                        FilledTonalIconButton(
                            onClick = onPlayClick,
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play next episode",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Fixed height for title to prevent layout jumping
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(36.dp)
        ) {
            Text(
                text = anime.title,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
                overflow = TextOverflow.Ellipsis,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun EpisodeSelectionDialog(
    anime: AnimeMedia,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onEpisodeSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val total = anime.totalEpisodes
    val released = anime.latestEpisode?.let { it - 1 } ?: total
    val episodeCount = if (total > 0) total else released.coerceAtLeast(1)
    val currentProgress = anime.progress

    // Cached image request for dialog
    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context)
            .data(anime.cover)
            .memoryCacheKey(anime.cover)
            .diskCacheKey(anime.cover)
            .crossfade(false)
            .build()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(50.dp)
                            .height(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            anime.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            "Progress: $currentProgress / ${if (total > 0) total else "??"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Watched",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    Color.Gray.copy(alpha = 0.3f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Not aired",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(episodeCount) { index ->
                        val episodeNum = index + 1
                        val isWatched = episodeNum <= currentProgress
                        val hasAired = episodeNum <= released

                        EpisodeButton(
                            episodeNumber = episodeNum,
                            isWatched = isWatched,
                            hasAired = hasAired,
                            isOled = isOled,
                            onClick = {
                                if (hasAired) {
                                    onEpisodeSelect(episodeNum)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val nextEp = currentProgress + 1
                    if (nextEp <= released) {
                        Button(
                            onClick = { onEpisodeSelect(nextEp) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume Ep $nextEp")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeButton(
    episodeNumber: Int,
    isWatched: Boolean,
    hasAired: Boolean,
    isOled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isWatched -> MaterialTheme.colorScheme.primary
        hasAired -> if (isOled) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Gray.copy(alpha = 0.2f)
    }

    val contentColor = when {
        isWatched -> Color.White
        hasAired -> if (isOled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.Gray.copy(alpha = 0.5f)
    }

    val borderColor = when {
        isWatched -> MaterialTheme.colorScheme.primary
        hasAired -> Color.Transparent
        else -> Color.Gray.copy(alpha = 0.3f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        contentColor = contentColor,
        modifier = Modifier
            .size(48.dp)
            .alpha(if (hasAired) 1f else 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$episodeNumber",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isWatched) FontWeight.Bold else FontWeight.Normal
            )

            if (isWatched) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .padding(2.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun HomeAnimeStatusDialog(
    anime: AnimeMedia,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (String, Int?) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(anime.listStatus) }
    var selectedProgress by remember { mutableStateOf(anime.progress.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                val statuses = listOf(
                    "CURRENT" to "Watching",
                    "PLANNING" to "Plan to Watch",
                    "COMPLETED" to "Completed",
                    "DROPPED" to "Dropped",
                    "PAUSED" to "On Hold"
                )

                statuses.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStatus = value }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStatus == value,
                            onClick = { selectedStatus = value },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = if (isOled) Color.White else MaterialTheme.colorScheme.primary,
                                unselectedColor = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            label,
                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Episode Progress",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = selectedProgress,
                    onValueChange = { selectedProgress = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = if (isOled) Color.White else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val progress = selectedProgress.toIntOrNull()
                        onUpdate(selectedStatus, progress)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun SearchOverlay(
    viewModel: MainViewModel,
    isOled: Boolean,
    currentlyWatching: List<AnimeMedia>,
    planningToWatch: List<AnimeMedia>,
    completed: List<AnimeMedia>,
    onHold: List<AnimeMedia>,
    dropped: List<AnimeMedia>,
    onClose: () -> Unit,
    onPlayEpisode: (AnimeMedia, Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ExploreAnime>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Selected anime for detail dialog
    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }

    // Get saved anime IDs and their statuses from all lists
    val savedAnimeMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        val map = mutableMapOf<Int, String>()
        currentlyWatching.forEach { map[it.id] = "CURRENT" }
        planningToWatch.forEach { map[it.id] = "PLANNING" }
        completed.forEach { map[it.id] = "COMPLETED" }
        onHold.forEach { map[it.id] = "PAUSED" }
        dropped.forEach { map[it.id] = "DROPPED" }
        map
    }

    // Debounced search
    LaunchedEffect(searchQuery) {
        debounceJob?.cancel()
        if (searchQuery.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
        } else {
            isSearching = true
            debounceJob = scope.launch {
                delay(500)
                val results = viewModel.searchAnime(searchQuery)
                searchResults = results
                isSearching = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            // Search bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search AniList...",
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    // Only show close button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Results
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                Text(
                    "No results found",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { anime ->
                        SearchResultItem(
                            anime = anime,
                            isOled = isOled,
                            currentStatus = savedAnimeMap[anime.id],
                            onClick = {
                                selectedAnime = anime
                                showDetailDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog
    if (showDetailDialog && selectedAnime != null) {
        SearchAnimeDetailDialog(
            anime = selectedAnime!!,
            isOled = isOled,
            currentStatus = savedAnimeMap[selectedAnime!!.id],
            onDismiss = { showDetailDialog = false },
            onPlayEpisode = { episode ->
                val animeMedia = AnimeMedia(
                    id = selectedAnime!!.id,
                    title = selectedAnime!!.title,
                    cover = selectedAnime!!.cover,
                    banner = selectedAnime!!.banner,
                    progress = 0,
                    totalEpisodes = selectedAnime!!.episodes,
                    latestEpisode = selectedAnime!!.latestEpisode,
                    status = "",
                    averageScore = selectedAnime!!.averageScore,
                    genres = selectedAnime!!.genres,
                    listStatus = "",
                    listEntryId = 0
                )
                onPlayEpisode(animeMedia, episode)
                showDetailDialog = false
                onClose()
            },
            onUpdateStatus = { status ->
                viewModel.addExploreAnimeToList(selectedAnime!!, status)
            },
            onRemove = {
                viewModel.removeAnimeFromList(selectedAnime!!.id)
            }
        )
    }
}

@Composable
private fun SearchResultItem(
    anime: ExploreAnime,
    isOled: Boolean,
    currentStatus: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val displayScore = anime.averageScore?.let { it / 10.0 }

    // Cached image request
    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context)
            .data(anime.cover)
            .memoryCacheKey(anime.cover)
            .diskCacheKey(anime.cover)
            .crossfade(false)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOled) Color(0xFF1A1A1A) else Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(50.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        anime.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (currentStatus != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = "Saved",
                            tint = when (currentStatus) {
                                "CURRENT" -> Color(0xFF2196F3) // Blue
                                "PLANNING" -> Color(0xFF9C27B0) // Purple
                                "COMPLETED" -> Color(0xFF4CAF50)
                                "PAUSED" -> Color(0xFFFFC107)
                                "DROPPED" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayScore?.let { score ->
                        Text(
                            "★ ${String.format(Locale.US, "%.1f", score)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFD700)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        "Episodes: ${anime.episodes.takeIf { it > 0 } ?: "?"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                if (anime.genres.isNotEmpty()) {
                    Text(
                        anime.genres.take(3).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentStatus != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when(currentStatus) {
                            "CURRENT" -> "▶ Watching"
                            "PLANNING" -> "📋 Planning"
                            "COMPLETED" -> "✓ Completed"
                            "PAUSED" -> "⏸ On Hold"
                            "DROPPED" -> "✕ Dropped"
                            else -> currentStatus
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (currentStatus) {
                            "CURRENT" -> Color(0xFF2196F3) // Blue
                            "PLANNING" -> Color(0xFF9C27B0) // Purple
                            "COMPLETED" -> Color(0xFF4CAF50)
                            "PAUSED" -> Color(0xFFFFC107)
                            "DROPPED" -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SearchAnimeDetailDialog(
    anime: ExploreAnime,
    isOled: Boolean,
    currentStatus: String?,
    onDismiss: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onUpdateStatus: (String) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val displayScore = anime.averageScore?.let { it / 10.0 }
    var selectedStatus by remember { mutableStateOf(currentStatus ?: "") }

    // Animation state
    var showAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1.05f else 1f,
        animationSpec = tween(150),
        finishedListener = {
            if (showAnimation) {
                showAnimation = false
            }
        },
        label = "statusScale"
    )

    // Cached image request
    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context)
            .data(anime.cover)
            .memoryCacheKey(anime.cover)
            .diskCacheKey(anime.cover)
            .crossfade(false)
            .build()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else Color(0xFF1A1A1A)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header with cover and info
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(90.dp)
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            anime.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            displayScore?.let { score ->
                                Text(
                                    "★ ${String.format(Locale.US, "%.1f", score)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Text(
                                "${anime.episodes.takeIf { it > 0 } ?: "?"} eps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (anime.genres.isNotEmpty()) {
                            Text(
                                anime.genres.take(3).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (currentStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (currentStatus) {
                                    "CURRENT" -> Color(0xFF2196F3).copy(alpha = 0.2f) // Blue
                                    "PLANNING" -> Color(0xFF9C27B0).copy(alpha = 0.2f) // Purple
                                    "COMPLETED" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                    "PAUSED" -> Color(0xFFFFC107).copy(alpha = 0.2f)
                                    "DROPPED" -> Color(0xFFF44336).copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = when(currentStatus) {
                                        "CURRENT" -> "▶ Watching"
                                        "PLANNING" -> "📋 Planning"
                                        "COMPLETED" -> "✓ Completed"
                                        "PAUSED" -> "⏸ On Hold"
                                        "DROPPED" -> "✕ Dropped"
                                        else -> currentStatus
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (currentStatus) {
                                        "CURRENT" -> Color(0xFF2196F3) // Blue
                                        "PLANNING" -> Color(0xFF9C27B0) // Purple
                                        "COMPLETED" -> Color(0xFF4CAF50)
                                        "PAUSED" -> Color(0xFFFFC107)
                                        "DROPPED" -> Color(0xFFF44336)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Status buttons - 2x2 grid with better design
                Text(
                    "Add to list:",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Row 1: Watching and Planning
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusButton(
                        icon = Icons.Default.PlayArrow,
                        label = "Watching",
                        selected = selectedStatus == "CURRENT",
                        selectedColor = Color(0xFF2196F3), // Blue
                        onClick = {
                            selectedStatus = "CURRENT"
                            showAnimation = true
                            Toast.makeText(context, "Added to Watching", Toast.LENGTH_SHORT).show()
                            onUpdateStatus("CURRENT")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (selectedStatus == "CURRENT" && showAnimation) scale else 1f)
                    )

                    StatusButton(
                        icon = Icons.Default.Bookmark,
                        label = "Planning",
                        selected = selectedStatus == "PLANNING",
                        selectedColor = Color(0xFF9C27B0), // Purple
                        onClick = {
                            selectedStatus = "PLANNING"
                            showAnimation = true
                            Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                            onUpdateStatus("PLANNING")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (selectedStatus == "PLANNING" && showAnimation) scale else 1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Completed and Remove
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusButton(
                        icon = Icons.Default.Check,
                        label = "Completed",
                        selected = selectedStatus == "COMPLETED",
                        selectedColor = Color(0xFF4CAF50),
                        onClick = {
                            selectedStatus = "COMPLETED"
                            showAnimation = true
                            Toast.makeText(context, "Marked as Completed", Toast.LENGTH_SHORT).show()
                            onUpdateStatus("COMPLETED")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (selectedStatus == "COMPLETED" && showAnimation) scale else 1f)
                    )

                    // Remove button - only show if anime is in a list
                    if (currentStatus != null) {
                        Button(
                            onClick = {
                                selectedStatus = ""
                                Toast.makeText(context, "Removed from list", Toast.LENGTH_SHORT).show()
                                onRemove()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red.copy(alpha = 0.15f),
                                contentColor = Color.Red
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove", fontWeight = FontWeight.Medium)
                        }
                    } else {
                        // Empty placeholder when no remove button
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Start Watching button
                Button(
                    onClick = { onPlayEpisode(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Start Watching",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun StatusButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected)
                selectedColor
            else
                Color.White.copy(alpha = 0.08f),
            contentColor = if (selected)
                Color.White
            else
                Color.White.copy(alpha = 0.8f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 4.dp else 0.dp
        )
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White,
            containerColor = Color.White.copy(alpha = 0.1f),
            labelColor = Color.White.copy(alpha = 0.7f)
        )
    )
}
