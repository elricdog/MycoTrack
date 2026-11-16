package com.mikotrack.domain.model

/** Lifecycle of a recorded trip. */
enum class TripStatus {
    /** Currently being recorded. */
    RECORDING,

    /** Finished normally by the user. */
    COMPLETED,

    /** Recovered after an unexpected interruption of the application. */
    RECOVERED,
}

/** A single GPS sample stored for a trip. */
data class TrackPoint(
    val id: Long = 0,
    val tripId: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val timestamp: Long,
)

/** A geotagged photo / point of interest attached to a trip. */
data class Waypoint(
    val id: Long = 0,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val timestamp: Long,
    val description: String,
    val category: String,
    val imagePath: String?,
)

/** A user visible category for waypoints. */
data class WaypointCategory(
    val id: Long = 0,
    val name: String,
    val color: Long,
    val symbol: String,
    val builtIn: Boolean,
)

/** A recorded or in-progress trip with its aggregated statistics. */
data class Trip(
    val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long?,
    val durationMillis: Long,
    val distanceMeters: Double,
    val elevationGain: Double,
    val elevationLoss: Double,
    val minAltitude: Double?,
    val maxAltitude: Double?,
    val notes: String,
    val status: TripStatus,
    val photoCount: Int,
    val waypointCount: Int,
    val lastUpdated: Long,
    /** The colour chosen for the route on the map (ARGB), or `null` for an automatic one. */
    val color: Int? = null,
) {
    val isRecording: Boolean get() = status == TripStatus.RECORDING
}

/** Live statistics computed from the track of the running trip. */
data class TripStats(
    val elapsedMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val currentSpeedMps: Double = 0.0,
    val averageSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val currentAltitude: Double? = null,
    val minAltitude: Double? = null,
    val maxAltitude: Double? = null,
    val elevationGain: Double = 0.0,
    val elevationLoss: Double = 0.0,
    val movingTimeMillis: Long = 0,
    val stationaryTimeMillis: Long = 0,
    val photoCount: Int = 0,
    val waypointCount: Int = 0,
    val pointCount: Int = 0,
)

/** One sample of the elevation profile: distance along the track and altitude. */
data class ElevationPoint(
    val distanceMeters: Double,
    val altitude: Double,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
)

/** The elevation profile of a trip. */
data class ElevationProfile(
    val points: List<ElevationPoint> = emptyList(),
    val minAltitude: Double = 0.0,
    val maxAltitude: Double = 0.0,
    val totalDistance: Double = 0.0,
    val elevationGain: Double = 0.0,
    val elevationLoss: Double = 0.0,
) {
    val isEmpty: Boolean get() = points.isEmpty()

    /** Altitude at a given distance along the track, interpolated between samples. */
    fun altitudeAtDistance(distance: Double): Double? {
        if (points.isEmpty()) return null
        if (distance <= points.first().distanceMeters) return points.first().altitude
        if (distance >= points.last().distanceMeters) return points.last().altitude
        val index = points.indexOfFirst { it.distanceMeters >= distance }
        if (index <= 0) return points.first().altitude
        val previous = points[index - 1]
        val current = points[index]
        val span = current.distanceMeters - previous.distanceMeters
        if (span <= 0.0) return current.altitude
        val ratio = (distance - previous.distanceMeters) / span
        return previous.altitude + (current.altitude - previous.altitude) * ratio
    }

    /** Index of the profile sample closest to the given distance. */
    fun indexAtDistance(distance: Double): Int {
        if (points.isEmpty()) return -1
        var best = 0
        var bestDelta = Double.MAX_VALUE
        points.forEachIndexed { index, point ->
            val delta = kotlin.math.abs(point.distanceMeters - distance)
            if (delta < bestDelta) {
                bestDelta = delta
                best = index
            }
        }
        return best
    }
}
