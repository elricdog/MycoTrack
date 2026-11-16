package com.mikotrack.export

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStats
import com.mikotrack.domain.model.Waypoint
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Packs a complete trip (track, statistics, waypoints and photographs) into a ZIP file. */
object ZipExporter {

    /** Builds the ZIP archive of a trip. */
    fun export(
        target: File,
        trip: Trip,
        points: List<TrackPoint>,
        waypoints: List<Waypoint>,
        stats: TripStats,
        images: List<File> = emptyList(),
    ): File {
        target.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            val safeName = trip.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "trip" }

            zip.writeText("${safeName}.gpx", GpxExporter.buildGpx(trip, points, waypoints))
            zip.writeText("${safeName}.geojson", GeoJsonExporter.buildGeoJson(trip, points, waypoints))
            zip.writeText("${safeName}.kml", KmlExporter.buildKml(trip, points, waypoints))
            zip.writeText("track.csv", CsvExporter.buildPointsCsv(points))
            zip.writeText("statistics.csv", CsvExporter.buildStatisticsCsv(trip, stats))
            zip.writeText(
                "metadata.json",
                buildMetadata(trip, stats, waypoints.size),
            )

            val usedNames = mutableSetOf<String>()
            images.forEach { image ->
                if (!image.exists()) return@forEach
                // Two photographs with the same name would make the ZIP stream fail
                // with a duplicate entry, so the later ones get a numeric suffix.
                zip.putNextEntry(ZipEntry("photos/${uniqueName(image.name, usedNames)}"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return target
    }

    /** Returns [name], or a variant with a numeric suffix when it was already used. */
    internal fun uniqueName(name: String, used: MutableSet<String>): String {
        var candidate = name
        var counter = 1
        while (!used.add(candidate)) {
            candidate = "${name.substringBeforeLast('.')}_$counter" +
                (name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" })
            counter++
        }
        return candidate
    }

    private fun buildMetadata(trip: Trip, stats: TripStats, waypointCount: Int): String = buildString {
        append("{\n")
        append("  \"name\": \"").append(GeoJsonExporter.escape(trip.name)).append("\",\n")
        append("  \"startTime\": ").append(trip.startTime).append(",\n")
        append("  \"endTime\": ").append(trip.endTime ?: "null").append(",\n")
        append("  \"durationMillis\": ").append(trip.durationMillis).append(",\n")
        append("  \"distanceMeters\": ").append(trip.distanceMeters).append(",\n")
        append("  \"elevationGain\": ").append(trip.elevationGain).append(",\n")
        append("  \"elevationLoss\": ").append(trip.elevationLoss).append(",\n")
        append("  \"minAltitude\": ").append(trip.minAltitude?.toString() ?: "null").append(",\n")
        append("  \"maxAltitude\": ").append(trip.maxAltitude?.toString() ?: "null").append(",\n")
        append("  \"pointCount\": ").append(stats.pointCount).append(",\n")
        append("  \"waypointCount\": ").append(waypointCount).append(",\n")
        append("  \"photoCount\": ").append(trip.photoCount).append(",\n")
        append("  \"notes\": \"").append(GeoJsonExporter.escape(trip.notes)).append("\",\n")
        append("  \"generator\": \"MycoTrack 1.0.0\"\n")
        append("}\n")
    }

    private fun ZipOutputStream.writeText(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }
}
