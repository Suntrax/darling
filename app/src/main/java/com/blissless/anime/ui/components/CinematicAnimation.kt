package com.blissless.anime.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.*

private val screenAnimations = mutableMapOf<String, ScreenAnimation>()

private class ScreenAnimation {
    val animatable = Animatable(0f)
    var hasPlayed = false
}

@Composable
fun rememberCinematicAnimation(screenKey: String = "default", isVisible: Boolean = true): Float {
    val animation = screenAnimations.getOrPut(screenKey) { ScreenAnimation() }
    
    LaunchedEffect(screenKey, isVisible) {
        if (isVisible && !animation.hasPlayed) {
            animation.hasPlayed = true
            animation.animatable.snapTo(0f)
            animation.animatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessVeryLow
                )
            )
        }
    }
    
    return animation.animatable.value
}
