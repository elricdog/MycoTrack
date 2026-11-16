package com.mikotrack.maps

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests of the slope orientation classification used by the habitat overlay. */
class AspectClassifierTest {

    @Test
    fun `a slope that goes down to the north faces north`() {
        // The terrain rises towards the south, so it goes down towards the north.
        assertEquals(0.0, AspectClassifier.aspectDegrees(dzdxEast = 0.0, dzdyNorth = -0.5), 1e-9)
        assertEquals(AspectClass.NORTH, AspectClassifier.classify(dzdxEast = 0.0, dzdyNorth = -0.5))
    }

    @Test
    fun `the compass directions of the downslope are reported clockwise from north`() {
        assertEquals(90.0, AspectClassifier.aspectDegrees(dzdxEast = -0.5, dzdyNorth = 0.0), 1e-9)
        assertEquals(180.0, AspectClassifier.aspectDegrees(dzdxEast = 0.0, dzdyNorth = 0.5), 1e-9)
        assertEquals(270.0, AspectClassifier.aspectDegrees(dzdxEast = 0.5, dzdyNorth = 0.0), 1e-9)
        assertEquals(AspectClass.SOUTH, AspectClassifier.classify(dzdxEast = 0.0, dzdyNorth = 0.5))
        assertEquals(AspectClass.NEUTRAL, AspectClassifier.classify(dzdxEast = -0.5, dzdyNorth = 0.0))
        assertEquals(AspectClass.NEUTRAL, AspectClassifier.classify(dzdxEast = 0.5, dzdyNorth = 0.0))
    }

    @Test
    fun `the sectors are 90 degrees wide and centred on north and south`() {
        assertEquals(AspectClass.NORTH, AspectClassifier.classifyAspect(315.0))
        assertEquals(AspectClass.NORTH, AspectClassifier.classifyAspect(44.9))
        assertEquals(AspectClass.NEUTRAL, AspectClassifier.classifyAspect(45.0))
        assertEquals(AspectClass.NEUTRAL, AspectClassifier.classifyAspect(134.9))
        assertEquals(AspectClass.SOUTH, AspectClassifier.classifyAspect(135.0))
        assertEquals(AspectClass.SOUTH, AspectClassifier.classifyAspect(224.9))
        assertEquals(AspectClass.NEUTRAL, AspectClassifier.classifyAspect(225.0))
        assertEquals(AspectClass.NEUTRAL, AspectClassifier.classifyAspect(314.9))
        assertEquals(AspectClass.NORTH, AspectClassifier.classifyAspect(-10.0))
    }

    @Test
    fun `nearly flat terrain is left transparent`() {
        // A 2 % gradient is about 1.1 degrees.
        assertEquals(AspectClass.FLAT, AspectClassifier.classify(dzdxEast = 0.0, dzdyNorth = -0.02))
        assertEquals(AspectClass.FLAT, AspectClassifier.classify(0.0, 0.0))
        assertEquals(10.0, AspectClassifier.slopeDegrees(0.0, Math.tan(Math.toRadians(10.0))), 1e-9)
    }

    @Test
    fun `a tile of a plane rising to the south is classified as a north slope`() {
        val size = 4
        val stride = size + 2
        // Rows run from north to south; every row is 1 m higher than the previous one,
        // with 5 m pixels that is a slope of about 11 degrees.
        val grid = FloatArray(stride * stride) { index -> (index / stride).toFloat() }
        val classes = AspectClassifier.classifyTile(grid, size) { 5.0 }
        assertArrayEquals(ByteArray(size * size) { AspectClass.NORTH.ordinal.toByte() }, classes)

        val southFacing = FloatArray(stride * stride) { index -> -(index / stride).toFloat() }
        val southClasses = AspectClassifier.classifyTile(southFacing, size) { 5.0 }
        assertArrayEquals(ByteArray(size * size) { AspectClass.SOUTH.ordinal.toByte() }, southClasses)

        val flat = AspectClassifier.classifyTile(FloatArray(stride * stride), size) { 5.0 }
        assertArrayEquals(ByteArray(size * size) { AspectClass.FLAT.ordinal.toByte() }, flat)
    }

    @Test
    fun `terrarium pixels are decoded into metres`() {
        // 32768 + 1000.5 m = 33768.5 -> R 131, G 232, B 128.
        val argb = (0xFF shl 24) or (131 shl 16) or (232 shl 8) or 128
        assertEquals(1000.5f, AspectClassifier.terrariumElevation(argb), 1e-3f)
    }
}
