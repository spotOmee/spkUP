package com.spkup.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndSliderTest {

    @Test
    fun testAppScreenEnum() {
        val screens = AppScreen.entries
        assertEquals(2, screens.size)
        assertTrue(screens.contains(AppScreen.WELCOME))
        assertTrue(screens.contains(AppScreen.DASHBOARD))
    }

    @Test
    fun testSliderNormalizationMath() {
        val range = 40f..80f
        val value = 52f
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        assertEquals(0.30f, fraction, 0.001f)

        // Clamping check
        val belowFraction = ((20f - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        assertEquals(0.0f, belowFraction, 0.001f)

        val aboveFraction = ((100f - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        assertEquals(1.0f, aboveFraction, 0.001f)
    }

    @Test
    fun testTop30PercentSwipeDetectionMath() {
        val screenHeight = 2400
        val top30Threshold = screenHeight * 0.30f // 720 px

        // Point inside top 30%
        val validTouchY = 500f
        assertTrue(validTouchY <= top30Threshold)

        // Point outside top 30%
        val invalidTouchY = 800f
        assertFalse(invalidTouchY <= top30Threshold)

        // Horizontal swipe distance threshold (80dp)
        val minSwipePx = 80f * 2.625f // e.g. xxhdpi = 210px
        val swipeAmount = 250f
        assertTrue(swipeAmount > minSwipePx)
    }
}
