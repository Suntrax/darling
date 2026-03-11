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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.ui.components.HomeStatusColors
import com.blissless.anime.ui.components.StatusButton
import java.util.Locale

@Composable
fun HomeAnimeInfoDialog(
    anime: AnimeMedia,
    isOled: Boolean,
    isFavorite: Boolean = false,
    canAddFavorite: Boolean = true,
    onToggleFavorite: () -> Unit = {},
    onDismiss: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onUpdateStatus: (String) -> Unit = {},
    onRemove: () -> Unit = {}
) {
    val context = LocalContext.current
    val displayScore = anime.averageScore?.let { it / 10.0 }
    var selectedStatus by remember { mutableStateOf(anime.listStatus) }
    var markedForRemoval by remember { mutableStateOf(false) }
    var showAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1.05f else 1f,
        animationSpec = tween(150),
        finishedListener = { if (showAnimation) showAnimation = false },
        label = "statusScale"
    )

    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context).data(anime.cover).memoryCacheKey(anime.cover).diskCacheKey(anime.cover).crossfade(false).build()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isOled) Color.Black else Color(0xFF1A1A1A))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(model = imageRequest, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.width(90.dp).height(130.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            displayScore?.let { score ->
                                Text("★ ${String.format(Locale.US, "%.1f", score)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text("${anime.totalEpisodes.takeIf { it > 0 } ?: "?"} eps", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (anime.genres.isNotEmpty()) { Text(anime.genres.take(3).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        Spacer(modifier = Modifier.height(8.dp))
                        anime.year?.let { Text("Released: $it", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium) }
                        Spacer(modifier = Modifier.height(6.dp))

                        val currentDisplayStatus = selectedStatus.ifEmpty { anime.listStatus }
                        if (currentDisplayStatus.isNotEmpty()) {
                            StatusBadge(status = currentDisplayStatus)
                        }
                        if (anime.progress > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Progress: ${anime.progress} / ${anime.totalEpisodes.takeIf { it > 0 } ?: "?"}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                StatusButtonsGrid(
                    selectedStatus = selectedStatus,
                    markedForRemoval = markedForRemoval,
                    showAnimation = showAnimation,
                    scale = scale,
                    onStatusSelected = { status ->
                        selectedStatus = status
                        markedForRemoval = false
                        showAnimation = true
                        Toast.makeText(context, "Status updated", Toast.LENGTH_SHORT).show()
                        onUpdateStatus(status)
                    },
                    onRemoveToggled = {
                        markedForRemoval = !markedForRemoval
                        showAnimation = true
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                // Favorite or Remove Button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isFavorite || canAddFavorite) {
                        Button(
                            onClick = {
                                if (!isFavorite && !canAddFavorite) {
                                    Toast.makeText(context, "Maximum 10 favorites!", Toast.LENGTH_SHORT).show()
                                } else {
                                    onToggleFavorite()
                                    Toast.makeText(context, if (isFavorite) "Removed from Favorites" else "Added to Favorites", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFavorite) Color(0xFFFFD700).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.15f),
                                contentColor = if (isFavorite) Color(0xFFFFD700) else Color.White
                            )
                        ) {
                            Icon(if (isFavorite) Icons.Default.Star else Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isFavorite) "Favorited" else "Favorite", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = { markedForRemoval = !markedForRemoval; showAnimation = true },
                            modifier = Modifier.weight(1f).height(44.dp).scale(if (markedForRemoval && showAnimation) scale else 1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (markedForRemoval) Color.Red.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.15f),
                                contentColor = if (markedForRemoval) Color.Red else Color.White
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                val nextEp = anime.progress + 1
                val released = anime.latestEpisode?.let { it - 1 } ?: anime.totalEpisodes
                val canPlay = nextEp <= released || selectedStatus == "PLANNING" || anime.listStatus == "PLANNING"

                if (canPlay) {
                    Button(
                        onClick = { onPlayEpisode(if (selectedStatus == "PLANNING" || anime.listStatus == "PLANNING") 1 else nextEp) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedStatus == "PLANNING" || anime.listStatus == "PLANNING") "Start Watching" else "Continue Ep $nextEp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (markedForRemoval) {
                    Button(
                        onClick = { Toast.makeText(context, "Removed from list", Toast.LENGTH_SHORT).show(); onRemove() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Remove from List", fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close", color = Color.White.copy(alpha = 0.6f)) }
            }
        }
    }
}

@Composable
fun HomeAnimeStatusDialog(
    anime: AnimeMedia,
    isOled: Boolean,
    showStatusColors: Boolean = false,
    isFavorite: Boolean = false,
    canAddFavorite: Boolean = true,
    onToggleFavorite: () -> Unit = {},
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (String, Int?) -> Unit
) {
    val context = LocalContext.current
    var selectedStatus by remember { mutableStateOf(anime.listStatus) }
    var selectedProgress by remember { mutableStateOf(anime.progress.toString()) }
    var markedForRemoval by remember { mutableStateOf(false) }
    var showAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1.05f else 1f,
        animationSpec = tween(150),
        finishedListener = { if (showAnimation) showAnimation = false },
        label = "statusScale"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isOled) Color.Black else Color(0xFF1A1A1A))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = anime.cover, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.width(60.dp).height(85.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Progress: ${anime.progress} / ${anime.totalEpisodes.takeIf { it > 0 } ?: "?"}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        anime.year?.let { Text("Released: $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f)) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                StatusButtonsGrid(
                    selectedStatus = selectedStatus,
                    markedForRemoval = markedForRemoval,
                    showAnimation = showAnimation,
                    scale = scale,
                    onStatusSelected = { status -> selectedStatus = status; markedForRemoval = false; showAnimation = true },
                    onRemoveToggled = { markedForRemoval = !markedForRemoval; showAnimation = true }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Episode Progress", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = selectedProgress,
                    onValueChange = { selectedProgress = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.3f), cursorColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (markedForRemoval) { Toast.makeText(context, "Removed from list", Toast.LENGTH_SHORT).show(); onRemove() }
                        else { val progress = selectedProgress.toIntOrNull(); onUpdate(selectedStatus, progress) }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (markedForRemoval) Color.Red else MaterialTheme.colorScheme.primary)
                ) { Text(if (markedForRemoval) "Remove from List" else "Save Changes", fontWeight = FontWeight.Bold) }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Color.White.copy(alpha = 0.6f)) }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = HomeStatusColors.getContainerColor(status)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Icon(
                imageVector = when(status) { "CURRENT" -> Icons.Default.PlayArrow; "PLANNING" -> Icons.Default.Bookmark; "COMPLETED" -> Icons.Default.Check; "PAUSED" -> Icons.Default.Pause; "DROPPED" -> Icons.Default.Close; else -> Icons.Default.Info },
                contentDescription = null, modifier = Modifier.size(14.dp), tint = HomeStatusColors.getColor(status)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when(status) { "CURRENT" -> "Watching"; "PLANNING" -> "Planning"; "COMPLETED" -> "Completed"; "PAUSED" -> "On Hold"; "DROPPED" -> "Dropped"; else -> status },
                style = MaterialTheme.typography.labelMedium, color = HomeStatusColors.getColor(status)
            )
        }
    }
}

@Composable
private fun StatusButtonsGrid(
    selectedStatus: String,
    markedForRemoval: Boolean,
    showAnimation: Boolean,
    scale: Float,
    onStatusSelected: (String) -> Unit,
    onRemoveToggled: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusButton(
            Icons.Default.PlayArrow,
            "Watching",
            selectedStatus == "CURRENT" && !markedForRemoval,
            { onStatusSelected("CURRENT") },
            Modifier.weight(1f)
                .scale(if (selectedStatus == "CURRENT" && showAnimation && !markedForRemoval) scale else 1f),
            HomeStatusColors.getColor("CURRENT")
        )
        StatusButton(
            Icons.Default.Bookmark,
            "Planning",
            selectedStatus == "PLANNING" && !markedForRemoval,
            { onStatusSelected("PLANNING") },
            Modifier.weight(1f)
                .scale(if (selectedStatus == "PLANNING" && showAnimation && !markedForRemoval) scale else 1f),
            HomeStatusColors.getColor("PLANNING")
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusButton(
            Icons.Default.Check,
            "Completed",
            selectedStatus == "COMPLETED" && !markedForRemoval,
            { onStatusSelected("COMPLETED") },
            Modifier.weight(1f)
                .scale(if (selectedStatus == "COMPLETED" && showAnimation && !markedForRemoval) scale else 1f),
            HomeStatusColors.getColor("COMPLETED")
        )
        StatusButton(
            Icons.Default.Pause,
            "On Hold",
            selectedStatus == "PAUSED" && !markedForRemoval,
            { onStatusSelected("PAUSED") },
            Modifier.weight(1f)
                .scale(if (selectedStatus == "PAUSED" && showAnimation && !markedForRemoval) scale else 1f),
            HomeStatusColors.getColor("PAUSED")
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusButton(
            Icons.Default.Delete,
            "Dropped",
            selectedStatus == "DROPPED" && !markedForRemoval,
            { onStatusSelected("DROPPED") },
            Modifier.weight(1f)
                .scale(if (selectedStatus == "DROPPED" && showAnimation && !markedForRemoval) scale else 1f),
            HomeStatusColors.getColor("DROPPED")
        )
        Button(onClick = onRemoveToggled, modifier = Modifier.weight(1f).height(44.dp).scale(if (markedForRemoval && showAnimation) scale else 1f), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (markedForRemoval) Color.Red.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.15f), contentColor = if (markedForRemoval) Color.Red else Color.White),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Remove", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
    }
}