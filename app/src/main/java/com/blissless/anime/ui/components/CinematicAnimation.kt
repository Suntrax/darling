package com.blissless.anime.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val screenAnimations = mutableMapOf<String, ScreenAnimation>()

private class ScreenAnimation {
    val animatable = Animatable(0f)
    var hasPlayed = false
    private var animationJob: Job? = null
    
    fun startAnimation(scope: CoroutineScope) {
        if (animationJob?.isActive == true) return
        animationJob = scope.launch(Dispatchers.Default) {
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessVeryLow
                )
            )
        }
    }
    
    fun ensureAnimationCompleted(scope: CoroutineScope) {
        if (!hasPlayed) {
            hasPlayed = true
            startAnimation(scope)
        } else if (animationJob?.isActive == true) {
            // Already running
        } else {
            // Has played but animation didn't complete, snap to end
            scope.launch {
                animatable.snapTo(1f)
            }
        }
    }
}

@Composable
fun rememberCinematicAnimation(screenKey: String = "default", isVisible: Boolean = true): Float {
    val animation = screenAnimations.getOrPut(screenKey) { ScreenAnimation() }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(screenKey, isVisible) {
        if (isVisible && !animation.hasPlayed) {
            animation.hasPlayed = true
            animation.startAnimation(scope)
        }
    }
    
    DisposableEffect(screenKey) {
        onDispose {
            animation.ensureAnimationCompleted(scope)
        }
    }
    
    return animation.animatable.value
}
