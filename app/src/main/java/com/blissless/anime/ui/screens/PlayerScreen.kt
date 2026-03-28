package com.blissless.anime.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.net.ConnectivityManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.ConnectivityManager
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.blissless.anime.api.AnimeSkipService
import com.blissless.anime.data.models.EpisodeTimestamps
import com.blissless.anime.data.models.Timestamp
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.QualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    latestAiredEpisode: Int? = null,
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
    qualityOptions: List<QualityOption> = emptyList(),
    currentQuality: String = "Auto",
    isLatestEpisode: Boolean = false,
    disableMaterialColors: Boolean = false,
    showBufferIndicator: Boolean = true,
    bufferAheadSeconds: Int = 30,
    animekaiIntroStart: Int? = null,
    animekaiIntroEnd: Int? = null,
    animekaiOutroStart: Int? = null,
    animekaiOutroEnd: Int? = null,
    onSavePosition: ((Long) -> Unit)? = null,
    onPositionSaved: ((Long) -> Unit)? = null,
    onProgressUpdate: (percentage: Int) -> Unit = {},
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onServerChange: ((serverName: String, category: String) -> Unit)? = null,
    onQualityChange: ((qualityUrl: String, qualityName: String) -> Unit)? = null,
    onPlaybackError: (() -> Unit)? = null,
    onPrefetchAdjacent: (() -> Unit)? = null,
    onInvalidateStreamCache: (() -> Unit)? = null,
    onGetCacheDataSourceFactory: (String) -> CacheDataSource.Factory? = { null }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasTriggeredProgressUpdate by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var isChangingServer by remember { mutableStateOf(false) }
    var hasPlaybackStarted by remember { mutableStateOf(false) }

    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "16:9"
    )

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var maxBufferedPosition by remember { mutableLongStateOf(0L) }
    var showServerMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }

    var selectedQuality by remember { mutableStateOf(currentQuality) }

    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }

    var showSkipIndicator by remember { mutableStateOf(false) }
    var skipIndicatorText by remember { mutableStateOf("") }
    var skipIsForward by remember { mutableStateOf(true) }
    
    // Helper to check if device has internet connection
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    val animeSkipService = remember { AnimeSkipService(context) }

    var episodeTimestamps by remember(videoUrl) { mutableStateOf<EpisodeTimestamps?>(null) }
    var isFetchingTimestamps by remember(videoUrl) { mutableStateOf(false) }
    var hasSkippedIntro by remember(videoUrl) { mutableStateOf(false) }
    var hasSkippedOutro by remember(videoUrl) { mutableStateOf(false) }
    var showSkipOpeningButton by remember(videoUrl) { mutableStateOf(false) }
    var showSkipEndingButton by remember(videoUrl) { mutableStateOf(false) }
    var hasFetchedTimestamps by remember(videoUrl) { mutableStateOf(false) }
    var actualEpisodeLength by remember(videoUrl) { mutableStateOf<Int?>(null) }

    var pendingQualityChange by remember { mutableStateOf<String?>(null) }
    var savedPositionForQuality by remember { mutableLongStateOf(0L) }

    val scope = rememberCoroutineScope()

    var hasShownFallbackToast by remember(videoUrl) { mutableStateOf(false) }
    var hasRestoredPosition by remember(videoUrl) { mutableStateOf(false) }
    var hasTriggeredPrefetch by remember(videoUrl) { mutableStateOf(false) }

    // PRIMARY: Use Animekai timestamps if available, create initial timestamps immediately
    val animekaiTimestamps = remember(animekaiIntroStart, animekaiIntroEnd, animekaiOutroStart, animekaiOutroEnd, currentEpisode) {
        if (animekaiIntroStart != null || animekaiOutroStart != null) {
            EpisodeTimestamps(
                episodeNumber = currentEpisode,
                introStart = animekaiIntroStart?.toLong(),
                introEnd = animekaiIntroEnd?.toLong(),
                creditsStart = animekaiOutroStart?.toLong(),
                creditsEnd = animekaiOutroEnd?.toLong(),
                recapStart = null,
                recapEnd = null,
                allTimestamps = buildList {
                    if (animekaiIntroStart != null) add(Timestamp(animekaiIntroStart.toDouble(), "op", "op"))
                    if (animekaiOutroStart != null) add(Timestamp(animekaiOutroStart.toDouble(), "ed", "ed"))
                }
            )
        } else null
    }

    val effectiveTimestamps by remember(episodeTimestamps, animekaiTimestamps) {
        derivedStateOf {
            episodeTimestamps ?: animekaiTimestamps ?: EpisodeTimestamps(
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

    // Update selected quality when currentQuality prop changes
    LaunchedEffect(currentQuality) {
        selectedQuality = currentQuality
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

    val exoPlayer = remember(context, bufferAheadSeconds, referer) {
        val bufferAheadMs = bufferAheadSeconds * 1000
        // Use a higher max buffer to ensure more content is cached for offline viewing
        val maxBufferMs = maxOf(bufferAheadMs + 60000, 180000) // At least 3 minutes, or buffer ahead + 1 minute
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferAheadMs, // minBufferMs - buffer this much before playing
                maxBufferMs, // maxBufferMs - buffer more for caching
                1500, // bufferForPlaybackMs - start playing after this much
                3000  // bufferForPlaybackAfterRebufferMs - resume after rebuffer
            )
            .build()
        
        // Get data source factory - use cache if available
        val cacheDataSourceFactory = onGetCacheDataSourceFactory(referer)
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(mapOf("Referer" to referer))
        
        val dataSourceFactory = if (cacheDataSourceFactory != null) {
            cacheDataSourceFactory
        } else {
            upstreamFactory
        }
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) {
                            hasPlaybackStarted = true
                        }
                    }

                    override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                        // When playback is suppressed, player is likely waiting for buffer
                        isBuffering = isPlaying && reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // Check if offline - if so, keep buffering state instead of showing error
                        if (!isNetworkAvailable()) {
                            isOffline = true
                            isBuffering = true
                            hasError = false
                            playbackError = null
                            // Don't show controls, keep playing state
                        } else {
                            hasError = true
                            playbackError = error.message ?: "Unknown playback error"
                            showControls = true
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) {
                            hasError = false
                            playbackError = null
                            isChangingServer = false
                            isBuffering = false
                            if (pendingQualityChange != null && savedPositionForQuality > 0) {
                                seekTo(savedPositionForQuality)
                                pendingQualityChange = null
                                savedPositionForQuality = 0L
                            }
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            if (autoPlayNextEpisode && onNextEpisode != null) {
                                if (isLatestEpisode) {
                                    Toast.makeText(context, "Latest episode watched", Toast.LENGTH_SHORT).show()
                                } else {
                                    onNextEpisode.invoke()
                                }
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
        hasSkippedIntro = false
        hasSkippedOutro = false
        hasTriggeredPrefetch = false
        isChangingServer = false
        hasPlaybackStarted = false
        bufferedPosition = 0L
        maxBufferedPosition = 0L
        isOffline = false

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
        // Only auto-play if no saved position, otherwise wait for seek
        exoPlayer.playWhenReady = savedPosition == 0L

        hasTriggeredProgressUpdate = false
        currentPosition = 0L
        sliderValue = 0f
    }

    LaunchedEffect(exoPlayer.playbackState, savedPosition, hasRestoredPosition) {
        if (exoPlayer.playbackState == Player.STATE_READY && savedPosition > 0 && !hasRestoredPosition) {
            exoPlayer.seekTo(savedPosition)
            hasRestoredPosition = true
            // Start playback after seeking to saved position
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(isPlaying, hasTriggeredPrefetch, hasError, isLatestEpisode) {
        if (isPlaying && !hasTriggeredPrefetch && !hasError && onPrefetchAdjacent != null && !isLatestEpisode) {
            delay(5000)
            if (!hasTriggeredPrefetch && isPlaying && !hasError && !isLatestEpisode) {
                hasTriggeredPrefetch = true
                onPrefetchAdjacent.invoke()
            }
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
        // Don't autoplay when user is manually skipping
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
                // Get buffered position from ExoPlayer
                bufferedPosition = exoPlayer.bufferedPosition
                // Track max buffer position to preserve buffer when scrubbing back
                if (bufferedPosition > maxBufferedPosition) {
                    maxBufferedPosition = bufferedPosition
                }
                if (duration > 0) {
                    sliderValue = currentPosition.toFloat()
                    if (actualEpisodeLength == null && duration > 60000 && exoPlayer.playbackState == Player.STATE_READY) {
                        actualEpisodeLength = (duration / 1000).toInt()
                    }
                }
            }
        }
    }

    // FALLBACK: Only fetch from AnimeSkip/AnimeThemes if Animekai timestamps are NOT available
    LaunchedEffect(
        actualEpisodeLength,
        videoUrl,
        malId,
        animeYear,
        animeName,
        animekaiTimestamps?.hasTimestamps()
    ) {
        val epLength = actualEpisodeLength
        if (epLength == null || hasFetchedTimestamps) return@LaunchedEffect

        if (animekaiTimestamps?.hasTimestamps() == true) {
            hasFetchedTimestamps = true
            return@LaunchedEffect
        }

        isFetchingTimestamps = true

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

                if (timestamps != null && timestamps.hasTimestamps()) {
                    episodeTimestamps = timestamps
                }
            } catch (e: Exception) {
            }
        }

        isFetchingTimestamps = false
        hasFetchedTimestamps = true
    }

    LaunchedEffect(currentPosition, effectiveTimestamps, hasError) {
        if (hasError) return@LaunchedEffect

        val ts = effectiveTimestamps
        val posSeconds = currentPosition / 1000

        if (ts.introStart != null && ts.introEnd != null) {
            val isInIntro = posSeconds >= ts.introStart && posSeconds < ts.introEnd
            if (isInIntro) {
                if (autoSkipOpening && !hasSkippedIntro) {
                    exoPlayer.seekTo(ts.introEnd * 1000L)
                    exoPlayer.play()
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
                    if (isLatestEpisode) {
                        Toast.makeText(context, "Latest episode watched", Toast.LENGTH_SHORT).show()
                    } else {
                        onNextEpisode.invoke()
                    }
                    hasSkippedOutro = true
                }
                showSkipEndingButton = true // Always show skip ending button
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

    LaunchedEffect(showControls, isPlaying, isDragging, hasError, showServerMenu, showQualityMenu) {
        if (showControls && isPlaying && !isDragging && !hasError && !showServerMenu && !showQualityMenu) {
            delay(3000)
            if (!isDragging && !hasError && isPlaying && !showServerMenu && !showQualityMenu) {
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
    val creditsAtEnd = if (duration > 0 && effectiveTimestamps.creditsEnd != null) {
        val creditsEndSeconds = effectiveTimestamps.creditsEnd!! * 1000
        val durationDiff = duration - creditsEndSeconds
        durationDiff < 30000 // Credits end within 30 seconds of the end
    } else false

    fun handleServerChange(serverName: String, category: String) {
        hasError = false
        playbackError = null
        isChangingServer = true
        onSavePosition?.invoke(exoPlayer.currentPosition)
        onPositionSaved?.invoke(exoPlayer.currentPosition)
        exoPlayer.stop()
        onServerChange?.invoke(serverName, category)
    }

    fun handleQualityChange(qualityUrl: String, qualityName: String) {
        savedPositionForQuality = exoPlayer.currentPosition
        pendingQualityChange = qualityName
        selectedQuality = qualityName
        onQualityChange?.invoke(qualityUrl, qualityName)
    }

    fun handlePlaybackError() {
        onInvalidateStreamCache?.invoke()
        onPlaybackError?.invoke()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = resizeModes[resizeModeIndex].first
                    useController = false
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    controllerShowTimeoutMs = 3000
                    controllerAutoShow = false

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
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (hasPlaybackStarted) 1f else 0f),
            update = { view -> 
                view.resizeMode = resizeModes[resizeModeIndex].first
            }
        )

        // 2. Active Gesture Zones (Middle Layer)
        // These handle seeking and toggling controls. Defined first so they are "under" the padding zones.

        // Left Seek Zone (30% width, offset by padding)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .padding(start = 40.dp)
                .align(Alignment.CenterStart)
                .pointerInput(backwardSkipSeconds) {
                    detectTapGestures(
                        onTap = { if (!hasError) showControls = !showControls },
                        onDoubleTap = { if (!hasError) seekBy(-(backwardSkipSeconds * 1000L), false) }
                    )
                }
        )

        // Center Toggle Zone (40% width)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.Center)
                .pointerInput(Unit) { detectTapGestures(onTap = { if (!hasError) showControls = !showControls }) }
        )

        // Right Seek Zone (30% width, offset by padding)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .padding(end = 40.dp)
                .align(Alignment.CenterEnd)
                .pointerInput(forwardSkipSeconds) {
                    detectTapGestures(
                        onTap = { if (!hasError) showControls = !showControls },
                        onDoubleTap = { if (!hasError) seekBy(forwardSkipSeconds * 1000L, true) }
                    )
                }
        )

        // 3. Padding Zones (Top Layer over Active Zones, Under UI Controls)
        // These consume touches to prevent UI toggling in safe areas.
        // Defined after active zones so they take precedence in overlap areas.

        // Left Padding
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )

        // Right Padding
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )

        // Top Padding (Matches side padding logic)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.TopCenter)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )

        // Bottom Padding (Matches side padding logic)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )

        // 4. UI Overlays (Top Layer)
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
                        exoPlayer.seekTo(ts.introEnd * 1000L)
                        exoPlayer.play()
                        hasSkippedIntro = true
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
                label = if (isLatestEpisode || !creditsAtEnd) "Skip\nEnding" else "Next\nEpisode",
                backgroundColor = Color.Black.copy(alpha = 0.6f),
                iconTint = Color.White,
                onClick = {
                    if (isLatestEpisode || !creditsAtEnd) {
                        if (exoPlayer.duration > 0) {
                            exoPlayer.seekTo(exoPlayer.duration)
                        }
                    } else {
                        onNextEpisode?.invoke()
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showControls || hasError || showSkipIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                if (isChangingServer) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 1.5.dp,
                                        color = if (disableMaterialColors) Color.White else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(IntrinsicSize.Max)) {
                            // Server selector
                            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                                if (onServerChange != null && (subServers.isNotEmpty() || dubServers.isNotEmpty())) {
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
                                                    onClick = {
                                                        showServerMenu = false
                                                        handleServerChange(server.name, "sub")
                                                    }
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
                                                    onClick = {
                                                        showServerMenu = false
                                                        handleServerChange(server.name, "dub")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Quality selector
                            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                                if (qualityOptions.isNotEmpty() && onQualityChange != null) {
                                    IconButton(
                                        onClick = { showQualityMenu = true },
                                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                                    ) {
                                        Icon(Icons.Default.Hd, "Quality", tint = Color.White)
                                    }

                                    DropdownMenu(
                                        expanded = showQualityMenu,
                                        onDismissRequest = { showQualityMenu = false },
                                        modifier = Modifier.background(Color(0xFF1A1A1A)).width(140.dp)
                                    ) {
                                        Text(
                                            "QUALITY",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        QualityMenuItem(
                                            qualityName = "Auto",
                                            isSelected = selectedQuality == "Auto",
                                            onClick = {
                                                if (selectedQuality != "Auto") {
                                                    handleQualityChange(videoUrl, "Auto")
                                                }
                                                showQualityMenu = false
                                            }
                                        )
                                        qualityOptions.forEach { quality ->
                                            QualityMenuItem(
                                                qualityName = quality.quality,
                                                isSelected = selectedQuality == quality.quality,
                                                onClick = {
                                                    if (selectedQuality != quality.quality) {
                                                        handleQualityChange(quality.url, quality.quality)
                                                    }
                                                    showQualityMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Resize button
                            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                                ) {
                                    Icon(Icons.Default.AspectRatio, "Change aspect ratio", tint = Color.White)
                                }
                            }
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
                            modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).alpha(if (onPreviousEpisode != null && !isLoadingStream && !isChangingServer) 1f else 0.3f),
                            enabled = onPreviousEpisode != null && !isLoadingStream && !isChangingServer
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Previous Episode", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = {
                                if (hasError) {
                                    handlePlaybackError()
                                    exoPlayer.prepare()
                                    exoPlayer.playWhenReady = true
                                } else {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            },
                            modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            if (isBuffering || isOffline) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(42.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (hasError) Icons.Default.Refresh else if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (hasError) "Retry" else if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onNextEpisode?.invoke() },
                            modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).alpha(if (onNextEpisode != null && !isLatestEpisode && !isLoadingStream && !isChangingServer) 1f else 0.3f),
                            enabled = onNextEpisode != null && !isLatestEpisode && !isLoadingStream && !isChangingServer
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

                if (isLoadingStream || isChangingServer) {
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

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        handlePlaybackError()
                                        hasError = false
                                        playbackError = null
                                        exoPlayer.prepare()
                                        exoPlayer.playWhenReady = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Retry")
                                }

                                if (onServerChange != null) {
                                    val servers = if (currentCategory == "sub") subServers else dubServers
                                    if (servers.size > 1) {
                                        Button(
                                            onClick = {
                                                val currentIndex = servers.indexOfFirst { it.name == currentServerName }
                                                val nextIndex = (currentIndex + 1) % servers.size
                                                handleServerChange(servers[nextIndex].name, currentCategory)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Text("Try Next Server")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom gradient
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..(if (duration > 0) duration.toFloat() else 1000f),
                        onValueChange = { newValue ->
                            isDragging = true
                            wasPlayingBeforeScrub = isPlaying
                            sliderValue = newValue
                            currentPosition = newValue.toLong()
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            exoPlayer.seekTo(sliderValue.toLong())
                            if (wasPlayingBeforeScrub) {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                val sliderWidth = size.width
                                val trackHeight = 8.dp.toPx()
                                val trackTop = (size.height - trackHeight) / 2f
                                val cornerRadius = 4.dp.toPx()
                                val thumbRadius = 8.dp.toPx()

                                if (duration > 0) {
                                    val progressRatio = currentPosition.toFloat() / duration
                                    val bufferedRatio = maxBufferedPosition.toFloat() / duration
                                    
                                    // Draw inactive track (entire track background)
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.3f),
                                        topLeft = Offset(0f, trackTop),
                                        size = Size(sliderWidth, trackHeight),
                                        cornerRadius = CornerRadius(cornerRadius)
                                    )
                                    
                                    // Draw buffer indicator (if enabled and has buffered content) - darker white
                                    if (showBufferIndicator && maxBufferedPosition > currentPosition) {
                                        val bufferStartX = progressRatio * sliderWidth
                                        val bufferEndX = bufferedRatio * sliderWidth
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.5f),
                                            topLeft = Offset(bufferStartX, trackTop),
                                            size = Size(bufferEndX - bufferStartX, trackHeight),
                                            cornerRadius = CornerRadius(2.dp.toPx())
                                        )
                                    }
                                    
                                    // Draw active track (played portion) first
                                    val progressX = progressRatio * sliderWidth
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = Offset(0f, trackTop),
                                        size = Size(progressX.coerceAtLeast(thumbRadius), trackHeight),
                                        cornerRadius = CornerRadius(cornerRadius)
                                    )
                                    
                                    // Draw intro/credits overlay ON TOP using BlendMode.Multiply to show through white
                                    if (introStartRatio != null && introEndRatio != null) {
                                        val introStartX = introStartRatio * sliderWidth
                                        val introEndX = introEndRatio * sliderWidth
                                        val introWidth = introEndX - introStartX
                                        if (introWidth > 0) {
                                            val leftRadius = if (introStartX < 10f) cornerRadius else 2.dp.toPx()
                                            val rightRadius = if (introEndX > sliderWidth - 10f) cornerRadius else 2.dp.toPx()
                                            drawRoundRect(
                                                color = Color(0xFFFF9800), // Full opacity, BlendMode.Multiply will show through
                                                topLeft = Offset(introStartX.coerceAtLeast(0f), trackTop),
                                                size = Size(
                                                    introWidth.coerceAtMost(sliderWidth - introStartX.coerceAtLeast(0f)),
                                                    trackHeight
                                                ),
                                                cornerRadius = CornerRadius(leftRadius, rightRadius),
                                                blendMode = BlendMode.Multiply
                                            )
                                        }
                                    }

                                    if (creditsStartRatio != null) {
                                        val creditsStartX = creditsStartRatio * sliderWidth
                                        val creditsWidth = sliderWidth - creditsStartX
                                        if (creditsStartX < sliderWidth && creditsWidth > 0) {
                                            drawRoundRect(
                                                color = Color(0xFFFF9800),
                                                topLeft = Offset(creditsStartX, trackTop),
                                                size = Size(creditsWidth.coerceAtLeast(0f), trackHeight),
                                                cornerRadius = CornerRadius(2.dp.toPx(), cornerRadius),
                                                blendMode = BlendMode.Multiply
                                            )
                                        }
                                    }
                                }
                            },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
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

@Composable
private fun QualityMenuItem(qualityName: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(qualityName, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White)
            }
        },
        onClick = onClick
    )
}