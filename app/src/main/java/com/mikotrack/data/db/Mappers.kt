package com.mikotrack.data.db

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStatus
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.domain.model.WaypointCategory

/** Conversions between the persistence model and the domain model. */
object Mappers {

    fun TripEntity.toDomain(): Trip = Trip(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        durationMillis = durationMillis,
        distanceMeters = distanceMeters,
        elevationGain = elevationGain,
        elevationLoss = elevationLoss,
        minAltitude = minAltitude,
        maxAltitude = maxAltitude,
        notes = notes,
        status = runCatching { TripStatus.valueOf(status) }.getOrDefault(TripStatus.COMPLETED),
        photoCount = photoCount,
        waypointCount = waypointCount,
        lastUpdated = lastUpdated,
        color = color,
    )

    fun Trip.toEntity(): TripEntity = TripEntity(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        durationMillis = durationMillis,
        distanceMeters = distanceMeters,
        elevationGain = elevationGain,
        elevationLoss = elevationLoss,
        minAltitude = minAltitude,
        maxAltitude = maxAltitude,
        notes = notes,
        status = status.name,
        photoCount = photoCount,
        waypointCount = waypointCount,
        lastUpdated = lastUpdated,
        color = color,
    )

    fun TrackPointEntity.toDomain(): TrackPoint = TrackPoint(
        id = id,
        tripId = tripId,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracy = accuracy,
        speed = speed,
        bearing = bearing,
        timestamp = timestamp,
    )

    fun TrackPoint.toEntity(): TrackPointEntity = TrackPointEntity(
        id = id,
        tripId = tripId,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracy = accuracy,
        speed = speed,
        bearing = bearing,
        timestamp = timestamp,
    )

    fun WaypointEntity.toDomain(): Waypoint = Waypoint(
        id = id,
        tripId = tripId,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        timestamp = timestamp,
        description = description,
        category = category,
        imagePath = imagePath,
    )

    fun Waypoint.toEntity(): WaypointEntity = WaypointEntity(
        id = id,
        tripId = tripId,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        timestamp = timestamp,
        description = description,
        category = category,
        imagePath = imagePath,
    )

    fun CategoryEntity.toDomain(): WaypointCategory = WaypointCategory(
        id = id,
        name = name,
        color = color,
        symbol = symbol,
        builtIn = builtIn,
    )
}
