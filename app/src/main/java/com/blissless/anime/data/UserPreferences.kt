package com.blissless.anime.data

import android.content.Context
import android.content.SharedPreferences
import com.blissless.anime.data.models.StoredFavorite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * Manages user preferences and settings
 */
class UserPreferences(private val context: Context) {

    companion object {
        private const val TAG = "UserPreferences"
        private const val PREFS_NAME = "anilist_prefs"
        private const val TOKEN_KEY = "auth_token"

        // Preference keys
        private const val KEY_OLED_MODE = "oled_mode"
        private const val KEY_DISABLE_MATERIAL_COLORS = "disable_material_colors"
        private const val KEY_PREFERRED_CATEGORY = "preferred_category"
        private const val KEY_SHOW_STATUS_COLORS = "show_status_colors"
        private const val KEY_TRACKING_PERCENTAGE = "tracking_percentage"
        private const val KEY_FORWARD_SKIP_SECONDS = "forward_skip_seconds"
        private const val KEY_BACKWARD_SKIP_SECONDS = "backward_skip_seconds"
        private const val KEY_FORCE_HIGH_REFRESH_RATE = "force_high_refresh_rate"
        private const val KEY_HIDE_NAVBAR_TEXT = "hide_navbar_text"
        private const val KEY_SIMPLIFY_EPISODE_MENU = "simplify_episode_menu"
        private const val KEY_SIMPLIFY_ANIME_DETAILS = "simplify_anime_details"
        private const val KEY_AUTO_SKIP_OPENING = "auto_skip_opening"
        private const val KEY_AUTO_SKIP_ENDING = "auto_skip_ending"
        private const val KEY_AUTO_PLAY_NEXT_EPISODE = "auto_play_next_episode"
        private const val KEY_ENABLE_THUMBNAIL_PREVIEW = "enable_thumbnail_preview"
        private const val KEY_LOCAL_FAVORITES_V2 = "local_favorites_v2"
        private const val KEY_LOCAL_FAVORITES = "local_favorites"
        private const val KEY_PREFERRED_SCRAPER = "preferred_scraper"
        private const val KEY_LAST_HOME_REFRESH = "last_home_refresh_time"
        private const val KEY_LAST_EXPLORE_REFRESH = "last_explore_refresh_time"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Auth token
    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    // UI Preferences
    private val _isOled = MutableStateFlow(false)
    val isOled: StateFlow<Boolean> = _isOled.asStateFlow()

    private val _disableMaterialColors = MutableStateFlow(false)
    val disableMaterialColors: StateFlow<Boolean> = _disableMaterialColors.asStateFlow()

    private val _preferredCategory = MutableStateFlow("sub")
    val preferredCategory: StateFlow<String> = _preferredCategory.asStateFlow()

    private val _showStatusColors = MutableStateFlow(true)
    val showStatusColors: StateFlow<Boolean> = _showStatusColors.asStateFlow()

    private val _trackingPercentage = MutableStateFlow(85)
    val trackingPercentage: StateFlow<Int> = _trackingPercentage.asStateFlow()

    private val _forwardSkipSeconds = MutableStateFlow(10)
    val forwardSkipSeconds: StateFlow<Int> = _forwardSkipSeconds.asStateFlow()

    private val _backwardSkipSeconds = MutableStateFlow(10)
    val backwardSkipSeconds: StateFlow<Int> = _backwardSkipSeconds.asStateFlow()

    private val _forceHighRefreshRate = MutableStateFlow(false)
    val forceHighRefreshRate: StateFlow<Boolean> = _forceHighRefreshRate.asStateFlow()

    private val _hideNavbarText = MutableStateFlow(false)
    val hideNavbarText: StateFlow<Boolean> = _hideNavbarText.asStateFlow()

    private val _simplifyEpisodeMenu = MutableStateFlow(true)
    val simplifyEpisodeMenu: StateFlow<Boolean> = _simplifyEpisodeMenu.asStateFlow()

    private val _simplifyAnimeDetails = MutableStateFlow(true)
    val simplifyAnimeDetails: StateFlow<Boolean> = _simplifyAnimeDetails.asStateFlow()

    private val _autoSkipOpening = MutableStateFlow(false)
    val autoSkipOpening: StateFlow<Boolean> = _autoSkipOpening.asStateFlow()

    private val _autoSkipEnding = MutableStateFlow(false)
    val autoSkipEnding: StateFlow<Boolean> = _autoSkipEnding.asStateFlow()

    private val _autoPlayNextEpisode = MutableStateFlow(false)
    val autoPlayNextEpisode: StateFlow<Boolean> = _autoPlayNextEpisode.asStateFlow()

    // Thumbnail extraction for seekbar preview
    private val _enableThumbnailPreview = MutableStateFlow(false)
    val enableThumbnailPreview: StateFlow<Boolean> = _enableThumbnailPreview.asStateFlow()

    // Preferred Scraper
    private val _preferredScraper = MutableStateFlow("Animekai")
    val preferredScraper: StateFlow<String> = _preferredScraper.asStateFlow()

    // Local favorites
    private val _localFavorites = MutableStateFlow<Map<Int, StoredFavorite>>(emptyMap())
    val localFavorites: StateFlow<Map<Int, StoredFavorite>> = _localFavorites.asStateFlow()

    val localFavoriteIds: Set<Int> get() = _localFavorites.value.keys

    /**
     * Load all preferences from SharedPreferences.
     * @param hasToken Whether to attempt loading the auth token
     */
    fun loadPreferences(hasToken: Boolean) {
        // Load auth token if requested
        if (hasToken) {
            val token = sharedPreferences.getString(TOKEN_KEY, null)
            _authToken.value = token
        }

        // Load UI preferences
        _isOled.value = sharedPreferences.getBoolean(KEY_OLED_MODE, false)
        _disableMaterialColors.value = sharedPreferences.getBoolean(KEY_DISABLE_MATERIAL_COLORS, false)
        _preferredCategory.value = sharedPreferences.getString(KEY_PREFERRED_CATEGORY, "sub") ?: "sub"
        _showStatusColors.value = sharedPreferences.getBoolean(KEY_SHOW_STATUS_COLORS, false)
        _trackingPercentage.value = sharedPreferences.getInt(KEY_TRACKING_PERCENTAGE, 85)
        _forwardSkipSeconds.value = sharedPreferences.getInt(KEY_FORWARD_SKIP_SECONDS, 10)
        _backwardSkipSeconds.value = sharedPreferences.getInt(KEY_BACKWARD_SKIP_SECONDS, 10)
        _forceHighRefreshRate.value = sharedPreferences.getBoolean(KEY_FORCE_HIGH_REFRESH_RATE, false)
        _hideNavbarText.value = sharedPreferences.getBoolean(KEY_HIDE_NAVBAR_TEXT, false)
        _simplifyEpisodeMenu.value = sharedPreferences.getBoolean(KEY_SIMPLIFY_EPISODE_MENU, false)
        _simplifyAnimeDetails.value = sharedPreferences.getBoolean(KEY_SIMPLIFY_ANIME_DETAILS, false)
        _autoSkipOpening.value = sharedPreferences.getBoolean(KEY_AUTO_SKIP_OPENING, false)
        _autoSkipEnding.value = sharedPreferences.getBoolean(KEY_AUTO_SKIP_ENDING, false)
        _autoPlayNextEpisode.value = sharedPreferences.getBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, true)
        _enableThumbnailPreview.value = sharedPreferences.getBoolean(KEY_ENABLE_THUMBNAIL_PREVIEW, false)
        _preferredScraper.value = sharedPreferences.getString(KEY_PREFERRED_SCRAPER, "Animekai") ?: "Animekai"

        // Load local favorites
        loadLocalFavorites()

    }

    // ============================================
    // Auth Methods
    // ============================================

    fun saveToken(token: String) {
        sharedPreferences.edit { putString(TOKEN_KEY, token) }
        _authToken.value = token
    }

    fun clearToken() {
        _authToken.value = null
        sharedPreferences.edit { remove(TOKEN_KEY) }
    }

    // ============================================
    // UI Preference Setters
    // ============================================

    fun setOledMode(enabled: Boolean) {
        _isOled.value = enabled
        sharedPreferences.edit { putBoolean(KEY_OLED_MODE, enabled) }
    }

    fun setDisableMaterialColors(enabled: Boolean) {
        _disableMaterialColors.value = enabled
        sharedPreferences.edit { putBoolean(KEY_DISABLE_MATERIAL_COLORS, enabled) }
    }

    fun setPreferredCategory(category: String) {
        _preferredCategory.value = category
        sharedPreferences.edit { putString(KEY_PREFERRED_CATEGORY, category) }
    }

    fun setShowStatusColors(enabled: Boolean) {
        _showStatusColors.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SHOW_STATUS_COLORS, enabled) }
    }

    fun setTrackingPercentage(percentage: Int) {
        val validPercentage = percentage.coerceIn(50, 100)
        _trackingPercentage.value = validPercentage
        sharedPreferences.edit { putInt(KEY_TRACKING_PERCENTAGE, validPercentage) }
    }

    fun setForwardSkipSeconds(seconds: Int) {
        val validSeconds = seconds.coerceIn(5, 30)
        _forwardSkipSeconds.value = validSeconds
        sharedPreferences.edit {putInt(KEY_FORWARD_SKIP_SECONDS, validSeconds) }
    }

    fun setBackwardSkipSeconds(seconds: Int) {
        val validSeconds = seconds.coerceIn(5, 30)
        _backwardSkipSeconds.value = validSeconds
        sharedPreferences.edit { putInt(KEY_BACKWARD_SKIP_SECONDS, validSeconds) }
    }

    fun setForceHighRefreshRate(enabled: Boolean) {
        _forceHighRefreshRate.value = enabled
        sharedPreferences.edit { putBoolean(KEY_FORCE_HIGH_REFRESH_RATE, enabled) }
    }

    fun setHideNavbarText(enabled: Boolean) {
        _hideNavbarText.value = enabled
        sharedPreferences.edit {putBoolean(KEY_HIDE_NAVBAR_TEXT, enabled) }
    }

    fun setSimplifyEpisodeMenu(enabled: Boolean) {
        _simplifyEpisodeMenu.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SIMPLIFY_EPISODE_MENU, enabled) }
    }

    fun setSimplifyAnimeDetails(enabled: Boolean) {
        _simplifyAnimeDetails.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SIMPLIFY_ANIME_DETAILS, enabled) }
    }

    fun setAutoSkipOpening(enabled: Boolean) {
        _autoSkipOpening.value = enabled
        sharedPreferences.edit {putBoolean(KEY_AUTO_SKIP_OPENING, enabled) }
    }

    fun setAutoSkipEnding(enabled: Boolean) {
        _autoSkipEnding.value = enabled
        sharedPreferences.edit {putBoolean(KEY_AUTO_SKIP_ENDING, enabled) }
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        _autoPlayNextEpisode.value = enabled
        sharedPreferences.edit {putBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, enabled) }
    }

    /**
     * Enable or disable thumbnail preview for video scrubbing.
     * This is a resource-intensive feature that extracts video frames.
     */
    fun setEnableThumbnailPreview(enabled: Boolean) {
        _enableThumbnailPreview.value = enabled
        sharedPreferences.edit {putBoolean(KEY_ENABLE_THUMBNAIL_PREVIEW, enabled) }
    }

    fun setPreferredScraper(scraper: String) {
        _preferredScraper.value = scraper
        sharedPreferences.edit { putString(KEY_PREFERRED_SCRAPER, scraper) }
    }

    // ============================================
    // Local Favorites
    // ============================================

    fun isLocalFavorite(mediaId: Int): Boolean = _localFavorites.value.containsKey(mediaId)

    fun canAddFavorite(): Boolean = _localFavorites.value.size < 10

    fun getLocalFavoriteCount(): Int = _localFavorites.value.size

    fun toggleLocalFavorite(
        mediaId: Int,
        title: String = "",
        cover: String = "",
        banner: String? = null,
        year: Int? = null,
        averageScore: Int? = null
    ) {
        val currentFavorites = _localFavorites.value.toMutableMap()
        val existingFavorite = currentFavorites[mediaId]

        if (existingFavorite != null) {
            // Update metadata if title was empty before
            if (existingFavorite.title.isEmpty() && title.isNotEmpty()) {
                currentFavorites[mediaId] = StoredFavorite(mediaId, title, cover, banner, year, averageScore)
            } else {
                // Remove favorite
                currentFavorites.remove(mediaId)
            }
        } else {
            // Add new favorite (max 10)
            if (currentFavorites.size >= 10) {
                return
            }
            currentFavorites[mediaId] = StoredFavorite(mediaId, title, cover, banner, year, averageScore)
        }

        _localFavorites.value = currentFavorites
        saveLocalFavorites(currentFavorites)
    }

    /**
     * Update metadata for an existing favorite.
     */
    fun updateFavoriteMetadata(
        mediaId: Int,
        title: String,
        cover: String,
        banner: String?,
        year: Int?,
        averageScore: Int?
    ) {
        val currentFavorites = _localFavorites.value.toMutableMap()
        if (currentFavorites.containsKey(mediaId)) {
            currentFavorites[mediaId] = StoredFavorite(mediaId, title, cover, banner, year, averageScore)
            _localFavorites.value = currentFavorites
            saveLocalFavorites(currentFavorites)
        }
    }

    private fun saveLocalFavorites(favorites: Map<Int, StoredFavorite>) {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val favoritesJson = favorites.values.map { fav ->
            json.encodeToString(StoredFavorite.serializer(), fav)
        }.toSet()

        sharedPreferences.edit {
            putStringSet(KEY_LOCAL_FAVORITES_V2, favoritesJson)
        }
    }

    private fun loadLocalFavorites() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        // Try v2 format first (with metadata)
        val savedV2 = sharedPreferences.getStringSet(KEY_LOCAL_FAVORITES_V2, null)
        if (savedV2 != null) {
            val favorites = mutableMapOf<Int, StoredFavorite>()
            savedV2.forEach { favJson ->
                try {
                    val fav = json.decodeFromString(StoredFavorite.serializer(), favJson)
                    favorites[fav.id] = fav
                } catch (e: Exception) {
                }
            }
            _localFavorites.value = favorites
            return
        }

        // Fall back to v1 format (IDs only)
        val saved = sharedPreferences.getStringSet(KEY_LOCAL_FAVORITES, emptySet()) ?: emptySet()
        if (saved.isNotEmpty()) {
            val favorites = mutableMapOf<Int, StoredFavorite>()
            saved.mapNotNull { it.toIntOrNull() }.forEach { id ->
                favorites[id] = StoredFavorite(id, "", "")
            }
            _localFavorites.value = favorites

            // Save in v2 format
            saveLocalFavorites(favorites)

            // Clear old format
            sharedPreferences.edit {remove(KEY_LOCAL_FAVORITES) }
        }
    }

    // ============================================
    // Data Management
    // ============================================

    fun clearAllUserData() {
        sharedPreferences.edit {
            remove(TOKEN_KEY)
                .remove("cache_home_data")
                .remove("cache_home_time")
                .remove("cache_explore_data")
                .remove("cache_explore_time")
                .remove("cache_airing_data")
                .remove("cache_airing_time")
        }
    }

    /**
     * Clear all preferences (useful for debugging or complete reset)
     */
    fun clearAllPreferences() {
        sharedPreferences.edit {clear()}
    }

    fun getSharedPreferences(): SharedPreferences = sharedPreferences

    // Refresh timestamp management
    fun getLastHomeRefreshTime(): Long = sharedPreferences.getLong(KEY_LAST_HOME_REFRESH, 0L)
    fun setLastHomeRefreshTime(time: Long) {
        sharedPreferences.edit { putLong(KEY_LAST_HOME_REFRESH, time) }
    }

    fun getLastExploreRefreshTime(): Long = sharedPreferences.getLong(KEY_LAST_EXPLORE_REFRESH, 0L)
    fun setLastExploreRefreshTime(time: Long) {
        sharedPreferences.edit { putLong(KEY_LAST_EXPLORE_REFRESH, time) }
    }
}
