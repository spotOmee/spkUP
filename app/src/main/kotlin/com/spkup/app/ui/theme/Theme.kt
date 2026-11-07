package com.spkup.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalSpkUpColors = staticCompositionLocalOf { LightSpkUpColors }

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceCard,
    onPrimaryContainer = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurfaceCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSliderInactiveTrack,
    onSurfaceVariant = LightTextSecondary,
    outline = LightToggleInactiveTrack
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryAccent,
    onPrimary = Color.Black,
    primaryContainer = DarkSurfaceCard,
    onPrimaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkToggleInactiveTrack
)

object SpkUpTheme {
    val colors: SpkUpColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSpkUpColors.current

    val typography: androidx.compose.material3.Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography
}

@Composable
fun SpkUpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val spkUpColors = if (darkTheme) DarkSpkUpColors else LightSpkUpColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = spkUpColors.background.toArgb()
            window.navigationBarColor = spkUpColors.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSpkUpColors provides spkUpColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpkUpTypography,
            content = content
        )
    }
}
