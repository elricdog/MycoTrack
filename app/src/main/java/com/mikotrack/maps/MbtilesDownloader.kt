package com.mikotrack.maps

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Downloads the tiles of a bounding box into an MBTiles file so the area stays available
 * without any network connection.
 */
class MbtilesDownloader {

    /** Result of a download run. */
    data class Result(
        val file: File,
        val downloadedTiles: Int,
        val failedTiles: Int,
        val sizeBytes: Long,
    )

    /**
     * Downloads every tile of [bounds] between [minZoom] and [maxZoom] from the given
     * [tileUrlTemplate] and stores it in [target].
     */
    suspend fun download(
        context: android.content.Context,
        target: File,
        name: String,
        tileUrlTemplate: String,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        tileSize: Int = 256,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val store = MbtilesStore(context, target)
        store.writeMetadata(
            name = name,
            bounds = bounds,
            minZoom = minZoom,
            maxZoom = maxZoom,
            format = MbtilesStore.FORMAT_PNG,
            tileSize = tileSize,
        )

        val jobs = buildList {
            for (zoom in minZoom..maxZoom) {
                val minX = TileMath.lonToTileX(bounds.minLongitude, zoom)
                val maxX = TileMath.lonToTileX(bounds.maxLongitude, zoom)
                val minY = TileMath.latToTileY(bounds.maxLatitude, zoom)
                val maxY = TileMath.latToTileY(bounds.minLatitude, zoom)
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        add(Triple(zoom, x, y))
                    }
                }
            }
        }

        val total = jobs.size
        var downloaded = 0
        var failed = 0
        val lock = Any()
        val pool = Executors.newFixedThreadPool(6)

        try {
            val futures = jobs.map { (zoom, x, y) ->
                pool.submit {
                    val data = fetchTile(tileUrlTemplate, zoom, x, y)
                    if (data != null) {
                        store.putTile(zoom, x, y, data)
                    }
                    synchronized(lock) {
                        if (data != null) downloaded++ else failed++
                        onProgress(downloaded + failed, total)
                    }
                }
            }
            futures.forEach { future ->
                runCatching { future.get() }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
            store.close()
        }

        Log.i(TAG, "Downloaded $downloaded tiles ($failed failed) into ${target.name}")

        Result(
            file = target,
            downloadedTiles = downloaded,
            failedTiles = failed,
            sizeBytes = target.length(),
        )
    }

    /** Downloads a single tile, returning `null` when the server does not provide it. */
    private fun fetchTile(template: String, zoom: Int, x: Int, y: Int): ByteArray? = runCatching {
        val url = URL(template.replace("{z}", zoom.toString()).replace("{x}", x.toString()).replace("{y}", y.toString()))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MycoTrack/1.0 (offline map downloader)")
        }
        try {
            if (connection.responseCode != 200) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    companion object {
        private const val TAG = "MbtilesDownloader"
    }
}
