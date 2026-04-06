package com.blissless.anime.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.MainViewModel
import com.blissless.anime.data.LoginProvider
import kotlin.math.round

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    isLoggedIn: Boolean,
    showStatusColors: Boolean = true,
    hideNavbarText: Boolean = false,
    autoSkipOpening: Boolean = false,
    autoSkipEnding: Boolean = false,
    autoPlayNextEpisode: Boolean = true,
    disableMaterialColors: Boolean = false,
    preferredCategory: String = "sub",
    preferredScraper: String = "Animekai"
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    val showAnimeCardButtons by viewModel.showAnimeCardButtons.collectAsState(initial = true)
    val trackingPercentage by viewModel.trackingPercentage.collectAsState(initial = 85)
    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)

    // Collect the settings from ViewModel
    val simplifyEpisodeMenuState by viewModel.simplifyEpisodeMenu.collectAsState(initial = false)
    val rawPreferredScraper by viewModel.preferredScraper.collectAsState(initial = "Animekai")
    // Ensure Animekai is always the selected scraper (only working option)
    val preferredScraperState = rawPreferredScraper.ifBlank { "Animekai" }
    val hideAdultContentState by viewModel.hideAdultContent.collectAsState(initial = false)
    
    // Buffer Settings
    val bufferAheadSeconds by viewModel.bufferAheadSeconds.collectAsState(initial = 30)
    val bufferSizeMb by viewModel.bufferSizeMb.collectAsState(initial = 100)
    val showBufferIndicator by viewModel.showBufferIndicator.collectAsState(initial = true)

    // Track thumbnail preview state for showing info dialog
    var showThumbnailInfoDialog by remember { mutableStateOf(false) }

    // Login provider
    val loginProvider by viewModel.loginProvider.collectAsState(initial = LoginProvider.NONE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = com.blissless.anime.R.mipmap.ic_launcher_round,
                    contentDescription = "App",
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                )
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Account Section
            SettingsSection(
                title = "Account",
                icon = Icons.Default.Person,
                isOled = isOled,
                scrollState = scrollState,
                estimatedItemCount = 3
            ) {
            if (loginProvider != LoginProvider.NONE) {
                val userName by viewModel.userName.collectAsState()
                val userAvatar by viewModel.userAvatar.collectAsState()
                val providerName = when (loginProvider) {
                    LoginProvider.ANILIST -> "AniList"
                    LoginProvider.MAL -> "MyAnimeList"
                    LoginProvider.NONE -> ""
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    if (userAvatar != null) {
                        AsyncImage(
                            model = userAvatar,
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            userName ?: "Logged In",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "via $providerName",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showLogoutConfirmation = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Log Out")
                }
            } else {
                // Login header for AniList
                Text(
                    "Login with AniList",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.loginWithAniList() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("https://anilist.co/img/icons/favicon-32x32.png")
                            .crossfade(true)
                            .build(),
                        contentDescription = "AniList",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login with AniList")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Login header for MAL
                Text(
                    "Login with MyAnimeList",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.loginWithMal() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("https://cdn.myanimelist.net/images/favicon.ico")
                            .crossfade(true)
                            .build(),
                        contentDescription = "MyAnimeList",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login with MyAnimeList")
                }
            }
        }

        // Appearance Section
        SettingsSection(
            title = "Appearance",
            icon = Icons.Default.Palette,
            isOled = isOled,
            scrollState = scrollState,
            estimatedItemCount = 5
        ) {
            SettingsToggle(
                title = "OLED Mode",
                description = "Pure black background for AMOLED screens",
                checked = isOled,
                onCheckedChange = { viewModel.setOledMode(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Monochrome Theme",
                description = "Disable Material3 colors for neutral appearance",
                checked = disableMaterialColors,
                onCheckedChange = { viewModel.setDisableMaterialColors(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Status Color Indicators",
                description = "Show colored status bars on anime cards",
                checked = showStatusColors,
                onCheckedChange = { viewModel.setShowStatusColors(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Compact Navigation",
                description = "Hide text labels in bottom navigation",
                checked = hideNavbarText,
                onCheckedChange = { viewModel.setHideNavbarText(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Simple Episode Menu",
                description = "Use compact episode grid (disable for detailed cards)",
                checked = simplifyEpisodeMenuState,
                onCheckedChange = { viewModel.setSimplifyEpisodeMenu(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Show Card Buttons",
                description = "Show bookmark and play buttons on anime cards in Explore",
                checked = showAnimeCardButtons,
                onCheckedChange = { viewModel.setShowAnimeCardButtons(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

        }

        // Content Settings Section
        SettingsSection(
            title = "Content",
            icon = Icons.Default.FilterAlt,
            isOled = isOled,
            scrollState = scrollState,
            estimatedItemCount = 1
        ) {
            SettingsToggle(
                title = "Hide Adult Content",
                description = "Exclude 18+ anime from showing up",
                checked = hideAdultContentState,
                onCheckedChange = { viewModel.setHideAdultContent(it) },
                isOled = isOled
            )
        }

        // General Section
        val startupScreenState by viewModel.startupScreen.collectAsState()
        val preventScheduleSync by viewModel.preventScheduleSync.collectAsState()
        SettingsSection(
            title = "General",
            icon = Icons.Default.Settings,
            isOled = isOled,
            scrollState = scrollState,
            estimatedItemCount = 2
        ) {
            Text(
                "Startup Screen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Choose the default screen when opening the app",
                style = MaterialTheme.typography.bodySmall,
                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    0 to "Schedule",
                    1 to "Explore",
                    2 to "Home"
                ).forEach { (index, label) ->
                    CategoryChip(
                        label = label,
                        isSelected = startupScreenState == index,
                        onClick = { viewModel.setStartupScreen(index) },
                        isOled = isOled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsToggle(
                title = "Auto Sync in Airing Schedule",
                description = "Automatically sync airing schedule when opening",
                checked = !preventScheduleSync,
                onCheckedChange = { viewModel.setPreventScheduleSync(!it) },
                isOled = isOled
            )
        }

        // Stream Settings Section
        SettingsSection(
            title = "Stream Settings",
            icon = Icons.Default.Subtitles,
            isOled = isOled,
            scrollState = scrollState,
            estimatedItemCount = 5
        ) {
            """// Preferred Scraper Selection
            Text(
                "Preferred Anime Scraper",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Choose the source for fetching anime streams",
                style = MaterialTheme.typography.bodySmall,
                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // First row of scrapers - Animekai is the only enabled option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Animekai - Default and ONLY working option (always selected)
                CategoryChip(
                    label = "Animekai",
                    isSelected = true,
                    onClick = { viewModel.setPreferredScraper("Animekai") },
                    isOled = isOled,
                    disableMaterialColors = disableMaterialColors
                )
                // Hianime - Greyed out / disabled
                CategoryChip(
                    label = "Hianime",
                    isSelected = false,
                    onClick = {
                        Toast.makeText(context, "Hianime is currently unavailable", Toast.LENGTH_SHORT).show()
                    },
                    isOled = isOled,
                    enabled = false,
                    disableMaterialColors = disableMaterialColors
                )
                // Zenime - Greyed out / disabled
                CategoryChip(
                    label = "Zenime",
                    isSelected = false,
                    onClick = {
                        Toast.makeText(context, "Zenime is currently unavailable", Toast.LENGTH_SHORT).show()
                    },
                    isOled = isOled,
                    enabled = false,
                    disableMaterialColors = disableMaterialColors
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second row of scrapers - Animepahe disabled
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Animepahe - Greyed out / disabled
                CategoryChip(
                    label = "Animepahe",
                    isSelected = false,
                    onClick = {
                        Toast.makeText(context, "Animepahe is currently unavailable", Toast.LENGTH_SHORT).show()
                    },
                    isOled = isOled,
                    enabled = false,
                    disableMaterialColors = disableMaterialColors
                )
            }
            
            

            Spacer(modifier = Modifier.height(16.dp))
            """
            // Preferred Category Selection
            // Animekai supports both SUB and DUB
            Column {
                Text(
                    "Preferred Audio Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Try servers from this category first when playing",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryChip(
                        label = "SUB",
                        isSelected = preferredCategory == "sub",
                        onClick = { viewModel.setPreferredCategory("sub") },
                        isOled = isOled,
                        enabled = true,
                        disableMaterialColors = disableMaterialColors
                    )
                    CategoryChip(
                        label = "DUB",
                        isSelected = preferredCategory == "dub",
                        onClick = { viewModel.setPreferredCategory("dub") },
                        isOled = isOled,
                        enabled = true,
                        disableMaterialColors = disableMaterialColors
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buffer Settings Card - Stand out with background
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isOled) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = if (isOled) 0.dp else 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Buffer Settings Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color(0xFF00BCD4),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Buffer Settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00BCD4)
                        )
                    }
                    Text(
                        "Control video buffering behavior",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buffer Ahead Slider
                    SettingsSlider(
                        title = "Buffer Ahead",
                        description = "Amount of video to buffer ahead of playback",
                        value = bufferAheadSeconds.toFloat(),
                        valueRange = 0f..300f,
                        valueLabel = "${bufferAheadSeconds}s",
                        onValueChange = { newValue ->
                            val snapped = round(newValue / 10f) * 10f
                            viewModel.setBufferAheadSeconds(snapped.toInt())
                        },
                        isOled = isOled,
                        minLabel = "0s",
                        maxLabel = "300s",
                        leadingIcon = Icons.Default.PlayArrow
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsSlider(
                        title = "Max Buffer Size",
                        description = "Maximum amount of data to buffer (approximate)",
                        value = bufferSizeMb.toFloat(),
                        valueRange = 50f..500f,
                        valueLabel = "${bufferSizeMb}MB",
                        onValueChange = { newValue ->
                            val snapped = round(newValue / 25f) * 25f
                            viewModel.setBufferSizeMb(snapped.toInt())
                        },
                        isOled = isOled,
                        minLabel = "50MB",
                        maxLabel = "500MB",
                        leadingIcon = Icons.Default.Settings
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsToggle(
                        title = "Show Buffer Indicator",
                        description = "Display buffered amount on the progress bar",
                        checked = showBufferIndicator,
                        onCheckedChange = { viewModel.setShowBufferIndicator(it) },
                        isOled = isOled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = if (isOled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }

        // Player Settings Section
        SettingsSection(
            title = "Player Settings",
            icon = Icons.Default.PlayArrow,
            isOled = isOled,
            scrollState = scrollState,
            estimatedItemCount = 6
        ) {
            SettingsSlider(
                title = "Episode Tracking",
                description = "Auto-update Episode progress when you've watched this percentage",
                value = trackingPercentage.toFloat(),
                valueRange = 50f..100f,
                valueLabel = "${trackingPercentage}%",
                onValueChange = { newValue ->
                    val snapped = round(newValue / 5f) * 5f
                    viewModel.setTrackingPercentage(snapped.toInt())
                },
                isOled = isOled,
                minLabel = "50%",
                maxLabel = "100%"
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSlider(
                title = "Skip Forward",
                description = "Double-tap right side to skip forward",
                value = forwardSkipSeconds.toFloat(),
                valueRange = 5f..30f,
                valueLabel = "${forwardSkipSeconds}s",
                onValueChange = { newValue ->
                    val snapped = round(newValue / 5f) * 5f
                    viewModel.setForwardSkipSeconds(snapped.toInt())
                },
                isOled = isOled,
                minLabel = "5s",
                maxLabel = "30s",
                leadingIcon = Icons.Default.FastForward
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSlider(
                title = "Skip Backward",
                description = "Double-tap left side to skip backward",
                value = backwardSkipSeconds.toFloat(),
                valueRange = 5f..30f,
                valueLabel = "${backwardSkipSeconds}s",
                onValueChange = { newValue ->
                    val snapped = round(newValue / 5f) * 5f
                    viewModel.setBackwardSkipSeconds(snapped.toInt())
                },
                isOled = isOled,
                minLabel = "5s",
                maxLabel = "30s",
                leadingIcon = Icons.Default.FastRewind
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsToggle(
                title = "Auto Skip Opening",
                description = "Automatically skip anime openings",
                checked = autoSkipOpening,
                onCheckedChange = { viewModel.setAutoSkipOpening(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Auto Skip Ending",
                description = "Automatically skip anime endings",
                checked = autoSkipEnding,
                onCheckedChange = { viewModel.setAutoSkipEnding(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Auto Play Next Episode",
                description = "Automatically play the next episode when current one ends",
                checked = autoPlayNextEpisode,
                onCheckedChange = { viewModel.setAutoPlayNextEpisode(it) },
                isOled = isOled
            )
        }

        // Thumbnail Preview Info Dialog
        if (showThumbnailInfoDialog) {
            AlertDialog(
                onDismissRequest = { showThumbnailInfoDialog = false },
                title = { Text("Seekbar Thumbnail Preview") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "This feature shows a preview of the video frame when you drag the seekbar, " +
                                    "similar to YouTube's scrubbing preview."
                        )
                        Text(
                            "How it works:",
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Frames are preloaded in the background while you watch")
                        Text("• Frames are cached for quick access during scrubbing")
                        Text("• Works best with stable internet connections")
                        Text(
                            "Note:",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFA500)
                        )
                        Text(
                            "This feature may use additional data and battery as it needs to " +
                                    "extract video frames. If you experience performance issues, " +
                                    "consider disabling this feature.",
                            color = Color(0xFFFFA500)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThumbnailInfoDialog = false }) {
                        Text("Got it")
                    }
                }
            )
        }

        // Cache Management Section
        var cacheSize by remember { mutableLongStateOf(0L) }
        var showClearCacheConfirmation by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            cacheSize = viewModel.getVideoCacheSize(context)
        }

        SettingsSection(
            title = "Cache Management",
            icon = Icons.Default.Memory,
            isOled = isOled,
            scrollState = scrollState,
            estimatedItemCount = 1
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Video Cache",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        formatFileSize(cacheSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showClearCacheConfirmation = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear")
                }
            }
        }

        if (showClearCacheConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearCacheConfirmation = false },
                title = { Text("Clear Cache") },
                text = { Text("This will clear all video cache and temporary data. Your playback positions will be preserved. Continue?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearNonEssentialCaches(context)
                            cacheSize = 0L
                            showClearCacheConfirmation = false
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    if (showLogoutConfirmation) {
        val providerName = when (loginProvider) {
            LoginProvider.ANILIST -> "AniList"
            LoginProvider.MAL -> "MyAnimeList"
            LoginProvider.NONE -> ""
        }
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout from $providerName?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutConfirmation = false
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    isOled: Boolean,
    initiallyExpanded: Boolean = false,
    scrollState: androidx.compose.foundation.ScrollState? = null,
    estimatedItemCount: Int = 1,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var shouldScroll by remember { mutableStateOf(false) }
    var sectionTop by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(shouldScroll) {
        if (shouldScroll && scrollState != null) {
            kotlinx.coroutines.delay(250)
            val scrollTarget = sectionTop - 80f
            if (scrollTarget > 0) {
                scrollState.animateScrollTo(scrollTarget.toInt())
            }
            shouldScroll = false
        }
    }
    
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "arrowRotation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                sectionTop = coordinates.positionInParent().y
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        val wasExpanded = expanded
                        expanded = !expanded
                        if (!wasExpanded) {
                            shouldScroll = true
                        }
                    }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = arrowRotation
                        }
                )
            }

            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200, delayMillis = 100)) togetherWith
                    fadeOut(animationSpec = tween(150))
                },
                label = "settingsContent"
            ) { isExpanded ->
                if (isExpanded) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = if (isOled) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        content()
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isOled: Boolean,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (trailingIcon != null) {
            trailingIcon()
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    isOled: Boolean,
    minLabel: String,
    maxLabel: String,
    leadingIcon: ImageVector? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                valueLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(minLabel, style = MaterialTheme.typography.labelSmall)
            Text(maxLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isOled: Boolean,
    enabled: Boolean = true,
    disableMaterialColors: Boolean = false
) {
    // When disabled, we still want onClick to fire for toast messages
    // so we handle the visual disabled state manually instead of using FilterChip's enabled parameter
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !enabled -> if (isOled) Color.White.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isSelected -> Color.Black
                    else -> Color.Unspecified
                }
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            labelColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = Color.Black
        ),
        modifier = if (!enabled) Modifier.alpha(0.5f) else Modifier
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
