package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.blissless.anime.data.models.AnimeRelation
import com.blissless.anime.data.models.TagData
import com.blissless.anime.data.models.DetailedAnimeData
import com.blissless.anime.data.models.LocalAnimeEntry
import com.blissless.anime.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedAnimeScreen(
    anime: DetailedAnimeData,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    currentStatus: String? = null,
    localStatus: String? = null,
    isLoggedIn: Boolean = false,
    isFavorite: Boolean = false,
    isLocalFavorite: Boolean = false,
    onDismiss: () -> Unit,
    onSwipeToClose: () -> Unit = {},
    onPlayEpisode: (Int) -> Unit = {},
    onUpdateStatus: (String?) -> Unit = {},
    onRemove: () -> Unit = {},
    onToggleFavorite: (DetailedAnimeData) -> Unit = {},
    onToggleLocalFavorite: (Int) -> Unit = {},
    onUpdateLocalStatus: (String?) -> Unit = {},
    onRemoveLocalStatus: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onRelationClick: (AnimeRelation) -> Unit = {}
) {
    val context = LocalContext.current
    var showFullDescription by remember { mutableStateOf(false) }

    var detailedData by remember { mutableStateOf<DetailedAnimeData?>(null) }
    var isLoadingDetails by remember { mutableStateOf(true) }
    var relations by remember { mutableStateOf<List<AnimeRelation>>(emptyList()) }

    var isVisible by remember { mutableStateOf(false) }
    var previousAnimeId by remember { mutableIntStateOf(anime.id) }
    var isTransitioning by remember { mutableStateOf(false) }
    var isStatusRateLimited by remember { mutableStateOf(false) }
    var selectedTagForDescription by remember { mutableStateOf<TagData?>(null) }

    val localFavorites by viewModel.localFavorites.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
    val effectiveLocalFavorite = if (isLoggedIn) isLocalFavorite else localFavorites.containsKey(anime.id)
    val effectiveLocalStatus = if (isLoggedIn) null else localAnimeStatus[anime.id]?.status

    val effectiveStatus = if (isLoggedIn) currentStatus else (effectiveLocalStatus ?: localStatus)
    val effectiveOnUpdateStatus = if (isLoggedIn) onUpdateStatus else onUpdateLocalStatus
    val effectiveOnRemove = if (isLoggedIn) onRemove else onRemoveLocalStatus

    val scale by animateFloatAsState(
        targetValue = when {
            isTransitioning -> 0.95f
            isVisible -> 1f
            else -> 0.92f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            isTransitioning -> 0.7f
            isVisible -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 300),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    LaunchedEffect(anime.id) {
        isLoadingDetails = true
        
        if (previousAnimeId != 0 && previousAnimeId != anime.id) {
            isTransitioning = true
            kotlinx.coroutines.delay(150)
            isTransitioning = false
        }
        previousAnimeId = anime.id
        
        try {
            detailedData = viewModel.fetchDetailedAnimeData(anime.id)
            relations = detailedData?.relations ?: anime.relations
        } catch (e: Exception) {
        } finally {
            isLoadingDetails = false
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            isLoadingDetails = false
        }
    }

    val displayData = detailedData ?: anime

    val windowInfo = LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height.toFloat()
    val dismissThreshold = screenHeightPx / 2f

    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val lazyListState = rememberLazyListState()

    val isAtTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
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

                val shouldDismiss = currentOffset > dismissThreshold || available.y > 500f

                if (shouldDismiss) {
                    scope.launch {
                        offsetY.animateTo(screenHeightPx, tween(180, easing = FastOutSlowInEasing))
                        onDismiss()
                        onSwipeToClose()
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
                .graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                    this.alpha = alpha
                }
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                .nestedScroll(nestedScrollConnection)
        ) {
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

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .zIndex(10f)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp))
            }

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
                                    color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
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

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOled) Color(0xFF0D0D0D).copy(alpha = 0.95f) else Color(0xFF181818).copy(alpha = 0.9f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val notYetAired = displayData.status == "NOT_YET_RELEASED"
                            val effectiveIsFavorite = if (isLoggedIn) isFavorite else effectiveLocalFavorite
                            val effectiveOnToggleFavorite: () -> Unit = if (isLoggedIn) {
                                { onToggleFavorite(displayData) }
                            } else {
                                { onToggleLocalFavorite(anime.id) }
                            }

                            Button(
                                onClick = { onPlayEpisode(1) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !notYetAired,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Watching", fontWeight = FontWeight.Medium)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isLoggedIn) {
                                        onToggleFavorite(displayData)
                                        Toast.makeText(context, if (isFavorite) "Removed from Favorites" else "Added to Favorites", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.toggleOfflineFavorite(
                                            anime.id,
                                            anime.title,
                                            anime.cover,
                                            anime.banner,
                                            anime.year,
                                            anime.averageScore
                                        )
                                        Toast.makeText(context, if (effectiveLocalFavorite) "Removed from Favorites" else "Added to Favorites", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFF1744).copy(alpha = 0.15f), contentColor = if (effectiveIsFavorite) Color(0xFFFF1744) else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    if (effectiveIsFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp),
                                    tint = if (effectiveIsFavorite) Color(0xFFFF1744) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOled) Color(0xFF121212).copy(alpha = 0.9f) else Color(0xFF1A1A1A).copy(alpha = 0.85f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isLoggedIn) "Add to List" else "Local List",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                )
                                if (!isLoggedIn) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "(Offline)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val statusToCheck = if (isLoggedIn) currentStatus else effectiveLocalStatus
                                val onUpdate: (String) -> Unit = if (isLoggedIn) {
                                    { status -> onUpdateStatus(status) }
                                } else { status ->
                                    viewModel.setLocalAnimeStatus(
                                        anime.id,
                                        LocalAnimeEntry(
                                            id = anime.id,
                                            status = status,
                                            progress = 0,
                                            totalEpisodes = anime.episodes,
                                            title = anime.title,
                                            cover = anime.cover,
                                            banner = anime.banner,
                                            year = anime.year,
                                            averageScore = anime.averageScore
                                        )
                                    )
                                }
                                val onRemoveStatus: () -> Unit = if (isLoggedIn) {
                                    { onRemove() }
                                } else {
                                    { viewModel.setLocalAnimeStatus(anime.id, null) }
                                }

                                StatusChip("Watching", Icons.Default.PlayArrow, Color(0xFF2196F3), statusToCheck == "CURRENT") {
                                    if (isStatusRateLimited) {
                                        Toast.makeText(context, "Please wait before changing again", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isStatusRateLimited = true
                                        scope.launch {
                                            kotlinx.coroutines.delay(3000)
                                            isStatusRateLimited = false
                                        }
                                        if (statusToCheck == "CURRENT") {
                                            onRemoveStatus()
                                            Toast.makeText(context, "Removed from Watching", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdate("CURRENT")
                                            Toast.makeText(context, "Added to Watching", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                StatusChip("Planning", Icons.Default.Schedule, Color(0xFF9C27B0), statusToCheck == "PLANNING") {
                                    if (isStatusRateLimited) {
                                        Toast.makeText(context, "Please wait before changing again", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isStatusRateLimited = true
                                        scope.launch {
                                            kotlinx.coroutines.delay(3000)
                                            isStatusRateLimited = false
                                        }
                                        if (statusToCheck == "PLANNING") {
                                            onRemoveStatus()
                                            Toast.makeText(context, "Removed from Planning", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdate("PLANNING")
                                            Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                StatusChip("Completed", Icons.Default.Check, Color(0xFF4CAF50), statusToCheck == "COMPLETED") {
                                    if (isStatusRateLimited) {
                                        Toast.makeText(context, "Please wait before changing again", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isStatusRateLimited = true
                                        scope.launch {
                                            kotlinx.coroutines.delay(3000)
                                            isStatusRateLimited = false
                                        }
                                        if (statusToCheck == "COMPLETED") {
                                            onRemoveStatus()
                                            Toast.makeText(context, "Marked as Completed", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdate("COMPLETED")
                                            Toast.makeText(context, "Marked as Completed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                StatusChip("On Hold", Icons.Default.Pause, Color(0xFFFFC107), statusToCheck == "PAUSED") {
                                    if (isStatusRateLimited) {
                                        Toast.makeText(context, "Please wait before changing again", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isStatusRateLimited = true
                                        scope.launch {
                                            kotlinx.coroutines.delay(3000)
                                            isStatusRateLimited = false
                                        }
                                        if (statusToCheck == "PAUSED") {
                                            onRemoveStatus()
                                            Toast.makeText(context, "Removed from On Hold", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdate("PAUSED")
                                            Toast.makeText(context, "Added to On Hold", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                StatusChip("Dropped", Icons.Default.Close, Color(0xFFF44336), statusToCheck == "DROPPED") {
                                    if (isStatusRateLimited) {
                                        Toast.makeText(context, "Please wait before changing again", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isStatusRateLimited = true
                                        scope.launch {
                                            kotlinx.coroutines.delay(3000)
                                            isStatusRateLimited = false
                                        }
                                        if (statusToCheck == "DROPPED") {
                                            onRemoveStatus()
                                            Toast.makeText(context, "Marked as Dropped", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdate("DROPPED")
                                            Toast.makeText(context, "Marked as Dropped", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                StatusChip("Remove", Icons.Default.Delete, Color(0xFFF44336), false) {
                                    if (isStatusRateLimited) {
                                        Toast.makeText(context, "Please wait before changing again", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isStatusRateLimited = true
                                        scope.launch {
                                            kotlinx.coroutines.delay(3000)
                                            isStatusRateLimited = false
                                        }
                                        if (statusToCheck != null) {
                                            onRemoveStatus()
                                            Toast.makeText(context, "Removed from list", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Anime not in list", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    // Redesigned Information Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOled) Color(0xFF0D0D0D).copy(alpha = 0.95f) else Color(0xFF181818).copy(alpha = 0.9f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Main stats row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val episodeText = when {
                                    displayData.episodes > 0 -> "${displayData.episodes} eps"
                                    displayData.latestEpisode != null && displayData.latestEpisode > 0 -> "Ep ${displayData.latestEpisode}"
                                    else -> null
                                }
                                episodeText?.let { text ->
                                    InfoStat("Episodes", text, Icons.Default.PlayCircle, MaterialTheme.colorScheme.primary)
                                }
                                displayData.duration?.let {
                                    InfoStat("Duration", "$it min", Icons.Default.Timer, MaterialTheme.colorScheme.primary)
                                }
                                displayData.averageScore?.let { score ->
                                    InfoStat("Score", String.format(Locale.US, "%.1f", score / 10.0), Icons.Default.Star, Color(0xFFFFD700))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = if (isOled) Color.White.copy(alpha = 0.08f) else Color.Gray.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Details grid
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                displayData.format?.let { formatDisplay }
                                    .takeIf { displayData.format != null }?.let {
                                        InfoRow("Format", formatDisplay, isOled)
                                    }
                                if (displayData.season != null && displayData.year != null) {
                                    InfoRow("Season", "${displayData.season.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }} ${displayData.year}", isOled)
                                }
                                if (displayData.status != null) {
                                    InfoRow("Status", statusDisplay, isOled)
                                }
                                displayData.source?.let {
                                    InfoRow("Source", it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }, isOled)
                                }
                                if (displayData.studios.isNotEmpty()) {
                                    val studio = displayData.studios.filter { it.isAnimationStudio }.joinToString(", ") { it.name }
                                    if (studio.isNotEmpty()) InfoRow("Studio", studio, isOled)
                                }
                                displayData.countryOfOrigin?.let {
                                    InfoRow("Country", it, isOled)
                                }
                            }
                        }
                    }
                }

                if (displayData.genres.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOled) Color(0xFF0D0D0D).copy(alpha = 0.95f) else Color(0xFF181818).copy(alpha = 0.9f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Category,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Genres", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayData.genres.forEach { genre ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                genre,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (displayData.tags.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOled) Color(0xFF0D0D0D).copy(alpha = 0.95f) else Color(0xFF181818).copy(alpha = 0.9f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Label,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayData.tags.filter { !it.isMediaSpoiler }.take(15).forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                                                selectedTagForDescription = tag
                                            }
                                        ) {
                                            Text(
                                                tag.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!displayData.description.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        // Redesigned Synopsis Section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOled) Color(0xFF0D0D0D).copy(alpha = 0.95f) else Color(0xFF181818).copy(alpha = 0.9f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Synopsis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                val cleanDescription = displayData.description.replace("<br>", "\n").replace("<br/>", "\n")
                                    .replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
                                Text(cleanDescription, style = MaterialTheme.typography.bodyMedium,
                                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (showFullDescription) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                                    lineHeight = 22.sp)
                                if (cleanDescription.length > 250) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { showFullDescription = !showFullDescription },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (showFullDescription) "Show Less" else "Read More",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val filteredRelations = displayData.relations.filter { relation ->
                    relation.format != "MANGA" && relation.format != "NOVEL" && relation.format != "ONE_SHOT"
                }

                if (filteredRelations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOled) Color(0xFF0E0E0E).copy(alpha = 0.95f) else Color(0xFF151515).copy(alpha = 0.9f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Relations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(filteredRelations) { relation ->
                                        Column(
                                            modifier = Modifier
                                                .width(110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    onRelationClick(relation)
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(3f / 4f)
                                            ) {
                                                Card(
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxSize(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                ) {
                                                    AsyncImage(
                                                        model = relation.cover,
                                                        contentDescription = relation.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                // Episode badge
                                                val episodeText = when {
                                                    relation.episodes != null && relation.episodes > 0 -> "${relation.episodes} ${if (relation.episodes == 1) "ep" else "eps"}"
                                                    relation.latestEpisode != null && relation.latestEpisode > 0 -> "Ep ${relation.latestEpisode}"
                                                    else -> null
                                                }
                                                episodeText?.let { text ->
                                                    Surface(
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .align(Alignment.BottomStart),
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color.Black.copy(alpha = 0.8f)
                                                    ) {
                                                        Text(
                                                            text,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                relation.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                            )
                                            relation.format?.let { format ->
                                                val formatDisplay = when (format) {
                                                    "TV" -> "TV"
                                                    "TV_SHORT" -> "TV Short"
                                                    "MOVIE" -> "Movie"
                                                    "SPECIAL" -> "Special"
                                                    "OVA" -> "OVA"
                                                    "ONA" -> "ONA"
                                                    "MANGA" -> "Manga"
                                                    "NOVEL" -> "Novel"
                                                    "ONE_SHOT" -> "One Shot"
                                                    "MUSIC" -> "Music"
                                                    else -> format
                                                }
                                                Text(
                                                    formatDisplay,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (selectedTagForDescription != null) {
        val tag: TagData = selectedTagForDescription!!
        ModalBottomSheet(
            onDismissRequest = { selectedTagForDescription = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        tag.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                tag.rank?.let { rank ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Rank: $rank%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = if (isOled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))
                val description = tag.description ?: "No description available."
                val cleanDescription = description.replace("<br>", "\n").replace("<br/>", "\n")
                    .replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
                    .replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                Text(
                    cleanDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOled) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, icon: ImageVector, color: Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Icon(icon, null, Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)); Text(label, fontSize = 12.sp); Spacer(Modifier.width(3.dp)) } },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.2f), selectedLabelColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) color else Color.Gray.copy(alpha = 0.3f))
    )
}

@Composable
private fun InfoSection(isOled: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
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

@Composable
private fun InfoStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun InfoRow(label: String, value: String, isOled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatNumber(num: Int): String = when {
    num >= 1_000_000 -> String.format(Locale.US, "%.1fM", num / 1_000_000.0)
    num >= 1_000 -> String.format(Locale.US, "%.1fK", num / 1_000.0)
    else -> num.toString()
}
