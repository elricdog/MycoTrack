package com.mikotrack.maps

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The disk cache and the downloads shared by the habitat tile providers (orientation and
 * soil). Everything lives below `cache/habitat`, which is kept under [MAX_CACHE_BYTES]:
 * once it grows bigger, the oldest files are deleted first.
 */
internal class HabitatTileCache(context: Context) {

    val root: File = File(context.applicationContext.cacheDir, CACHE_DIRECTORY)

    private val trimmed = AtomicBoolean(false)

    fun read(file: File): ByteArray? =
        if (file.isFile) runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() } else null

    fun write(file: File, bytes: ByteArray) {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, file.name + ".${Thread.currentThread().id}.part")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }.onFailure { Log.w(TAG, "Could not cache ${file.name}", it) }
    }

    /** Downloads a file, or returns `null` on any error (usually there is no connection). */
    fun download(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "$url answered ${connection.responseCode}")
                null
            } else {
                connection.inputStream.use { it.readBytes() }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not download $url: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Keeps the cache bounded, once per process, on a background thread.
     *
     * @param obsolete whether a top-level directory belongs to an older version of the
     * tiles and can be deleted right away.
     */
    fun trimOnce(obsolete: (File) -> Boolean) {
        if (!trimmed.compareAndSet(false, true)) return
        Thread({
            runCatching {
                root.listFiles()?.filter { it.isDirectory && obsolete(it) }?.forEach { it.deleteRecursively() }
                val files = root.walkTopDown().filter { it.isFile }.toMutableList()
                var total = files.sumOf { it.length() }
                if (total <= MAX_CACHE_BYTES) return@runCatching
                files.sortBy { it.lastModified() }
                for (file in files) {
                    if (total <= TRIMMED_CACHE_BYTES) break
                    val length = file.length()
                    if (file.delete()) total -= length
                }
            }.onFailure { Log.w(TAG, "Could not trim the habitat cache", it) }
        }, "mycotrack-habitat-trim").apply { isDaemon = true }.start()
    }

    companion object {
        private const val TAG = "HabitatTileCache"
        private const val CACHE_DIRECTORY = "habitat"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_CACHE_BYTES = 150L * 1024 * 1024
        private const val TRIMMED_CACHE_BYTES = 100L * 1024 * 1024
        private const val USER_AGENT = "MycoTrack (Android)"

        /** Encodes ARGB pixels as a PNG. */
        fun encodePng(pixels: IntArray, size: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
            return try {
                ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
