package com.mikotrack.domain

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the distance shown when a waypoint is tapped on the map. */
class WaypointDistanceCalculatorTest {

    private val start = 1_700_000_000_000L

    private fun point(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null,
        secondsAfterStart: Long = 0,
    ) = TrackPoint(
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracy = 5f,
        speed = null,
        timestamp = start + secondsAfterStart * 1_000,
    )

    private fun waypoint(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null,
        tripId: Long = 1L,
    ) = Waypoint(
        id = 10L,
        tripId = tripId,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        timestamp = start,
        description = "Mushroom",
        category = "mushroom_found",
        imagePath = null,
    )

    @Test
    fun `the straight line distance is measured from the current position`() {
        // One degree of latitude is roughly 111 km.
        val distance = WaypointDistanceCalculator.compute(
            waypoint = waypoint(42.0, 2.0),
            currentLatitude = 41.0,
            currentLongitude = 2.0,
        )
        assertEquals(111_195.0, distance.straightLineMeters, 500.0)
    }

    @Test
    fun `the direction points towards the waypoint`() {
        val toTheNorth = WaypointDistanceCalculator.compute(
            waypoint = waypoint(42.0, 2.0),
            currentLatitude = 41.0,
            currentLongitude = 2.0,
        )
        assertEquals(0.0, toTheNorth.bearingDegrees, 0.5)

        val toTheEast = WaypointDistanceCalculator.compute(
            waypoint = waypoint(41.0, 3.0),
            currentLatitude = 41.0,
            currentLongitude = 2.0,
        )
        assertEquals(90.0, toTheEast.bearingDegrees, 0.5)
    }

    @Test
    fun `the altitude difference is measured against the current position`() {
        val distance = WaypointDistanceCalculator.compute(
            waypoint = waypoint(41.0, 2.0, altitude = 250.0),
            currentLatitude = 41.0,
            currentLongitude = 2.0,
            currentAltitude = 200.0,
        )
        assertEquals(50.0, distance.elevationDifferenceMeters ?: 0.0, 0.001)
    }

    @Test
    fun `the altitude difference is unknown without an altitude`() {
        val distance = WaypointDistanceCalculator.compute(
            waypoint = waypoint(41.0, 2.0, altitude = 250.0),
            currentLatitude = 41.0,
            currentLongitude = 2.0,
            currentAltitude = null,
        )
        assertNull(distance.elevationDifferenceMeters)
    }

    @Test
    fun `the distance along the route counts the walked segments`() {
        val track = listOf(
            point(41.0, 2.0, secondsAfterStart = 0),
            point(41.01, 2.0, secondsAfterStart = 10),
            point(41.02, 2.0, secondsAfterStart = 20),
            point(41.03, 2.0, secondsAfterStart = 30),
        )
        val distance = WaypointDistanceCalculator.compute(
            waypoint = waypoint(41.02, 2.0),
            currentLatitude = 41.03,
            currentLongitude = 2.0,
            trackPoints = track,
        )
        // The waypoint sits on the third sample, two segments away from the start.
        assertEquals(2_223.0, distance.alongTrackMeters ?: 0.0, 40.0)
    }

    @Test
    fun `the distance along the route is unknown without a route`() {
        val distance = WaypointDistanceCalculator.compute(
            waypoint = waypoint(41.0, 2.0),
            currentLatitude = 41.0,
            currentLongitude = 2.0,
            trackPoints = emptyList(),
        )
        assertNull(distance.alongTrackMeters)
    }

    @Test
    fun `the distance along the route never exceeds the length of the route`() {
        val track = listOf(
            point(41.0, 2.0, secondsAfterStart = 0),
            point(41.01, 2.0, secondsAfterStart = 10),
        )
        val along = WaypointDistanceCalculator.distanceAlongTrack(
            waypoint = waypoint(41.01, 2.0),
            trackPoints = track,
        )
        assertTrue("Unexpected distance: $along", along != null && along <= 1_200.0)
    }
}
