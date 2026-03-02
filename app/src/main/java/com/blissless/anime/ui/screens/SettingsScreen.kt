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
import androidx.compose.material.icons.filled.Timer
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
    forceHighRefreshRate: Boolean = false
) {
    val scrollState = rememberScrollState()
    val trackingPercentage by viewModel.trackingPercentage.collectAsState(initial = 85)
    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)

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
            // OLED Mode Toggle
            SettingsToggle(
                title = "OLED Mode",
                description = "Pure black background for AMOLED screens",
                checked = isOled,
                onCheckedChange = { viewModel.setOledMode(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status Colors Toggle
            SettingsToggle(
                title = "Status Color Indicators",
                description = "Show colored status bars on anime cards",
                checked = showStatusColors,
                onCheckedChange = { viewModel.setShowStatusColors(it) },
                isOled = isOled
            )

            Spacer(modifier = Modifier.height(8.dp))

            // High Refresh Rate Toggle
            SettingsToggle(
                title = "High Refresh Rate",
                description = "Force 120Hz for smoother scrolling",
                checked = forceHighRefreshRate,
                onCheckedChange = { viewModel.setForceHighRefreshRate(it) },
                isOled = isOled
            )
        }

        // Player Settings Section
        SettingsSection(
            title = "Player Settings",
            icon = Icons.Default.PlayArrow,
            isOled = isOled
        ) {
            // Episode Tracking
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

            // Skip Forward Duration
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

            // Skip Backward Duration
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
                    // Avatar image or placeholder
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
            // Section Header
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

            // Section Content
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
    isOled: Boolean
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
