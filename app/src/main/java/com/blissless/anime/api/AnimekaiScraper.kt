package com.blissless.anime.api

import com.blissless.anime.data.models.QualityOption
import com.blissless.anime.data.models.ServerInfo
import com.blissless.anime.data.models.EpisodeStreams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class AnimekaiStreamResult(
    val url: String,
    val isDirectStream: Boolean = true,
    val headers: Map<String, String>?,
    val subtitleUrl: String? = null,
    val serverName: String = "Animekai",
    val category: String = "sub",
    val qualities: List<QualityOption> = emptyList(),
    val introStart: Int? = null,
    val introEnd: Int? = null,
    val outroStart: Int? = null,
    val outroEnd: Int? = null
)

data class AnimekaiServerInfo(
    val name: String,
    val url: String = "",
    val type: String = "sub",
    val qualities: List<QualityOption> = emptyList()
)

data class AnimekaiEpisodeStreams(
    val subServers: List<AnimekaiServerInfo>,
    val dubServers: List<AnimekaiServerInfo>,
    val animeId: String,
    val episodeId: String
)

object AnimekaiScraper {
    private const val TAG = "AnimekaiScraper"
    private const val BASE = "https://anikai.to"

    private const val SEARCH_URL = "$BASE/ajax/anime/search"
    private const val EPISODES_URL = "$BASE/ajax/episodes/list"
    private const val SERVERS_URL = "$BASE/ajax/links/list"
    private const val SOURCE_URL = "$BASE/ajax/links/view"

    private const val ENC_URL = "https://enc-dec.app/api/enc-kai"
    private const val DEC_KAI_URL = "https://enc-dec.app/api/dec-kai"
    private const val DEC_MEGA_URL = "https://enc-dec.app/api/dec-mega"

    // Timestamp validation constants
    private const val MAX_INTRO_DURATION = 180
    private const val MAX_OUTRO_DURATION = 180
    private const val MIN_INTRO_DURATION = 10
    private const val MIN_OUTRO_DURATION = 10

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Referer" to "$BASE/"
    )

    private val AJAX_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Referer" to "$BASE/",
        "X-Requested-With" to "XMLHttpRequest"
    )

    private var cachedEpisodeInfo: AnimekaiEpisodeStreams? = null
    private var cachedAnimeName: String? = null
    private var cachedEpisodeNumber: Int? = null

    private suspend fun <T> retry(retries: Int = 3, block: suspend () -> T?): T? {
        var attempt = 0
        while (attempt < retries) {
            try {
                return block()
            } catch (e: Exception) {
                attempt++
                if (attempt >= retries) return null
                delay(500L * attempt)
            }
        }
        return null
    }

    private suspend fun encode(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$ENC_URL?text=${URLEncoder.encode(text, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", HEADERS["User-Agent"]!!)
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null
            val data = JSONObject(response.body.string())
            data.optString("result").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun decodeKai(text: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().put("text", text).toString()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(DEC_KAI_URL)
                .post(requestBody)
                .header("User-Agent", HEADERS["User-Agent"]!!)
                .header("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null
            val data = JSONObject(response.body.string())
            val result = data.opt("result")
            when (result) {
                is String -> JSONObject(result)
                is JSONObject -> result
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun decodeMega(text: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject()
                .put("text", text)
                .put("agent", HEADERS["User-Agent"]!!)
                .toString()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(DEC_MEGA_URL)
                .post(requestBody)
                .header("User-Agent", HEADERS["User-Agent"]!!)
                .header("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null
            val data = JSONObject(response.body.string())
            val result = data.opt("result")
            when (result) {
                is String -> JSONObject(result)
                is JSONObject -> result
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalize title for comparison
     */
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace("'", "")
            .replace("′", "")
            .replace("!", "")
            .replace("?", "")
            .replace(",", "")
            .replace(":", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Arc/Season identification map
     * Maps arc keywords to season numbers for matching
     */
    private val ARC_SEASON_MAP = mapOf(
        // Jujutsu Kaisen
        "culling game" to 3,
        "culling" to 3,
        "shibuya incident" to 2,
        "shibuya" to 2,
        "hidden inventory" to 2,
        "kaigyoku gyokusetsu" to 2,

        // Generic season patterns
        "season 3" to 3,
        "season 2" to 2,
        "season 1" to 1,
        "3rd season" to 3,
        "2nd season" to 2,
        "1st season" to 1,
        "third season" to 3,
        "second season" to 2,
        "first season" to 1,
        "s3" to 3,
        "s2" to 2,
        "s1" to 1,
        "part 3" to 3,
        "part 2" to 2,
        "part 1" to 1,
        "cour 3" to 3,
        "cour 2" to 2,
        "cour 1" to 1,

        // Ordinal patterns
        "3rd" to 3,
        "2nd" to 2,
        "1st" to 1,
    )

    /**
     * Extract season number from title based on arc keywords
     */
    private fun extractSeasonFromTitle(title: String): Int? {
        val lower = title.lowercase()

        // Check arc/season keywords
        for ((keyword, season) in ARC_SEASON_MAP) {
            if (lower.contains(keyword)) {
                return season
            }
        }

        // Check ordinal patterns like "2nd Season", "3rd Season"
        val ordinalMatch = Regex("""(\d+)(?:st|nd|rd|th)\s*(?:season|part|cour)""").find(lower)
        if (ordinalMatch != null) {
            return ordinalMatch.groupValues[1].toIntOrNull()
        }

        // Check for standalone number suffix like "II", "III"
        val romanMatch = Regex("""\s+(II|III|IV|V|VI|VII|VIII)\s*$""").find(lower)
        if (romanMatch != null) {
            return when (romanMatch.groupValues[1]) {
                "II" -> 2
                "III" -> 3
                "IV" -> 4
                "V" -> 5
                "VI" -> 6
                "VII" -> 7
                "VIII" -> 8
                else -> null
            }
        }

        return null
    }

    /**
     * Get the arc keywords present in a title
     */
    private fun getArcKeywords(title: String): Set<String> {
        val lower = title.lowercase()
        val keywords = mutableSetOf<String>()

        for ((keyword, _) in ARC_SEASON_MAP) {
            if (lower.contains(keyword)) {
                keywords.add(keyword)
            }
        }

        return keywords
    }

    /**
     * Calculate match score between search query and result title
     */
    private fun calculateMatchScore(searchTitle: String, resultTitle: String): Int {
        val normalizedSearch = normalizeTitle(searchTitle)
        val normalizedResult = normalizeTitle(resultTitle)

        var score = 0

        // Exact match = perfect score
        if (normalizedSearch == normalizedResult) {
            return 1000
        }

        // Extract season info
        val searchSeason = extractSeasonFromTitle(searchTitle)
        val resultSeason = extractSeasonFromTitle(resultTitle)

        // Get arc keywords
        val searchArcKeywords = getArcKeywords(searchTitle)
        val resultArcKeywords = getArcKeywords(resultTitle)


        // CRITICAL: If search has specific arc/season and result doesn't match, HEAVY penalty
        if (searchSeason != null) {
            if (resultSeason == null) {
                // Result has no season info, searching for specific season - BIG penalty
                score -= 400
            } else if (searchSeason != resultSeason) {
                // Season mismatch - HEAVY penalty
                score -= 500
            } else {
                // Season matches - GOOD bonus
                score += 300
            }
        }

        // Check for matching arc keywords (like "culling game")
        val matchingArcKeywords = searchArcKeywords.intersect(resultArcKeywords)
        val missingArcKeywords = searchArcKeywords.subtract(resultArcKeywords)

        if (matchingArcKeywords.isNotEmpty()) {
            score += matchingArcKeywords.size * 150
        }

        if (missingArcKeywords.isNotEmpty()) {
            score -= missingArcKeywords.size * 200
        }

        // Word overlap scoring
        val searchWords = normalizedSearch.split(" ").filter { it.length > 2 }
        val resultWords = normalizedResult.split(" ").filter { it.length > 2 }

        var matchingWords = 0
        for (word in searchWords) {
            if (resultWords.any { it == word || it.contains(word) || word.contains(it) }) {
                matchingWords++
            }
        }

        if (matchingWords > 0) {
            val wordScore = matchingWords * 15
            score += wordScore
        }

        // Substring match
        if (normalizedResult.contains(normalizedSearch) || normalizedSearch.contains(normalizedResult)) {
            score += 25
        }

        // Prefer result that is closer in length (more specific match)
        val lengthDiff = kotlin.math.abs(normalizedSearch.length - normalizedResult.length)
        score -= lengthDiff / 10

        return score
    }

    /**
     * Validate intro timestamps
     */
    private fun isValidIntro(start: Int?, end: Int?, episodeLength: Int? = null): Boolean {
        if (start == null || end == null) return false
        if (start < 0 || end <= start) return false

        val duration = end - start
        if (duration < MIN_INTRO_DURATION || duration > MAX_INTRO_DURATION) {
            return false
        }

        if (episodeLength != null && start > episodeLength * 0.3) {
            return false
        }

        return true
    }

    /**
     * Validate outro timestamps
     */
    private fun isValidOutro(start: Int?, end: Int?, episodeLength: Int? = null): Boolean {
        if (start == null || end == null) return false
        if (start < 0 || end <= start) return false

        val duration = end - start
        if (duration < MIN_OUTRO_DURATION || duration > MAX_OUTRO_DURATION) {
            return false
        }

        if (episodeLength != null && start < episodeLength * 0.6) {
            return false
        }

        return true
    }

    private data class StreamResolveResult(
        val url: String?,
        val qualities: List<QualityOption>,
        val introStart: Int?,
        val introEnd: Int?,
        val outroStart: Int?,
        val outroEnd: Int?,
        val embedOrigin: String
    )

    suspend fun searchAnime(keyword: String): List<Map<String, String>>? = withContext(Dispatchers.IO) {
        retry {
            val url = "$SEARCH_URL?keyword=${URLEncoder.encode(keyword, "UTF-8")}"

            val request = Request.Builder()
                .url(url)
                .headers(Headers.Builder().apply {
                    AJAX_HEADERS.forEach { (k, v) -> add(k, v) }
                }.build())
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@retry null

            val jsonData = JSONObject(response.body.string())
            val html = jsonData.getJSONObject("result").optString("html")
            if (html.isEmpty()) return@retry null

            val results = mutableListOf<Map<String, String>>()
            val aitemRegex = Regex("""<a[^>]*class="[^"]*aitem[^"]*"[^>]*href="([^"]*)"[^>]*>""")
            val titleRegex = Regex("""<h6[^>]*class="[^"]*title[^"]*"[^>]*>([^<]*)</h6>""")

            aitemRegex.findAll(html).forEach { match ->
                val href = match.groupValues[1]
                val startIndex = match.range.first
                val afterAnchor = html.substring(startIndex)

                val titleMatch = titleRegex.find(afterAnchor)
                if (titleMatch != null) {
                    val title = titleMatch.groupValues[1].trim()
                    val slug = href.replace("/watch/", "")
                    results.add(mapOf("title" to title, "slug" to slug))
                }
            }

            if (results.isEmpty()) return@retry null
            results
        }
    }

    private suspend fun getAnimeId(slug: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/watch/$slug"
            val request = Request.Builder()
                .url(url)
                .headers(Headers.Builder().apply {
                    HEADERS.forEach { (k, v) -> add(k, v) }
                }.build())
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null

            val html = response.body.string()
            val syncDataRegex = Regex("""<script[^>]*id="syncData"[^>]*>([^<]*)</script>""")
            val match = syncDataRegex.find(html)

            if (match != null) {
                val dataStr = match.groupValues[1]
                val json = JSONObject(dataStr)
                json.optString("anime_id").takeIf { it.isNotEmpty() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getEpisodes(aniId: String): List<Map<String, Any>>? = withContext(Dispatchers.IO) {
        retry {
            val token = encode(aniId) ?: return@retry null
            val url = "$EPISODES_URL?ani_id=$aniId&_=$token"

            val request = Request.Builder()
                .url(url)
                .headers(Headers.Builder().apply {
                    AJAX_HEADERS.forEach { (k, v) -> add(k, v) }
                }.build())
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@retry null

            val html = JSONObject(response.body.string()).optString("result")
            if (html.isEmpty()) return@retry null

            val episodes = mutableListOf<Map<String, Any>>()
            val epRegex = Regex("""<a[^>]*num=["'](\d+)["'][^>]*token=["']([^"']+)["'][^>]*>""")

            epRegex.findAll(html).forEach { match ->
                val num = match.groupValues[1].toIntOrNull() ?: return@forEach
                val epToken = match.groupValues[2]
                episodes.add(mapOf("number" to num, "token" to epToken))
            }

            episodes
        }
    }

    suspend fun getServers(epToken: String): Map<String, List<AnimekaiServerInfo>>? = withContext(Dispatchers.IO) {
        retry {

            val token = encode(epToken) ?: return@retry null
            val url = "$SERVERS_URL?token=$epToken&_=$token"

            val request = Request.Builder()
                .url(url)
                .headers(Headers.Builder().apply {
                    AJAX_HEADERS.forEach { (k, v) -> add(k, v) }
                }.build())
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@retry null

            val html = JSONObject(response.body.string()).optString("result")
            if (html.isEmpty()) return@retry null

            val subServers = mutableListOf<AnimekaiServerInfo>()
            val dubServers = mutableListOf<AnimekaiServerInfo>()

            val serverGroupRegex = Regex("""<div[^>]*class="[^"]*server-items[^"]*"[^>]*data-id="([^"]+)"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

            serverGroupRegex.findAll(html).forEach { groupMatch ->
                val rawLang = groupMatch.groupValues[1].lowercase()
                val groupHtml = groupMatch.groupValues[2]

                val lang = when (rawLang) {
                    "sub", "ja", "jpn", "japanese" -> "sub"
                    "dub", "en", "eng", "english" -> "dub"
                    else -> rawLang
                }

                val serverRegex = Regex("""<span[^>]*class="[^"]*server[^"]*"[^>]*data-lid="([^"]+)"[^>]*>([^<]*)</span>""")
                serverRegex.findAll(groupHtml).forEach { serverMatch ->
                    val lid = serverMatch.groupValues[1]
                    val serverName = serverMatch.groupValues[2].trim().ifEmpty { "Server" }

                    val serverInfo = AnimekaiServerInfo(
                        name = "$serverName ($lang)",
                        url = lid,
                        type = lang
                    )

                    if (lang == "sub") {
                        subServers.add(serverInfo)
                    } else if (lang == "dub") {
                        dubServers.add(serverInfo)
                    }
                }
            }

            if (subServers.isEmpty() && dubServers.isEmpty()) {
                val altRegex = Regex("""data-(?:id|type|lang)="([^"]+)"[^>]*>.*?<span[^>]*class="[^"]*server[^"]*"[^>]*data-lid="([^"]+)"[^>]*>([^<]*)</span>""", RegexOption.DOT_MATCHES_ALL)
                altRegex.findAll(html).forEach { match ->
                    val rawLang = match.groupValues[1].lowercase()
                    val lid = match.groupValues[2]
                    val serverName = match.groupValues[3].trim().ifEmpty { "Server" }

                    val lang = when (rawLang) {
                        "sub", "ja", "jpn", "japanese" -> "sub"
                        "dub", "en", "eng", "english" -> "dub"
                        else -> rawLang
                    }

                    val serverInfo = AnimekaiServerInfo(name = "$serverName ($lang)", url = lid, type = lang)

                    if (lang == "sub" && subServers.none { it.url == lid }) {
                        subServers.add(serverInfo)
                    } else if (lang == "dub" && dubServers.none { it.url == lid }) {
                        dubServers.add(serverInfo)
                    }
                }
            }


            if (subServers.isEmpty() && dubServers.isEmpty()) return@retry null

            mapOf("sub" to subServers, "dub" to dubServers)
        }
    }

    private suspend fun getAllQualityStreams(masterUrl: String, headers: Map<String, String>): List<QualityOption> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(masterUrl)
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body.string()
            val lines = body.split("\n")
            val baseUrl = masterUrl.substringBeforeLast("/") + "/"

            val qualities = mutableListOf<QualityOption>()

            for (i in lines.indices) {
                val line = lines[i]
                if (line.contains("RESOLUTION=")) {
                    val resolutionMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
                    if (resolutionMatch != null && i + 1 < lines.size) {
                        val width = resolutionMatch.groupValues[1].toIntOrNull() ?: 0
                        val height = resolutionMatch.groupValues[2].toIntOrNull() ?: 0
                        val qualityName = "${height}p"
                        val streamUrl = if (lines[i + 1].trim().startsWith("http")) {
                            lines[i + 1].trim()
                        } else {
                            baseUrl + lines[i + 1].trim()
                        }

                        qualities.add(QualityOption(quality = qualityName, url = streamUrl, width = width))
                    }
                }
            }

            qualities.sortByDescending { it.width }
            qualities
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveStreamWithTimestamps(linkId: String, serverName: String = "", episodeLength: Int? = null): StreamResolveResult? = withContext(Dispatchers.IO) {
        var lastError: String? = null
        retry {

            val token = encode(linkId)
            if (token == null) {
                lastError = "Failed to encode linkId"
                return@retry null
            }

            val url = "$SOURCE_URL?id=$linkId&_=$token"

            val request = Request.Builder()
                .url(url)
                .headers(Headers.Builder().apply {
                    AJAX_HEADERS.forEach { (k, v) -> add(k, v) }
                }.build())
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                lastError = "Source request failed: ${response.code}"
                return@retry null
            }

            val responseBody = response.body.string()
            val encrypted = JSONObject(responseBody).optString("result")
            if (encrypted.isEmpty()) {
                lastError = "No result in source response"
                return@retry null
            }

            val embed = decodeKai(encrypted)
            if (embed == null) {
                lastError = "Failed to decode embed"
                return@retry null
            }

            val embedUrl = embed.optString("url")
            if (embedUrl.isEmpty()) {
                lastError = "No embed URL in decoded response"
                return@retry null
            }

            // Extract skip timestamps from embed
            val skip = embed.optJSONObject("skip")
            val intro = skip?.optJSONArray("intro")
            val outro = skip?.optJSONArray("outro")

            val rawIntroStart = intro?.optInt(0)
            val rawIntroEnd = intro?.optInt(1)
            val rawOutroStart = outro?.optInt(0)
            val rawOutroEnd = outro?.optInt(1)

            // VALIDATE timestamps before using
            val introStart = if (isValidIntro(rawIntroStart, rawIntroEnd, episodeLength)) rawIntroStart else null
            val introEnd = if (isValidIntro(rawIntroStart, rawIntroEnd, episodeLength)) rawIntroEnd else null
            val outroStart = if (isValidOutro(rawOutroStart, rawOutroEnd, episodeLength)) rawOutroStart else null
            val outroEnd = if (isValidOutro(rawOutroStart, rawOutroEnd, episodeLength)) rawOutroEnd else null

            if (rawIntroStart != null || rawOutroStart != null) {
            }

            // Extract embed origin
            val embedOrigin = try {
                val uri = java.net.URI(embedUrl)
                "${uri.scheme}://${uri.host}"
            } catch (e: Exception) {
                "https://4spromax.site"
            }

            val videoId = embedUrl.substringAfterLast("/")
            val baseUrl = embedUrl.substringBefore("/e/")

            val mediaUrl = "$baseUrl/media/$videoId"
            val mediaRequest = Request.Builder()
                .url(mediaUrl)
                .headers(Headers.Builder().apply {
                    HEADERS.forEach { (k, v) -> add(k, v) }
                    add("Referer", embedUrl)
                }.build())
                .build()
            val mediaResponse = client.newCall(mediaRequest).execute()

            if (!mediaResponse.isSuccessful) return@retry null

            val mediaResult = JSONObject(mediaResponse.body.string()).optString("result")
            val final = decodeMega(mediaResult) ?: return@retry null

            val sources = final.optJSONArray("sources")
            if (sources == null || sources.length() == 0) return@retry null

            val masterUrl = sources.getJSONObject(0).optString("file")
            if (masterUrl.isEmpty()) return@retry null


            val streamHeaders = mapOf(
                "User-Agent" to HEADERS["User-Agent"]!!,
                "Referer" to embedOrigin,
                "Accept" to "*/*"
            )

            val qualities = getAllQualityStreams(masterUrl, streamHeaders)
            val bestUrl = qualities.firstOrNull()?.url ?: masterUrl

            StreamResolveResult(
                url = bestUrl,
                qualities = qualities,
                introStart = introStart,
                introEnd = introEnd,
                outroStart = outroStart,
                outroEnd = outroEnd,
                embedOrigin = embedOrigin
            )
        }
    }

    suspend fun getEpisodeInfo(animeName: String, episodeNumber: Int): AnimekaiEpisodeStreams? = withContext(Dispatchers.IO) {

        if (cachedEpisodeInfo != null && cachedAnimeName == animeName && cachedEpisodeNumber == episodeNumber) {
            return@withContext cachedEpisodeInfo
        }

        val searchResults = searchAnime(animeName)
        if (searchResults.isNullOrEmpty()) {
            return@withContext null
        }

        // Calculate match scores for all results
        data class ScoredResult(
            val title: String,
            val slug: String,
            val score: Int
        )

        val scoredResults = searchResults.mapNotNull { result ->
            val title = result["title"] ?: return@mapNotNull null
            val slug = result["slug"] ?: return@mapNotNull null
            val score = calculateMatchScore(animeName, title)
            ScoredResult(title, slug, score)
        }.sortedByDescending { it.score }

        scoredResults.take(5).forEachIndexed { index, sr ->
        }

        // Pick the best match
        val bestMatch = scoredResults.firstOrNull()
        if (bestMatch == null) {
            return@withContext null
        }

        val slug = bestMatch.slug

        val animeId = getAnimeId(slug) ?: return@withContext null
        val episodes = getEpisodes(animeId)
        if (episodes.isNullOrEmpty()) return@withContext null

        var epToken: String? = null
        for (ep in episodes) {
            val num = ep["number"] as? Int
            val token = ep["token"] as? String
            if (num == episodeNumber && token != null) {
                epToken = token
                break
            }
        }

        if (epToken == null) {
            return@withContext null
        }

        val servers = getServers(epToken) ?: return@withContext null

        @Suppress("UNCHECKED_CAST")
        val subServers = (servers["sub"] as? List<AnimekaiServerInfo>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val dubServers = (servers["dub"] as? List<AnimekaiServerInfo>) ?: emptyList()

        val result = AnimekaiEpisodeStreams(
            subServers = subServers,
            dubServers = dubServers,
            animeId = animeId,
            episodeId = epToken
        )

        cachedEpisodeInfo = result
        cachedAnimeName = animeName
        cachedEpisodeNumber = episodeNumber

        result
    }

    suspend fun getStreamWithFallback(
        animeName: String,
        episodeNumber: Int,
        preferredCategory: String = "sub"
    ): AnimekaiStreamResult? = withContext(Dispatchers.IO) {

        val episodeInfo = getEpisodeInfo(animeName, episodeNumber)
        if (episodeInfo == null) {
            return@withContext null
        }

        val preferredServers = if (preferredCategory == "dub") episodeInfo.dubServers else episodeInfo.subServers
        val fallbackServers = if (preferredCategory == "dub") episodeInfo.subServers else episodeInfo.dubServers
        val fallbackCategory = if (preferredCategory == "dub") "sub" else "dub"


        // Try ALL preferred servers
        for ((index, server) in preferredServers.withIndex()) {

            val result = resolveStreamWithTimestamps(server.url, server.name)
            if (result != null && result.url != null) {
                return@withContext AnimekaiStreamResult(
                    url = result.url!!,
                    headers = HEADERS + ("Referer" to result.embedOrigin),
                    serverName = server.name,
                    category = preferredCategory,
                    qualities = result.qualities,
                    introStart = result.introStart,
                    introEnd = result.introEnd,
                    outroStart = result.outroStart,
                    outroEnd = result.outroEnd
                )
            }
        }

        // Try ALL fallback servers
        for ((index, server) in fallbackServers.withIndex()) {

            val result = resolveStreamWithTimestamps(server.url, server.name)
            if (result != null && result.url != null) {
                return@withContext AnimekaiStreamResult(
                    url = result.url!!,
                    headers = HEADERS + ("Referer" to result.embedOrigin),
                    serverName = server.name,
                    category = fallbackCategory,
                    qualities = result.qualities,
                    introStart = result.introStart,
                    introEnd = result.introEnd,
                    outroStart = result.outroStart,
                    outroEnd = result.outroEnd
                )
            }
        }

        null
    }

    suspend fun getStreamForCategory(
        animeName: String,
        episodeNumber: Int,
        category: String
    ): AnimekaiStreamResult? {
        return getStreamWithFallback(animeName, episodeNumber, category)
    }

    /**
     * Get stream for a specific server by its linkId.
     * This is used when user manually selects a different server.
     */
    suspend fun getStreamForSpecificServer(
        animeName: String,
        episodeNumber: Int,
        serverName: String,
        category: String
    ): AnimekaiStreamResult? = withContext(Dispatchers.IO) {

        // Get episode info to find the specific server
        val episodeInfo = getEpisodeInfo(animeName, episodeNumber)
        if (episodeInfo == null) {
            return@withContext null
        }

        // Find the specific server
        val servers = if (category == "dub") episodeInfo.dubServers else episodeInfo.subServers
        val server = servers.find { it.name == serverName }

        if (server == null) {
            // Try fallback category
            val fallbackServers = if (category == "dub") episodeInfo.subServers else episodeInfo.dubServers
            val fallbackServer = fallbackServers.find { it.name == serverName }
            if (fallbackServer != null) {
                val result = resolveStreamWithTimestamps(fallbackServer.url, fallbackServer.name)
                if (result != null && result.url != null) {
                    val actualCategory = if (category == "dub") "sub" else "dub"
                    return@withContext AnimekaiStreamResult(
                        url = result.url!!,
                        headers = HEADERS + ("Referer" to result.embedOrigin),
                        serverName = fallbackServer.name,
                        category = actualCategory,
                        qualities = result.qualities,
                        introStart = result.introStart,
                        introEnd = result.introEnd,
                        outroStart = result.outroStart,
                        outroEnd = result.outroEnd
                    )
                }
            }
            return@withContext null
        }

        val result = resolveStreamWithTimestamps(server.url, server.name)
        if (result != null && result.url != null) {
            return@withContext AnimekaiStreamResult(
                url = result.url!!,
                headers = HEADERS + ("Referer" to result.embedOrigin),
                serverName = server.name,
                category = category,
                qualities = result.qualities,
                introStart = result.introStart,
                introEnd = result.introEnd,
                outroStart = result.outroStart,
                outroEnd = result.outroEnd
            )
        }

        null
    }

    fun toAniwatchStreamResult(result: AnimekaiStreamResult): com.blissless.anime.data.models.AniwatchStreamResult {
        return com.blissless.anime.data.models.AniwatchStreamResult(
            url = result.url,
            isDirectStream = result.isDirectStream,
            headers = result.headers,
            subtitleUrl = result.subtitleUrl,
            serverName = result.serverName,
            category = result.category,
            qualities = result.qualities,
            introStart = result.introStart,
            introEnd = result.introEnd,
            outroStart = result.outroStart,
            outroEnd = result.outroEnd
        )
    }

    fun toEpisodeStreams(result: AnimekaiEpisodeStreams): EpisodeStreams {
        return EpisodeStreams(
            subServers = result.subServers.map { server ->
                ServerInfo(name = server.name, url = server.url, qualities = server.qualities)
            },
            dubServers = result.dubServers.map { server ->
                ServerInfo(name = server.name, url = server.url, qualities = server.qualities)
            },
            animeId = result.animeId,
            episodeId = result.episodeId
        )
    }
}
