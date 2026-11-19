package com.mikotrack.location

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.core.GeoUtils
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.data.repository.DefaultCategory
import com.mikotrack.data.repository.ParkingLocator
import com.mikotrack.data.repository.TripRepository
import com.mikotrack.data.repository.WaypointRepository
import com.mikotrack.domain.SafetyWarning
import com.mikotrack.domain.SafetyWarningTracker
import com.mikotrack.domain.SunCalculator
import com.mikotrack.domain.WalkingEstimate
import com.mikotrack.domain.model.Waypoint
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Looks after the safety of the user while a trip is being recorded: it saves where the
 * car was parked, warns before sunset and when the battery runs low, and alerts when
 * the walk back to the car would be longer than the daylight that is left.
 *
 * It is driven by the positions of [RecordingService], so it works with the screen off.
 * The decisions themselves live in [SafetyWarningTracker], which is unit tested.
 */
class TripSafetyMonitor(
    context: Context,
    private val tripId: Long,
    private val settingsStore: SettingsStore,
    private val tripRepository: TripRepository,
    private val waypointRepository: WaypointRepository,
) {

    private val appContext = context.applicationContext
    private val parkingLocator = ParkingLocator(tripRepository, waypointRepository)
    private val tracker = SafetyWarningTracker(issuedByTrip[tripId].orEmpty())
    private var parkingPending = false
    private var lastCheckAt = 0L

    /** Prepares the monitor; a resumed trip that already has a parking waypoint keeps it. */
    suspend fun start() {
        parkingPending = settingsStore.autoParkingEnabled.first() &&
            waypointRepository.getForTrip(tripId).none { it.category == DefaultCategory.PARKING.key }
    }

    /** Receives every smoothed position of the recording. */
    suspend fun onPosition(position: CurrentPosition) {
        if (parkingPending) saveParking(position)

        val now = System.currentTimeMillis()
        if (now - lastCheckAt < CHECK_INTERVAL_MILLIS) return
        lastCheckAt = now

        val zone = ZoneId.systemDefault()
        val sunset = SunCalculator.sunsetUtcMillis(
            LocalDate.now(zone),
            position.latitude,
            position.longitude,
        )
        val battery = readBattery()

        // The way back is evaluated once per check and reused by the warnings below.
        val parking = parkingLocator.find(tripId)
        val walkMillis = parking?.let {
            GeoUtils.distanceMeters(position.latitude, position.longitude, it.latitude, it.longitude)
        }?.let { WalkingEstimate.millisFor(it) }

        val warnings = listOfNotNull(
            tracker.sunsetWarning(now, sunset),
            tracker.batteryWarning(battery?.first, battery?.second ?: false),
            tracker.unreachableWarning(now, sunset, walkMillis),
        )
        issuedByTrip[tripId] = tracker.issued
        if (warnings.isEmpty()) return

        val carText = carText(position, parking)
        warnings.forEach { warning ->
            when (warning) {
                SafetyWarning.SUNSET_60, SafetyWarning.SUNSET_20 -> {
                    val minutes = ((sunset ?: now) - now).coerceAtLeast(0L) / 60_000L
                    notify(
                        id = SafetyNotifier.SUNSET_NOTIFICATION_ID,
                        title = appContext.resources.getQuantityString(
                            R.plurals.field_sunset_title,
                            minutes.toInt(),
                            minutes.toInt(),
                        ),
                        text = appContext.getString(R.string.field_sunset_text, formatTime(sunset ?: now, zone)) +
                            "\n" + carText,
                    )
                }

                SafetyWarning.SUNSET_TOO_FAR -> notify(
                    id = SafetyNotifier.SUNSET_NOTIFICATION_ID,
                    title = appContext.getString(R.string.field_sunset_far_title),
                    text = appContext.getString(R.string.field_sunset_far_text, formatTime(sunset ?: now, zone)) +
                        "\n" + carText,
                )

                SafetyWarning.BATTERY_20, SafetyWarning.BATTERY_10 -> notify(
                    id = SafetyNotifier.BATTERY_NOTIFICATION_ID,
                    title = appContext.getString(R.string.field_battery_title, battery?.first ?: 0),
                    text = appContext.getString(R.string.field_battery_text) + "\n" + carText,
                )
            }
        }
    }

    /** Formats an instant as a short local time. */
    private fun formatTime(millis: Long, zone: ZoneId): String =
        android.text.format.DateFormat.getTimeFormat(appContext).apply {
            timeZone = java.util.TimeZone.getTimeZone(zone)
        }.format(java.util.Date.from(Instant.ofEpochMilli(millis)))

    private suspend fun saveParking(position: CurrentPosition) {
        val accuracy = position.accuracy ?: return
        if (accuracy > PARKING_ACCURACY_METERS) return
        parkingPending = false
        waypointRepository.add(
            tripId = tripId,
            latitude = position.latitude,
            longitude = position.longitude,
            altitude = position.altitude,
            timestamp = System.currentTimeMillis(),
            description = appContext.getString(R.string.field_parking_description),
            category = DefaultCategory.PARKING.key,
        )
        val count = waypointRepository.countForTrip(tripId)
        tripRepository.updateCounters(tripId, count, count)
    }

    /** The distance and walking time to the car, or a note that it is unknown. */
    private suspend fun carText(position: CurrentPosition, parking: Waypoint?): String {
        if (parking == null) return appContext.getString(R.string.field_car_unknown)
        val meters = GeoUtils.distanceMeters(position.latitude, position.longitude, parking.latitude, parking.longitude)
        val bearing = GeoUtils.bearingDegrees(position.latitude, position.longitude, parking.latitude, parking.longitude)
        val compass = appContext.resources.getStringArray(R.array.format_compass_points)[Formatters.compassIndex(bearing)]
        return appContext.getString(
            R.string.field_car_distance,
            Formatters.distance(meters),
            compass,
            Formatters.durationShort(WalkingEstimate.millisFor(meters)),
        )
    }

    /** The battery level in percent and whether it is charging; no permission is needed. */
    private fun readBattery(): Pair<Int, Boolean>? = runCatching {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level <= 0 || level > 100) null else level to manager.isCharging
    }.getOrNull()

    private fun notify(id: Int, title: String, text: String) {
        vibrate(appContext, WARNING_VIBRATION)
        if (!SafetyNotifier.canNotify(appContext)) return
        SafetyNotifier.warn(appContext, id, title, text)
    }

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 30_000L
        private const val PARKING_ACCURACY_METERS = 30f
        private val WARNING_VIBRATION = longArrayOf(0, 300, 150, 300)

        /** The warnings given per trip, so a restarted service does not repeat them. */
        private val issuedByTrip = mutableMapOf<Long, Set<SafetyWarning>>()
    }
}

/** A short vibration used as feedback when the phone stays in the pocket. */
internal fun vibrate(context: Context, pattern: LongArray) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
