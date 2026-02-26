package com.blissless.anime.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.blissless.anime.ExploreAnime
import com.blissless.anime.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onAnimeClick: (ExploreAnime) -> Unit,
    isLoggedIn: Boolean = false
) {
    val scrollState = rememberScrollState()
    val featuredAnime by viewModel.featuredAnime.collectAsState()
    val seasonalAnime by viewModel.seasonalAnime.collectAsState()
    val topSeries by viewModel.topSeries.collectAsState()
    val topMovies by viewModel.topMovies.collectAsState()
    val isLoading by viewModel.isLoadingExplore.collectAsState()

    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog && selectedAnime != null) {
        ExploreAnimeDialog(
            anime = selectedAnime!!,
            onDismiss = { showDialog = false },
            onAddToPlanning = {
                viewModel.addExploreAnimeToList(selectedAnime!!, "PLANNING")
                showDialog = false
            },
            onStartWatching = {
                viewModel.addExploreAnimeToList(selectedAnime!!, "CURRENT")
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
        SectionTitle("This Season")

        if (seasonalAnime.isNotEmpty()) {
            ExploreAnimeHorizontalList(
                animeList = seasonalAnime,
                onAnimeClick = { anime ->
                    selectedAnime = anime
                    showDialog = true
                },
                onBookmarkClick = { anime ->
                    viewModel.addExploreAnimeToList(anime, "PLANNING")
                },
                isLoggedIn = isLoggedIn
            )
        } else {
            LoadingPlaceholder()
        }

        // Top Rated Series
        SectionTitle("Top Rated Series")

        if (topSeries.isNotEmpty()) {
            ExploreAnimeHorizontalList(
                animeList = topSeries,
                onAnimeClick = { anime ->
                    selectedAnime = anime
                    showDialog = true
                },
                onBookmarkClick = { anime ->
                    viewModel.addExploreAnimeToList(anime, "PLANNING")
                },
                isLoggedIn = isLoggedIn
            )
        } else if (isLoading) {
            LoadingPlaceholder()
        } else {
            Text(
                "No top series found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Top Rated Movies
        SectionTitle("Top Rated Movies")

        if (topMovies.isNotEmpty()) {
            ExploreAnimeHorizontalList(
                animeList = topMovies,
                onAnimeClick = { anime ->
                    selectedAnime = anime
                    showDialog = true
                },
                onBookmarkClick = { anime ->
                    viewModel.addExploreAnimeToList(anime, "PLANNING")
                },
                isLoggedIn = isLoggedIn
            )
        } else if (isLoading) {
            LoadingPlaceholder()
        } else {
            Text(
                "No top movies found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun LoadingPlaceholder() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            Card(
                modifier = Modifier
                    .width(130.dp)
                    .height(200.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
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
                modifier = Modifier
                    .fillMaxSize()
                    .blur(15.dp)
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
                                    Text(
                                        "★ $score%",
                                        color = Color(0xFFFFD700),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                anime.latestEpisode?.let { ep ->
                                    Text(
                                        "Ep $ep ${if (anime.episodes > 0) "/ ${anime.episodes}" else ""}",
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
    isLoggedIn: Boolean = false
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(animeList) { anime ->
            ExploreAnimeCard(
                anime = anime,
                onClick = { onAnimeClick(anime) },
                onBookmarkClick = { onBookmarkClick(anime) },
                isLoggedIn = isLoggedIn
            )
        }
    }
}

@Composable
fun ExploreAnimeCard(
    anime: ExploreAnime,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    isLoggedIn: Boolean = false
) {
    Column(modifier = Modifier.width(130.dp)) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(185.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = anime.cover,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient overlay
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

                // Score badge
                anime.averageScore?.let { score ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            "★ $score",
                            color = Color(0xFFFFD700),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Episode badge
                anime.latestEpisode?.let { ep ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    ) {
                        Text(
                            "Ep $ep",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Bottom buttons - only show when logged in
                if (isLoggedIn) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bookmark button (left) - adds to planning list
                        FilledTonalIconButton(
                            onClick = onBookmarkClick,
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkAdd,
                                contentDescription = "Add to Planning",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Play button (right)
                        FAB(
                            onClick = onClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
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
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FAB(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(36.dp),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun ExploreAnimeDialog(
    anime: ExploreAnime,
    onDismiss: () -> Unit,
    onAddToPlanning: () -> Unit,
    onStartWatching: () -> Unit,
    isLoggedIn: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
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
                            overflow = TextOverflow.Ellipsis
                        )

                        anime.averageScore?.let { score ->
                            Text(
                                "★ $score%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFFD700)
                            )
                        }

                        anime.latestEpisode?.let { ep ->
                            Text(
                                "Episode $ep ${if (anime.episodes > 0) "of ${anime.episodes}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Only show action buttons when logged in
                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onStartWatching,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Watching")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onAddToPlanning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Planning")
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