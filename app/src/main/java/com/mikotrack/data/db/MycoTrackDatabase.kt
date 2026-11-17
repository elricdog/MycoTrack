package com.mikotrack.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The Room database backing MycoTrack. */
@Database(
    entities = [
        TripEntity::class,
        TrackPointEntity::class,
        WaypointEntity::class,
        CategoryEntity::class,
        OfflineAreaEntity::class,
        RainDailyEntity::class,
        RainMetaEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MycoTrackDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun waypointDao(): WaypointDao
    abstract fun categoryDao(): CategoryDao
    abstract fun offlineAreaDao(): OfflineAreaDao
    abstract fun rainDao(): RainDao

    companion object {
        private const val DATABASE_NAME = "mycotrack.db"

        /**
         * Version 2 adds the columns that describe how an offline area has to be
         * rendered: whether it ships with the application, the encoding of its tiles
         * and the edge length of a tile.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE offline_areas ADD COLUMN bundled INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE offline_areas ADD COLUMN format TEXT NOT NULL DEFAULT 'jpg'",
                )
                db.execSQL(
                    "ALTER TABLE offline_areas ADD COLUMN tileSize INTEGER NOT NULL DEFAULT 256",
                )
            }
        }

        /** Version 3 stores the colour the user picked for the route of each trip. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN color INTEGER")
            }
        }

        /** Version 4 stores the lattice of the rainfall overlay. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rain_daily` (`latitude` INTEGER NOT NULL, " +
                        "`longitude` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                        "`millimeters` REAL NOT NULL, PRIMARY KEY(`latitude`, `longitude`, `day`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rain_meta` (`id` INTEGER NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, `lat0` REAL NOT NULL, `lon0` REAL NOT NULL, " +
                        "`stepDeg` REAL NOT NULL, `rows` INTEGER NOT NULL, `cols` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        @Volatile
        private var instance: MycoTrackDatabase? = null

        fun getInstance(context: Context): MycoTrackDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): MycoTrackDatabase =
            Room.databaseBuilder(context, MycoTrackDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
