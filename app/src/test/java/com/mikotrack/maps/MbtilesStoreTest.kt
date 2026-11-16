package com.mikotrack.maps

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests of the MBTiles reader and writer.
 *
 * The store has to keep the metadata that describes how the tiles have to be rendered,
 * because the packs that ship with the application use two different tile sizes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.mikotrack.TestApplication::class)
class MbtilesStoreTest {

    private lateinit var file: File
    private lateinit var store: MbtilesStore

    private val bounds = BoundingBox(
        minLatitude = 40.52,
        minLongitude = 0.15,
        maxLatitude = 42.86,
        maxLongitude = 3.33,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = File(context.cacheDir, "test_${System.nanoTime()}.mbtiles")
        store = MbtilesStore(context, file)
    }

    @After
    fun tearDown() {
        store.close()
        file.delete()
    }

    @Test
    fun `the metadata of a pack is written and read back`() {
        store.writeMetadata(
            name = "Catalonia orthophotography",
            bounds = bounds,
            minZoom = 10,
            maxZoom = 13,
            format = MbtilesStore.FORMAT_JPEG,
            tileSize = 256,
        )

        val metadata = store.readMetadata()
        assertEquals("Catalonia orthophotography", metadata["name"])
        assertEquals("jpg", metadata["format"])
        assertEquals("256", metadata["tilesize"])
        assertEquals("baselayer", metadata["type"])
        assertEquals("0.15,40.52,3.33,42.86", metadata["bounds"])
        assertEquals("10", metadata["minzoom"])
        assertEquals("13", metadata["maxzoom"])
    }

    @Test
    fun `the format and the tile size are exposed directly`() {
        store.writeMetadata(
            name = "Catalonia topographic",
            bounds = bounds,
            minZoom = 10,
            maxZoom = 12,
            format = MbtilesStore.FORMAT_JPEG,
            tileSize = 512,
        )

        assertEquals(MbtilesStore.FORMAT_JPEG, store.format())
        assertEquals(512, store.tileSize())
    }

    @Test
    fun `a pack without metadata falls back to the default values`() {
        assertEquals(MbtilesStore.FORMAT_JPEG, store.format())
        assertEquals(256, store.tileSize())
    }

    @Test
    fun `a tile is stored and read back from its slippy coordinates`() {
        val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        store.putTile(zoom = 12, slippyX = 2085, slippyY = 1401, data = data)

        val read = store.getTile(zoom = 12, slippyX = 2085, slippyY = 1401)
        assertTrue(data.contentEquals(read))
        assertEquals(1, store.tileCount())
        assertEquals(listOf(12), store.availableZooms())
    }

    @Test
    fun `a missing tile is reported as absent`() {
        assertNull(store.getTile(zoom = 12, slippyX = 1, slippyY = 1))
    }

    @Test
    fun `the tiles are stored using the tms row convention`() {
        // The MBTiles specification stores the rows bottom-up, so the row of a slippy
        // tile is the mirrored one.
        store.putTile(zoom = 12, slippyX = 2085, slippyY = 1401, data = byteArrayOf(1))
        val expectedRow = (1 shl 12) - 1 - 1401

        val cursor = store.readableDatabase.rawQuery(
            "SELECT tile_row FROM tiles WHERE zoom_level = 12 AND tile_column = 2085",
            null,
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(expectedRow, it.getInt(0))
        }
    }

    @Test
    fun `storing the same tile twice replaces it`() {
        store.putTile(zoom = 10, slippyX = 512, slippyY = 647, data = byteArrayOf(1, 2, 3))
        store.putTile(zoom = 10, slippyX = 512, slippyY = 647, data = byteArrayOf(4, 5, 6))

        assertEquals(1, store.tileCount())
        val read = store.getTile(zoom = 10, slippyX = 512, slippyY = 647)
        assertTrue(byteArrayOf(4, 5, 6).contentEquals(read))
    }
}
