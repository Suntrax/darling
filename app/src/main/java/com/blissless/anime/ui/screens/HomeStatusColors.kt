package com.blissless.anime.ui.screens

import androidx.compose.ui.graphics.Color

object HomeStatusColors {
    fun getColor(status: String?): Color {
        return when (status) {
            "CURRENT" -> Color(0xFF2196F3) // Blue
            "PLANNING" -> Color(0xFF9C27B0) // Purple
            "COMPLETED" -> Color(0xFF4CAF50) // Green
            "PAUSED" -> Color(0xFFFFC107) // Amber
            "DROPPED" -> Color(0xFFF44336) // Red
            else -> Color.Gray
        }
    }

    fun getContainerColor(status: String?): Color {
        return getColor(status).copy(alpha = 0.2f)
    }
}