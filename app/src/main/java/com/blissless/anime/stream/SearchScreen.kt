package com.blissless.anime.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blissless.anime.stream.SourceManager.SourceWithExt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSearchScreen(
    isOled: Boolean,
    onBack: () -> Unit,
    viewModel: ExtensionStreamViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var selectedExtIndex by remember { mutableIntStateOf(-1) }
    var showExtPicker by remember { mutableStateOf(false) }
    val sources = viewModel.getSources()

    LaunchedEffect(Unit) {
        viewModel.loadSources()
    }

    if (showExtPicker && sources.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showExtPicker = false },
            title = { Text("Choose extension") },
            text = {
                Column {
                    sources.forEachIndexed { index, sw ->
                        TextButton(
                            onClick = {
                                showExtPicker = false
                                selectedExtIndex = index
                                viewModel.search(searchText, sw)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(sw.extension.name.removePrefix("Aniyomi: "))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExtPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val hosters = uiState.hosters
    if (hosters != null && hosters.size > 1 && uiState.selectedHoster == null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Select Server") },
            text = {
                Column {
                    hosters.forEach { hoster ->
                        TextButton(
                            onClick = { viewModel.selectHoster(hoster) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(hoster.hosterName.ifEmpty { "Server" })
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    val pendingVideos = uiState.pendingVideos
    if (pendingVideos != null && uiState.selectedHoster == null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Select Source") },
            text = {
                Column {
                    pendingVideos.forEachIndexed { idx, video ->
                        val label = if (video.resolution != null && video.resolution > 0) "${video.videoTitle} (${video.resolution}p)"
                                    else video.videoTitle.ifEmpty { "Source ${idx + 1}" }
                        TextButton(
                            onClick = { viewModel.selectVideo(idx) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Extension Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface,
                    titleContentColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Anime name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (uiState.isSearching || !uiState.isInitialized) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Button(onClick = {
                            if (selectedExtIndex >= 0 && selectedExtIndex < sources.size) {
                                viewModel.search(searchText, sources[selectedExtIndex])
                            } else {
                                showExtPicker = true
                            }
                        }) {
                            Text("Search")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (sources.isNotEmpty()) {
                Text(
                    text = if (selectedExtIndex >= 0 && selectedExtIndex < sources.size)
                            "Using: ${sources[selectedExtIndex].extension.name.removePrefix("Aniyomi: ")}"
                           else "No extension selected — searches all",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEachIndexed { index, sw ->
                    FilterChip(
                        selected = selectedExtIndex == index,
                        onClick = {
                            selectedExtIndex = if (selectedExtIndex == index) -1 else index
                        },
                        label = {
                            Text(
                                sw.extension.name.removePrefix("Aniyomi: ").take(16),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            when {
                !uiState.isInitialized -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching...")
                        }
                    }
                }
                uiState.searchResults.isEmpty() && uiState.query.isNotEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found", color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                uiState.selectedAnime != null -> {
                    EpisodeListContent(viewModel, uiState, isOled)
                }
                uiState.searchResults.isNotEmpty() -> {
                    AnimeResults(viewModel, uiState, isOled)
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Select an extension, enter an anime name, and tap Search",
                            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeResults(
    viewModel: ExtensionStreamViewModel,
    uiState: ExtensionStreamUiState,
    isOled: Boolean,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(uiState.searchResults, key = { "${it.source.extension.packageName}_${it.anime.url}" }) { result ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    viewModel.selectAnime(result.source, result.anime)
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = result.anime.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    result.source.extension.name.removePrefix("Aniyomi: "),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    viewModel: ExtensionStreamViewModel,
    uiState: ExtensionStreamUiState,
    isOled: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.selectedAnime!!.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.backToResults() }) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = uiState.selectedSource?.extension?.name?.removePrefix("Aniyomi: ") ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isLoadingEpisodes) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.episodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No episodes found", color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.episodes, key = { it.url }) { episode ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectEpisode(episode) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = String.format("%.0f", episode.episode_number),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = episode.name.ifEmpty { "Episode ${episode.episode_number}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (uiState.selectedEpisode?.url == episode.url && uiState.isLoadingVideos) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
