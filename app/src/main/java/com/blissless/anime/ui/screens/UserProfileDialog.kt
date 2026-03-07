package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.AnimeMedia
import com.blissless.anime.ExploreAnime
import com.blissless.anime.MainViewModel
import com.blissless.anime.UserActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserProfileDialog(
    viewModel: MainViewModel,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onShowAnimeDialog: (ExploreAnime) -> Unit
) {
    val context = LocalContext.current
    val userName by viewModel.userName.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()
    val userId by viewModel.userId.collectAsState()
    val localFavorites by viewModel.localFavorites.collectAsState()
    val userActivity by viewModel.userActivity.collectAsState()
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val completed by viewModel.completed.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Favorites", "History", "Rate")

    // Fetch activity when userId becomes available
    LaunchedEffect(userId) {
        if (userId != null) {
            viewModel.fetchUserActivity()
        }
    }

    // Refetch activity when avatar is clicked
    var avatarClickCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(avatarClickCount) {
        if (avatarClickCount > 0 && userId != null) {
            viewModel.fetchUserActivity()
        }
    }

    // Get favorite anime from the lists
    val favoriteAnime = remember(localFavorites, currentlyWatching, completed) {
        val allAnime = currentlyWatching + completed
        allAnime.filter { localFavorites.contains(it.id) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else Color(0xFF1A1A1A)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with user info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar - click to refresh activity
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable {
                                avatarClickCount++
                                Toast.makeText(context, "Refreshing history...", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        if (userAvatar != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(userAvatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Avatar (tap to refresh)",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Refresh indicator overlay
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            userName ?: "User",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "AniList Profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[selectedTab])
                                    .height(3.dp)
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                    )
                            )
                        }
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> FavoritesTab(
                            viewModel = viewModel,
                            favoriteAnime = favoriteAnime,
                            localFavorites = localFavorites,
                            isOled = isOled,
                            onAnimeClick = { anime ->
                                // Convert to ExploreAnime and show dialog
                                val exploreAnime = ExploreAnime(
                                    id = anime.id,
                                    title = anime.title,
                                    cover = anime.cover,
                                    banner = anime.banner,
                                    episodes = anime.totalEpisodes,
                                    latestEpisode = anime.latestEpisode,
                                    averageScore = anime.averageScore,
                                    genres = anime.genres,
                                    year = anime.year
                                )
                                onShowAnimeDialog(exploreAnime)
                            }
                        )
                        1 -> HistoryTab(
                            activities = userActivity,
                            isOled = isOled
                        )
                        2 -> RateTab(
                            viewModel = viewModel,
                            isOled = isOled,
                            completedOnly = completed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    viewModel: MainViewModel,
    favoriteAnime: List<AnimeMedia>,
    localFavorites: Set<Int>,
    isOled: Boolean,
    onAnimeClick: (AnimeMedia) -> Unit
) {
    val context = LocalContext.current
    var showSearchDialog by remember { mutableStateOf(false) }
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val completed by viewModel.completed.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val allAnime = remember(currentlyWatching, completed) {
        currentlyWatching + completed
    }

    val filteredAnime = remember(searchQuery, allAnime) {
        if (searchQuery.isEmpty()) allAnime
        else allAnime.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column {
        // Header with search button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Favorites (${localFavorites.size}/10)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSearchDialog = true }) {
                Icon(Icons.Default.Search, "Add favorites", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (favoriteAnime.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No favorites yet", color = Color.White.copy(alpha = 0.7f))
                    Text("Tap the search icon to add", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favoriteAnime) { anime ->
                    FavoriteAnimeItem(
                        anime = anime,
                        isOled = isOled,
                        isFavorite = true,
                        onClick = { onAnimeClick(anime) },
                        onToggleFavorite = {
                            viewModel.toggleLocalFavorite(anime.id)
                            Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Search Dialog for adding favorites
    if (showSearchDialog) {
        Dialog(onDismissRequest = { showSearchDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOled) Color.Black else Color(0xFF1A1A1A)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Add Favorite (${localFavorites.size}/10)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showSearchDialog = false }) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search your anime...", color = Color.White.copy(alpha = 0.5f)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "${filteredAnime.size} anime in your list",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredAnime) { anime ->
                            val isFavorite = localFavorites.contains(anime.id)
                            FavoriteAnimeItem(
                                anime = anime,
                                isOled = isOled,
                                isFavorite = isFavorite,
                                onClick = { onAnimeClick(anime) },
                                onToggleFavorite = {
                                    if (!isFavorite && localFavorites.size >= 10) {
                                        Toast.makeText(context, "Maximum 10 favorites! Remove one first.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.toggleLocalFavorite(anime.id)
                                        Toast.makeText(
                                            context,
                                            if (isFavorite) "Removed from favorites" else "Added to favorites",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteAnimeItem(
    anime: AnimeMedia,
    isOled: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
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
                model = anime.cover,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(50.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                anime.year?.let { year ->
                    Text(
                        "$year - ${anime.totalEpisodes} eps",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                anime.averageScore?.let { score ->
                    Text(
                        "★ ${score / 10.0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD700)
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(
    activities: List<UserActivity>,
    isOled: Boolean
) {
    Column {
        Text(
            "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        if (activities.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No recent activity", color = Color.White.copy(alpha = 0.7f))
                    Text("Watch some anime to see your history", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activities, key = { it.id }) { activity ->
                    ActivityItem(
                        activity = activity,
                        isOled = isOled
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(
    activity: UserActivity,
    isOled: Boolean
) {
    // Use 24h format
    val timeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    val timeString = remember(activity.createdAt) {
        timeFormat.format(Date(activity.createdAt * 1000L))
    }

    val statusText = when (activity.status) {
        "watched episode" -> "Watched episode ${activity.progress}"
        "completed" -> "Completed"
        "started" -> "Started watching"
        "plans to watch" -> "Plans to watch"
        "dropped" -> "Dropped"
        "paused watching" -> "Paused"
        else -> activity.status ?: "Updated"
    }

    val statusIcon = when (activity.status) {
        "watched episode" -> Icons.Default.PlayArrow
        "completed" -> Icons.Default.Check
        "started" -> Icons.Default.PlayCircleOutline
        "plans to watch" -> Icons.Default.Bookmark
        "dropped" -> Icons.Default.Delete
        "paused watching" -> Icons.Default.Pause
        else -> Icons.Default.Update
    }

    val statusColor = when (activity.status) {
        "watched episode" -> Color(0xFF2196F3)
        "completed" -> Color(0xFF4CAF50)
        "started" -> Color(0xFF2196F3)
        "plans to watch" -> Color(0xFF9C27B0)
        "dropped" -> Color(0xFFF44336)
        "paused watching" -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOled) Color(0xFF1A1A1A) else Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show anime cover image if available, otherwise status icon
            if (activity.mediaCover.isNotEmpty()) {
                AsyncImage(
                    model = activity.mediaCover,
                    contentDescription = activity.mediaTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(50.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                // Fallback to status icon if no cover
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    activity.mediaTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun RateTab(
    viewModel: MainViewModel,
    isOled: Boolean,
    completedOnly: List<AnimeMedia>
) {
    val context = LocalContext.current
    val localFavorites by viewModel.localFavorites.collectAsState()
    var selectedAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    var selectedRating by remember { mutableIntStateOf(0) }

    Column {
        Text(
            "Rate your anime (1-100)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Rate completed anime. Star = Favorite",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))

        if (selectedAnime != null) {
            // Rating UI
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOled) Color(0xFF1A1A1A) else Color(0xFF2A2A2A)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        selectedAnime!!.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))

                    // Rating slider (1-100)
                    Text(
                        if (selectedRating > 0) "$selectedRating / 100" else "Slide to rate",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedRating > 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                    )

                    Spacer(Modifier.height(8.dp))

                    Slider(
                        value = selectedRating.toFloat(),
                        onValueChange = { selectedRating = it.toInt() },
                        valueRange = 0f..100f,
                        steps = 100,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )

                    // Quick rating buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(25, 50, 75, 100).forEach { rating ->
                            OutlinedButton(
                                onClick = { selectedRating = rating },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (selectedRating == rating) MaterialTheme.colorScheme.primary else Color.White
                                )
                            ) {
                                Text("$rating")
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedAnime = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (selectedRating > 0) {
                                    viewModel.updateAnimeRating(selectedAnime!!.id, selectedRating)
                                    Toast.makeText(context, "Rating saved: $selectedRating/100", Toast.LENGTH_SHORT).show()
                                    selectedAnime = null
                                    selectedRating = 0
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedRating > 0
                        ) {
                            Text("Save Rating")
                        }
                    }
                }
            }
        } else {
            // Anime selection list - ONLY COMPLETED
            if (completedOnly.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No completed anime", color = Color.White.copy(alpha = 0.7f))
                        Text("Complete some anime to rate them", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(completedOnly) { anime ->
                        RateAnimeItem(
                            anime = anime,
                            isOled = isOled,
                            isFavorite = localFavorites.contains(anime.id),
                            onRateClick = {
                                selectedAnime = anime
                                selectedRating = 0
                            },
                            onFavoriteClick = {
                                if (!localFavorites.contains(anime.id) && localFavorites.size >= 10) {
                                    Toast.makeText(context, "Maximum 10 favorites! Remove one first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.toggleLocalFavorite(anime.id)
                                    Toast.makeText(
                                        context,
                                        if (localFavorites.contains(anime.id)) "Removed from favorites" else "Added to favorites",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RateAnimeItem(
    anime: AnimeMedia,
    isOled: Boolean,
    isFavorite: Boolean,
    onRateClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRateClick() },
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
                model = anime.cover,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(50.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                anime.year?.let { year ->
                    Text(
                        "$year - ${anime.totalEpisodes} eps",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            // Favorite button (star) - YELLOW when favorite
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f)
                )
            }
            // Chevron arrow
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Rate",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// Tab indicator offset modifier
private fun Modifier.tabIndicatorOffset(
    currentTabPosition: TabPosition
): Modifier = this
    .fillMaxWidth()
    .wrapContentSize(Alignment.BottomStart)
    .offset(x = currentTabPosition.left)
    .width(currentTabPosition.width)
