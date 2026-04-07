package com.blissless.anime

import com.blissless.anime.data.models.ExploreAnime

sealed class OverlayState {
    data object None : OverlayState()
    
    data class ExploreAnimeDialog(
        val anime: ExploreAnime,
        val firstAnime: ExploreAnime? = null,
        val isFirstOpen: Boolean = true
    ) : OverlayState()

    data class CharacterDialog(
        val characterId: Int,
        val animeId: Int,
        val previousAnime: ExploreAnime? = null,
        val previousFirstAnime: ExploreAnime? = null,
        val previousIsFirstOpen: Boolean = false
    ) : OverlayState()

    data class StaffDialog(
        val staffId: Int,
        val animeId: Int,
        val previousAnime: ExploreAnime? = null,
        val previousFirstAnime: ExploreAnime? = null,
        val previousIsFirstOpen: Boolean = false
    ) : OverlayState()

    data class AllCastDialog(
        val animeId: Int,
        val animeTitle: String,
        val previousAnime: ExploreAnime? = null,
        val previousFirstAnime: ExploreAnime? = null,
        val previousIsFirstOpen: Boolean = false
    ) : OverlayState()

    data class AllStaffDialog(
        val animeId: Int,
        val animeTitle: String,
        val previousAnime: ExploreAnime? = null,
        val previousFirstAnime: ExploreAnime? = null,
        val previousIsFirstOpen: Boolean = false
    ) : OverlayState()
}
