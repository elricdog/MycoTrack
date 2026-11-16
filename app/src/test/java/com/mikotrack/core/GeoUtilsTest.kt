package com.mikotrack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the geodesic helpers. */
class GeoUtilsTest {

    @Test
    fun `distance between two nearby points is accurate`() {
        // One degree of latitude is roughly 111 km.
        val distance = GeoUtils.distanceMeters(41.0, 2.0, 42.0, 2.0)
        assertEquals(111_195.0, distance, 500.0)
    }

    @Test
    fun `distance between the same point is zero`() {
        assertEquals(0.0, GeoUtils.distanceMeters(41.3888, 2.159, 41.3888, 2.159), 0.001)
    }

    @Test
    fun `distance across the mediterranean is plausible`() {
        // Barcelona to Madrid is roughly 505 km.
        val distance = GeoUtils.distanceMeters(41.3888, 2.159, 40.4168, -3.7038)
        assertTrue("Unexpected distance: $distance", distance in 490_000.0..520_000.0)
    }

    @Test
    fun `bearing points north, east and south`() {
        assertEquals(0.0, GeoUtils.bearingDegrees(41.0, 2.0, 42.0, 2.0), 0.01)
        assertEquals(90.0, GeoUtils.bearingDegrees(41.0, 2.0, 41.0, 3.0), 0.5)
        assertEquals(180.0, GeoUtils.bearingDegrees(42.0, 2.0, 41.0, 2.0), 0.01)
    }
}
