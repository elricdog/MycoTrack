package com.mikotrack.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {

    @Test
    fun `half length point is interpolated on the longest part of the route`() {
        // Two legs along the equator: 1 degree and then 3 degrees of longitude.
        val points = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(0.0, 4.0))
        val middle = RouteGeometry.pointAtHalfLength(points)!!
        assertEquals(0.0, middle.latitude, 1e-9)
        assertEquals(2.0, middle.longitude, 1e-6)
    }

    @Test
    fun `half length point handles degenerate routes`() {
        assertNull(RouteGeometry.pointAtHalfLength(emptyList()))
        val single = GeoPoint(41.5, 2.1)
        assertEquals(single, RouteGeometry.pointAtHalfLength(listOf(single)))
        assertEquals(single, RouteGeometry.pointAtHalfLength(listOf(single, single, single)))
    }

    @Test
    fun `bounds cover every point and detect a single place`() {
        val bounds = RouteGeometry.bounds(listOf(GeoPoint(41.0, 2.5), GeoPoint(41.2, 2.1)))!!
        assertEquals(RouteBounds(41.0, 2.1, 41.2, 2.5), bounds)
        assertTrue(RouteGeometry.bounds(listOf(GeoPoint(41.0, 2.0), GeoPoint(41.000001, 2.000001)))!!.isSinglePlace)
        assertNull(RouteGeometry.bounds(emptyList()))
    }
}
