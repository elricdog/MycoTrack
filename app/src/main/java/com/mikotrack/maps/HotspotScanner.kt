package com.mikotrack.maps

import android.content.Context
import android.util.Log

/**
 * Scans the habitat of a multi-species search and returns its candidates: the places of
 * the circle where at least one of the searched species reaches a good score, with the
 * score every species reaches there.
 *
 * It reuses the pipeline of the likely areas tiles: the circle is scanned at the zoom
 * level [HotspotFinder.chooseZoom] chooses — one to a handful of tiles covering it — and
 * every tile's habitats are read with [SuitabilityTileProvider.habitatInputs], so the
 * same disk caches and the same source tiles as the visible overlay are used and the
 * scan is almost free once the overlay has been shown.
 *
 * [scan] blocks and downloads, so it must only be called from a background thread.
 */
class HotspotScanner private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val suitability by lazy {
        SuitabilityTileProvider(appContext, AspectTileProvider(appContext), SoilTileProvider(appContext))
    }

    /**
     * The candidates of [search] inside its radius, in raster order. Empty when none of
     * the species is known or nothing could be downloaded. [progress] receives the ratio
     * of tiles read, when given.
     */
    fun scan(search: SuitabilitySearch, progress: ((Float) -> Unit)? = null): List<HotspotCandidate> {
        val guide = runCatching { suitability.preferencesOf(search.speciesIds) }.getOrNull()
            ?: return emptyList()
        val models = guide.map { (id, preference) -> id to SuitabilityScorer.Model(preference) }
        val radius = search.radiusMeters.toDouble()
        val zoom = HotspotFinder.chooseZoom(search.latitude, radius)
        val (rows, columns) = HotspotFinder.tileRange(zoom, search.latitude, search.longitude, radius)
        Log.i(TAG, "Scanning zoom $zoom ($rows x $columns) for ${models.size} species within ${search.radiusMeters} m")

        val candidates = ArrayList<HotspotCandidate>()
        val tiles = rows.count() * columns.count()
        var read = 0
        for (row in rows) {
            for (column in columns) {
                read++
                progress?.invoke(read.toFloat() / tiles)
                if (!SuitabilityScorer.tileIntersectsCircle(
                        zoom, column, row, search.latitude, search.longitude, radius
                    )
                ) {
                    continue
                }
                val input = suitability.habitatInputs(zoom, column, row)
                if (input.aspect == null && input.lithologies == null && input.trees == null) continue
                val scores = models.map { (_, model) ->
                    SuitabilityScorer.scoreGrid(
                        model, GRID_SIZE, TILE_SIZE, input.trees, input.lithologies, lithologyGroups, input.aspect
                    )
                }
                for (cell in scores.first().indices) {
                    val best = scores.maxOf { it[cell] }
                    if (best < HotspotFinder.MIN_SCORE) continue
                    val pixelX = (cell % GRID_SIZE + 0.5) * CELL_PIXELS
                    val pixelY = (cell / GRID_SIZE + 0.5) * CELL_PIXELS
                    val lat = SuitabilityScorer.latitudeAt(zoom, row, pixelY, TILE_SIZE)
                    val lon = SuitabilityScorer.longitudeAt(zoom, column, pixelX, TILE_SIZE)
                    if (SuitabilityScorer.distanceMeters(search.latitude, search.longitude, lat, lon) > radius) continue
                    val speciesScores = ArrayList<Pair<String, Float>>(scores.size)
                    for (index in scores.indices) {
                        val score = scores[index][cell]
                        if (score >= HotspotFinder.SPECIES_MIN_SCORE) {
                            speciesScores += models[index].first to score
                        }
                    }
                    if (speciesScores.isNotEmpty()) {
                        candidates += HotspotCandidate(lat, lon, best, speciesScores)
                    }
                }
            }
        }
        Log.i(TAG, "Scan found ${candidates.size} candidate places")
        return candidates
    }

    private val lithologyGroups = HabitatSources.soilLegend.map { it.group.ordinal }.toIntArray()

    companion object {
        private const val TAG = "HotspotScanner"

        /** The scoring grid and tile sizes, the same the tiles are drawn with. */
        private const val GRID_SIZE = 128
        private const val TILE_SIZE = 256
        private const val CELL_PIXELS = TILE_SIZE / GRID_SIZE.toDouble()

        @Volatile
        private var instance: HotspotScanner? = null

        fun get(context: Context): HotspotScanner =
            instance ?: synchronized(this) {
                instance ?: HotspotScanner(context.applicationContext).also { instance = it }
            }
    }
}
