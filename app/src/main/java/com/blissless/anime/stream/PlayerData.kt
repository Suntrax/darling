package com.blissless.anime.stream

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

object PlayerData {
    var videos: List<Video> = emptyList()
    var animeTitle: String = ""
    var currentQualityIndex: Int = 0
    var selectedSubtitle: Track? = null
    var selectedAudio: Track? = null
    var extensionClient: OkHttpClient? = null
}
