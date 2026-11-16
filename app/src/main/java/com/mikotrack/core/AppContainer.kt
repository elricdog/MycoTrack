package com.mikotrack.core

import android.content.Context
import com.mikotrack.data.db.MycoTrackDatabase
import com.mikotrack.data.files.ImageStore
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.data.repository.CategoryRepository
import com.mikotrack.data.repository.TripRepository
import com.mikotrack.data.repository.WaypointRepository
import com.mikotrack.location.GnssMonitor
import com.mikotrack.location.LocationTracker
import com.mikotrack.location.ProximityAlertManager
import com.mikotrack.location.SensorMonitor
import com.mikotrack.maps.OfflineMapManager

/**
 * Manual dependency container.
 *
 * The application is small enough that a full blown DI framework would add more
 * complexity than value; this container keeps the wiring explicit and testable.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: MycoTrackDatabase by lazy { MycoTrackDatabase.getInstance(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val imageStore: ImageStore by lazy { ImageStore(appContext) }

    val locationTracker: LocationTracker by lazy { LocationTracker(appContext) }

    val gnssMonitor: GnssMonitor by lazy { GnssMonitor(appContext) }

    val sensorMonitor: SensorMonitor by lazy { SensorMonitor(appContext) }

    val tripRepository: TripRepository by lazy {
        TripRepository(database.tripDao(), database.trackPointDao(), imageStore)
    }

    val waypointRepository: WaypointRepository by lazy {
        WaypointRepository(database.waypointDao(), imageStore)
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(database.categoryDao())
    }

    val offlineMapManager: OfflineMapManager by lazy {
        OfflineMapManager(appContext, database.offlineAreaDao())
    }

    val proximityAlertManager: ProximityAlertManager by lazy {
        ProximityAlertManager(appContext, settingsStore, tripRepository, waypointRepository)
    }
}
