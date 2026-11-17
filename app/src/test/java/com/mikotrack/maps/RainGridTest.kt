package com.mikotrack.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Tests for the accumulated precipitation of the lattice. */
class RainGridTest {

    /**
     * A 3x2 lattice with a step of one degree, from latitude 40 to 41 and longitude 0
     * to 2, holding three days of data. Node `n` (row-major) accumulates `n` mm per day.
     */
    private fun grid() = RainGrid(
        lat0 = 40.0,
        lon0 = 0.0,
        stepDeg = 1.0,
        rows = 2,
        cols = 3,
        days = intArrayOf(10, 11, 12),
        millimeters = FloatArray(6 * 3) { (it / 3) % 6f },
    )

    @Test
    fun `window of one day sums only the latest day`() {
        val totals = grid().windowTotals(1)
        // The latest day is 12, the third of the three, so every node holds its own value.
        assertEquals(0f, totals[0], 0.001f)
        assertEquals(5f, totals[5], 0.001f)
    }

    @Test
    fun `window of two days sums the last two days`() {
        val totals = grid().windowTotals(2)
        // Node 3 accumulates 3 mm on each of the two days.
        assertEquals(6f, totals[3], 0.001f)
    }

    @Test
    fun `window wider than the data sums every day`() {
        val totals = grid().windowTotals(30)
        // Node 4 accumulates its value on each of the three days.
        assertEquals(12f, totals[4], 0.001f)
    }

    @Test
    fun `sample at a node returns that node`() {
        val grid = grid()
        val totals = grid.windowTotals(1)
        // The node at row 1, column 2 is latitude 41, longitude 2.
        val value = grid.sample(totals, 41.0, 2.0)
        assertNotNull(value)
        assertEquals(totals[5], value!!, 0.001f)
    }

    @Test
    fun `sample between two nodes interpolates`() {
        // A one-row, two-column lattice with 0 and 10 mm, sampled in the middle.
        val grid = RainGrid(40.0, 0.0, 1.0, 1, 2, intArrayOf(0), FloatArray(2))
        val value = grid.sample(floatArrayOf(0f, 10f), 40.0, 0.5)
        assertNotNull(value)
        assertEquals(5f, value!!, 0.001f)
    }

    @Test
    fun `sample outside the lattice is null`() {
        val grid = grid()
        val totals = grid.windowTotals(1)
        // Far to the north, beyond the half-cell margin.
        assertNull(grid.sample(totals, 45.0, 1.0))
    }

    @Test
    fun `sample inside the half-cell margin is not null`() {
        val grid = grid()
        val totals = grid.windowTotals(1)
        assertNotNull(grid.sample(totals, 40.0, -0.4))
    }

    @Test
    fun `empty lattice has no data and zero totals`() {
        val empty = RainGrid(40.0, 0.0, 1.0, 1, 1, intArrayOf(), FloatArray(0))
        assertTrue(!empty.hasData)
        assertEquals(0, empty.latestDay)
        // The totals are one zero per node, so the tiles stay empty.
        val totals = empty.windowTotals(7)
        assertEquals(1, totals.size)
        assertEquals(0f, totals[0], 0f)
    }

    @Test
    fun `renderTile paints the lattice and stays transparent outside`() {
        val grid = grid()
        val totals = grid.windowTotals(1)
        // At zoom 7 each tile spans 2.8 degrees; tile x=64 covers longitude 0 to 2.8 and
        // y=48 covers latitude 40 to 41, so the lattice fills the tile.
        val size = 8
        val pixels = RainGrid.renderTile(
            totals, 1, grid.lat0, grid.lon0, grid.stepDeg, grid.rows, grid.cols,
            zoom = 7, x = 64, y = 48, size = size,
        )
        assertEquals(size * size, pixels.size)
        // The northern nodes carry 3 to 5 mm, so at least one pixel is coloured.
        var painted = 0
        for (pixel in pixels) if (pixel != 0) painted++
        assertTrue("the lattice should paint at least one pixel", painted > 0)
        // The southern edge of the tile (latitude near 39, below the lattice) stays
        // transparent.
        assertTrue(pixels[(size - 1) * size] == 0)
    }

    @Test
    fun `mercator helpers round trip`() {
        val tiles = 1 shl 5
        // The centre of the world map is longitude 0, latitude 0.
        val lon = RainGrid.pixelToLongitude(tiles / 2.0, tiles)
        val lat = RainGrid.pixelToLatitude(tiles / 2.0, tiles)
        assertTrue(abs(lon) < 1e-9)
        assertTrue(abs(lat) < 1e-9)
    }
}
