package com.mikotrack.data.repository

import com.mikotrack.data.db.Mappers.toDomain
import com.mikotrack.data.db.WaypointDao
import com.mikotrack.data.db.WaypointEntity
import com.mikotrack.data.files.ImageStore
import com.mikotrack.domain.model.Waypoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Manages the geotagged photographs and points of interest. */
class WaypointRepository(
    private val dao: WaypointDao,
    private val imageStore: ImageStore,
) {

    fun observeAll(): Flow<List<Waypoint>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForTrip(tripId: Long): Flow<List<Waypoint>> =
        dao.observeForTrip(tripId).map { list -> list.map { it.toDomain() } }

    suspend fun getForTrip(tripId: Long): List<Waypoint> = dao.getForTrip(tripId).map { it.toDomain() }

    suspend fun get(id: Long): Waypoint? = dao.getById(id)?.toDomain()

    suspend fun add(
        tripId: Long,
        latitude: Double,
        longitude: Double,
        altitude: Double?,
        timestamp: Long,
        description: String,
        category: String,
        imagePath: String? = null,
    ): Long = dao.insert(
        WaypointEntity(
            tripId = tripId,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            timestamp = timestamp,
            description = description,
            category = category,
            imagePath = imagePath,
        ),
    )

    /**
     * Updates the editable metadata of a waypoint. The coordinates and the photograph are
     * deliberately left untouched so a description edit can never move a waypoint.
     */
    suspend fun update(id: Long, description: String, category: String) {
        dao.updateDescriptionAndCategory(id, description, category)
    }

    suspend fun delete(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.deleteById(id)
        imageStore.delete(existing.imagePath)
    }

    suspend fun countForTrip(tripId: Long): Int = dao.countForTrip(tripId)
}
