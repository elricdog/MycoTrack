package com.mikotrack.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the textures of the orientation overlay. */
class AspectHatchTest {

    private val all = setOf(AspectClass.NORTH, AspectClass.SOUTH, AspectClass.NEUTRAL)

    @Test
    fun `north lines rise to the right and south lines fall to the right`() {
        // Moving one pixel right and one pixel up stays on the same "/" line.
        assertTrue(AspectHatch.isNorthLine(100, 100))
        assertTrue(AspectHatch.isNorthLine(101, 99))
        assertFalse(AspectHatch.isNorthLine(101, 101))
        // Moving one pixel right and one pixel down stays on the same "\" line.
        assertTrue(AspectHatch.isSouthLine(100, 100))
        assertTrue(AspectHatch.isSouthLine(101, 101))
        assertFalse(AspectHatch.isSouthLine(101, 99))
    }

    @Test
    fun `the lines cover the expected share of the pixels`() {
        val spacing = AspectHatch.LINE_SPACING
        var north = 0
        var south = 0
        for (y in 0L until spacing) for (x in 0L until spacing) {
            if (AspectHatch.isNorthLine(x, y)) north++
            if (AspectHatch.isSouthLine(x, y)) south++
        }
        assertEquals(AspectHatch.LINE_WIDTH * spacing, north)
        assertEquals(AspectHatch.LINE_WIDTH * spacing, south)
    }

    @Test
    fun `the pattern continues across tile borders`() {
        val size = 8
        val north = { _: Int, _: Int -> AspectClass.NORTH }
        val left = AspectHatch.render(north, size, tileX = 3, tileY = 5, enabled = all)
        val right = AspectHatch.render(north, size, tileX = 4, tileY = 5, enabled = all)
        val below = AspectHatch.render(north, size, tileX = 3, tileY = 6, enabled = all)
        for (row in 0 until size) {
            for (column in 0 until size) {
                val globalX = 3L * size + column
                val globalY = 5L * size + row
                val expected = if (AspectHatch.isNorthLine(globalX, globalY)) AspectHatch.NORTH_COLOR else 0
                assertEquals(expected, left[row * size + column])
                val expectedRight = if (AspectHatch.isNorthLine(globalX + size, globalY)) AspectHatch.NORTH_COLOR else 0
                assertEquals(expectedRight, right[row * size + column])
                val expectedBelow = if (AspectHatch.isNorthLine(globalX, globalY + size)) AspectHatch.NORTH_COLOR else 0
                assertEquals(expectedBelow, below[row * size + column])
            }
        }
    }

    @Test
    fun `negative and large global coordinates keep the period`() {
        assertEquals(AspectHatch.isNorthLine(-3, 0), AspectHatch.isNorthLine(-3L + AspectHatch.LINE_SPACING, 0))
        val far = (1L shl 26) - 1
        assertEquals(AspectHatch.isSouthLine(far, far - 4), AspectHatch.isSouthLine(far - AspectHatch.LINE_SPACING, far - 4))
    }

    @Test
    fun `only the enabled classes are drawn`() {
        val size = 16
        val south = { _: Int, _: Int -> AspectClass.SOUTH }
        val hidden = AspectHatch.render(south, size, 0, 0, setOf(AspectClass.NORTH))
        assertTrue(hidden.all { it == 0 })
        val shown = AspectHatch.render(south, size, 0, 0, setOf(AspectClass.SOUTH))
        assertTrue(shown.any { it == AspectHatch.SOUTH_COLOR })
        assertTrue(shown.all { it == 0 || it == AspectHatch.SOUTH_COLOR })
        val flat = AspectHatch.render({ _, _ -> AspectClass.FLAT }, size, 0, 0, all)
        assertTrue(flat.all { it == 0 })
    }

    @Test
    fun `the east and west dots are sparse`() {
        val size = 12
        val pixels = AspectHatch.render({ _, _ -> AspectClass.NEUTRAL }, size, 0, 0, all)
        val dots = pixels.count { it == AspectHatch.NEUTRAL_COLOR }
        assertEquals((size / AspectHatch.DOT_SPACING) * (size / AspectHatch.DOT_SPACING), dots)
    }

    @Test
    fun `class codes round trip and default to north and south`() {
        assertEquals("ns", AspectHatch.encode(setOf(AspectClass.SOUTH, AspectClass.NORTH)))
        assertEquals("nse", AspectHatch.encode(all))
        assertEquals("", AspectHatch.encode(emptySet()))
        assertEquals(all, AspectHatch.decode("nse"))
        assertEquals(setOf(AspectClass.SOUTH), AspectHatch.decode("s"))
        assertEquals(emptySet<AspectClass>(), AspectHatch.decode(""))
        assertEquals(AspectHatch.DEFAULT_CLASSES, AspectHatch.decode(null))
    }
}
