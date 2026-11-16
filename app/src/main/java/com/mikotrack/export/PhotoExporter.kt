package com.mikotrack.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mikotrack.R
import com.mikotrack.domain.model.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports the geotagged photographs of the gallery: it shares them with other
 * applications, copies them into the public picture gallery of the device or packs
 * them, together with their locations, into a ZIP file.
 */
class PhotoExporter(private val context: Context) {

    private val exportManager = ExportManager(context)

    /** The directory where the generated packages are stored. */
    private val exportDirectory: File
        get() = File(context.cacheDir, "export").apply { if (!exists()) mkdirs() }

    /** Builds an intent that shares the photographs of the given waypoints. */
    fun shareIntent(waypoints: List<Waypoint>): Intent {
        val files = photoFiles(waypoints)
        require(files.isNotEmpty()) { context.getString(R.string.export_msg_photos_unavailable) }
        return exportManager.shareIntent(files, "image/jpeg")
    }

    /**
     * Copies the photographs into the public "Pictures/MikoTrack" folder, so that they
     * appear in the gallery of the device. The EXIF data, with the location, is kept
     * because the files are copied byte by byte.
     *
     * On Android 9 and older the caller must hold the WRITE_EXTERNAL_STORAGE permission.
     *
     * @return the number of photographs that were saved.
     */
    suspend fun saveToDevice(waypoints: List<Waypoint>): Int = withContext(Dispatchers.IO) {
        val files = photoFiles(waypoints)
        require(files.isNotEmpty()) { context.getString(R.string.export_msg_photos_unavailable) }
        files.count { file -> runCatching { saveOne(file) }.isSuccess }
    }

    private fun saveOne(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("The photograph could not be added to the gallery.")
            try {
                resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                    ?: error("The photograph could not be written.")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
            if (!album.exists() && !album.mkdirs()) error("The album folder could not be created.")
            val target = File(album, file.name)
            file.inputStream().use { input -> FileOutputStream(target).use { input.copyTo(it) } }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg"), null)
        }
    }

    /**
     * Packs the photographs into a ZIP file, together with a GeoJSON and a CSV file that
     * hold the location, the category and the description of each of them.
     */
    suspend fun exportZip(waypoints: List<Waypoint>, tripNames: Map<Long, String>): File =
        withContext(Dispatchers.IO) {
            val withPhoto = waypoints.filter { waypoint -> waypoint.imagePath?.let { File(it).exists() } == true }
            require(withPhoto.isNotEmpty()) { context.getString(R.string.export_msg_photos_unavailable) }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val target = File(exportDirectory, "MikoTrack_photos_$stamp.zip")
            val usedNames = mutableSetOf<String>()
            val entries = withPhoto.map { waypoint ->
                val file = File(waypoint.imagePath!!)
                PhotoEntry(waypoint, file, "photos/${ZipExporter.uniqueName(file.name, usedNames)}")
            }

            ZipOutputStream(FileOutputStream(target)).use { zip ->
                entries.forEach { entry ->
                    zip.putNextEntry(ZipEntry(entry.zipPath))
                    entry.file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("photos.geojson"))
                zip.write(buildGeoJson(entries, tripNames).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("photos.csv"))
                zip.write(buildCsv(entries, tripNames).toByteArray())
                zip.closeEntry()
            }
            target
        }

    private fun photoFiles(waypoints: List<Waypoint>): List<File> =
        waypoints.mapNotNull { it.imagePath?.let(::File) }.filter { it.exists() }.distinct()

    /** A photograph and the path it receives inside the ZIP package. */
    internal data class PhotoEntry(val waypoint: Waypoint, val file: File, val zipPath: String)

    companion object {
        /** The album of the device gallery where the photographs are saved. */
        const val ALBUM = "MikoTrack"

        /** The header of the CSV table of the photographs. */
        const val CSV_HEADER = "file,trip,timestamp,iso_time,latitude,longitude,altitude_m,category,description"

        internal fun buildCsv(entries: List<PhotoEntry>, tripNames: Map<Long, String>): String = buildString {
            append(CSV_HEADER).append('\n')
            entries.forEach { entry ->
                val waypoint = entry.waypoint
                append(quote(entry.zipPath)).append(',')
                append(quote(tripNames[waypoint.tripId].orEmpty())).append(',')
                append(waypoint.timestamp).append(',')
                append(GpxExporter.formatTime(waypoint.timestamp)).append(',')
                append(format(waypoint.latitude)).append(',')
                append(format(waypoint.longitude)).append(',')
                append(waypoint.altitude?.let { format(it) } ?: "").append(',')
                append(quote(waypoint.category)).append(',')
                append(quote(waypoint.description)).append('\n')
            }
        }

        internal fun buildGeoJson(entries: List<PhotoEntry>, tripNames: Map<Long, String>): String = buildString {
            append("{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n")
            entries.forEachIndexed { index, entry ->
                val waypoint = entry.waypoint
                if (index > 0) append(",\n")
                append("    {\"type\": \"Feature\", \"properties\": {")
                append("\"file\": \"").append(GeoJsonExporter.escape(entry.zipPath)).append('"')
                append(", \"trip\": \"").append(GeoJsonExporter.escape(tripNames[waypoint.tripId].orEmpty())).append('"')
                append(", \"timestamp\": ").append(waypoint.timestamp)
                append(", \"altitude\": ").append(waypoint.altitude?.let { format(it) } ?: "null")
                append(", \"category\": \"").append(GeoJsonExporter.escape(waypoint.category)).append('"')
                append(", \"description\": \"").append(GeoJsonExporter.escape(waypoint.description)).append('"')
                append("}, \"geometry\": {\"type\": \"Point\", \"coordinates\": [")
                append(format(waypoint.longitude)).append(", ").append(format(waypoint.latitude))
                append("]}}")
            }
            append("\n  ]\n}\n")
        }

        private fun format(value: Double): String = String.format(Locale.US, "%.7f", value)

        private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\n", " ") + "\""
    }
}
