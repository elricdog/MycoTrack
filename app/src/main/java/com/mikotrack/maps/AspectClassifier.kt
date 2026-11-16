package com.mikotrack.maps

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sinh

/** The orientation classes of a slope that the habitat layer distinguishes. */
enum class AspectClass {
    /** Nearly flat terrain, which has no meaningful orientation. */
    FLAT,

    /** Facing north (±45°): shadier and wetter, usually the best for mushrooms. */
    NORTH,

    /** Facing east or west: an intermediate exposure. */
    NEUTRAL,

    /** Facing south (±45°): sunnier and drier. */
    SOUTH,
}

/**
 * Pure functions that derive the orientation (aspect) of the terrain from an elevation
 * grid; [AspectHatch] turns the classes into the textures of the orientation overlay.
 *
 * The gradient is computed with Horn's method over a 3×3 window, the same estimator
 * used by GDAL and most GIS tools.
 */
object AspectClassifier {

    /** Slopes gentler than this, in degrees, are considered flat and left transparent. */
    const val FLAT_SLOPE_DEGREES = 4.0

    /** The circumference of the Earth at the equator, in metres (Web Mercator sphere). */
    private const val EARTH_CIRCUMFERENCE = 40_075_016.686

    /**
     * The compass direction, in degrees clockwise from north in [0, 360), that a slope
     * faces, i.e. the direction in which the terrain goes down.
     *
     * @param dzdxEast the rise of the terrain per metre towards the east.
     * @param dzdyNorth the rise of the terrain per metre towards the north.
     */
    fun aspectDegrees(dzdxEast: Double, dzdyNorth: Double): Double {
        val degrees = Math.toDegrees(atan2(-dzdxEast, -dzdyNorth))
        return (degrees + 360.0) % 360.0
    }

    /** The steepness of the terrain in degrees for the given gradient. */
    fun slopeDegrees(dzdxEast: Double, dzdyNorth: Double): Double =
        Math.toDegrees(atan(hypot(dzdxEast, dzdyNorth)))

    /** The class of a compass direction, ignoring the steepness. */
    fun classifyAspect(aspectDegrees: Double): AspectClass {
        val aspect = ((aspectDegrees % 360.0) + 360.0) % 360.0
        return when {
            aspect >= 315.0 || aspect < 45.0 -> AspectClass.NORTH
            aspect >= 135.0 && aspect < 225.0 -> AspectClass.SOUTH
            else -> AspectClass.NEUTRAL
        }
    }

    /** The class of a slope given its gradient (see [aspectDegrees] for the units). */
    fun classify(
        dzdxEast: Double,
        dzdyNorth: Double,
        flatSlopeDegrees: Double = FLAT_SLOPE_DEGREES,
    ): AspectClass {
        if (slopeDegrees(dzdxEast, dzdyNorth) < flatSlopeDegrees) return AspectClass.FLAT
        return classifyAspect(aspectDegrees(dzdxEast, dzdyNorth))
    }

    /**
     * The ground size, in metres, of a pixel of a Web Mercator tile at a given row.
     *
     * @param zoom the zoom level of the tile.
     * @param tileY the row of the tile.
     * @param pixelRow the row of the pixel inside the tile.
     * @param tileSize the edge length of the tile in pixels.
     */
    fun pixelSizeMeters(zoom: Int, tileY: Int, pixelRow: Int, tileSize: Int): Double {
        val tiles = 1 shl zoom
        val mercatorY = (tileY + (pixelRow + 0.5) / tileSize) / tiles
        val latitude = atan(sinh(PI * (1.0 - 2.0 * mercatorY)))
        return EARTH_CIRCUMFERENCE * cos(latitude) / (tileSize.toDouble() * tiles)
    }

    /**
     * Classifies every pixel of a tile by the orientation of the terrain.
     *
     * @param elevations the elevations in metres of a `(size + 2) × (size + 2)` grid:
     * the tile plus a one pixel border taken from the neighbouring tiles, row by row
     * from north to south.
     * @param size the edge length of the tile in pixels.
     * @param cellSizeMeters the ground size of a pixel for every row of the tile.
     * @return the [AspectClass.ordinal] of the `size × size` pixels, row by row.
     */
    fun classifyTile(elevations: FloatArray, size: Int, cellSizeMeters: (Int) -> Double): ByteArray {
        val stride = size + 2
        require(elevations.size == stride * stride) { "The grid must include a one pixel border" }
        val classes = ByteArray(size * size)
        for (row in 0 until size) {
            val cell = cellSizeMeters(row)
            val north = row * stride
            val middle = (row + 1) * stride
            val south = (row + 2) * stride
            for (column in 0 until size) {
                val a = elevations[north + column]
                val b = elevations[north + column + 1]
                val c = elevations[north + column + 2]
                val d = elevations[middle + column]
                val f = elevations[middle + column + 2]
                val g = elevations[south + column]
                val h = elevations[south + column + 1]
                val i = elevations[south + column + 2]
                val dzdx = ((c + 2 * f + i) - (a + 2 * d + g)) / (8.0 * cell)
                val dzdy = ((a + 2 * b + c) - (g + 2 * h + i)) / (8.0 * cell)
                classes[row * size + column] = classify(dzdx, dzdy).ordinal.toByte()
            }
        }
        return classes
    }

    /** Decodes a Terrarium pixel (ARGB) into an elevation in metres. */
    fun terrariumElevation(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 256f + g + b / 256f) - 32768f
    }
}
