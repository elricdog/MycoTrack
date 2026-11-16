package com.mikotrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.data.prefs.DEFAULT_ZOOM_LEVEL
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.data.prefs.ThemeMode
import com.mikotrack.data.db.OfflineAreaEntity
import com.mikotrack.maps.BoundingBox
import com.mikotrack.maps.MapLayer
import com.mikotrack.maps.OfflineMapManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The progress of an offline map download. */
data class DownloadProgress(
    val running: Boolean = false,
    val downloaded: Int = 0,
    val total: Int = 0,
    val error: String? = null,
)

/** Drives the settings screen. */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val offlineMapManager: OfflineMapManager,
) : ViewModel() {

    /** The theme mode. */
    val themeMode = settingsStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** The requested GPS update interval. */
    val gpsInterval = settingsStore.gpsIntervalMillis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2_000)

    /** The maximum accepted accuracy. */
    val maxAccuracy = settingsStore.maxAccuracyMeters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    /** The maximum accepted jump between two fixes. */
    val maxJump = settingsStore.maxJumpMeters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)

    /** Whether the screen stays on while recording. */
    val keepScreenOn = settingsStore.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The zoom level the map uses by default. */
    val defaultZoom = settingsStore.defaultZoom
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_ZOOM_LEVEL)

    /** Changes the default zoom level of the map. */
    fun setDefaultZoom(zoom: Float) {
        viewModelScope.launch { settingsStore.setDefaultZoom(zoom) }
    }

    /** The keys of the map buttons the user hid. */
    val hiddenMapButtons = settingsStore.hiddenMapButtons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Shows or hides one of the buttons of the map screen. */
    fun setMapButtonVisible(key: String, visible: Boolean) {
        viewModelScope.launch { settingsStore.setMapButtonVisible(key, visible) }
    }

    /** Whether a parking waypoint is added automatically when a trip starts. */
    val autoParking = settingsStore.autoParkingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAutoParking(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoParkingEnabled(enabled) }
    }

    /** The downloaded offline areas. */
    val offlineAreas: StateFlow<List<OfflineAreaEntity>> = offlineMapManager.observeAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The identifier of the offline area that is used as the base map. */
    val activeAreaId: StateFlow<Long?> = settingsStore.activeOfflineLayerId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Selects the offline area that is used as the base map. */
    fun setActiveArea(id: Long?) {
        viewModelScope.launch { settingsStore.setActiveOfflineLayer(id) }
    }

    private val _download = MutableStateFlow(DownloadProgress())
    val download: StateFlow<DownloadProgress> = _download.asStateFlow()

    /** Changes the theme. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    /** Changes the GPS update interval. */
    fun setGpsInterval(millis: Int) {
        viewModelScope.launch { settingsStore.setGpsInterval(millis) }
    }

    /** Changes the maximum accepted accuracy. */
    fun setMaxAccuracy(meters: Int) {
        viewModelScope.launch { settingsStore.setMaxAccuracy(meters) }
    }

    /** Changes the maximum accepted jump distance. */
    fun setMaxJump(meters: Int) {
        viewModelScope.launch { settingsStore.setMaxJumpDistance(meters) }
    }

    /** Changes whether the screen stays on. */
    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setKeepScreenOn(enabled) }
    }

    /** Downloads a new offline area. */
    fun downloadArea(
        layer: MapLayer,
        name: String,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
    ) {
        viewModelScope.launch {
            _download.value = DownloadProgress(running = true)
            runCatching {
                offlineMapManager.downloadArea(layer, name, bounds, minZoom, maxZoom) { done, total ->
                    _download.value = DownloadProgress(running = true, downloaded = done, total = total)
                }
            }.onFailure {
                _download.value = DownloadProgress(error = it.message)
            }
            _download.value = DownloadProgress()
        }
    }

    /** Deletes an offline area. */
    fun deleteArea(area: OfflineAreaEntity) {
        viewModelScope.launch { offlineMapManager.deleteArea(area) }
    }
}
