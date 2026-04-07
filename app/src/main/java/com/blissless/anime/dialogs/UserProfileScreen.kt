package com.blissless.anime.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.blissless.anime.MainViewModel
import com.blissless.anime.data.JikanFavoriteAnime
import com.blissless.anime.data.JikanHistoryEntry
import com.blissless.anime.data.LoginProvider
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.data.models.UserActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryData(val entries: List<JikanHistoryEntry>, val statuses: List<String>, val progressList: List<String>)

enum class UserProfileSection {
    ABOUT_ME, FAVORITES, HISTORY
}

@Composable
fun UserProfileScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit,
    onShowDetailedAnimeFromMal: (Int) -> Unit
) {
    var selectedSection by remember { mutableStateOf(UserProfileSection.ABOUT_ME) }
    
    val loginProvider by viewModel.loginProvider.collectAsState()
    val jikanFavorites by viewModel.jikanFavorites.collectAsState()
    val jikanHistory by viewModel.jikanHistory.collectAsState()
    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val userActivity by viewModel.userActivity.collectAsState()
    val malUsername by viewModel.malUsernameFlow.collectAsState()

    LaunchedEffect(loginProvider) {
        if (loginProvider == LoginProvider.ANILIST) {
            viewModel.loadAniListFavoritesFromStorage()
            viewModel.fetchAniListFavorites()
            viewModel.fetchUserActivity()
        }
    }

    val favorites: List<JikanFavoriteAnime> = when (loginProvider) {
        LoginProvider.ANILIST -> {
            aniListFavorites.map { aniListFavorite ->
                JikanFavoriteAnime(
                    malId = 0,
                    title = aniListFavorite.title.english ?: aniListFavorite.title.romaji ?: "",
                    images = com.blissless.anime.data.JikanImages(
                        jpg = com.blissless.anime.data.JikanImageUrls(
                            aniListFavorite.coverImage?.large 
                                ?: aniListFavorite.coverImage?.medium 
                                ?: ""
                        )
                    )
                )
            }
        }
        LoginProvider.MAL -> jikanFavorites?.anime ?: emptyList()
        LoginProvider.NONE -> emptyList()
    }

    val historyData = when (loginProvider) {
        LoginProvider.ANILIST -> {
            val statuses = mutableListOf<String>()
            val entries = userActivity.take(50).map { activity ->
                val progressStr = activity.progress
                val episodeDisplay = progressStr?.let { prog ->
                    val nums = prog.filter { it.isDigit() }.chunked(2).map { it.toString().toIntOrNull() }.filterNotNull()
                    when {
                        nums.size >= 2 && nums[1] > nums[0] -> "${nums[0]}-${nums[1]}"
                        nums.isNotEmpty() -> "Episode ${nums[0]}"
                        else -> null
                    }
                }
                statuses.add(activity.status ?: "")
                JikanHistoryEntry(
                    malId = activity.mediaId,
                    title = activity.mediaTitle,
                    images = com.blissless.anime.data.JikanImages(
                        jpg = com.blissless.anime.data.JikanImageUrls(activity.mediaCover)
                    ),
                    episodesWatched = episodeDisplay?.filter { it.isDigit() }?.toIntOrNull(),
                    chaptersRead = null,
                    increment = null,
                    date = formatTimestamp(activity.createdAt)
                )
            }
            HistoryData(entries, statuses, userActivity.take(50).map { it.progress ?: "" })
        }
        LoginProvider.MAL -> {
            val malHistory = jikanHistory?.anime?.take(50) ?: emptyList()
            HistoryData(malHistory, malHistory.map { it.date ?: "" }, malHistory.map { "Episode ${it.episodesWatched ?: 0}" })
        }
        LoginProvider.NONE -> HistoryData(emptyList(), emptyList(), emptyList())
    }

    val history = historyData.entries
    val statuses = historyData.statuses
    val progressDisplay = historyData.progressList

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else Color(0xFF1A1A1A)
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    when (selectedSection) {
                        UserProfileSection.ABOUT_ME -> AboutMeContent(
                            username = malUsername ?: "User",
                            isOled = isOled,
                            loginProvider = loginProvider
                        )
                        UserProfileSection.FAVORITES -> FavoritesContent(
                            favorites = favorites,
                            isOled = isOled,
                            onAnimeClick = { onShowDetailedAnimeFromMal(it.malId) }
                        )
                        UserProfileSection.HISTORY -> HistoryContent(
                            history = history,
                            isOled = isOled,
                            onAnimeClick = { onShowDetailedAnimeFromMal(it.malId) },
                            statuses = statuses,
                            progressList = progressDisplay
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isOled) Color.Black else Color(0xFF121212))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserProfileNavButton(
                        icon = Icons.Default.Person,
                        title = "About Me",
                        isSelected = selectedSection == UserProfileSection.ABOUT_ME,
                        onClick = { selectedSection = UserProfileSection.ABOUT_ME }
                    )

                    UserProfileNavButton(
                        icon = Icons.Default.Favorite,
                        title = "Favorites",
                        isSelected = selectedSection == UserProfileSection.FAVORITES,
                        onClick = { selectedSection = UserProfileSection.FAVORITES },
                        badge = favorites.size
                    )

                    UserProfileNavButton(
                        icon = Icons.Default.History,
                        title = "History",
                        isSelected = selectedSection == UserProfileSection.HISTORY,
                        onClick = { selectedSection = UserProfileSection.HISTORY },
                        badge = history.size
                    )
                }
            }
        }
    }
}

@Composable
private fun UserProfileNavButton(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    badge: Int? = null
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
            )
            if (badge != null && badge > 0) {
                Badge(
                    modifier = Modifier.offset(x = 12.dp, y = (-4).dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        badge.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            title,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AboutMeContent(
    username: String,
    isOled: Boolean,
    loginProvider: LoginProvider
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            username,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            when (loginProvider) {
                LoginProvider.ANILIST -> "AniList Profile"
                LoginProvider.MAL -> "MyAnimeList Profile"
                LoginProvider.NONE -> "Not logged in"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun FavoritesContent(
    favorites: List<JikanFavoriteAnime>,
    isOled: Boolean,
    onAnimeClick: (JikanFavoriteAnime) -> Unit
) {
    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("No favorites yet", color = Color.White.copy(alpha = 0.7f))
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(favorites) { anime ->
                FavoriteItem(
                    anime = anime,
                    isOled = isOled,
                    onClick = { onAnimeClick(anime) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteItem(
    anime: JikanFavoriteAnime,
    isOled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOled) Color.Black else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = anime.images.jpg?.imageUrl,
            contentDescription = anime.title,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            anime.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HistoryContent(
    history: List<JikanHistoryEntry>,
    isOled: Boolean,
    onAnimeClick: (JikanHistoryEntry) -> Unit,
    statuses: List<String> = emptyList(),
    progressList: List<String> = emptyList()
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("No watch history", color = Color.White.copy(alpha = 0.7f))
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history.indices.toList()) { index ->
                HistoryItem(
                    entry = history[index],
                    isOled = isOled,
                    onClick = { onAnimeClick(history[index]) },
                    status = statuses.getOrNull(index),
                    progress = progressList.getOrNull(index)
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: JikanHistoryEntry,
    isOled: Boolean,
    onClick: () -> Unit,
    status: String? = null,
    progress: String? = null
) {
    val (statusIcon, statusColor, statusLabel) = when {
        status?.contains("completed", ignoreCase = true) == true -> Triple(Icons.Default.Check, Color(0xFF4CAF50), "Completed")
        status?.contains("watching", ignoreCase = true) == true -> Triple(Icons.Default.PlayArrow, Color(0xFF2196F3), "Watched")
        status?.contains("plan", ignoreCase = true) == true -> Triple(Icons.Default.Bookmark, Color(0xFF9C27B0), "Planning to Watch")
        status?.contains("hold", ignoreCase = true) == true -> Triple(Icons.Default.Pause, Color(0xFFFFC107), "On Hold")
        status?.contains("dropped", ignoreCase = true) == true -> Triple(Icons.Default.Delete, Color(0xFFF44336), "Dropped")
        else -> Triple(Icons.Default.PlayArrow, Color(0xFF2196F3), status ?: "")
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOled) Color.Black else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.images.jpg?.imageUrl,
            contentDescription = entry.title,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "$statusLabel $progress",
                    color = statusColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            entry.date?.let { date ->
                Text(
                    date,
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMMM, yyyy - HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}
