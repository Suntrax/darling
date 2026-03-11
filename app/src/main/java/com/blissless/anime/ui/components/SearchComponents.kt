package com.blissless.anime.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.MainViewModel
import com.blissless.anime.ui.screens.DetailedAnimeScreen
import com.blissless.anime.data.models.toDetailedAnimeData
import kotlinx.coroutines.delay
import java.util.Locale
import com.blissless.anime.data.models.StoredFavorite

@Composable
fun SearchOverlay(
    viewModel: MainViewModel,
    isOled: Boolean,
    simplifyAnimeDetails: Boolean = true,
    currentlyWatching: List<AnimeMedia>,
    planningToWatch: List<AnimeMedia>,
    completed: List<AnimeMedia>,
    onHold: List<AnimeMedia>,
    dropped: List<AnimeMedia>,
    localFavorites: Map<Int, StoredFavorite>,
    onToggleFavorite: (AnimeMedia) -> Unit,
    onClose: () -> Unit,
    onPlayEpisode: (AnimeMedia, Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ExploreAnime>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

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

    // Calculate canAddFavorite locally based on max 10 favorites limit
    val canAddFavoriteLocal = remember(localFavorites) {
        localFavorites.size < 10
    }

    // Back handler for search overlay - close search on back press
    BackHandler(enabled = true) {
        onClose()
    }

    // Debounced search - LaunchedEffect automatically cancels when searchQuery changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
        } else {
            isSearching = true
            delay(500) // Debounce delay
            val results = viewModel.searchAnime(searchQuery)
            searchResults = results
            isSearching = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ) // Consume all clicks to make background unclickable
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

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search AniList...",
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                }
                                innerTextField()
                            }
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
        val isAnimeFavorite = localFavorites.containsKey(selectedAnime!!.id)
        if (simplifyAnimeDetails) {
            // Simple dialog
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
                },
                onUpdateStatus = { status ->
                    viewModel.addExploreAnimeToList(selectedAnime!!, status)
                },
                onRemove = {
                    viewModel.removeAnimeFromList(selectedAnime!!.id)
                }
            )
        } else {
            // Rich detailed dialog
            DetailedAnimeScreen(
                anime = selectedAnime!!.toDetailedAnimeData(),
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = savedAnimeMap[selectedAnime!!.id],
                isFavorite = isAnimeFavorite,
                onToggleFavorite = { _ ->
                    // Convert ExploreAnime to AnimeMedia for the callback
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
                        listEntryId = 0,
                        year = selectedAnime!!.year
                    )
                    onToggleFavorite(animeMedia)
                },
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
                    viewModel.addExploreAnimeToList(selectedAnime!!, "CURRENT")
                    onPlayEpisode(animeMedia, episode)
                    showDetailDialog = false
                },
                onUpdateStatus = { status ->
                    if (status != null) {
                        viewModel.addExploreAnimeToList(selectedAnime!!, status)
                    }
                },
                onRemove = {
                    viewModel.removeAnimeFromList(selectedAnime!!.id)
                    showDetailDialog = false
                },
                isLoggedIn = true
            )
        }
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
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = "Saved",
                            tint = HomeStatusColors.getColor(currentStatus),
                            modifier = Modifier.size(20.dp)
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

                    // Year before episode count
                    anime.year?.let { year ->
                        Text(
                            "$year",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
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
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = HomeStatusColors.getContainerColor(currentStatus)
                    ) {
                        Text(
                            text = when(currentStatus) {
                                "CURRENT" -> "Watching"
                                "PLANNING" -> "Planning"
                                "COMPLETED" -> "Completed"
                                "PAUSED" -> "On Hold"
                                "DROPPED" -> "Dropped"
                                else -> currentStatus
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = HomeStatusColors.getColor(currentStatus),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = MaterialTheme.colorScheme.primary,
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

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = HomeStatusColors.getContainerColor(currentStatus)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        // Icon based on status
                                        Icon(
                                            imageVector = when(currentStatus) {
                                                "CURRENT" -> Icons.Default.PlayArrow
                                                "PLANNING" -> Icons.Default.Bookmark
                                                "COMPLETED" -> Icons.Default.Check
                                                "PAUSED" -> Icons.Default.Pause
                                                "DROPPED" -> Icons.Default.Close
                                                else -> Icons.Default.Info
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = HomeStatusColors.getColor(currentStatus)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when(currentStatus) {
                                                "CURRENT" -> "Watching"
                                                "PLANNING" -> "Planning"
                                                "COMPLETED" -> "Completed"
                                                "PAUSED" -> "On Hold"
                                                "DROPPED" -> "Dropped"
                                                else -> currentStatus
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = HomeStatusColors.getColor(currentStatus)
                                        )
                                    }
                                }
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusButton(
                        icon = Icons.Default.PlayArrow,
                        label = "Watching",
                        selected = selectedStatus == "CURRENT",
                        selectedColor = HomeStatusColors.getColor("CURRENT"),
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
                        selectedColor = HomeStatusColors.getColor("PLANNING"),
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

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Completed and On Hold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusButton(
                        icon = Icons.Default.Check,
                        label = "Completed",
                        selected = selectedStatus == "COMPLETED",
                        selectedColor = HomeStatusColors.getColor("COMPLETED"),
                        onClick = {
                            selectedStatus = "COMPLETED"
                            showAnimation = true
                            Toast.makeText(context, "Marked as Completed", Toast.LENGTH_SHORT)
                                .show()
                            onUpdateStatus("COMPLETED")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (selectedStatus == "COMPLETED" && showAnimation) scale else 1f)
                    )

                    StatusButton(
                        icon = Icons.Default.Pause,
                        label = "On Hold",
                        selected = selectedStatus == "PAUSED",
                        selectedColor = HomeStatusColors.getColor("PAUSED"),
                        onClick = {
                            selectedStatus = "PAUSED"
                            showAnimation = true
                            Toast.makeText(context, "Added to On Hold", Toast.LENGTH_SHORT).show()
                            onUpdateStatus("PAUSED")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (selectedStatus == "PAUSED" && showAnimation) scale else 1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 3: Dropped and Remove
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusButton(
                        icon = Icons.Default.Delete,
                        label = "Dropped",
                        selected = selectedStatus == "DROPPED",
                        selectedColor = HomeStatusColors.getColor("DROPPED"),
                        onClick = {
                            selectedStatus = "DROPPED"
                            showAnimation = true
                            Toast.makeText(context, "Marked as Dropped", Toast.LENGTH_SHORT).show()
                            onUpdateStatus("DROPPED")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (selectedStatus == "DROPPED" && showAnimation) scale else 1f)
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
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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