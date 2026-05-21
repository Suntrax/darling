package com.blissless.anime.stream

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import com.blissless.anime.stream.SourceManager.SourceWithExt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchResult(
    val source: SourceWithExt,
    val anime: SAnime,
)

data class StreamUiState(
    val isInitialized: Boolean = false,
    val isSearching: Boolean = false,
    val query: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val selectedAnime: SAnime? = null,
    val selectedSource: SourceWithExt? = null,
    val episodes: List<SEpisode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val selectedEpisode: SEpisode? = null,
    val isLoadingVideos: Boolean = false,
    val hosters: List<Hoster>? = null,
    val selectedHoster: Hoster? = null,
    val pendingVideos: List<Video>? = null,
    val error: String? = null,
)

class StreamViewModel(application: Application) : AndroidViewModel(application) {

    val manager = SourceManager(application)

    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    fun reloadSources() {
        _uiState.value = _uiState.value.copy(isInitialized = false)
        viewModelScope.launch {
            manager.reloadSources()
            manager.loadSources()
            _uiState.value = _uiState.value.copy(isInitialized = true)
        }
    }

    fun loadSources() {
        if (_uiState.value.isInitialized) return
        viewModelScope.launch {
            manager.loadSources()
            _uiState.value = _uiState.value.copy(isInitialized = true)
        }
    }

    fun search(query: String, sourceFilter: SourceWithExt? = null) {
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(
            isSearching = true,
            query = query,
            searchResults = emptyList(),
            selectedAnime = null,
            selectedSource = null,
            episodes = emptyList(),
            selectedEpisode = null,
        )
        viewModelScope.launch {
            val results = mutableListOf<SearchResult>()
            manager.search(query, sourceFilter) { sw, animes ->
                animes.forEach { anime ->
                    results.add(SearchResult(sw, anime))
                }
                _uiState.value = _uiState.value.copy(searchResults = results.toList())
            }
            _uiState.value = _uiState.value.copy(isSearching = false, searchResults = results.toList())
        }
    }

    fun selectAnime(source: SourceWithExt, anime: SAnime) {
        android.util.Log.d("StreamVM", "selectAnime called: source=${source.extension.name} anime=${anime.title} url=${anime.url}")
        _uiState.value = _uiState.value.copy(
            selectedAnime = anime,
            selectedSource = source,
            isLoadingEpisodes = true,
            episodes = emptyList(),
            selectedEpisode = null,
        )
        viewModelScope.launch {
            android.util.Log.d("StreamVM", "calling manager.getEpisodes...")
            val episodes = manager.getEpisodes(source.source, anime)
            android.util.Log.d("StreamVM", "manager.getEpisodes returned ${episodes.size} episodes")
            _uiState.value = _uiState.value.copy(
                episodes = episodes,
                isLoadingEpisodes = false,
            )
        }
    }

    fun selectEpisode(episode: SEpisode) {
        val source = _uiState.value.selectedSource ?: return
        val current = _uiState.value
        if (current.isLoadingVideos) return

        _uiState.value = current.copy(
            selectedEpisode = episode,
            isLoadingVideos = true,
            hosters = null,
            selectedHoster = null,
        )
        viewModelScope.launch {
            val hosters = manager.getHosters(source.source, episode)
            android.util.Log.d("StreamVM", "hosters = $hosters (${hosters?.size})")
            _uiState.value = _uiState.value.copy(
                isLoadingVideos = false,
                hosters = hosters,
            )
            when {
                hosters == null -> {
                    android.util.Log.d("StreamVM", "getHosters threw, falling back to getVideosDirect")
                    launchPlayer(source.source, episode)
                }
                hosters.size == 1 -> {
                    android.util.Log.d("StreamVM", "Single hoster, getting videos")
                    loadAndPlay(source.source, hosters.first(), episode)
                }
                hosters.isEmpty() -> {
                    android.util.Log.d("StreamVM", "No hosters, trying direct video list")
                    launchPlayer(source.source, episode)
                }
                else -> {
                    android.util.Log.d("StreamVM", "${hosters.size} hosters available, waiting for user selection")
                }
            }
        }
    }

    fun selectHoster(hoster: Hoster) {
        val source = _uiState.value.selectedSource ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedHoster = hoster,
                isLoadingVideos = true,
            )
            loadAndPlay(source.source, hoster, _uiState.value.selectedEpisode!!)
        }
    }

    private suspend fun loadAndPlay(source: AnimeCatalogueSource, hoster: Hoster, episode: SEpisode) {
        val videos = try {
            val v = manager.getVideosFromHoster(source, hoster)
            if (source is AnimeHttpSource) {
                PlayerData.extensionClient = source.client
                android.util.Log.d("StreamVM", "Got extension OkHttpClient: ${PlayerData.extensionClient}")
            }
            v
        } catch (e: Throwable) {
            android.util.Log.e("StreamVM", "getVideosFromHoster failed, trying fallback", e)
            try {
                val v = manager.getVideosDirect(source, episode)
                if (source is AnimeHttpSource) {
                    PlayerData.extensionClient = source.client
                }
                v
            } catch (e2: Throwable) {
                android.util.Log.e("StreamVM", "fallback also failed", e2)
                _uiState.value = _uiState.value.copy(error = "Failed to load videos: ${e2.message}")
                emptyList()
            }
        }
        android.util.Log.d("StreamVM", "getVideosFromHoster returned ${videos.size} videos")
        videos.forEach { v ->
            android.util.Log.d("StreamVM", "  video: title='${v.videoTitle}' url='${v.videoUrl}' res=${v.resolution}")
        }
        _uiState.value = _uiState.value.copy(isLoadingVideos = false)
        launchPlayerWithVideos(videos)
    }

    private suspend fun launchPlayer(source: AnimeCatalogueSource, episode: SEpisode) {
        val videos = try {
            val v = manager.getVideosDirect(source, episode)
            if (source is AnimeHttpSource) {
                PlayerData.extensionClient = source.client
            }
            v
        } catch (e: Throwable) {
            android.util.Log.e("StreamVM", "getVideosDirect failed", e)
            _uiState.value = _uiState.value.copy(error = "Failed to load videos: ${e.message}")
            emptyList()
        }
        android.util.Log.d("StreamVM", "launchPlayer returned ${videos.size} videos")
        videos.forEach { v ->
            android.util.Log.d("StreamVM", "  video: title='${v.videoTitle}' url='${v.videoUrl}' res=${v.resolution}")
        }
        _uiState.value = _uiState.value.copy(isLoadingVideos = false)
        launchPlayerWithVideos(videos)
    }

    private fun launchPlayerWithVideos(videos: List<Video>) {
        if (videos.isEmpty()) {
            android.util.Log.e("StreamVM", "No videos found, cannot launch player")
            return
        }
        _uiState.value = _uiState.value.copy(pendingVideos = videos)
    }

    fun selectVideo(index: Int) {
        val oldVideos = _uiState.value.pendingVideos ?: return
        val source = _uiState.value.selectedSource ?: return
        val episode = _uiState.value.selectedEpisode ?: return

        val bestIndex = oldVideos.indexOfLast {
            val res = it.resolution ?: 0
            if (res == 0) {
                (it.videoTitle.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) > 0
            } else res > 0
        }.let { if (it < 0) oldVideos.indices.last else it }

        android.util.Log.d("StreamVM", "Selected video index: $index, best: $bestIndex, oldVideos: ${oldVideos.size}")
        android.util.Log.d("StreamVM", "Original videoUrl=${oldVideos[index].videoUrl}")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVideos = true)
            try {
                // Re-fetch fresh video list just before playing (URL tokens expire)
                val freshVideos = manager.getVideosDirect(source.source, episode)
                if (freshVideos.isNotEmpty()) {
                    android.util.Log.d("StreamVM", "Fresh videos: ${freshVideos.size}, using index $index")
                    val freshVideo = freshVideos.getOrNull(index) ?: freshVideos.first()
                    android.util.Log.d("StreamVM", "Fresh videoUrl=${freshVideo.videoUrl}")
                    val ctx = getApplication<Application>()
                    PlayerData.videos = freshVideos
                    PlayerData.animeTitle = source.extension.name.removePrefix("Aniyomi: ")
                    PlayerData.currentQualityIndex = freshVideos.indexOf(freshVideo).coerceAtLeast(0)
                    PlayerData.selectedSubtitle = freshVideo.subtitleTracks.firstOrNull()
                    PlayerData.selectedAudio = freshVideo.audioTracks.firstOrNull()
                    PlayerData.extensionClient = (source.source as? AnimeHttpSource)?.client
                    ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } else {
                    android.util.Log.e("StreamVM", "Fresh video list is empty")
                }
            } catch (e: Throwable) {
                android.util.Log.e("StreamVM", "Failed to re-fetch videos, using stale URLs", e)
                val ctx = getApplication<Application>()
                PlayerData.videos = oldVideos
                PlayerData.animeTitle = source.extension.name.removePrefix("Aniyomi: ")
                PlayerData.currentQualityIndex = index
                PlayerData.selectedSubtitle = oldVideos[index].subtitleTracks.firstOrNull()
                PlayerData.selectedAudio = oldVideos[index].audioTracks.firstOrNull()
                PlayerData.extensionClient = (source.source as? AnimeHttpSource)?.client
                ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } finally {
                _uiState.value = _uiState.value.copy(pendingVideos = null, isLoadingVideos = false)
            }
        }
    }

    fun backToResults() {
        _uiState.value = _uiState.value.copy(
            selectedAnime = null,
            selectedSource = null,
            episodes = emptyList(),
            selectedEpisode = null,
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getSources(): List<SourceWithExt> = manager.getSources()
}
