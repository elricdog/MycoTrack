package com.mikotrack.export

import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStatus
import com.mikotrack.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.xml.parsers.DocumentBuilderFactory

/** Tests of the geographic exporters. */
class ExporterTest {

    private val trip = Trip(
        id = 1,
        name = "Bolet & friends",
        startTime = 1_700_000_000_000,
        endTime = 1_700_003_600_000,
        durationMillis = 3_600_000,
        distanceMeters = 5_400.0,
        elevationGain = 320.0,
        elevationLoss = 280.0,
        minAltitude = 600.0,
        maxAltitude = 920.0,
        notes = "Rainy day & good harvest",
        status = TripStatus.COMPLETED,
        photoCount = 2,
        waypointCount = 1,
        lastUpdated = 1_700_003_600_000,
    )

    private val points = listOf(
        TrackPoint(latitude = 41.3888, longitude = 2.159, altitude = 610.0, timestamp = 1_700_000_000_000),
        TrackPoint(latitude = 41.3900, longitude = 2.161, altitude = 640.0, timestamp = 1_700_000_030_000),
        TrackPoint(latitude = 41.3950, longitude = 2.165, altitude = 700.0, timestamp = 1_700_000_060_000),
    )

    private val waypoints = listOf(
        Waypoint(
            id = 7,
            tripId = 1,
            latitude = 41.3901,
            longitude = 2.1605,
            altitude = 645.0,
            timestamp = 1_700_000_045_000,
            description = "Two boletes",
            category = "MUSHROOM_FOUND",
            imagePath = "/tmp/photo.jpg",
        ),
    )

    @Test
    fun `the gpx document is valid xml with a track and a waypoint`() {
        val gpx = GpxExporter.buildGpx(trip, points, waypoints)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            gpx.byteInputStream(),
        )
        document.documentElement.nodeName.let { assertEquals("gpx", it) }
        val trackPoints = document.getElementsByTagName("trkpt")
        assertEquals(3, trackPoints.length)
        val wpt = document.getElementsByTagName("wpt")
        assertEquals(1, wpt.length)
        assertTrue(gpx.contains("Two boletes"))
        // GPX 1.1 requires the waypoints before the track.
        assertTrue(gpx.indexOf("<wpt") < gpx.indexOf("<trk>"))
    }

    @Test
    fun `the export file name keeps accented letters`() {
        assertEquals("Bolets_a_lÀger_2026", ExportManager.fileBaseName("Bolets a l'Àger  2026"))
        assertEquals("trip", ExportManager.fileBaseName("***"))
    }

    @Test
    fun `duplicated photo names receive a suffix inside the zip`() {
        val used = mutableSetOf<String>()
        assertEquals("IMG.jpg", ZipExporter.uniqueName("IMG.jpg", used))
        assertEquals("IMG_1.jpg", ZipExporter.uniqueName("IMG.jpg", used))
        assertEquals("IMG_2.jpg", ZipExporter.uniqueName("IMG.jpg", used))
    }

    @Test
    fun `the photo package lists every photo with its location`() {
        val entries = listOf(
            PhotoExporter.PhotoEntry(waypoints.first(), java.io.File("/tmp/photo.jpg"), "photos/photo.jpg"),
        )
        val csv = PhotoExporter.buildCsv(entries, mapOf(1L to trip.name)).trim().lines()
        assertEquals(PhotoExporter.CSV_HEADER, csv.first())
        assertTrue(csv[1].startsWith("\"photos/photo.jpg\",\"Bolet & friends\""))
        assertTrue(csv[1].contains("41.3901000"))

        val geoJson = PhotoExporter.buildGeoJson(entries, mapOf(1L to trip.name))
        val root = kotlinx.serialization.json.Json.parseToJsonElement(geoJson).jsonObject
        assertEquals(1, root["features"]?.jsonArray?.size)
    }

    @Test
    fun `the geojson document is a valid feature collection`() {
        val geoJson = GeoJsonExporter.buildGeoJson(trip, points, waypoints)
        val root = kotlinx.serialization.json.Json.parseToJsonElement(geoJson).jsonObject
        assertEquals("FeatureCollection", root["type"]?.jsonPrimitive?.content)
        assertEquals(2, root["features"]?.jsonArray?.size)
        assertTrue(geoJson.contains("LineString"))
        assertTrue(geoJson.contains("MUSHROOM_FOUND"))
    }

    @Test
    fun `the kml document is valid xml`() {
        val kml = KmlExporter.buildKml(trip, points, waypoints)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            kml.byteInputStream(),
        )
        assertEquals("kml", document.documentElement.nodeName)
        assertEquals(2, document.getElementsByTagName("Placemark").length)
    }

    @Test
    fun `the csv table has one row per point`() {
        val csv = CsvExporter.buildPointsCsv(points)
        val lines = csv.trim().lines()
        assertEquals(CsvExporter.POINTS_HEADER, lines.first())
        assertEquals(4, lines.size)
        assertTrue(lines[1].contains("41.388800"))
    }

    @Test
    fun `the statistics table contains the distance and the duration`() {
        val stats = com.mikotrack.domain.stats.TripStatsCalculator.compute(
            points = points,
            startTime = trip.startTime,
            endTime = trip.endTime,
        )
        val csv = CsvExporter.buildStatisticsCsv(trip, stats)
        assertTrue(csv.contains("distance,5400.000000,m"))
        assertTrue(csv.contains("photo_count,2"))
    }
}
