package com.blissless.anime.stream

import android.content.Context
import android.util.Log
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

    companion object {
        private const val TAG = "SourceManager"
    }

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
            Log.d(TAG, "Detected ${extensions.size} extensions: ${extensions.map { "${it.name} (${it.packageName}) sourceClass=${it.sourceClass}" }}")
            sources = extensions.flatMap { ext ->
                try {
                    val loaded = loader.loadSources(ext)
                    Log.d(TAG, "Loaded ${loaded.size} sources from ${ext.packageName}")
                    loaded.map { SourceWithExt(it, ext) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load sources from ${ext.packageName}", e)
                    emptyList()
                }
            }
            Log.d(TAG, "Total loaded sources: ${sources.size}")
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
                    Log.e(TAG, "Search failed for ${sw.extension.packageName}", e)
                }
            }
        }
    }

    suspend fun getEpisodes(source: AnimeCatalogueSource, anime: SAnime): List<SEpisode> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "getEpisodes: source=${source.name} sourceClass=${source::class.qualifiedName} anime.title=${anime.title} anime.url=${anime.url}")
            Log.d(TAG, "  source.javaClass=${source.javaClass}")
            Log.d(TAG, "  getEpisodeList method exists: ${source.javaClass.methods.any { it.name == "getEpisodeList" }}")
            Log.d(TAG, "  super class: ${source.javaClass.superclass?.name}")
            try {
                val result = source.getEpisodeList(anime)
                Log.d(TAG, "getEpisodes OK: returned ${result.size} episodes")
                result.forEachIndexed { i, ep ->
                    Log.d(TAG, "  episode[$i]: name=${ep.name} url=${ep.url} ep_num=${ep.episode_number}")
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "getEpisodeList EXCEPTION", e)
                throw e
            }
        }
    }

    suspend fun getAnimeDetails(source: AnimeCatalogueSource, anime: SAnime): SAnime {
        return withContext(Dispatchers.IO) {
            source.getAnimeDetails(anime)
        }
    }

    suspend fun getHosters(source: AnimeCatalogueSource, episode: SEpisode): List<Hoster>? {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "getHosters episode: url=${episode.url} scanlator=${episode.scanlator} name=${episode.name}")
            Log.d(TAG, "getHosters source: ${source.name} class=${source::class.qualifiedName}")
            try {
                source.getHosterList(episode).also {
                    Log.d(TAG, "getHosters returned ${it.size} hosters: ${it.map { h -> h.hosterName }}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "getHosters failed", e)
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

    suspend fun getVideosDirect(source: AnimeCatalogueSource, episode: SEpisode): List<Video> {
        return withContext(Dispatchers.IO) {
            try {
                source.getVideoList(episode).also {
                    Log.d(TAG, "getVideosDirect returned ${it.size} videos")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "getVideosDirect failed", e)
                emptyList()
            }
        }
    }

    suspend fun getVideos(source: AnimeCatalogueSource, episode: SEpisode): List<Video> {
        return withContext(Dispatchers.IO) {
            val hosters = try {
                source.getHosterList(episode).also {
                    Log.d(TAG, "getHosterList returned ${it.size} hosters")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "getHosterList failed", e)
                null
            }
            if (hosters != null && hosters.isNotEmpty()) {
                hosters.flatMap { hoster ->
                    if (hoster.lazy) {
                        if (hoster.hosterUrl.isNotEmpty()) {
                            source.getVideoList(hoster)
                        } else {
                            source.getVideoList(hoster)
                        }
                    } else {
                        hoster.videoList ?: source.getVideoList(hoster)
                    }
                }
            } else {
                Log.d(TAG, "No hosters, trying getVideoList(episode) directly")
                try {
                    source.getVideoList(episode)
                } catch (e: Throwable) {
                    Log.e(TAG, "getVideoList(episode) also failed", e)
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
                Log.e(TAG, "resolveVideoUrl failed", e)
                video.videoUrl
            }
        }
    }

}
