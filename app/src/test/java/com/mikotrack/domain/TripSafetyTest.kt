package com.mikotrack.domain

import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStatus
import com.mikotrack.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests of the sunset and battery warnings, the walking estimate and the parking choice. */
class TripSafetyTest {

    private val minute = 60_000L
    private val sunset = 1_000_000_000L

    @Test
    fun `sunset warnings are given at 60 and 20 minutes, each once`() {
        val tracker = SafetyWarningTracker()
        assertNull(tracker.sunsetWarning(sunset - 90 * minute, sunset))
        assertEquals(SafetyWarning.SUNSET_60, tracker.sunsetWarning(sunset - 59 * minute, sunset))
        assertNull(tracker.sunsetWarning(sunset - 40 * minute, sunset))
        assertEquals(SafetyWarning.SUNSET_20, tracker.sunsetWarning(sunset - 20 * minute, sunset))
        assertNull(tracker.sunsetWarning(sunset - 5 * minute, sunset))
    }

    @Test
    fun `a trip started shortly before sunset only gets the late warning`() {
        val tracker = SafetyWarningTracker()
        assertEquals(SafetyWarning.SUNSET_20, tracker.sunsetWarning(sunset - 10 * minute, sunset))
        assertNull(tracker.sunsetWarning(sunset - 9 * minute, sunset))
    }

    @Test
    fun `no sunset warning after sunset or without a sunset`() {
        val tracker = SafetyWarningTracker()
        assertNull(tracker.sunsetWarning(sunset + minute, sunset))
        assertNull(tracker.sunsetWarning(sunset, null))
    }

    @Test
    fun `battery warnings at 20 and 10 percent, each once, never while charging`() {
        val tracker = SafetyWarningTracker()
        assertNull(tracker.batteryWarning(45, charging = false))
        assertNull(tracker.batteryWarning(19, charging = true))
        assertEquals(SafetyWarning.BATTERY_20, tracker.batteryWarning(20, charging = false))
        assertNull(tracker.batteryWarning(15, charging = false))
        assertEquals(SafetyWarning.BATTERY_10, tracker.batteryWarning(10, charging = false))
        assertNull(tracker.batteryWarning(5, charging = false))
    }

    @Test
    fun `issued warnings survive a restart of the tracker`() {
        val tracker = SafetyWarningTracker(setOf(SafetyWarning.BATTERY_20))
        assertNull(tracker.batteryWarning(18, charging = false))
    }

    @Test
    fun `walking time uses 4 km per hour and a 1_3 detour factor`() {
        // 2 km in a straight line -> 2.6 km walked -> 39 minutes.
        assertEquals(39 * minute, WalkingEstimate.millisFor(2_000.0))
    }

    @Test
    fun `the unreachable warning fires when the walk is longer than the daylight left`() {
        val tracker = SafetyWarningTracker()
        // 50 minutes of walking, 85 minutes of daylight left: inside the 10 minute margin.
        assertNull(tracker.unreachableWarning(sunset - 85 * minute, sunset, 50 * minute))
        assertEquals(SafetyWarning.SUNSET_TOO_FAR, tracker.unreachableWarning(sunset - 30 * minute, sunset, 50 * minute))
        // Only once per trip.
        assertNull(tracker.unreachableWarning(sunset - 10 * minute, sunset, 50 * minute))
    }

    @Test
    fun `the unreachable warning needs both a sunset and a walk time`() {
        val tracker = SafetyWarningTracker()
        assertNull(tracker.unreachableWarning(sunset - minute, null, 50 * minute))
        assertNull(tracker.unreachableWarning(sunset - minute, sunset, null))
        assertNull(tracker.unreachableWarning(sunset + minute, sunset, 50 * minute))
    }

    @Test
    fun `the check-in deadline adds the chosen interval, clamped to at least five minutes`() {
        val now = 10_000_000L
        assertEquals(now + 120 * minute, CheckInPolicy.deadlineMillis(now, 120))
        assertEquals(now + 5 * minute, CheckInPolicy.deadlineMillis(now, 0))
        assertEquals(listOf(30, 60, 120, 180, 240), CheckInPolicy.CHOICES_MINUTES.toList())
    }

    private fun waypoint(id: Long, tripId: Long, category: String, timestamp: Long) = Waypoint(
        id = id,
        tripId = tripId,
        latitude = 41.0,
        longitude = 2.0,
        altitude = null,
        timestamp = timestamp,
        description = "",
        category = category,
        imagePath = null,
    )

    private fun trip(id: Long, endTime: Long?) = Trip(
        id = id,
        name = "Trip",
        startTime = 0,
        endTime = endTime,
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
        lastUpdated = endTime ?: 0,
    )

    @Test
    fun `while recording the last parking of the recorded trip is used`() {
        val waypoints = listOf(
            waypoint(1, 5, "PARKING", 100),
            waypoint(2, 5, "MUSHROOM_FOUND", 200),
            waypoint(3, 5, "PARKING", 300),
        )
        assertEquals(3L, ParkingSelector.select(5, waypoints, null, emptyList(), 1_000, "PARKING")?.id)
        assertNull(ParkingSelector.select(5, waypoints.filter { it.category != "PARKING" }, null, emptyList(), 0, "PARKING"))
    }

    @Test
    fun `without a recording the parking of a recent last trip is used`() {
        val now = 100 * 60 * minute
        val parking = listOf(waypoint(7, 9, "PARKING", 0))
        val recent = trip(9, endTime = now - 11 * 60 * minute)
        val old = trip(9, endTime = now - 13 * 60 * minute)
        assertEquals(7L, ParkingSelector.select(null, emptyList(), recent, parking, now, "PARKING")?.id)
        assertNull(ParkingSelector.select(null, emptyList(), old, parking, now, "PARKING"))
        assertNull(ParkingSelector.select(null, emptyList(), null, emptyList(), now, "PARKING"))
    }
}
