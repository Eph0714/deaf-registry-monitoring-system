package com.deafregistry.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** System-wide theme choice, set by an admin in Control Panel - not a per-device preference. */
enum class AppThemeOption(val key: String) {
    LIGHT_BLUE("light_blue"),
    DARK_PURPLE("dark_purple");

    companion object {
        fun fromKey(key: String?): AppThemeOption = entries.firstOrNull { it.key == key } ?: LIGHT_BLUE
    }
}

/**
 * Reactive holder for the current theme choice. Compose observes this directly (it's read inside
 * DeafRegistryTheme below), so updating it - from the cached value at app startup, or from a
 * fresh fetch after login - recomposes the whole app with the new color scheme immediately,
 * no restart needed.
 */
object ThemeState {
    var current: AppThemeOption by mutableStateOf(AppThemeOption.LIGHT_BLUE)
}

private val BannerBlue = Color(0xFF1565C0)
private val BannerBlueVariant = Color(0xFF0D47A1)
private val SecondaryTeal = Color(0xFF00ACC1)

private val LightBlueColors = lightColorScheme(
    primary = BannerBlue,
    onPrimary = Color.White,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    tertiary = BannerBlueVariant,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF5F8FC)
)

private val BannerPurple = Color(0xFF6A1B9A)
private val BannerPurpleVariant = Color(0xFF4A148C)

private val DarkPurpleColors = darkColorScheme(
    primary = BannerPurple,
    onPrimary = Color.White,
    secondary = BannerPurpleVariant,
    onSecondary = Color.White,
    tertiary = BannerPurpleVariant,
    background = Color.Black,
    onBackground = Color.White,
    // A hair above pure black so cards/surfaces stay visible against the background - still
    // reads as "black" but keeps the UI usable (a card the exact same color as the page behind
    // it would be invisible).
    surface = Color(0xFF121212),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceVariant = Color(0xFF1E1E1E)
)

@Composable
fun DeafRegistryTheme(content: @Composable () -> Unit) {
    val colors = when (ThemeState.current) {
        AppThemeOption.LIGHT_BLUE -> LightBlueColors
        AppThemeOption.DARK_PURPLE -> DarkPurpleColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
