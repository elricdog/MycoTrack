package com.mikotrack.domain

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.stats.TripStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the statistics and elevation engine. */
class TripStatsCalculatorTest {

    private val start = 1_700_000_000_000L

    private fun point(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null,
        speed: Float? = null,
        secondsAfterStart: Long = 0,
    ) = TrackPoint(
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracy = 5f,
        speed = speed,
        timestamp = start + secondsAfterStart * 1_000,
    )

    @Test
    fun `distance is the sum of the segments`() {
        val points = listOf(
            point(41.0, 2.0, secondsAfterStart = 0),
            point(41.01, 2.0, secondsAfterStart = 10),
            point(41.02, 2.0, secondsAfterStart = 20),
        )
        val stats = TripStatsCalculator.compute(points, start, start + 20_000)
        assertEquals(2_223.0, stats.distanceMeters, 30.0)
        assertEquals(20_000L, stats.elapsedMillis)
    }

    @Test
    fun `elevation gain ignores the noise below the threshold`() {
        val points = listOf(
            point(41.0, 2.0, altitude = 100.0, secondsAfterStart = 0),
            point(41.001, 2.0, altitude = 101.0, secondsAfterStart = 10),
            point(41.002, 2.0, altitude = 120.0, secondsAfterStart = 20),
        )
        val stats = TripStatsCalculator.compute(points, start, start + 20_000)
        assertEquals(20.0, stats.elevationGain, 0.5)
        assertEquals(100.0, stats.minAltitude!!, 0.001)
        assertEquals(120.0, stats.maxAltitude!!, 0.001)
    }

    @Test
    fun `moving and stationary time are separated by the speed`() {
        val points = listOf(
            point(41.0, 2.0, speed = 1.2f, secondsAfterStart = 0),
            point(41.001, 2.0, speed = 1.2f, secondsAfterStart = 10),
            point(41.001, 2.0, speed = 0.0f, secondsAfterStart = 20),
            point(41.001, 2.0, speed = 0.0f, secondsAfterStart = 30),
        )
        val stats = TripStatsCalculator.compute(points, start, start + 30_000)
        assertEquals(10_000L, stats.movingTimeMillis)
        assertEquals(20_000L, stats.stationaryTimeMillis)
    }

    @Test
    fun `the elevation profile accumulates the distance along the track`() {
        val points = listOf(
            point(41.0, 2.0, altitude = 100.0, secondsAfterStart = 0),
            point(41.01, 2.0, altitude = 110.0, secondsAfterStart = 10),
            point(41.02, 2.0, altitude = 90.0, secondsAfterStart = 20),
        )
        val profile = TripStatsCalculator.buildElevationProfile(points)
        assertEquals(3, profile.points.size)
        assertEquals(0.0, profile.points.first().distanceMeters, 0.001)
        assertTrue(profile.totalDistance > 2_000.0)
        assertEquals(90.0, profile.minAltitude, 0.001)
        assertEquals(110.0, profile.maxAltitude, 0.001)
        assertEquals(10.0, profile.elevationGain, 0.5)
        assertEquals(20.0, profile.elevationLoss, 0.5)
    }

    @Test
    fun `the altitude at a distance is interpolated`() {
        val points = listOf(
            point(41.0, 2.0, altitude = 100.0, secondsAfterStart = 0),
            point(41.01, 2.0, altitude = 200.0, secondsAfterStart = 10),
        )
        val profile = TripStatsCalculator.buildElevationProfile(points)
        val middle = profile.altitudeAtDistance(profile.totalDistance / 2)
        assertTrue("Unexpected altitude: $middle", middle != null && middle in 140.0..160.0)
    }

    @Test
    fun `an empty track produces empty statistics`() {
        val stats = TripStatsCalculator.compute(emptyList(), start, start)
        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(0, stats.pointCount)
    }
}
