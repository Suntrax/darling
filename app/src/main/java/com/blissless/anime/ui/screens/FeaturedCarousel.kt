package com.blissless.anime.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.blissless.anime.ExploreAnime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
fun FeaturedCarousel(
    animeList: List<ExploreAnime>,
    onAnimeClick: (ExploreAnime) -> Unit,
    autoScrollEnabled: Boolean = true,
    isVisible: Boolean = true
) {
    val pagerState = rememberPagerState(pageCount = { animeList.size })
    val context = LocalContext.current

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
            while (isActive) {
                delay(4000) // FIXED: Reduced from 5000ms to 4000ms for faster transitions
                if (!isActive) break

                if (!pagerState.isScrollInProgress && isActive) {
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
                    } catch (_: Exception) {
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
