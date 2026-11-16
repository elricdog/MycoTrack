package com.mikotrack.export

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStats
import java.io.File
import java.util.Locale

/** Writes the GPS samples and the statistics of a trip as CSV. */
object CsvExporter {

    /** The header of the point table. */
    const val POINTS_HEADER = "index,timestamp,iso_time,latitude,longitude,altitude_m,accuracy_m,speed_mps,bearing_deg"

    /** Builds the CSV table with every GPS sample of the trip. */
    fun buildPointsCsv(points: List<TrackPoint>): String {
        val builder = StringBuilder()
        builder.append(POINTS_HEADER).append('\n')
        points.forEachIndexed { index, point ->
            builder.append(index).append(',')
                .append(point.timestamp).append(',')
                .append(GpxExporter.formatTime(point.timestamp)).append(',')
                .append(format(point.latitude)).append(',')
                .append(format(point.longitude)).append(',')
                .append(point.altitude?.let { format(it) } ?: "").append(',')
                .append(point.accuracy?.let { format(it.toDouble()) } ?: "").append(',')
                .append(point.speed?.let { format(it.toDouble()) } ?: "").append(',')
                .append(point.bearing?.let { format(it.toDouble()) } ?: "")
                .append('\n')
        }
        return builder.toString()
    }

    /** Builds the CSV table with the aggregated statistics of the trip. */
    fun buildStatisticsCsv(trip: Trip, stats: TripStats): String {
        val builder = StringBuilder()
        builder.append("metric,value,unit\n")
        builder.append("name,\"").append(trip.name.replace("\"", "\"\"")).append("\",\n")
        builder.append("start_time,").append(GpxExporter.formatTime(trip.startTime)).append(",\n")
        builder.append("end_time,").append(trip.endTime?.let { GpxExporter.formatTime(it) } ?: "").append(",\n")
        builder.append("duration,").append(format(trip.durationMillis / 1000.0)).append(",s\n")
        builder.append("distance,").append(format(trip.distanceMeters)).append(",m\n")
        builder.append("elevation_gain,").append(format(trip.elevationGain)).append(",m\n")
        builder.append("elevation_loss,").append(format(trip.elevationLoss)).append(",m\n")
        builder.append("min_altitude,").append(trip.minAltitude?.let { format(it) } ?: "").append(",m\n")
        builder.append("max_altitude,").append(trip.maxAltitude?.let { format(it) } ?: "").append(",m\n")
        builder.append("average_speed,").append(format(stats.averageSpeedMps)).append(",m/s\n")
        builder.append("max_speed,").append(format(stats.maxSpeedMps)).append(",m/s\n")
        builder.append("moving_time,").append(format(stats.movingTimeMillis / 1000.0)).append(",s\n")
        builder.append("stationary_time,").append(format(stats.stationaryTimeMillis / 1000.0)).append(",s\n")
        builder.append("photo_count,${trip.photoCount},\n")
        builder.append("waypoint_count,${trip.waypointCount},\n")
        builder.append("point_count,${stats.pointCount},\n")
        builder.append("notes,\"").append(trip.notes.replace("\"", "\"\"")).append("\",\n")
        return builder.toString()
    }

    /** Writes the point table into [target]. */
    fun exportPoints(points: List<TrackPoint>, target: File): File {
        target.parentFile?.mkdirs()
        target.writeText(buildPointsCsv(points))
        return target
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.6f", value)
}
