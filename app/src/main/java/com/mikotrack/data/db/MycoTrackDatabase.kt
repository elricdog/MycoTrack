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
    ],
    version = 3,
    exportSchema = true,
)
abstract class MycoTrackDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun waypointDao(): WaypointDao
    abstract fun categoryDao(): CategoryDao
    abstract fun offlineAreaDao(): OfflineAreaDao

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

        @Volatile
        private var instance: MycoTrackDatabase? = null

        fun getInstance(context: Context): MycoTrackDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): MycoTrackDatabase =
            Room.databaseBuilder(context, MycoTrackDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
