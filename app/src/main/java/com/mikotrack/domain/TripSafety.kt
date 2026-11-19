package com.mikotrack.domain

import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.Waypoint
import kotlin.math.roundToLong

/** The safety warnings shown while a trip is being recorded; each one at most once per trip. */
enum class SafetyWarning {
    SUNSET_60,
    SUNSET_20,
    SUNSET_TOO_FAR,
    BATTERY_20,
    BATTERY_10,
}

/**
 * Decides when the sunset and low battery warnings are due.
 *
 * It is pure so that it can be tested without a device: the caller supplies the time, the
 * sunset and the battery state and posts the notifications it returns.
 */
class SafetyWarningTracker(issued: Set<SafetyWarning> = emptySet()) {

    private val _issued = issued.toMutableSet()

    /** The warnings that have already been given during the trip. */
    val issued: Set<SafetyWarning> get() = _issued

    /**
     * The sunset warning that is due now, if any. The 60 minute warning is skipped when the
     * trip starts less than 20 minutes before sunset, and nothing is said after sunset.
     */
    fun sunsetWarning(nowMillis: Long, sunsetMillis: Long?): SafetyWarning? {
        if (sunsetMillis == null) return null
        val remaining = sunsetMillis - nowMillis
        if (remaining <= 0) return null
        return when {
            remaining <= SUNSET_LATE_MILLIS -> issueOnce(SafetyWarning.SUNSET_20, SafetyWarning.SUNSET_60)
            remaining <= SUNSET_EARLY_MILLIS -> issueOnce(SafetyWarning.SUNSET_60)
            else -> null
        }
    }

    /**
     * The warning shown when the walk back to the car would take longer than the
     * daylight that is left. [walkMillis] is the estimated walking time to the car;
     * nothing is said when there is no parking waypoint or the sun has already set.
     */
    fun unreachableWarning(nowMillis: Long, sunsetMillis: Long?, walkMillis: Long?): SafetyWarning? {
        if (sunsetMillis == null || walkMillis == null) return null
        val remaining = sunsetMillis - nowMillis
        if (remaining <= 0) return null
        return if (walkMillis > remaining + SAFE_MARGIN_MILLIS) SafetyWarning.SUNSET_TOO_FAR.takeIf { it !in _issued }?.also { _issued += it }
        else null
    }

    /** The low battery warning that is due now, if any. Nothing is said while charging. */
    fun batteryWarning(levelPercent: Int?, charging: Boolean): SafetyWarning? {
        if (levelPercent == null || charging) return null
        return when {
            levelPercent <= BATTERY_CRITICAL -> issueOnce(SafetyWarning.BATTERY_10, SafetyWarning.BATTERY_20)
            levelPercent <= BATTERY_LOW -> issueOnce(SafetyWarning.BATTERY_20)
            else -> null
        }
    }

    /** Gives [warning] unless it was already given; [alsoCovers] are no longer needed after it. */
    private fun issueOnce(warning: SafetyWarning, vararg alsoCovers: SafetyWarning): SafetyWarning? {
        if (warning in _issued) return null
        _issued += warning
        _issued += alsoCovers
        return warning
    }

    companion object {
        const val SUNSET_EARLY_MILLIS = 60 * 60_000L
        const val SUNSET_LATE_MILLIS = 20 * 60_000L
        const val BATTERY_LOW = 20
        const val BATTERY_CRITICAL = 10
        /** The warning tolerates a slight underestimate of the walking time before alarming. */
        const val SAFE_MARGIN_MILLIS = 10 * 60_000L
    }
}

/**
 * Decides when the periodic "are you ok?" check-in of a recorded trip is due, and which
 * intervals the user can choose from. Pure so it can be tested without a device.
 */
object CheckInPolicy {

    /** The check-in intervals the settings offer, in minutes. */
    val CHOICES_MINUTES = intArrayOf(30, 60, 120, 180, 240)

    const val DEFAULT_MINUTES = 120

    /** The moment the next check-in is due. */
    fun deadlineMillis(nowMillis: Long, intervalMinutes: Int): Long =
        nowMillis + intervalMinutes.coerceAtLeast(5) * 60_000L
}

/** Rough walking time estimates for the way back. */
object WalkingEstimate {

    /** A relaxed walking pace in the forest, in kilometres per hour. */
    const val WALKING_SPEED_KMH = 4.0

    /** How much longer a real path is than the straight line. */
    const val DETOUR_FACTOR = 1.3

    /** The time needed to walk back over a straight-line distance, in milliseconds. */
    fun millisFor(straightLineMeters: Double): Long {
        val meters = straightLineMeters.coerceAtLeast(0.0) * DETOUR_FACTOR
        val metersPerMilli = WALKING_SPEED_KMH * 1_000.0 / 3_600_000.0
        return (meters / metersPerMilli).roundToLong()
    }
}

/** Finds the waypoint where the car was parked. */
object ParkingSelector {

    /** How long the parking of a previous trip is still offered when nothing is recorded. */
    const val RECENT_TRIP_MILLIS = 12 * 60 * 60_000L

    /**
     * The parking waypoint to go back to.
     *
     * While recording it is the last parking waypoint of the recorded trip. Otherwise it is
     * the last parking waypoint of [latestTrip] (the most recent trip) when that trip ended
     * less than [RECENT_TRIP_MILLIS] ago.
     */
    fun select(
        recordingTripId: Long?,
        recordingWaypoints: List<Waypoint>,
        latestTrip: Trip?,
        latestTripWaypoints: List<Waypoint>,
        nowMillis: Long,
        parkingCategory: String,
    ): Waypoint? {
        if (recordingTripId != null) {
            return recordingWaypoints.lastParking(recordingTripId, parkingCategory)
        }
        val trip = latestTrip ?: return null
        val endedAt = trip.endTime ?: trip.lastUpdated
        if (nowMillis - endedAt >= RECENT_TRIP_MILLIS) return null
        return latestTripWaypoints.lastParking(trip.id, parkingCategory)
    }

    private fun List<Waypoint>.lastParking(tripId: Long, category: String): Waypoint? =
        filter { it.tripId == tripId && it.category == category }.maxByOrNull { it.timestamp }
}
