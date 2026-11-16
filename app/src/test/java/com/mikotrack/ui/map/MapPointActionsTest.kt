package com.mikotrack.ui.map

import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapPointActionsTest {

    @Test
    fun `route focus cycles through the routes and back to the user`() {
        val ids = listOf(5L, 9L, 2L)
        assertEquals(5L, nextRouteFocus(null, ids))
        assertEquals(9L, nextRouteFocus(5L, ids))
        assertEquals(2L, nextRouteFocus(9L, ids))
        assertNull(nextRouteFocus(2L, ids))
    }

    @Test
    fun `route focus alternates with a single route and restarts when the route is gone`() {
        assertEquals(7L, nextRouteFocus(null, listOf(7L)))
        assertNull(nextRouteFocus(7L, listOf(7L)))
        assertEquals(3L, nextRouteFocus(7L, listOf(3L, 4L)))
        assertNull(nextRouteFocus(null, emptyList()))
    }

    @Test
    fun `walking directions use dots whatever the default locale`() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ca-ES"))
        try {
            assertEquals(
                "https://www.google.com/maps/dir/?api=1&destination=41.123457,2.500000&travelmode=walking",
                MapPointLinks.walkingDirectionsUrl(41.1234567, 2.5),
            )
            assertEquals("geo:-0.500000,1.000000?q=-0.500000,1.000000", MapPointLinks.geoUri(-0.5, 1.0))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `waypoint trips list the overlaid ones first and then the most recent`() {
        val trips = listOf(trip(1, 100), trip(2, 300), trip(3, 200), trip(4, 400))
        val ordered = waypointTripCandidates(trips, overlaidIds = listOf(3L, 1L), recentLimit = 1)
        assertEquals(listOf(3L, 1L, 4L), ordered.map { it.id })
    }

    private fun trip(id: Long, start: Long) = Trip(
        id = id,
        name = "Trip $id",
        startTime = start,
        endTime = null,
        durationMillis = 0,
        distanceMeters = 0.0,
        elevationGain = 0.0,
        elevationLoss = 0.0,
        minAltitude = null,
        maxAltitude = null,
        notes = "",
        status = TripStatus.COMPLETED,
        photoCount = 0,
        waypointCount = 0,
        lastUpdated = 0,
    )
}
