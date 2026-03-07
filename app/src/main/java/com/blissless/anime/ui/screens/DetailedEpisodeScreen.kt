package com.blissless.anime.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.blissless.anime.AnimeMedia
import com.blissless.anime.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedEpisodeScreen(
    anime: AnimeMedia,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    onDismiss: () -> Unit,
    onEpisodeSelect: (Int) -> Unit
) {
    val total = anime.totalEpisodes
    val released = anime.latestEpisode?.let { it - 1 } ?: total
    val episodeCount = if (total > 0) total else released.coerceAtLeast(1)
    val currentProgress = anime.progress

    var selectedEpisode by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to current progress + 1 on first load
    LaunchedEffect(Unit) {
        if (currentProgress > 0 && currentProgress < episodeCount) {
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(currentProgress)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isOled) Color(0xFF121212) else MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Close button
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Anime cover
                            AsyncImage(
                                model = anime.cover,
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(45.dp)
                                    .height(65.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = anime.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Progress: $currentProgress / ${if (total > 0) total else "??"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (released > 0) {
                                        Text(
                                            text = "•",
                                            color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$released aired",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LegendItem(
                                color = MaterialTheme.colorScheme.primary,
                                label = "Watched",
                                isOled = isOled
                            )
                            LegendItem(
                                color = MaterialTheme.colorScheme.secondary,
                                label = "Current",
                                isOled = isOled
                            )
                            LegendItem(
                                color = Color.Gray.copy(alpha = 0.3f),
                                label = "Unaired",
                                isOled = isOled
                            )
                        }
                    }
                }

                // Quick jump buttons
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isOled) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Resume button
                        val nextEp = currentProgress + 1
                        if (nextEp <= released) {
                            item {
                                FilterChip(
                                    selected = true,
                                    onClick = { onEpisodeSelect(nextEp) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Resume Ep $nextEp")
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // First episode
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                },
                                label = { Text("Ep 1") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface
                                )
                            )
                        }

                        // Last aired
                        if (released > 1) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        scope.launch {
                                            listState.animateScrollToItem(released - 1)
                                        }
                                    },
                                    label = { Text("Latest: Ep $released") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        }
                    }
                }

                // Episode list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(episodeCount) { index ->
                        val episodeNum = index + 1
                        val isWatched = episodeNum <= currentProgress
                        val isCurrent = episodeNum == currentProgress + 1
                        val hasAired = episodeNum <= released

                        EpisodeCard(
                            episodeNumber = episodeNum,
                            isWatched = isWatched,
                            isCurrent = isCurrent,
                            hasAired = hasAired,
                            isOled = isOled,
                            isSelected = selectedEpisode == episodeNum,
                            onSelect = { selectedEpisode = episodeNum },
                            onPlay = {
                                if (hasAired) {
                                    onEpisodeSelect(episodeNum)
                                }
                            }
                        )
                    }

                    // Bottom padding
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isOled: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeCard(
    episodeNumber: Int,
    isWatched: Boolean,
    isCurrent: Boolean,
    hasAired: Boolean,
    isOled: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    val backgroundColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isWatched -> if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        hasAired -> if (isOled) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }

    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    val contentAlpha = if (hasAired) 1f else 0.4f

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.95f),
        exit = fadeOut(tween(200))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .then(
                    if (borderColor != Color.Transparent) {
                        Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            onClick = {
                if (hasAired) {
                    onSelect()
                    onPlay()
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Episode number/indicator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            when {
                                isWatched -> MaterialTheme.colorScheme.primary
                                isCurrent -> MaterialTheme.colorScheme.secondary
                                hasAired -> if (isOled) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surfaceVariant
                                else -> Color.Gray.copy(alpha = 0.2f)
                            },
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isWatched -> Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Watched",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        isCurrent -> Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Current",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        else -> Text(
                            text = "$episodeNumber",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hasAired) {
                                if (isOled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                Color.Gray
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Episode info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Episode $episodeNumber",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = when {
                            !hasAired -> "Not yet aired"
                            isCurrent -> "Up next"
                            isWatched -> "Watched"
                            else -> "Available"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            !hasAired -> Color.Gray
                            isCurrent -> MaterialTheme.colorScheme.secondary
                            isWatched -> MaterialTheme.colorScheme.primary
                            else -> if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // Play button for aired episodes
                if (hasAired) {
                    FilledTonalIconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            },
                            contentColor = if (isCurrent) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
