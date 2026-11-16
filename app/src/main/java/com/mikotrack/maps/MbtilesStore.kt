package com.mikotrack.maps

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/**
 * Reader and writer for MBTiles files (the de-facto standard for offline raster maps).
 *
 * The implementation follows the MBTiles 1.3 specification: a SQLite database with a
 * `tiles` table and a `metadata` table.
 */
class MbtilesStore(context: Context, file: File) : SQLiteOpenHelper(context, file.absolutePath, null, DB_VERSION) {

    /** A tile read from the store. */
    data class Tile(val zoom: Int, val column: Int, val row: Int, val data: ByteArray)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tiles (
                zoom_level INTEGER NOT NULL,
                tile_column INTEGER NOT NULL,
                tile_row INTEGER NOT NULL,
                tile_data BLOB NOT NULL,
                PRIMARY KEY (zoom_level, tile_column, tile_row)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS tiles_index ON tiles (zoom_level, tile_column, tile_row)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The store is a cache; recreating it is always safe.
        db.execSQL("DROP TABLE IF EXISTS tiles")
        db.execSQL("DROP TABLE IF EXISTS metadata")
        onCreate(db)
    }

    /**
     * Writes the standard MBTiles metadata.
     *
     * The [format] is the encoding of the stored tiles (`jpg` or `png`) and the
     * [tileSize] is the edge length in pixels of a single tile. Both are needed to
     * display the pack correctly, because a pack that mixes encodings or tile sizes
     * cannot be rendered with a single raster source.
     */
    fun writeMetadata(
        name: String,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        format: String = FORMAT_JPEG,
        tileSize: Int = 256,
    ) {
        val entries = mapOf(
            "name" to name,
            "type" to "baselayer",
            "version" to "1.3",
            "description" to "MycoTrack offline map area",
            "format" to format,
            "tilesize" to tileSize.toString(),
            "bounds" to "${bounds.minLongitude},${bounds.minLatitude}," +
                "${bounds.maxLongitude},${bounds.maxLatitude}",
            "minzoom" to minZoom.toString(),
            "maxzoom" to maxZoom.toString(),
        )

        val database = writableDatabase
        entries.forEach { (key, value) ->
            // The metadata table has no primary key, so the previous value has to be
            // removed before the new one is written.
            database.delete("metadata", "name = ?", arrayOf(key))
            val values = ContentValues().apply {
                put("name", key)
                put("value", value)
            }
            database.insert("metadata", null, values)
        }
    }

    /**
     * Reads the metadata of the file.
     *
     * The map is empty when the file does not carry any metadata row, which is the case
     * for a pack that has been created outside of the application.
     */
    fun readMetadata(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val cursor = readableDatabase.rawQuery("SELECT name, value FROM metadata", null)
        cursor.use {
            while (it.moveToNext()) result[it.getString(0)] = it.getString(1)
        }
        return result
    }

    /** The encoding of the tiles of the pack, `jpg` unless the file says otherwise. */
    fun format(): String = readMetadata()["format"]?.lowercase() ?: FORMAT_JPEG

    /** The edge length in pixels of a tile of the pack. */
    fun tileSize(): Int = readMetadata()["tilesize"]?.toIntOrNull() ?: 256

    /** Inserts a tile, replacing an existing one with the same coordinates. */
    fun putTile(zoom: Int, slippyX: Int, slippyY: Int, data: ByteArray) {
        val values = ContentValues().apply {
            put("zoom_level", zoom)
            put("tile_column", slippyX)
            put("tile_row", TileMath.toTmsRow(zoom, slippyY))
            put("tile_data", data)
        }
        writableDatabase.insertWithOnConflict("tiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Reads a tile, using the TMS row convention of the MBTiles format. */
    fun getTile(zoom: Int, slippyX: Int, slippyY: Int): ByteArray? {
        val cursor = readableDatabase.query(
            "tiles",
            arrayOf("tile_data"),
            "zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf(zoom.toString(), slippyX.toString(), TileMath.toTmsRow(zoom, slippyY).toString()),
            null,
            null,
            null,
            "1",
        )
        cursor.use {
            if (it.moveToFirst()) return it.getBlob(0)
        }
        return null
    }

    /** Number of tiles stored in the file. */
    fun tileCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM tiles", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    /** The zoom levels that are available in the file. */
    fun availableZooms(): List<Int> {
        val result = mutableListOf<Int>()
        val cursor = readableDatabase.rawQuery("SELECT DISTINCT zoom_level FROM tiles ORDER BY zoom_level", null)
        cursor.use {
            while (it.moveToNext()) result += it.getInt(0)
        }
        return result
    }

    companion object {
        private const val DB_VERSION = 1

        /** The JPEG encoding of a tile. */
        const val FORMAT_JPEG = "jpg"

        /** The PNG encoding of a tile. */
        const val FORMAT_PNG = "png"
    }
}
