package com.blissless.anime

import com.blissless.anime.data.models.ExploreAnime

sealed class OverlayState {
    data object None : OverlayState()
    
    data class ExploreAnimeDialog(
        val anime: ExploreAnime,
        val firstAnime: ExploreAnime? = null,
        val isFirstOpen: Boolean = true
    ) : OverlayState()
}
