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
    private var lastScreenKey: String = ""
    private var animationJob: Job? = null
    
    fun startAnimation(scope: CoroutineScope, screenKey: String) {
        if (animationJob?.isActive == true) return
        
        // Reset animation when screen changes
        if (screenKey != lastScreenKey || animatable.value >= 1f) {
            lastScreenKey = screenKey
            scope.launch(Dispatchers.Default) {
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
    }
}

@Composable
fun rememberCinematicAnimation(screenKey: String = "default", isVisible: Boolean = true): Float {
    val animation = screenAnimations.getOrPut(screenKey) { ScreenAnimation() }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(screenKey, isVisible) {
        if (isVisible) {
            animation.startAnimation(scope, screenKey)
        }
    }
    
    return animation.animatable.value
}
