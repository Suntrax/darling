package com.blissless.anime.data

import android.content.Context
import com.blissless.anime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class JikanUserFavorites(
    val anime: List<JikanFavoriteAnime>,
    val manga: List<JikanFavoriteManga>,
    val characters: List<JikanFavoriteCharacter>
)

data class JikanFavoriteAnime(
    val malId: Int,
    val title: String,
    val images: JikanImages
)

data class JikanFavoriteManga(
    val malId: Int,
    val title: String,
    val images: JikanImages
)

data class JikanFavoriteCharacter(
    val malId: Int,
    val name: String,
    val images: JikanImages
)

data class JikanImages(
    val jpg: JikanImageUrls?
)

data class JikanImageUrls(
    val imageUrl: String?
)

data class JikanUserHistory(
    val anime: List<JikanHistoryEntry>,
    val manga: List<JikanHistoryEntry>
)

data class JikanHistoryEntry(
    val malId: Int,
    val title: String,
    val images: JikanImages,
    val episodesWatched: Int?,
    val chaptersRead: Int?,
    val increment: String?
)

class JikanService(private val context: Context) {
    
    companion object {
        private const val JIKAN_API_BASE = "https://api.jikan.moe/v4"
        private const val TIMEOUT_MS = 15000
    }
    
    suspend fun searchAnimeByTitle(title: String): Int? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val url = URL("$JIKAN_API_BASE/anime?q=$encodedTitle&limit=1&sfw=true")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                
                val malIdMatch = Regex("\"mal_id\"\\s*:\\s*(\\d+)").find(response)
                malIdMatch?.groupValues?.get(1)?.toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("JIKAN_SEARCH", "Error searching anime: ${e.message}")
            null
        }
    }
    
    suspend fun getUserFavorites(username: String): JikanUserFavorites? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("JIKAN_DEBUG", "Fetching favorites for user: $username")
            val url = URL("$JIKAN_API_BASE/users/$username/favorites")
            android.util.Log.d("JIKAN_DEBUG", "Favorites URL: $url")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            
            val responseCode = conn.responseCode
            android.util.Log.d("JIKAN_DEBUG", "Favorites response code: $responseCode")
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                android.util.Log.d("JIKAN_DEBUG", "Favorites response length: ${response.length}")
                val result = parseUserFavorites(response)
                android.util.Log.d("JIKAN_DEBUG", "Parsed favorites: ${result.anime.size} anime, ${result.manga.size} manga, ${result.characters.size} characters")
                result
            } else {
                android.util.Log.e("JIKAN_DEBUG", "Failed to fetch favorites: $responseCode")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("JIKAN_DEBUG", "Error fetching favorites: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun parseUserFavorites(jsonStr: String): JikanUserFavorites {
        val anime = mutableListOf<JikanFavoriteAnime>()
        val manga = mutableListOf<JikanFavoriteManga>()
        val characters = mutableListOf<JikanFavoriteCharacter>()
        
        try {
            val animeArrayMatch = Regex("\"anime\"\\s*:\\s*\\[([\\s\\S]*?)]\\s*,\"manga\"").find(jsonStr)
            animeArrayMatch?.let { match ->
                val animeContent = match.groupValues[1]
                parseFavoriteEntries(animeContent, "anime").forEach { anime.add(it as JikanFavoriteAnime) }
            }
            
            val mangaArrayMatch = Regex("\"manga\"\\s*:\\s*\\[([\\s\\S]*?)]\\s*,\"characters\"").find(jsonStr)
            mangaArrayMatch?.let { match ->
                val mangaContent = match.groupValues[1]
                parseFavoriteEntries(mangaContent, "manga").forEach { manga.add(it as JikanFavoriteManga) }
            }
            
            val charactersArrayMatch = Regex("\"characters\"\\s*:\\s*\\[([\\s\\S]*?)]\\s*,\"").find(jsonStr)
            charactersArrayMatch?.let { match ->
                val charactersContent = match.groupValues[1]
                parseFavoriteEntries(charactersContent, "character").forEach { characters.add(it as JikanFavoriteCharacter) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return JikanUserFavorites(anime, manga, characters)
    }
    
    private fun parseFavoriteEntries(content: String, type: String): List<Any> {
        val entries = mutableListOf<Any>()
        
        val entryRegex = Regex("\\{\"mal_id\":\\s*(\\d+)")
        val entriesFound = entryRegex.findAll(content).map { it.range.first to it.range.last }.toList()
        
        for (i in entriesFound.indices) {
            val (start) = entriesFound[i]
            val end = entriesFound.getOrNull(i + 1)?.first ?: content.length
            
            val entryContent = content.substring(start, end)
            
            val idMatch = Regex("\"mal_id\"\\s*:\\s*(\\d+)").find(entryContent)
            val id = idMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
            
            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(entryContent)
            val title = titleMatch?.groupValues?.get(1) ?: continue
            
            val imageMatch = Regex("\"image_url\"\\s*:\\s*\"([^\"]*)\"").find(entryContent)
            val imageUrl = imageMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() && it != "null" }
            
            val images = JikanImages(jpg = JikanImageUrls(imageUrl = imageUrl))
            
            when (type) {
                "anime" -> {
                    entries.add(JikanFavoriteAnime(malId = id, title = title, images = images))
                }
                "manga" -> entries.add(JikanFavoriteManga(malId = id, title = title, images = images))
                "character" -> entries.add(JikanFavoriteCharacter(malId = id, name = title, images = images))
            }
        }
        
        return entries
    }
    
    private fun parseHistoryDataEntries(content: String): List<Pair<JikanHistoryEntry, String>> {
        val entries = mutableListOf<Pair<JikanHistoryEntry, String>>()
        
        android.util.Log.d("JIKAN_DEBUG", "parseHistoryDataEntries called, content length=${content.length}")
        
        val entryRegex = Regex("\\{\"entry\":\\s*\\{")
        val entryStarts = entryRegex.findAll(content).toList()
        android.util.Log.d("JIKAN_DEBUG", "Found ${entryStarts.size} entries")
        
        for (i in entryStarts.indices) {
            val start = entryStarts[i].range.first
            val end = if (i + 1 < entryStarts.size) entryStarts[i + 1].range.first else content.length
            
            val entryBlock = content.substring(start, end)
            
            val malIdMatch = Regex("\"mal_id\"\\s*:\\s*(\\d+)").find(entryBlock)
            val id = malIdMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
            
            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(entryBlock)
            val title = titleMatch?.groupValues?.get(1) ?: "Unknown"
            
            val imageMatch = Regex("\"image_url\"\\s*:\\s*\"([^\"]*)\"").find(entryBlock)
            val imageUrl = imageMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() && it != "null" }
            
            val incrementMatch = Regex("\"increment\"\\s*:\\s*\"([^\"]+)\"").find(entryBlock)
            val increment = incrementMatch?.groupValues?.get(1)
            
            val type = if (entryBlock.contains("\"episodes_watched\"")) "anime" else "manga"
            
            val episodesMatch = Regex("\"episodes_watched\"\\s*:\\s*(\\d+)").find(entryBlock)
            val episodes = episodesMatch?.groupValues?.get(1)?.toIntOrNull()
            
            val chaptersMatch = Regex("\"chapters_read\"\\s*:\\s*(\\d+)").find(entryBlock)
            val chapters = chaptersMatch?.groupValues?.get(1)?.toIntOrNull()
            
            val images = JikanImages(jpg = JikanImageUrls(imageUrl = imageUrl))
            
            val entry = JikanHistoryEntry(
                malId = id,
                title = title,
                images = images,
                episodesWatched = episodes,
                chaptersRead = chapters,
                increment = increment
            )
            
            entries.add(Pair(entry, type))
        }
        
        return entries
    }
    
    suspend fun getUserHistory(username: String): JikanUserHistory? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("JIKAN_DEBUG", "Fetching history for user: $username")
            val url = URL("$JIKAN_API_BASE/users/$username/history")
            android.util.Log.d("JIKAN_DEBUG", "History URL: $url")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            
            val responseCode = conn.responseCode
            android.util.Log.d("JIKAN_DEBUG", "History response code: $responseCode")
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                android.util.Log.d("JIKAN_DEBUG", "History response length: ${response.length}, first 500 chars: ${response.take(500)}")
                val result = parseUserHistory(response)
                android.util.Log.d("JIKAN_DEBUG", "Parsed history: ${result.anime.size} anime entries, ${result.manga.size} manga entries")
                result
            } else {
                android.util.Log.e("JIKAN_DEBUG", "Failed to fetch history: $responseCode")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("JIKAN_DEBUG", "Error fetching history: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun parseUserHistory(jsonStr: String): JikanUserHistory {
        val animeHistory = mutableListOf<JikanHistoryEntry>()
        val mangaHistory = mutableListOf<JikanHistoryEntry>()
        
        try {
            if (jsonStr.contains("\"data\":")) {
                val dataMatch = Regex("\"data\"\\s*:\\s*\\[(.*?)]\\s*\\}", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
                dataMatch?.let { match ->
                    android.util.Log.d("JIKAN_DEBUG", "Found data array format, content length: ${match.groupValues[1].length}")
                    val entries = parseHistoryDataEntries(match.groupValues[1])
                    entries.forEach { entry ->
                        if (entry.second == "anime") {
                            animeHistory.add(entry.first)
                        } else {
                            mangaHistory.add(entry.first)
                        }
                    }
                }
            } else {
                val animeArrayMatch = Regex("\"anime\"\\s*:\\s*\\[(.*?)]\\s*,\"manga\"", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
                animeArrayMatch?.let { match ->
                    android.util.Log.d("JIKAN_DEBUG", "Found anime array, content length: ${match.groupValues[1].length}")
                    animeHistory.addAll(parseHistoryEntries(match.groupValues[1], "anime"))
                } ?: android.util.Log.d("JIKAN_DEBUG", "No anime array found in history response")
                
                val mangaArrayMatch = Regex("\"manga\"\\s*:\\s*\\[(.*?)]\\s*}\\s*$", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
                mangaArrayMatch?.let { match ->
                    mangaHistory.addAll(parseHistoryEntries(match.groupValues[1], "manga"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JIKAN_DEBUG", "Error parsing history: ${e.message}")
            e.printStackTrace()
        }
        
        android.util.Log.d("JIKAN_DEBUG", "Parsed history: ${animeHistory.size} anime, ${mangaHistory.size} manga")
        return JikanUserHistory(anime = animeHistory, manga = mangaHistory)
    }
    
    private fun parseHistoryEntries(content: String, type: String): List<JikanHistoryEntry> {
        val entries = mutableListOf<JikanHistoryEntry>()
        
        android.util.Log.d("JIKAN_DEBUG", "parseHistoryEntries called for type=$type, content length=${content.length}")
        
        val entryRegex = Regex("\\{\"mal_id\":\\s*(\\d+)")
        val entriesFound = entryRegex.findAll(content).toList()
        android.util.Log.d("JIKAN_DEBUG", "Found ${entriesFound.size} mal_id entries")
        
        for (match in entriesFound) {
            val start = match.range.first
            val nextMatch = entriesFound.drop(1).firstOrNull()
            val end = nextMatch?.range?.first ?: content.length
            
            val entryContent = content.substring(start, end)
            
            val idMatch = Regex("\"mal_id\"\\s*:\\s*(\\d+)").find(entryContent)
            val id = idMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
            
            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(entryContent)
            val title = titleMatch?.groupValues?.get(1) ?: "Unknown"
            
            val imageMatch = Regex("\"image_url\"\\s*:\\s*\"([^\"]*)\"").find(entryContent)
            val imageUrl = imageMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() && it != "null" }
            
            val incrementMatch = Regex("\"increment\"\\s*:\\s*\"([^\"]+)\"").find(entryContent)
            val increment = incrementMatch?.groupValues?.get(1)
            
            val images = JikanImages(jpg = JikanImageUrls(imageUrl = imageUrl))
            
            if (type == "anime") {
                val episodesMatch = Regex("\"episodes_watched\"\\s*:\\s*(\\d+)").find(entryContent)
                val episodes = episodesMatch?.groupValues?.get(1)?.toIntOrNull()
                
                entries.add(JikanHistoryEntry(
                    malId = id,
                    title = title,
                    images = images,
                    episodesWatched = episodes,
                    chaptersRead = null,
                    increment = increment
                ))
            } else {
                val chaptersMatch = Regex("\"chapters_read\"\\s*:\\s*(\\d+)").find(entryContent)
                val chapters = chaptersMatch?.groupValues?.get(1)?.toIntOrNull()
                
                entries.add(JikanHistoryEntry(
                    malId = id,
                    title = title,
                    images = images,
                    episodesWatched = null,
                    chaptersRead = chapters,
                    increment = increment
                ))
            }
        }
        
        return entries
    }
}
