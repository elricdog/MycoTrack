package com.mikotrack.maps

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the soil overlay: recolouring of the lithological map and the URLs. */
class SoilClassifierTest {

    private val palette = intArrayOf(0xFF0080, 0xD95900, 0xCC5900)
    private val colors = intArrayOf(0x7F00000A, 0x7F00000B, 0x7F00000C)

    private fun opaque(rgb: Int) = (0xFF shl 24) or rgb

    @Test
    fun `pixels match the closest palette colour within the tolerance`() {
        assertEquals(0, SoilClassifier.match(opaque(0xFE0181), palette))
        // Two close oranges of the service are still told apart.
        assertEquals(1, SoilClassifier.match(opaque(0xD85900), palette))
        assertEquals(2, SoilClassifier.match(opaque(0xCD5A01), palette))
        assertEquals(-1, SoilClassifier.match(opaque(0x808080), palette))
    }

    @Test
    fun `hidden lithologies and transparent pixels are left transparent`() {
        val pixels = intArrayOf(opaque(0xFF0080), opaque(0xD95900), 0x00FF0080, opaque(0xCC5900))
        val visible = (1L shl 0) or (1L shl 2)
        val output = SoilClassifier.recolor(pixels, 2, palette, colors, visible)
        assertArrayEquals(intArrayOf(colors[0], 0, 0, colors[2]), output)
    }

    @Test
    fun `blended border pixels take the class of their left neighbour`() {
        val blended = opaque(0x80406F)
        val pixels = intArrayOf(opaque(0xCC5900), blended)
        val output = SoilClassifier.recolor(pixels, 2, palette, colors, SoilClassifier.allVisible(3))
        assertArrayEquals(intArrayOf(colors[2], colors[2]), output)
    }

    @Test
    fun `masks round trip through their code`() {
        val mask = HabitatSources.soilMask(setOf("gneiss"))
        val all = SoilClassifier.allVisible(HabitatSources.soilLegend.size)
        assertEquals(all, HabitatSources.soilMask(emptySet()))
        assertEquals(all and (1L shl 3).inv(), mask)
        assertEquals(mask, SoilClassifier.decodeMask(SoilClassifier.encodeMask(mask)))
        assertNull(SoilClassifier.decodeMask("zz"))
    }

    @Test
    fun `tile bounds follow the web mercator grid`() {
        val world = SoilClassifier.tileBounds(0, 0, 0)
        assertEquals(-20_037_508.34, world[0], 0.01)
        assertEquals(20_037_508.34, world[3], 0.01)
        val tile = SoilClassifier.tileBounds(1, 1, 0)
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 20_037_508.34, 20_037_508.34), tile, 0.01)
    }

    @Test
    fun `the soil palette has distinct colours`() {
        val colors = HabitatSources.soilLegend.map { it.sourceColor }
        assertEquals(colors.size, colors.toSet().size)
        assertEquals(HabitatSources.soilLegend.size, HabitatSources.soilLegend.map { it.key }.toSet().size)
        // Every colour of the palette must match itself and nothing else.
        val palette = colors.toIntArray()
        colors.forEachIndexed { index, rgb -> assertEquals(index, SoilClassifier.match(opaque(rgb), palette)) }
    }

    @Test
    fun `the tree url filters the selected formations only`() {
        assertEquals(HabitatSources.TREES_WMS_URL, HabitatSources.treesUrl(emptySet()))
        assertNull(HabitatSources.treesUrl(HabitatSources.treeLegend.map { it.key }.toSet()))
        val onlyBeechAndHolmOak = HabitatSources.treeLegend.map { it.key }.toSet() - setOf("beech", "holm_oak")
        val url = HabitatSources.treesUrl(onlyBeechAndHolmOak)!!
        assertTrue(url.startsWith(HabitatSources.TREES_WMS_URL))
        assertTrue(url.endsWith("&CQL_FILTER=id_supraforarb%20IN%20(1,18)"))
    }

    @Test
    fun `the habitat urls carry the selected classes`() {
        val layers = HabitatLayers(
            aspect = true,
            soil = true,
            aspectClasses = setOf(AspectClass.NORTH),
            aspectUrl = "http://127.0.0.1:1/aspect/{z}/{x}/{y}.png",
            soilUrl = "http://127.0.0.1:1/soil/{z}/{x}/{y}.png",
        )
        assertEquals("http://127.0.0.1:1/aspect/{z}/{x}/{y}.png?c=n", layers.aspectTileUrl)
        val all = SoilClassifier.encodeMask(SoilClassifier.allVisible(HabitatSources.soilLegend.size))
        assertEquals("http://127.0.0.1:1/soil/{z}/{x}/{y}.png?m=$all", layers.soilTileUrl)
        assertNull(layers.copy(aspectClasses = emptySet()).aspectTileUrl)
        assertNull(layers.copy(hiddenSoil = HabitatSources.soilLegend.map { it.key }.toSet()).soilTileUrl)
        assertNull(layers.treesTileUrl)
    }
}
