package com.blissless.anime.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.AnimeMedia
import com.blissless.anime.MainViewModel
import com.blissless.anime.TmdbEpisode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun EpisodeSelectionDialog(anime: AnimeMedia, isOled: Boolean, onDismiss: () -> Unit, onEpisodeSelect: (Int) -> Unit) {
    val context = LocalContext.current
    val total = anime.totalEpisodes
    val released = anime.latestEpisode?.let { it - 1 } ?: total
    val episodeCount = if (total > 0) total else released.coerceAtLeast(1)
    val currentProgress = anime.progress
    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context).data(anime.cover).memoryCacheKey(anime.cover).diskCacheKey(anime.cover).crossfade(false).build()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().height(450.dp).padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(model = imageRequest, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.width(50.dp).height(70.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Progress: $currentProgress / ${if (total > 0) total else "??"}", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        anime.year?.let { Text("Released: $it", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))); Spacer(modifier = Modifier.width(4.dp)); Text("Watched", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(12.dp).background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))); Spacer(modifier = Modifier.width(4.dp)); Text("Not aired", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(episodeCount) { index ->
                        val episodeNum = index + 1
                        val isWatched = episodeNum <= currentProgress
                        val hasAired = episodeNum <= released
                        EpisodeButton(episodeNumber = episodeNum, isWatched = isWatched, hasAired = hasAired, isOled = isOled, onClick = { if (hasAired) onEpisodeSelect(episodeNum) })
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val nextEp = currentProgress + 1
                    if (nextEp <= released) {
                        Button(onClick = { onEpisodeSelect(nextEp) }, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Resume Ep $nextEp") }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun EpisodeButton(episodeNumber: Int, isWatched: Boolean, hasAired: Boolean, isOled: Boolean, onClick: () -> Unit) {
    val backgroundColor = when {
        isWatched -> MaterialTheme.colorScheme.primary
        hasAired -> if (isOled) Color(0xFF1A1A1A) else Color(0xFF2A2A2A)
        else -> Color(0xFF1A1A1A).copy(alpha = 0.5f)
    }
    val contentColor = when { isWatched -> Color.White; hasAired -> Color.White; else -> Color.Gray.copy(alpha = 0.5f) }
    val borderColor = when { isWatched -> MaterialTheme.colorScheme.primary; hasAired -> Color.White.copy(alpha = 0.1f); else -> Color.Gray.copy(alpha = 0.2f) }

    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = backgroundColor, contentColor = contentColor, modifier = Modifier.size(48.dp).alpha(if (hasAired) 1f else 0.5f), border = BorderStroke(1.dp, borderColor)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$episodeNumber", style = MaterialTheme.typography.labelLarge, fontWeight = if (isWatched) FontWeight.Bold else FontWeight.Medium)
            if (isWatched) { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd).size(12.dp).padding(2.dp), tint = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichEpisodeScreen(anime: AnimeMedia, viewModel: MainViewModel, isOled: Boolean, onDismiss: () -> Unit, onEpisodeSelect: (Int) -> Unit) {
    val context = LocalContext.current
    val total = anime.totalEpisodes
    val released = anime.latestEpisode?.let { it - 1 } ?: total
    val episodeCount = if (total > 0) total else released.coerceAtLeast(1)
    val currentProgress = anime.progress

    var tmdbEpisodes by remember { mutableStateOf<List<TmdbEpisode>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }
    var selectedEpisode by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val windowInfo = LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height.toFloat()
    val dismissThreshold = screenHeightPx / 2f

    val offsetY = remember { Animatable(0f) }

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val currentOffset = offsetY.value

                if (currentOffset > 0) {
                    if (available.y < 0) {
                        scope.launch {
                            offsetY.snapTo((currentOffset + available.y).coerceAtLeast(0f))
                        }
                        return available
                    }
                    if (available.y > 0) {
                        scope.launch {
                            offsetY.snapTo(currentOffset + available.y)
                        }
                        return available
                    }
                }

                if (isAtTop && currentOffset == 0f && available.y > 0) {
                    scope.launch { offsetY.snapTo(available.y) }
                    return available
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val currentOffset = offsetY.value

                if (currentOffset == 0f) return Velocity.Zero

                val shouldDismiss = currentOffset > dismissThreshold || available.y > 2000f

                if (shouldDismiss) {
                    scope.launch {
                        offsetY.animateTo(screenHeightPx, tween(250, easing = FastOutSlowInEasing))
                        onDismiss()
                    }
                } else {
                    scope.launch {
                        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }

                return available
            }
        }
    }

    suspend fun handleDragEnd() {
        val currentOffset = offsetY.value
        if (currentOffset == 0f) return

        val shouldDismiss = currentOffset > dismissThreshold
        if (shouldDismiss) {
            offsetY.animateTo(screenHeightPx, tween(250, easing = FastOutSlowInEasing))
            onDismiss()
        } else {
            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    // Updated to use relations-based season detection inside ViewModel
    LaunchedEffect(anime.id) {
        isLoadingEpisodes = true
        // Pass anime.year for TMDB matching
        val result = viewModel.fetchTmdbEpisodes(anime.title, anime.id, anime.year)
        if (result.isNotEmpty()) tmdbEpisodes = result
        isLoadingEpisodes = false
    }

    LaunchedEffect(Unit) { if (currentProgress > 0 && currentProgress < episodeCount) { kotlinx.coroutines.delay(100); listState.animateScrollToItem(currentProgress) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                .nestedScroll(nestedScrollConnection)
        ) {
            if (!anime.banner.isNullOrEmpty() || anime.cover.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { scope.launch { handleDragEnd() } },
                                onVerticalDrag = { _, dragAmount ->
                                    scope.launch {
                                        val multiplier = 3.0f
                                        offsetY.snapTo((offsetY.value + (dragAmount * multiplier)).coerceAtLeast(0f))
                                    }
                                }
                            )
                        }
                ) {
                    AsyncImage(
                        model = anime.banner.takeIf { !it.isNullOrEmpty() } ?: anime.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(colors = listOf(Color.Transparent, if (isOled) Color.Black else MaterialTheme.colorScheme.background))))
                }
            }

            IconButton(onClick = onDismiss, modifier = Modifier.padding(top = 40.dp, end = 8.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.6f), CircleShape).zIndex(10f)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp)) }
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).width(40.dp).height(4.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)).zIndex(5f))
            if (isLoadingEpisodes) { CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp).zIndex(10f), color = MaterialTheme.colorScheme.primary) }

            Column(modifier = Modifier.fillMaxSize().padding(top = 160.dp)) {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = { scope.launch { handleDragEnd() } },
                                    onVerticalDrag = { _, dragAmount ->
                                        scope.launch {
                                            val multiplier = 3.0f
                                            offsetY.snapTo((offsetY.value + (dragAmount * multiplier)).coerceAtLeast(0f))
                                        }
                                    }
                                )
                            }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = anime.cover, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.width(60.dp).height(85.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Progress: $currentProgress / ${if (total > 0) total else "??"}", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (released > 0) { Text(text = "$released episodes aired", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))); Spacer(modifier = Modifier.width(4.dp)); Text("Watched", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))); Spacer(modifier = Modifier.width(4.dp)); Text("Current", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(10.dp).background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))); Spacer(modifier = Modifier.width(4.dp)); Text("Unaired", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val nextEp = currentProgress + 1
                            if (nextEp <= released) { item { FilterChip(selected = true, onClick = { onEpisodeSelect(nextEp) }, label = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Resume Ep $nextEp") } }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)) } }
                            item { FilterChip(selected = false, onClick = { scope.launch { listState.animateScrollToItem(0) } }, label = { Text("Ep 1") }, colors = FilterChipDefaults.filterChipColors(containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface)) }
                            if (released > 1) { item { FilterChip(selected = false, onClick = { scope.launch { listState.animateScrollToItem(released - 1) } }, label = { Text("Latest: Ep $released") }, colors = FilterChipDefaults.filterChipColors(containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface)) } }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = if (isOled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)) {
                    if (tmdbEpisodes.isNotEmpty()) {
                        items(tmdbEpisodes.size) { index ->
                            val ep = tmdbEpisodes[index]
                            val episodeNum = ep.episode
                            val isWatched = episodeNum <= currentProgress
                            val isCurrent = episodeNum == currentProgress + 1
                            val hasAired = episodeNum <= released
                            RichTmdbEpisodeCard(episodeNumber = episodeNum, title = ep.title, description = ep.description, image = ep.image, isWatched = isWatched, isCurrent = isCurrent, hasAired = hasAired, isOled = isOled, isSelected = selectedEpisode == episodeNum, onSelect = { selectedEpisode = episodeNum }, onPlay = { if (hasAired) onEpisodeSelect(episodeNum) })
                        }
                    } else if (!isLoadingEpisodes) {
                        items(episodeCount) { index ->
                            val episodeNum = index + 1
                            val isWatched = episodeNum <= currentProgress
                            val isCurrent = episodeNum == currentProgress + 1
                            val hasAired = episodeNum <= released
                            SimpleRichEpisodeCard(episodeNumber = episodeNum, isWatched = isWatched, isCurrent = isCurrent, hasAired = hasAired, isOled = isOled, animeCover = anime.cover, isSelected = selectedEpisode == episodeNum, onSelect = { selectedEpisode = episodeNum }, onPlay = { if (hasAired) onEpisodeSelect(episodeNum) })
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RichTmdbEpisodeCard(
    episodeNumber: Int,
    title: String?,
    description: String?,
    image: String?,
    isWatched: Boolean,
    isCurrent: Boolean,
    hasAired: Boolean,
    isOled: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    val backgroundColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isWatched -> if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        hasAired -> if (isOled) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }

    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    val contentAlpha = if (hasAired) 1f else 0.4f

    // Pre-calculate colors to avoid complex inline when logic
    val surfaceColor = when {
        isWatched -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.secondary
        else -> Color.Black.copy(alpha = 0.6f)
    }

    AnimatedVisibility(visible = true, enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.95f), exit = fadeOut(tween(200))) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).then(if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            onClick = { if (hasAired) { onSelect(); onPlay() } }
        ) {
            Column(modifier = Modifier.fillMaxWidth().alpha(contentAlpha)) {
                if (!image.isNullOrEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        AsyncImage(model = image, contentDescription = "Episode $episodeNumber", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        // FIX: Added {} to Box to ensure valid syntax
                        Box(
                            modifier = Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        )
                        // Fixed Surface call with separated logic
                        Surface(
                            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                            shape = RoundedCornerShape(6.dp),
                            color = surfaceColor
                        ) {
                            Text(
                                text = "EP $episodeNumber",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (isWatched) {
                            Surface(
                                modifier = Modifier.padding(8.dp).align(Alignment.TopEnd),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = "Watched",
                                    tint = Color.White,
                                    modifier = Modifier.padding(4.dp).size(16.dp)
                                )
                            }
                        }
                        if (hasAired) {
                            FilledTonalIconButton(
                                onClick = onPlay,
                                modifier = Modifier.align(Alignment.Center).size(56.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.6f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (image.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier.size(44.dp).background(
                                    when {
                                        isWatched -> MaterialTheme.colorScheme.primary
                                        isCurrent -> MaterialTheme.colorScheme.secondary
                                        hasAired -> if (isOled) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surfaceVariant
                                        else -> Color.Gray.copy(alpha = 0.2f)
                                    },
                                    RoundedCornerShape(10.dp)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isWatched -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Watched", tint = Color.White, modifier = Modifier.size(24.dp))
                                    isCurrent -> Icon(Icons.Default.PlayArrow, contentDescription = "Current", tint = Color.White, modifier = Modifier.size(24.dp))
                                    else -> Text(
                                        text = "$episodeNumber",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasAired) {
                                            if (isOled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            Color.Gray
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title?.ifEmpty { "Episode $episodeNumber" } ?: "Episode $episodeNumber",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    !hasAired -> "Not yet aired"
                                    isCurrent -> "Up next"
                                    isWatched -> "Watched"
                                    else -> "Available"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    !hasAired -> Color.Gray
                                    isCurrent -> MaterialTheme.colorScheme.secondary
                                    isWatched -> MaterialTheme.colorScheme.primary
                                    else -> if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (image.isNullOrEmpty() && hasAired) {
                            FilledTonalIconButton(
                                onClick = onPlay,
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    contentColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    if (!description.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleRichEpisodeCard(
    episodeNumber: Int,
    isWatched: Boolean,
    isCurrent: Boolean,
    hasAired: Boolean,
    isOled: Boolean,
    animeCover: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    val backgroundColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isWatched -> if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        hasAired -> if (isOled) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }

    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    val contentAlpha = if (hasAired) 1f else 0.4f

    AnimatedVisibility(visible = true, enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.95f), exit = fadeOut(tween(200))) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).then(if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            onClick = { if (hasAired) { onSelect(); onPlay() } }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp).alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).background(
                        when {
                            isWatched -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.secondary
                            hasAired -> if (isOled) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surfaceVariant
                            else -> Color.Gray.copy(alpha = 0.2f)
                        },
                        RoundedCornerShape(10.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isWatched -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Watched", tint = Color.White, modifier = Modifier.size(24.dp))
                        isCurrent -> Icon(Icons.Default.PlayArrow, contentDescription = "Current", tint = Color.White, modifier = Modifier.size(24.dp))
                        else -> Text(
                            text = "$episodeNumber",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hasAired) {
                                if (isOled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                Color.Gray
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Episode $episodeNumber",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            !hasAired -> "Not yet aired"
                            isCurrent -> "Up next"
                            isWatched -> "Watched"
                            else -> "Available"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            !hasAired -> Color.Gray
                            isCurrent -> MaterialTheme.colorScheme.secondary
                            isWatched -> MaterialTheme.colorScheme.primary
                            else -> if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (hasAired) {
                    FilledTonalIconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}