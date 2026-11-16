package com.mikotrack.ui.diagnostics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.core.Permissions
import com.mikotrack.domain.GpsQuality
import com.mikotrack.domain.GpsQualityRating
import com.mikotrack.location.GnssMonitor
import com.mikotrack.location.GnssSnapshot
import com.mikotrack.location.LocationTracker
import com.mikotrack.location.SensorKind
import com.mikotrack.location.SensorMonitor
import com.mikotrack.location.SensorSnapshot
import com.mikotrack.location.SensorStaticInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/** The fixes received from one location provider while the screen is visible. */
data class ProviderStats(
    val provider: String,
    val count: Int,
    val lastFix: Location,
    /** Mean time between the last few fixes, or null until two fixes have arrived. */
    val averageIntervalMillis: Long?,
)

/** The state of the location services and of the permissions. */
data class LocationStatus(
    val locationEnabled: Boolean = false,
    val gpsEnabled: Boolean = false,
    val networkEnabled: Boolean = false,
    val finePermission: Boolean = false,
    val coarsePermission: Boolean = false,
    val backgroundPermission: Boolean = false,
)

/** Battery and power-saving state of the phone. */
data class DeviceStatus(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val batteryTemperatureCelsius: Float? = null,
    val powerSaveMode: Boolean = false,
    /** `PowerManager.LOCATION_MODE_*`, or null before Android 9. */
    val locationPowerSaveMode: Int? = null,
    val ignoringBatteryOptimizations: Boolean = false,
    val androidRelease: String = Build.VERSION.RELEASE.orEmpty(),
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val manufacturer: String = Build.MANUFACTURER.orEmpty(),
    val model: String = Build.MODEL.orEmpty(),
) {
    /** True when Battery Saver (when on) limits the GPS while the screen is off. */
    val locationLimitedInBackground: Boolean
        get() = locationPowerSaveMode != null &&
            locationPowerSaveMode != PowerManager.LOCATION_MODE_NO_CHANGE &&
            locationPowerSaveMode != PowerManager.LOCATION_MODE_FOREGROUND_ONLY
}

/** Everything shown by the diagnostics screen. */
data class DiagnosticsUiState(
    val quality: GpsQuality = GpsQuality.SEARCHING,
    val location: LocationStatus = LocationStatus(),
    val gnssHardwareModel: String? = null,
    val gnssHardwareYear: Int? = null,
    val primaryFix: Location? = null,
    val fixAgeMillis: Long? = null,
    val providers: List<ProviderStats> = emptyList(),
    val gnss: GnssSnapshot = GnssSnapshot(),
    val sensors: SensorSnapshot = SensorSnapshot(),
    val sensorInfo: Map<SensorKind, SensorStaticInfo?> = emptyMap(),
    val device: DeviceStatus = DeviceStatus(),
)

/** Accumulates the fixes of every provider. */
private data class FixHistory(
    val stats: Map<String, ProviderStats> = emptyMap(),
    val intervals: Map<String, List<Long>> = emptyMap(),
) {
    operator fun plus(location: Location): FixHistory {
        val provider = location.provider ?: "?"
        val previous = stats[provider]
        val interval = previous?.let {
            (location.elapsedRealtimeNanos - it.lastFix.elapsedRealtimeNanos) / 1_000_000L
        }?.takeIf { it > 0 }
        val recent = (intervals[provider].orEmpty() + listOfNotNull(interval)).takeLast(INTERVAL_WINDOW)
        val updated = ProviderStats(
            provider = provider,
            count = (previous?.count ?: 0) + 1,
            lastFix = location,
            averageIntervalMillis = recent.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        )
        return FixHistory(stats + (provider to updated), intervals + (provider to recent))
    }

    companion object {
        const val INTERVAL_WINDOW = 10
    }
}

/** Collects the GPS fixes, the satellites and the sensors while the screen is visible. */
class DiagnosticsViewModel(
    context: Context,
    private val locationTracker: LocationTracker,
    private val gnssMonitor: GnssMonitor,
    sensorMonitor: SensorMonitor,
) : ViewModel() {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager?
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?

    private val sensorInfo = sensorMonitor.sensorInfo()
    private val hardwareModel = gnssMonitor.hardwareModelName()
    private val hardwareYear = gnssMonitor.hardwareYear()

    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    // The location request also wakes up the GNSS receiver, without which no satellite
    // status is reported. The flow ends when no provider is enabled, so it is retried.
    private val fixes: Flow<FixHistory> = flow {
        while (true) {
            emitAll(locationTracker.locationUpdates(UPDATE_INTERVAL_MILLIS))
            delay(RETRY_DELAY_MILLIS)
        }
    }.scan(FixHistory()) { history, location -> history + location }

    private val gnss: Flow<GnssSnapshot> = flow {
        while (true) {
            emitAll(gnssMonitor.status())
            delay(RETRY_DELAY_MILLIS)
        }
    }.onStart { emit(GnssSnapshot()) }

    private val sensors: Flow<SensorSnapshot> =
        sensorMonitor.readings(displayRotation = ::displayRotation).onStart { emit(SensorSnapshot()) }

    val uiState: StateFlow<DiagnosticsUiState> =
        combine(ticker, fixes, gnss, sensors) { _, history, gnss, sensors ->
            buildState(history, gnss, sensors)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DiagnosticsUiState(sensorInfo = sensorInfo),
        )

    private fun buildState(history: FixHistory, gnss: GnssSnapshot, sensors: SensorSnapshot): DiagnosticsUiState {
        val location = locationStatus()
        val now = SystemClock.elapsedRealtimeNanos()
        fun ageOf(fix: Location) = ((now - fix.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)

        // A recent GPS fix is preferred over a newer but coarser network fix.
        val gpsFix = history.stats[LocationManager.GPS_PROVIDER]?.lastFix
        val primary = gpsFix?.takeIf { ageOf(it) <= GpsQualityRating.MAX_FIX_AGE_MILLIS }
            ?: history.stats.values.maxByOrNull { it.lastFix.elapsedRealtimeNanos }?.lastFix
        val age = primary?.let(::ageOf)
        val quality = GpsQualityRating.rate(
            locationEnabled = location.locationEnabled,
            accuracyMeters = primary?.takeIf { it.hasAccuracy() }?.accuracy,
            fixAgeMillis = age,
            satellitesUsed = gnss.usedCount.takeIf { gnss.satellites.isNotEmpty() },
        )
        return DiagnosticsUiState(
            quality = quality,
            location = location,
            gnssHardwareModel = hardwareModel,
            gnssHardwareYear = hardwareYear,
            primaryFix = primary,
            fixAgeMillis = age,
            providers = history.stats.values.sortedBy { it.provider },
            gnss = gnss,
            sensors = sensors,
            sensorInfo = sensorInfo,
            device = deviceStatus(),
        )
    }

    private fun locationStatus(): LocationStatus {
        fun enabled(provider: String) =
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        val gps = enabled(LocationManager.GPS_PROVIDER)
        val network = enabled(LocationManager.NETWORK_PROVIDER)
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            gps || network
        }
        return LocationStatus(
            locationEnabled = locationEnabled,
            gpsEnabled = gps,
            networkEnabled = network,
            finePermission = Permissions.isGranted(appContext, Manifest.permission.ACCESS_FINE_LOCATION),
            coarsePermission = Permissions.isGranted(appContext, Manifest.permission.ACCESS_COARSE_LOCATION),
            backgroundPermission = Permissions.hasBackgroundLocation(appContext),
        )
    }

    private fun deviceStatus(): DeviceStatus {
        // ACTION_BATTERY_CHANGED is sticky: registering without a receiver returns the last value.
        val battery: Intent? = runCatching {
            ContextCompat.registerReceiver(
                appContext,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
        return DeviceStatus(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            batteryTemperatureCelsius = temperature?.let { it / 10f },
            powerSaveMode = powerManager?.isPowerSaveMode ?: false,
            locationPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                powerManager?.locationPowerSaveMode
            } else {
                null
            },
            ignoringBatteryOptimizations =
                powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: false,
        )
    }

    private fun displayRotation(): Int =
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0

    companion object {
        const val UPDATE_INTERVAL_MILLIS = 1_000L
        private const val RETRY_DELAY_MILLIS = 3_000L
    }
}
