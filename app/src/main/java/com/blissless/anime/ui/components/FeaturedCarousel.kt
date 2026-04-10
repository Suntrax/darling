package com.blissless.anime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onStatusClick: (ExploreAnime) -> Unit,
    onPlayClick: (ExploreAnime) -> Unit,
    onInfoClick: (ExploreAnime) -> Unit,
    animeStatusMap: Map<Int, String> = emptyMap(),
    preferEnglishTitles: Boolean = true,
    isOled: Boolean = false,
    isDialogOpen: Boolean = false,
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

    LaunchedEffect(autoScrollEnabled, isVisible, isHeaderSwiping, isDialogOpen, timerResetSignal) {
        if (autoScrollEnabled && isVisible && !isHeaderSwiping && !isDialogOpen) {
            while (true) {
                delay(4500)
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

    Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = true,
            beyondViewportPageCount = 0
        ) { page ->
            val anime = animeList[page % actualCount]
            
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(anime.cover)
                        .memoryCacheKey(anime.cover)
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
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).align(Alignment.BottomCenter),
            contentAlignment = Alignment.BottomCenter
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val displayTitle = (if (preferEnglishTitles && currentAnime.titleEnglish != null) currentAnime.titleEnglish else currentAnime.title) ?: "Unknown"
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val avgScore = currentAnime.averageScore
                        val format = currentAnime.format
                        currentAnime.year?.let { year ->
                            Text(text = year.toString(), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            Text(text = " • ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        val formatText = when (format?.uppercase()) {
                            "MOVIE" -> "Movie"
                            "ONA", "OVA", "TV" -> "Series"
                            else -> "Series"
                        }
                        Text(text = formatText, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        if (avgScore != null) {
                            Text(text = " • ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            val scoreValue = avgScore / 10.0
                            val scoreFormatted = "%,.1f".format(scoreValue).replace(".", ",")
                            Text(
                                text = "★ $scoreFormatted",
                                color = Color(0xFFFFD700),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentStatus = animeStatusMap[currentAnime.id]
                        val isSaved = currentStatus != null
                        val statusColor = when (currentStatus) {
                            "COMPLETED" -> Color(0xFF4CAF50)
                            "CURRENT" -> Color(0xFF2196F3)
                            "PLANNING" -> Color(0xFF9C27B0)
                            "PAUSED" -> Color(0xFFFFC107)
                            "DROPPED" -> Color(0xFFF44336)
                            else -> Color.White
                        }
                        
                        IconButton(
                            onClick = { onStatusClick(currentAnime) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Save",
                                tint = if (isSaved) statusColor else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Button(
                            onClick = { onPlayClick(currentAnime) },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isOled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Watch",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watch Now", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        IconButton(
                            onClick = { onInfoClick(currentAnime) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Info",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}