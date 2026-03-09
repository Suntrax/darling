package com.blissless.anime.ui.screens

import androidx.compose.ui.graphics.Color

// Status colors for different list types
val StatusColors = mapOf(
    "CURRENT" to Color(0xFF2196F3),    // Blue - Watching
    "PLANNING" to Color(0xFF9C27B0),   // Purple - Planning
    "COMPLETED" to Color(0xFF4CAF50),  // Green - Completed
    "PAUSED" to Color(0xFFFFC107),     // Amber - On Hold
    "DROPPED" to Color(0xFFF44336)     // Red - Dropped
)

val StatusLabels = mapOf(
    "CURRENT" to "Watching",
    "PLANNING" to "Planning",
    "COMPLETED" to "Completed",
    "PAUSED" to "On Hold",
    "DROPPED" to "Dropped"
)
