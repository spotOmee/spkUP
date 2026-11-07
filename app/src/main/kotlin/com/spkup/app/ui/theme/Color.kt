package com.spkup.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ── Exact Light Mode Palette Tokens ──────────────────────────────────────────
val LightBackground = Color(0xFFF7F7F5)         // Warm Off-White
val LightSurfaceCard = Color(0xFFEDF7F9)        // Soft Pale Cyan tint
val LightPrimaryAccent = Color(0xFF38B6CC)      // Teal-Cyan
val LightSliderActiveTrack = Color(0xFF121A24)  // Deep Dark Slate
val LightSliderInactiveTrack = Color(0xFFE6DEFA)// Soft Light Lavender (gap fill)
val LightTextPrimary = Color(0xFF121A24)
val LightTextSecondary = Color(0xFF5A6570)
val LightToggleInactiveThumb = Color(0xFF8E8E93)
val LightToggleInactiveTrack = Color(0xFFD1D1D6)
val LightOffWhiteFill = Color(0xFFFFFFFF)

// ── Exact Dark Mode Palette Tokens (Apple OLED Style) ────────────────────────
val DarkBackground = Color(0xFF000000)          // True OLED Black
val DarkSurfaceCard = Color(0xFF141416)         // Elevated dark surface
val DarkOffWhiteFill = Color(0xFFF5F5F7)        // Apple Off-White
val DarkPrimaryAccent = Color(0xFFFF9F0A)       // Amber Yellow / Orange
val DarkSliderActiveTrack = Color(0xFFFF9F0A)   // Amber Yellow / Orange
val DarkSliderInactiveTrack = Color(0xFFE6DEFA) // Soft Light Lavender (must remain #E6DEFA)
val DarkTextPrimary = Color(0xFFF5F5F7)
val DarkTextSecondary = Color(0xFF98989D)
val DarkToggleInactiveThumb = Color(0xFF8E8E93)
val DarkToggleInactiveTrack = Color(0xFF2C2C2E)

/**
 * Custom SpkUP Color Tokens contract.
 */
@Immutable
data class SpkUpColors(
    val background: Color,
    val surfaceCard: Color,
    val offWhiteFill: Color,
    val primaryAccent: Color,
    val sliderActiveTrack: Color,
    val sliderInactiveTrack: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val toggleInactiveThumb: Color,
    val toggleInactiveTrack: Color,
    val isDark: Boolean
)

val LightSpkUpColors = SpkUpColors(
    background = LightBackground,
    surfaceCard = LightSurfaceCard,
    offWhiteFill = LightOffWhiteFill,
    primaryAccent = LightPrimaryAccent,
    sliderActiveTrack = LightSliderActiveTrack,
    sliderInactiveTrack = LightSliderInactiveTrack,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    toggleInactiveThumb = LightToggleInactiveThumb,
    toggleInactiveTrack = LightToggleInactiveTrack,
    isDark = false
)

val DarkSpkUpColors = SpkUpColors(
    background = DarkBackground,
    surfaceCard = DarkSurfaceCard,
    offWhiteFill = DarkOffWhiteFill,
    primaryAccent = DarkPrimaryAccent,
    sliderActiveTrack = DarkSliderActiveTrack,
    sliderInactiveTrack = DarkSliderInactiveTrack,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    toggleInactiveThumb = DarkToggleInactiveThumb,
    toggleInactiveTrack = DarkToggleInactiveTrack,
    isDark = true
)
