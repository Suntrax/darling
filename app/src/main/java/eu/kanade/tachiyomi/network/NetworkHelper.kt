package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetworkHelper {
    val client: OkHttpClient
    val cloudflareClient: OkHttpClient

    init {
        val cookieJar = object : CookieJar {
            private val store = ConcurrentHashMap<String, List<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return store[url.host] ?: emptyList()
            }
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cookieJar(cookieJar)

        client = builder.build()
        cloudflareClient = client
    }

    fun defaultUserAgentProvider(): String {
        return "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
    }

    companion object {
        @Volatile
        private var instance: NetworkHelper? = null

        fun getInstance(): NetworkHelper {
            return instance ?: synchronized(this) {
                instance ?: NetworkHelper().also { instance = it }
            }
        }
    }
}
