package com.spkup.app

import com.spkup.app.ui.components.WaveformState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpkUpWaveformTest {

    @Test
    fun testWaveformStateEnumParity() {
        val states = WaveformState.entries
        assertEquals(3, states.size)
        assertTrue(states.contains(WaveformState.IDLE_BREATHING))
        assertTrue(states.contains(WaveformState.ACTIVE_SPEAKING))
        assertTrue(states.contains(WaveformState.PROCESSING))
    }

    @Test
    fun testSymmetricHeightProfileGeometry() {
        val profile = floatArrayOf(0.18f, 0.40f, 0.65f, 1.00f, 0.65f, 0.40f, 0.18f)
        assertEquals(7, profile.size)

        // Center bar is strictly tallest
        assertEquals(1.00f, profile[3], 0.001f)

        // Strict symmetry: outer bars, medium-short bars, medium-tall bars
        assertEquals(profile[0], profile[6], 0.001f)
        assertEquals(profile[1], profile[5], 0.001f)
        assertEquals(profile[2], profile[4], 0.001f)

        // Monotonic rise towards center
        assertTrue(profile[0] < profile[1])
        assertTrue(profile[1] < profile[2])
        assertTrue(profile[2] < profile[3])

        // Outer bar/dot ratio
        assertEquals(0.18f, profile[0], 0.001f)
        assertEquals(0.40f, profile[1], 0.001f)
        assertEquals(0.65f, profile[2], 0.001f)
    }

    @Test
    fun testVoiceMultipliersSymmetry() {
        val multipliers = floatArrayOf(0.40f, 0.70f, 1.05f, 1.40f, 1.05f, 0.70f, 0.40f)
        assertEquals(7, multipliers.size)
        assertEquals(multipliers[0], multipliers[6], 0.001f)
        assertEquals(multipliers[1], multipliers[5], 0.001f)
        assertEquals(multipliers[2], multipliers[4], 0.001f)
        assertTrue(multipliers[3] > multipliers[2])
    }
}
