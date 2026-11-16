package com.mikotrack.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Data access for trips (table `TRIP`). */
@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeById(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE status = 'RECORDING' ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query(
        """
        UPDATE trips SET
            name = :name,
            notes = :notes,
            status = :status,
            endTime = :endTime,
            durationMillis = :durationMillis,
            distanceMeters = :distanceMeters,
            elevationGain = :elevationGain,
            elevationLoss = :elevationLoss,
            minAltitude = :minAltitude,
            maxAltitude = :maxAltitude,
            photoCount = :photoCount,
            waypointCount = :waypointCount,
            lastUpdated = :lastUpdated
        WHERE id = :id
        """,
    )
    suspend fun updateStats(
        id: Long,
        name: String,
        notes: String,
        status: String,
        endTime: Long?,
        durationMillis: Long,
        distanceMeters: Double,
        elevationGain: Double,
        elevationLoss: Double,
        minAltitude: Double?,
        maxAltitude: Double?,
        photoCount: Int,
        waypointCount: Int,
        lastUpdated: Long,
    )

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE trips SET color = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: Int?)
}

/** Data access for GPS samples (table `TRACK_POINT`). */
@Dao
interface TrackPointDao {

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeForTrip(tripId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getForTrip(tripId: Long): List<TrackPointEntity>

    @Query("SELECT COUNT(*) FROM track_points WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int

    @Insert
    suspend fun insert(point: TrackPointEntity): Long
}

/** Data access for waypoints (table `WAYPOINT`). */
@Dao
interface WaypointDao {

    @Query("SELECT * FROM waypoints ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeForTrip(tripId: Long): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getForTrip(tripId: Long): List<WaypointEntity>

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getById(id: Long): WaypointEntity?

    @Query("SELECT COUNT(*) FROM waypoints WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int

    @Insert
    suspend fun insert(waypoint: WaypointEntity): Long

    @Update
    suspend fun update(waypoint: WaypointEntity)

    @Query(
        """
        UPDATE waypoints SET description = :description, category = :category
        WHERE id = :id
        """,
    )
    suspend fun updateDescriptionAndCategory(id: Long, description: String, category: String)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** Data access for waypoint categories (table `WAYPOINT_CATEGORY`). */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM waypoint_categories ORDER BY builtIn DESC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)
}

/** Data access for downloaded offline areas (table `OFFLINE_AREA`). */
@Dao
interface OfflineAreaDao {

    @Query("SELECT * FROM offline_areas ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OfflineAreaEntity>>

    @Query("SELECT * FROM offline_areas ORDER BY createdAt DESC")
    suspend fun getAll(): List<OfflineAreaEntity>

    @Query("SELECT * FROM offline_areas WHERE id = :id")
    suspend fun getById(id: Long): OfflineAreaEntity?

    /**
     * The bundled area with the given name.
     *
     * The bundled areas are restored on every launch, so the row is looked up by name
     * to avoid inserting it twice.
     */
    @Query("SELECT * FROM offline_areas WHERE name = :name AND bundled = 1 LIMIT 1")
    suspend fun getBundledByName(name: String): OfflineAreaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(area: OfflineAreaEntity): Long

    @Update
    suspend fun update(area: OfflineAreaEntity)

    @Query("DELETE FROM offline_areas WHERE id = :id")
    suspend fun deleteById(id: Long)
}
