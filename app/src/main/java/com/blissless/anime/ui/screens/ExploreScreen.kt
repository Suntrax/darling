package com.blissless.anime.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.MainViewModel
import com.blissless.anime.dialogs.ExploreAnimeDialog
import com.blissless.anime.ui.components.ExploreAnimeHorizontalList
import com.blissless.anime.ui.components.FeaturedCarousel
import com.blissless.anime.ui.components.LoadingPlaceholder
import com.blissless.anime.ui.components.SectionTitle
import android.widget.Toast
import com.blissless.anime.data.models.AnimeRelation
import com.blissless.anime.data.models.toDetailedAnimeData
import com.blissless.anime.data.models.StoredFavorite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false,
    showStatusColors: Boolean = true,
    simplifyAnimeDetails: Boolean = true,
    localFavorites: Map<Int, StoredFavorite> = emptyMap(),
    onToggleFavorite: (ExploreAnime) -> Unit = {},
    onPlayEpisode: (AnimeMedia, Int) -> Unit = { _, _ -> },
    currentlyWatching: List<AnimeMedia> = emptyList(),
    planningToWatch: List<AnimeMedia> = emptyList(),
    completed: List<AnimeMedia> = emptyList(),
    onHold: List<AnimeMedia> = emptyList(),
    dropped: List<AnimeMedia> = emptyList(),
    isVisible: Boolean = true,
    onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit = { _, _ -> },
    onClearAnimeStack: () -> Unit = {}
) {
    val context = LocalContext.current
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
        buildMap {
            currentlyWatching.forEach { put(it.id, "CURRENT") }
            planningToWatch.forEach { put(it.id, "PLANNING") }
            completed.forEach { put(it.id, "COMPLETED") }
            onHold.forEach { put(it.id, "PAUSED") }
            dropped.forEach { put(it.id, "DROPPED") }
        }
    }

    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    
    // Track navigation history for back button
    var firstAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    
    // Scope for coroutines - must be at composition level
    val scope = rememberCoroutineScope()

    // Calculate canAddFavorite locally based on max 10 favorites limit
    val canAddFavoriteLocal = localFavorites.size < 10

    // Show appropriate dialog based on simplifyAnimeDetails setting
    if (showDialog && selectedAnime != null) {
        // Set first anime on first open
        if (firstAnime == null) {
            firstAnime = selectedAnime
        }
        
        val anime = selectedAnime!!
        val isAnimeFavorite = localFavorites.containsKey(anime.id)
        val animeStatus = animeStatusMap[anime.id]

        if (simplifyAnimeDetails) {
            // Simple dialog
            ExploreAnimeDialog(
                anime = anime,
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = animeStatus,
                isFavorite = isAnimeFavorite,
                canAddFavorite = canAddFavoriteLocal || isAnimeFavorite,
                onToggleFavorite = { onToggleFavorite(anime) },
                onDismiss = { 
                    // Go back to first anime if we've navigated, otherwise close
                    if (firstAnime != null && selectedAnime?.id != firstAnime?.id) {
                        selectedAnime = firstAnime
                    } else {
                        showDialog = false
                        firstAnime = null
                        onClearAnimeStack()
                    }
                },
                onAddToPlanning = { viewModel.addExploreAnimeToList(anime, "PLANNING") },
                onAddToDropped = { viewModel.addExploreAnimeToList(anime, "DROPPED") },
                onAddToOnHold = { viewModel.addExploreAnimeToList(anime, "PAUSED") },
                onRemoveFromList = { viewModel.removeAnimeFromList(anime.id) },
                onStartWatching = { episode ->
                    val animeMedia = AnimeMedia(
                        id = anime.id,
                        title = anime.title,
                        cover = anime.cover,
                        banner = anime.banner,
                        progress = 0,
                        totalEpisodes = anime.episodes,
                        latestEpisode = anime.latestEpisode,
                        status = "",
                        averageScore = anime.averageScore,
                        genres = anime.genres,
                        listStatus = "",
                        listEntryId = 0,
                        year = anime.year
                    )
                    onPlayEpisode(animeMedia, episode)
                    showDialog = false
                },
                isLoggedIn = isLoggedIn
            )
        } else {
            // Rich detailed dialog with favorite button
            DetailedAnimeScreen(
                anime = anime.toDetailedAnimeData(),
                viewModel = viewModel,
                isOled = isOled,
                currentStatus = animeStatus,
                isFavorite = isAnimeFavorite,
                canAddFavorite = canAddFavoriteLocal || isAnimeFavorite,
                onDismiss = { 
                    // Go back to first anime if we've navigated, otherwise close
                    if (firstAnime != null && selectedAnime?.id != firstAnime?.id) {
                        selectedAnime = firstAnime
                    } else {
                        showDialog = false
                        firstAnime = null
                        onClearAnimeStack()
                    }
                },
                onSwipeToClose = { showDialog = false; onClearAnimeStack() },
                onPlayEpisode = { episode ->
                    val animeMedia = AnimeMedia(
                        id = anime.id,
                        title = anime.title,
                        cover = anime.cover,
                        banner = anime.banner,
                        progress = 0,
                        totalEpisodes = anime.episodes,
                        latestEpisode = anime.latestEpisode,
                        status = "",
                        averageScore = anime.averageScore,
                        genres = anime.genres,
                        listStatus = "",
                        listEntryId = 0
                    )
                    onPlayEpisode(animeMedia, episode)
                    showDialog = false
                },
                onUpdateStatus = { status ->
                    if (status != null) {
                        viewModel.addExploreAnimeToList(anime, status)
                    }
                },
                onRemove = {
                    viewModel.removeAnimeFromList(anime.id)
                },
                onToggleFavorite = { onToggleFavorite(anime) },
                isLoggedIn = isLoggedIn,
                onRelationClick = { relation ->
                    try {
                        scope.launch {
                            try {
                                delay(100)
                                val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                                if (detailedData != null) {
                                    // Update the current dialog instead of opening a new one
                                    selectedAnime = ExploreAnime(
                                        id = relation.id,
                                        title = detailedData.title,
                                        titleEnglish = detailedData.titleEnglish,
                                        cover = detailedData.cover,
                                        banner = detailedData.banner,
                                        episodes = detailedData.episodes,
                                        latestEpisode = detailedData.latestEpisode,
                                        averageScore = detailedData.averageScore,
                                        genres = detailedData.genres,
                                        year = detailedData.year,
                                        format = detailedData.format
                                    )
                                } else {
                                    Toast.makeText(context, "Anime not found - ID: ${relation.id}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
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
    var isRefreshing by remember { mutableStateOf(false) }

    // Reset pull-to-refresh when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isRefreshing = false
        }
    }

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
                    autoScrollEnabled = isVisible && !showDialog
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

    // Stop refreshing when loading completes or after timeout
    LaunchedEffect(isLoading, isRefreshing) {
        if (isRefreshing) {
            // Use a timeout to ensure refreshing stops even if loading state gets stuck
            kotlinx.coroutines.delay(15000)
            isRefreshing = false
        }
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
