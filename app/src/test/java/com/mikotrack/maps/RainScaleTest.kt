package com.mikotrack.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the colour ramp and the qualitative bands of the rainfall overlay. */
class RainScaleTest {

    @Test
    fun `no rain is fully transparent`() {
        assertEquals(0, RainScale.color(0f))
    }

    @Test
    fun `the ramp grows more opaque with the intensity`() {
        val drizzle = RainScale.color(0.05f)
        val rain = RainScale.color(0.5f)
        val torrent = RainScale.color(4f)
        assertTrue(alpha(drizzle) < alpha(rain))
        assertTrue(alpha(rain) < alpha(torrent))
    }

    @Test
    fun `the opacity saturates at the top of the ramp`() {
        // Beyond the last stop the colour does not change.
        assertEquals(RainScale.color(4f), RainScale.color(100f))
    }

    @Test
    fun `the ramp never turns fully opaque`() {
        // The overlay must always let the base map show through.
        assertTrue(alpha(RainScale.color(100f)) < 255)
    }

    @Test
    fun `the qualitative bands follow the intensity`() {
        assertEquals(RainScale.Level.DRY, RainScale.intensityOf(0f))
        assertEquals(RainScale.Level.DAMP, RainScale.intensityOf(0.1f))
        assertEquals(RainScale.Level.WET, RainScale.intensityOf(0.5f))
        assertEquals(RainScale.Level.VERY_WET, RainScale.intensityOf(2f))
    }

    @Test
    fun `the legend intensities sit inside the ramp`() {
        val top = 4f
        for (intensity in RainScale.legendIntensities) {
            assertTrue(intensity > 0f && intensity <= top)
        }
    }

    private fun alpha(color: Int): Int = color ushr 24 and 0xFF
}
