package com.mikotrack.maps

import com.mikotrack.data.db.OfflineAreaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the offline map packs that ship with the application and of the way the
 * offline areas are described.
 */
class OfflineMapPackTest {

    @Test
    fun `the bundled packs cover the whole of Catalonia`() {
        assertEquals(2, OfflineMapManager.BUNDLED_PACKS.size)

        val bounds = OfflineMapManager.BUNDLED_PACKS.map { it.bounds }
        bounds.forEach { box ->
            assertTrue("The western edge is wrong", box.minLongitude < 1.0)
            assertTrue("The eastern edge is wrong", box.maxLongitude > 3.0)
            assertTrue("The southern edge is wrong", box.minLatitude < 40.6)
            assertTrue("The northern edge is wrong", box.maxLatitude > 42.8)
        }
    }

    @Test
    fun `the bundled packs use the tile size of their source layer`() {
        val packs = OfflineMapManager.BUNDLED_PACKS.associateBy { it.layerId }

        val ortho = packs[MapLayer.ICGC_ORTHO.id]
        assertEquals(256, ortho!!.tileSize)
        assertEquals(MapLayer.ICGC_ORTHO.tileSize, ortho.tileSize)

        val topo = packs[MapLayer.ICGC_TOPO.id]
        assertEquals(512, topo!!.tileSize)
        assertEquals(MapLayer.ICGC_TOPO.tileSize, topo.tileSize)
    }

    @Test
    fun `the bundled packs use an asset path inside the assets folder`() {
        OfflineMapManager.BUNDLED_PACKS.forEach { pack ->
            assertTrue(pack.assetPath.startsWith("offline/"))
            assertTrue(pack.assetPath.endsWith(".mbtiles"))
            assertTrue(pack.fileName.endsWith(".mbtiles"))
            assertTrue(pack.minZoom <= pack.maxZoom)
            assertTrue(pack.tileCount > 0)
        }
    }

    @Test
    fun `the bundled packs have a unique name`() {
        val names = OfflineMapManager.BUNDLED_PACKS.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `an offline area carries the format and the tile size of its tiles`() {
        val area = OfflineAreaEntity(
            name = "Catalonia orthophotography",
            layerId = MapLayer.ICGC_ORTHO.id,
            filePath = "/data/offline/bundled_catalonia_ortho.mbtiles",
            minLatitude = 40.52,
            minLongitude = 0.15,
            maxLatitude = 42.86,
            maxLongitude = 3.33,
            minZoom = 10,
            maxZoom = 13,
            tileCount = 7049,
            sizeBytes = 143_257_600,
            createdAt = 0L,
            bundled = true,
            format = MbtilesStore.FORMAT_JPEG,
            tileSize = 256,
        )

        assertTrue(area.bundled)
        assertEquals(MbtilesStore.FORMAT_JPEG, area.format)
        assertEquals(256, area.tileSize)
    }

    @Test
    fun `an area that is not bundled can be deleted`() {
        val area = OfflineAreaEntity(
            name = "Downloaded area",
            layerId = MapLayer.OSM_STREET.id,
            filePath = "/data/offline/area_1.mbtiles",
            minLatitude = 41.0,
            minLongitude = 2.0,
            maxLatitude = 41.1,
            maxLongitude = 2.1,
            minZoom = 12,
            maxZoom = 14,
            tileCount = 100,
            sizeBytes = 1_000,
            createdAt = 1L,
        )

        assertFalse(area.bundled)
    }

    @Test
    fun `the mbtiles formats are the ones that maplibre can decode`() {
        assertEquals("jpg", MbtilesStore.FORMAT_JPEG)
        assertEquals("png", MbtilesStore.FORMAT_PNG)
    }
}
