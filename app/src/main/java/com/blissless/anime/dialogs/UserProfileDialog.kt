package com.blissless.anime.dialogs

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.data.JikanFavoriteAnime
import com.blissless.anime.data.JikanHistoryEntry
import com.blissless.anime.data.JikanUserFavorites
import com.blissless.anime.data.JikanUserHistory
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.data.models.UserFavoriteAnime
import com.blissless.anime.MainViewModel
import com.blissless.anime.data.models.UserActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserProfileDialog(
    viewModel: MainViewModel,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userName by viewModel.userName.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()
    val userId by viewModel.userId.collectAsState()
    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val userActivity by viewModel.userActivity.collectAsState()
    val loginProvider by viewModel.loginProvider.collectAsState()
    val jikanFavorites by viewModel.jikanFavorites.collectAsState()
    val jikanHistory by viewModel.jikanHistory.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (loginProvider == com.blissless.anime.data.LoginProvider.MAL) {
        listOf("Favorites", "History")
    } else {
        listOf("Favorites", "History")
    }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    
    LaunchedEffect(selectedTab) {
        dragOffset = 0f
    }
    
    LaunchedEffect(dragOffset) {
        if (dragOffset != 0f) {
            kotlinx.coroutines.delay(300)
            dragOffset = 0f
        }
    }
    
    LaunchedEffect(userId, loginProvider) {
        if (userId != null) {
            if (loginProvider == com.blissless.anime.data.LoginProvider.ANILIST) {
                viewModel.fetchUserActivity()
                viewModel.fetchAniListFavorites()
            }
        }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        if (userAvatar != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(userAvatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "User Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                            if (loginProvider == com.blissless.anime.data.LoginProvider.MAL) "MyAnimeList Profile" else "AniList Profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showLogoutConfirmation = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }

                val tabPositions = remember { mutableStateListOf<TabPosition>() }
                val animatedOffset by animateDpAsState(
                    targetValue = if (selectedTab < tabPositions.size) tabPositions[selectedTab].left else 0.dp,
                    animationSpec = tween(durationMillis = 120),
                    label = "tabOffset"
                )
                val animatedWidth by animateDpAsState(
                    targetValue = if (selectedTab < tabPositions.size) tabPositions[selectedTab].width else 0.dp,
                    animationSpec = tween(durationMillis = 120),
                    label = "tabWidth"
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    divider = {},
                    indicator = { positions ->
                        tabPositions.clear()
                        tabPositions.addAll(positions)
                        if (selectedTab < positions.size) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentSize(Alignment.BottomStart)
                                    .offset(x = animatedOffset)
                                    .width(animatedWidth)
                                    .height(3.dp)
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

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    val maxWidth = constraints.maxWidth.toFloat()
                    val progress = (dragOffset / maxWidth).coerceIn(-1f, 1f)
                    
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            val targetOffset = if (targetState > initialState) maxWidth.toInt() else -maxWidth.toInt()
                            
                            (slideInHorizontally(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                initialOffsetX = { targetOffset }
                            ) + fadeIn(animationSpec = tween(300))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                targetOffsetX = { -targetOffset }
                            ) + fadeOut(animationSpec = tween(300)))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "tabAnimation"
                    ) { tab ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(tab) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { dragOffset = 0f },
                                        onDragEnd = {
                                            val currentProgress = dragOffset / maxWidth
                                            val threshold = 0.2f
                                            
                                            if (currentProgress < -threshold && tab == 0) {
                                                selectedTab = 1
                                            } else if (currentProgress > threshold && tab == 1) {
                                                selectedTab = 0
                                            }
                                            scope.launch {
                                                kotlinx.coroutines.delay(50)
                                                dragOffset = 0f
                                            }
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            val newOffset = dragOffset + dragAmount
                                            dragOffset = when {
                                                tab == 1 && newOffset < 0 -> newOffset * 0.1f
                                                tab == 0 && newOffset > 0 -> newOffset * 0.1f
                                                else -> newOffset.coerceIn(-maxWidth, maxWidth)
                                            }
                                        }
                                    )
                                }
                        ) {
                            if (kotlin.math.abs(progress) > 0.05f) {
                                val previewAlpha = kotlin.math.abs(progress) * 0.3f
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val direction = if (progress < 0) 1f else -1f
                                            translationX = direction * maxWidth * (1f - kotlin.math.abs(progress)) * 0.5f
                                            alpha = previewAlpha
                                        }
                                        .background(Color.Black)
                                )
                            }
                            
                            if (loginProvider == com.blissless.anime.data.LoginProvider.MAL) {
                                if (tab == 0) {
                                    JikanFavoritesTab(
                                        favorites = jikanFavorites,
                                        isOled = isOled,
                                        onAnimeClick = { anime ->
                                            onShowAnimeDialog(anime, null)
                                        }
                                    )
                                } else {
                                    JikanHistoryTab(
                                        history = jikanHistory,
                                        isOled = isOled
                                    )
                                }
                            } else if (tab == 0) {
                                FavoritesTab(
                                    viewModel = viewModel,
                                    favorites = aniListFavorites,
                                    isOled = isOled,
                                    onAnimeClick = { anime ->
                                        onShowAnimeDialog(anime, null)
                                    }
                                )
                            } else {
                                HistoryTab(
                                    activities = userActivity,
                                    isOled = isOled
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout from ${if (loginProvider == com.blissless.anime.data.LoginProvider.MAL) "MyAnimeList" else "AniList"}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutConfirmation = false
                        onDismiss()
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FavoritesTab(
    viewModel: MainViewModel,
    favorites: List<UserFavoriteAnime>,
    isOled: Boolean,
    onAnimeClick: (ExploreAnime) -> Unit
) {
    val context = LocalContext.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "My Favorites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "${favorites.size} anime",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (favorites.isEmpty()) {
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
                    Text("Add favorites on AniList", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favorites) { fav ->
                    AniListFavoriteItem(
                        favorite = fav,
                        isOled = isOled,
                        onClick = {
                            val exploreAnime = ExploreAnime(
                                id = fav.id,
                                title = fav.title.romaji ?: fav.title.english ?: "Anime",
                                cover = fav.coverImage?.large ?: "",
                                banner = null,
                                episodes = fav.episodes ?: 0,
                                latestEpisode = null,
                                averageScore = fav.averageScore,
                                genres = fav.genres ?: emptyList(),
                                year = fav.seasonYear,
                                format = null
                            )
                            onAnimeClick(exploreAnime)
                        },
                        onRemove = {
                            viewModel.toggleAniListFavorite(fav.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AniListFavoriteItem(
    favorite: UserFavoriteAnime,
    isOled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val displayTitle = favorite.title.romaji ?: favorite.title.english ?: "Anime"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.3f))
            ) {
                if (!favorite.coverImage?.large.isNullOrEmpty()) {
                    AsyncImage(
                        model = favorite.coverImage?.large,
                        contentDescription = displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                favorite.seasonYear?.let { year ->
                    Text(
                        "$year",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Remove from favorites",
                    tint = Color(0xFFFF1744),
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
    val timeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH)
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
        else -> activity.status
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
private fun JikanFavoritesTab(
    favorites: JikanUserFavorites?,
    isOled: Boolean,
    onAnimeClick: (ExploreAnime) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "My Favorites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "${favorites?.anime?.size ?: 0} anime",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (favorites == null || favorites.anime.isEmpty()) {
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
                    Text("Heart an anime to add it here", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favorites.anime) { anime ->
                    JikanFavoriteItem(
                        anime = anime,
                        isOled = isOled,
                        onClick = {
                            val exploreAnime = ExploreAnime(
                                id = anime.malId,
                                title = anime.title,
                                cover = anime.images.jpg?.imageUrl ?: "",
                                banner = null,
                                episodes = 0,
                                latestEpisode = null,
                                averageScore = null,
                                genres = emptyList(),
                                year = null,
                                format = null
                            )
                            onAnimeClick(exploreAnime)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun JikanFavoriteItem(
    anime: JikanFavoriteAnime,
    isOled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.3f))
            ) {
                anime.images.jpg?.imageUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        AsyncImage(
                            model = url,
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
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
            }
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "MAL Favorite",
                tint = Color(0xFFFF1744),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun JikanHistoryTab(
    history: JikanUserHistory?,
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

        if (history == null || history.anime.isEmpty()) {
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
                items(history.anime.take(20)) { entry ->
                    JikanHistoryItem(
                        entry = entry,
                        isOled = isOled
                    )
                }
            }
        }
    }
}

@Composable
private fun JikanHistoryItem(
    entry: JikanHistoryEntry,
    isOled: Boolean
) {
    val actionText = when {
        entry.increment != null -> entry.increment
        entry.episodesWatched != null -> "Episode ${entry.episodesWatched}"
        entry.chaptersRead != null -> "Chapter ${entry.chaptersRead}"
        else -> "Viewed"
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
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Blue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                entry.images.jpg?.imageUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        AsyncImage(
                            model = url,
                            contentDescription = entry.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } ?: Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Blue,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        actionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }
    }
}

private fun Modifier.tabIndicatorOffset(
    currentTabPosition: TabPosition
): Modifier = this
    .fillMaxWidth()
    .wrapContentSize(Alignment.BottomStart)
    .offset(x = currentTabPosition.left)
    .width(currentTabPosition.width)
