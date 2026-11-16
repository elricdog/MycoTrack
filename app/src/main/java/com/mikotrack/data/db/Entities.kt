package com.mikotrack.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A recorded or in-progress trip (table `TRIP`). */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val elevationGain: Double = 0.0,
    val elevationLoss: Double = 0.0,
    val minAltitude: Double? = null,
    val maxAltitude: Double? = null,
    val notes: String = "",
    val status: String = "RECORDING",
    val photoCount: Int = 0,
    val waypointCount: Int = 0,
    val lastUpdated: Long = 0,
    /** The colour chosen for the route (ARGB), or `null` to use an automatic one. */
    val color: Int? = null,
)

/** A GPS sample belonging to a trip (table `TRACK_POINT`). */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["tripId", "timestamp"]),
    ],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val timestamp: Long,
)

/** A geotagged photo or point of interest (table `WAYPOINT`). */
@Entity(
    tableName = "waypoints",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["timestamp"]),
    ],
)
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val timestamp: Long,
    val description: String = "",
    val category: String = "MUSHROOM_FOUND",
    val imagePath: String? = null,
)

/** A waypoint category, either built in or created by the user (table `WAYPOINT_CATEGORY`). */
@Entity(
    tableName = "waypoint_categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val symbol: String,
    val builtIn: Boolean,
)

/**
 * An offline map area backed by an MBTiles file (table `OFFLINE_AREA`).
 *
 * The [bundled] flag marks the areas that ship inside the application. They are
 * restored on every launch and cannot be deleted by the user.
 */
@Entity(tableName = "offline_areas")
data class OfflineAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val layerId: String,
    val filePath: String,
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val sizeBytes: Long,
    val createdAt: Long,
    val bundled: Boolean = false,
    val format: String = "jpg",
    val tileSize: Int = 256,
)
