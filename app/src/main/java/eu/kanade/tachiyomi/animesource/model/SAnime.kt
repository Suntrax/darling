package eu.kanade.tachiyomi.animesource.model

data class SAnime(
    val title: String = "",
    val url: String = "",
    val thumbnail_url: String = "",
    val artist: String = "",
    val author: String = "",
    val description: String = "",
    val genre: List<String> = emptyList(),
    val status: Int = 0,
    val initializer: Boolean = false,
)
