package eu.kanade.tachiyomi.animesource

interface ConfigurableAnimeSource : AnimeSource {
    fun setupPreferences()
}
