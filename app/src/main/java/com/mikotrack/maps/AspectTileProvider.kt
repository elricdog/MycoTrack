package com.mikotrack.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Produces the tiles of the orientation (aspect) overlay.
 *
 * The orientation is computed on the device from the public Terrarium elevation tiles:
 * the elevation tile and its eight neighbours (for the one pixel border Horn's method
 * needs) are downloaded and every pixel is classified with [AspectClassifier]. The
 * classes are then drawn as textures by [AspectHatch], only for the classes the user
 * selected, and encoded as a transparent PNG.
 *
 * The elevation tiles stop at zoom [ELEVATION_MAX_ZOOM]; deeper tiles reuse the classes
 * of their ancestor but draw the textures at their own resolution, so the lines keep the
 * same spacing on the screen up to [MAX_ZOOM].
 *
 * The elevation tiles, the classes and the drawn tiles (one folder per set of classes)
 * are cached in the cache directory of the application, so a tile is only computed
 * once. Every call blocks, so it must only be used from a background thread (the local
 * tile server calls it from its workers).
 */
class AspectTileProvider(context: Context) {

    private val cache = HabitatTileCache(context)
    private val demDirectory = File(cache.root, "terrarium")
    private val aspectDirectory = File(cache.root, "aspect-v$ALGORITHM_VERSION")
    private val classDirectory = File(aspectDirectory, "classes")

    /** Decoded elevation tiles, shared by the neighbouring tiles that need them. */
    private val elevationCache = object : LruCache<String, FloatArray>(MEMORY_CACHE_TILES) {}

    /** Classified tiles, shared by the tiles of the deeper zoom levels. */
    private val classCache = object : LruCache<String, ByteArray>(MEMORY_CACHE_TILES) {}

    /** One lock per tile, so a tile requested twice at the same time is computed once. */
    private val locks = ConcurrentHashMap<String, Any>()

    /** Downloads the nine elevation tiles of a tile in parallel. */
    private val downloader = Executors.newFixedThreadPool(DOWNLOAD_THREADS) { runnable ->
        Thread(runnable, "mycotrack-terrarium").apply { isDaemon = true }
    }

    /**
     * The PNG of the aspect tile at the given coordinates drawing only the given
     * classes, or `null` when it cannot be produced, for instance without an internet
     * connection.
     */
    fun tile(zoom: Int, x: Int, y: Int, classes: Set<AspectClass>): ByteArray? {
        if (zoom < 0 || zoom > MAX_ZOOM) return null
        val tiles = 1 shl zoom
        if (x !in 0 until tiles || y !in 0 until tiles) return null
        cache.trimOnce { it.name.startsWith("aspect-v") && it != aspectDirectory }

        val code = AspectHatch.encode(classes).ifEmpty { "none" }
        val file = File(aspectDirectory, "png-$code/$zoom/$x/$y.png")
        cache.read(file)?.let { return it }

        val depth = (zoom - ELEVATION_MAX_ZOOM).coerceAtLeast(0)
        val sourceZoom = zoom - depth
        val sourceX = x shr depth
        val sourceY = y shr depth
        val grid = classes(sourceZoom, sourceX, sourceY) ?: return null
        // The pixels of this tile inside its ancestor, which is 2^depth times coarser.
        val offsetX = (x - (sourceX shl depth)) * TILE_SIZE
        val offsetY = (y - (sourceY shl depth)) * TILE_SIZE
        val pixels = AspectHatch.render(
            classAt = { column, row ->
                val sourceColumn = (offsetX + column) shr depth
                val sourceRow = (offsetY + row) shr depth
                CLASSES[grid[sourceRow * TILE_SIZE + sourceColumn].toInt()]
            },
            size = TILE_SIZE,
            tileX = x,
            tileY = y,
            enabled = classes,
        )
        val png = HabitatTileCache.encodePng(pixels, TILE_SIZE)
        cache.write(file, png)
        return png
    }

    /**
     * The [AspectClass] ordinal of every pixel of the tile at the given coordinates, or
     * `null` when the elevation tiles cannot be obtained. Beyond [ELEVATION_MAX_ZOOM] the
     * classes of the ancestor tile are scaled up. Used by the probability overlay
     * ([SuitabilityTileProvider]).
     */
    fun classGrid(zoom: Int, x: Int, y: Int): ByteArray? {
        if (zoom < 0 || zoom > MAX_ZOOM) return null
        val depth = (zoom - ELEVATION_MAX_ZOOM).coerceAtLeast(0)
        val sourceX = x shr depth
        val sourceY = y shr depth
        val grid = classes(zoom - depth, sourceX, sourceY) ?: return null
        if (depth == 0) return grid
        val offsetX = (x - (sourceX shl depth)) * TILE_SIZE
        val offsetY = (y - (sourceY shl depth)) * TILE_SIZE
        return ByteArray(TILE_SIZE * TILE_SIZE) { index ->
            val sourceColumn = (offsetX + index % TILE_SIZE) shr depth
            val sourceRow = (offsetY + index / TILE_SIZE) shr depth
            grid[sourceRow * TILE_SIZE + sourceColumn]
        }
    }

    /**
     * The elevations in metres of the `256 × 256` pixels of a Terrarium tile, row by row
     * from north to south, or `null` when it cannot be obtained. Used by
     * [HabitatPointInspector] to read the terrain at a single point.
     */
    fun elevationTile(zoom: Int, x: Int, y: Int): FloatArray? {
        if (zoom < 0 || zoom > ELEVATION_MAX_ZOOM) return null
        val tiles = 1 shl zoom
        if (x !in 0 until tiles || y !in 0 until tiles) return null
        return elevations(zoom, x, y)
    }

    /** The classes of the pixels of a tile up to [ELEVATION_MAX_ZOOM], computed once. */
    private fun classes(zoom: Int, x: Int, y: Int): ByteArray? {
        val key = "$zoom/$x/$y"
        classCache.get(key)?.let { return it }
        val file = File(classDirectory, "$key.bin")
        val lock = locks.getOrPut(key) { Any() }
        try {
            synchronized(lock) {
                classCache.get(key)?.let { return it }
                val stored = cache.read(file)?.let { inflate(it) }
                val grid = stored ?: compute(zoom, x, y)?.also { cache.write(file, deflate(it)) } ?: return null
                classCache.put(key, grid)
                return grid
            }
        } finally {
            locks.remove(key)
        }
    }

    private fun compute(zoom: Int, x: Int, y: Int): ByteArray? {
        val tiles = 1 shl zoom
        // The tile itself and its eight neighbours; the rows beyond the poles do not exist.
        val futures = HashMap<Pair<Int, Int>, Future<FloatArray?>>()
        for (dy in -1..1) {
            for (dx in -1..1) {
                val ny = y + dy
                if (ny !in 0 until tiles) continue
                val nx = Math.floorMod(x + dx, tiles)
                futures[dx to dy] = downloader.submit<FloatArray?> { elevations(zoom, nx, ny) }
            }
        }
        val neighbours = futures.mapValues { (_, future) ->
            runCatching { future.get(TOTAL_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrNull()
        }
        val center = neighbours[0 to 0] ?: return null

        val size = TILE_SIZE
        val stride = size + 2
        val grid = FloatArray(stride * stride)
        for (row in -1..size) {
            val dy = if (row < 0) -1 else if (row >= size) 1 else 0
            for (column in -1..size) {
                val dx = if (column < 0) -1 else if (column >= size) 1 else 0
                val neighbour = neighbours[dx to dy]
                // A missing neighbour is replaced by the edge of the tile itself.
                val value = if (neighbour != null) {
                    neighbour[Math.floorMod(row, size) * size + Math.floorMod(column, size)]
                } else {
                    center[row.coerceIn(0, size - 1) * size + column.coerceIn(0, size - 1)]
                }
                grid[(row + 1) * stride + column + 1] = value
            }
        }
        return AspectClassifier.classifyTile(grid, size) { row ->
            AspectClassifier.pixelSizeMeters(zoom, y, row, size)
        }
    }

    /** The elevations of a Terrarium tile, from memory, from the disk or downloaded. */
    private fun elevations(zoom: Int, x: Int, y: Int): FloatArray? {
        val key = "$zoom/$x/$y"
        elevationCache.get(key)?.let { return it }

        val file = File(demDirectory, "$key.png")
        var bytes = cache.read(file)
        if (bytes == null) {
            bytes = cache.download(TERRAIN_DEM_URL.replace("{z}", "$zoom").replace("{x}", "$x").replace("{y}", "$y"))
                ?: return null
        }
        val decoded = decode(bytes)
        if (decoded == null) {
            file.delete()
            return null
        }
        if (!file.exists()) cache.write(file, bytes)
        elevationCache.put(key, decoded)
        return decoded
    }

    private fun decode(bytes: ByteArray): FloatArray? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        try {
            if (bitmap.width != TILE_SIZE || bitmap.height != TILE_SIZE) return null
            val pixels = IntArray(TILE_SIZE * TILE_SIZE)
            bitmap.getPixels(pixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE)
            return FloatArray(pixels.size) { AspectClassifier.terrariumElevation(pixels[it]) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val output = ByteArrayOutputStream(data.size / 8)
            val buffer = ByteArray(8192)
            while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer))
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val grid = ByteArray(TILE_SIZE * TILE_SIZE)
            var read = 0
            while (read < grid.size && !inflater.finished()) {
                val count = inflater.inflate(grid, read, grid.size - read)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                read += count
            }
            grid.takeIf { read == grid.size && it.all { value -> value in 0 until CLASSES.size } }
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    companion object {
        /** Bumped whenever the classification or the textures change. */
        private const val ALGORITHM_VERSION = 2

        /** The highest zoom level of the Terrarium tiles. */
        const val ELEVATION_MAX_ZOOM = 15

        /** The highest zoom level that is drawn; MapLibre scales up the tiles beyond it. */
        const val MAX_ZOOM = 18

        /** The lowest zoom level at which the orientation is still meaningful. */
        const val MIN_ZOOM = 10

        private const val TILE_SIZE = 256
        private const val MEMORY_CACHE_TILES = 24
        private const val DOWNLOAD_THREADS = 6
        private const val TOTAL_TIMEOUT_MS = 30_000L
        private val CLASSES = AspectClass.entries.toTypedArray()
    }
}
