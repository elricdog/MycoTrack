package com.mikotrack

import android.app.Application
import com.mikotrack.core.AppContainer
import com.mikotrack.location.RecordingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/**
 * Application entry point.
 *
 * It initialises MapLibre, creates the dependency container and seeds the built-in
 * waypoint categories.
 */
class MycoTrackApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // MapLibre has to be initialised before any MapView is created.
        MapLibre.getInstance(this)

        applicationScope.launch {
            container.categoryRepository.ensureDefaults()
        }

        // A previous session may have been killed while recording; the state is rebuilt
        // from the database when the map screen asks for it.
        RecordingController.reset()

        // The proximity alarm watches the position for the whole life of the process,
        // so it keeps working while a trip is recorded with the screen off.
        container.proximityAlertManager.start(applicationScope)
    }
}
