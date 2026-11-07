package com.spkup.app

import androidx.compose.ui.graphics.Color
import com.spkup.app.data.SpkUpPreferences
import com.spkup.app.data.StyleMode
import com.spkup.app.data.ThemeMode
import com.spkup.app.ui.theme.DarkSpkUpColors
import com.spkup.app.ui.theme.LightSpkUpColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpkUpPreferencesTest {

    @Test
    fun testPreferencesDefaultsAndRanges() {
        assertEquals(ThemeMode.SYSTEM, SpkUpPreferences.DEFAULT_THEME_MODE)
        assertFalse(SpkUpPreferences.DEFAULT_IS_BUBBLE_ENABLED)

        assertEquals(0.61f, SpkUpPreferences.DEFAULT_BUBBLE_OPACITY, 0.001f)
        assertEquals(0.20f, SpkUpPreferences.MIN_BUBBLE_OPACITY, 0.001f)
        assertEquals(1.0f, SpkUpPreferences.MAX_BUBBLE_OPACITY, 0.001f)

        assertEquals(52, SpkUpPreferences.DEFAULT_BUBBLE_SIZE_DP)
        assertEquals(40, SpkUpPreferences.MIN_BUBBLE_SIZE_DP)
        assertEquals(80, SpkUpPreferences.MAX_BUBBLE_SIZE_DP)

        assertEquals(15, SpkUpPreferences.DEFAULT_BUBBLE_SHAPE_PERCENT)
        assertEquals(0, SpkUpPreferences.MIN_BUBBLE_SHAPE_PERCENT)
        assertEquals(50, SpkUpPreferences.MAX_BUBBLE_SHAPE_PERCENT)

        assertEquals(StyleMode.CASUAL, SpkUpPreferences.DEFAULT_STYLE_MODE)
    }

    @Test
    fun testStyleModeParsing() {
        assertEquals(StyleMode.FORMAL, StyleMode.fromKey("FORMAL"))
        assertEquals(StyleMode.CASUAL, StyleMode.fromKey("casual"))
        assertEquals(StyleMode.SUPER_CASUAL, StyleMode.fromKey("SUPER_CASUAL"))
        assertEquals(StyleMode.ORGANISE, StyleMode.fromKey("Organise"))
        assertEquals(StyleMode.MKDOWN, StyleMode.fromKey("mkdown"))
        assertEquals(StyleMode.CASUAL, StyleMode.fromKey("unknown_fallback"))
    }

    @Test
    fun testLightModeExactColors() {
        val light = LightSpkUpColors
        assertEquals(Color(0xFFF7F7F5), light.background)
        assertEquals(Color(0xFFEDF7F9), light.surfaceCard)
        assertEquals(Color(0xFF38B6CC), light.primaryAccent)
        assertEquals(Color(0xFF121A24), light.sliderActiveTrack)
        assertEquals(Color(0xFFE6DEFA), light.sliderInactiveTrack)
        assertEquals(Color(0xFF121A24), light.textPrimary)
        assertEquals(Color(0xFF5A6570), light.textSecondary)
        assertEquals(Color(0xFF8E8E93), light.toggleInactiveThumb)
        assertEquals(Color(0xFFD1D1D6), light.toggleInactiveTrack)
        assertFalse(light.isDark)
    }

    @Test
    fun testOledDarkModeExactColors() {
        val dark = DarkSpkUpColors
        assertEquals(Color(0xFF000000), dark.background)
        assertEquals(Color(0xFF141416), dark.surfaceCard)
        assertEquals(Color(0xFFF5F5F7), dark.offWhiteFill)
        assertEquals(Color(0xFFFF9F0A), dark.primaryAccent)
        assertEquals(Color(0xFFFF9F0A), dark.sliderActiveTrack)
        assertEquals(Color(0xFFE6DEFA), dark.sliderInactiveTrack) // Preserved in dark mode
        assertEquals(Color(0xFFF5F5F7), dark.textPrimary)
        assertEquals(Color(0xFF98989D), dark.textSecondary)
        assertEquals(Color(0xFF8E8E93), dark.toggleInactiveThumb)
        assertEquals(Color(0xFF2C2C2E), dark.toggleInactiveTrack)
        assertEquals(true, dark.isDark)
    }
}
