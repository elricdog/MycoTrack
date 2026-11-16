package com.mikotrack.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Produces the tiles of the soil (substrate) overlay.
 *
 * The tiles of the IGME lithological map are downloaded through its WMS, cached as they
 * are and recoloured on every request by [SoilClassifier]: one colour per substrate
 * group, and only for the lithologies the user selected. Changing the selection
 * therefore does not download anything again. Every call blocks, so it must only be
 * used from a background thread (the local tile server calls it from its workers).
 */
class SoilTileProvider(context: Context) {

    private val cache = HabitatTileCache(context)
    private val rawDirectory = File(cache.root, "soil-v$SOURCE_VERSION")

    /** One lock per tile, so a tile requested twice at the same time is downloaded once. */
    private val locks = ConcurrentHashMap<String, Any>()

    private val palette = HabitatSources.soilLegend.map { it.sourceColor }.toIntArray()
    private val colors = HabitatSources.soilLegend.map { (it.group.color and 0xFFFFFFFFL).toInt() }.toIntArray()

    /** The lithologies of the latest source tiles, shared by the deeper probability tiles. */
    private val classCache = object : LruCache<String, IntArray>(CLASS_CACHE_TILES) {}

    /**
     * The PNG of the soil tile at the given coordinates drawing only the lithologies of
     * the [visible] mask, or `null` when it cannot be produced (usually offline).
     */
    fun tile(zoom: Int, x: Int, y: Int, visible: Long): ByteArray? {
        if (zoom < 0 || zoom > MAX_ZOOM) return null
        val tiles = 1 shl zoom
        if (x !in 0 until tiles || y !in 0 until tiles) return null
        cache.trimOnce { it.name.startsWith("soil-v") && it != rawDirectory }

        val pixels = sourcePixels(zoom, x, y) ?: return null
        val recolored = SoilClassifier.recolor(pixels, TILE_SIZE, palette, colors, visible)
        return HabitatTileCache.encodePng(recolored, TILE_SIZE)
    }

    /**
     * The lithology (index in [HabitatSources.soilLegend]) of every pixel of the tile at
     * the given coordinates, or -1 outside the map; `null` when the source tile cannot
     * be obtained. Beyond [MAX_ZOOM] the classes of the ancestor tile are scaled up.
     * Used by the probability overlay ([SuitabilityTileProvider]).
     */
    fun lithologies(zoom: Int, x: Int, y: Int): IntArray? {
        if (zoom < MIN_ZOOM) return null
        val depth = (zoom - MAX_ZOOM).coerceAtLeast(0)
        val sourceZoom = zoom - depth
        val sourceX = x shr depth
        val sourceY = y shr depth
        val key = "$sourceZoom/$sourceX/$sourceY"
        val classes = classCache.get(key) ?: run {
            val pixels = sourcePixels(sourceZoom, sourceX, sourceY) ?: return null
            SoilClassifier.classify(pixels, TILE_SIZE, palette).also { classCache.put(key, it) }
        }
        if (depth == 0) return classes
        return SuitabilityScorer.zoomIn(classes, TILE_SIZE, depth, x - (sourceX shl depth), y - (sourceY shl depth))
    }

    private fun sourcePixels(zoom: Int, x: Int, y: Int): IntArray? {
        val key = "$zoom/$x/$y"
        val file = File(rawDirectory, "$key.png")
        cache.read(file)?.let { bytes -> decode(bytes)?.let { return it } }
        val lock = locks.getOrPut(key) { Any() }
        try {
            synchronized(lock) {
                cache.read(file)?.let { bytes -> decode(bytes)?.let { return it } }
                val bytes = cache.download(sourceUrl(zoom, x, y)) ?: return null
                val pixels = decode(bytes) ?: return null
                cache.write(file, bytes)
                return pixels
            }
        } finally {
            locks.remove(key)
        }
    }

    private fun decode(bytes: ByteArray): IntArray? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inPremultiplied = false
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        try {
            if (bitmap.width != TILE_SIZE || bitmap.height != TILE_SIZE) return null
            val pixels = IntArray(TILE_SIZE * TILE_SIZE)
            bitmap.getPixels(pixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE)
            return pixels
        } finally {
            bitmap.recycle()
        }
    }

    private fun sourceUrl(zoom: Int, x: Int, y: Int): String {
        val bounds = SoilClassifier.tileBounds(zoom, x, y)
        val bbox = bounds.joinToString(",") { String.format(Locale.ROOT, "%.3f", it) }
        return "${HabitatSources.SOIL_WMS_URL}&WIDTH=$TILE_SIZE&HEIGHT=$TILE_SIZE&BBOX=$bbox"
    }

    companion object {
        /** Bumped whenever the source map or its request change. */
        private const val SOURCE_VERSION = 1

        /** The map is drawn at 1:1,000,000: deeper tiles would add no detail. */
        const val MAX_ZOOM = 12
        const val MIN_ZOOM = 6

        private const val TILE_SIZE = 256
        private const val CLASS_CACHE_TILES = 8
    }
}
