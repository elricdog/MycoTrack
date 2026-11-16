package com.mikotrack.data.repository

import com.mikotrack.domain.ParkingSelector
import com.mikotrack.domain.model.Waypoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Finds the waypoint where the car was parked: the one of the trip being recorded or,
 * when nothing is recorded, the one of the most recent trip if it is recent enough.
 */
class ParkingLocator(
    private val tripRepository: TripRepository,
    private val waypointRepository: WaypointRepository,
) {

    private val parkingKey = DefaultCategory.PARKING.key

    /** The parking waypoint, kept up to date with the recording and the waypoints. */
    fun observe(recordingTripId: Flow<Long?>): Flow<Waypoint?> =
        recordingTripId.distinctUntilChanged().flatMapLatest { recordingId ->
            if (recordingId != null) {
                waypointRepository.observeForTrip(recordingId).map { waypoints ->
                    ParkingSelector.select(recordingId, waypoints, null, emptyList(), System.currentTimeMillis(), parkingKey)
                }
            } else {
                tripRepository.observeTrips()
                    .map { it.firstOrNull() }
                    .distinctUntilChanged()
                    .flatMapLatest { latest ->
                        if (latest == null) {
                            flowOf(null)
                        } else {
                            // The parking of the last trip expires after a while, so the
                            // selection is re-evaluated every minute.
                            combine(waypointRepository.observeForTrip(latest.id), minuteTicker()) { waypoints, now ->
                                ParkingSelector.select(null, emptyList(), latest, waypoints, now, parkingKey)
                            }
                        }
                    }
            }
        }.distinctUntilChanged()

    /** The parking waypoint right now, for the given recorded trip (or none). */
    suspend fun find(recordingTripId: Long?): Waypoint? {
        val now = System.currentTimeMillis()
        if (recordingTripId != null) {
            return ParkingSelector.select(
                recordingTripId,
                waypointRepository.getForTrip(recordingTripId),
                null,
                emptyList(),
                now,
                parkingKey,
            )
        }
        val latest = tripRepository.observeTrips().first().firstOrNull() ?: return null
        return ParkingSelector.select(null, emptyList(), latest, waypointRepository.getForTrip(latest.id), now, parkingKey)
    }

    private fun minuteTicker(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }
}
