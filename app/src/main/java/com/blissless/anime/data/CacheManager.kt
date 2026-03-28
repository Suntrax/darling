package com.blissless.anime.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.*
import androidx.media3.datasource.DefaultHttpDataSource
import com.blissless.anime.data.models.AniwatchStreamResult
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.ServerInfo
import com.blissless.anime.data.models.QualityOption
import com.blissless.anime.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import androidx.core.content.edit
import java.io.File

class CacheManager(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val TAG = "CacheManager"
        private const val CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L
        private const val AIRING_CACHE_DURATION_MS = 1 * 60 * 60 * 1000L
        private const val STREAM_CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L

        private const val CACHE_EXPLORE_TIME = "cache_explore_time"
        private const val CACHE_HOME_TIME = "cache_home_time"
        private const val CACHE_EXPLORE_DATA = "cache_explore_data"
        private const val CACHE_HOME_DATA = "cache_home_data"
        private const val CACHE_STREAM_DATA = "cache_stream_data"
        private const val CACHE_AIRING_TIME = "cache_airing_time"
        private const val CACHE_AIRING_DATA = "cache_airing_data"
        private const val CACHE_PLAYBACK_POSITIONS = "cache_playback_positions"
        
        // Video cache settings
        private const val VIDEO_CACHE_SIZE_BYTES = 1024L * 1024 * 1024 // 1 GB default - more space for offline content
        private var videoCache: SimpleCache? = null
        private var isCacheInitialized = false
    }

    // Initialize video cache - call this once when app starts
    @OptIn(UnstableApi::class)
    fun initializeVideoCache(context: Context) {
        if (isCacheInitialized) return
        
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(VIDEO_CACHE_SIZE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context)
            videoCache = SimpleCache(cacheDir, evictor, databaseProvider)
            isCacheInitialized = true
        } catch (e: Exception) {
            // Cache initialization failed, continue without caching
        }
    }
    
    // Get a CacheDataSource.Factory that uses the video cache
    @OptIn(UnstableApi::class)
    fun getCacheDataSourceFactory(referer: String): CacheDataSource.Factory? {
        val cache = videoCache ?: return null
        
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(60000) // Increased timeout for caching
            .setDefaultRequestProperties(mapOf("Referer" to referer))
        
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            // Don't use FLAG_IGNORE_CACHE_ON_ERROR - we want to use cache even if there are issues
    }
    
    // Get the raw SimpleCache for direct access if needed
    @OptIn(UnstableApi::class)
    fun getVideoCache(): SimpleCache? = videoCache
    
    // Release the cache when app closes
    @OptIn(UnstableApi::class)
    fun releaseVideoCache() {
        try {
            videoCache?.release()
            videoCache = null
            isCacheInitialized = false
        } catch (e: Exception) {
            // Ignore errors during release
        }
    }
    
    // Preload a video URL to the cache completely
    @OptIn(UnstableApi::class)
    fun preloadVideoToCache(
        context: Context,
        videoUrl: String,
        referer: String,
        onProgress: ((Long, Long) -> Unit)? = null,
        scope: CoroutineScope
    ): Job? {
        val cache = videoCache ?: return null
        
        val job = scope.launch(Dispatchers.IO) {
            try {
                // Check if already fully cached
                val key = videoUrl.hashCode().toString()
                val cachedSpans = cache.getCachedSpans(key)
                val cachedBytes = cachedSpans.sumOf { it.length }
                
                // Get content length from the server
                val contentLength = getContentLength(videoUrl, referer)
                
                if (contentLength > 0 && cachedBytes >= contentLength) {
                    // Already fully cached
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(contentLength, contentLength)
                    }
                    return@launch
                }
                
                // Use CacheDataSource to download the entire video
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(20000)
                    .setDefaultRequestProperties(mapOf("Referer" to referer))
                
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                
                val dataSource = cacheDataSourceFactory.createDataSource()
                
                // Read the data to trigger caching
                val buffer = ByteArray(64 * 1024) // 64KB buffer
                var totalRead = cachedBytes
                var lastProgressUpdate = 0L
                
                try {
                    val dataSpec = androidx.media3.datasource.DataSpec.Builder()
                        .setUri(videoUrl)
                        .setPosition(cachedBytes)
                        .build()
                    
                    val sourceResponse = dataSource.open(dataSpec)
                    try {
                        while (isActive) {
                            val bytesRead = dataSource.read(buffer, 0, buffer.size)
                            if (bytesRead == -1) break
                            totalRead += bytesRead
                            
                            // Update progress periodically (every 500ms)
                            val now = System.currentTimeMillis()
                            if (contentLength > 0 && now - lastProgressUpdate > 500) {
                                lastProgressUpdate = now
                                withContext(Dispatchers.Main) {
                                    onProgress?.invoke(totalRead, contentLength)
                                }
                            }
                        }
                    } finally {
                        dataSource.close()
                    }
                } catch (e: Exception) {
                    // Read failed, but cache may have some data
                }
                
                // Report completion
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(totalRead, totalRead)
                }
                
            } catch (e: Exception) {
                // Preload failed, continue without full cache
            }
        }
        
        return job
    }
    
    private fun getContentLength(videoUrl: String, referer: String): Long {
        return try {
            val connection = java.net.URL(videoUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("Referer", referer)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val length = connection.contentLengthLong
            connection.disconnect()
            length
        } catch (e: Exception) {
            -1
        }
    }
    
    // Check if a video is fully cached
    @OptIn(UnstableApi::class)
    fun isVideoFullyCached(videoUrl: String): Boolean {
        val cache = videoCache ?: return false
        return try {
            val key = videoUrl.hashCode().toString()
            val cachedSpans = cache.getCachedSpans(key)
            val cachedBytes = cachedSpans.sumOf { it.length }
            val contentLength = getContentLength(videoUrl, "")
            contentLength > 0 && cachedBytes >= contentLength
        } catch (e: Exception) {
            false
        }
    }
    
    // Get cache progress for a video
    @OptIn(UnstableApi::class)
    fun getCacheProgress(videoUrl: String): Pair<Long, Long>? {
        val cache = videoCache ?: return null
        return try {
            val key = videoUrl.hashCode().toString()
            val cachedSpans = cache.getCachedSpans(key)
            val cachedBytes = cachedSpans.sumOf { it.length }
            val contentLength = getContentLength(videoUrl, "")
            if (contentLength > 0) {
                Pair(cachedBytes, contentLength)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val _prefetchedStreams = MutableStateFlow<Map<String, AniwatchStreamResult?>>(emptyMap())
    val prefetchedStreams: StateFlow<Map<String, AniwatchStreamResult?>> = _prefetchedStreams.asStateFlow()

    private val _prefetchedEpisodeInfo = MutableStateFlow<Map<String, EpisodeStreams?>>(emptyMap())
    val prefetchedEpisodeInfo: StateFlow<Map<String, EpisodeStreams?>> = _prefetchedEpisodeInfo.asStateFlow()

    private val _detailedAnimeCache = MutableStateFlow<Map<Int, DetailedAnimeData>>(emptyMap())
    val detailedAnimeCache: StateFlow<Map<Int, DetailedAnimeData>> = _detailedAnimeCache.asStateFlow()

    private val _playbackPositions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val playbackPositions: StateFlow<Map<String, Long>> = _playbackPositions.asStateFlow()

    fun invalidateUserCache() {
        sharedPreferences.edit {
            remove(CACHE_HOME_DATA)
                .remove(CACHE_HOME_TIME)
        }
    }

    private fun isCacheValid(cacheKey: String, customDuration: Long = CACHE_DURATION_MS): Boolean {
        val cacheTime = sharedPreferences.getLong(cacheKey, 0)
        val now = System.currentTimeMillis()
        return (now - cacheTime) < customDuration
    }

    private fun setCacheTime(cacheKey: String) {
        sharedPreferences.edit { putLong(cacheKey, System.currentTimeMillis()) }
    }

    fun saveHomeDataToCache(data: HomeCacheData) {
        try {
            val jsonString = json.encodeToString(HomeCacheData.serializer(), data)
            sharedPreferences.edit { putString(CACHE_HOME_DATA, jsonString) }
            setCacheTime(CACHE_HOME_TIME)
        } catch (e: Exception) {
        }
    }

    fun loadHomeDataFromCache(): HomeCacheData? {
        val cachedData = sharedPreferences.getString(CACHE_HOME_DATA, null)
        if (cachedData != null && isCacheValid(CACHE_HOME_TIME)) {
            return try {
                json.decodeFromString<HomeCacheData>(cachedData)
            } catch (e: Exception) { null }
        }
        return null
    }

    fun saveExploreDataToCache(data: ExploreCacheData) {
        try {
            val jsonString = json.encodeToString(ExploreCacheData.serializer(), data)
            sharedPreferences.edit { putString(CACHE_EXPLORE_DATA, jsonString) }
            setCacheTime(CACHE_EXPLORE_TIME)
        } catch (e: Exception) {
        }
    }

    fun loadExploreDataFromCache(): ExploreCacheData? {
        val cachedData = sharedPreferences.getString(CACHE_EXPLORE_DATA, null)
        if (cachedData != null && isCacheValid(CACHE_EXPLORE_TIME)) {
            return try {
                json.decodeFromString<ExploreCacheData>(cachedData)
            } catch (e: Exception) { null }
        }
        return null
    }

    fun saveAiringScheduleCache(scheduleByDay: Map<Int, List<AiringScheduleAnime>>, airingAnimeList: List<AiringScheduleAnime>) {
        try {
            val cacheData = AiringCacheData(scheduleByDay, airingAnimeList)
            val jsonString = json.encodeToString(AiringCacheData.serializer(), cacheData)
            sharedPreferences.edit { putString(CACHE_AIRING_DATA, jsonString) }
            setCacheTime(CACHE_AIRING_TIME)
        } catch (e: Exception) {
        }
    }

    fun loadAiringScheduleCache(): AiringCacheData? {
        val cachedData = sharedPreferences.getString(CACHE_AIRING_DATA, null)
        if (cachedData != null && isCacheValid(CACHE_AIRING_TIME, AIRING_CACHE_DURATION_MS)) {
            return try {
                json.decodeFromString<AiringCacheData>(cachedData)
            } catch (e: Exception) { null }
        }
        return null
    }

    fun loadStreamCache() {
        try {
            val cachedData = sharedPreferences.getString(CACHE_STREAM_DATA, null) ?: return
            val cacheData = json.decodeFromString<StreamCacheData>(cachedData)
            val now = System.currentTimeMillis()

            val streamMap = mutableMapOf<String, AniwatchStreamResult?>()
            val episodeMap = mutableMapOf<String, EpisodeStreams?>()

            cacheData.entries.filter { (now - it.value.timestamp) < STREAM_CACHE_DURATION_MS }
                .forEach { (key, entry) ->
                    streamMap[key] = entry.stream?.let {
                        val qualities = it.qualities.map { q -> QualityOption(q.quality, q.url, q.width) }
                        AniwatchStreamResult(
                            it.url,
                            it.isDirectStream,
                            it.headers,
                            it.subtitleUrl,
                            it.serverName,
                            it.category,
                            qualities,
                            // Skip timestamps
                            it.introStart,
                            it.introEnd,
                            it.outroStart,
                            it.outroEnd
                        )
                    }
                    entry.episodeInfo?.let { ep ->
                        episodeMap[key] = EpisodeStreams(
                            ep.subServers.map { s ->
                                val quals = s.qualities.map { q -> QualityOption(q.quality, q.url, q.width) }
                                ServerInfo(s.name, s.url, quals)
                            },
                            ep.dubServers.map { s ->
                                val quals = s.qualities.map { q -> QualityOption(q.quality, q.url, q.width) }
                                ServerInfo(s.name, s.url, quals)
                            },
                            ep.animeId, ep.episodeId
                        )
                    }
                }

            _prefetchedStreams.value = streamMap
            _prefetchedEpisodeInfo.value = episodeMap
        } catch (e: Exception) {
        }
    }

    fun saveStreamCache() {
        try {
            val now = System.currentTimeMillis()
            val entries = _prefetchedStreams.value.mapValues { (key, stream) ->
                val ep = _prefetchedEpisodeInfo.value[key]
                StreamCacheEntry(
                    stream = stream?.let {
                        val qualities = it.qualities.map { q -> CachedQuality(q.quality, q.url, q.width) }
                        CachedStream(
                            it.url,
                            it.isDirectStream,
                            it.headers,
                            it.subtitleUrl,
                            it.serverName,
                            it.category,
                            qualities,
                            // Skip timestamps
                            it.introStart,
                            it.introEnd,
                            it.outroStart,
                            it.outroEnd
                        )
                    },
                    episodeInfo = ep?.let { info ->
                        CachedEpisodeInfo(
                            info.subServers.map { s ->
                                val quals = s.qualities.map { q -> CachedQuality(q.quality, q.url, q.width) }
                                CachedServer(s.name, s.url, quals)
                            },
                            info.dubServers.map { s ->
                                val quals = s.qualities.map { q -> CachedQuality(q.quality, q.url, q.width) }
                                CachedServer(s.name, s.url, quals)
                            },
                            info.animeId, info.episodeId
                        )
                    },
                    timestamp = now
                )
            }

            val jsonString = json.encodeToString(StreamCacheData.serializer(), StreamCacheData(entries))
            sharedPreferences.edit { putString(CACHE_STREAM_DATA, jsonString) }
        } catch (e: Exception) {
        }
    }

    fun loadPlaybackPositions() {
        try {
            val cachedData = sharedPreferences.getString(CACHE_PLAYBACK_POSITIONS, null) ?: return
            val cacheData = json.decodeFromString<PlaybackPositionCache>(cachedData)
            _playbackPositions.value = cacheData.positions
        } catch (e: Exception) {
        }
    }

    fun savePlaybackPosition(animeId: Int, episode: Int, position: Long) {
        val key = "${animeId}_$episode"
        _playbackPositions.value = _playbackPositions.value + (key to position)
        try {
            val jsonString = json.encodeToString(PlaybackPositionCache.serializer(), PlaybackPositionCache(_playbackPositions.value))
            sharedPreferences.edit { putString(CACHE_PLAYBACK_POSITIONS, jsonString) }
        } catch (e: Exception) { }
    }

    fun getPlaybackPosition(animeId: Int, episode: Int): Long = _playbackPositions.value["${animeId}_$episode"] ?: 0L

    fun clearPlaybackPosition(animeId: Int, episode: Int) {
        val key = "${animeId}_$episode"
        if (_playbackPositions.value.containsKey(key)) {
            _playbackPositions.value = _playbackPositions.value - key
            sharedPreferences.edit {
                val jsonString = json.encodeToString(PlaybackPositionCache.serializer(), PlaybackPositionCache(_playbackPositions.value))
                putString(CACHE_PLAYBACK_POSITIONS, jsonString)
            }
        }
    }

    fun clearAllPlaybackPositionsForAnime(animeId: Int) {
        val prefix = "${animeId}_"
        val newMap = _playbackPositions.value.filterKeys { !it.startsWith(prefix) }
        _playbackPositions.value = newMap
        sharedPreferences.edit {
            val jsonString = json.encodeToString(PlaybackPositionCache.serializer(), PlaybackPositionCache(_playbackPositions.value))
            putString(CACHE_PLAYBACK_POSITIONS, jsonString)
        }
    }

    fun getCachedStream(key: String): AniwatchStreamResult? {
        val stream = _prefetchedStreams.value[key]
        if (stream != null) {
        }
        return stream
    }

    fun getCachedStreamImmediate(animeId: Int, episode: Int, category: String): AniwatchStreamResult? {
        val key = "${animeId}_${episode}_$category"
        return _prefetchedStreams.value[key]
    }

    fun cacheStream(key: String, stream: AniwatchStreamResult?) {
        _prefetchedStreams.value = _prefetchedStreams.value + (key to stream)
        saveStreamCache()
    }

    fun getCachedEpisodeInfo(key: String): EpisodeStreams? {
        return _prefetchedEpisodeInfo.value[key]
    }

    fun cacheEpisodeInfo(key: String, info: EpisodeStreams) {
        _prefetchedEpisodeInfo.value = _prefetchedEpisodeInfo.value + (key to info)
    }

    fun hasStream(key: String): Boolean {
        val exists = _prefetchedStreams.value.containsKey(key)
        return exists
    }

    fun hasAnyStreamForEpisode(animeId: Int, episode: Int): String? {
        val subKey = "${animeId}_${episode}_sub"
        val dubKey = "${animeId}_${episode}_dub"

        return when {
            _prefetchedStreams.value.containsKey(subKey) -> "sub"
            _prefetchedStreams.value.containsKey(dubKey) -> "dub"
            else -> null
        }
    }

    fun getAvailableCategories(animeId: Int, episode: Int): List<String> {
        val categories = mutableListOf<String>()
        val subKey = "${animeId}_${episode}_sub"
        val dubKey = "${animeId}_${episode}_dub"

        if (_prefetchedStreams.value.containsKey(subKey)) categories.add("sub")
        if (_prefetchedStreams.value.containsKey(dubKey)) categories.add("dub")

        return categories
    }

    /**
     * Clear stream cache for a specific episode.
     * Call this when a stream fails to force refetch on next attempt.
     */
    fun invalidateStreamCache(animeId: Int, episode: Int, category: String? = null) {
        val keysToRemove = mutableListOf<String>()

        if (category != null) {
            // Remove specific category cache
            keysToRemove.add("${animeId}_${episode}_$category")
        } else {
            // Remove both sub and dub caches
            keysToRemove.add("${animeId}_${episode}_sub")
            keysToRemove.add("${animeId}_${episode}_dub")
        }

        // Also remove the old-style key
        keysToRemove.add("${animeId}_$episode")

        val newMap = _prefetchedStreams.value.toMutableMap()
        var removed = false
        keysToRemove.forEach { key ->
            if (newMap.containsKey(key)) {
                newMap.remove(key)
                removed = true
            }
        }

        if (removed) {
            _prefetchedStreams.value = newMap
            saveStreamCache()
        }
    }

    /**
     * Clear stream cache for a specific server/category combination.
     */
    fun invalidateServerCache(animeId: Int, episode: Int, serverName: String, category: String) {
        val key = "${animeId}_${episode}_${serverName}_$category"

        if (_prefetchedStreams.value.containsKey(key)) {
            val newMap = _prefetchedStreams.value.toMutableMap()
            newMap.remove(key)
            _prefetchedStreams.value = newMap
            saveStreamCache()
        }
    }

    fun clearStreamForEpisode(animeId: Int, episode: Int) {
        val subKey = "${animeId}_${episode}_sub"
        val dubKey = "${animeId}_${episode}_dub"

        val newMap = _prefetchedStreams.value.toMutableMap()
        newMap.remove(subKey)
        newMap.remove(dubKey)
        _prefetchedStreams.value = newMap

        val oldKey = "${animeId}_$episode"
        newMap.remove(oldKey)

        saveStreamCache()
    }

    fun getCachedDetailedAnime(animeId: Int): DetailedAnimeData? = _detailedAnimeCache.value[animeId]
    fun cacheDetailedAnime(animeId: Int, data: DetailedAnimeData) {
        _detailedAnimeCache.value = _detailedAnimeCache.value + (animeId to data)
    }

    fun clearAllCaches() {
        _prefetchedStreams.value = emptyMap()
        _prefetchedEpisodeInfo.value = emptyMap()
        _detailedAnimeCache.value = emptyMap()
        sharedPreferences.edit { clear() }
    }
}
