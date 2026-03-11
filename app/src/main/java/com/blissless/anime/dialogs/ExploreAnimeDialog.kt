package com.blissless.anime.dialogs

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.MainViewModel
import com.blissless.anime.ui.components.StatusColors
import com.blissless.anime.ui.components.StatusLabels
import java.util.Locale

@Composable
fun ExploreAnimeDialog(
    anime: ExploreAnime,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    currentStatus: String?,
    isFavorite: Boolean = false,
    canAddFavorite: Boolean = true,
    onToggleFavorite: () -> Unit = {},
    onDismiss: () -> Unit,
    onAddToPlanning: () -> Unit,
    onAddToDropped: () -> Unit = {},
    onAddToOnHold: () -> Unit = {},
    onRemoveFromList: () -> Unit = {},
    onStartWatching: (Int) -> Unit,
    isLoggedIn: Boolean = false
) {
    val context = LocalContext.current
    val displayScore = anime.averageScore?.let { it / 10.0 }

    var showAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1.2f else 1f,
        animationSpec = tween(200),
        finishedListener = {
            if (showAnimation) {
                showAnimation = false
            }
        },
        label = "buttonScale"
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
                // Header with cover and info - MATCHING SearchAnimeDetailDialog style
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

                        // Year, Score, and Episodes row
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ADDED: Year before score
                            anime.year?.let { year ->
                                Text(
                                    "$year",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    " • ",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            displayScore?.let { score ->
                                Text(
                                    "★ ${String.format(Locale.US, "%.1f", score)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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

                        // Status badge - ADDED: Year on left side of status badge
                        if (currentStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Year on the left side
                                anime.year?.let { year ->
                                    Text(
                                        "$year",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                StatusBadge(currentStatus)
                            }
                        }
                    }
                }

                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Status buttons label
                    Text(
                        "Add to list:",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 1: Watching and Planning - MATCHING SearchAnimeDetailDialog style
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusButton(
                            label = "Watching",
                            icon = Icons.Default.PlayArrow,
                            isActive = currentStatus == "CURRENT",
                            activeColor = Color(0xFF2196F3),
                            showAnimation = currentStatus == "CURRENT" && showAnimation,
                            scale = scale,
                            onClick = {
                                if (currentStatus == "CURRENT") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from Watching", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addExploreAnimeToList(anime, "CURRENT")
                                    showAnimation = true
                                    Toast.makeText(context, "Added to Watching", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        StatusButton(
                            label = "Planning",
                            icon = Icons.Default.Schedule,
                            isActive = currentStatus == "PLANNING",
                            activeColor = Color(0xFF9C27B0),
                            showAnimation = currentStatus == "PLANNING" && showAnimation,
                            scale = scale,
                            onClick = {
                                if (currentStatus == "PLANNING") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from Planning", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddToPlanning()
                                    showAnimation = true
                                    Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Completed and On Hold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusButton(
                            label = "Completed",
                            icon = Icons.Default.Check,
                            isActive = currentStatus == "COMPLETED",
                            activeColor = Color(0xFF4CAF50),
                            showAnimation = currentStatus == "COMPLETED" && showAnimation,
                            scale = scale,
                            onClick = {
                                if (currentStatus == "COMPLETED") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from Completed", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addExploreAnimeToList(anime, "COMPLETED")
                                    showAnimation = true
                                    Toast.makeText(context, "Marked as Completed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        StatusButton(
                            label = "On Hold",
                            icon = Icons.Default.Pause,
                            isActive = currentStatus == "PAUSED",
                            activeColor = Color(0xFFFFC107),
                            showAnimation = currentStatus == "PAUSED" && showAnimation,
                            scale = scale,
                            onClick = {
                                if (currentStatus == "PAUSED") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from On Hold", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddToOnHold()
                                    showAnimation = true
                                    Toast.makeText(context, "Added to On Hold", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 3: Dropped and Favorite
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusButton(
                            label = "Dropped",
                            icon = Icons.Default.Delete,
                            isActive = currentStatus == "DROPPED",
                            activeColor = Color(0xFFF44336),
                            showAnimation = currentStatus == "DROPPED" && showAnimation,
                            scale = scale,
                            onClick = {
                                if (currentStatus == "DROPPED") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from Dropped", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddToDropped()
                                    showAnimation = true
                                    Toast.makeText(context, "Marked as Dropped", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // Favorite button - always visible
                        Button(
                            onClick = {
                                onToggleFavorite()
                                if (isFavorite) {
                                    Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFavorite) Color(0xFFFFD700).copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.08f),
                                contentColor = if (isFavorite) Color(0xFFFFD700)
                                else Color.White.copy(alpha = 0.8f)
                            ),
                            enabled = isFavorite || canAddFavorite
                        ) {
                            Icon(
                                if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                                modifier = Modifier.size(16.dp),
                                tint = if (isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isFavorite) "Favorited" else "Favorite",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Start Watching button
                    Button(
                        onClick = { onStartWatching(1) },
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
                } else {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Disabled button when not logged in
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = if (isOled) Color(0xFF333333) else MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            "Log in to AniList first",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
private fun StatusBadge(currentStatus: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = (StatusColors[currentStatus] ?: Color(0xFF2196F3)).copy(alpha = 0.2f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            // Icon based on status
            Icon(
                imageVector = when (currentStatus) {
                    "CURRENT" -> Icons.Default.PlayArrow
                    "PLANNING" -> Icons.Default.Bookmark
                    "COMPLETED" -> Icons.Default.Check
                    "PAUSED" -> Icons.Default.Pause
                    "DROPPED" -> Icons.Default.Close
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = StatusColors[currentStatus] ?: Color(0xFF2196F3)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = StatusLabels[currentStatus] ?: currentStatus,
                style = MaterialTheme.typography.labelMedium,
                color = StatusColors[currentStatus] ?: Color(0xFF2196F3)
            )
        }
    }
}

@Composable
private fun RowScope.StatusButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    showAnimation: Boolean,
    scale: Float,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .scale(if (showAnimation) scale else 1f),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) activeColor
            else Color.White.copy(alpha = 0.08f),
            contentColor = if (isActive) Color.White
            else Color.White.copy(alpha = 0.8f)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}