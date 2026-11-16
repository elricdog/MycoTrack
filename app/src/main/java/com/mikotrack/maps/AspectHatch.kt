package com.mikotrack.maps

/**
 * The textures of the orientation overlay.
 *
 * Instead of a solid fill, every class is drawn as a sparse pattern so the layers below
 * (the tree types, the soil and the base map) stay visible: north-facing slopes get blue
 * `////` lines, south-facing ones orange `\\\\` lines and east/west ones a light grid of
 * dots. The patterns are computed from the global pixel coordinates of the tile
 * (`tileX × 256 + x`, `tileY × 256 + y`), so they continue seamlessly across tile borders
 * and keep the same spacing on the screen at every zoom level.
 */
object AspectHatch {

    /** The distance between two parallel lines, in tile pixels. */
    const val LINE_SPACING = 10

    /** The width of a line, measured along a pixel row, in tile pixels. */
    const val LINE_WIDTH = 2

    /** The distance between two dots of the east/west pattern, in tile pixels. */
    const val DOT_SPACING = 6

    /** The colours (ARGB, not premultiplied) of the patterns. */
    const val NORTH_COLOR: Int = (235 shl 24) or (20 shl 16) or (80 shl 8) or 220
    const val SOUTH_COLOR: Int = (235 shl 24) or (240 shl 16) or (110 shl 8) or 20
    const val NEUTRAL_COLOR: Int = (200 shl 24) or (150 shl 16) or (120 shl 8) or 60

    /** The classes drawn until the user picks others: the east/west slopes add little. */
    val DEFAULT_CLASSES: Set<AspectClass> = setOf(AspectClass.NORTH, AspectClass.SOUTH)

    /** The classes that can be drawn, in the order of their codes. */
    val SELECTABLE_CLASSES: List<AspectClass> = listOf(AspectClass.NORTH, AspectClass.SOUTH, AspectClass.NEUTRAL)

    /** Whether a pixel lies on a `/` line (it rises to the right on the screen). */
    fun isNorthLine(globalX: Long, globalY: Long): Boolean =
        Math.floorMod(globalX + globalY, LINE_SPACING.toLong()) < LINE_WIDTH

    /** Whether a pixel lies on a `\` line (it falls to the right on the screen). */
    fun isSouthLine(globalX: Long, globalY: Long): Boolean =
        Math.floorMod(globalX - globalY, LINE_SPACING.toLong()) < LINE_WIDTH

    /** Whether a pixel is one of the dots of the east/west pattern. */
    fun isNeutralDot(globalX: Long, globalY: Long): Boolean =
        Math.floorMod(globalX, DOT_SPACING.toLong()) == 0L && Math.floorMod(globalY, DOT_SPACING.toLong()) == 0L

    /** The colour of a pixel of the given class, or transparent (0) outside its pattern. */
    fun colorAt(aspectClass: AspectClass, globalX: Long, globalY: Long, enabled: Set<AspectClass>): Int {
        if (aspectClass !in enabled) return 0
        return when (aspectClass) {
            AspectClass.NORTH -> if (isNorthLine(globalX, globalY)) NORTH_COLOR else 0
            AspectClass.SOUTH -> if (isSouthLine(globalX, globalY)) SOUTH_COLOR else 0
            AspectClass.NEUTRAL -> if (isNeutralDot(globalX, globalY)) NEUTRAL_COLOR else 0
            AspectClass.FLAT -> 0
        }
    }

    /**
     * Draws the patterns of a tile.
     *
     * @param classAt the class of a pixel of the tile given its column and row.
     * @param size the edge length of the tile in pixels.
     * @param tileX the column of the tile at its zoom level.
     * @param tileY the row of the tile at its zoom level.
     * @param enabled the classes to draw; the others stay transparent.
     * @return the ARGB colours of the `size × size` pixels, row by row.
     */
    fun render(
        classAt: (column: Int, row: Int) -> AspectClass,
        size: Int,
        tileX: Int,
        tileY: Int,
        enabled: Set<AspectClass>,
    ): IntArray {
        val pixels = IntArray(size * size)
        if (enabled.isEmpty()) return pixels
        val originX = tileX.toLong() * size
        val originY = tileY.toLong() * size
        for (row in 0 until size) {
            for (column in 0 until size) {
                pixels[row * size + column] = colorAt(classAt(column, row), originX + column, originY + row, enabled)
            }
        }
        return pixels
    }

    /** The short code of a set of classes, used in the tile URLs and the cache keys. */
    fun encode(classes: Set<AspectClass>): String =
        SELECTABLE_CLASSES.filter { it in classes }.joinToString("") { codeOf(it).toString() }

    /** The classes of a code made by [encode]; unknown letters are ignored. */
    fun decode(code: String?): Set<AspectClass> {
        if (code == null) return DEFAULT_CLASSES
        return SELECTABLE_CLASSES.filter { codeOf(it) in code }.toSet()
    }

    private fun codeOf(aspectClass: AspectClass): Char = when (aspectClass) {
        AspectClass.NORTH -> 'n'
        AspectClass.SOUTH -> 's'
        AspectClass.NEUTRAL -> 'e'
        AspectClass.FLAT -> 'f'
    }
}
