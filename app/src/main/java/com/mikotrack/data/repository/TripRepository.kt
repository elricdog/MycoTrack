package com.mikotrack.data.repository

import com.mikotrack.data.db.Mappers.toDomain
import com.mikotrack.data.db.TrackPointDao
import com.mikotrack.data.db.TrackPointEntity
import com.mikotrack.data.db.TripDao
import com.mikotrack.data.db.TripEntity
import com.mikotrack.data.files.ImageStore
import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStatus
import com.mikotrack.domain.stats.TripStatsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Central access point for trips and their GPS tracks.
 *
 * Track points are written to the database while the trip is running, so a crash or a
 * force stop never loses recorded data.
 */
class TripRepository(
    private val tripDao: TripDao,
    private val trackPointDao: TrackPointDao,
    private val imageStore: ImageStore,
) {

    fun observeTrips(): Flow<List<Trip>> =
        tripDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTrip(id: Long): Flow<Trip?> =
        tripDao.observeById(id).map { it?.toDomain() }

    fun observePoints(tripId: Long): Flow<List<TrackPoint>> =
        trackPointDao.observeForTrip(tripId).map { list -> list.map { it.toDomain() } }

    suspend fun getTrip(id: Long): Trip? = tripDao.getById(id)?.toDomain()

    suspend fun getActiveTrip(): Trip? = tripDao.getActiveTrip()?.toDomain()

    suspend fun getPoints(tripId: Long): List<TrackPoint> =
        trackPointDao.getForTrip(tripId).map { it.toDomain() }

    /** Creates a new trip and returns its identifier. */
    suspend fun startTrip(name: String, startTime: Long): Long {
        val id = tripDao.insert(
            TripEntity(
                name = name,
                startTime = startTime,
                status = TripStatus.RECORDING.name,
                lastUpdated = startTime,
            ),
        )
        return id
    }

    /** Appends a GPS sample to a running trip. */
    suspend fun appendPoint(tripId: Long, point: TrackPoint) {
        trackPointDao.insert(
            TrackPointEntity(
                tripId = tripId,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                accuracy = point.accuracy,
                speed = point.speed,
                bearing = point.bearing,
                timestamp = point.timestamp,
            ),
        )
    }

    /** Recomputes and stores the aggregated statistics of a trip. */
    suspend fun refreshStatistics(tripId: Long, status: TripStatus? = null) {
        val trip = tripDao.getById(tripId) ?: return
        val points = trackPointDao.getForTrip(tripId).map { it.toDomain() }
        val stats = TripStatsCalculator.compute(
            points = points,
            startTime = trip.startTime,
            endTime = trip.endTime,
        )
        tripDao.updateStats(
            id = tripId,
            name = trip.name,
            notes = trip.notes,
            status = (status ?: runCatching { TripStatus.valueOf(trip.status) }.getOrDefault(TripStatus.COMPLETED)).name,
            endTime = trip.endTime,
            durationMillis = stats.elapsedMillis,
            distanceMeters = stats.distanceMeters,
            elevationGain = stats.elevationGain,
            elevationLoss = stats.elevationLoss,
            minAltitude = stats.minAltitude,
            maxAltitude = stats.maxAltitude,
            photoCount = trip.photoCount,
            waypointCount = trip.waypointCount,
            lastUpdated = System.currentTimeMillis(),
        )
    }

    /** Finishes a trip: stamps the end time and stores the final statistics. */
    suspend fun finishTrip(tripId: Long, endTime: Long, status: TripStatus = TripStatus.COMPLETED) {
        val trip = tripDao.getById(tripId) ?: return
        val points = trackPointDao.getForTrip(tripId).map { it.toDomain() }
        val stats = TripStatsCalculator.compute(points, trip.startTime, endTime)
        tripDao.updateStats(
            id = tripId,
            name = trip.name,
            notes = trip.notes,
            status = status.name,
            endTime = endTime,
            durationMillis = stats.elapsedMillis,
            distanceMeters = stats.distanceMeters,
            elevationGain = stats.elevationGain,
            elevationLoss = stats.elevationLoss,
            minAltitude = stats.minAltitude,
            maxAltitude = stats.maxAltitude,
            photoCount = trip.photoCount,
            waypointCount = trip.waypointCount,
            lastUpdated = endTime,
        )
    }

    /** Updates the editable metadata of a trip. */
    suspend fun updateTripDetails(tripId: Long, name: String, notes: String) {
        val trip = tripDao.getById(tripId) ?: return
        tripDao.update(
            trip.copy(
                name = name,
                notes = notes,
                lastUpdated = System.currentTimeMillis(),
            ),
        )
    }

    /** Updates the cached counters of photos and waypoints of a trip. */
    suspend fun updateCounters(tripId: Long, photoCount: Int, waypointCount: Int) {
        val trip = tripDao.getById(tripId) ?: return
        tripDao.update(
            trip.copy(
                photoCount = photoCount,
                waypointCount = waypointCount,
                lastUpdated = System.currentTimeMillis(),
            ),
        )
    }

    /** Deletes a trip together with its track, its waypoints and its photographs. */
    suspend fun deleteTrip(tripId: Long) {
        tripDao.deleteById(tripId)
        imageStore.deleteTripImages(tripId)
    }

    /** Sets the colour of the route of a trip on the map; `null` restores the automatic one. */
    suspend fun setColor(tripId: Long, color: Int?) {
        tripDao.updateColor(tripId, color)
    }
}
