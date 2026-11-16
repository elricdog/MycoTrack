package com.mikotrack.maps

/**
 * Pure functions of the soil (substrate) overlay: they turn a tile of the IGME
 * lithological map, drawn with one colour per lithology, into a tile drawn with one
 * colour per substrate group (acid, basic, other) and only for the lithologies the user
 * selected.
 */
object SoilClassifier {

    /** The largest squared RGB distance at which a pixel still matches a palette colour. */
    const val MATCH_TOLERANCE_SQUARED = 3 * 10 * 10

    /** Pixels more transparent than this are outside the map. */
    private const val MIN_ALPHA = 128

    private const val HALF_WORLD_METERS = 20_037_508.342789244

    /**
     * The index of the palette colour closest to a pixel, or -1 when none is within
     * [MATCH_TOLERANCE_SQUARED] (the blended pixels along the borders of the polygons).
     */
    fun match(argb: Int, palette: IntArray): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        var best = -1
        var bestDistance = Int.MAX_VALUE
        for (index in palette.indices) {
            val color = palette[index]
            val dr = r - ((color shr 16) and 0xFF)
            val dg = g - ((color shr 8) and 0xFF)
            val db = b - (color and 0xFF)
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return if (bestDistance <= MATCH_TOLERANCE_SQUARED) best else -1
    }

    /**
     * Recolours a tile of the lithological map.
     *
     * @param pixels the ARGB pixels of the source tile, row by row.
     * @param width the width of the tile in pixels.
     * @param palette the RGB colour of every lithology in the source map.
     * @param colors the ARGB colour every lithology is drawn with.
     * @param visible the bit mask of the lithologies to draw (bit `i` is `palette[i]`).
     * @return the ARGB pixels of the overlay; the hidden lithologies are transparent.
     */
    fun recolor(pixels: IntArray, width: Int, palette: IntArray, colors: IntArray, visible: Long): IntArray {
        require(palette.size == colors.size) { "Every lithology needs a colour" }
        val classes = classify(pixels, width, palette)
        val output = IntArray(pixels.size)
        for (index in pixels.indices) {
            val lithology = classes[index]
            if (lithology >= 0 && isVisible(visible, lithology)) output[index] = colors[lithology]
        }
        return output
    }

    /**
     * The lithology (index in [palette]) of every pixel of a tile of the lithological
     * map, or -1 for the transparent pixels outside the map.
     */
    fun classify(pixels: IntArray, width: Int, palette: IntArray): IntArray {
        val classes = IntArray(pixels.size) { -1 }
        for (index in pixels.indices) {
            val argb = pixels[index]
            if ((argb ushr 24) < MIN_ALPHA) continue
            var lithology = match(argb, palette)
            if (lithology < 0) {
                // A blended border pixel takes the class of a neighbour already seen.
                val column = index % width
                lithology = when {
                    column > 0 && classes[index - 1] >= 0 -> classes[index - 1]
                    index >= width && classes[index - width] >= 0 -> classes[index - width]
                    else -> nearest(argb, palette)
                }
            }
            classes[index] = lithology
        }
        return classes
    }

    /** Whether bit [index] of a visibility mask is set. */
    fun isVisible(mask: Long, index: Int): Boolean = index in 0 until 63 && (mask shr index) and 1L == 1L

    /** The mask with every one of [count] lithologies visible. */
    fun allVisible(count: Int): Long = if (count >= 63) Long.MAX_VALUE else (1L shl count) - 1

    /** The hexadecimal code of a mask, used in the tile URLs. */
    fun encodeMask(mask: Long): String = java.lang.Long.toHexString(mask)

    /** The mask of a code made by [encodeMask], or `null` when it is not valid. */
    fun decodeMask(code: String?): Long? = code?.toLongOrNull(16)?.takeIf { it >= 0 }

    /** The Web Mercator bounds (west, south, east, north) of a tile, in metres. */
    fun tileBounds(zoom: Int, x: Int, y: Int): DoubleArray {
        val span = 2 * HALF_WORLD_METERS / (1 shl zoom)
        val west = -HALF_WORLD_METERS + x * span
        val north = HALF_WORLD_METERS - y * span
        return doubleArrayOf(west, north - span, west + span, north)
    }

    private fun nearest(argb: Int, palette: IntArray): Int {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (index in palette.indices) {
            val color = palette[index]
            val dr = ((argb shr 16) and 0xFF) - ((color shr 16) and 0xFF)
            val dg = ((argb shr 8) and 0xFF) - ((color shr 8) and 0xFF)
            val db = (argb and 0xFF) - (color and 0xFF)
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }
}
