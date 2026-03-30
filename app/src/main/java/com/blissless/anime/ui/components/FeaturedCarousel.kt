package com.blissless.anime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.data.models.ExploreAnime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FeaturedCarousel(
    animeList: List<ExploreAnime>,
    onAnimeClick: (ExploreAnime) -> Unit,
    autoScrollEnabled: Boolean = true,
    isVisible: Boolean = true
) {
    if (animeList.isEmpty()) return

    val actualCount = animeList.size
    val pagerState = rememberPagerState(
        initialPage = actualCount * 100,
        pageCount = { actualCount * 200 }
    )
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var headerVisible by remember { mutableStateOf(false) }
    var pageWhenScrollStarted by remember { mutableIntStateOf(pagerState.currentPage) }
    var isHeaderSwiping by remember { mutableStateOf(false) }
    var timerResetSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(300)
        headerVisible = true
    }

    LaunchedEffect(pagerState.isScrollInProgress, pagerState.currentPageOffsetFraction) {
        if (pagerState.isScrollInProgress) {
            pageWhenScrollStarted = pagerState.currentPage
        } else if (pagerState.currentPage != pageWhenScrollStarted) {
            headerVisible = false
            delay(80)
            headerVisible = true
            pageWhenScrollStarted = pagerState.currentPage
        }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            timerResetSignal++
        }
    }

    LaunchedEffect(autoScrollEnabled, isVisible, isHeaderSwiping, timerResetSignal) {
        if (autoScrollEnabled && isVisible && !isHeaderSwiping) {
            while (true) {
                delay(5000)
                if (isHeaderSwiping) continue

                headerVisible = false
                delay(80)
                headerVisible = true
                
                autoScrollJob = scope.launch {
                    try {
                        val targetPage = pagerState.currentPage + 1
                        pagerState.animateScrollToPage(targetPage)
                    } catch (_: Exception) {}
                }
                autoScrollJob?.join()
                
                delay(300)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { autoScrollJob?.cancel() }
    }

    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = true,
            beyondViewportPageCount = 0
        ) { page ->
            val anime = animeList[page % actualCount]
            
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { clip = true }) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(anime.banner ?: anime.cover)
                        .memoryCacheKey(anime.banner ?: anime.cover)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
            contentAlignment = Alignment.BottomStart
        ) {
            val currentAnime by remember {
                derivedStateOf { animeList[pagerState.currentPage % actualCount] }
            }

            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            animationSpec = tween(400, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 2 }
                        ),
                exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                        slideOutVertically(
                            animationSpec = tween(150, easing = FastOutSlowInEasing),
                            targetOffsetY = { it / 2 }
                        )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var totalDragX = 0f
                            var hasTriggeredSwipe = false
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    totalDragX = 0f
                                    hasTriggeredSwipe = false
                                    isHeaderSwiping = true
                                },
                                onDragEnd = {
                                    totalDragX = 0f
                                    isHeaderSwiping = false
                                },
                                onDragCancel = {
                                    totalDragX = 0f
                                    isHeaderSwiping = false
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDragX += dragAmount
                                    val threshold = 50f
                                    if (!hasTriggeredSwipe) {
                                        if (totalDragX > threshold) {
                                            scope.launch {
                                                try {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                } catch (_: Exception) {}
                                            }
                                            hasTriggeredSwipe = true
                                        } else if (totalDragX < -threshold) {
                                            scope.launch {
                                                try {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                } catch (_: Exception) {}
                                            }
                                            hasTriggeredSwipe = true
                                        }
                                    }
                                }
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onAnimeClick(currentAnime) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = currentAnime.cover,
                            contentDescription = currentAnime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(50.dp)
                                .height(70.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentAnime.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                currentAnime.year?.let { year ->
                                    Text(text = year.toString(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                    Text(text = " • ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = currentAnime.genres.take(3).joinToString(" - "),
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val score = currentAnime.averageScore
                                if (score != null) {
                                    Text(
                                        text = "★ ${String.format(Locale.US, "%.1f", score / 10.0)}",
                                        color = Color(0xFFFFD700),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                val latestEp = currentAnime.latestEpisode
                                if (latestEp != null && latestEp > 0) {
                                    val epText = "Ep $latestEp${if (currentAnime.episodes > 0) " / ${currentAnime.episodes}" else ""}"
                                    Text(text = epText, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentPageIndex = pagerState.currentPage % actualCount
            val targetPageIndex = pagerState.targetPage % actualCount
            
            repeat(actualCount) { index ->
                val isCurrentSelected = index == currentPageIndex
                val isTargetSelected = index == targetPageIndex
                
                val width by animateDpAsState(
                    targetValue = when {
                        isCurrentSelected && !isDragged -> 24.dp
                        isCurrentSelected && isDragged -> 16.dp
                        isTargetSelected && isDragged -> 16.dp
                        else -> 8.dp
                    },
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "w$index"
                )
                val alpha by animateFloatAsState(
                    targetValue = when {
                        isCurrentSelected -> 1f
                        isDragged && isTargetSelected -> 1f
                        else -> 0.4f
                    },
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "a$index"
                )
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(width)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = alpha))
                )
            }
        }
    }
}
