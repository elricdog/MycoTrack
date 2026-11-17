package com.mikotrack.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One node of the rainfall lattice for one day: the millimetres the weather model
 * measured at that coordinate. The coordinates are stored in micro degrees so that the
 * primary key is stable across fetches (the model snaps every requested point to its
 * own cells, which gives a handful of decimals).
 */
@Entity(tableName = "rain_daily", primaryKeys = ["latitude", "longitude", "day"])
data class RainDailyEntity(
    val latitude: Int,
    val longitude: Int,
    /** The day of the measurement as an epoch day. */
    val day: Int,
    /** The precipitation of the day, in millimetres. */
    val millimeters: Float,
)

/** The single row that describes the lattice of the [RainDailyEntity] table. */
@Entity(tableName = "rain_meta")
data class RainMetaEntity(
    @PrimaryKey
    val id: Int = 0,
    /** When the lattice was downloaded, in epoch milliseconds; it busts the tile cache. */
    val fetchedAt: Long,
    /** The latitude of the first row of the lattice (the southernmost). */
    val lat0: Double,
    /** The longitude of the first column of the lattice (the westernmost). */
    val lon0: Double,
    /** The spacing of the lattice, in degrees. */
    val stepDeg: Double,
    val rows: Int,
    val cols: Int,
)

@Dao
interface RainDao {

    @Query("SELECT * FROM rain_daily")
    fun all(): List<RainDailyEntity>

    @Query("SELECT * FROM rain_meta WHERE id = 0")
    fun meta(): RainMetaEntity?

    @Query("DELETE FROM rain_daily")
    fun clearDaily()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDaily(rows: List<RainDailyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMeta(meta: RainMetaEntity)
}
