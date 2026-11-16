package com.mikotrack.maps

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.sinh
import kotlin.math.tan

/** Conversion between WGS-84 coordinates and the slippy map tile scheme. */
object TileMath {

    /** Converts a longitude to the X tile index at the given zoom level. */
    fun lonToTileX(longitude: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return ((longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    /** Converts a latitude to the Y tile index at the given zoom level. */
    fun latToTileY(latitude: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val latRad = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
        val value = (1.0 - asinh(tan(latRad)) / PI) / 2.0
        return (value * n).toInt().coerceIn(0, n - 1)
    }

    /** Converts a tile X index back to a longitude. */
    fun tileXToLon(x: Int, zoom: Int): Double {
        val n = 1 shl zoom
        return x / n.toDouble() * 360.0 - 180.0
    }

    /** Converts a tile Y index back to a latitude. */
    fun tileYToLat(y: Int, zoom: Int): Double {
        val n = 1 shl zoom
        val value = atan(sinh(PI * (1.0 - 2.0 * y / n)))
        return Math.toDegrees(value)
    }

    /** Converts a slippy map Y index into the TMS row used inside MBTiles files. */
    fun toTmsRow(zoom: Int, slippyY: Int): Int = (1 shl zoom) - 1 - slippyY

    /** Converts a TMS row into the slippy map Y index. */
    fun fromTmsRow(zoom: Int, tmsRow: Int): Int = (1 shl zoom) - 1 - tmsRow

    /** Number of tiles needed to cover a bounding box at the given zoom level. */
    fun tileCount(
        minLatitude: Double,
        minLongitude: Double,
        maxLatitude: Double,
        maxLongitude: Double,
        zoom: Int,
    ): Int {
        val minX = lonToTileX(minLongitude, zoom)
        val maxX = lonToTileX(maxLongitude, zoom)
        val minY = latToTileY(maxLatitude, zoom)
        val maxY = latToTileY(minLatitude, zoom)
        val width = (maxX - minX + 1).coerceAtLeast(1)
        val height = (maxY - minY + 1).coerceAtLeast(1)
        return width * height
    }
}
