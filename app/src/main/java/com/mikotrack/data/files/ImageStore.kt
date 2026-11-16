package com.mikotrack.data.files

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Stores the photographs attached to waypoints.
 *
 * Images live in the application private storage (`files/images/<tripId>`) so they stay
 * available offline and are removed together with the trip.
 */
class ImageStore(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    fun tripFolder(tripId: Long): File = File(root, tripId.toString()).apply { if (!exists()) mkdirs() }

    /** Creates a new, empty image file for a trip and returns it. */
    fun createImageFile(tripId: Long, prefix: String = "IMG"): File {
        val name = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        return File(tripFolder(tripId), name)
    }

    /** Copies the content of an URI into application storage. */
    fun importFromUri(tripId: Long, uri: Uri): File? = runCatching {
        val target = createImageFile(tripId)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrNull()

    /** Deletes a stored image, ignoring files that are already gone. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    /** Deletes every image belonging to a trip. */
    fun deleteTripImages(tripId: Long) {
        runCatching { tripFolder(tripId).listFiles()?.forEach { it.delete() } }
    }

    /** Reads the GPS coordinates stored in the EXIF data of a photo, if present. */
    fun readExifLocation(path: String?): Pair<Double, Double>? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val latLong = exif.getLatLong() ?: return null
            latLong[0] to latLong[1]
        }.getOrNull()
    }
}
