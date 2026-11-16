package com.mikotrack.export

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.Waypoint
import java.io.File
import java.util.Locale

/** Writes a trip as a KML 2.2 document that can be opened in Google Earth or QGIS. */
object KmlExporter {

    /** Serialises the trip into a KML document. */
    fun buildKml(trip: Trip, points: List<TrackPoint>, waypoints: List<Waypoint>): String {
        val builder = StringBuilder()
        builder.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        builder.append("""<kml xmlns="http://www.opengis.net/kml/2.2">""").append('\n')
        builder.append("  <Document>\n")
        builder.append("    <name>").append(escape(trip.name)).append("</name>\n")
        builder.append("    <description>").append(escape(trip.notes)).append("</description>\n")
        builder.append("    <Style id=\"trackStyle\">\n")
        builder.append("      <LineStyle><color>ff2f7a3a</color><width>4</width></LineStyle>\n")
        builder.append("    </Style>\n")
        builder.append("    <Style id=\"waypointStyle\">\n")
        builder.append("      <IconStyle><color>ff00b0ff</color><scale>1.0</scale></IconStyle>\n")
        builder.append("    </Style>\n")

        if (points.size >= 2) {
            builder.append("    <Placemark>\n")
            builder.append("      <name>").append(escape(trip.name)).append("</name>\n")
            builder.append("      <styleUrl>#trackStyle</styleUrl>\n")
            builder.append("      <LineString>\n")
            builder.append("        <tessellate>1</tessellate>\n")
            builder.append("        <altitudeMode>clampToGround</altitudeMode>\n")
            builder.append("        <coordinates>\n")
            points.forEach { point ->
                builder.append("          ")
                    .append(format(point.longitude)).append(',')
                    .append(format(point.latitude)).append(',')
                    .append(point.altitude?.let { format(it) } ?: "0").append('\n')
            }
            builder.append("        </coordinates>\n")
            builder.append("      </LineString>\n")
            builder.append("    </Placemark>\n")
        }

        waypoints.forEach { waypoint ->
            builder.append("    <Placemark>\n")
            builder.append("      <name>").append(escape(waypoint.description.ifBlank { waypoint.category }))
                .append("</name>\n")
            builder.append("      <description>").append(escape(waypoint.category)).append("</description>\n")
            builder.append("      <styleUrl>#waypointStyle</styleUrl>\n")
            builder.append("      <TimeStamp><when>")
                .append(GpxExporter.formatTime(waypoint.timestamp)).append("</when></TimeStamp>\n")
            builder.append("      <Point>\n")
            builder.append("        <altitudeMode>clampToGround</altitudeMode>\n")
            builder.append("        <coordinates>")
                .append(format(waypoint.longitude)).append(',')
                .append(format(waypoint.latitude)).append(',')
                .append(waypoint.altitude?.let { format(it) } ?: "0")
                .append("</coordinates>\n")
            builder.append("      </Point>\n")
            builder.append("    </Placemark>\n")
        }

        builder.append("  </Document>\n")
        builder.append("</kml>\n")
        return builder.toString()
    }

    /** Writes the KML document into [target]. */
    fun export(trip: Trip, points: List<TrackPoint>, waypoints: List<Waypoint>, target: File): File {
        target.parentFile?.mkdirs()
        target.writeText(buildKml(trip, points, waypoints))
        return target
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.7f", value)

    internal fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
