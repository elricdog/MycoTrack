package com.mikotrack.maps

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

/**
 * The accumulated precipitation of the weather model on a regular lattice.
 *
 * The lattice is the grid of coordinates requested to the weather service; every node
 * holds the millimetres of each finished day (the model snaps the requested points to
 * its own cells, which only shifts them by a few kilometres). The value of a window
 * (3, 7, 15 or 30 days) is the sum of the last days that fall inside it, and the tiles
 * bilinearly interpolate that sum, which is smooth enough for a source of this
 * resolution.
 *
 * The class is pure Kotlin so that it can be tested on the JVM.
 *
 * @param lat0 the latitude of the first row, in degrees (the southernmost row).
 * @param lon0 the longitude of the first column, in degrees (the westernmost column).
 * @param stepDeg the spacing of the lattice, in degrees.
 * @param rows the number of lattice rows (south to north).
 * @param cols the number of lattice columns (west to east).
 * @param days the epoch days with data, ascending; the window sums end on the last one.
 * @param millimeters `rows * cols * days.size` values in millimetres, node-major: the
 * value of node `n` on day `i` is at `(n * days.size) + i`.
 */
class RainGrid(
    val lat0: Double,
    val lon0: Double,
    val stepDeg: Double,
    val rows: Int,
    val cols: Int,
    val days: IntArray,
    val millimeters: FloatArray,
) {

    /** The last day with data, the day every window ends on. */
    val latestDay: Int get() = if (days.isEmpty()) 0 else days.last()

    /** Whether the lattice holds at least one day of data. */
    val hasData: Boolean get() = days.isNotEmpty()

    /**
     * The millimetres accumulated in the last [windowDays] days (the window ends on
     * [latestDay]) for every node of the lattice.
     */
    fun windowTotals(windowDays: Int): FloatArray {
        val totals = FloatArray(rows * cols)
        if (days.isEmpty()) return totals
        val firstDay = days.last() - windowDays + 1
        for (d in days.indices) {
            if (days[d] < firstDay) continue
            for (node in totals.indices) totals[node] += millimeters[node * days.size + d]
        }
        return totals
    }

    /**
     * The bilinear interpolation of [totals] at a coordinate, or `null` when the point
     * lies outside the lattice (plus a margin of half a cell, which covers the snapping
     * of the model).
     */
    fun sample(totals: FloatArray, latitude: Double, longitude: Double): Float? {
        val column = (longitude - lon0) / stepDeg
        val row = (latitude - lat0) / stepDeg
        val margin = 0.5
        if (column < -margin || column > cols - 1 + margin || row < -margin || row > rows - 1 + margin) return null
        val c0 = column.coerceIn(0.0, (cols - 2).coerceAtLeast(0).toDouble()).toInt()
        val r0 = row.coerceIn(0.0, (rows - 2).coerceAtLeast(0).toDouble()).toInt()
        val c1 = (c0 + 1).coerceAtMost(cols - 1)
        val r1 = (r0 + 1).coerceAtMost(rows - 1)
        val fc = (column - c0).coerceIn(0.0, 1.0).toFloat()
        val fr = (row - r0).coerceIn(0.0, 1.0).toFloat()
        val southWest = totals[r0 * cols + c0]
        val southEast = totals[r0 * cols + c1]
        val northWest = totals[r1 * cols + c0]
        val northEast = totals[r1 * cols + c1]
        val bottom = southWest + (southEast - southWest) * fc
        val top = northWest + (northEast - northWest) * fc
        return bottom + (top - bottom) * fr
    }

    companion object {

        /**
         * The ARGB pixels of a rainfall tile: every pixel is bilinearly interpolated
         * from [totals], turned into an intensity (millimetres per day, so one ramp
         * fits every window) and coloured with [RainScale]. Pixels outside the lattice
         * stay transparent.
         */
        fun renderTile(
            totals: FloatArray,
            windowDays: Int,
            lat0: Double,
            lon0: Double,
            stepDeg: Double,
            rows: Int,
            cols: Int,
            zoom: Int,
            x: Int,
            y: Int,
            size: Int,
        ): IntArray {
            val grid = RainGrid(lat0, lon0, stepDeg, rows, cols, intArrayOf(0), FloatArray(totals.size))
            val tiles = 1 shl zoom
            val perDay = 1f / windowDays.coerceAtLeast(1)
            val pixels = IntArray(size * size)
            for (py in 0 until size) {
                val latitude = pixelToLatitude(y + py.toDouble() / size, tiles)
                for (px in 0 until size) {
                    val longitude = pixelToLongitude(x + px.toDouble() / size, tiles)
                    val mm = grid.sample(totals, latitude, longitude)
                    pixels[py * size + px] = if (mm == null || mm <= 0.01f) 0 else RainScale.color(mm * perDay)
                }
            }
            return pixels
        }

        /** The longitude of a horizontal Web Mercator coordinate (`x`, in tiles). */
        fun pixelToLongitude(tileX: Double, tiles: Int): Double = tileX / tiles * 360.0 - 180.0

        /** The latitude of a vertical Web Mercator coordinate (`y`, in tiles). */
        fun pixelToLatitude(tileY: Double, tiles: Int): Double =
            Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * tileY / tiles))))
    }
}
