package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import okhttp3.Headers
import okhttp3.OkHttpClient

abstract class AnimeHttpSource : AnimeCatalogueSource {
    abstract val baseUrl: String
    open val client: OkHttpClient get() = OkHttpClient()
    abstract fun getVideoUrl(video: eu.kanade.tachiyomi.animesource.model.Video): String

    open fun getHeaders(): Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:109.0) Gecko/20100101 Firefox/115.0")
        .add("Accept-Language", "en-US,en;q=0.5")
        .build()
}
