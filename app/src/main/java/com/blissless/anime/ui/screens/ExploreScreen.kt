package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.*
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.AnimeMedia
import com.blissless.anime.ExploreAnime
import com.blissless.anime.MainViewModel
import kotlinx.coroutines.delay
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

@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onAnimeClick: (ExploreAnime) -> Unit,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false,
    showStatusColors: Boolean = true,
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

    if (showDialog && selectedAnime != null) {
        ExploreAnimeDialog(
            anime = selectedAnime!!,
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = animeStatusMap[selectedAnime!!.id],
            showStatusColors = showStatusColors,
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
                showDialog = false
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
    autoScrollEnabled: Boolean = true
) {
    val pagerState = rememberPagerState(pageCount = { animeList.size })
    val context = LocalContext.current

    // Auto-scroll - only when page is visible
    LaunchedEffect(autoScrollEnabled) {
        if (autoScrollEnabled) {
            while (true) {
                delay(5000)
                val nextPage = (pagerState.currentPage + 1) % animeList.size
                pagerState.animateScrollToPage(nextPage, animationSpec = tween(800))
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

        AnimatedContent(
            targetState = currentAnime,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) + slideInHorizontally(animationSpec = tween(400)) { it })
                    .togetherWith(fadeOut(animationSpec = tween(400)) + slideOutHorizontally(animationSpec = tween(400)) { -it })
                    .using(SizeTransform(clip = false))
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

                            Text(
                                anime.genres.take(3).joinToString(" - "),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = anime.cover,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(60.dp)
                            .height(85.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            anime.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                        )

                        displayScore?.let { score ->
                            Text(
                                "★ ${String.format(Locale.US, "%.1f", score)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFFD700)
                            )
                        }

                        val episodeText = when {
                            anime.latestEpisode != null && anime.latestEpisode > 0 -> {
                                val releasedEp = anime.latestEpisode - 1
                                if (releasedEp > 0 && anime.episodes > 0) {
                                    "Episode $releasedEp of ${anime.episodes}"
                                } else if (releasedEp > 0) {
                                    "Episode $releasedEp"
                                } else if (anime.episodes > 0) {
                                    "${anime.episodes} Episodes"
                                } else null
                            }
                            anime.episodes > 0 -> "${anime.episodes} Episodes"
                            else -> null
                        }
                        if (episodeText != null) {
                            Text(
                                episodeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (anime.genres.isNotEmpty()) {
                            Text(
                                anime.genres.take(3).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (currentStatus != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (showStatusColors) {
                                    StatusColors[currentStatus]?.copy(alpha = 0.2f)
                                        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = StatusLabels[currentStatus] ?: currentStatus,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (showStatusColors) {
                                        StatusColors[currentStatus] ?: MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onStartWatching(1) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Watching")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status buttons row 1: Watching, Planning
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusButtonSmall(
                            icon = Icons.Default.PlayArrow,
                            label = "Watching",
                            color = StatusColors["CURRENT"]!!,
                            isActive = currentStatus == "CURRENT",
                            onClick = {
                                viewModel.addExploreAnimeToList(anime, "CURRENT")
                                Toast.makeText(context, "Added to Watching", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        StatusButtonSmall(
                            icon = Icons.Default.Bookmark,
                            label = "Planning",
                            color = StatusColors["PLANNING"]!!,
                            isActive = currentStatus == "PLANNING",
                            onClick = {
                                onAddToPlanning()
                                Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status buttons row 2: On Hold, Dropped
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusButtonSmall(
                            icon = Icons.Default.Pause,
                            label = "On Hold",
                            color = StatusColors["PAUSED"]!!,
                            isActive = currentStatus == "PAUSED",
                            onClick = {
                                onAddToOnHold()
                                Toast.makeText(context, "Added to On Hold", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        StatusButtonSmall(
                            icon = Icons.Default.Delete,
                            label = "Dropped",
                            color = StatusColors["DROPPED"]!!,
                            isActive = currentStatus == "DROPPED",
                            onClick = {
                                onAddToDropped()
                                Toast.makeText(context, "Added to Dropped", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Remove button (only if in a list)
                    if (currentStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Removed from ${StatusLabels[currentStatus]}", Toast.LENGTH_SHORT).show()
                                onRemoveFromList()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.Red
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove from List")
                        }
                    }
                }
            }
        }
    }
}

// Need to pass viewModel - will use a different approach
@Composable
private fun StatusButtonSmall(
    icon: ImageVector,
    label: String,
    color: Color,
    isActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
            contentColor = if (isActive) color else Color.White.copy(alpha = 0.7f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
    }
}
