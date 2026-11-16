package com.mikotrack.domain.stats

import com.mikotrack.core.GeoUtils
import com.mikotrack.domain.SunCalculator
import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Waypoint
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Analyses a mushroom outing from its GPS samples and waypoints.
 *
 * Pure and deterministic, so that every figure can be unit tested.
 */
object TripAnalyzer {

    /** The categories of the waypoints that count as a mushroom find. */
    val FIND_CATEGORIES = setOf("MUSHROOM_FOUND", "INTERESTING_MUSHROOM")

    /** The category of the parking waypoint. */
    const val PARKING_CATEGORY = "PARKING"

    /** A segment counts as moving when it is at least this fast (metres per second). */
    const val MOVING_SPEED_THRESHOLD_MPS = 0.5

    /** A gap between two samples longer than this is a pause, never movement. */
    const val MAX_MOVING_GAP_MILLIS = 5 * 60_000L

    /** Faster than this (about 180 km/h) a jump between two samples is a GPS spike. */
    const val MAX_PLAUSIBLE_SPEED_MPS = 50.0

    /** A route is a loop when it ends at most this far from where it started. */
    const val LOOP_RADIUS_METERS = 150.0

    /** ...and when it went at least this far away from the start. */
    const val LOOP_MIN_EXCURSION_METERS = 300.0

    /** The minimum horizontal length over which the steepest climb is measured. */
    const val CLIMB_WINDOW_METERS = 100.0

    /** The length of the stretches of route used to find the most productive one. */
    const val SEGMENT_LENGTH_METERS = 500.0

    /** The time slots of the chart, the shortest one that gives at most [MAX_SLOTS] slots. */
    private val SLOT_MINUTES = listOf(15L, 30L, 60L, 120L, 240L)
    private const val MAX_SLOTS = 12

    /** Analyses a trip. [startTime] and [endTime] default to the first and last samples. */
    fun analyze(
        points: List<TrackPoint>,
        waypoints: List<Waypoint>,
        startTime: Long? = null,
        endTime: Long? = null,
        photoCount: Int? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TripAnalysis {
        val sorted = points.sortedBy { it.timestamp }
        val clean = removeSpikes(sorted)
        val start = startTime
            ?: sorted.firstOrNull()?.timestamp
            ?: waypoints.minOfOrNull { it.timestamp }
            ?: 0L
        val end = max(
            endTime ?: sorted.lastOrNull()?.timestamp ?: waypoints.maxOfOrNull { it.timestamp } ?: start,
            start,
        )
        val cumulative = cumulativeDistances(clean)
        val movement = movement(clean, cumulative).copy(discardedSpikes = sorted.size - clean.size)
        return TripAnalysis(
            startMillis = start,
            endMillis = end,
            movement = movement,
            terrain = terrain(clean),
            route = route(clean, waypoints),
            finds = finds(clean, cumulative, waypoints, start, end),
            daylight = daylight(clean, waypoints, start, end, zoneId),
            photoCount = photoCount ?: waypoints.count { !it.imagePath.isNullOrBlank() },
            waypointCount = waypoints.size,
            pointCount = sorted.size,
        )
    }

    /** Compares a trip with all the trips of the user; `null` when it is not among them. */
    fun rank(tripId: Long, trips: List<TripSummary>): TripRanking? {
        val trip = trips.firstOrNull { it.tripId == tripId } ?: return null
        return TripRanking(
            totalTrips = trips.size,
            findsRank = 1 + trips.count { it.finds > trip.finds },
            distanceRank = 1 + trips.count { it.distanceMeters > trip.distanceMeters },
            durationRank = 1 + trips.count { it.durationMillis > trip.durationMillis },
        )
    }

    /** True when the waypoint is a mushroom find. */
    fun isFind(waypoint: Waypoint): Boolean = waypoint.category in FIND_CATEGORIES

    /**
     * Removes isolated GPS spikes: a sample that is reached and left at an impossible
     * speed while its two neighbours are plausibly close to each other.
     */
    fun removeSpikes(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size < 3) return points
        val result = ArrayList<TrackPoint>(points.size)
        result += points.first()
        for (index in 1 until points.lastIndex) {
            val previous = result.last()
            val current = points[index]
            val next = points[index + 1]
            val isSpike = segmentSpeed(previous, current) > MAX_PLAUSIBLE_SPEED_MPS &&
                segmentSpeed(current, next) > MAX_PLAUSIBLE_SPEED_MPS &&
                segmentSpeed(previous, next) <= MAX_PLAUSIBLE_SPEED_MPS
            if (!isSpike) result += current
        }
        result += points.last()
        return result
    }

    private fun distance(a: TrackPoint, b: TrackPoint): Double =
        GeoUtils.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    /** The speed derived from the positions of two samples, in metres per second. */
    private fun segmentSpeed(a: TrackPoint, b: TrackPoint): Double {
        val seconds = (b.timestamp - a.timestamp) / 1000.0
        val meters = distance(a, b)
        return when {
            seconds > 0 -> meters / seconds
            meters < 1.0 -> 0.0
            else -> Double.MAX_VALUE
        }
    }

    private fun cumulativeDistances(points: List<TrackPoint>): DoubleArray {
        val result = DoubleArray(points.size)
        for (index in 1 until points.size) {
            result[index] = result[index - 1] + distance(points[index - 1], points[index])
        }
        return result
    }

    private fun movement(points: List<TrackPoint>, cumulative: DoubleArray): MovementAnalysis {
        if (points.size < 2) return MovementAnalysis()
        var movingTime = 0L
        var stoppedTime = 0L
        var movingDistance = 0.0
        var currentStop = 0L
        var longestStop = 0L
        val speeds = ArrayList<Double>(points.size)
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val deltaMillis = (current.timestamp - previous.timestamp).coerceAtLeast(0)
            val meters = cumulative[index] - cumulative[index - 1]
            val derived = if (deltaMillis > 0) meters / (deltaMillis / 1000.0) else 0.0
            // The Doppler speed of the receiver is far less noisy than the positions,
            // which jitter by a few metres even when standing still.
            val speed = current.speed?.toDouble() ?: derived
            speeds += speed
            val moving = speed >= MOVING_SPEED_THRESHOLD_MPS && deltaMillis <= MAX_MOVING_GAP_MILLIS
            if (moving) {
                movingTime += deltaMillis
                movingDistance += meters
                currentStop = 0
            } else {
                stoppedTime += deltaMillis
                currentStop += deltaMillis
                longestStop = max(longestStop, currentStop)
            }
        }
        val averageMoving = if (movingTime > 0) movingDistance / (movingTime / 1000.0) else 0.0
        val pace = if (movingDistance >= 50.0 && movingTime > 0) {
            (movingTime / (movingDistance / 1000.0)).toLong()
        } else {
            null
        }
        return MovementAnalysis(
            movingTimeMillis = movingTime,
            stoppedTimeMillis = stoppedTime,
            movingDistanceMeters = movingDistance,
            totalDistanceMeters = cumulative.last(),
            averageMovingSpeedMps = averageMoving,
            maxSpeedMps = filteredMaxSpeed(speeds),
            paceMillisPerKm = pace,
            longestStopMillis = longestStop,
        )
    }

    /** The maximum of the speeds smoothed with a median of three, so single spikes vanish. */
    fun filteredMaxSpeed(speeds: List<Double>): Double {
        if (speeds.isEmpty()) return 0.0
        if (speeds.size < 3) return speeds.minOf { it }.coerceAtMost(MAX_PLAUSIBLE_SPEED_MPS)
        var best = 0.0
        for (index in 1 until speeds.lastIndex) {
            val window = listOf(speeds[index - 1], speeds[index], speeds[index + 1]).sorted()
            val median = window[1]
            if (median <= MAX_PLAUSIBLE_SPEED_MPS && median > best) best = median
        }
        return best
    }

    private fun terrain(points: List<TrackPoint>): TerrainAnalysis {
        val altitudes = points.mapNotNull { it.altitude }
        if (altitudes.isEmpty()) return TerrainAnalysis()
        val stats = TripStatsCalculator.compute(points, points.first().timestamp, points.last().timestamp)
        return TerrainAnalysis(
            minAltitude = altitudes.min(),
            maxAltitude = altitudes.max(),
            averageAltitude = altitudes.average(),
            ascentMeters = stats.elevationGain,
            descentMeters = stats.elevationLoss,
            steepestClimb = steepestClimb(points),
        )
    }

    private fun steepestClimb(points: List<TrackPoint>): SteepestClimb? {
        val profile = TripStatsCalculator.buildElevationProfile(points.filter { it.altitude != null }).points
        if (profile.size < 2) return null
        var best: SteepestClimb? = null
        var end = 0
        for (startIndex in profile.indices) {
            val from = profile[startIndex]
            if (end <= startIndex) end = startIndex + 1
            while (end < profile.size && profile[end].distanceMeters - from.distanceMeters < CLIMB_WINDOW_METERS) end++
            if (end >= profile.size) break
            val to = profile[end]
            val length = to.distanceMeters - from.distanceMeters
            val gain = to.altitude - from.altitude
            if (length <= 0 || gain <= TripStatsCalculator.ELEVATION_THRESHOLD_METERS) continue
            val grade = gain / length * 100.0
            if (best == null || grade > best.gradePercent) {
                best = SteepestClimb(grade, gain, length, from.distanceMeters)
            }
        }
        return best
    }

    private fun route(points: List<TrackPoint>, waypoints: List<Waypoint>): RouteShapeAnalysis {
        if (points.isEmpty()) return RouteShapeAnalysis()
        val first = points.first()
        val maxFromStart = points.maxOf { distance(first, it) }
        val parking = waypoints.filter { it.category == PARKING_CATEGORY }.minByOrNull { it.timestamp }
        val maxFromParking = parking?.let { car ->
            points.maxOf { GeoUtils.distanceMeters(car.latitude, car.longitude, it.latitude, it.longitude) }
        }
        val endToStart = if (points.size >= 2) distance(first, points.last()) else null
        val isLoop = endToStart != null &&
            endToStart <= LOOP_RADIUS_METERS &&
            maxFromStart >= LOOP_MIN_EXCURSION_METERS
        return RouteShapeAnalysis(
            maxDistanceFromStartMeters = maxFromStart,
            maxDistanceFromParkingMeters = maxFromParking,
            endToStartMeters = endToStart,
            isLoop = isLoop,
        )
    }

    /** The index of the sample closest in time to [timestamp], or -1 without samples. */
    private fun nearestIndex(points: List<TrackPoint>, timestamp: Long): Int {
        if (points.isEmpty()) return -1
        var low = 0
        var high = points.lastIndex
        while (low < high) {
            val middle = (low + high) / 2
            if (points[middle].timestamp < timestamp) low = middle + 1 else high = middle
        }
        if (low > 0 && abs(points[low - 1].timestamp - timestamp) <= abs(points[low].timestamp - timestamp)) {
            return low - 1
        }
        return low
    }

    private fun finds(
        points: List<TrackPoint>,
        cumulative: DoubleArray,
        waypoints: List<Waypoint>,
        start: Long,
        end: Long,
    ): FindsAnalysis {
        val counts = waypoints.groupingBy { it.category }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
        val finds = waypoints.filter(::isFind).sortedBy { it.timestamp }
        if (finds.isEmpty()) return FindsAnalysis(countsByCategory = counts)

        val duration = end - start
        val totalDistance = cumulative.lastOrNull() ?: 0.0
        val altitudes = finds.mapNotNull { find ->
            find.altitude ?: nearestIndex(points, find.timestamp).takeIf { it >= 0 }?.let { points[it].altitude }
        }
        val alongTrack = if (points.isEmpty()) {
            emptyList()
        } else {
            finds.map { cumulative[nearestIndex(points, it.timestamp)] }
        }
        val slots = timeSlots(finds, start, end)
        val bestSegment = alongTrack.takeIf { it.isNotEmpty() }?.let { distances ->
            distances.groupingBy { floor(it / SEGMENT_LENGTH_METERS).toInt() }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .first()
                .let { DistanceSegment(it.key * SEGMENT_LENGTH_METERS, (it.key + 1) * SEGMENT_LENGTH_METERS, it.value) }
        }
        return FindsAnalysis(
            findCount = finds.size,
            countsByCategory = counts,
            findsPerHour = if (duration >= 60_000) finds.size / (duration / 3_600_000.0) else null,
            findsPerKm = if (totalDistance >= 100.0) finds.size / (totalDistance / 1000.0) else null,
            firstFindMillis = finds.first().timestamp,
            lastFindMillis = finds.last().timestamp,
            timeToFirstFindMillis = (finds.first().timestamp - start).coerceAtLeast(0),
            minAltitude = altitudes.minOrNull(),
            maxAltitude = altitudes.maxOrNull(),
            averageAltitude = altitudes.takeIf { it.isNotEmpty() }?.average(),
            averageDistanceBetweenFindsMeters = if (alongTrack.size >= 2) {
                (alongTrack.last() - alongTrack.first()) / (alongTrack.size - 1)
            } else {
                null
            },
            timeSlots = slots,
            bestTimeSlot = slots.filter { it.finds > 0 }.maxByOrNull { it.finds },
            altitudeBands = altitudeBands(altitudes),
            bestSegment = bestSegment,
        )
    }

    /** Splits the trip into time slots and counts the finds of every slot. */
    fun timeSlots(finds: List<Waypoint>, start: Long, end: Long): List<TimeSlot> {
        val duration = (end - start).coerceAtLeast(1)
        val slotMillis = SLOT_MINUTES.map { it * 60_000L }
            .firstOrNull { ceil(duration / it.toDouble()) <= MAX_SLOTS }
            ?: (SLOT_MINUTES.last() * 60_000L)
        val count = ceil(duration / slotMillis.toDouble()).toInt().coerceAtLeast(1)
        val totals = IntArray(count)
        finds.forEach { find ->
            val index = ((find.timestamp - start) / slotMillis).toInt().coerceIn(0, count - 1)
            totals[index]++
        }
        return List(count) { index ->
            TimeSlot(start + index * slotMillis, start + (index + 1) * slotMillis, totals[index])
        }
    }

    /** Groups the altitudes of the finds in bands of 50 m (100 m for large ranges). */
    fun altitudeBands(altitudes: List<Double>): List<AltitudeBand> {
        if (altitudes.isEmpty()) return emptyList()
        val min = altitudes.min()
        val max = altitudes.max()
        val band = if (max - min <= 400.0) 50.0 else 100.0
        val first = floor(min / band).toInt()
        val last = floor(max / band).toInt()
        val totals = IntArray(last - first + 1)
        altitudes.forEach { totals[floor(it / band).toInt() - first]++ }
        return totals.mapIndexed { index, finds ->
            AltitudeBand((first + index) * band, (first + index + 1) * band, finds)
        }
    }

    private fun daylight(
        points: List<TrackPoint>,
        waypoints: List<Waypoint>,
        start: Long,
        end: Long,
        zoneId: ZoneId,
    ): DaylightAnalysis? {
        val latitude = points.firstOrNull()?.latitude ?: waypoints.firstOrNull()?.latitude ?: return null
        val longitude = points.firstOrNull()?.longitude ?: waypoints.firstOrNull()?.longitude ?: return null
        val date = Instant.ofEpochMilli(start).atZone(zoneId).toLocalDate()
        return DaylightAnalysis(
            sunriseMillis = SunCalculator.sunriseUtcMillis(date, latitude, longitude),
            sunsetMillis = SunCalculator.sunsetUtcMillis(date, latitude, longitude),
            startMillis = start,
            endMillis = end,
        )
    }
}
