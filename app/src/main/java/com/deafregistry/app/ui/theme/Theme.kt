package com.deafregistry.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BannerBlue = Color(0xFF1565C0)
private val BannerBlueVariant = Color(0xFF0D47A1)
private val SecondaryTeal = Color(0xFF00ACC1)

private val AppColors = lightColorScheme(
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

@Composable
fun DeafRegistryTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppColors.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = AppColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
