package com.mikotrack.domain.stats

/** How the time of a trip was split between walking and standing still. */
data class MovementAnalysis(
    val movingTimeMillis: Long = 0,
    val stoppedTimeMillis: Long = 0,
    val movingDistanceMeters: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    /** The average speed while moving, in metres per second. */
    val averageMovingSpeedMps: Double = 0.0,
    /** The maximum speed once isolated GPS spikes have been filtered out. */
    val maxSpeedMps: Double = 0.0,
    /** The time needed to walk one kilometre while moving, or `null` when too short. */
    val paceMillisPerKm: Long? = null,
    /** The longest uninterrupted stop. */
    val longestStopMillis: Long = 0,
    /** The number of GPS samples discarded as spikes. */
    val discardedSpikes: Int = 0,
) {
    /** The fraction (0..1) of the recorded time spent moving. */
    val movingFraction: Double
        get() {
            val total = movingTimeMillis + stoppedTimeMillis
            return if (total <= 0) 0.0 else movingTimeMillis.toDouble() / total
        }
}

/** The steepest climb of the route, measured over a minimum horizontal length. */
data class SteepestClimb(
    val gradePercent: Double,
    val gainMeters: Double,
    val lengthMeters: Double,
    /** Where the climb starts, as distance along the track. */
    val startDistanceMeters: Double,
)

/** The altitudes of the trip. */
data class TerrainAnalysis(
    val minAltitude: Double? = null,
    val maxAltitude: Double? = null,
    val averageAltitude: Double? = null,
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    val steepestClimb: SteepestClimb? = null,
)

/** The shape of the route: how far it went and whether it came back to the start. */
data class RouteShapeAnalysis(
    val maxDistanceFromStartMeters: Double = 0.0,
    /** The farthest distance from the parking waypoint, when the trip has one. */
    val maxDistanceFromParkingMeters: Double? = null,
    /** The straight distance between the first and the last GPS sample. */
    val endToStartMeters: Double? = null,
    val isLoop: Boolean = false,
)

/** The number of finds in a period of time of the trip. */
data class TimeSlot(val startMillis: Long, val endMillis: Long, val finds: Int)

/** The number of finds in a range of altitudes. */
data class AltitudeBand(val minAltitude: Double, val maxAltitude: Double, val finds: Int)

/** The number of finds in a stretch of the route, measured along the track. */
data class DistanceSegment(val startMeters: Double, val endMeters: Double, val finds: Int)

/** The mushroom finds of a trip. */
data class FindsAnalysis(
    val findCount: Int = 0,
    /** The number of waypoints of every category, the most frequent first. */
    val countsByCategory: List<Pair<String, Int>> = emptyList(),
    val findsPerHour: Double? = null,
    val findsPerKm: Double? = null,
    val firstFindMillis: Long? = null,
    val lastFindMillis: Long? = null,
    val timeToFirstFindMillis: Long? = null,
    val minAltitude: Double? = null,
    val maxAltitude: Double? = null,
    val averageAltitude: Double? = null,
    /** The average distance walked along the track between two consecutive finds. */
    val averageDistanceBetweenFindsMeters: Double? = null,
    val timeSlots: List<TimeSlot> = emptyList(),
    val bestTimeSlot: TimeSlot? = null,
    val altitudeBands: List<AltitudeBand> = emptyList(),
    val bestSegment: DistanceSegment? = null,
) {
    val hasFinds: Boolean get() = findCount > 0
}

/** The trip compared with the daylight of its day. */
data class DaylightAnalysis(
    val sunriseMillis: Long?,
    val sunsetMillis: Long?,
    val startMillis: Long,
    val endMillis: Long,
) {
    /** The daylight left when the trip ended; negative when it ended after sunset. */
    val daylightLeftAtEndMillis: Long? get() = sunsetMillis?.let { it - endMillis }
    val startedBeforeSunrise: Boolean get() = sunriseMillis != null && startMillis < sunriseMillis
    val endedAfterSunset: Boolean get() = sunsetMillis != null && endMillis > sunsetMillis
}

/** The complete analysis of a mushroom outing. */
data class TripAnalysis(
    val startMillis: Long = 0,
    val endMillis: Long = 0,
    val movement: MovementAnalysis = MovementAnalysis(),
    val terrain: TerrainAnalysis = TerrainAnalysis(),
    val route: RouteShapeAnalysis = RouteShapeAnalysis(),
    val finds: FindsAnalysis = FindsAnalysis(),
    val daylight: DaylightAnalysis? = null,
    val photoCount: Int = 0,
    val waypointCount: Int = 0,
    val pointCount: Int = 0,
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0)
}

/** The figures of a trip used to compare it with the other trips of the user. */
data class TripSummary(
    val tripId: Long,
    val distanceMeters: Double,
    val durationMillis: Long,
    val finds: Int,
)

/** The position (1 = best) of a trip among all the trips of the user. */
data class TripRanking(
    val totalTrips: Int,
    val findsRank: Int,
    val distanceRank: Int,
    val durationRank: Int,
) {
    /** Comparing makes sense only when there are other trips. */
    val isMeaningful: Boolean get() = totalTrips >= 2
}
