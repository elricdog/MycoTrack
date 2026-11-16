package com.mikotrack.domain

import com.mikotrack.core.GeoUtils
import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Waypoint

/**
 * How far a waypoint is from the user, and how to get there.
 */
data class WaypointDistance(
    /** Straight line distance from the current position, in metres. */
    val straightLineMeters: Double,
    /** Direction from the current position towards the waypoint, in degrees from north. */
    val bearingDegrees: Double,
    /** How far into its route the waypoint is, in metres. `null` when it has no route. */
    val alongTrackMeters: Double? = null,
    /** Difference of altitude between the current position and the waypoint. */
    val elevationDifferenceMeters: Double? = null,
)

/**
 * Works out how far a waypoint is from the position of the user.
 *
 * The same calculation serves the waypoints of the trip that is being recorded and the
 * ones of the previous trips that are drawn on top of the map: only the route used to
 * measure the distance along the track changes.
 */
object WaypointDistanceCalculator {

    /**
     * Computes the distance of [waypoint] from the given position.
     *
     * [trackPoints] is the route the waypoint belongs to. When it is available, the
     * distance along the route is also reported, which is what tells the user where in
     * the trip the photograph was taken.
     */
    fun compute(
        waypoint: Waypoint,
        currentLatitude: Double,
        currentLongitude: Double,
        currentAltitude: Double? = null,
        trackPoints: List<TrackPoint> = emptyList(),
    ): WaypointDistance {
        val straightLine = GeoUtils.distanceMeters(
            currentLatitude,
            currentLongitude,
            waypoint.latitude,
            waypoint.longitude,
        )
        val bearing = GeoUtils.bearingDegrees(
            currentLatitude,
            currentLongitude,
            waypoint.latitude,
            waypoint.longitude,
        )
        val elevationDifference = if (currentAltitude != null && waypoint.altitude != null) {
            waypoint.altitude - currentAltitude
        } else {
            null
        }

        return WaypointDistance(
            straightLineMeters = straightLine,
            bearingDegrees = bearing,
            alongTrackMeters = distanceAlongTrack(waypoint, trackPoints),
            elevationDifferenceMeters = elevationDifference,
        )
    }

    /**
     * The distance walked along the route from its start until the waypoint, in metres.
     *
     * The waypoint is attached to the sample of the route that is closest to it, which
     * is the point where the photograph was taken.
     */
    fun distanceAlongTrack(waypoint: Waypoint, trackPoints: List<TrackPoint>): Double? {
        if (trackPoints.size < 2) return null

        var cumulative = 0.0
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        trackPoints.forEachIndexed { index, point ->
            if (index > 0) {
                val previous = trackPoints[index - 1]
                cumulative += GeoUtils.distanceMeters(
                    previous.latitude,
                    previous.longitude,
                    point.latitude,
                    point.longitude,
                )
            }
            val distance = GeoUtils.distanceMeters(
                point.latitude,
                point.longitude,
                waypoint.latitude,
                waypoint.longitude,
            )
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }

        var total = 0.0
        for (index in 1 until trackPoints.size) {
            val previous = trackPoints[index - 1]
            val point = trackPoints[index]
            total += GeoUtils.distanceMeters(
                previous.latitude,
                previous.longitude,
                point.latitude,
                point.longitude,
            )
        }

        // The cumulative distance of the sample the waypoint belongs to.
        var along = 0.0
        for (index in 1..bestIndex) {
            val previous = trackPoints[index - 1]
            val point = trackPoints[index]
            along += GeoUtils.distanceMeters(
                previous.latitude,
                previous.longitude,
                point.latitude,
                point.longitude,
            )
        }
        return along.coerceIn(0.0, total)
    }
}
