package com.blissless.anime.stream

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import com.blissless.anime.extensions.Extension
import com.blissless.anime.extensions.ExtensionDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SourceManager(private val context: Context) {
    private val detector = ExtensionDetector(context)
    private val loader = ExtensionLoader(context)
    private var sources: List<SourceWithExt> = emptyList()

    data class SourceWithExt(
        val source: AnimeCatalogueSource,
        val extension: Extension,
    )

    fun getSources(): List<SourceWithExt> = sources

    fun reloadSources() {
        sources = emptyList()
    }

    suspend fun loadSources() {
        withContext(Dispatchers.IO) {
            val extensions = detector.detectInstalledExtensions()
            sources = extensions.flatMap { ext ->
                try {
                    val loaded = loader.loadSources(ext)
                    loaded.map { SourceWithExt(it, ext) }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }

    suspend fun search(
        query: String,
        sourceFilter: SourceWithExt? = null,
        onProgress: (SourceWithExt, List<SAnime>) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val targets = if (sourceFilter != null) listOf(sourceFilter) else sources
            for (sw in targets) {
                try {
                    val page = sw.source.getSearchAnime(1, query, AnimeFilterList())
                    if (page.animes.isNotEmpty()) {
                        onProgress(sw, page.animes)
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    suspend fun getEpisodes(source: AnimeCatalogueSource, anime: SAnime): List<SEpisode> {
        return withContext(Dispatchers.IO) {
            try {
                source.getEpisodeList(anime)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getAnimeDetails(source: AnimeCatalogueSource, anime: SAnime): SAnime {
        return withContext(Dispatchers.IO) {
            try {
                source.getAnimeDetails(anime)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getHosters(source: AnimeCatalogueSource, episode: SEpisode, anime: SAnime? = null): List<Hoster>? {
        return withContext(Dispatchers.IO) {
            if (anime != null && source is AnimeHttpSource) {
                source.prepareNewEpisode(episode, anime)
            }
            try {
                source.getHosterList(episode)
            } catch (e: Throwable) {
                try {
                    val videos = source.getVideoList(episode)
                    if (videos.isNotEmpty()) {
                        val derivedHosters = videos.map { video ->
                            Hoster(
                                hosterUrl = video.videoUrl,
                                hosterName = video.videoTitle.take(50),
                                videoList = listOf(video),
                                lazy = false,
                            )
                        }
                        return@withContext derivedHosters.distinctBy { it.hosterName }
                    }
                } catch (_: Throwable) {}
                null
            }
        }
    }

    suspend fun getVideosFromHoster(source: AnimeCatalogueSource, hoster: Hoster): List<Video> {
        return withContext(Dispatchers.IO) {
            if (hoster.lazy) {
                source.getVideoList(hoster)
            } else {
                hoster.videoList ?: source.getVideoList(hoster)
            }
        }
    }

    suspend fun getVideosDirect(source: AnimeCatalogueSource, episode: SEpisode, anime: SAnime? = null): List<Video> {
        return withContext(Dispatchers.IO) {
            if (anime != null && source is AnimeHttpSource) {
                source.prepareNewEpisode(episode, anime)
            }
            try {
                source.getVideoList(episode)
            } catch (e: Throwable) {
                emptyList()
            }
        }
    }

    suspend fun getVideos(source: AnimeCatalogueSource, episode: SEpisode, anime: SAnime? = null): List<Video> {
        return withContext(Dispatchers.IO) {
            if (anime != null && source is AnimeHttpSource) {
                source.prepareNewEpisode(episode, anime)
            }
            val hosters = try {
                source.getHosterList(episode)
            } catch (e: Throwable) {
                null
            }
            if (hosters != null && hosters.isNotEmpty()) {
                hosters.flatMap { hoster ->
                    if (hoster.lazy) {
                        source.getVideoList(hoster)
                    } else {
                        hoster.videoList ?: source.getVideoList(hoster)
                    }
                }
            } else {
                try {
                    source.getVideoList(episode)
                } catch (e: Throwable) {
                    emptyList()
                }
            }
        }
    }

    suspend fun resolveVideoUrl(source: AnimeCatalogueSource, video: Video): String {
        return withContext(Dispatchers.IO) {
            try {
                (source as? AnimeHttpSource)?.getVideoUrl(video) ?: video.videoUrl
            } catch (e: Throwable) {
                video.videoUrl
            }
        }
    }

}
