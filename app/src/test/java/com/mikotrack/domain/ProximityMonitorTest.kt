package com.mikotrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the proximity alarm logic. */
class ProximityMonitorTest {

    private val target = ProximityTarget(
        waypointId = 1,
        tripId = 10,
        latitude = 41.6100,
        longitude = 2.2900,
        description = "Ceps",
        category = "MUSHROOM_FOUND",
        tripName = "Old trip",
    )

    // About 111 m per 0.001 degrees of latitude.
    private fun north(meters: Double) = target.latitude + meters / 111_195.0

    @Test
    fun `a waypoint alerts once when the user enters its radius`() {
        val monitor = ProximityMonitor()
        assertTrue(monitor.update(north(120.0), target.longitude, 5f, 50.0, listOf(target)).isEmpty())

        val hits = monitor.update(north(40.0), target.longitude, 5f, 50.0, listOf(target))
        assertEquals(1, hits.size)
        assertEquals(40.0, hits.first().distanceMeters, 1.0)

        assertTrue(monitor.update(north(10.0), target.longitude, 5f, 50.0, listOf(target)).isEmpty())
    }

    @Test
    fun `gps jitter around the edge does not ring again`() {
        val monitor = ProximityMonitor()
        assertEquals(1, monitor.update(north(48.0), target.longitude, 5f, 50.0, listOf(target)).size)
        assertTrue(monitor.update(north(60.0), target.longitude, 5f, 50.0, listOf(target)).isEmpty())
        assertTrue(monitor.update(north(49.0), target.longitude, 5f, 50.0, listOf(target)).isEmpty())
    }

    @Test
    fun `the waypoint alerts again after the user moved clearly away`() {
        val monitor = ProximityMonitor()
        assertEquals(1, monitor.update(north(20.0), target.longitude, 5f, 50.0, listOf(target)).size)
        assertTrue(monitor.update(north(90.0), target.longitude, 5f, 50.0, listOf(target)).isEmpty())
        assertEquals(1, monitor.update(north(20.0), target.longitude, 5f, 50.0, listOf(target)).size)
    }

    @Test
    fun `an inaccurate fix is ignored`() {
        val monitor = ProximityMonitor()
        assertTrue(monitor.update(north(10.0), target.longitude, 150f, 50.0, listOf(target)).isEmpty())
        assertEquals(1, monitor.update(north(10.0), target.longitude, 8f, 50.0, listOf(target)).size)
    }

    @Test
    fun `a waypoint whose trip is hidden and shown again can alert again`() {
        val monitor = ProximityMonitor()
        assertEquals(1, monitor.update(north(10.0), target.longitude, 5f, 50.0, listOf(target)).size)
        assertTrue(monitor.update(north(10.0), target.longitude, 5f, 50.0, emptyList()).isEmpty())
        assertEquals(1, monitor.update(north(10.0), target.longitude, 5f, 50.0, listOf(target)).size)
    }
}
