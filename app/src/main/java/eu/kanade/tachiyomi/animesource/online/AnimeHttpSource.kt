package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import okhttp3.OkHttpClient

abstract class AnimeHttpSource : AnimeCatalogueSource {
    abstract val baseUrl: String
    open val client: OkHttpClient get() = OkHttpClient()
    abstract fun getVideoUrl(video: eu.kanade.tachiyomi.animesource.model.Video): String
}
