package com.blissless.anime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blissless.anime.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    isLoggedIn: Boolean
) {
    val scrollState = rememberScrollState()
    val trackingPercentage by viewModel.trackingPercentage.collectAsState(initial = 85)

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

        // OLED Mode Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "OLED Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Pure black background for AMOLED screens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isOled,
                    onCheckedChange = { viewModel.setOledMode(it) }
                )
            }
        }

        // Episode Tracking Percentage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Episode Tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "$trackingPercentage%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Auto-update AniList progress when you've watched this percentage of an episode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                var sliderPosition by remember { mutableFloatStateOf(trackingPercentage.toFloat()) }

                Slider(
                    value = sliderPosition,
                    onValueChange = { newValue ->
                        val rounded = (newValue / 5).toInt() * 5
                        sliderPosition = rounded.toFloat()
                    },
                    onValueChangeFinished = {
                        viewModel.setTrackingPercentage(sliderPosition.toInt())
                    },
                    valueRange = 50f..100f,
                    steps = 10
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("50%", style = MaterialTheme.typography.labelSmall)
                    Text("100%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Account Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoggedIn) {
                    val userName by viewModel.userName.collectAsState()
                    val userAvatar by viewModel.userAvatar.collectAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userAvatar?.let { avatar ->
                            Text("👤 ", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            userName ?: "Logged In",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
}