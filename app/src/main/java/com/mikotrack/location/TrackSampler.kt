package com.mikotrack.location

import com.mikotrack.core.GeoUtils
import com.mikotrack.domain.model.TrackPoint

/**
 * Decides which smoothed positions are written to the track.
 *
 * The receiver is sampled every second, but the track only stores a point every
 * [intervalMillis] (the interval chosen in the settings), and only when the user really
 * moved: while the user stands still a point is stored at a slow pace on the very same
 * coordinates as the previous one, so the remaining noise neither draws a "spider web"
 * around the stop nor inflates the distance.
 */
class TrackSampler(
    private val intervalMillis: Long,
    private val minDistanceMeters: Double = 3.0,
    private val stationaryIntervalMillis: Long = 10_000L,
    /** A stationary estimate closer than this to the last point is anchored onto it. */
    private val anchorRadiusMeters: Double = 5.0,
) {

    /** The last point that was stored. */
    var lastStored: TrackPoint? = null
        private set

    /** Continues an existing track, for example after the service was restarted. */
    fun resumeFrom(point: TrackPoint?) {
        lastStored = point
    }

    /** Returns the point to store for [fix], or `null` when nothing has to be stored. */
    fun sample(fix: SmoothedFix): TrackPoint? {
        val last = lastStored
        val point = TrackPoint(
            latitude = fix.latitude,
            longitude = fix.longitude,
            altitude = fix.altitude,
            accuracy = fix.accuracy,
            speed = fix.speed,
            bearing = fix.bearing,
            timestamp = fix.timestamp,
        )
        if (last == null) return point.also { lastStored = it }

        val elapsed = fix.timestamp - last.timestamp
        if (elapsed < intervalMillis) return null

        val distance = GeoUtils.distanceMeters(last.latitude, last.longitude, fix.latitude, fix.longitude)
        val heartbeat = elapsed >= maxOf(intervalMillis, stationaryIntervalMillis)

        val stored = when {
            fix.isStationary && distance < anchorRadiusMeters ->
                if (heartbeat) point.copy(latitude = last.latitude, longitude = last.longitude, speed = 0f) else null
            distance >= minDistanceMeters || heartbeat || fix.wasReset -> point
            else -> null
        }
        return stored?.also { lastStored = it }
    }
}
