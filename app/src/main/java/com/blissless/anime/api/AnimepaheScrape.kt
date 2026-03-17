package com.blissless.anime.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import com.blissless.anime.data.models.AniwatchStreamResult

object AnimepaheScrape {
    private const val TAG = "AnimepaheScrape"
    private const val BASE_URL = "https://animepahe.si"

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // --- CACHES ---
    // Map<NormalizedTitle, SessionString>
    private val animeSessionCache = ConcurrentHashMap<String, String>()
    // Map<SessionString, MutableList<JsonElement>>
    private val episodeListCache = ConcurrentHashMap<String, MutableList<JsonElement>>()
    // Map<EpisodeSessionString, M3u8Url>
    private val streamUrlCache = ConcurrentHashMap<String, String>()

    private fun getApiHeaders(referer: String, cookies: String) = Headers.Builder()
        .add("User-Agent", UA)
        .add("Accept", "application/json, text/javascript, */*; q=0.01")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("Referer", referer)
        .add("Cookie", cookies)
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    private fun normalize(s: String): String = s.lowercase()
        .replace(Regex("[^a-z0-9]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    suspend fun getStream(context: Context, query: String, episodeNumber: Int, englishTitle: String? = null): AniwatchStreamResult? = withContext(Dispatchers.IO) {
        try {

            // 1. Get Cookies
            val cookies = withContext(Dispatchers.Main) {
                fetchCookiesWithWebView(context.applicationContext, BASE_URL)
            }

            if (cookies.isNullOrEmpty()) {
                return@withContext null
            }

            // 2. Search or Retrieve Cached Session
            val normQuery = normalize(query)
            var session = animeSessionCache[normQuery]

            if (session == null) {
                var searchResults = performSearch(query, cookies)
                if ((searchResults == null || searchResults.isEmpty()) && !englishTitle.isNullOrBlank()) {
                    searchResults = performSearch(englishTitle, cookies)
                }

                if (searchResults == null || searchResults.isEmpty()) {
                    return@withContext null
                }

                val match = findBestMatch(searchResults, normQuery, englishTitle?.let { normalize(it) })
                if (match != null) {
                    session = match["session"]?.jsonPrimitive?.content
                    if (session != null) {
                        animeSessionCache[normQuery] = session
                    }
                }
            } else {
            }

            if (session == null) return@withContext null

            // 3. Get Episodes (Cached)
            val episodes = episodeListCache.getOrPut(session) { mutableListOf() }

            fun getEpNum(ep: JsonElement): Int? {
                return ep.jsonObject["episode"]?.jsonPrimitive?.content?.trim()?.toDoubleOrNull()?.roundToInt()
            }

            // Fetch Page 1 if cache is empty
            if (episodes.isEmpty()) {
                fetchAndCacheEpisodes(session, cookies, 1, episodes)
            }

            // Handle absolute episode mapping
            val firstEpObj = episodes.firstOrNull { getEpNum(it)?.let { ep -> ep > 0 } ?: false }
            val firstEpNum = getEpNum(firstEpObj ?: return@withContext null) ?: 1

            val absoluteTarget = if (episodeNumber < firstEpNum && firstEpNum > 1) {
                firstEpNum + episodeNumber - 1
            } else {
                episodeNumber
            }


            // Find Episode
            var episodeMatch = episodes.find { getEpNum(it) == absoluteTarget }
            if (episodeMatch == null && absoluteTarget != episodeNumber) {
                episodeMatch = episodes.find { getEpNum(it) == episodeNumber }
            }

            // If not found, try fetching more pages (basic pagination support)
            // Note: For speed, we rely heavily on the first page for Season detection,
            // but if the specific ep is missing, we might need to fetch the specific page.
            // For now, we stick to cached pages to ensure speed for Prev/Next.
            // If missing, we return null.

            if (episodeMatch == null) {
                return@withContext null
            }

            val epSession = episodeMatch.jsonObject["session"]?.jsonPrimitive?.content ?: return@withContext null

            // 4. Check Stream URL Cache
            streamUrlCache[epSession]?.let { cachedUrl ->
                return@withContext createResult(cachedUrl)
            }

            // 5. Get Play Page & Kwik Link
            val playUrl = "$BASE_URL/play/$session/$epSession"
            val playRequest = Request.Builder()
                .url(playUrl)
                .header("User-Agent", UA)
                .header("Cookie", cookies)
                .build()

            val playHtml = client.newCall(playRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body.string()
            }

            val kwikRegex = Regex("https://kwik\\.cx/e/[a-zA-Z0-9]+")
            val kwikLink = kwikRegex.find(playHtml)?.value ?: run {
                return@withContext null
            }


            // 6. Extract M3U8
            val streamUrl = withContext(Dispatchers.Main) {
                extractM3u8FromKwik(context.applicationContext, kwikLink)
            }

            if (streamUrl != null) {
                streamUrlCache[epSession] = streamUrl // Cache the extracted URL
                return@withContext createResult(streamUrl)
            }

            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private fun createResult(url: String) = AniwatchStreamResult(
        url = url,
        isDirectStream = true,
        headers = mapOf(
            "Referer" to "https://kwik.cx/",
            "User-Agent" to UA
        ),
        subtitleUrl = null,
        serverName = "Kwik",
        category = "sub"
    )

    private suspend fun fetchAndCacheEpisodes(session: String, cookies: String, page: Int, cacheList: MutableList<JsonElement>) {
        val episodesUrl = "$BASE_URL/api?m=release&id=$session&sort=episode_asc&page=$page"
        val epRequest = Request.Builder()
            .url(episodesUrl)
            .headers(getApiHeaders("$BASE_URL/anime/$session", cookies))
            .build()

        val epResponseStr = client.newCall(epRequest).execute().use { response ->
            if (!response.isSuccessful) return
            response.body.string()
        }

        val epData = json.parseToJsonElement(epResponseStr).jsonObject
        val pageEpisodes = epData["data"]?.jsonArray ?: return
        cacheList.addAll(pageEpisodes)
    }

    private fun findBestMatch(results: JsonArray, normQuery: String, normEnglish: String?): JsonObject? {
        return results.map { it.jsonObject }.maxByOrNull { result ->
            val title = result["title"]?.jsonPrimitive?.content ?: ""
            val normResultTitle = normalize(title)
            var score = 0
            if (normResultTitle == normQuery || (normEnglish != null && normResultTitle == normEnglish)) {
                score = 2000
            } else {
                val queryWords = normQuery.split(" ").filter { it.length > 2 }
                val matchedWords = queryWords.count { normResultTitle.contains(it) }
                score = matchedWords * 50
                val targetLen = if (normEnglish != null && Math.abs(normResultTitle.length - normEnglish.length) < Math.abs(normResultTitle.length - normQuery.length)) {
                    normEnglish.length
                } else normQuery.length
                score -= Math.abs(normResultTitle.length - targetLen)
            }
            score
        }
    }

    private fun performSearch(q: String, cookies: String): JsonArray? {
        return try {
            val encodedQuery = URLEncoder.encode(q, "UTF-8")
            val url = "$BASE_URL/api?m=search&q=$encodedQuery"
            val request = Request.Builder().url(url).headers(getApiHeaders(BASE_URL, cookies)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                json.parseToJsonElement(response.body.string()).jsonObject["data"]?.jsonArray
            }
        } catch (e: Exception) { null }
    }

    private suspend fun fetchCookiesWithWebView(context: Context, url: String): String? = suspendCancellableCoroutine { continuation ->
        val webView = WebView(context)
        val cookieManager = CookieManager.getInstance()
        val handler = Handler(Looper.getMainLooper())
        var finished = false

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = UA

        val checkRunnable = object : Runnable {
            override fun run() {
                if (finished) return
                val cookies = cookieManager.getCookie(BASE_URL)
                if (!cookies.isNullOrEmpty() && (cookies.contains("__ddg") || cookies.contains("animepahe_"))) {
                    finished = true
                    continuation.resume(cookies)
                    webView.destroy()
                } else {
                    handler.postDelayed(this, 1000)
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                handler.postDelayed(checkRunnable, 2000)
            }
        }

        handler.postDelayed({
            if (!finished) {
                finished = true
                continuation.resume(null)
                webView.destroy()
            }
        }, 15000)

        continuation.invokeOnCancellation {
            finished = true
            handler.removeCallbacks(checkRunnable)
            handler.post { webView.destroy() }
        }

        webView.loadUrl(url)
    }

    private suspend fun extractM3u8FromKwik(context: Context, kwikUrl: String): String? = suspendCancellableCoroutine { continuation ->
        val webView = WebView(context)
        val handler = Handler(Looper.getMainLooper())
        var finished = false

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = UA

        val timeoutRunnable = Runnable {
            if (!finished) {
                finished = true
                continuation.resume(null)
                webView.destroy()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                if (url.contains(".m3u8")) {
                    if (!finished) {
                        finished = true
                        handler.removeCallbacks(timeoutRunnable)
                        continuation.resume(url)
                        handler.post { webView.destroy() }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript("""
                    (function() {
                        var video = document.querySelector('video');
                        if (video) {
                            video.play();
                            video.muted = true;
                        }
                        var playBtn = document.querySelector('.jw-display-icon-container') || 
                                     document.querySelector('.jw-icon-playback') ||
                                     document.querySelector('.jw-media');
                        if (playBtn) playBtn.click();
                    })();
                """, null)
            }
        }

        handler.postDelayed(timeoutRunnable, 20000)

        continuation.invokeOnCancellation {
            finished = true
            handler.removeCallbacks(timeoutRunnable)
            handler.post { webView.destroy() }
        }

        val extraHeaders = mapOf("Referer" to BASE_URL)
        webView.loadUrl(kwikUrl, extraHeaders)
    }
}