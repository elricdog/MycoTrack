package com.mikotrack.domain.stats

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Tests of the analysis of a mushroom outing. */
class TripAnalyzerTest {

    private val zone: ZoneId = ZoneOffset.UTC

    /** 15 October 2026, 08:00 UTC, in central Catalonia. */
    private val start = LocalDateTime.of(2026, 10, 15, 8, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val baseLatitude = 42.0
    private val baseLongitude = 1.5

    /** The length of one degree of latitude on the sphere used by GeoUtils. */
    private val metersPerDegree = 111_195.08

    private fun point(
        northMeters: Double,
        seconds: Long,
        altitude: Double? = null,
        speed: Float? = null,
        eastMeters: Double = 0.0,
    ) = TrackPoint(
        latitude = baseLatitude + northMeters / metersPerDegree,
        longitude = baseLongitude + eastMeters / (metersPerDegree * Math.cos(Math.toRadians(baseLatitude))),
        altitude = altitude,
        speed = speed,
        timestamp = start + seconds * 1_000,
    )

    private fun waypoint(
        seconds: Long,
        category: String = "MUSHROOM_FOUND",
        altitude: Double? = null,
        northMeters: Double = 0.0,
        imagePath: String? = null,
    ) = Waypoint(
        tripId = 1,
        latitude = baseLatitude + northMeters / metersPerDegree,
        longitude = baseLongitude,
        altitude = altitude,
        timestamp = start + seconds * 1_000,
        description = "",
        category = category,
        imagePath = imagePath,
    )

    @Test
    fun `an empty trip produces an empty analysis`() {
        val analysis = TripAnalyzer.analyze(emptyList(), emptyList(), startTime = start, endTime = start, zoneId = zone)

        assertEquals(0, analysis.pointCount)
        assertEquals(0L, analysis.movement.movingTimeMillis)
        assertNull(analysis.terrain.minAltitude)
        assertFalse(analysis.route.isLoop)
        assertFalse(analysis.finds.hasFinds)
        assertNull(analysis.daylight)
    }

    @Test
    fun `time is split between moving and stopped`() {
        // 10 minutes walking at 1 m/s, 10 minutes standing still and 5 more minutes walking.
        val points = buildList {
            for (i in 0..10) add(point(northMeters = i * 60.0, seconds = i * 60L))
            for (i in 1..10) add(point(northMeters = 600.0, seconds = 600L + i * 60L))
            for (i in 1..5) add(point(northMeters = 600.0 + i * 60.0, seconds = 1_200L + i * 60L))
        }
        val movement = TripAnalyzer.analyze(points, emptyList(), zoneId = zone).movement

        assertEquals(15 * 60_000L, movement.movingTimeMillis)
        assertEquals(10 * 60_000L, movement.stoppedTimeMillis)
        assertEquals(10 * 60_000L, movement.longestStopMillis)
        assertEquals(900.0, movement.movingDistanceMeters, 1.0)
        assertEquals(1.0, movement.averageMovingSpeedMps, 0.01)
        assertEquals(1_000_000.0, movement.paceMillisPerKm!!.toDouble(), 5_000.0)
        assertEquals(0.6, movement.movingFraction, 0.001)
    }

    @Test
    fun `the reported speed wins over the jitter of the positions`() {
        // The positions jitter by 5 m every 5 s (1 m/s) but the receiver reports 0 m/s.
        val points = (0..20).map { i ->
            point(northMeters = if (i % 2 == 0) 0.0 else 5.0, seconds = i * 5L, speed = 0f)
        }
        val movement = TripAnalyzer.analyze(points, emptyList(), zoneId = zone).movement

        assertEquals(0L, movement.movingTimeMillis)
        assertEquals(100_000L, movement.stoppedTimeMillis)
    }

    @Test
    fun `long gaps between samples count as a stop`() {
        val points = listOf(point(0.0, 0), point(600.0, 1_200))
        val movement = TripAnalyzer.analyze(points, emptyList(), zoneId = zone).movement

        assertEquals(0L, movement.movingTimeMillis)
        assertEquals(1_200_000L, movement.stoppedTimeMillis)
    }

    @Test
    fun `isolated GPS spikes are removed and do not set the max speed`() {
        val points = listOf(
            point(0.0, 0, speed = 1.2f),
            point(10.0, 10, speed = 1.2f),
            point(2_000.0, 20, speed = 90f),
            point(20.0, 30, speed = 1.3f),
            point(30.0, 40, speed = 1.1f),
            point(40.0, 50, speed = 1.2f),
        )
        val analysis = TripAnalyzer.analyze(points, emptyList(), zoneId = zone)

        assertEquals(1, analysis.movement.discardedSpikes)
        assertEquals(40.0, analysis.movement.totalDistanceMeters, 1.0)
        assertTrue(analysis.movement.maxSpeedMps < 2.0)
        assertTrue(analysis.route.maxDistanceFromStartMeters < 50.0)
        assertEquals(6, analysis.pointCount)
    }

    @Test
    fun `the median filter ignores a single speed spike`() {
        assertEquals(1.5, TripAnalyzer.filteredMaxSpeed(listOf(1.0, 1.2, 40.0, 1.5, 1.1)), 1e-9)
        assertEquals(3.0, TripAnalyzer.filteredMaxSpeed(listOf(1.0, 3.0, 3.0, 1.0)), 1e-9)
        assertEquals(0.0, TripAnalyzer.filteredMaxSpeed(emptyList()), 1e-9)
    }

    @Test
    fun `altitudes and the steepest climb are measured`() {
        // 500 m flat, then 200 m climbing 40 m (20 %), then flat again.
        val points = buildList {
            for (i in 0..10) add(point(i * 50.0, i * 50L, altitude = 800.0))
            for (i in 1..4) add(point(500.0 + i * 50.0, 500L + i * 50L, altitude = 800.0 + i * 10.0))
            for (i in 1..4) add(point(700.0 + i * 50.0, 700L + i * 50L, altitude = 840.0))
        }
        val terrain = TripAnalyzer.analyze(points, emptyList(), zoneId = zone).terrain

        assertEquals(800.0, terrain.minAltitude!!, 1e-9)
        assertEquals(840.0, terrain.maxAltitude!!, 1e-9)
        assertEquals(40.0, terrain.ascentMeters, 1e-6)
        assertEquals(0.0, terrain.descentMeters, 1e-6)
        val climb = terrain.steepestClimb
        assertNotNull(climb)
        assertEquals(20.0, climb!!.gradePercent, 0.5)
        assertTrue(climb.startDistanceMeters in 495.0..605.0)
    }

    @Test
    fun `a trip without altitudes has no terrain figures`() {
        val terrain = TripAnalyzer.analyze(listOf(point(0.0, 0), point(100.0, 100)), emptyList(), zoneId = zone).terrain

        assertNull(terrain.minAltitude)
        assertNull(terrain.steepestClimb)
    }

    @Test
    fun `a route that ends near the start is a loop`() {
        val loop = listOf(
            point(0.0, 0),
            point(400.0, 400),
            point(400.0, 800, eastMeters = 400.0),
            point(50.0, 1_200, eastMeters = 30.0),
        )
        val route = TripAnalyzer.analyze(loop, emptyList(), zoneId = zone).route

        assertTrue(route.isLoop)
        assertEquals(58.3, route.endToStartMeters!!, 1.0)
        assertEquals(565.7, route.maxDistanceFromStartMeters, 1.0)
    }

    @Test
    fun `a route that ends far away is not a loop, nor one that never left`() {
        val oneWay = listOf(point(0.0, 0), point(500.0, 500), point(1_000.0, 1_000))
        assertFalse(TripAnalyzer.analyze(oneWay, emptyList(), zoneId = zone).route.isLoop)

        val stayed = listOf(point(0.0, 0), point(50.0, 100), point(10.0, 200))
        assertFalse(TripAnalyzer.analyze(stayed, emptyList(), zoneId = zone).route.isLoop)
    }

    @Test
    fun `the distance from the parking waypoint is measured`() {
        val points = listOf(point(100.0, 0), point(600.0, 500), point(100.0, 1_000))
        val parking = waypoint(seconds = 0, category = "PARKING", northMeters = 0.0)
        val route = TripAnalyzer.analyze(points, listOf(parking), zoneId = zone).route

        assertEquals(600.0, route.maxDistanceFromParkingMeters!!, 1.0)
        assertEquals(500.0, route.maxDistanceFromStartMeters, 1.0)
    }

    @Test
    fun `finds are counted, timed and located`() {
        // A 2 h walk north at 0.5 m/s, the altitude rising 1 m every minute.
        val points = (0..120).map { i -> point(i * 30.0, i * 60L, altitude = 900.0 + i, speed = 0.5f) }
        val waypoints = listOf(
            waypoint(seconds = 1_800, altitude = 930.0),
            waypoint(seconds = 2_400, category = "INTERESTING_MUSHROOM"),
            waypoint(seconds = 3_000, altitude = 950.0, imagePath = "a.jpg"),
            waypoint(seconds = 4_000, category = "PARKING"),
            waypoint(seconds = 5_000, category = "MUSHROOM_AREA"),
        )
        val analysis = TripAnalyzer.analyze(
            points,
            waypoints,
            startTime = start,
            endTime = start + 7_200_000,
            zoneId = zone,
        )
        val finds = analysis.finds

        assertEquals(3, finds.findCount)
        assertEquals(1.5, finds.findsPerHour!!, 1e-9)
        assertEquals(3 / 3.6, finds.findsPerKm!!, 0.01)
        assertEquals(1_800_000L, finds.timeToFirstFindMillis)
        assertEquals(start + 1_800_000L, finds.firstFindMillis)
        assertEquals(start + 3_000_000L, finds.lastFindMillis)
        // The second find has no altitude: it takes the one of the track at that moment.
        assertEquals(930.0, finds.minAltitude!!, 1e-9)
        assertEquals(950.0, finds.maxAltitude!!, 1e-9)
        assertEquals(940.0, finds.averageAltitude!!, 1e-9)
        // From 900 m to 1500 m along the track, in two gaps.
        assertEquals(300.0, finds.averageDistanceBetweenFindsMeters!!, 1.0)
        assertEquals("MUSHROOM_FOUND" to 2, finds.countsByCategory.first())
        assertEquals(4, finds.countsByCategory.size)
        assertEquals(1, analysis.photoCount)
        assertEquals(5, analysis.waypointCount)
    }

    @Test
    fun `the most productive time slot and stretch are found`() {
        val points = (0..120).map { i -> point(i * 30.0, i * 60L) }
        val waypoints = listOf(
            waypoint(seconds = 600),
            waypoint(seconds = 3_700),
            waypoint(seconds = 3_800),
            waypoint(seconds = 4_000),
        )
        val finds = TripAnalyzer.analyze(
            points,
            waypoints,
            startTime = start,
            endTime = start + 7_200_000,
            zoneId = zone,
        ).finds

        // Two hours give 15 minute slots.
        assertEquals(8, finds.timeSlots.size)
        assertEquals(4, finds.timeSlots.sumOf { it.finds })
        val best = finds.bestTimeSlot!!
        assertEquals(start + 3_600_000L, best.startMillis)
        assertEquals(3, best.finds)
        // The finds are at 300 m, 1860 m, 1890 m and 2010 m along the track.
        val segment = finds.bestSegment!!
        assertEquals(1_500.0, segment.startMeters, 1e-9)
        assertEquals(2, segment.finds)
    }

    @Test
    fun `the time slots grow with long trips`() {
        val slots = TripAnalyzer.timeSlots(emptyList(), start, start + 10 * 3_600_000L)

        assertEquals(10, slots.size)
        assertEquals(3_600_000L, slots.first().endMillis - slots.first().startMillis)
    }

    @Test
    fun `finds are grouped by altitude band`() {
        val bands = TripAnalyzer.altitudeBands(listOf(905.0, 940.0, 1_010.0, 1_049.0))

        assertEquals(listOf(900.0, 950.0, 1_000.0), bands.map { it.minAltitude })
        assertEquals(listOf(2, 0, 2), bands.map { it.finds })
    }

    @Test
    fun `a trip without finds keeps the other waypoints`() {
        val points = listOf(point(0.0, 0), point(100.0, 100))
        val finds = TripAnalyzer.analyze(points, listOf(waypoint(50, category = "PARKING")), zoneId = zone).finds

        assertFalse(finds.hasFinds)
        assertNull(finds.findsPerHour)
        assertNull(finds.bestTimeSlot)
        assertTrue(finds.timeSlots.isEmpty())
        assertEquals(listOf("PARKING" to 1), finds.countsByCategory)
    }

    @Test
    fun `the daylight left at the end is computed`() {
        val points = listOf(point(0.0, 0), point(100.0, 3_600))
        val daylight = TripAnalyzer.analyze(points, emptyList(), zoneId = zone).daylight!!

        // In mid-October the sun rises around 06:00 UTC and sets around 17:00 UTC there.
        assertTrue(daylight.sunriseMillis!! < start)
        assertFalse(daylight.startedBeforeSunrise)
        assertFalse(daylight.endedAfterSunset)
        assertTrue(daylight.daylightLeftAtEndMillis!! in 7 * 3_600_000L..9 * 3_600_000L)
    }

    @Test
    fun `a trip is ranked among the trips of the user`() {
        val trips = listOf(
            TripSummary(1, distanceMeters = 5_000.0, durationMillis = 3_600_000, finds = 12),
            TripSummary(2, distanceMeters = 8_000.0, durationMillis = 7_200_000, finds = 3),
            TripSummary(3, distanceMeters = 2_000.0, durationMillis = 1_800_000, finds = 0),
        )
        val ranking = TripAnalyzer.rank(1, trips)!!

        assertEquals(3, ranking.totalTrips)
        assertEquals(1, ranking.findsRank)
        assertEquals(2, ranking.distanceRank)
        assertEquals(2, ranking.durationRank)
        assertNull(TripAnalyzer.rank(99, trips))
        assertFalse(TripAnalyzer.rank(1, trips.take(1))!!.isMeaningful)
    }
}
