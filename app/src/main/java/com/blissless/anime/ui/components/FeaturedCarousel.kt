package com.blissless.anime.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
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
    // Use a key to re-initialize when the list size changes (e.g. from 0 to N)
    val pagerState = rememberPagerState(
        initialPage = actualCount * 100,
        pageCount = { actualCount * 200 }
    )
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(autoScrollEnabled, isVisible, isDragged) {
        if (autoScrollEnabled && isVisible && !isDragged) {
            while (true) {
                delay(4000)
                if (isDragged) break

                autoScrollJob = scope.launch {
                    try {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage + 1,
                            animationSpec = tween(800, easing = FastOutSlowInEasing)
                        )
                    } catch (_: Exception) {}
                }
                autoScrollJob?.join()
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
            userScrollEnabled = true
        ) { page ->
            val anime = animeList[page % actualCount]
            
            Box(modifier = Modifier.fillMaxSize()) {
                // Banner background
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

                // Dark gradient overlay
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

                // Info Card overlay
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAnimeClick(anime) }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = anime.cover,
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(50.dp).height(70.dp).clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = anime.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (anime.year != null) {
                                        Text(text = anime.year.toString(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                        Text(text = " • ", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        text = anime.genres.take(3).joinToString(" - "),
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (anime.averageScore != null) {
                                        Text(
                                            text = "★ ${String.format(Locale.US, "%.1f", anime.averageScore / 10.0)}",
                                            color = Color(0xFFFFD700),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    if (anime.latestEpisode != null && anime.latestEpisode > 0) {
                                        val epText = "Ep ${anime.latestEpisode}${if (anime.episodes > 0) " / ${anime.episodes}" else ""}"
                                        Text(text = epText, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Indicators
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(actualCount) { index ->
                val isSelected = (pagerState.currentPage % actualCount) == index
                val width by animateDpAsState(if (isSelected) 24.dp else 8.dp, tween(300), label = "w")
                val alpha by animateFloatAsState(if (isSelected) 1f else 0.4f, tween(300), label = "a")
                Box(modifier = Modifier.height(4.dp).width(width).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = alpha)))
            }
        }
    }
}
