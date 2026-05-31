package com.blissless.anime.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.blissless.anime.data.models.AiringCacheData
import com.blissless.anime.data.models.AiringScheduleAnime
import com.blissless.anime.data.models.AniwatchStreamResult
import com.blissless.anime.data.models.CachedEpisodeInfo
import com.blissless.anime.data.models.CachedQuality
import com.blissless.anime.data.models.CachedServer
import com.blissless.anime.data.models.CachedStream
import com.blissless.anime.data.models.DetailedAnimeData
import com.blissless.anime.data.models.EpisodeStreams
import com.blissless.anime.data.models.ExploreCacheData
import com.blissless.anime.data.models.HomeCacheData
import com.blissless.anime.data.models.PlaybackPositionCache
import com.blissless.anime.data.models.QualityOption
import com.blissless.anime.data.models.ServerInfo
import com.blissless.anime.data.models.StreamCacheData
import com.blissless.anime.data.models.StreamCacheEntry
import com.blissless.anime.data.models.TmdbEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

class CacheManager(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val TAG = "CacheManager"
        private const val CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L
        private const val AIRING_CACHE_DURATION_MS = 1 * 60 * 60 * 1000L
        private const val STREAM_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

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

    // Stream cache with timestamps for TTL (30 minutes)
    private val _streamCacheTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val streamCacheTtlMs = 30 * 60 * 1000L // 30 minutes

    private val _prefetchedStreams = MutableStateFlow<Map<String, AniwatchStreamResult?>>(emptyMap())
    val prefetchedStreams: StateFlow<Map<String, AniwatchStreamResult?>> = _prefetchedStreams.asStateFlow()

    private val _prefetchedEpisodeInfo = MutableStateFlow<Map<String, EpisodeStreams?>>(emptyMap())
    val prefetchedEpisodeInfo: StateFlow<Map<String, EpisodeStreams?>> = _prefetchedEpisodeInfo.asStateFlow()

    private val _detailedAnimeCache = MutableStateFlow<Map<Int, DetailedAnimeData>>(emptyMap())
    val detailedAnimeCache: StateFlow<Map<Int, DetailedAnimeData>> = _detailedAnimeCache.asStateFlow()

    private val _playbackPositions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val playbackPositions: StateFlow<Map<String, Long>> = _playbackPositions.asStateFlow()

    private val _playbackDurations = MutableStateFlow<Map<String, Long>>(emptyMap())
    val playbackDurations: StateFlow<Map<String, Long>> = _playbackDurations.asStateFlow()

    // TMDB episode cache - stores episode titles by anime ID
    private val _tmdbEpisodeCache = MutableStateFlow<Map<Int, List<TmdbEpisode>>>(emptyMap())
    val tmdbEpisodeCache: StateFlow<Map<Int, List<TmdbEpisode>>> = _tmdbEpisodeCache.asStateFlow()

    fun getCachedTmdbEpisodes(animeId: Int): List<TmdbEpisode>? {
        return _tmdbEpisodeCache.value[animeId]
    }

    fun cacheTmdbEpisodes(animeId: Int, episodes: List<TmdbEpisode>) {
        _tmdbEpisodeCache.value = _tmdbEpisodeCache.value + (animeId to episodes)
    }

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
            _playbackDurations.value = cacheData.durations
        } catch (e: Exception) {
        }
    }

    fun savePlaybackPosition(animeId: Int, episode: Int, position: Long, duration: Long = 0L) {
        val key = "${animeId}_$episode"
        _playbackPositions.value = _playbackPositions.value + (key to position)
        if (duration > 0L) {
            _playbackDurations.value = _playbackDurations.value + (key to duration)
        }
        try {
            val jsonString = json.encodeToString(
                PlaybackPositionCache.serializer(),
                PlaybackPositionCache(_playbackPositions.value, _playbackDurations.value)
            )
            sharedPreferences.edit { putString(CACHE_PLAYBACK_POSITIONS, jsonString) }
        } catch (e: Exception) { }
    }

    fun getPlaybackPosition(animeId: Int, episode: Int): Long = _playbackPositions.value["${animeId}_$episode"] ?: 0L

    fun getPlaybackDuration(animeId: Int, episode: Int): Long = _playbackDurations.value["${animeId}_$episode"] ?: 0L

    fun clearPlaybackPosition(animeId: Int, episode: Int) {
        val key = "${animeId}_$episode"
        if (_playbackPositions.value.containsKey(key)) {
            _playbackPositions.value = _playbackPositions.value - key
            _playbackDurations.value = _playbackDurations.value - key
            sharedPreferences.edit {
                val jsonString = json.encodeToString(
                    PlaybackPositionCache.serializer(),
                    PlaybackPositionCache(_playbackPositions.value, _playbackDurations.value)
                )
                putString(CACHE_PLAYBACK_POSITIONS, jsonString)
            }
        }
    }

    fun clearAllPlaybackPositionsForAnime(animeId: Int) {
        val prefix = "${animeId}_"
        val newMap = _playbackPositions.value.filterKeys { !it.startsWith(prefix) }
        _playbackPositions.value = newMap
        _playbackDurations.value = _playbackDurations.value.filterKeys { !it.startsWith(prefix) }
        sharedPreferences.edit {
            val jsonString = json.encodeToString(
                PlaybackPositionCache.serializer(),
                PlaybackPositionCache(_playbackPositions.value, _playbackDurations.value)
            )
            putString(CACHE_PLAYBACK_POSITIONS, jsonString)
        }
    }

    fun getCachedStream(key: String): AniwatchStreamResult? {
        val stream = _prefetchedStreams.value[key]
        if (stream != null) {
        }
        return stream
    }

    fun cacheStream(key: String, stream: AniwatchStreamResult?) {
        _prefetchedStreams.value = _prefetchedStreams.value + (key to stream)
        // Store timestamp for TTL tracking
        _streamCacheTimestamps.value = _streamCacheTimestamps.value + (key to System.currentTimeMillis())
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

    // Time-based expiration for detailed anime cache
    private val _detailedAnimeCacheTimestamps = mutableMapOf<Int, Long>()
    private val DETAILED_ANIME_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    private val MAX_DETAILED_ANIME_CACHE_SIZE = 50

    fun getCachedDetailedAnime(animeId: Int): DetailedAnimeData? {
        val data = _detailedAnimeCache.value[animeId]
        val timestamp = _detailedAnimeCacheTimestamps[animeId]

        if (data != null && timestamp != null) {
            val age = System.currentTimeMillis() - timestamp
            if (age > DETAILED_ANIME_CACHE_MAX_AGE_MS) {
                clearDetailedAnimeCache(animeId)
                return null
            }
        }
        return data
    }

    fun cacheDetailedAnime(animeId: Int, data: DetailedAnimeData) {
        _detailedAnimeCache.value = _detailedAnimeCache.value + (animeId to data)
        _detailedAnimeCacheTimestamps[animeId] = System.currentTimeMillis()
        trimDetailedAnimeCacheToLimit()
    }

    fun clearDetailedAnimeCache(animeId: Int) {
        val updated = _detailedAnimeCache.value.toMutableMap()
        updated.remove(animeId)
        _detailedAnimeCache.value = updated
        _detailedAnimeCacheTimestamps.remove(animeId)
    }

    fun clearExpiredDetailedAnimeCache() {
        val now = System.currentTimeMillis()
        val expiredKeys = _detailedAnimeCacheTimestamps.filter { (_, timestamp) ->
            (now - timestamp) > DETAILED_ANIME_CACHE_MAX_AGE_MS
        }.keys

        if (expiredKeys.isNotEmpty()) {
            val updated = _detailedAnimeCache.value.toMutableMap()
            expiredKeys.forEach { key ->
                updated.remove(key)
                _detailedAnimeCacheTimestamps.remove(key)
            }
            _detailedAnimeCache.value = updated
        }
    }

    private fun trimDetailedAnimeCacheToLimit() {
        if (_detailedAnimeCache.value.size > MAX_DETAILED_ANIME_CACHE_SIZE) {
            val sortedByTime = _detailedAnimeCacheTimestamps.entries.sortedBy { it.value }
            val keysToRemove = sortedByTime.take(_detailedAnimeCache.value.size - MAX_DETAILED_ANIME_CACHE_SIZE).map { it.key }

            val updated = _detailedAnimeCache.value.toMutableMap()
            keysToRemove.forEach { key ->
                updated.remove(key)
                _detailedAnimeCacheTimestamps.remove(key)
            }
            _detailedAnimeCache.value = updated
        }
    }

    fun clearAllCaches() {
        _prefetchedStreams.value = emptyMap()
        _prefetchedEpisodeInfo.value = emptyMap()
        _detailedAnimeCache.value = emptyMap()
        _detailedAnimeCacheTimestamps.clear()
        sharedPreferences.edit { clear() }
    }

    // ExoPlayer video cache management
    @OptIn(UnstableApi::class)
    fun clearVideoCache(context: Context): Long {
        var bytesCleared = 0L

        // First release the cache if it exists
        val cache = videoCache
        if (cache != null) {
            try {
                cache.release()
                videoCache = null
                isCacheInitialized = false
            } catch (e: Exception) {
                // Cache release failed
            }
        }

        // Clear from disk
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    bytesCleared += file.length()
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Disk cleanup failed
        }

        return bytesCleared
    }

    @OptIn(UnstableApi::class)
    fun getVideoCacheSize(context: Context): Long {
        val cache = videoCache
        if (cache != null) {
            try {
                val cacheDir = File(context.cacheDir, "video_cache")
                if (cacheDir.exists()) {
                    return cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                }
            } catch (e: Exception) {
                // Error getting cache size
            }
        }

        // Fallback to disk calculation
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (cacheDir.exists()) {
                return cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            }
        } catch (e: Exception) {
            // Error getting disk cache size
        }
        return 0L
    }

    // Clear all non-essential caches (for settings "Clear Cache" button)
    fun clearNonEssentialCaches(context: Context) {
        clearVideoCache(context)
        clearExpiredDetailedAnimeCache()
        trimDetailedAnimeCacheToLimit()

        // Clear stream cache but keep playback positions
        _prefetchedStreams.value = emptyMap()
        _prefetchedEpisodeInfo.value = emptyMap()
        sharedPreferences.edit { remove(CACHE_STREAM_DATA) }
    }
}
