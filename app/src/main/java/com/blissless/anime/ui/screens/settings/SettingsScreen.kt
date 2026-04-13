package com.blissless.anime.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.anime.MainViewModel
import com.blissless.anime.R
import com.blissless.anime.api.myanimelist.LoginProvider
import kotlin.math.round

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    isLoggedIn: Boolean,
    showStatusColors: Boolean = true,
    autoSkipOpening: Boolean = false,
    autoSkipEnding: Boolean = false,
    autoPlayNextEpisode: Boolean = true,
    disableMaterialColors: Boolean = false,
    preferredCategory: String = "sub",
    preferredScraper: String = "Animekai",
    onNavigateBack: () -> Unit = {}
) {
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    
    val settingsGroups = remember {
        listOf(
            SettingsGroup(
                id = "account",
                title = "Account",
                description = "Login and manage your anime list",
                icon = Icons.Default.Person,
                iconBackgroundColor = if (isOled) Color.White else primary
            ),
            SettingsGroup(
                id = "appearance",
                title = "Appearance",
                description = "Theme, colors, and display options",
                icon = Icons.Default.Palette,
                iconBackgroundColor = if (isOled) Color.White else secondary
            ),
            SettingsGroup(
                id = "general",
                title = "General",
                description = "Startup screen and sync settings",
                icon = Icons.Default.Settings,
                iconBackgroundColor = if (isOled) Color.White else tertiary
            ),
            SettingsGroup(
                id = "stream",
                title = "Stream Settings",
                description = "Audio preferences and buffering",
                icon = Icons.Default.PlayArrow,
                iconBackgroundColor = if (isOled) Color.White else primary
            ),
            SettingsGroup(
                id = "player",
                title = "Player Settings",
                description = "Playback controls and skipping",
                icon = Icons.Default.Subscriptions,
                iconBackgroundColor = if (isOled) Color.White else secondary
            ),
            SettingsGroup(
                id = "cache",
                title = "Cache Management",
                description = "Storage and data cleanup",
                icon = Icons.Default.Memory,
                iconBackgroundColor = if (isOled) Color.White else tertiary
            )
        )
    }

    AnimatedContent(
        targetState = selectedGroup,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "settingsNavigation"
    ) { targetGroup ->
        if (targetGroup == null) {
            SettingsLandingPage(
                groups = settingsGroups,
                onGroupClick = { selectedGroup = it },
                isOled = isOled
            )
        } else {
            BackHandler {
                selectedGroup = null
            }
            when (targetGroup) {
                "account" -> AccountSettingsPage(
                    viewModel = viewModel,
                    isOled = isOled,
                    onBack = { selectedGroup = null }
                )
                "appearance" -> AppearanceSettingsPage(
                    viewModel = viewModel,
                    isOled = isOled,
                    disableMaterialColors = disableMaterialColors,
                    onBack = { selectedGroup = null }
                )
                "general" -> GeneralSettingsPage(
                    viewModel = viewModel,
                    isOled = isOled,
                    onBack = { selectedGroup = null }
                )
                "stream" -> StreamSettingsPage(
                    viewModel = viewModel,
                    isOled = isOled,
                    disableMaterialColors = disableMaterialColors,
                    preferredCategory = preferredCategory,
                    onBack = { selectedGroup = null }
                )
                "player" -> PlayerSettingsPage(
                    viewModel = viewModel,
                    isOled = isOled,
                    autoSkipOpening = autoSkipOpening,
                    autoSkipEnding = autoSkipEnding,
                    autoPlayNextEpisode = autoPlayNextEpisode,
                    onBack = { selectedGroup = null }
                )
                "cache" -> CacheSettingsPage(
                    viewModel = viewModel,
                    context = LocalContext.current,
                    isOled = isOled,
                    onBack = { selectedGroup = null }
                )
            }
        }
    }
}

private data class SettingsGroup(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconBackgroundColor: Color
)

@Composable
private fun SettingsLandingPage(
    groups: List<SettingsGroup>,
    onGroupClick: (String) -> Unit,
    isOled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = R.mipmap.ic_launcher_round,
                contentDescription = "App",
                modifier = Modifier.size(32.dp).clip(CircleShape)
            )
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(groups) { group ->
                SettingsGroupCard(
                    group = group,
                    onClick = { onGroupClick(group.id) },
                    isOled = isOled
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    group: SettingsGroup,
    onClick: () -> Unit,
    isOled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(group.iconBackgroundColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = group.iconBackgroundColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                group.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageScaffold(
    title: String,
    icon: ImageVector,
    iconBackgroundColor: Color,
    onBack: () -> Unit,
    isOled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onBack)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBackgroundColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                when (title) {
                    "Account" -> "Login and manage your anime list"
                    "Appearance" -> "Theme, colors, and display options"
                    "General" -> "Startup screen and sync settings"
                    "Stream Settings" -> "Audio preferences and buffering"
                    "Player Settings" -> "Playback controls and skipping"
                    "Cache Management" -> "Storage and data cleanup"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun AccountSettingsPage(
    viewModel: MainViewModel,
    isOled: Boolean,
    onBack: () -> Unit
) {
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    val loginProvider by viewModel.loginProvider.collectAsState(initial = LoginProvider.NONE)

    SettingsPageScaffold(
        title = "Account",
        icon = Icons.Default.Person,
        iconBackgroundColor = if (isOled) Color.White else MaterialTheme.colorScheme.primary,
        onBack = onBack,
        isOled = isOled
    ) {
        if (loginProvider != LoginProvider.NONE) {
            val userName by viewModel.userName.collectAsState()
            val userAvatar by viewModel.userAvatar.collectAsState()
            val providerName = when (loginProvider) {
                LoginProvider.ANILIST -> "AniList"
                LoginProvider.MAL -> "MyAnimeList"
                LoginProvider.NONE -> ""
            }

            SettingsCard(isOled = isOled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    if (userAvatar != null) {
                        AsyncImage(
                            model = userAvatar,
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            userName ?: "Logged In",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "via $providerName",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsCard(isOled = isOled) {
                Button(
                    onClick = { showLogoutConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Log Out")
                }
            }
        } else {
            Text(
                "Login with AniList",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsCard(isOled = isOled) {
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Login with MyAnimeList",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsCard(isOled = isOled) {
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
private fun AppearanceSettingsPage(
    viewModel: MainViewModel,
    isOled: Boolean,
    disableMaterialColors: Boolean,
    onBack: () -> Unit
) {
    val showStatusColorsState by viewModel.showStatusColors.collectAsState(initial = true)
    val simplifyEpisodeMenuState by viewModel.simplifyEpisodeMenu.collectAsState(initial = false)
    val showAnimeCardButtons by viewModel.showAnimeCardButtons.collectAsState(initial = true)
    val preferEnglishTitles by viewModel.preferEnglishTitles.collectAsState(initial = true)

    SettingsPageScaffold(
        title = "Appearance",
        icon = Icons.Default.Palette,
        iconBackgroundColor = if (isOled) Color.White else MaterialTheme.colorScheme.secondary,
        onBack = onBack,
        isOled = isOled
    ) {
        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "OLED Mode",
                description = "Pure black background for AMOLED screens",
                checked = isOled,
                onCheckedChange = { viewModel.setOledMode(it) },
                isOled = isOled
            )
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Monochrome Theme",
                description = "Disable Material3 colors for neutral appearance",
                checked = disableMaterialColors,
                onCheckedChange = { viewModel.setDisableMaterialColors(it) },
                isOled = isOled
            )
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Status Color Indicators",
                description = "Show colored status bars on anime cards",
                checked = showStatusColorsState,
                onCheckedChange = { viewModel.setShowStatusColors(it) },
                isOled = isOled
            )
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Simple Episode Menu",
                description = "Use compact episode grid (disable for detailed cards)",
                checked = simplifyEpisodeMenuState,
                onCheckedChange = { viewModel.setSimplifyEpisodeMenu(it) },
                isOled = isOled
            )
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Show Card Buttons",
                description = "Show bookmark and play buttons on anime cards in Explore",
                checked = showAnimeCardButtons,
                onCheckedChange = { viewModel.setShowAnimeCardButtons(it) },
                isOled = isOled
            )
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "English Titles",
                description = "Show English titles instead of Romaji",
                checked = preferEnglishTitles,
                onCheckedChange = { viewModel.setPreferEnglishTitles(it) },
                isOled = isOled
            )
        }
    }
}

@Composable
private fun GeneralSettingsPage(
    viewModel: MainViewModel,
    isOled: Boolean,
    onBack: () -> Unit
) {
    val startupScreenState by viewModel.startupScreen.collectAsState()
    val preventScheduleSync by viewModel.preventScheduleSync.collectAsState()
    val hideAdultContentState by viewModel.hideAdultContent.collectAsState(initial = false)

    SettingsPageScaffold(
        title = "General",
        icon = Icons.Default.Settings,
        iconBackgroundColor = if (isOled) Color.White else MaterialTheme.colorScheme.tertiary,
        onBack = onBack,
        isOled = isOled
    ) {
        SettingsCard(isOled = isOled) {
            Column {
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

                Spacer(modifier = Modifier.height(12.dp))

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
            }
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Auto Sync in Airing Schedule",
                description = "Automatically sync airing schedule when opening",
                checked = !preventScheduleSync,
                onCheckedChange = { viewModel.setPreventScheduleSync(!it) },
                isOled = isOled
            )
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Hide Adult Content",
                description = "Exclude 18+ anime from showing up",
                checked = hideAdultContentState,
                onCheckedChange = { viewModel.setHideAdultContent(it) },
                isOled = isOled
            )
        }
    }
}

@Composable
private fun StreamSettingsPage(
    viewModel: MainViewModel,
    isOled: Boolean,
    disableMaterialColors: Boolean,
    preferredCategory: String,
    onBack: () -> Unit
) {
    val bufferAheadSeconds by viewModel.bufferAheadSeconds.collectAsState(initial = 30)
    val bufferSizeMb by viewModel.bufferSizeMb.collectAsState(initial = 100)
    val showBufferIndicator by viewModel.showBufferIndicator.collectAsState(initial = true)

    SettingsPageScaffold(
        title = "Stream Settings",
        icon = Icons.Default.PlayArrow,
        iconBackgroundColor = if (isOled) Color.White else MaterialTheme.colorScheme.primary,
        onBack = onBack,
        isOled = isOled
    ) {
        SettingsCard(isOled = isOled) {
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

                Spacer(modifier = Modifier.height(12.dp))

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
        }

        SettingsCard(isOled = isOled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (isOled) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Buffer Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "Control video buffering behavior",
                style = MaterialTheme.typography.bodySmall,
                color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            SettingsToggle(
                title = "Show Buffer Indicator",
                description = "Display buffered amount on the progress bar",
                checked = showBufferIndicator,
                onCheckedChange = { viewModel.setShowBufferIndicator(it) },
                isOled = isOled
            )
        }
    }
}

@Composable
private fun PlayerSettingsPage(
    viewModel: MainViewModel,
    isOled: Boolean,
    autoSkipOpening: Boolean,
    autoSkipEnding: Boolean,
    autoPlayNextEpisode: Boolean,
    onBack: () -> Unit
) {
    val trackingPercentage by viewModel.trackingPercentage.collectAsState(initial = 85)
    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)

    SettingsPageScaffold(
        title = "Player Settings",
        icon = Icons.Default.Subscriptions,
        iconBackgroundColor = Color(0xFFE91E63),
        onBack = onBack,
        isOled = isOled
    ) {
        SettingsCard(isOled = isOled) {
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
        }

        SettingsCard(isOled = isOled) {
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
        }

        SettingsCard(isOled = isOled) {
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

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Auto Skip Opening",
                description = "Automatically skip anime openings",
                checked = autoSkipOpening,
                onCheckedChange = { viewModel.setAutoSkipOpening(it) },
                isOled = isOled
            )
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Auto Skip Ending",
                description = "Automatically skip anime endings",
                checked = autoSkipEnding,
                onCheckedChange = { viewModel.setAutoSkipEnding(it) },
                isOled = isOled
            )
        }

        SettingsCard(isOled = isOled) {
            SettingsToggle(
                title = "Auto Play Next Episode",
                description = "Automatically play the next episode when current one ends",
                checked = autoPlayNextEpisode,
                onCheckedChange = { viewModel.setAutoPlayNextEpisode(it) },
                isOled = isOled
            )
        }
    }
}

@Composable
private fun CacheSettingsPage(
    viewModel: MainViewModel,
    context: Context,
    isOled: Boolean,
    onBack: () -> Unit
) {
    var cacheSize by remember { mutableLongStateOf(0L) }
    var showClearCacheConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSize = viewModel.getVideoCacheSize(context)
    }

    SettingsPageScaffold(
        title = "Cache Management",
        icon = Icons.Default.Memory,
        iconBackgroundColor = Color(0xFF00BCD4),
        onBack = onBack,
        isOled = isOled
    ) {
        SettingsCard(isOled = isOled) {
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

@Composable
private fun SettingsCard(
    isOled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
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

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isOled: Boolean,
    enabled: Boolean = true,
    disableMaterialColors: Boolean = false
) {
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
        )
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