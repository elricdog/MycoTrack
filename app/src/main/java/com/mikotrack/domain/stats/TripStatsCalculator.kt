package com.mikotrack.domain.stats

import com.mikotrack.core.GeoUtils
import com.mikotrack.domain.model.ElevationPoint
import com.mikotrack.domain.model.ElevationProfile
import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.TripStats
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Derives the statistics and the elevation profile of a trip from its GPS samples.
 *
 * The engine is intentionally deterministic and side-effect free so it can be unit tested
 * and reused for both live recording and historical trips.
 */
object TripStatsCalculator {

    /** Altitude changes smaller than this (metres) are ignored to suppress GPS noise. */
    const val ELEVATION_THRESHOLD_METERS = 3.0

    /** A sample counts as "moving" when it is faster than this (metres per second). */
    const val MOVING_SPEED_THRESHOLD_MPS = 0.4

    /** Computes the aggregated statistics of a list of GPS samples. */
    fun compute(
        points: List<TrackPoint>,
        startTime: Long,
        endTime: Long?,
        photoCount: Int = 0,
        waypointCount: Int = 0,
    ): TripStats {
        if (points.isEmpty()) {
            return TripStats(
                elapsedMillis = (endTime ?: startTime) - startTime,
                photoCount = photoCount,
                waypointCount = waypointCount,
            )
        }

        var distance = 0.0
        var gain = 0.0
        var loss = 0.0
        var movingTime = 0L
        var stationaryTime = 0L
        var maxSpeed = 0.0
        var minAltitude = Double.MAX_VALUE
        var maxAltitude = -Double.MAX_VALUE
        var currentAltitude: Double? = null

        var referenceAltitude: Double? = null

        points.forEachIndexed { index, point ->
            val altitude = point.altitude
            if (altitude != null) {
                currentAltitude = altitude
                minAltitude = min(minAltitude, altitude)
                maxAltitude = max(maxAltitude, altitude)
                val reference = referenceAltitude
                if (reference == null) {
                    referenceAltitude = altitude
                } else {
                    val delta = altitude - reference
                    if (delta > ELEVATION_THRESHOLD_METERS) {
                        gain += delta
                        referenceAltitude = altitude
                    } else if (delta < -ELEVATION_THRESHOLD_METERS) {
                        loss += -delta
                        referenceAltitude = altitude
                    }
                }
            }

            val speed = point.speed?.toDouble() ?: 0.0
            if (speed > maxSpeed) maxSpeed = speed

            if (index > 0) {
                val previous = points[index - 1]
                distance += GeoUtils.distanceMeters(
                    previous.latitude,
                    previous.longitude,
                    point.latitude,
                    point.longitude,
                )
                val deltaMillis = (point.timestamp - previous.timestamp).coerceAtLeast(0)
                if (speed >= MOVING_SPEED_THRESHOLD_MPS) movingTime += deltaMillis else stationaryTime += deltaMillis
            }
        }

        val elapsed = (endTime ?: points.last().timestamp) - startTime
        val totalTime = max(movingTime + stationaryTime, 1L)
        val averageSpeed = if (movingTime > 0) distance / (movingTime / 1000.0) else distance / (totalTime / 1000.0)

        return TripStats(
            elapsedMillis = elapsed,
            distanceMeters = distance,
            currentSpeedMps = points.last().speed?.toDouble() ?: 0.0,
            averageSpeedMps = averageSpeed,
            maxSpeedMps = maxSpeed,
            currentAltitude = currentAltitude,
            minAltitude = if (minAltitude == Double.MAX_VALUE) null else minAltitude,
            maxAltitude = if (maxAltitude == -Double.MAX_VALUE) null else maxAltitude,
            elevationGain = gain,
            elevationLoss = loss,
            movingTimeMillis = movingTime,
            stationaryTimeMillis = stationaryTime,
            photoCount = photoCount,
            waypointCount = waypointCount,
            pointCount = points.size,
        )
    }

    /**
     * Builds the elevation profile of a trip.
     *
     * Samples closer together than [minSampleDistance] are collapsed so that a stationary
     * user does not generate thousands of identical profile points.
     */
    fun buildElevationProfile(
        points: List<TrackPoint>,
        minSampleDistance: Double = 3.0,
    ): ElevationProfile {
        if (points.isEmpty()) return ElevationProfile()

        val profilePoints = mutableListOf<ElevationPoint>()
        var cumulative = 0.0
        var previous: TrackPoint? = null

        points.forEach { point ->
            val altitude = point.altitude
            if (previous == null) {
                cumulative = 0.0
                profilePoints += ElevationPoint(
                    distanceMeters = 0.0,
                    altitude = altitude ?: 0.0,
                    timestamp = point.timestamp,
                    latitude = point.latitude,
                    longitude = point.longitude,
                )
            } else {
                cumulative += GeoUtils.distanceMeters(
                    previous.latitude,
                    previous.longitude,
                    point.latitude,
                    point.longitude,
                )
                val delta = cumulative - profilePoints.last().distanceMeters
                if (delta >= minSampleDistance) {
                    profilePoints += ElevationPoint(
                        distanceMeters = cumulative,
                        altitude = altitude ?: profilePoints.last().altitude,
                        timestamp = point.timestamp,
                        latitude = point.latitude,
                        longitude = point.longitude,
                    )
                }
            }
            previous = point
        }

        // Always keep the very last sample so the profile ends at the real end of the trip.
        val last = points.last()
        val lastEntry = profilePoints.lastOrNull()
        if (lastEntry == null || abs(lastEntry.timestamp - last.timestamp) > 0) {
            profilePoints += ElevationPoint(
                distanceMeters = cumulative,
                altitude = last.altitude ?: lastEntry?.altitude ?: 0.0,
                timestamp = last.timestamp,
                latitude = last.latitude,
                longitude = last.longitude,
            )
        }

        var gain = 0.0
        var loss = 0.0
        var reference = profilePoints.first().altitude
        profilePoints.drop(1).forEach { point ->
            val delta = point.altitude - reference
            if (delta > ELEVATION_THRESHOLD_METERS) {
                gain += delta
                reference = point.altitude
            } else if (delta < -ELEVATION_THRESHOLD_METERS) {
                loss += -delta
                reference = point.altitude
            }
        }

        return ElevationProfile(
            points = profilePoints,
            minAltitude = profilePoints.minOf { it.altitude },
            maxAltitude = profilePoints.maxOf { it.altitude },
            totalDistance = cumulative,
            elevationGain = gain,
            elevationLoss = loss,
        )
    }
}
