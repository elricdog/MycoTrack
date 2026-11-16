package com.mikotrack.export

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.Waypoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Writes a trip as a GPX 1.1 file with a track and its waypoints. */
object GpxExporter {

    /** Serialises the trip into a GPX document. */
    fun buildGpx(trip: Trip, points: List<TrackPoint>, waypoints: List<Waypoint>): String {
        val builder = StringBuilder()
        builder.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        builder.append("""<gpx version="1.1" creator="MycoTrack" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        builder.append("  <metadata>\n")
        builder.append("    <name>").append(escape(trip.name)).append("</name>\n")
        builder.append("    <time>").append(formatTime(trip.startTime)).append("</time>\n")
        if (trip.notes.isNotBlank()) {
            builder.append("    <desc>").append(escape(trip.notes)).append("</desc>\n")
        }
        builder.append("  </metadata>\n")

        // GPX 1.1 requires the waypoints before the track, and the children of a
        // waypoint in the order ele, time, name, desc, sym.
        waypoints.forEach { waypoint ->
            builder.append("  <wpt lat=\"").append(format(waypoint.latitude))
                .append("\" lon=\"").append(format(waypoint.longitude)).append("\">\n")
            if (waypoint.altitude != null) {
                builder.append("    <ele>").append(format(waypoint.altitude)).append("</ele>\n")
            }
            builder.append("    <time>").append(formatTime(waypoint.timestamp)).append("</time>\n")
            builder.append("    <name>").append(escape(waypoint.description.ifBlank { waypoint.category }))
                .append("</name>\n")
            builder.append("    <desc>").append(escape(waypoint.category)).append("</desc>\n")
            builder.append("    <sym>Waypoint</sym>\n")
            builder.append("  </wpt>\n")
        }

        if (points.isNotEmpty()) {
            builder.append("  <trk>\n")
            builder.append("    <name>").append(escape(trip.name)).append("</name>\n")
            builder.append("    <trkseg>\n")
            points.forEach { point ->
                builder.append("      <trkpt lat=\"").append(format(point.latitude))
                    .append("\" lon=\"").append(format(point.longitude)).append("\">\n")
                if (point.altitude != null) {
                    builder.append("        <ele>").append(format(point.altitude)).append("</ele>\n")
                }
                builder.append("        <time>").append(formatTime(point.timestamp)).append("</time>\n")
                if (point.speed != null) {
                    builder.append("        <extensions><speed>").append(format(point.speed.toDouble()))
                        .append("</speed></extensions>\n")
                }
                builder.append("      </trkpt>\n")
            }
            builder.append("    </trkseg>\n")
            builder.append("  </trk>\n")
        }

        builder.append("</gpx>\n")
        return builder.toString()
    }

    /** Writes the GPX document into [target]. */
    fun export(trip: Trip, points: List<TrackPoint>, waypoints: List<Waypoint>, target: File): File {
        target.parentFile?.mkdirs()
        target.writeText(buildGpx(trip, points, waypoints))
        return target
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.7f", value)

    internal fun formatTime(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }

    internal fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
