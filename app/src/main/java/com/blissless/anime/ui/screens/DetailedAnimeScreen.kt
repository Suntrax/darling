package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.blissless.anime.data.models.DetailedAnimeData
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
    isLoggedIn: Boolean = false,
    isFavorite: Boolean = false,
    canAddFavorite: Boolean = true,
    onDismiss: () -> Unit,
    onSwipeToClose: () -> Unit = {},
    onPlayEpisode: (Int) -> Unit = {},
    onUpdateStatus: (String?) -> Unit = {},
    onRemove: () -> Unit = {},
    onToggleFavorite: (DetailedAnimeData) -> Unit = {},
    onLoginClick: () -> Unit = {},
    onRelationClick: (AnimeRelation) -> Unit = {}
) {
    val context = LocalContext.current
    var showFullDescription by remember { mutableStateOf(false) }

    var detailedData by remember { mutableStateOf<DetailedAnimeData?>(null) }
    var isLoadingDetails by remember { mutableStateOf(true) }
    var relations by remember { mutableStateOf<List<AnimeRelation>>(emptyList()) }

    // Animation states for entry and transitions
    var isVisible by remember { mutableStateOf(false) }
    var previousAnimeId by remember { mutableIntStateOf(anime.id) }
    var isTransitioning by remember { mutableStateOf(false) }
    
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

    // Track anime changes for transition animation
    LaunchedEffect(anime.id) {
        // Reset loading state when anime changes
        isLoadingDetails = true
        
        if (previousAnimeId != 0 && previousAnimeId != anime.id) {
            // This is a navigation (relation click or back), trigger transition animation
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
    
    // Ensure loading is reset when composable leaves composition
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

                val shouldDismiss = currentOffset > dismissThreshold || available.y > 2000f

                if (shouldDismiss) {
                    scope.launch {
                        offsetY.animateTo(screenHeightPx, tween(250, easing = FastOutSlowInEasing))
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
                    .padding(top = 40.dp, end = 8.dp)
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .zIndex(10f)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
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

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isLoggedIn) {
                            Button(
                                onClick = { onPlayEpisode(1) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Watching", fontWeight = FontWeight.Medium)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (!isFavorite && !canAddFavorite) {
                                        Toast.makeText(context, "Maximum 10 favorites! Remove one first.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onToggleFavorite(displayData)
                                        Toast.makeText(context, if (isFavorite) "Removed from Favorites" else "Added to Favorites", Toast.LENGTH_SHORT).show()
                                    }
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
                        } else {
                            Button(
                                onClick = onLoginClick,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Log in to AniList first", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                if (isLoggedIn) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Add to List", style = MaterialTheme.typography.labelLarge,
                                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    StatusChip("Watching", Icons.Default.PlayArrow, Color(0xFF2196F3), currentStatus == "CURRENT") {
                                        if (currentStatus == "CURRENT") {
                                            onRemove()
                                            Toast.makeText(context, "Removed from Watching", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdateStatus("CURRENT")
                                            Toast.makeText(context, "Added to Watching", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    StatusChip("Planning", Icons.Default.Schedule, Color(0xFF9C27B0), currentStatus == "PLANNING") {
                                        if (currentStatus == "PLANNING") {
                                            onRemove()
                                            Toast.makeText(context, "Removed from Planning", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdateStatus("PLANNING")
                                            Toast.makeText(context, "Added to Planning", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    StatusChip("Completed", Icons.Default.Check, Color(0xFF4CAF50), currentStatus == "COMPLETED") {
                                        if (currentStatus == "COMPLETED") {
                                            onRemove()
                                            Toast.makeText(context, "Removed from Completed", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdateStatus("COMPLETED")
                                            Toast.makeText(context, "Marked as Completed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    StatusChip("On Hold", Icons.Default.Pause, Color(0xFFFFC107), currentStatus == "PAUSED") {
                                        if (currentStatus == "PAUSED") {
                                            onRemove()
                                            Toast.makeText(context, "Removed from On Hold", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdateStatus("PAUSED")
                                            Toast.makeText(context, "Added to On Hold", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    StatusChip("Dropped", Icons.Default.Close, Color(0xFFF44336), currentStatus == "DROPPED") {
                                        if (currentStatus == "DROPPED") {
                                            onRemove()
                                            Toast.makeText(context, "Removed from Dropped", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onUpdateStatus("DROPPED")
                                            Toast.makeText(context, "Marked as Dropped", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    InfoSection(isOled) {
                        InfoGrid(listOfNotNull(
                            if (displayData.episodes > 0) "Episodes" to "${displayData.episodes}" else null,
                            displayData.duration?.let { "Duration" to "$it min per ep" },
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

                val filteredRelations = displayData.relations.filter { relation ->
                    relation.format != "MANGA" && relation.format != "NOVEL" && relation.format != "ONE_SHOT"
                }

                if (filteredRelations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Relations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(filteredRelations) { relation ->
                                    Column(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .border(
                                                width = 1.dp,
                                                color = if (isOled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { 
                                                onRelationClick(relation) 
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                        ) {
                                            Card(
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                AsyncImage(
                                                    model = relation.cover,
                                                    contentDescription = relation.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            // Episodes badge - top left
                                            relation.episodes?.let { eps ->
                                                Surface(
                                                    modifier = Modifier
                                                        .padding(4.dp)
                                                        .align(Alignment.TopStart),
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.Black.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        "${eps} eps",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            // Rating badge - top right
                                            relation.averageScore?.let { score ->
                                                Surface(
                                                    modifier = Modifier
                                                        .padding(4.dp)
                                                        .align(Alignment.TopEnd),
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.Black.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        "★ ${score / 10}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFFFD700),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            relation.title,
                                            style = MaterialTheme.typography.labelSmall,
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
                                                color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
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

private fun formatNumber(num: Int): String = when {
    num >= 1_000_000 -> String.format(Locale.US, "%.1fM", num / 1_000_000.0)
    num >= 1_000 -> String.format(Locale.US, "%.1fK", num / 1_000.0)
    else -> num.toString()
}
