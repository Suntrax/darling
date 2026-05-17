package eu.kanade.tachiyomi.animesource.model

import okhttp3.Headers

data class Video(
    val videoUrl: String,
    val videoTitle: String,
    val resolution: Int?,
    val headers: Headers?,
    val subtitleTracks: List<Track>,
    val audioTracks: List<Track>,
) {
    constructor(
        videoUrl: String,
        videoTitle: String,
        resolution: Int? = null,
        headers: Headers? = null,
    ) : this(videoUrl, videoTitle, resolution, headers, emptyList(), emptyList())
}
