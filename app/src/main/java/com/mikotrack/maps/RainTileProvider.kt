package com.mikotrack.maps

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Produces the tiles of the rainfall overlay from the lattice stored by
 * [RainRepository]: the accumulation of the chosen window is interpolated per pixel and
 * painted with the [RainScale] ramp. Tiles outside the lattice are empty, and so are
 * all tiles when no data has been downloaded yet.
 *
 * The window sums are computed once per lattice and window and shared by every tile,
 * so drawing a tile is just interpolation. Every call blocks on the database, so it
 * must only be used from a background thread (the local tile server calls it from its
 * workers).
 */
class RainTileProvider(context: Context) {

    private val repository = RainRepository.get(context)

    /** The sums of the last fetch, per window; cleared when the lattice changes. */
    private val totals = ConcurrentHashMap<Int, Pair<Long, FloatArray>>()

    /** The PNG of the rainfall tile at the given coordinates, or `null` with no data. */
    fun tile(zoom: Int, x: Int, y: Int, windowDays: Int): ByteArray? {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) return null
        val tiles = 1 shl zoom
        if (x !in 0 until tiles || y !in 0 until tiles) return null
        val grid = runBlocking { repository.grid() }?.takeIf { it.hasData } ?: return null
        val version = runBlocking { repository.currentVersion() }
        val sums = totals.getOrPut(windowDays) { version to grid.windowTotals(windowDays) }
            .takeIf { it.first == version }
            ?: (version to grid.windowTotals(windowDays)).also { totals[windowDays] = it }
        val pixels = RainGrid.renderTile(
            sums.second, windowDays, grid.lat0, grid.lon0, grid.stepDeg, grid.rows, grid.cols,
            zoom, x, y, TILE_SIZE,
        )
        return HabitatTileCache.encodePng(pixels, TILE_SIZE)
    }

    companion object {
        /** The weather models are coarse, so deep tiles are scaled up. */
        const val MIN_ZOOM = 6
        const val MAX_ZOOM = 12

        private const val TILE_SIZE = 256
    }
}
