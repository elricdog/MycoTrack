package com.mikotrack.export

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.Waypoint
import java.io.File
import java.util.Locale

/** Writes a trip as a GeoJSON feature collection. */
object GeoJsonExporter {

    /** Serialises the trip into a GeoJSON document. */
    fun buildGeoJson(trip: Trip, points: List<TrackPoint>, waypoints: List<Waypoint>): String {
        val builder = StringBuilder()
        builder.append("{\n")
        builder.append("  \"type\": \"FeatureCollection\",\n")
        builder.append("  \"properties\": {\n")
        builder.append("    \"name\": \"").append(escape(trip.name)).append("\",\n")
        builder.append("    \"startTime\": ").append(trip.startTime).append(",\n")
        builder.append("    \"endTime\": ").append(trip.endTime ?: "null").append(",\n")
        builder.append("    \"durationMillis\": ").append(trip.durationMillis).append(",\n")
        builder.append("    \"distanceMeters\": ").append(format(trip.distanceMeters)).append(",\n")
        builder.append("    \"elevationGain\": ").append(format(trip.elevationGain)).append(",\n")
        builder.append("    \"elevationLoss\": ").append(format(trip.elevationLoss)).append(",\n")
        builder.append("    \"minAltitude\": ").append(trip.minAltitude?.let { format(it) } ?: "null").append(",\n")
        builder.append("    \"maxAltitude\": ").append(trip.maxAltitude?.let { format(it) } ?: "null").append(",\n")
        builder.append("    \"photoCount\": ").append(trip.photoCount).append(",\n")
        builder.append("    \"waypointCount\": ").append(trip.waypointCount).append(",\n")
        builder.append("    \"notes\": \"").append(escape(trip.notes)).append("\"\n")
        builder.append("  },\n")
        builder.append("  \"features\": [\n")

        val features = mutableListOf<String>()

        if (points.size >= 2) {
            features += buildString {
                append("    {\n")
                append("      \"type\": \"Feature\",\n")
                append("      \"properties\": { \"kind\": \"track\" },\n")
                append("      \"geometry\": { \"type\": \"LineString\", \"coordinates\": [")
                points.forEachIndexed { index, point ->
                    if (index > 0) append(", ")
                    append("[").append(format(point.longitude)).append(", ")
                        .append(format(point.latitude)).append("]")
                }
                append("] }\n")
                append("    }")
            }
        }

        waypoints.forEach { waypoint ->
            features += buildString {
                append("    {\n")
                append("      \"type\": \"Feature\",\n")
                append("      \"properties\": {")
                append("\"id\": ").append(waypoint.id)
                append(", \"kind\": \"waypoint\"")
                append(", \"description\": \"").append(escape(waypoint.description)).append('"')
                append(", \"category\": \"").append(escape(waypoint.category)).append('"')
                append(", \"timestamp\": ").append(waypoint.timestamp)
                append(", \"altitude\": ").append(waypoint.altitude?.let { format(it) } ?: "null")
                // Only the file name is written: the private path of the device is useless
                // elsewhere, while the name matches the "photos/" folder of the ZIP export.
                append(", \"image\": \"").append(escape(waypoint.imagePath?.let { File(it).name } ?: "")).append('"')
                append(" },\n")
                append("      \"geometry\": { \"type\": \"Point\", \"coordinates\": [")
                append(format(waypoint.longitude)).append(", ").append(format(waypoint.latitude)).append("] }\n")
                append("    }")
            }
        }

        builder.append(features.joinToString(",\n"))
        builder.append("\n  ]\n")
        builder.append("}\n")
        return builder.toString()
    }

    /** Writes the GeoJSON document into [target]. */
    fun export(trip: Trip, points: List<TrackPoint>, waypoints: List<Waypoint>, target: File): File {
        target.parentFile?.mkdirs()
        target.writeText(buildGeoJson(trip, points, waypoints))
        return target
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.7f", value)

    internal fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
}
