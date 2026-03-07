package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.blissless.anime.ExploreAnime
import com.blissless.anime.MainViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class DetailedAnimeData(
    val id: Int,
    val title: String,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val cover: String,
    val banner: String? = null,
    val description: String? = null,
    val episodes: Int = 0,
    val duration: Int? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val genres: List<String> = emptyList(),
    val tags: List<TagData> = emptyList(),
    val season: String? = null,
    val year: Int? = null,
    val format: String? = null,
    val source: String? = null,
    val studios: List<StudioData> = emptyList(),
    val startDate: String? = null,
    val endDate: String? = null,
    val nextAiringEpisode: Int? = null,
    val nextAiringTime: Long? = null,
    val isAdult: Boolean = false,
    val countryOfOrigin: String? = null,
    val trailerUrl: String? = null,
    val recommendations: List<ExploreAnime> = emptyList(),
    val latestEpisode: Int? = null,
    val malId: Int? = null
)

data class TagData(val name: String, val rank: Int? = null, val isMediaSpoiler: Boolean = false)
data class StudioData(val id: Int, val name: String, val isAnimationStudio: Boolean = true)

fun ExploreAnime.toDetailedAnimeData(): DetailedAnimeData = DetailedAnimeData(
    id = this.id, title = this.title, cover = this.cover, banner = this.banner,
    description = null, episodes = this.episodes, status = null, averageScore = this.averageScore,
    genres = this.genres, year = this.year, latestEpisode = this.latestEpisode, malId = this.malId
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedAnimeScreen(
    anime: DetailedAnimeData,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    currentStatus: String? = null,
    showStatusColors: Boolean = true,
    isLoggedIn: Boolean = false,
    isFavorite: Boolean = false,
    canAddFavorite: Boolean = true,
    onDismiss: () -> Unit,
    onPlayEpisode: (Int) -> Unit = {},
    onUpdateStatus: (String?) -> Unit = {},
    onRemove: () -> Unit = {},
    onToggleFavorite: () -> Unit = {}
) {
    val context = LocalContext.current
    var showFullDescription by remember { mutableStateOf(false) }

    var detailedData by remember { mutableStateOf<DetailedAnimeData?>(null) }
    var isLoadingDetails by remember { mutableStateOf(true) }

    LaunchedEffect(anime.id) {
        isLoadingDetails = true
        detailedData = viewModel.fetchDetailedAnimeData(anime.id)
        isLoadingDetails = false
    }

    val displayData = detailedData ?: anime

    // Get screen dimensions for dismiss calculations
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThreshold = screenHeightPx / 2f
    val velocityThreshold = 500f // pixels per second - fast swipe detection

    // Swipe state - like iOS initialTouchPoint
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var isUserDragging by remember { mutableStateOf(false) }

    // Velocity tracker for fast swipe detection
    val velocityTracker = remember { VelocityTracker() }

    // LazyList state
    val lazyListState = rememberLazyListState()

    // Check if the LazyColumn is at the very top
    val isAtTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    // Animation for offset - smooth snap back or slide out
    val animatedOffsetY by animateFloatAsState(
        targetValue = if (isDismissing) screenHeightPx else offsetY,
        animationSpec = if (isDismissing) {
            tween(250, easing = FastOutSlowInEasing)
        } else {
            spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioMediumBouncy
            )
        },
        label = "offsetY",
        finishedListener = {
            if (isDismissing) {
                onDismiss()
            }
        }
    )

    // Reset offsetY when animation snaps back to 0
    LaunchedEffect(animatedOffsetY, isUserDragging) {
        if (animatedOffsetY == 0f && offsetY != 0f && !isDismissing && !isUserDragging) {
            offsetY = 0f
        }
    }

    val statusDisplay = when (displayData.status) {
        "RELEASING" -> "Airing"
        "FINISHED" -> "Released"
        "NOT_YET_RELEASED" -> "Not Yet Aired"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> displayData.status ?: "Unknown"
    }

    val formatDisplay = when (displayData.format) {
        "TV" -> "TV Series"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        else -> displayData.format ?: "Unknown"
    }

    // Nested scroll connection - handles scroll from LazyColumn
    // This is like the iOS "disable scrollView while dragging main view" fix
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Only intercept when at top, not dismissing, and scrolling down (positive y)
                if (isAtTop && available.y > 0 && !isDismissing && !isUserDragging) {
                    val newOffset = (offsetY + available.y).coerceAtLeast(0f)
                    offsetY = newOffset

                    // Check if we should dismiss based on position
                    if (offsetY > dismissThreshold) {
                        isDismissing = true
                    }

                    return available // Consume the scroll
                }
                return Offset.Zero // Don't consume
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                .nestedScroll(nestedScrollConnection)
                .pointerInput(Unit) {
                    // iOS-style pan gesture handler
                    detectVerticalDragGestures(
                        onDragStart = {
                            // 1. Store the starting position (like iOS .began)
                            isUserDragging = true
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            // 3. Decide whether to dismiss or snap back (like iOS .ended)
                            isUserDragging = false

                            // Calculate velocity
                            val velocity = velocityTracker.calculateVelocity().y
                            velocityTracker.resetTracking()

                            // Logic: Dismiss if dragged past half screen OR swiped down fast
                            // This matches the iOS: if self.view.frame.origin.y > threshold || velocity.y > 500
                            if (offsetY > dismissThreshold || velocity > velocityThreshold) {
                                // Dismiss the view
                                isDismissing = true
                            } else {
                                // FIX FOR "GETTING STUCK": Snap back to the top (y = 0)
                                offsetY = 0f
                            }
                        },
                        onDragCancel = {
                            isUserDragging = false
                            velocityTracker.resetTracking()
                            // Snap back on cancel
                            offsetY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            // 2. Move the view with the finger (like iOS .changed)
                            if (!isDismissing) {
                                // Track velocity
                                velocityTracker.addPointerInputChange(change)

                                // Calculate new position
                                val newOffset = offsetY + dragAmount

                                // Constraint: Don't let the view drag higher than the top (y < 0)
                                // This matches iOS: if newY >= 0 { self.view.frame.origin.y = newY }
                                offsetY = newOffset.coerceAtLeast(0f)

                                // Consume the gesture to prevent it from passing through
                                change.consume()
                            }
                        }
                    )
                }
        ) {
            // Banner
            if (!displayData.banner.isNullOrEmpty() || displayData.cover.isNotEmpty()) {
                AsyncImage(
                    model = displayData.banner ?: displayData.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(280.dp).background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                        )
                    )
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 40.dp, end = 8.dp).align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape).zIndex(10f)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            // Drag indicator - shows user they can swipe to dismiss
            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .width(40.dp).height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)).zIndex(5f)
            )

            if (isLoadingDetails) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp).zIndex(10f),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 200.dp, bottom = 24.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-40).dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Surface(
                            modifier = Modifier.width(120.dp).height(175.dp),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 12.dp,
                            color = Color.Transparent
                        ) {
                            AsyncImage(
                                model = displayData.cover, contentDescription = displayData.title,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayData.title, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                            if (!displayData.titleEnglish.isNullOrEmpty() && displayData.titleEnglish != displayData.title) {
                                Text(
                                    text = displayData.titleEnglish, style = MaterialTheme.typography.bodyMedium,
                                    color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                displayData.averageScore?.let { score ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.background(Color(0xFFFFD700).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            String.format(Locale.US, "%.1f", score / 10.0),
                                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (displayData.status) {
                                        "RELEASING" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                        "FINISHED" -> Color(0xFF2196F3).copy(alpha = 0.15f)
                                        "NOT_YET_RELEASED" -> Color(0xFFFFC107).copy(alpha = 0.15f)
                                        "CANCELLED" -> Color(0xFFF44336).copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, when (displayData.status) {
                                        "RELEASING" -> Color(0xFF4CAF50)
                                        "FINISHED" -> Color(0xFF2196F3)
                                        "NOT_YET_RELEASED" -> Color(0xFFFFC107)
                                        "CANCELLED" -> Color(0xFFF44336)
                                        else -> Color.Gray.copy(alpha = 0.5f)
                                    })
                                ) {
                                    Text(
                                        statusDisplay, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                        color = when (displayData.status) {
                                            "RELEASING" -> Color(0xFF4CAF50)
                                            "FINISHED" -> Color(0xFF2196F3)
                                            "NOT_YET_RELEASED" -> Color(0xFFFFC107)
                                            "CANCELLED" -> Color(0xFFF44336)
                                            else -> if (isOled) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (displayData.year != null || displayData.format != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    listOfNotNull(displayData.year?.toString(), formatDisplay).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Buttons
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onPlayEpisode(1) }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Watching", fontWeight = FontWeight.Medium)
                        }
                        if (isLoggedIn) {
                            OutlinedButton(
                                onClick = {
                                    onToggleFavorite()
                                    Toast.makeText(context, if (isFavorite) "Removed from Favorites" else "Added to Favorites", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary),
                                enabled = isFavorite || canAddFavorite
                            ) {
                                Icon(
                                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star, null, Modifier.size(18.dp),
                                    tint = if (isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Status chips
                if (isLoggedIn) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Add to List", style = MaterialTheme.typography.labelLarge,
                                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { StatusChip("Watching", Icons.Default.PlayArrow, Color(0xFF2196F3), currentStatus == "CURRENT") { if (currentStatus == "CURRENT") { onRemove(); Toast.makeText(context, "Removed from Watching", Toast.LENGTH_SHORT).show() } else onUpdateStatus("CURRENT") } }
                                item { StatusChip("Planning", Icons.Default.Schedule, Color(0xFF9C27B0), currentStatus == "PLANNING") { if (currentStatus == "PLANNING") { onRemove(); Toast.makeText(context, "Removed from Planning", Toast.LENGTH_SHORT).show() } else onUpdateStatus("PLANNING") } }
                                item { StatusChip("Completed", Icons.Default.Check, Color(0xFF4CAF50), currentStatus == "COMPLETED") { if (currentStatus == "COMPLETED") { onRemove(); Toast.makeText(context, "Removed from Completed", Toast.LENGTH_SHORT).show() } else onUpdateStatus("COMPLETED") } }
                                item { StatusChip("On Hold", Icons.Default.Pause, Color(0xFFFFC107), currentStatus == "PAUSED") { if (currentStatus == "PAUSED") { onRemove(); Toast.makeText(context, "Removed from On Hold", Toast.LENGTH_SHORT).show() } else onUpdateStatus("PAUSED") } }
                                item { StatusChip("Dropped", Icons.Default.Close, Color(0xFFF44336), currentStatus == "DROPPED") { if (currentStatus == "DROPPED") { onRemove(); Toast.makeText(context, "Removed from Dropped", Toast.LENGTH_SHORT).show() } else onUpdateStatus("DROPPED") } }
                            }
                        }
                    }
                }

                // Info
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    InfoSection("Information", isOled) {
                        InfoGrid(listOfNotNull(
                            if (displayData.episodes > 0) "Episodes" to "${displayData.episodes}" else null,
                            displayData.duration?.let { "Duration" to "${it} min per ep" },
                            if (displayData.format != null) "Format" to formatDisplay else null,
                            if (displayData.status != null) "Status" to statusDisplay else null,
                            if (displayData.season != null && displayData.year != null) "Season" to "${displayData.season.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }} ${displayData.year}" else null,
                            displayData.source?.let { "Source" to it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } },
                            if (displayData.studios.isNotEmpty()) "Studio" to displayData.studios.filter { it.isAnimationStudio }.joinToString(", ") { it.name } else null,
                            displayData.popularity?.let { "Popularity" to "#$it" },
                            displayData.favourites?.let { "Favorites" to formatNumber(it) },
                            displayData.countryOfOrigin?.let { "Country" to it }
                        ), isOled)
                    }
                }

                // Genres
                if (displayData.genres.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Genres", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(displayData.genres) { genre ->
                                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                                        Text(genre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Synopsis
                if (!displayData.description.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Synopsis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(8.dp))
                            val cleanDescription = displayData.description.replace("<br>", "\n").replace("<br/>", "\n")
                                .replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
                            Text(cleanDescription, style = MaterialTheme.typography.bodyMedium,
                                color = if (isOled) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (showFullDescription) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                            if (cleanDescription.length > 300) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { showFullDescription = !showFullDescription }) {
                                    Text(if (showFullDescription) "Show Less" else "Read More")
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, icon: ImageVector, color: Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 12.sp) } },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.2f), selectedLabelColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) color else Color.Gray.copy(alpha = 0.3f))
    )
}

@Composable
private fun InfoSection(title: String, isOled: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>, isOled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowItems.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun formatNumber(num: Int): String = when {
    num >= 1_000_000 -> String.format(Locale.US, "%.1fM", num / 1_000_000.0)
    num >= 1_000 -> String.format(Locale.US, "%.1fK", num / 1_000.0)
    else -> num.toString()
}
