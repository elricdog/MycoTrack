package com.mikotrack.maps

import com.mikotrack.ui.mushrooms.HabitatPreference
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sinh

/**
 * Pure functions of the "likely areas" (probability) overlay: they combine, pixel by
 * pixel, the tree formation, the substrate group and the orientation of the terrain with
 * the habitat affinities of a species ([HabitatPreference]) into a score from 0 to 1,
 * and draw it as a transparent colour ramp inside a circle around the user.
 *
 * The score of a pixel is the weighted mean of the affinities of the factors the
 * species depends on (trees [TREES_WEIGHT], soil [SOIL_WEIGHT], orientation
 * [ASPECT_WEIGHT]; a factor whose affinity map is empty is left out and the other
 * weights are renormalised):
 * - a class the species does not list, or an input that is unknown (its source could
 *   not be downloaded, or the pixel is outside the source map), counts as [NEUTRAL], so
 *   the overlay still works, only less sharply, when a source fails;
 * - outside the forest the tree affinity is the one of the [NO_FOREST_KEY] entry, or 0
 *   for the forest species that do not list it;
 * - a known tree affinity of 0 (no host tree) makes the whole score 0, because a
 *   mycorrhizal species does not fruit there however good the soil or the slope are;
 * - flat terrain takes the affinity of the east/west (`e`) orientation, the
 *   intermediate exposure.
 */
object SuitabilityScorer {

    const val TREES_WEIGHT = 0.5f
    const val SOIL_WEIGHT = 0.25f
    const val ASPECT_WEIGHT = 0.25f

    /** The affinity of a class that is unknown or not listed by the species. */
    const val NEUTRAL = 0.5f

    /** The optional key of [HabitatPreference.trees] for the land without forest. */
    const val NO_FOREST_KEY = "none"

    /** A pixel without forest (transparent in the forest map). */
    const val TREE_NONE = -1

    /** A pixel whose forest formation is unknown. */
    const val TREE_UNKNOWN = -2

    /** A pixel whose substrate group or orientation is unknown. */
    const val UNKNOWN = -1

    /** Scores below this are not drawn. */
    const val MIN_VISIBLE_SCORE = 0.4f

    /** The colours of the ramp at [MIN_VISIBLE_SCORE], [MIDDLE_SCORE] and 1. */
    const val LOW_COLOR = 0xFFFFE03B.toInt()
    const val MIDDLE_COLOR = 0xFFFF2E9A.toInt()
    const val HIGH_COLOR = 0xFF7B1FA2.toInt()
    const val MIDDLE_SCORE = 0.7f

    /** The opacity of the ramp at [MIN_VISIBLE_SCORE] and at 1. */
    const val MIN_ALPHA = 90
    const val MAX_ALPHA = 200

    /** The colour of the dashed border of the search circle. */
    const val BORDER_COLOR = 0xE04A148C.toInt()

    private const val BORDER_WIDTH_PX = 2.5
    private const val DASH_PERIOD_PX = 18.0
    private const val DASH_ON_PX = 11.0

    /** Pixels more transparent than this are outside the forest map. */
    private const val MIN_SOURCE_ALPHA = 128

    private const val EARTH_RADIUS = 6_371_008.8

    /** The affinities of a species, looked up by the indices of the classified tiles. */
    class Model(
        preference: HabitatPreference,
        treeKeys: List<String> = HabitatSources.treeLegend.map { it.key },
    ) {
        val usesTrees = preference.trees.isNotEmpty()
        val usesSoil = preference.soil.isNotEmpty()
        val usesAspect = preference.aspect.isNotEmpty()

        private val trees = FloatArray(treeKeys.size) { affinity(preference.trees[treeKeys[it]]) }
        private val noForest = preference.trees[NO_FOREST_KEY]?.coerceIn(0f, 1f) ?: 0f
        private val soil = FloatArray(SoilGroup.entries.size) {
            affinity(preference.soil[SoilGroup.entries[it].name.lowercase()])
        }
        private val aspect = FloatArray(AspectClass.entries.size) {
            val key = when (AspectClass.entries[it]) {
                AspectClass.NORTH -> "n"
                AspectClass.SOUTH -> "s"
                AspectClass.NEUTRAL, AspectClass.FLAT -> "e"
            }
            affinity(preference.aspect[key])
        }

        fun treeAffinity(tree: Int): Float = when (tree) {
            TREE_NONE -> noForest
            in trees.indices -> trees[tree]
            else -> NEUTRAL
        }

        fun soilAffinity(group: Int): Float = if (group in soil.indices) soil[group] else NEUTRAL

        fun aspectAffinity(aspectClass: Int): Float = if (aspectClass in aspect.indices) aspect[aspectClass] else NEUTRAL

        /**
         * The score of a pixel.
         *
         * @param tree the index of the formation in the tree legend, [TREE_NONE] or [TREE_UNKNOWN].
         * @param soilGroup the [SoilGroup] ordinal or [UNKNOWN].
         * @param aspectClass the [AspectClass] ordinal or [UNKNOWN].
         */
        fun score(tree: Int, soilGroup: Int, aspectClass: Int): Float {
            var sum = 0f
            var weight = 0f
            if (usesTrees) {
                val value = treeAffinity(tree)
                if (tree != TREE_UNKNOWN && value <= 0f) return 0f
                sum += TREES_WEIGHT * value
                weight += TREES_WEIGHT
            }
            if (usesSoil) {
                sum += SOIL_WEIGHT * soilAffinity(soilGroup)
                weight += SOIL_WEIGHT
            }
            if (usesAspect) {
                sum += ASPECT_WEIGHT * aspectAffinity(aspectClass)
                weight += ASPECT_WEIGHT
            }
            return if (weight == 0f) NEUTRAL else (sum / weight).coerceIn(0f, 1f)
        }

        private fun affinity(value: Float?): Float = value?.coerceIn(0f, 1f) ?: NEUTRAL
    }

    /**
     * The scores of a `gridSize × gridSize` grid over a tile of `size × size` pixels,
     * sampling the inputs at the centre of every cell. A `null` input is unknown.
     *
     * @param trees the formation of every pixel (see [classifyTrees]).
     * @param lithologies the index in the soil legend of every pixel, or -1.
     * @param lithologyGroups the [SoilGroup] ordinal of every lithology.
     * @param aspect the [AspectClass] ordinal of every pixel.
     */
    fun scoreGrid(
        model: Model,
        gridSize: Int,
        size: Int,
        trees: IntArray?,
        lithologies: IntArray?,
        lithologyGroups: IntArray,
        aspect: ByteArray?,
    ): FloatArray {
        val step = size / gridSize
        return FloatArray(gridSize * gridSize) { cell ->
            val row = (cell / gridSize) * step + step / 2
            val column = (cell % gridSize) * step + step / 2
            val pixel = row * size + column
            val tree = trees?.get(pixel) ?: TREE_UNKNOWN
            val lithology = lithologies?.get(pixel) ?: -1
            val group = if (lithology in lithologyGroups.indices) lithologyGroups[lithology] else UNKNOWN
            val aspectClass = aspect?.get(pixel)?.toInt() ?: UNKNOWN
            model.score(tree, group, aspectClass)
        }
    }

    /**
     * The forest formation of every pixel of a tile of the forest map: the index of the
     * closest [palette] colour within the tolerance of [SoilClassifier.match],
     * [TREE_NONE] for the transparent pixels and [TREE_UNKNOWN] for the colours that are
     * not in the palette. A blended border pixel takes the class of a neighbour.
     */
    fun classifyTrees(pixels: IntArray, width: Int, palette: IntArray): IntArray {
        val classes = IntArray(pixels.size) { TREE_NONE }
        for (index in pixels.indices) {
            val argb = pixels[index]
            if ((argb ushr 24) < MIN_SOURCE_ALPHA) continue
            var tree = SoilClassifier.match(argb, palette)
            if (tree < 0) {
                val column = index % width
                tree = when {
                    column > 0 && classes[index - 1] >= 0 -> classes[index - 1]
                    index >= width && classes[index - width] >= 0 -> classes[index - width]
                    else -> TREE_UNKNOWN
                }
            }
            classes[index] = tree
        }
        return classes
    }

    /**
     * The part of a `size × size` grid covered by one of its descendant tiles, `depth`
     * zoom levels deeper, scaled up to `size × size`.
     *
     * @param subX the column of the descendant inside the grid, from 0 to `2^depth - 1`.
     * @param subY the row of the descendant inside the grid.
     */
    fun zoomIn(grid: IntArray, size: Int, depth: Int, subX: Int, subY: Int): IntArray {
        val offsetX = subX * size
        val offsetY = subY * size
        return IntArray(size * size) { index ->
            val column = (offsetX + index % size) shr depth
            val row = (offsetY + index / size) shr depth
            grid[row * size + column]
        }
    }

    /** The ARGB colour (not premultiplied) of a score; transparent below [MIN_VISIBLE_SCORE]. */
    fun color(score: Float): Int {
        if (score.isNaN() || score < MIN_VISIBLE_SCORE) return 0
        val value = score.coerceAtMost(1f)
        val rgb = if (value <= MIDDLE_SCORE) {
            mix(LOW_COLOR, MIDDLE_COLOR, (value - MIN_VISIBLE_SCORE) / (MIDDLE_SCORE - MIN_VISIBLE_SCORE))
        } else {
            mix(MIDDLE_COLOR, HIGH_COLOR, (value - MIDDLE_SCORE) / (1f - MIDDLE_SCORE))
        }
        val fraction = (value - MIN_VISIBLE_SCORE) / (1f - MIN_VISIBLE_SCORE)
        val alpha = (MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * fraction).roundToInt()
        return (alpha shl 24) or (rgb and 0xFFFFFF)
    }

    /**
     * The distance in metres between two points. The equirectangular approximation is
     * enough (and much faster than the haversine formula) within the radius of a search.
     */
    fun distanceMeters(latitude1: Double, longitude1: Double, latitude2: Double, longitude2: Double): Double {
        val meanLatitude = Math.toRadians((latitude1 + latitude2) / 2)
        val dx = Math.toRadians(longitude2 - longitude1) * cos(meanLatitude)
        val dy = Math.toRadians(latitude2 - latitude1)
        return EARTH_RADIUS * hypot(dx, dy)
    }

    /** The latitude of a vertical position of a Web Mercator tile, in pixels. */
    fun latitudeAt(zoom: Int, tileY: Int, pixelY: Double, size: Int): Double {
        val mercatorY = (tileY + pixelY / size) / (1 shl zoom)
        return Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * mercatorY))))
    }

    /** The longitude of a horizontal position of a Web Mercator tile, in pixels. */
    fun longitudeAt(zoom: Int, tileX: Int, pixelX: Double, size: Int): Double =
        (tileX + pixelX / size) / (1 shl zoom) * 360.0 - 180.0

    /**
     * The south-west and north-east corners (latitude, longitude) of the box that
     * contains the circle of [radiusMeters] around a point.
     */
    fun circleBounds(latitude: Double, longitude: Double, radiusMeters: Double): Pair<GeoPoint, GeoPoint> {
        val dLatitude = Math.toDegrees(radiusMeters / EARTH_RADIUS)
        val dLongitude = dLatitude / cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        return GeoPoint(latitude - dLatitude, longitude - dLongitude) to
            GeoPoint(latitude + dLatitude, longitude + dLongitude)
    }

    /** Whether any part of a tile is within [radiusMeters] of a point. */
    fun tileIntersectsCircle(
        zoom: Int,
        tileX: Int,
        tileY: Int,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Boolean {
        val north = latitudeAt(zoom, tileY, 0.0, 1)
        val south = latitudeAt(zoom, tileY, 1.0, 1)
        val west = longitudeAt(zoom, tileX, 0.0, 1)
        val east = longitudeAt(zoom, tileX, 1.0, 1)
        val nearestLatitude = latitude.coerceIn(south, north)
        val nearestLongitude = longitude.coerceIn(west, east)
        return distanceMeters(latitude, longitude, nearestLatitude, nearestLongitude) <= radiusMeters
    }

    /**
     * Draws a tile of the overlay: the scores of a coarser grid, interpolated
     * bilinearly, coloured with [color] inside the circle and a dashed border along it.
     *
     * @return the ARGB pixels (not premultiplied) of the `size × size` tile.
     */
    fun render(
        scores: FloatArray,
        gridSize: Int,
        size: Int,
        zoom: Int,
        tileX: Int,
        tileY: Int,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): IntArray {
        require(scores.size == gridSize * gridSize) { "The grid must be square" }
        val latitudes = DoubleArray(size) { latitudeAt(zoom, tileY, it + 0.5, size) }
        val longitudes = DoubleArray(size) { longitudeAt(zoom, tileX, it + 0.5, size) }
        val cosLatitude = cos(Math.toRadians(latitude))
        val scale = gridSize.toDouble() / size
        val output = IntArray(size * size)
        for (row in 0 until size) {
            val dy = EARTH_RADIUS * Math.toRadians(latitudes[row] - latitude)
            val pixelMeters = AspectClassifier.pixelSizeMeters(zoom, tileY, row, size)
            val borderWidth = BORDER_WIDTH_PX * pixelMeters
            val gridY = ((row + 0.5) * scale - 0.5).coerceIn(0.0, gridSize - 1.0)
            for (column in 0 until size) {
                val dx = EARTH_RADIUS * Math.toRadians(longitudes[column] - longitude) * cosLatitude
                val distance = hypot(dx, dy)
                if (distance > radiusMeters) continue
                if (distance >= radiusMeters - borderWidth) {
                    val arc = (atan2(dx, dy) + PI) * radiusMeters
                    val period = DASH_PERIOD_PX * pixelMeters
                    if (arc - floor(arc / period) * period < DASH_ON_PX * pixelMeters) {
                        output[row * size + column] = BORDER_COLOR
                        continue
                    }
                }
                val gridX = ((column + 0.5) * scale - 0.5).coerceIn(0.0, gridSize - 1.0)
                output[row * size + column] = color(bilinear(scores, gridSize, gridX, gridY))
            }
        }
        return output
    }

    private fun bilinear(grid: FloatArray, gridSize: Int, x: Double, y: Double): Float {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(gridSize - 1)
        val y1 = (y0 + 1).coerceAtMost(gridSize - 1)
        val fx = (x - x0).toFloat()
        val fy = (y - y0).toFloat()
        val top = grid[y0 * gridSize + x0] * (1 - fx) + grid[y0 * gridSize + x1] * fx
        val bottom = grid[y1 * gridSize + x0] * (1 - fx) + grid[y1 * gridSize + x1] * fx
        return top * (1 - fy) + bottom * fy
    }

    private fun mix(from: Int, to: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).roundToInt() and 0xFF
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
