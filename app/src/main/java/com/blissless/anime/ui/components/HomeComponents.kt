package com.blissless.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.data.models.AnimeMedia

@Composable
fun LoadingSkeleton(isOled: Boolean) {
    Column {
        Text(
            "Currently Watching",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(4) {
                Card(
                    modifier = Modifier.width(130.dp).height(220.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
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
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
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
    isLoggedIn: Boolean = false,
    playbackPositions: Map<String, Long> = emptyMap(),
    onAnimeClick: (AnimeMedia) -> Unit,
    onPlayClick: (AnimeMedia) -> Unit,
    onStatusClick: (AnimeMedia) -> Unit,
    onInfoClick: (AnimeMedia) -> Unit = {}
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items = animeList, key = { "${listType}_${it.id}" }) { anime ->
            HomeAnimeCard(
                anime = anime,
                listType = listType,
                isOled = isOled,
                showStatusColors = showStatusColors,
                isLoggedIn = isLoggedIn,
                playbackPositions = playbackPositions,
                onClick = { onAnimeClick(anime) },
                onPlayClick = { onPlayClick(anime) },
                onStatusClick = { onStatusClick(anime) },
                onInfoClick = { onInfoClick(anime) }
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
    isLoggedIn: Boolean = false,
    playbackPositions: Map<String, Long> = emptyMap(),
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onStatusClick: () -> Unit,
    onInfoClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val statusColor = HomeStatusColors.getColor(listType)

    val total = anime.totalEpisodes
    val released = anime.latestEpisode?.let { it - 1 } ?: total
    val isFinished = total in 1..released

    val nextEpisode = anime.progress + 1
    val playbackKey = "${anime.id}_$nextEpisode"
    val savedPosition = playbackPositions[playbackKey] ?: 0L
    val defaultEpisodeDuration = 24 * 60 * 1000L
    val progressPercent = if (savedPosition > 0) (savedPosition.toFloat() / defaultEpisodeDuration.toFloat()).coerceIn(0f, 1f) else 0f

    val progressText = when (listType) {
        "CURRENT" -> {
            when {
                isFinished -> "${anime.progress} / $total"
                total > 0 -> "${anime.progress} / $released / $total"
                released > 0 -> "${anime.progress} / $released"
                else -> "${anime.progress}"
            }
        }
        "COMPLETED" -> { if (total > 0) "$total eps" else "${anime.progress} eps" }
        else -> {
            when {
                total > 0 -> "$released / $total"
                released > 0 -> "$released / ??"
                else -> "??"
            }
        }
    }

    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context)
            .data(anime.cover)
            .memoryCacheKey(anime.cover)
            .diskCacheKey(anime.cover)
            .crossfade(false)
            .build()
    }

    Column(modifier = Modifier.width(130.dp)) {
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.height(185.dp), onClick = onClick) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(model = imageRequest, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

                // Gradient at bottom
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp)
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))

                // Status indicator bar at top
                if (showStatusColors) {
                    Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).background(statusColor))
                }

                // Progress bar at bottom (continue watching indicator)
                if (progressPercent > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                    }
                }

                // TOP START: Episode Counter
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // TOP END: Status/Edit Button
                FilledTonalIconButton(
                    onClick = onStatusClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White)
                ) { Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Edit Status", modifier = Modifier.size(18.dp)) }

                // Bottom Row
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // BOTTOM START: Info Button
                    FilledTonalIconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        )
                    ) { Icon(imageVector = Icons.Outlined.Info, contentDescription = "Anime Info", modifier = Modifier.size(18.dp)) }

                    Spacer(modifier = Modifier.weight(1f))

                    // BOTTOM END: Play Button (Only show if logged in)
                    if (isLoggedIn) {
                        FilledTonalIconButton(
                            onClick = {
                                if (listType == "CURRENT" || listType == "PAUSED") {
                                    onPlayClick() // Plays next episode
                                } else {
                                    onClick() // Opens Episode Dialogue
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = if (listType == "CURRENT" || listType == "PAUSED") "Play next episode" else "Episodes",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        // Title
        Box(modifier = Modifier.width(130.dp).height(36.dp)) {
            Text(text = anime.title, modifier = Modifier.padding(top = 6.dp), maxLines = 2, style = MaterialTheme.typography.labelMedium, overflow = TextOverflow.Ellipsis, color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun StatusButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) selectedColor else Color.White.copy(alpha = 0.08f),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.8f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 4.dp else 0.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}