package eu.kanade.tachiyomi.animesource.online

abstract class ParsedAnimeHttpSource : AnimeHttpSource() {
    override val lang: String = ""
    open val supportsLatest: Boolean = true
    override val name: String = ""
    override val id: Long = 0
}
