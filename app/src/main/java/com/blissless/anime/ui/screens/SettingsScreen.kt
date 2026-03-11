package com.blissless.anime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.blissless.anime.MainViewModel
import kotlin.math.round

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    isLoggedIn: Boolean,
    showStatusColors: Boolean = true,
    forceHighRefreshRate: Boolean = false,
    hideNavbarText: Boolean = false,
    autoSkipOpening: Boolean = false,
    autoSkipEnding: Boolean = false,
    autoPlayNextEpisode: Boolean = false,
    disableMaterialColors: Boolean = false,
    preferredCategory: String = "sub",
    simplifyEpisodeMenu: Boolean = true,
    simplifyAnimeDetails: Boolean = true,
    enableThumbnailPreview: Boolean = false
) {
    val scrollState = rememberScrollState()
    val trackingPercentage by viewModel.trackingPercentage.collectAsState(initial = 85)
    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)

    // Collect the settings from ViewModel
    val simplifyEpisodeMenuState by viewModel.simplifyEpisodeMenu.collectAsState(initial = true)
    val simplifyAnimeDetailsState by viewModel.simplifyAnimeDetails.collectAsState(initial = true)

    // Track thumbnail preview state for showing info dialog
    var showThumbnailInfoDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        // Appearance Section
        SettingsSection(
            title = "Appearance",
            icon = Icons.Default.Palette,
            isOled = isOled
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
                title = "High Refresh Rate",
                description = "Force 120Hz for smoother scrolling",
                checked = forceHighRefreshRate,
                onCheckedChange = { viewModel.setForceHighRefreshRate(it) },
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
                title = "Simple Anime Details",
                description = "Use compact dialog (disable for full info page)",
                checked = simplifyAnimeDetailsState,
                onCheckedChange = { viewModel.setSimplifyAnimeDetails(it) },
                isOled = isOled
            )
        }

        // Stream Settings Section
        SettingsSection(
            title = "Stream Settings",
            icon = Icons.Default.Subtitles,
            isOled = isOled
        ) {
            // Preferred Category Selection
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
                    isOled = isOled
                )
                CategoryChip(
                    label = "DUB",
                    isSelected = preferredCategory == "dub",
                    onClick = { viewModel.setPreferredCategory("dub") },
                    isOled = isOled
                )
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
            isOled = isOled
        ) {
            SettingsSlider(
                title = "Episode Tracking",
                description = "Auto-update AniList progress when you've watched this percentage",
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
                description = "Automatically skip anime openings when detected",
                checked = autoSkipOpening,
                onCheckedChange = { viewModel.setAutoSkipOpening(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Auto Skip Ending",
                description = "Automatically skip to next episode during credits",
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

        // Account Section
        SettingsSection(
            title = "Account",
            icon = Icons.Default.Person,
            isOled = isOled
        ) {
            if (isLoggedIn) {
                val userName by viewModel.userName.collectAsState()
                val userAvatar by viewModel.userAvatar.collectAsState()

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
                    Text(
                        userName ?: "Logged In",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Log Out")
                }
            } else {
                Button(
                    onClick = { viewModel.loginWithAniList() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Login with AniList")
                }
            }
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
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    isOled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOled) Color(0xFF121212) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 12.dp),
                color = if (isOled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            content()
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
    isOled: Boolean
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            labelColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = Color.White
        )
    )
}
