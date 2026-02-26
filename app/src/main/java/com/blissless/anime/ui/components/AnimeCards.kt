package com.blissless.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blissless.anime.data.models.AnimeMedia

@Composable
fun AnimeCard(
    anime: AnimeMedia,
    onClick: (AnimeMedia) -> Unit,
    onPlayClick: (AnimeMedia) -> Unit,
    onStatusClick: (AnimeMedia) -> Unit,
    modifier: Modifier = Modifier,
    listType: String = "CURRENT"
) {
    Card(
        onClick = { onClick(anime) },
        modifier = modifier.width(140.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box {
            AsyncImage(
                model = anime.cover,
                contentDescription = anime.title,
                modifier = Modifier.height(185.dp).fillMaxWidth(),
                contentScale = ContentScale.Crop
            )

            val released = anime.latestEpisode ?: 0
            val total = anime.totalEpisodes
            val isFinished = total > 0 && released >= total

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

            // Bottom gradient overlay with progress and buttons
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(top = 28.dp, bottom = 6.dp, start = 8.dp, end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = progressText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    // Play button (bottom right)
                    if (listType == "CURRENT") {
                        SmallFloatingActionButton(
                            onClick = { onPlayClick(anime) },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play next episode",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Status button (top right)
            SmallFloatingActionButton(
                onClick = { onStatusClick(anime) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Change status",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = anime.title,
            modifier = Modifier.padding(8.dp),
            maxLines = 2,
            style = MaterialTheme.typography.labelMedium,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AnimeHorizontalList(
    list: List<AnimeMedia>,
    listType: String = "CURRENT",
    onAnimeClick: (AnimeMedia) -> Unit,
    onPlayClick: (AnimeMedia) -> Unit,
    onStatusClick: (AnimeMedia) -> Unit
) {
    if (list.isEmpty()) {
        Text("No anime found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                items = list,
                key = { it.id }
            ) { anime ->
                AnimeCard(
                    anime = anime,
                    onClick = onAnimeClick,
                    onPlayClick = onPlayClick,
                    onStatusClick = onStatusClick,
                    listType = listType
                )
            }
        }
    }
}