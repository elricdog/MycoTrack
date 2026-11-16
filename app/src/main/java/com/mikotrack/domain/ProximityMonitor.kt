package com.mikotrack.domain

import com.mikotrack.core.GeoUtils
import kotlin.math.max

/** A waypoint of a previous trip that can trigger the proximity alarm. */
data class ProximityTarget(
    val waypointId: Long,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val category: String,
    val tripName: String,
)

/** A waypoint the user has just walked into the radius of. */
data class ProximityHit(
    val target: ProximityTarget,
    val distanceMeters: Double,
)

/**
 * Decides when the user reaches a waypoint of a previous trip.
 *
 * Each waypoint alerts once when the user enters its radius. It is armed again only after
 * the user has moved clearly away from it ([rearmFactor] times the radius), so the GPS
 * jitter around the edge of the radius never makes the alarm ring over and over.
 */
class ProximityMonitor(private val rearmFactor: Double = 1.5) {

    private val inside = mutableSetOf<Long>()

    /**
     * Processes a new position and returns the waypoints that have just been reached,
     * the closest first.
     *
     * Fixes whose accuracy is much worse than the radius are ignored: they cannot tell
     * whether the user is really close to the waypoint.
     */
    fun update(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        radiusMeters: Double,
        targets: List<ProximityTarget>,
    ): List<ProximityHit> {
        inside.retainAll(targets.mapTo(HashSet()) { it.waypointId })
        if (accuracyMeters != null && accuracyMeters > max(radiusMeters, MIN_ACCURACY_TOLERANCE) * 2) {
            return emptyList()
        }
        val hits = mutableListOf<ProximityHit>()
        targets.forEach { target ->
            val distance = GeoUtils.distanceMeters(latitude, longitude, target.latitude, target.longitude)
            if (distance <= radiusMeters) {
                if (inside.add(target.waypointId)) hits += ProximityHit(target, distance)
            } else if (distance > radiusMeters * rearmFactor) {
                inside.remove(target.waypointId)
            }
        }
        return hits.sortedBy { it.distanceMeters }
    }

    /** Forgets every reached waypoint, so all of them can alert again. */
    fun reset() {
        inside.clear()
    }

    private companion object {
        /** Below this value the accuracy is always good enough. */
        const val MIN_ACCURACY_TOLERANCE = 25.0
    }
}
