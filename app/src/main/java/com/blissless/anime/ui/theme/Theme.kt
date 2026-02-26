package com.blissless.anime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Define a pure black color for OLED
val OledBlack = Color(0xFF000000)

@Composable
fun AppTheme(
    useOled: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ (Material You)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }.let { scheme ->
        // Override surface and background for True OLED if enabled in Dark Mode
        if (useOled && darkTheme) {
            scheme.copy(
                surface = OledBlack,
                background = OledBlack,
                surfaceVariant = OledBlack, // Makes cards/lists blend into black
                primaryContainer = scheme.primaryContainer.copy(alpha = 0.2f)
            )
        } else scheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // You can add custom typography or shapes here later
        content = content
    )
}