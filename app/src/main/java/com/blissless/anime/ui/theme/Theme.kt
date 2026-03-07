package com.blissless.anime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Define a pure black color for OLED
val OledBlack = Color(0xFF000000)

// Monochrome color schemes - grayscale Material3 theme
private val MonochromeLightColorScheme = lightColorScheme(
    primary = Color(0xFF424242),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF616161),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E8E8),
    onSecondaryContainer = Color(0xFF1D1D1D),
    tertiary = Color(0xFF757575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEEEEE),
    onTertiaryContainer = Color(0xFF2A2A2A),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF424242),
    outline = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFFBDBDBD)
)

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF424242),
    onPrimaryContainer = Color(0xFFE8E8E8),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF1D1D1D),
    secondaryContainer = Color(0xFF4A4A4A),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF2A2A2A),
    tertiaryContainer = Color(0xFF3A3A3A),
    onTertiaryContainer = Color(0xFFEEEEEE),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF616161),
    outlineVariant = Color(0xFF424242)
)

private val MonochromeOledColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color(0xFFE8E8E8),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1A1A1A),
    onTertiaryContainer = Color(0xFFEEEEEE),
    background = OledBlack,
    onBackground = Color(0xFFE6E6E6),
    surface = OledBlack,
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = OledBlack,
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF424242),
    outlineVariant = Color(0xFF2A2A2A)
)

@Composable
fun AppTheme(
    useOled: Boolean = false,
    useMonochrome: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ (Material You)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        // Monochrome OLED mode - pure black background with grayscale colors
        useMonochrome && useOled && darkTheme -> MonochromeOledColorScheme

        // Monochrome dark mode - standard dark with grayscale colors
        useMonochrome && darkTheme -> MonochromeDarkColorScheme

        // Monochrome light mode - standard light with grayscale colors
        useMonochrome -> MonochromeLightColorScheme

        // Standard dynamic colors (Material You) on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // Standard dark theme
        darkTheme -> darkColorScheme()

        // Standard light theme
        else -> lightColorScheme()
    }.let { scheme ->
        // Override surface and background for True OLED if enabled in Dark Mode
        // This applies to non-monochrome OLED mode
        if (useOled && darkTheme && !useMonochrome) {
            scheme.copy(
                surface = OledBlack,
                background = OledBlack,
                surfaceVariant = OledBlack,
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