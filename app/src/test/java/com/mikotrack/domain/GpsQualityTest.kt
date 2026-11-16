package com.mikotrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsQualityTest {

    private fun rate(
        enabled: Boolean = true,
        accuracy: Float? = 4f,
        age: Long? = 1_000L,
        satellites: Int? = 10,
    ) = GpsQualityRating.rate(enabled, accuracy, age, satellites)

    @Test
    fun `location off is disabled even with a good fix`() {
        assertEquals(GpsQuality.DISABLED, rate(enabled = false))
    }

    @Test
    fun `no fix or a stale fix is searching`() {
        assertEquals(GpsQuality.SEARCHING, rate(age = null, accuracy = null))
        assertEquals(GpsQuality.SEARCHING, rate(age = 15_001L))
        assertEquals(GpsQuality.EXCELLENT, rate(age = 15_000L))
    }

    @Test
    fun `accuracy thresholds are inclusive`() {
        assertEquals(GpsQuality.EXCELLENT, rate(accuracy = 5f))
        assertEquals(GpsQuality.GOOD, rate(accuracy = 5.1f))
        assertEquals(GpsQuality.GOOD, rate(accuracy = 10f))
        assertEquals(GpsQuality.FAIR, rate(accuracy = 10.1f))
        assertEquals(GpsQuality.FAIR, rate(accuracy = 25f))
        assertEquals(GpsQuality.POOR, rate(accuracy = 25.1f))
    }

    @Test
    fun `excellent needs at least eight satellites when the count is known`() {
        assertEquals(GpsQuality.GOOD, rate(accuracy = 3f, satellites = 7))
        assertEquals(GpsQuality.EXCELLENT, rate(accuracy = 3f, satellites = 8))
        assertEquals(GpsQuality.EXCELLENT, rate(accuracy = 3f, satellites = null))
    }

    @Test
    fun `fresh fix without accuracy is poor`() {
        assertEquals(GpsQuality.POOR, rate(accuracy = null))
    }
}
