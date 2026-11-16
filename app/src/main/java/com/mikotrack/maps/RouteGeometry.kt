package com.mikotrack.maps

import com.mikotrack.core.GeoUtils

/** The geographic extent of a route. */
data class RouteBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    /** Whether the route fits in a single spot, too small to be framed. */
    val isSinglePlace: Boolean
        get() = maxLatitude - minLatitude < 1e-5 && maxLongitude - minLongitude < 1e-5
}

/** Pure geometric helpers used to place the route labels and to frame the routes. */
object RouteGeometry {

    /** The bounding box of [points], or `null` when there are none. */
    fun bounds(points: List<GeoPoint>): RouteBounds? {
        if (points.isEmpty()) return null
        return RouteBounds(
            minLatitude = points.minOf { it.latitude },
            minLongitude = points.minOf { it.longitude },
            maxLatitude = points.maxOf { it.latitude },
            maxLongitude = points.maxOf { it.longitude },
        )
    }

    /**
     * The point of the route that lies at half of its length, interpolated between the
     * two samples around it. It is where the name of the route is drawn: unlike the
     * centre of the bounding box, it is always on the line.
     */
    fun pointAtHalfLength(points: List<GeoPoint>): GeoPoint? {
        if (points.isEmpty()) return null
        if (points.size == 1) return points.first()
        val segments = DoubleArray(points.size - 1) { index ->
            val a = points[index]
            val b = points[index + 1]
            GeoUtils.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        val total = segments.sum()
        if (total <= 0.0) return points.first()
        var remaining = total / 2
        segments.forEachIndexed { index, length ->
            if (remaining <= length) {
                val a = points[index]
                val b = points[index + 1]
                val fraction = if (length > 0.0) remaining / length else 0.0
                return GeoPoint(
                    latitude = a.latitude + (b.latitude - a.latitude) * fraction,
                    longitude = a.longitude + (b.longitude - a.longitude) * fraction,
                )
            }
            remaining -= length
        }
        return points.last()
    }
}
