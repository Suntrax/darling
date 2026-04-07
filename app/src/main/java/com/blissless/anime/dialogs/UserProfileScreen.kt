package com.blissless.anime.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
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
import com.blissless.anime.data.models.ExploreAnime

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
    
    val jikanFavorites by viewModel.jikanFavorites.collectAsState()
    val jikanHistory by viewModel.jikanHistory.collectAsState()
    val malUsername by viewModel.malUsernameFlow.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()

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
                            isOled = isOled
                        )
                        UserProfileSection.FAVORITES -> FavoritesContent(
                            favorites = jikanFavorites?.anime ?: emptyList(),
                            isOled = isOled,
                            onAnimeClick = { onShowDetailedAnimeFromMal(it.malId) }
                        )
                        UserProfileSection.HISTORY -> HistoryContent(
                            history = jikanHistory?.anime ?: emptyList(),
                            isOled = isOled,
                            onAnimeClick = { onShowDetailedAnimeFromMal(it.malId) }
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
                        badge = jikanFavorites?.anime?.size
                    )

                    UserProfileNavButton(
                        icon = Icons.Default.History,
                        title = "History",
                        isSelected = selectedSection == UserProfileSection.HISTORY,
                        onClick = { selectedSection = UserProfileSection.HISTORY },
                        badge = jikanHistory?.anime?.size
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
    isOled: Boolean
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
            "My Anime Profile",
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
    onAnimeClick: (JikanHistoryEntry) -> Unit
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
            items(history) { entry ->
                HistoryItem(
                    entry = entry,
                    isOled = isOled,
                    onClick = { onAnimeClick(entry) }
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: JikanHistoryEntry,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            entry.episodesWatched?.let { ep ->
                Text(
                    "Episode $ep",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
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