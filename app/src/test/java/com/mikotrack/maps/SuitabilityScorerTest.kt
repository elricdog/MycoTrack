package com.mikotrack.maps

import com.mikotrack.ui.mushrooms.HabitatPreference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the likely areas overlay: scores, unknown inputs, circle and colour ramp. */
class SuitabilityScorerTest {

    private val treeKeys = listOf("scots_pine", "holm_oak", "beech")
    private val pine = 0
    private val oak = 1
    private val beech = 2
    private val acid = SoilGroup.ACID.ordinal
    private val basic = SoilGroup.BASIC.ordinal
    private val north = AspectClass.NORTH.ordinal
    private val south = AspectClass.SOUTH.ordinal
    private val flat = AspectClass.FLAT.ordinal

    private val pineSpecies = SuitabilityScorer.Model(
        HabitatPreference(
            trees = mapOf("scots_pine" to 1f, "holm_oak" to 0f),
            soil = mapOf("acid" to 1f, "basic" to 0.2f),
            aspect = mapOf("n" to 1f, "s" to 0.2f, "e" to 0.6f),
        ),
        treeKeys,
    )

    @Test
    fun `the score is the weighted mean of the affinities`() {
        assertEquals(1f, pineSpecies.score(pine, acid, north), 1e-6f)
        // 0.5 * 1 + 0.25 * 0.2 + 0.25 * 0.2
        assertEquals(0.6f, pineSpecies.score(pine, basic, south), 1e-6f)
        // Flat terrain takes the east/west affinity.
        assertEquals(0.5f * 1f + 0.25f * 1f + 0.25f * 0.6f, pineSpecies.score(pine, acid, flat), 1e-6f)
    }

    @Test
    fun `no host tree or no forest gives zero for a forest species`() {
        assertEquals(0f, pineSpecies.score(oak, acid, north), 0f)
        assertEquals(0f, pineSpecies.score(SuitabilityScorer.TREE_NONE, acid, north), 0f)
    }

    @Test
    fun `a species can accept the land without forest`() {
        val meadow = SuitabilityScorer.Model(
            HabitatPreference(trees = mapOf("none" to 1f, "beech" to 0.4f)),
            treeKeys,
        )
        assertEquals(1f, meadow.score(SuitabilityScorer.TREE_NONE, acid, north), 1e-6f)
        assertEquals(0.4f, meadow.score(beech, acid, north), 1e-6f)
    }

    @Test
    fun `unknown inputs and unlisted classes count as neutral`() {
        // 0.5 * 0.5 (unknown trees) + 0.25 * 0.5 (unknown soil) + 0.25 * 1
        assertEquals(
            0.625f,
            pineSpecies.score(SuitabilityScorer.TREE_UNKNOWN, SuitabilityScorer.UNKNOWN, north),
            1e-6f,
        )
        // Beech is not listed by the species: neutral, not zero.
        assertEquals(0.5f * 0.5f + 0.25f + 0.25f, pineSpecies.score(beech, acid, north), 1e-6f)
        // Every input unknown.
        assertEquals(
            0.5f,
            pineSpecies.score(SuitabilityScorer.TREE_UNKNOWN, SuitabilityScorer.UNKNOWN, SuitabilityScorer.UNKNOWN),
            1e-6f,
        )
    }

    @Test
    fun `factors the species does not depend on are left out`() {
        val soilOnly = SuitabilityScorer.Model(HabitatPreference(soil = mapOf("basic" to 0.9f)), treeKeys)
        assertEquals(0.9f, soilOnly.score(SuitabilityScorer.TREE_NONE, basic, south), 1e-6f)
        val nothing = SuitabilityScorer.Model(HabitatPreference(), treeKeys)
        assertEquals(SuitabilityScorer.NEUTRAL, nothing.score(pine, acid, north), 0f)
    }

    @Test
    fun `missing inputs are unknown in the grid`() {
        val size = 4
        val trees = IntArray(size * size) { pine }
        val grid = SuitabilityScorer.scoreGrid(pineSpecies, 2, size, trees, null, intArrayOf(acid), null)
        // Trees 1, soil and orientation unknown: 0.5 * 1 + 0.25 * 0.5 + 0.25 * 0.5.
        grid.forEach { assertEquals(0.75f, it, 1e-6f) }
    }

    @Test
    fun `forest pixels are matched to the legend colours`() {
        val palette = intArrayOf(0x00A639, 0x897044)
        val pixels = intArrayOf(
            0x00000000, 0xFF808080.toInt(), 0xFF00A63A.toInt(),
            0xFF897044.toInt(), 0xFF123456.toInt(), 0x00000000,
        )
        val classes = SuitabilityScorer.classifyTrees(pixels, 3, palette)
        // An unknown colour takes the class of the pixel on its left, if it has one.
        assertArrayEquals(
            intArrayOf(SuitabilityScorer.TREE_NONE, SuitabilityScorer.TREE_UNKNOWN, 0, 1, 1, SuitabilityScorer.TREE_NONE),
            classes,
        )
    }

    @Test
    fun `descendant tiles scale up their part of the grid`() {
        val grid = intArrayOf(1, 2, 3, 4)
        assertArrayEquals(intArrayOf(2, 2, 2, 2), SuitabilityScorer.zoomIn(grid, 2, 1, 1, 0))
        assertArrayEquals(intArrayOf(3, 3, 3, 3), SuitabilityScorer.zoomIn(grid, 2, 1, 0, 1))
    }

    @Test
    fun `the colour ramp starts at the visibility threshold`() {
        assertEquals(0, SuitabilityScorer.color(0f))
        assertEquals(0, SuitabilityScorer.color(SuitabilityScorer.MIN_VISIBLE_SCORE - 0.01f))
        assertEquals(0, SuitabilityScorer.color(Float.NaN))

        val low = SuitabilityScorer.color(SuitabilityScorer.MIN_VISIBLE_SCORE)
        val middle = SuitabilityScorer.color(SuitabilityScorer.MIDDLE_SCORE)
        val high = SuitabilityScorer.color(1f)
        assertEquals(SuitabilityScorer.LOW_COLOR and 0xFFFFFF, low and 0xFFFFFF)
        assertEquals(SuitabilityScorer.MIDDLE_COLOR and 0xFFFFFF, middle and 0xFFFFFF)
        assertEquals(SuitabilityScorer.HIGH_COLOR and 0xFFFFFF, high and 0xFFFFFF)
        assertEquals(SuitabilityScorer.MIN_ALPHA, low ushr 24)
        assertEquals(SuitabilityScorer.MAX_ALPHA, high ushr 24)
        assertTrue((middle ushr 24) in (low ushr 24)..(high ushr 24))
    }

    @Test
    fun `only the pixels inside the circle are drawn`() {
        // Barcelona, zoom 14: the tile that contains the centre of the circle.
        val latitude = 41.3874
        val longitude = 2.1686
        val zoom = 14
        val tiles = 1 shl zoom
        val x = ((longitude + 180) / 360 * tiles).toInt()
        val latRad = Math.toRadians(latitude)
        val y = ((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * tiles).toInt()
        val size = 64
        val scores = FloatArray(4) { 1f }
        val pixels = SuitabilityScorer.render(scores, 2, size, zoom, x, y, latitude, longitude, 500.0)

        val centerColumn = ((longitude + 180) / 360 * tiles - x) * size
        val centerRow = ((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * tiles - y) * size
        val inside = pixels[centerRow.toInt() * size + centerColumn.toInt()]
        assertEquals(SuitabilityScorer.color(1f), inside)
        // A tile at zoom 14 is about 1.8 km wide: its corners are beyond 500 m from the centre
        // unless the centre is close to them, so at least one corner is transparent.
        val corners = listOf(0, size - 1, size * (size - 1), size * size - 1).map { pixels[it] }
        assertTrue(corners.any { it == 0 })
        // The border of the circle is drawn somewhere.
        assertTrue(pixels.any { it == SuitabilityScorer.BORDER_COLOR })
    }

    @Test
    fun `tiles far from the circle are skipped`() {
        assertTrue(SuitabilityScorer.tileIntersectsCircle(10, 518, 382, 41.39, 2.17, 2000.0))
        assertFalse(SuitabilityScorer.tileIntersectsCircle(10, 600, 382, 41.39, 2.17, 2000.0))
    }

    @Test
    fun `the circle bounds contain the radius`() {
        val (southWest, northEast) = SuitabilityScorer.circleBounds(41.39, 2.17, 2000.0)
        assertEquals(2000.0, SuitabilityScorer.distanceMeters(41.39, 2.17, northEast.latitude, 2.17), 1.0)
        assertEquals(2000.0, SuitabilityScorer.distanceMeters(41.39, 2.17, 41.39, southWest.longitude), 1.0)
    }

    @Test
    fun `searches round-trip through the tile query`() {
        val search = SuitabilitySearch("cep", "Cep", 41.123456, 2.654321, 5000)
        val parsed = SuitabilitySearch.fromQuery(search.toQuery())
        assertNotNull(parsed)
        assertEquals("cep", parsed!!.speciesId)
        assertEquals(41.12346, parsed.latitude, 1e-9)
        assertEquals(5000, parsed.radiusMeters)
        assertNull(SuitabilitySearch.fromQuery("s=cep&la=41&lo=2"))
        assertNull(SuitabilitySearch.fromQuery("s=cep&la=41&lo=2&r=999999"))
    }
}
