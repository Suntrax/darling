package com.blissless.anime.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.blissless.anime.AnimeMedia
import com.blissless.anime.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isLoggedIn: Boolean,
    isOled: Boolean = false,
    onPlayerStateChange: (Boolean) -> Unit = {},
    onPlayEpisode: (AnimeMedia, Int) -> Unit = { _, _ -> },
    onLoginClick: () -> Unit = {}
) {
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val isLoading by viewModel.isLoadingHome.collectAsState()

    var selectedAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

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
                IconButton(onClick = { viewModel.refreshHome() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        if (!isLoggedIn) {
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
                        "Login to AniList to sync your anime list",
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onLoginClick) {
                        Text("Login with AniList")
                    }
                }
            }
        } else if (isLoading && currentlyWatching.isEmpty() && planningToWatch.isEmpty()) {
            LoadingSkeleton(isOled)
        } else {
            if (currentlyWatching.isNotEmpty()) {
                SectionHeader("Currently Watching", isOled)

                HomeAnimeHorizontalList(
                    animeList = currentlyWatching,
                    listType = "CURRENT",
                    isOled = isOled,
                    onAnimeClick = { anime ->
                        selectedAnime = anime
                        showEpisodeSheet = true
                    },
                    onPlayClick = { anime ->
                        // Play next episode directly
                        val nextEp = anime.progress + 1
                        onPlayEpisode(anime, nextEp)
                    },
                    onStatusClick = { anime ->
                        selectedAnime = anime
                        showStatusDialog = true
                    }
                )
            }

            if (planningToWatch.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Planning to Watch", isOled)

                HomeAnimeHorizontalList(
                    animeList = planningToWatch,
                    listType = "PLANNING",
                    isOled = isOled,
                    onAnimeClick = { anime ->
                        selectedAnime = anime
                        showEpisodeSheet = true
                    },
                    onPlayClick = { anime ->
                        // For planning, start from episode 1
                        onPlayEpisode(anime, 1)
                    },
                    onStatusClick = { anime ->
                        selectedAnime = anime
                        showStatusDialog = true
                    }
                )
            }

            if (currentlyWatching.isEmpty() && planningToWatch.isEmpty() && !isLoading) {
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
    }

    // Episode Selection Dialog
    if (showEpisodeSheet && selectedAnime != null) {
        EpisodeSelectionDialog(
            anime = selectedAnime!!,
            isOled = isOled,
            onDismiss = { showEpisodeSheet = false },
            onEpisodeSelect = { episode ->
                // Start playback for selected episode
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
private fun SectionHeader(title: String, isOled: Boolean) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun HomeAnimeHorizontalList(
    animeList: List<AnimeMedia>,
    listType: String,
    isOled: Boolean,
    onAnimeClick: (AnimeMedia) -> Unit,
    onPlayClick: (AnimeMedia) -> Unit,
    onStatusClick: (AnimeMedia) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(animeList) { anime ->
            HomeAnimeCard(
                anime = anime,
                listType = listType,
                isOled = isOled,
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
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onStatusClick: () -> Unit
) {
    val total = anime.totalEpisodes
    val released = anime.latestEpisode?.let { it - 1 } ?: total
    val isFinished = total in 1..released

    val progressText = if (listType == "CURRENT") {
        when {
            isFinished -> "${anime.progress} / $total"
            total > 0 -> "${anime.progress} / $released / $total"
            released > 0 -> "${anime.progress} / $released"
            else -> "${anime.progress}"
        }
    } else {
        when {
            total > 0 -> "$released / $total"
            released > 0 -> "$released / ??"
            else -> "??"
        }
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
                    model = anime.cover,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient overlay at bottom
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

                // Status/Edit button (top right)
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

                // Bottom row with progress and play button
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

                    // Play button - plays next episode
                    if (listType == "CURRENT") {
                        FAB(
                            onClick = onPlayClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
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

@Composable
private fun FAB(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(36.dp),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
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
    val total = anime.totalEpisodes
    val released = anime.latestEpisode?.let { it - 1 } ?: total
    val episodeCount = if (total > 0) total else released.coerceAtLeast(1)
    val currentProgress = anime.progress

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
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = anime.cover,
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

                // Legend
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

                // Episode grid - episodes are 1-indexed
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

                // Action buttons
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