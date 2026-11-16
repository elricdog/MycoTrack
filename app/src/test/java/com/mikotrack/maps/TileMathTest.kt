package com.mikotrack.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the tile maths and of the map layer catalogue. */
class TileMathTest {

    @Test
    fun `a point belongs to the tile that is returned for it`() {
        val zoom = 13
        val latitude = 41.3888
        val longitude = 2.159
        val x = TileMath.lonToTileX(longitude, zoom)
        val y = TileMath.latToTileY(latitude, zoom)

        // The tile must contain the point.
        assertTrue(longitude >= TileMath.tileXToLon(x, zoom))
        assertTrue(longitude <= TileMath.tileXToLon(x + 1, zoom))
        assertTrue(latitude <= TileMath.tileYToLat(y, zoom))
        assertTrue(latitude >= TileMath.tileYToLat(y + 1, zoom))
    }

    @Test
    fun `tile indices stay inside the grid`() {
        val zoom = 5
        val limit = 1 shl zoom
        assertTrue(TileMath.lonToTileX(-180.0, zoom) >= 0)
        assertTrue(TileMath.lonToTileX(180.0, zoom) <= limit - 1)
        assertTrue(TileMath.latToTileY(85.0, zoom) >= 0)
        assertTrue(TileMath.latToTileY(-85.0, zoom) <= limit - 1)
    }

    @Test
    fun `coordinates are converted back into the original position`() {
        val zoom = 14
        val x = TileMath.lonToTileX(2.159, zoom)
        val y = TileMath.latToTileY(41.3888, zoom)
        assertEquals(2.159, TileMath.tileXToLon(x, zoom), 0.01)
        assertEquals(41.3888, TileMath.tileYToLat(y, zoom), 0.01)
    }

    @Test
    fun `the tms row is the mirrored slippy row`() {
        assertEquals(7, TileMath.toTmsRow(3, 0))
        assertEquals(0, TileMath.fromTmsRow(3, 7))
    }

    @Test
    fun `the number of tiles of a bounding box is computed`() {
        val count = TileMath.tileCount(41.38, 2.15, 41.40, 2.17, 14)
        assertTrue("Unexpected tile count: $count", count in 1..10_000)
    }

    @Test
    fun `every layer exposes a tile template except the offline one`() {
        MapLayer.entries.forEach { layer ->
            if (layer == MapLayer.OFFLINE) {
                assertNull(layer.tileUrlTemplate)
            } else {
                assertTrue(layer.tileUrlTemplate!!.contains("{z}"))
                assertTrue(layer.tileUrlTemplate!!.contains("{x}"))
                assertTrue(layer.tileUrlTemplate!!.contains("{y}"))
            }
        }
    }

    @Test
    fun `layers can be resolved by their identifier`() {
        assertEquals(MapLayer.ICGC_ORTHO, MapLayer.fromId("ICGC_ORTHO"))
        assertNull(MapLayer.fromId("UNKNOWN"))
    }
}
