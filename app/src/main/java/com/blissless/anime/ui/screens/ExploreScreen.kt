package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.blissless.anime.AnimeMedia
import com.blissless.anime.ExploreAnime
import com.blissless.anime.MainViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onAnimeClick: (ExploreAnime) -> Unit,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false,
    onPlayEpisode: (AnimeMedia, Int) -> Unit = { _, _ -> },
    currentlyWatching: List<AnimeMedia> = emptyList(),
    planningToWatch: List<AnimeMedia> = emptyList()
) {
    val scrollState = rememberScrollState()
    val featuredAnime by viewModel.featuredAnime.collectAsState()
    val seasonalAnime by viewModel.seasonalAnime.collectAsState()
    val topSeries by viewModel.topSeries.collectAsState()
    val topMovies by viewModel.topMovies.collectAsState()
    val isLoading by viewModel.isLoadingExplore.collectAsState()

    // Get IDs from user's lists to check if anime is already saved
    val savedAnimeIds = remember(currentlyWatching, planningToWatch) {
        val watchingIds = currentlyWatching.map { it.id }.toSet()
        val planningIds = planningToWatch.map { it.id }.toSet()
        watchingIds + planningIds
    }

    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog && selectedAnime != null) {
        ExploreAnimeDialog(
            anime = selectedAnime!!,
            isOled = isOled,
            isSaved = savedAnimeIds.contains(selectedAnime!!.id),
            onDismiss = { showDialog = false },
            onAddToPlanning = {
                viewModel.addExploreAnimeToList(selectedAnime!!, "PLANNING")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Featured Carousel
        if (featuredAnime.isNotEmpty()) {
            FeaturedCarousel(
                animeList = featuredAnime,
                onAnimeClick = { anime ->
                    selectedAnime = anime
                    showDialog = true
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
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
                onAnimeClick = { anime ->
                    selectedAnime = anime
                    showDialog = true
                },
                onBookmarkClick = { anime ->
                    if (savedAnimeIds.contains(anime.id)) {
                        // Remove from list
                        viewModel.removeAnimeFromList(anime.id)
                    } else {
                        viewModel.addExploreAnimeToList(anime, "PLANNING")
                    }
                },
                isLoggedIn = isLoggedIn,
                isOled = isOled,
                savedAnimeIds = savedAnimeIds
            )
        } else {
            LoadingPlaceholder(isOled)
        }

        // Top Rated Series
        SectionTitle("Top Rated Series", isOled)

        if (topSeries.isNotEmpty()) {
            ExploreAnimeHorizontalList(
                animeList = topSeries,
                onAnimeClick = { anime ->
                    selectedAnime = anime
                    showDialog = true
                },
                onBookmarkClick = { anime ->
                    if (savedAnimeIds.contains(anime.id)) {
                        viewModel.removeAnimeFromList(anime.id)
                    } else {
                        viewModel.addExploreAnimeToList(anime, "PLANNING")
                    }
                },
                isLoggedIn = isLoggedIn,
                isOled = isOled,
                savedAnimeIds = savedAnimeIds
            )
        } else if (isLoading) {
            LoadingPlaceholder(isOled)
        } else {
            Text(
                "No top series found",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Top Rated Movies
        SectionTitle("Top Rated Movies", isOled)

        if (topMovies.isNotEmpty()) {
            ExploreAnimeHorizontalList(
                animeList = topMovies,
                onAnimeClick = { anime ->
                    selectedAnime = anime
                    showDialog = true
                },
                onBookmarkClick = { anime ->
                    if (savedAnimeIds.contains(anime.id)) {
                        viewModel.removeAnimeFromList(anime.id)
                    } else {
                        viewModel.addExploreAnimeToList(anime, "PLANNING")
                    }
                },
                isLoggedIn = isLoggedIn,
                isOled = isOled,
                savedAnimeIds = savedAnimeIds
            )
        } else if (isLoading) {
            LoadingPlaceholder(isOled)
        } else {
            Text(
                "No top movies found",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
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
        items(5) {
            Card(
                modifier = Modifier
                    .width(130.dp)
                    .height(200.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedCarousel(
    animeList: List<ExploreAnime>,
    onAnimeClick: (ExploreAnime) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { animeList.size })

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % animeList.size
            pagerState.animateScrollToPage(nextPage, animationSpec = tween(1000))
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
        val currentAnime = animeList.getOrElse(pagerState.currentPage) { animeList.first() }

        AnimatedContent(
            targetState = currentAnime,
            transitionSpec = {
                fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
            },
            label = "BannerTransition"
        ) { anime ->
            AsyncImage(
                model = anime.banner ?: anime.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.3f),
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.9f)
                    )
                ))
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
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
                        .clickable { onAnimeClick(anime) },
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
                            model = anime.cover,
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
                                anime.genres.take(3).joinToString(" • "),
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

                                // latestEpisode is NEXT to air, so subtract 1 for released count
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
    onAnimeClick: (ExploreAnime) -> Unit,
    onBookmarkClick: (ExploreAnime) -> Unit,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false,
    savedAnimeIds: Set<Int> = emptySet()
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(animeList) { anime ->
            ExploreAnimeCard(
                anime = anime,
                isSaved = savedAnimeIds.contains(anime.id),
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
    isSaved: Boolean = false,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false
) {
    val context = LocalContext.current

    var showAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1.3f else 1f,
        animationSpec = tween(200),
        finishedListener = {
            if (showAnimation) {
                showAnimation = false
            }
        },
        label = "bookmarkScale"
    )

    Column(modifier = Modifier.width(130.dp)) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(185.dp),
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = anime.cover,
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

                anime.averageScore?.let { score ->
                    val displayScore = score / 10.0
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            "★ ${String.format(Locale.US, "%.1f", displayScore)}",
                            color = Color(0xFFFFD700),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Episode badge - latestEpisode is NEXT to air, so subtract 1
                if (anime.latestEpisode != null || anime.episodes > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        // latestEpisode from AniList is the next episode TO AIR
                        // So released episodes = latestEpisode - 1
                        val releasedEpisodes = anime.latestEpisode?.let { it - 1 }
                        val episodeText = when {
                            releasedEpisodes != null && releasedEpisodes > 0 -> "Ep $releasedEpisodes"
                            anime.episodes > 0 -> "${anime.episodes} Ep"
                            else -> ""
                        }
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
                        // Bookmark button - same style as edit button in HomeScreen
                        FilledTonalIconButton(
                            onClick = {
                                showAnimation = true
                                if (isSaved) {
                                    Toast.makeText(context, "Removed from list", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                                }
                                onBookmarkClick()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .scale(if (showAnimation) scale else 1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            AnimatedContent(
                                targetState = isSaved,
                                transitionSpec = {
                                    (scaleIn(animationSpec = tween(200)) + fadeIn())
                                        .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut())
                                },
                                label = "bookmarkIcon"
                            ) { saved ->
                                Icon(
                                    imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                                    contentDescription = if (saved) "Saved" else "Add to Planning",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Play button - same style
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
            modifier = Modifier.padding(top = 6.dp),
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
    isOled: Boolean = false,
    isSaved: Boolean = false,
    onDismiss: () -> Unit,
    onAddToPlanning: () -> Unit,
    onStartWatching: (Int) -> Unit,
    isLoggedIn: Boolean = false
) {
    val context = LocalContext.current
    val displayScore = anime.averageScore?.let { it / 10.0 }

    // Animation state for add to planning button
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

                        // latestEpisode is NEXT to air, so subtract 1 for released count
                        anime.latestEpisode?.let { ep ->
                            val releasedEp = ep - 1
                            if (releasedEp > 0) {
                                Text(
                                    "Episode $releasedEp ${if (anime.episodes > 0) "of ${anime.episodes}" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                    }
                }

                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onStartWatching(1) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Watching")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Add to Planning button with animation
                    val isAlreadySaved = isSaved || hasAddedToPlanning

                    OutlinedButton(
                        onClick = {
                            if (!isAlreadySaved) {
                                showAnimation = true
                                hasAddedToPlanning = true
                                Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                            }
                            onAddToPlanning()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale),
                        shape = RoundedCornerShape(10.dp),
                        colors = if (isAlreadySaved) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isOled) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        AnimatedContent(
                            targetState = isAlreadySaved,
                            transitionSpec = {
                                (scaleIn(animationSpec = tween(200)) + fadeIn())
                                    .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut())
                            },
                            label = "buttonIcon"
                        ) { saved ->
                            if (saved) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null)
                            } else {
                                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isAlreadySaved) "Saved" else "Add to Planning")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}
