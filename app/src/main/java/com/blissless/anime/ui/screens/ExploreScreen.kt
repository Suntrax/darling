package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.AnimeMedia
import com.blissless.anime.ExploreAnime
import com.blissless.anime.MainViewModel
import com.blissless.anime.ui.screens.DetailedAnimeScreen
import com.blissless.anime.ui.screens.toDetailedAnimeData
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

// Status colors for different list types
val StatusColors = mapOf(
    "CURRENT" to Color(0xFF2196F3),    // Blue - Watching
    "PLANNING" to Color(0xFF9C27B0),   // Purple - Planning
    "COMPLETED" to Color(0xFF4CAF50),  // Green - Completed
    "PAUSED" to Color(0xFFFFC107),     // Amber - On Hold
    "DROPPED" to Color(0xFFF44336)     // Red - Dropped
)

val StatusLabels = mapOf(
    "CURRENT" to "Watching",
    "PLANNING" to "Planning",
    "COMPLETED" to "Completed",
    "PAUSED" to "On Hold",
    "DROPPED" to "Dropped"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onAnimeClick: (ExploreAnime) -> Unit,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false,
    showStatusColors: Boolean = true,
    simplifyAnimeDetails: Boolean = true,
    localFavorites: Set<Int> = emptySet(),
    canAddFavorite: Boolean = true,
    onToggleFavorite: (Int) -> Unit = {},
    onPlayEpisode: (AnimeMedia, Int) -> Unit = { _, _ -> },
    currentlyWatching: List<AnimeMedia> = emptyList(),
    planningToWatch: List<AnimeMedia> = emptyList(),
    completed: List<AnimeMedia> = emptyList(),
    onHold: List<AnimeMedia> = emptyList(),
    dropped: List<AnimeMedia> = emptyList(),
    isVisible: Boolean = true
) {
    val featuredAnime by viewModel.featuredAnime.collectAsState()
    val seasonalAnime by viewModel.seasonalAnime.collectAsState()
    val topSeries by viewModel.topSeries.collectAsState()
    val topMovies by viewModel.topMovies.collectAsState()
    val actionAnime by viewModel.actionAnime.collectAsState()
    val romanceAnime by viewModel.romanceAnime.collectAsState()
    val comedyAnime by viewModel.comedyAnime.collectAsState()
    val fantasyAnime by viewModel.fantasyAnime.collectAsState()
    val scifiAnime by viewModel.scifiAnime.collectAsState()
    val isLoading by viewModel.isLoadingExplore.collectAsState()

    // Create a map of animeId -> status for quick lookup
    val animeStatusMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        val map = mutableMapOf<Int, String>()
        currentlyWatching.forEach { map[it.id] = "CURRENT" }
        planningToWatch.forEach { map[it.id] = "PLANNING" }
        completed.forEach { map[it.id] = "COMPLETED" }
        onHold.forEach { map[it.id] = "PAUSED" }
        dropped.forEach { map[it.id] = "DROPPED" }
        map
    }

    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // Show appropriate dialog based on simplifyAnimeDetails setting
    if (showDialog && selectedAnime != null) {
        if (simplifyAnimeDetails) {
            // Simple dialog
            val isAnimeFavorite = localFavorites.contains(selectedAnime!!.id)
            ExploreAnimeDialog(
                anime = selectedAnime!!,
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = animeStatusMap[selectedAnime!!.id],
                showStatusColors = showStatusColors,
                isFavorite = isAnimeFavorite,
                canAddFavorite = canAddFavorite || isAnimeFavorite,
                onToggleFavorite = { onToggleFavorite(selectedAnime!!.id) },
                onDismiss = { showDialog = false },
                onAddToPlanning = {
                    viewModel.addExploreAnimeToList(selectedAnime!!, "PLANNING")
                },
                onAddToDropped = {
                    viewModel.addExploreAnimeToList(selectedAnime!!, "DROPPED")
                },
                onAddToOnHold = {
                    viewModel.addExploreAnimeToList(selectedAnime!!, "PAUSED")
                },
                onRemoveFromList = {
                    viewModel.removeAnimeFromList(selectedAnime!!.id)
                },
                onStartWatching = { episode ->
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
                    showDialog = false
                },
                isLoggedIn = isLoggedIn
            )
        } else {
            // Rich detailed dialog with favorite button
            val isAnimeFavorite = localFavorites.contains(selectedAnime!!.id)
            DetailedAnimeScreen(
                anime = selectedAnime!!.toDetailedAnimeData(),
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = animeStatusMap[selectedAnime!!.id],
                showStatusColors = showStatusColors,
                isFavorite = isAnimeFavorite,
                canAddFavorite = canAddFavorite || isAnimeFavorite,
                onDismiss = { showDialog = false },
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
                    showDialog = false
                },
                onUpdateStatus = { status ->
                    if (status != null) {
                        viewModel.addExploreAnimeToList(selectedAnime!!, status)
                    }
                },
                onRemove = {
                    viewModel.removeAnimeFromList(selectedAnime!!.id)
                    // FIXED: Don't close dialog when removing status
                },
                onToggleFavorite = {
                    onToggleFavorite(selectedAnime!!.id)
                },
                isLoggedIn = isLoggedIn
            )
        }
    }

    // Stable callbacks to avoid recomposition
    val onAnimeClickStable = remember<(ExploreAnime) -> Unit> {
        { anime ->
            selectedAnime = anime
            showDialog = true
        }
    }

    val onBookmarkClickStable = remember<(ExploreAnime) -> Unit> {
        { anime ->
            val status = animeStatusMap[anime.id]
            if (status != null) {
                viewModel.removeAnimeFromList(anime.id)
            } else {
                viewModel.addExploreAnimeToList(anime, "PLANNING")
            }
        }
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.forceRefreshExplore()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        // Use Column + verticalScroll instead of LazyColumn for smoother scrolling
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 80.dp)
        ) {
            // Featured Carousel with HorizontalPager
            if (featuredAnime.isNotEmpty()) {
                FeaturedCarousel(
                    animeList = featuredAnime,
                    onAnimeClick = onAnimeClickStable,
                    autoScrollEnabled = isVisible
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // This Season
            SectionTitle("This Season", isOled)
            if (seasonalAnime.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = seasonalAnime,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    isLoggedIn = isLoggedIn,
                    isOled = isOled
                )
            } else if (isLoading) {
                LoadingPlaceholder(isOled)
            }

            // Top Rated Series
            SectionTitle("Top Rated Series", isOled)
            if (topSeries.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = topSeries,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    isLoggedIn = isLoggedIn,
                    isOled = isOled
                )
            } else if (isLoading) {
                LoadingPlaceholder(isOled)
            }

            // Top Rated Movies
            SectionTitle("Top Rated Movies", isOled)
            if (topMovies.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = topMovies,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    isLoggedIn = isLoggedIn,
                    isOled = isOled
                )
            } else if (isLoading) {
                LoadingPlaceholder(isOled)
            }

            // Genre Sections
            GenreSection(
                title = "Action",
                animeList = actionAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable
            )

            GenreSection(
                title = "Romance",
                animeList = romanceAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable
            )

            GenreSection(
                title = "Comedy",
                animeList = comedyAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable
            )

            GenreSection(
                title = "Fantasy",
                animeList = fantasyAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable
            )

            GenreSection(
                title = "Sci-Fi",
                animeList = scifiAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Stop refreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }
}

@Composable
private fun GenreSection(
    title: String,
    animeList: List<ExploreAnime>,
    animeStatusMap: Map<Int, String>,
    showStatusColors: Boolean,
    isLoading: Boolean,
    isOled: Boolean,
    isLoggedIn: Boolean,
    onAnimeClick: (ExploreAnime) -> Unit,
    onBookmarkClick: (ExploreAnime) -> Unit
) {
    if (animeList.isEmpty() && !isLoading) return

    Column {
        SectionTitle(title, isOled)
        if (animeList.isNotEmpty()) {
            ExploreAnimeHorizontalList(
                animeList = animeList,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                onAnimeClick = onAnimeClick,
                onBookmarkClick = onBookmarkClick,
                isLoggedIn = isLoggedIn,
                isOled = isOled
            )
        } else if (isLoading) {
            LoadingPlaceholder(isOled)
        }
    }
}

@Composable
private fun SectionTitle(title: String, isOled: Boolean = false) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun LoadingPlaceholder(isOled: Boolean = false) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5, key = { "loading_$it" }) {
            Card(
                modifier = Modifier
                    .width(130.dp)
                    .height(200.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun FeaturedCarousel(
    animeList: List<ExploreAnime>,
    onAnimeClick: (ExploreAnime) -> Unit,
    autoScrollEnabled: Boolean = true,
    isVisible: Boolean = true
) {
    val pagerState = rememberPagerState(pageCount = { animeList.size })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Track animation direction - FIXED: properly sync with swipe
    var lastPage by remember { mutableIntStateOf(0) }
    var isAutoScrolling by remember { mutableStateOf(false) }

    // Determine animation direction based on page change
    val swipeDirection = remember { derivedStateOf {
        val current = pagerState.currentPage
        val diff = current - lastPage
        when {
            isAutoScrolling -> 1 // Auto-scroll always goes forward
            diff > 0 || (lastPage == animeList.size - 1 && current == 0) -> 1 // Forward
            diff < 0 || (lastPage == 0 && current == animeList.size - 1) -> -1 // Backward
            else -> 1
        }
    }}

    // Update last page after animation settles
    LaunchedEffect(pagerState.settledPage) {
        lastPage = pagerState.settledPage
    }

    // Auto-scroll with looping - FIXED: loops back to first from last
    LaunchedEffect(autoScrollEnabled, isVisible) {
        if (autoScrollEnabled && isVisible) {
            while (isVisible && isActive) {
                delay(4000) // FIXED: Reduced from 5000ms to 4000ms for faster transitions
                if (!isVisible || !isActive) break

                if (!pagerState.isScrollInProgress && isVisible && isActive) {
                    isAutoScrolling = true
                    // FIXED: Loop back to 0 when reaching end
                    val nextPage = if (pagerState.currentPage >= animeList.size - 1) 0
                    else pagerState.currentPage + 1
                    try {
                        pagerState.animateScrollToPage(
                            nextPage,
                            animationSpec = tween(400, easing = FastOutSlowInEasing) // FIXED: Faster animation
                        )
                        delay(200)
                    } catch (e: Exception) {
                        // Animation cancelled
                    }
                    isAutoScrolling = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Animated banner background
        val currentAnime = animeList.getOrElse(pagerState.currentPage) { animeList.first() }

        // AnimatedContent with FIXED direction sync
        AnimatedContent(
            targetState = currentAnime,
            transitionSpec = {
                // FIXED: Animation direction matches swipe direction
                if (swipeDirection.value >= 0) {
                    // Going forward (or looping): new from right, old to left
                    (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { it/2 })
                        .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { -it/2 })
                        .using(SizeTransform(clip = false))
                } else {
                    // Going backward: new from left, old to right
                    (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { -it/2 })
                        .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { it/2 })
                        .using(SizeTransform(clip = false))
                }
            },
            label = "BannerTransition"
        ) { anime ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(anime.banner ?: anime.cover)
                    .memoryCacheKey(anime.banner ?: anime.cover)
                    .diskCacheKey(anime.banner ?: anime.cover)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // Swipeable pager for info cards
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = true
        ) { page ->
            val anime = animeList[page]

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onAnimeClick(anime) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(anime.cover)
                                .memoryCacheKey(anime.cover)
                                .diskCacheKey(anime.cover)
                                .crossfade(false)
                                .build(),
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(50.dp)
                                .height(70.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                anime.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                anime.year?.let { year ->
                                    Text(
                                        "$year",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        " • ",
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    anime.genres.take(3).joinToString(" - "),
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                anime.averageScore?.let { score ->
                                    val displayScore = score / 10.0
                                    Text(
                                        "★ ${String.format(Locale.US, "%.1f", displayScore)}",
                                        color = Color(0xFFFFD700),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                anime.latestEpisode?.let { ep ->
                                    val releasedEp = ep - 1
                                    if (releasedEp > 0) {
                                        Text(
                                            "Ep $releasedEp ${if (anime.episodes > 0) "/ ${anime.episodes}" else ""}",
                                            color = Color.White.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Page indicators with animation
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(animeList.size) { index ->
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(if (index == pagerState.currentPage) 24.dp else 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index == pagerState.currentPage) Color.White
                            else Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

@Composable
fun ExploreAnimeHorizontalList(
    animeList: List<ExploreAnime>,
    animeStatusMap: Map<Int, String>,
    showStatusColors: Boolean,
    onAnimeClick: (ExploreAnime) -> Unit,
    onBookmarkClick: (ExploreAnime) -> Unit,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = animeList,
            key = { it.id }
        ) { anime ->
            ExploreAnimeCard(
                anime = anime,
                currentStatus = animeStatusMap[anime.id],
                showStatusColors = showStatusColors,
                onClick = { onAnimeClick(anime) },
                onBookmarkClick = { onBookmarkClick(anime) },
                isLoggedIn = isLoggedIn,
                isOled = isOled
            )
        }
    }
}

@Composable
fun ExploreAnimeCard(
    anime: ExploreAnime,
    currentStatus: String?,
    showStatusColors: Boolean,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false
) {
    val context = LocalContext.current

    var showAnimation by remember { mutableStateOf(false) }
    val bookmarkScale by animateFloatAsState(
        targetValue = if (showAnimation) 1.3f else 1f,
        animationSpec = tween(200),
        finishedListener = {
            if (showAnimation) {
                showAnimation = false
            }
        },
        label = "bookmarkScale"
    )

    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context)
            .data(anime.cover)
            .memoryCacheKey(anime.cover)
            .diskCacheKey(anime.cover)
            .crossfade(false)
            .build()
    }

    val statusIndicatorColor = remember(currentStatus, showStatusColors) {
        if (showStatusColors && currentStatus != null) {
            StatusColors[currentStatus] ?: Color.Transparent
        } else {
            Color.Transparent
        }
    }

    val displayScore = remember(anime.averageScore) {
        anime.averageScore?.let { it / 10.0 }
    }

    val episodeText = remember(anime.latestEpisode, anime.episodes) {
        val releasedEpisodes = anime.latestEpisode?.let { it - 1 }
        when {
            releasedEpisodes != null && releasedEpisodes > 0 -> "Ep $releasedEpisodes"
            anime.episodes > 0 -> "${anime.episodes} Ep"
            else -> ""
        }
    }

    val buttonContainerColor = remember(showStatusColors, currentStatus) {
        if (showStatusColors && currentStatus != null) {
            (StatusColors[currentStatus] ?: Color.Black).copy(alpha = 0.8f)
        } else {
            Color.Black.copy(alpha = 0.6f)
        }
    }

    Column(modifier = Modifier.width(130.dp)) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(185.dp),
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(70.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                if (statusIndicatorColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(statusIndicatorColor)
                    )
                }

                displayScore?.let { score ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            "★ ${String.format(Locale.US, "%.1f", score)}",
                            color = Color(0xFFFFD700),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                if (episodeText.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            episodeText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                if (isLoggedIn) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                showAnimation = true
                                if (currentStatus != null) {
                                    Toast.makeText(context, "Removed from ${StatusLabels[currentStatus]}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                                }
                                onBookmarkClick()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .scale(if (showAnimation) bookmarkScale else 1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = buttonContainerColor,
                                contentColor = Color.White
                            )
                        ) {
                            AnimatedContent(
                                targetState = currentStatus,
                                transitionSpec = {
                                    (scaleIn(animationSpec = tween(200)) + fadeIn())
                                        .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut())
                                },
                                label = "bookmarkIcon"
                            ) { status ->
                                Icon(
                                    imageVector = if (status != null) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                                    contentDescription = if (status != null) StatusLabels[status] else "Add to Planning",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        FilledTonalIconButton(
                            onClick = onClick,
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = anime.title,
            modifier = Modifier
                .padding(top = 6.dp)
                .height(32.dp),
            maxLines = 2,
            style = MaterialTheme.typography.labelMedium,
            overflow = TextOverflow.Ellipsis,
            color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun ExploreAnimeDialog(
    anime: ExploreAnime,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    currentStatus: String?,
    showStatusColors: Boolean = true,
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
    var selectedStatus by remember { mutableStateOf(currentStatus ?: "") }

    // REMOVED: Description fetching - no longer showing description in simple dialog

    var showAnimation by remember { mutableStateOf(false) }
    var hasAddedToPlanning by remember { mutableStateOf(false) }
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

    val isCurrentlySaved = currentStatus != null || hasAddedToPlanning
    val statusColor = currentStatus?.let { StatusColors[it] } ?: MaterialTheme.colorScheme.primary

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

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (currentStatus) {
                                        "CURRENT" -> Color(0xFF2196F3).copy(alpha = 0.2f) // Blue
                                        "PLANNING" -> Color(0xFF9C27B0).copy(alpha = 0.2f) // Purple
                                        "COMPLETED" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                        "PAUSED" -> Color(0xFFFFC107).copy(alpha = 0.2f)
                                        "DROPPED" -> Color(0xFFF44336).copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    }
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
                                            tint = when (currentStatus) {
                                                "CURRENT" -> Color(0xFF2196F3)
                                                "PLANNING" -> Color(0xFF9C27B0)
                                                "COMPLETED" -> Color(0xFF4CAF50)
                                                "PAUSED" -> Color(0xFFFFC107)
                                                "DROPPED" -> Color(0xFFF44336)
                                                else -> MaterialTheme.colorScheme.primary
                                            }
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
                                            color = when (currentStatus) {
                                                "CURRENT" -> Color(0xFF2196F3)
                                                "PLANNING" -> Color(0xFF9C27B0)
                                                "COMPLETED" -> Color(0xFF4CAF50)
                                                "PAUSED" -> Color(0xFFFFC107)
                                                "DROPPED" -> Color(0xFFF44336)
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                }
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
                        Button(
                            onClick = {
                                if (currentStatus == "CURRENT") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from Watching", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addExploreAnimeToList(anime, "CURRENT")
                                    hasAddedToPlanning = true
                                    showAnimation = true
                                    Toast.makeText(context, "Added to Watching", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .scale(if (currentStatus == "CURRENT" && showAnimation) scale else 1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStatus == "CURRENT") Color(0xFF2196F3)
                                else Color.White.copy(alpha = 0.08f),
                                contentColor = if (currentStatus == "CURRENT") Color.White
                                else Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Watching", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                if (currentStatus == "PLANNING") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from Planning", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddToPlanning()
                                    hasAddedToPlanning = true
                                    showAnimation = true
                                    Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .scale(if (currentStatus == "PLANNING" && showAnimation) scale else 1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStatus == "PLANNING") Color(0xFF9C27B0)
                                else Color.White.copy(alpha = 0.08f),
                                contentColor = if (currentStatus == "PLANNING") Color.White
                                else Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Planning", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Completed and On Hold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                if (currentStatus == "COMPLETED") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from Completed", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addExploreAnimeToList(anime, "COMPLETED")
                                    hasAddedToPlanning = true
                                    showAnimation = true
                                    Toast.makeText(context, "Marked as Completed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .scale(if (currentStatus == "COMPLETED" && showAnimation) scale else 1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStatus == "COMPLETED") Color(0xFF4CAF50)
                                else Color.White.copy(alpha = 0.08f),
                                contentColor = if (currentStatus == "COMPLETED") Color.White
                                else Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Completed", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                if (currentStatus == "PAUSED") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from On Hold", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddToOnHold()
                                    hasAddedToPlanning = true
                                    showAnimation = true
                                    Toast.makeText(context, "Added to On Hold", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .scale(if (currentStatus == "PAUSED" && showAnimation) scale else 1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStatus == "PAUSED") Color(0xFFFFC107)
                                else Color.White.copy(alpha = 0.08f),
                                contentColor = if (currentStatus == "PAUSED") Color.White
                                else Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("On Hold", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 3: Dropped and Remove
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                if (currentStatus == "DROPPED") {
                                    onRemoveFromList()
                                    Toast.makeText(context, "Removed from Dropped", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddToDropped()
                                    hasAddedToPlanning = true
                                    showAnimation = true
                                    Toast.makeText(context, "Marked as Dropped", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .scale(if (currentStatus == "DROPPED" && showAnimation) scale else 1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStatus == "DROPPED") Color(0xFFF44336)
                                else Color.White.copy(alpha = 0.08f),
                                contentColor = if (currentStatus == "DROPPED") Color.White
                                else Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dropped", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }

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
