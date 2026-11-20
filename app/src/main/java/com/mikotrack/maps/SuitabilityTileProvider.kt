package com.mikotrack.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.mikotrack.ui.mushrooms.HabitatPreference
import com.mikotrack.ui.mushrooms.MushroomCatalog
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The habitat inputs of a tile — orientation, lithologies and forest formations — any of
 * which may be missing when its source failed.
 */
data class HabitatInputs(
    val aspect: ByteArray?,
    val lithologies: IntArray?,
    val trees: IntArray?,
)

/**
 * Produces the tiles of the "likely areas" overlay of a species.
 *
 * For every tile, the three habitat inputs of the same tile are gathered: the
 * orientation classes ([AspectTileProvider]), the substrate lithologies
 * ([SoilTileProvider]) and the forest formations, read from the colours of the MITECO
 * forest map. [SuitabilityScorer] combines them with the affinities of the species on a
 * coarser grid (enough for sources of this resolution, and four times cheaper) and draws
 * the scores inside the search circle.
 *
 * The source tiles are cached by [HabitatTileCache] and shared with the other habitat
 * overlays; the drawn tiles are cached under a key made of the species, its affinities
 * and the circle, but only when every input was available, so a tile drawn while a
 * source failed is computed again later. Every call blocks, so it must only be used from
 * a background thread (the local tile server calls it from a small dedicated pool).
 */
class SuitabilityTileProvider(
    private val context: Context,
    private val aspectTiles: AspectTileProvider,
    private val soilTiles: SoilTileProvider,
) {

    private val cache = HabitatTileCache(context)
    private val treesDirectory = File(cache.root, "trees-v$TREES_VERSION")
    private val tilesDirectory = File(cache.root, "suitability-v$ALGORITHM_VERSION")

    /** The formations of the latest forest tiles, shared by the deeper tiles. */
    private val treeCache = object : LruCache<String, IntArray>(MEMORY_CACHE_TILES) {}

    /** One lock per tile, so a forest tile requested twice at the same time is downloaded once. */
    private val locks = ConcurrentHashMap<String, Any>()

    /** Gathers the soil and forest inputs while the orientation is computed. */
    private val inputs = Executors.newFixedThreadPool(INPUT_THREADS) { runnable ->
        Thread(runnable, "mycotrack-suitability").apply { isDaemon = true }
    }

    private val treePalette = HabitatSources.treeLegend.map { (it.color and 0xFFFFFFL).toInt() }.toIntArray()
    private val lithologyGroups = HabitatSources.soilLegend.map { it.group.ordinal }.toIntArray()

    /**
     * The PNG of the likely areas tile at the given coordinates for a search, or `null`
     * when the tile is outside the circle, the species is unknown or nothing could be
     * computed.
     */
    fun tile(zoom: Int, x: Int, y: Int, search: SuitabilitySearch): ByteArray? {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) return null
        val tiles = 1 shl zoom
        if (x !in 0 until tiles || y !in 0 until tiles) return null
        val radius = search.radiusMeters.toDouble()
        if (!SuitabilityScorer.tileIntersectsCircle(zoom, x, y, search.latitude, search.longitude, radius)) return null
        cache.trimOnce { (it.name.startsWith("suitability-v") && it != tilesDirectory) ||
            (it.name.startsWith("trees-v") && it != treesDirectory) }

        val preference = preferencesOf(search.speciesIds) ?: return null
        val key = cacheKey(search, preference)
        val file = File(tilesDirectory, "$key/$zoom/$x/$y.png")
        cache.read(file)?.let { return it }

        val inputs = habitatInputs(zoom, x, y)
        val aspect = inputs.aspect
        val lithologies = inputs.lithologies
        val trees = inputs.trees
        if (aspect == null && lithologies == null && trees == null) return null

        val models = preference.map { SuitabilityScorer.Model(it.second) }
        // A cell counts as good when it suits at least one of the searched species.
        var scores: FloatArray? = null
        for (model in models) {
            val scoresOfModel = SuitabilityScorer.scoreGrid(model, GRID_SIZE, TILE_SIZE, trees, lithologies, lithologyGroups, aspect)
            scores = scores?.let { existing ->
                FloatArray(existing.size) { index -> maxOf(existing[index], scoresOfModel[index]) }
            } ?: scoresOfModel
        }
        val pixels = SuitabilityScorer.render(
            scores ?: return null, GRID_SIZE, TILE_SIZE, zoom, x, y, search.latitude, search.longitude, radius,
        )
        val png = HabitatTileCache.encodePng(pixels, TILE_SIZE)
        val complete = models.all { model ->
            (aspect != null || !model.usesAspect) &&
                (lithologies != null || !model.usesSoil) &&
                (trees != null || !model.usesTrees)
        }
        if (complete) cache.write(file, png) else Log.d(TAG, "Tile $zoom/$x/$y drawn without some inputs")
        return png
    }

    /**
     * The three habitat inputs of a tile, gathered concurrently: the orientation classes,
     * the substrate lithologies and the forest formations. Each one is `null` when it
     * could not be obtained.
     */
    fun habitatInputs(zoom: Int, x: Int, y: Int): HabitatInputs {
        val soilFuture = inputs.submit<IntArray?> { soilTiles.lithologies(zoom, x, y) }
        val treesFuture = inputs.submit<IntArray?> { trees(zoom, x, y) }
        val aspect = runCatching { aspectTiles.classGrid(zoom, x, y) }.getOrNull()
        val lithologies = runCatching { soilFuture.get(INPUT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrNull()
        val trees = runCatching { treesFuture.get(INPUT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrNull()
        return HabitatInputs(aspect, lithologies, trees)
    }

    /**
     * The habitats of the searched species of the guide paired with their ids, in the
     * order of [speciesIds] (the affinities do not depend on the language), or `null`
     * when none of them is known.
     */
    internal fun preferencesOf(speciesIds: List<String>): List<Pair<String, HabitatPreference>>? =
        runCatching { MushroomCatalog.load(context, "en") }
            .onFailure { Log.w(TAG, "Could not load the mushroom guide", it) }
            .getOrNull()
            ?.let { guide ->
                speciesIds.mapNotNull { id -> guide.firstOrNull { it.id == id }?.let { id to it.data.habitat } }
                    .takeIf { it.isNotEmpty() }
            }

    private fun cacheKey(search: SuitabilitySearch, preference: List<Pair<String, HabitatPreference>>): String {
        val text = "${search.toQuery()}|$preference"
        return Integer.toHexString(text.hashCode()) + "-" + Integer.toHexString(text.reversed().hashCode())
    }

    /** The forest formations of the pixels of a tile (scaled up from [TREES_MAX_ZOOM]). */
    private fun trees(zoom: Int, x: Int, y: Int): IntArray? {
        val depth = (zoom - TREES_MAX_ZOOM).coerceAtLeast(0)
        val sourceZoom = zoom - depth
        val sourceX = x shr depth
        val sourceY = y shr depth
        val classes = treeClasses(sourceZoom, sourceX, sourceY) ?: return null
        if (depth == 0) return classes
        return SuitabilityScorer.zoomIn(classes, TILE_SIZE, depth, x - (sourceX shl depth), y - (sourceY shl depth))
    }

    private fun treeClasses(zoom: Int, x: Int, y: Int): IntArray? {
        val key = "$zoom/$x/$y"
        treeCache.get(key)?.let { return it }
        val file = File(treesDirectory, "$key.png")
        val lock = locks.getOrPut(key) { Any() }
        try {
            synchronized(lock) {
                treeCache.get(key)?.let { return it }
                var pixels = cache.read(file)?.let(::decode)
                if (pixels == null) {
                    val bytes = cache.download(treesUrl(zoom, x, y)) ?: return null
                    pixels = decode(bytes) ?: return null
                    cache.write(file, bytes)
                }
                val classes = SuitabilityScorer.classifyTrees(pixels, TILE_SIZE, treePalette)
                treeCache.put(key, classes)
                return classes
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

    /** The unfiltered forest map of a tile, at the size of the tile. */
    private fun treesUrl(zoom: Int, x: Int, y: Int): String {
        val bbox = SoilClassifier.tileBounds(zoom, x, y).joinToString(",") { String.format(Locale.ROOT, "%.3f", it) }
        return HabitatSources.TREES_WMS_URL
            .replace("WIDTH=512&HEIGHT=512", "WIDTH=$TILE_SIZE&HEIGHT=$TILE_SIZE")
            .replace("{bbox-epsg-3857}", bbox)
    }

    companion object {
        private const val TAG = "SuitabilityTiles"

        /** Bumped whenever the scoring or the drawing change. */
        private const val ALGORITHM_VERSION = 1

        /** Bumped whenever the forest map or its request change. */
        private const val TREES_VERSION = 1

        /**
         * The largest circles (50 km) only fit on the screen at about this level. The
         * sources are coarse there, so zooming in gives more detailed tiles.
         */
        const val MIN_ZOOM = 7

        /** MapLibre scales up the tiles beyond this zoom level. */
        const val MAX_ZOOM = 16

        /** The forest polygons are detailed enough up to this level; deeper tiles reuse it. */
        private const val TREES_MAX_ZOOM = 15

        private const val TILE_SIZE = 256

        /** The scores are computed on a coarser grid and interpolated. */
        private const val GRID_SIZE = 128
        private const val MEMORY_CACHE_TILES = 8
        private const val INPUT_THREADS = 6
        private const val INPUT_TIMEOUT_MS = 40_000L
    }
}
