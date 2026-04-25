package com.blissless.anime.ui.screens.profile

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.blissless.anime.MainViewModel
import com.blissless.anime.api.jikan.JikanFavoriteAnime
import com.blissless.anime.api.jikan.JikanHistoryEntry
import com.blissless.anime.api.jikan.JikanImageUrls
import com.blissless.anime.api.jikan.JikanImages
import com.blissless.anime.api.myanimelist.LoginProvider
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.data.models.UserAnimeStats
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

data class HistoryData(val entries: List<JikanHistoryEntry>, val statuses: List<String>, val progressList: List<String>)

enum class UserProfileSection {
    ABOUT_ME, FAVORITES, HISTORY
}

@Composable
fun UserProfileScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    preferEnglishTitles: Boolean = true,
    onDismiss: () -> Unit,
    onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit,
    onShowDetailedAnimeFromMal: (Int) -> Unit
) {
    var selectedSection by remember { mutableStateOf(UserProfileSection.ABOUT_ME) }
    val context = LocalContext.current
    
    val loginProvider by viewModel.loginProvider.collectAsState()
    val jikanFavorites by viewModel.jikanFavorites.collectAsState()
    val jikanHistory by viewModel.jikanHistory.collectAsState()
    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val userActivity by viewModel.userActivity.collectAsState()
    val userStats by viewModel.userStats.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userBanner by viewModel.userBanner.collectAsState()
    val userBio by viewModel.userBio.collectAsState()
    val userSiteUrl by viewModel.userSiteUrl.collectAsState()
    val userCreatedAt by viewModel.userCreatedAt.collectAsState()

    LaunchedEffect(loginProvider) {
        if (loginProvider == LoginProvider.ANILIST) {
            viewModel.loadAniListFavoritesFromStorage()
            viewModel.fetchAniListFavorites()
            viewModel.fetchUserActivity()
            viewModel.fetchUserStats()
        }
    }

    // Lines 135-155 (the only changed section)
    val favorites: List<JikanFavoriteAnime> = when (loginProvider) {
        LoginProvider.ANILIST -> {
            aniListFavorites.map { aniListFavorite ->
                val coverUrl = aniListFavorite.coverImage?.extraLarge
                    ?: ""
                JikanFavoriteAnime(
                    malId = 0,
                    title = aniListFavorite.title.romaji ?: aniListFavorite.title.english ?: "",
                    titleEnglish = aniListFavorite.title.english,
                    images = JikanImages(
                        jpg = JikanImageUrls(coverUrl)
                    ),
                    year = aniListFavorite.seasonYear
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
                    titleEnglish = activity.mediaTitleEnglish,
                    images = JikanImages(
                        jpg = JikanImageUrls(activity.mediaCover)
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
    
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    val slideOffset = remember { Animatable(1000f) }
    val dismissSlideOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        slideOffset.animateTo(
            targetValue = 0f,
            animationSpec = tween(200, easing = LinearEasing)
        )
    }
    
    fun dismissWithAnimation() {
        scope.launch {
            dismissSlideOffset.snapTo(0f)
            dismissSlideOffset.animateTo(
                targetValue = 1000f,
                animationSpec = tween(150, easing = LinearEasing)
            )
            onDismiss()
        }
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (slideOffset.value > 0 || dismissSlideOffset.value > 0) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "alpha"
    )

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (slideOffset.value + dismissSlideOffset.value).roundToInt()) }
                .graphicsLayer {
                    this.alpha = alpha
                }
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
                        .padding(
                            top = statusBarsPadding.calculateTopPadding() + 8.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 8.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedSection == UserProfileSection.ABOUT_ME && userSiteUrl != null) {
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, userSiteUrl)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                        }) {
                            Icon(Icons.Default.Share, "Share", tint = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                    
                    Text(
                        when (selectedSection) {
                            UserProfileSection.ABOUT_ME -> "About Me"
                            UserProfileSection.FAVORITES -> "Favorites"
                            UserProfileSection.HISTORY -> "History"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = { dismissWithAnimation() }) {
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
                            username = userName ?: "User",
                            isOled = isOled,
                            loginProvider = loginProvider,
                            userAvatar = userAvatar,
                            userBanner = userBanner,
                            userBio = userBio,
                            userSiteUrl = userSiteUrl,
                            userCreatedAt = userCreatedAt,
                            userStats = userStats,
                            onShareClick = { /* TODO */ }
                        )
                        UserProfileSection.FAVORITES -> FavoritesContent(
                            favorites = favorites,
                            isOled = isOled,
                            preferEnglishTitles = preferEnglishTitles,
                            onAnimeClick = { onShowDetailedAnimeFromMal(it.malId) }
                        )
                        UserProfileSection.HISTORY -> HistoryContent(
                            history = history,
                            isOled = isOled,
                            preferEnglishTitles = preferEnglishTitles,
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
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 16.dp,
                            bottom = navigationBarsPadding.calculateBottomPadding() + 16.dp
                        ),
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
    loginProvider: LoginProvider,
    userAvatar: String? = null,
    userBanner: String? = null,
    userBio: String? = null,
    userSiteUrl: String? = null,
    userCreatedAt: Long? = null,
    userStats: UserAnimeStats? = null,
    onShareClick: () -> Unit = {}
) {
    val bgColor = if (isOled) Color.Black else Color(0xFF1A1A1A)
    var showFullscreenAvatar by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Background banner - show full banner without cropping sides
        userBanner?.let { bannerUrl ->
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Inside
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(160.dp))
            
            // Avatar (clickable)
            Box {
                userAvatar?.let { avatarUrl ->
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .clickable { showFullscreenAvatar = true },
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Username
            Text(
                username,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Bio
            userBio?.let { bio ->
                if (bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        bio.take(150) + if (bio.length > 150) "..." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 3,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Created date
            userCreatedAt?.let { timestamp ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Joined ${formatDate(timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Stats cards
            if (userStats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard(
                        value = userStats.count.toString(),
                        label = "Anime",
                        color = Color(0xFF2196F3)
                    )
                    StatCard(
                        value = formatEpisodes(userStats.episodesWatched),
                        label = "Episodes",
                        color = Color(0xFF4CAF50)
                    )
                    StatCard(
                        value = userStats.meanScore?.let { "%.1f".format(it / 10.0) } ?: "-",
                        label = "Mean",
                        color = Color(0xFFFFC107)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Time watched
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Total: ${formatMinutesWatched(userStats.minutesWatched)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }

        // Fullscreen avatar dialog
        if (showFullscreenAvatar) {
            Dialog(onDismissRequest = { showFullscreenAvatar = false }) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { showFullscreenAvatar = false }
                            }
                    )
                    userAvatar?.let { avatarUrl ->
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    color: Color
) {
    Card(
        modifier = Modifier.width(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatEpisodes(episodes: Int): String = when {
    episodes >= 1000 -> "%.1fK".format(episodes / 1000.0)
    else -> episodes.toString()
}

private fun formatMinutesWatched(minutes: Int): String {
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "$days days"
        hours > 0 -> "$hours hours"
        else -> "$minutes min"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMMM, yyyy", Locale("de", "DE"))
    return sdf.format(Date(timestamp * 1000))
}

@Composable
private fun FavoritesContent(
    favorites: List<JikanFavoriteAnime>,
    isOled: Boolean,
    preferEnglishTitles: Boolean,
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
        val listState = rememberLazyListState()
        val resultsVisible = remember { mutableStateOf(true) }
        
        LaunchedEffect(favorites) { resultsVisible.value = true }
        
        val cinematicProgress by animateFloatAsState(
            targetValue = if (resultsVisible.value) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "cinematicProgress"
        )
        
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(favorites) { index, anime ->
                val isScrolling by remember {
                    derivedStateOf { listState.isScrollInProgress }
                }
                
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val itemInfo = visibleItems.find { it.index == index }
                
                val centerOffset = if (itemInfo != null) {
                    val itemCenter = itemInfo.offset + itemInfo.size / 2
                    val screenCenter = (layoutInfo.viewportSize.height / 2).toFloat()
                    (itemCenter - screenCenter) / screenCenter
                } else {
                    0f
                }
                
                val animatedOffset by animateFloatAsState(
                    targetValue = if (isScrolling) centerOffset.coerceIn(-2f, 2f) else 0f,
                    animationSpec = if (isScrolling) {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    },
                    label = "centerOffset"
                )
                
                val staggerDelay = minOf(index, 15) * 40f
                val staggerMs = staggerDelay / 1000f
                val rawProgress = ((cinematicProgress - staggerMs) / (1f - staggerMs))
                val easedProgress = easeOutCubic(rawProgress.coerceAtMost(1f))
                
                val introScale = 0.3f + easedProgress * 0.7f
                val introAlpha = easedProgress
                val introTranslationY = -40f * (1f - easedProgress)
                
                val scrollScale = 1f - (animatedOffset.absoluteValue * 0.2f).coerceAtMost(0.2f)
                val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
                val scrollParallax = animatedOffset * 25f
                
                val finalScale = scrollScale * introScale
                val finalAlpha = (scrollAlpha * introAlpha).coerceIn(0f, 1f)
                val finalTranslationY = scrollParallax + introTranslationY
                
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = finalScale
                            scaleY = finalScale
                            alpha = finalAlpha
                            translationY = finalTranslationY
                        }
                ) {
                    FavoriteItem(
                        anime = anime,
                        isOled = isOled,
                        preferEnglishTitles = preferEnglishTitles,
                        onClick = { onAnimeClick(anime) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteItem(
    anime: JikanFavoriteAnime,
    isOled: Boolean,
    preferEnglishTitles: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(
            brush = Brush.linearGradient(
                colors = if (isOled) listOf(Color(0xFF3A3A3A), Color(0xFF1A1A1A))
                else listOf(Color(0xFF4A4A5A), Color(0xFF2A2A3A)),
                start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        )) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = anime.images.jpg?.imageUrl, contentDescription = anime.title,
                    modifier = Modifier.width(70.dp).height(95.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val displayTitle = if (preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty()) anime.titleEnglish else anime.title
                    Text(displayTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    anime.year?.let { year ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(year.toString(), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    history: List<JikanHistoryEntry>,
    isOled: Boolean,
    preferEnglishTitles: Boolean,
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
        val listState = rememberLazyListState()
        val resultsVisible = remember { mutableStateOf(true) }
        
        LaunchedEffect(history) { resultsVisible.value = true }
        
        val cinematicProgress by animateFloatAsState(
            targetValue = if (resultsVisible.value) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "cinematicProgress"
        )
        
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(history) { index, entry ->
                val isScrolling by remember {
                    derivedStateOf { listState.isScrollInProgress }
                }
                
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val itemInfo = visibleItems.find { it.index == index }
                
                val centerOffset = if (itemInfo != null) {
                    val itemCenter = itemInfo.offset + itemInfo.size / 2
                    val screenCenter = (layoutInfo.viewportSize.height / 2).toFloat()
                    (itemCenter - screenCenter) / screenCenter
                } else {
                    0f
                }
                
                val animatedOffset by animateFloatAsState(
                    targetValue = if (isScrolling) centerOffset.coerceIn(-2f, 2f) else 0f,
                    animationSpec = if (isScrolling) {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    },
                    label = "centerOffset"
                )
                
                val staggerDelay = minOf(index, 15) * 40f
                val staggerMs = staggerDelay / 1000f
                val rawProgress = ((cinematicProgress - staggerMs) / (1f - staggerMs))
                val easedProgress = easeOutCubic(rawProgress.coerceAtMost(1f))
                
                val introScale = 0.3f + easedProgress * 0.7f
                val introAlpha = easedProgress
                val introTranslationY = -40f * (1f - easedProgress)
                
                val scrollScale = 1f - (animatedOffset.absoluteValue * 0.2f).coerceAtMost(0.2f)
                val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
                val scrollParallax = animatedOffset * 25f
                
                val finalScale = scrollScale * introScale
                val finalAlpha = (scrollAlpha * introAlpha).coerceIn(0f, 1f)
                val finalTranslationY = scrollParallax + introTranslationY
                
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = finalScale
                            scaleY = finalScale
                            alpha = finalAlpha
                            translationY = finalTranslationY
                        }
                ) {
                    HistoryItem(
                        entry = entry,
                        isOled = isOled,
                        preferEnglishTitles = preferEnglishTitles,
                        onClick = { onAnimeClick(entry) },
                        status = statuses.getOrNull(index),
                        progress = progressList.getOrNull(index)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: JikanHistoryEntry,
    isOled: Boolean,
    preferEnglishTitles: Boolean,
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
    
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(
            brush = Brush.linearGradient(
                colors = if (isOled) listOf(Color(0xFF3A3A3A), Color(0xFF1A1A1A))
                else listOf(Color(0xFF4A4A5A), Color(0xFF2A2A3A)),
                start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        )) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = entry.images.jpg?.imageUrl, contentDescription = entry.title,
                    modifier = Modifier.width(70.dp).height(95.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val displayTitle = if (preferEnglishTitles && !entry.titleEnglish.isNullOrEmpty()) entry.titleEnglish else entry.title
                    Text(displayTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                        Text("$statusLabel $progress", color = statusColor, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    entry.date?.let { date -> Text(date, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMMM, yyyy - HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}

private fun easeOutCubic(t: Float): Float = (1f - (1f - t) * (1f - t) * (1f - t)).coerceIn(0f, 1f)
