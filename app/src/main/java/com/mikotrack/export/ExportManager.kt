package com.mikotrack.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.mikotrack.R
import com.mikotrack.domain.model.ElevationProfile
import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStats
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.maps.MapLayer
import com.mikotrack.maps.OfflineSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coordinates the export of a trip: it writes the files into the cache directory and
 * hands out a shareable URI for each of them.
 */
class ExportManager(private val context: Context) {

    /** The formats the user can choose from. */
    enum class Format(val id: String, @StringRes val labelRes: Int, val extension: String, val suffix: String = "") {
        GPX("gpx", R.string.export_format_gpx, "gpx"),
        GEOJSON("geojson", R.string.export_format_geojson, "geojson"),
        KML("kml", R.string.export_format_kml, "kml"),
        CSV("csv", R.string.export_format_csv, "csv", "_points"),
        ZIP("zip", R.string.export_format_zip, "zip"),
        MAP_IMAGE("map", R.string.export_format_map, "png", "_map"),
        ELEVATION_IMAGE("elevation", R.string.export_format_elevation, "png", "_elevation"),
        REPORT("report", R.string.export_format_report, "png", "_report"),
    }

    /** The directory where the generated files are stored. */
    private val exportDirectory: File
        get() = File(context.cacheDir, "export").apply { if (!exists()) mkdirs() }

    /**
     * Exports a trip in the requested format and returns the generated file.
     *
     * @param offlineSource the offline area that backs the [MapLayer.OFFLINE] layer. It
     * is required when the offline layer is exported, because the exported image must
     * never fall back to an online map.
     */
    suspend fun export(
        format: Format,
        trip: Trip,
        points: List<TrackPoint>,
        waypoints: List<Waypoint>,
        stats: TripStats,
        profile: ElevationProfile,
        layer: MapLayer,
        offlineSource: OfflineSource? = null,
    ): File = withContext(Dispatchers.IO) {
        val baseName = fileBaseName(trip.name)
        // Every format has its own file name, so the three PNG exports never overwrite
        // each other while a share sheet is still reading one of them.
        val target = File(exportDirectory, "$baseName${format.suffix}.${format.extension}")

        requireOfflineSource(format, layer, offlineSource)
        requireTrack(format, points)

        when (format) {
            Format.GPX -> GpxExporter.export(trip, points, waypoints, target)
            Format.GEOJSON -> GeoJsonExporter.export(trip, points, waypoints, target)
            Format.KML -> KmlExporter.export(trip, points, waypoints, target)
            Format.CSV -> CsvExporter.exportPoints(points, target)
            Format.ZIP -> {
                val images = waypoints.mapNotNull { it.imagePath?.let(::File) }.filter { it.exists() }
                ZipExporter.export(target, trip, points, waypoints, stats, images)
            }

            Format.MAP_IMAGE -> {
                val exporter = ImageExporter(context)
                val bitmap = exporter.renderMapImage(
                    layer = layer,
                    points = points,
                    waypoints = waypoints,
                    options = ImageExporter.MapOptions(offlineSource = offlineSource),
                ) ?: error(context.getString(R.string.export_msg_map_not_rendered))
                target.outputStream().use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                bitmap.recycle()
                target
            }

            Format.ELEVATION_IMAGE -> {
                val exporter = ImageExporter(context)
                val bitmap = exporter.renderElevationImage(profile)
                target.outputStream().use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                bitmap.recycle()
                target
            }

            Format.REPORT -> {
                val exporter = ImageExporter(context)
                val mapImage = exporter.renderMapImage(
                    layer = layer,
                    points = points,
                    waypoints = waypoints,
                    options = ImageExporter.MapOptions(offlineSource = offlineSource),
                )
                val bitmap = exporter.renderTripReport(mapImage, profile, trip, stats)
                target.outputStream().use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                bitmap.recycle()
                mapImage?.recycle()
                target
            }
        }
    }

    /**
     * Fails when a map image is requested for the offline layer but no offline area is
     * available.
     *
     * The exported image must never fall back to an online map: the user asked for the
     * offline layer because the area has no coverage, and an image fetched from the
     * network would be misleading.
     */
    private fun requireOfflineSource(format: Format, layer: MapLayer, source: OfflineSource?) {
        val needsMap = format == Format.MAP_IMAGE || format == Format.REPORT
        if (needsMap && layer == MapLayer.OFFLINE && source == null) {
            error(context.getString(R.string.export_msg_no_offline_area))
        }
    }

    /** Fails with a clear message when the format needs a track that the trip does not have. */
    private fun requireTrack(format: Format, points: List<TrackPoint>) {
        val needsTrack = format == Format.MAP_IMAGE
        if (needsTrack && points.size < 2) {
            error(context.getString(R.string.export_msg_not_enough_points))
        }
    }

    /** Builds an intent that shares the exported file with other applications. */
    fun shareIntent(file: File, mimeType: String = mimeTypeFor(file)): Intent =
        shareIntent(listOf(file), mimeType)

    /**
     * Builds an intent that shares one or several files with other applications.
     *
     * The URIs are also placed in the [ClipData] of the intent: it is the only way the
     * read permission reaches the target application when the intent is wrapped in a
     * chooser.
     */
    fun shareIntent(files: List<File>, mimeType: String): Intent {
        require(files.isNotEmpty()) { context.getString(R.string.export_msg_nothing_to_share) }
        val uris = files.map { uriFor(it) }
        val clip = ClipData.newRawUri(files.first().name, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        return intent.apply {
            type = mimeType
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** The content URI that exposes a private file through the file provider. */
    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** The MIME type that matches the extension of the file. */
    fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "gpx" -> "application/gpx+xml"
        "geojson" -> "application/geo+json"
        "kml" -> "application/vnd.google-earth.kml+xml"
        "csv" -> "text/csv"
        "zip" -> "application/zip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    companion object {
        /**
         * A file name derived from the trip name. Letters with accents are kept, so a
         * name like "Bolets a l'Àger" does not lose characters.
         */
        fun fileBaseName(name: String): String =
            name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().replace(Regex("\\s+"), "_").ifEmpty { "trip" }
    }
}
