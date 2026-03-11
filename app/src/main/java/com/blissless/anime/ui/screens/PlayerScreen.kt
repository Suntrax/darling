package com.blissless.anime.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.blissless.anime.api.EpisodeStreams
import com.blissless.anime.api.AnimeSkipService
import com.blissless.anime.api.EpisodeTimestamps
import java.util.Locale
import kotlin.math.abs

private const val PLAYER_TAG = "PlayerScreen"

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    referer: String,
    subtitleUrl: String? = null,
    currentEpisode: Int = 1,
    totalEpisodes: Int = 0,
    animeName: String = "",
    animeId: Int = 0,
    malId: Int = 0,
    animeYear: Int? = null,
    episodeLength: Int = 1440,
    isLoadingStream: Boolean = false,
    episodeInfo: EpisodeStreams? = null,
    currentServerName: String = "",
    currentCategory: String = "sub",
    isFallbackStream: Boolean = false,
    requestedCategory: String = "sub",
    actualCategory: String = "sub",
    forwardSkipSeconds: Int = 10,
    backwardSkipSeconds: Int = 10,
    autoSkipOpening: Boolean = false,
    autoSkipEnding: Boolean = false,
    autoPlayNextEpisode: Boolean = false,
    savedPosition: Long = 0L,
    onSavePosition: ((Long) -> Unit)? = null,
    onProgressUpdate: (percentage: Int) -> Unit = {},
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onServerChange: ((serverName: String, category: String) -> Unit)? = null,
    onPlaybackError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasTriggeredProgressUpdate by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var hasError by remember { mutableStateOf(false) }

    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "16:9"
    )

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showServerMenu by remember { mutableStateOf(false) }

    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    var showSkipIndicator by remember { mutableStateOf(false) }
    var skipIndicatorText by remember { mutableStateOf("") }
    var skipIsForward by remember { mutableStateOf(true) }

    val animeSkipService = remember { AnimeSkipService(context) }

    var episodeTimestamps by remember(videoUrl) { mutableStateOf<EpisodeTimestamps?>(null) }
    var isFetchingTimestamps by remember(videoUrl) { mutableStateOf(false) }
    var hasSkippedIntro by remember(videoUrl) { mutableStateOf(false) }
    var hasSkippedOutro by remember(videoUrl) { mutableStateOf(false) }
    var showSkipOpeningButton by remember(videoUrl) { mutableStateOf(false) }
    var showSkipEndingButton by remember(videoUrl) { mutableStateOf(false) }
    var hasFetchedTimestamps by remember(videoUrl) { mutableStateOf(false) }
    var actualEpisodeLength by remember(videoUrl) { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()

    var hasShownFallbackToast by remember(videoUrl) { mutableStateOf(false) }
    var hasRestoredPosition by remember(videoUrl) { mutableStateOf(false) }

    val effectiveTimestamps by remember(episodeTimestamps) {
        derivedStateOf {
            episodeTimestamps ?: EpisodeTimestamps(
                episodeNumber = currentEpisode,
                introStart = null,
                introEnd = null,
                creditsStart = null,
                creditsEnd = null,
                recapStart = null,
                recapEnd = null,
                allTimestamps = emptyList()
            )
        }
    }

    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    DisposableEffect(Unit) {
        onDispose {
            onSavePosition?.invoke(currentPosition)
            activity?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(mapOf("Referer" to referer))
                )
            )
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        hasError = true
                        playbackError = error.message ?: "Unknown playback error"
                        onPlaybackError?.invoke()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            hasError = false
                            playbackError = null
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            if (autoPlayNextEpisode && onNextEpisode != null) {
                                onNextEpisode.invoke()
                            }
                        }
                    }
                })
            }
    }

    LaunchedEffect(videoUrl) {
        hasError = false
        playbackError = null
        hasRestoredPosition = false

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)

        if (subtitleUrl != null) {
            val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitle))
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        hasTriggeredProgressUpdate = false
        currentPosition = 0L
        sliderValue = 0f
    }

    LaunchedEffect(exoPlayer.playbackState, savedPosition, hasRestoredPosition) {
        if (exoPlayer.playbackState == Player.STATE_READY && savedPosition > 0 && !hasRestoredPosition) {
            exoPlayer.seekTo(savedPosition)
            hasRestoredPosition = true
        }
    }

    LaunchedEffect(videoUrl, isFallbackStream) {
        if (isFallbackStream && !hasShownFallbackToast && videoUrl.isNotEmpty()) {
            hasShownFallbackToast = true
            val message = if (requestedCategory == "dub") "Dub not available, playing sub" else "Sub not available, playing dub"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun seekBy(milliseconds: Long, isForward: Boolean) {
        val newPosition = (exoPlayer.currentPosition + milliseconds).coerceIn(0, exoPlayer.duration)
        exoPlayer.seekTo(newPosition)
        val seconds = abs(milliseconds / 1000)
        skipIndicatorText = if (milliseconds > 0) "+${seconds}s" else "-${seconds}s"
        skipIsForward = isForward
        showSkipIndicator = true
        scope.launch {
            delay(500)
            showSkipIndicator = false
        }
    }

    LaunchedEffect(exoPlayer, videoUrl) {
        while (true) {
            delay(500)
            if (!isDragging) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration
                if (duration > 0) {
                    sliderValue = currentPosition.toFloat()
                    if (actualEpisodeLength == null && duration > 60000 && exoPlayer.playbackState == Player.STATE_READY) {
                        actualEpisodeLength = (duration / 1000).toInt()
                    }
                }
            }
        }
    }

    LaunchedEffect(actualEpisodeLength, videoUrl, malId, animeYear, animeName) {
        val epLength = actualEpisodeLength
        if (epLength == null || hasFetchedTimestamps) return@LaunchedEffect

        isFetchingTimestamps = true
        Log.d(PLAYER_TAG, "Fetching timestamps for: $animeName")

        withContext(Dispatchers.IO) {
            try {
                val timestamps = if (malId > 0) {
                    animeSkipService.getSkipTimestampsWithFallback(
                        malId = malId,
                        episodeNumber = currentEpisode,
                        episodeLength = epLength,
                        animeName = animeName,
                        animeYear = animeYear,
                        animeId = animeId
                    )
                } else if (animeName.isNotEmpty()) {
                    animeSkipService.getSkipTimestampsByName(
                        animeName = animeName,
                        episodeNumber = currentEpisode,
                        episodeLength = epLength,
                        year = animeYear
                    )
                } else null

                if (timestamps != null) {
                    episodeTimestamps = timestamps
                    Log.d(PLAYER_TAG, "Got timestamps: OP=${timestamps.introStart}-${timestamps.introEnd}")
                }
            } catch (e: Exception) {
                Log.e(PLAYER_TAG, "Error fetching timestamps", e)
            }
        }

        isFetchingTimestamps = false
        hasFetchedTimestamps = true
    }

    LaunchedEffect(currentPosition, effectiveTimestamps) {
        val ts = effectiveTimestamps
        val posSeconds = currentPosition / 1000

        if (ts.introStart != null && ts.introEnd != null) {
            val isInIntro = posSeconds >= ts.introStart && posSeconds < ts.introEnd
            if (isInIntro) {
                if (autoSkipOpening && !hasSkippedIntro) {
                    exoPlayer.seekTo(ts.introEnd * 1000)
                    hasSkippedIntro = true
                }
                showSkipOpeningButton = !autoSkipOpening
            } else {
                showSkipOpeningButton = false
            }
        }

        if (ts.creditsStart != null && onNextEpisode != null) {
            val isInCredits = posSeconds >= ts.creditsStart
            if (isInCredits) {
                if (autoSkipEnding && !hasSkippedOutro) {
                    onNextEpisode.invoke()
                    hasSkippedOutro = true
                }
                showSkipEndingButton = !autoSkipEnding
            } else {
                showSkipEndingButton = false
            }
        }
    }

    LaunchedEffect(exoPlayer, hasTriggeredProgressUpdate) {
        while (!hasTriggeredProgressUpdate) {
            delay(1000)
            if (exoPlayer.playbackState == Player.STATE_READY && exoPlayer.duration > 0) {
                val percentage = ((exoPlayer.currentPosition.toFloat() / exoPlayer.duration) * 100).toInt()
                onProgressUpdate(percentage)
            }
        }
    }

    LaunchedEffect(showControls, isPlaying, isDragging) {
        if (showControls && isPlaying && !isDragging) {
            delay(3000)
            if (!isDragging) {
                showControls = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    val subServers = episodeInfo?.subServers ?: emptyList()
    val dubServers = episodeInfo?.dubServers ?: emptyList()

    val introStartRatio = if (duration > 0 && effectiveTimestamps.introStart != null) {
        (effectiveTimestamps.introStart!! * 1000).toFloat() / duration.toFloat()
    } else null
    val introEndRatio = if (duration > 0 && effectiveTimestamps.introEnd != null) {
        (effectiveTimestamps.introEnd!! * 1000).toFloat() / duration.toFloat()
    } else null
    val creditsStartRatio = if (duration > 0 && effectiveTimestamps.creditsStart != null) {
        (effectiveTimestamps.creditsStart!! * 1000).toFloat() / duration.toFloat()
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .padding(start = 40.dp)
                .align(Alignment.CenterStart)
                .pointerInput(backwardSkipSeconds) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { seekBy(-(backwardSkipSeconds * 1000L), false) }
                    )
                }
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.Center)
                .pointerInput(Unit) { detectTapGestures(onTap = { showControls = !showControls }) }
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .padding(end = 40.dp)
                .align(Alignment.CenterEnd)
                .pointerInput(forwardSkipSeconds) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { seekBy(forwardSkipSeconds * 1000L, true) }
                    )
                }
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )

        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = resizeModes[resizeModeIndex].first

                    val style = CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null
                    )
                    subtitleView?.apply {
                        setStyle(style)
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
                    }
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.resizeMode = resizeModes[resizeModeIndex].first }
        )

        AnimatedVisibility(
            visible = showSkipOpeningButton,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
        ) {
            SkipIconButton(
                icon = Icons.Default.FastForward,
                label = "Skip\nOpening",
                backgroundColor = Color.Black.copy(alpha = 0.6f),
                iconTint = Color.White,
                onClick = {
                    val ts = effectiveTimestamps
                    if (ts.introEnd != null) {
                        exoPlayer.seekTo(ts.introEnd * 1000)
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showSkipEndingButton,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
        ) {
            SkipIconButton(
                icon = Icons.Default.SkipNext,
                label = "Next\nEpisode",
                backgroundColor = Color.Black.copy(alpha = 0.6f),
                iconTint = Color.White,
                onClick = { onNextEpisode?.invoke() }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        if (animeName.isNotEmpty()) {
                            Text(
                                text = animeName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Episode $currentEpisode${if (totalEpisodes > 0) " / $totalEpisodes" else ""}",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isFallbackStream)
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = actualCategory.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFallbackStream)
                                        MaterialTheme.colorScheme.secondary
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (isFetchingTimestamps) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onServerChange != null && (subServers.isNotEmpty() || dubServers.isNotEmpty())) {
                            Box {
                                IconButton(
                                    onClick = { showServerMenu = true },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                                ) {
                                    Icon(Icons.Default.Settings, "Server Selection", tint = Color.White)
                                }

                                DropdownMenu(
                                    expanded = showServerMenu,
                                    onDismissRequest = { showServerMenu = false },
                                    modifier = Modifier.background(Color(0xFF1A1A1A)).width(180.dp)
                                ) {
                                    if (subServers.isNotEmpty()) {
                                        Text("SUB", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                        subServers.forEach { server ->
                                            ServerMenuItem(
                                                serverName = server.name,
                                                isSelected = server.name == currentServerName && currentCategory == "sub",
                                                onClick = { onServerChange(server.name, "sub"); showServerMenu = false }
                                            )
                                        }
                                    }
                                    if (dubServers.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("DUB", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                        dubServers.forEach { server ->
                                            ServerMenuItem(
                                                serverName = server.name,
                                                isSelected = server.name == currentServerName && currentCategory == "dub",
                                                onClick = { onServerChange(server.name, "dub"); showServerMenu = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(Icons.Default.AspectRatio, "Change aspect ratio", tint = Color.White)
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.Center)) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onPreviousEpisode?.invoke() },
                            modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).alpha(if (onPreviousEpisode != null && !isLoadingStream) 1f else 0.3f),
                            enabled = onPreviousEpisode != null && !isLoadingStream
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Previous Episode", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        IconButton(
                            onClick = { onNextEpisode?.invoke() },
                            modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).alpha(if (onNextEpisode != null && !isLoadingStream) 1f else 0.3f),
                            enabled = onNextEpisode != null && !isLoadingStream
                        ) {
                            Icon(Icons.Default.SkipNext, "Next Episode", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    AnimatedVisibility(
                        visible = showSkipIndicator && !skipIsForward,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = (-120).dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FastRewind, "Rewind", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(skipIndicatorText, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(
                        visible = showSkipIndicator && skipIsForward,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = 120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FastForward, "Forward", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(skipIndicatorText, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isLoadingStream) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).offset(y = 64.dp), color = Color.White)
                }

                if (hasError && playbackError != null) {
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Stream Error", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(playbackError ?: "Unknown error", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            if (onServerChange != null && subServers.size > 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val currentIndex = subServers.indexOfFirst { it.name == currentServerName }
                                        val nextIndex = (currentIndex + 1) % subServers.size
                                        onServerChange(subServers[nextIndex].name, currentCategory)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) { Text("Try Next Server") }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..(if (duration > 0) duration.toFloat() else 1000f),
                        onValueChange = { newValue ->
                            isDragging = true
                            sliderValue = newValue
                            currentPosition = newValue.toLong()
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            exoPlayer.seekTo(sliderValue.toLong())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                val sliderWidth = size.width
                                val trackHeight = 12.dp.toPx()
                                val trackTop = (size.height - trackHeight) / 2f
                                val cornerRadius = 6.dp.toPx()

                                if (introStartRatio != null && introEndRatio != null) {
                                    val startX = introStartRatio * sliderWidth
                                    val endX = introEndRatio * sliderWidth
                                    drawRoundRect(
                                        color = Color(0xFF505050),
                                        topLeft = Offset(startX, trackTop),
                                        size = Size(endX - startX, trackHeight),
                                        cornerRadius = CornerRadius(if (introStartRatio < 0.05f) cornerRadius else 2.dp.toPx())
                                    )
                                }

                                if (creditsStartRatio != null) {
                                    val startX = creditsStartRatio * sliderWidth
                                    drawRoundRect(
                                        color = Color(0xFF505050),
                                        topLeft = Offset(startX, trackTop),
                                        size = Size(sliderWidth - startX, trackHeight),
                                        cornerRadius = CornerRadius(2.dp.toPx(), cornerRadius)
                                    )
                                }
                            },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text(if (duration > 0) formatTime(duration) else "--:--", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp).background(backgroundColor, shape = MaterialTheme.shapes.small)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ServerMenuItem(serverName: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(serverName, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White)
            }
        },
        onClick = onClick
    )
}
