package com.blissless.anime.player

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.core.content.edit

/**
 * Stores user-marked intro/outro timestamps for anime
 * This is more reliable than automatic detection
 *
 * Place this file at: app/src/main/java/com/blissless/anime/player/TimestampManager.kt
 */
class TimestampManager(context: Context) {

    companion object {
        private const val TAG = "TimestampManager"
        private const val PREFS_NAME = "anime_timestamps"
        private const val KEY_TIMESTAMPS = "saved_timestamps"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Save timestamps for an anime
     */
    fun saveTimestamps(
        animeId: Int,
        animeName: String,
        episodeNumber: Int,
        introStart: Long?,
        introEnd: Long?,
        creditsStart: Long?
    ) {
        val timestamps = getSavedTimestampsMap().toMutableMap()
        val key = if (animeId > 0) {
            "${animeId}_$episodeNumber"
        } else {
            "${animeName}_$episodeNumber"
        }

        timestamps[key] = SavedTimestamp(
            animeId = animeId,
            animeName = animeName,
            episodeNumber = episodeNumber,
            introStart = introStart,
            introEnd = introEnd,
            creditsStart = creditsStart,
            timestamp = System.currentTimeMillis()
        )

        prefs.edit {
            putString(
                KEY_TIMESTAMPS, json.encodeToString(
                    SerializableMap(timestamps)
                )
            )
        }

        Log.d(TAG, "Saved timestamps for $animeName ep $episodeNumber: intro=$introStart-$introEnd, credits=$creditsStart")
    }

    /**
     * Get timestamps for a specific anime episode
     */
    fun getTimestamps(animeId: Int, episodeNumber: Int): SavedTimestamp? {
        if (animeId <= 0) return null
        val timestamps = getSavedTimestampsMap()
        val key = "${animeId}_$episodeNumber"
        return timestamps[key]
    }

    /**
     * Get timestamps by anime name (fallback when ID not available)
     */
    fun getTimestampsByName(animeName: String, episodeNumber: Int): SavedTimestamp? {
        val timestamps = getSavedTimestampsMap()
        val key = "${animeName}_$episodeNumber"
        return timestamps[key]
    }

    /**
     * Get all saved timestamps
     */
    private fun getSavedTimestampsMap(): Map<String, SavedTimestamp> {
        val stored = prefs.getString(KEY_TIMESTAMPS, null) ?: return emptyMap()
        return try {
            val map = json.decodeFromString<SerializableMap>(stored)
            map.timestamps
        } catch (e: Exception) {
            Log.e(TAG, "Error loading timestamps", e)
            emptyMap()
        }
    }

    /**
     * Delete timestamps for an anime
     */
    fun deleteTimestamps(animeId: Int, episodeNumber: Int) {
        val timestamps = getSavedTimestampsMap().toMutableMap()
        val key = "${animeId}_$episodeNumber"
        timestamps.remove(key)

        prefs.edit {
            putString(
                KEY_TIMESTAMPS, json.encodeToString(
                    SerializableMap(timestamps)
                )
            )
        }
    }

    /**
     * Delete timestamps by anime name
     */
    fun deleteTimestampsByName(animeName: String, episodeNumber: Int) {
        val timestamps = getSavedTimestampsMap().toMutableMap()
        val key = "${animeName}_$episodeNumber"
        timestamps.remove(key)

        prefs.edit {
            putString(
                KEY_TIMESTAMPS, json.encodeToString(
                    SerializableMap(timestamps)
                )
            )
        }
    }

    /**
     * Clear all saved timestamps
     */
    fun clearAll() {
        prefs.edit { remove(KEY_TIMESTAMPS) }
    }
}

@Serializable
data class SavedTimestamp(
    val animeId: Int,
    val animeName: String,
    val episodeNumber: Int,
    val introStart: Long?,    // Seconds
    val introEnd: Long?,      // Seconds
    val creditsStart: Long?,  // Seconds
    val timestamp: Long       // When it was saved
)

@Serializable
data class SerializableMap(
    val timestamps: Map<String, SavedTimestamp>
)
