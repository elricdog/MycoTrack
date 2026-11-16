package com.mikotrack.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mikotrack.MainActivity
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.data.repository.DefaultCategory
import com.mikotrack.data.repository.TripRepository
import com.mikotrack.data.repository.WaypointRepository
import com.mikotrack.domain.ProximityHit
import com.mikotrack.domain.ProximityMonitor
import com.mikotrack.domain.ProximityTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sounds an alarm when the user reaches a waypoint of one of the previous trips that are
 * overlaid on the map.
 *
 * It lives as long as the application process, so it keeps working with the screen off
 * while a trip is being recorded: the recording service publishes the positions through
 * [RecordingController]. When nothing is being recorded, the map screen hands over the
 * positions it tracks while it is visible through [onPassivePosition].
 */
class ProximityAlertManager(
    context: Context,
    private val settingsStore: SettingsStore,
    private val tripRepository: TripRepository,
    private val waypointRepository: WaypointRepository,
) {

    private val appContext = context.applicationContext
    private val monitor = ProximityMonitor()
    private val passivePosition = MutableStateFlow<CurrentPosition?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAlarmAt = 0L

    private val _alerts = MutableSharedFlow<ProximityHit>(extraBufferCapacity = 4)

    /** The waypoints that have just been reached, so the map can point at them. */
    val alerts: SharedFlow<ProximityHit> = _alerts.asSharedFlow()

    /** Receives the position tracked by the map screen; `null` once it stops tracking. */
    fun onPassivePosition(position: CurrentPosition?) {
        passivePosition.value = position
    }

    /** Starts watching the position and the overlaid trips. */
    fun start(scope: CoroutineScope) {
        createNotificationChannel()

        // The trip that is being recorded is never a target, even when it was overlaid
        // before it was resumed.
        val targets = combine(
            settingsStore.overlayTripIds,
            RecordingController.state.map { it.tripId }.distinctUntilChanged(),
        ) { ids, recordingId -> ids.filter { it != recordingId } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        tripRepository.observeTrips(),
                        combine(ids.map { waypointRepository.observeForTrip(it) }) { lists -> lists.toList().flatten() },
                    ) { trips, waypoints ->
                        val names = trips.associate { it.id to it.name }
                        waypoints.map { waypoint ->
                            ProximityTarget(
                                waypointId = waypoint.id,
                                tripId = waypoint.tripId,
                                latitude = waypoint.latitude,
                                longitude = waypoint.longitude,
                                description = waypoint.description,
                                category = waypoint.category,
                                tripName = names[waypoint.tripId].orEmpty(),
                            )
                        }
                    }
                }
            }

        val position = combine(RecordingController.state, passivePosition) { recording, passive ->
            if (recording.isRecording) recording.position else passive
        }.distinctUntilChanged()

        val settings = combine(
            settingsStore.proximityAlarmEnabled,
            settingsStore.proximityRadiusMeters,
        ) { enabled, radius -> enabled to radius }

        scope.launch {
            combine(position, targets, settings) { current, waypoints, (enabled, radius) ->
                if (!enabled) {
                    monitor.reset()
                    return@combine emptyList()
                }
                if (current == null) return@combine emptyList()
                monitor.update(
                    latitude = current.latitude,
                    longitude = current.longitude,
                    accuracyMeters = current.accuracy,
                    radiusMeters = radius.toDouble(),
                    targets = waypoints,
                )
            }.collect { hits ->
                val closest = hits.firstOrNull() ?: return@collect
                alert(closest)
            }
        }
    }

    private fun alert(hit: ProximityHit) {
        _alerts.tryEmit(hit)
        val now = System.currentTimeMillis()
        // Several waypoints reached within a few seconds ring only once.
        if (now - lastAlarmAt >= MIN_ALARM_INTERVAL_MILLIS) {
            lastAlarmAt = now
            mainHandler.post {
                playTone()
                vibrate()
            }
        }
        showNotification(hit)
    }

    private fun playTone() {
        runCatching {
            // The alarm stream is used because the phone is usually in a pocket during
            // a trip, where a notification sound is easy to miss.
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TONE_DURATION_MILLIS)
            mainHandler.postDelayed({ tone.release() }, TONE_DURATION_MILLIS + 200L)
        }
    }

    private fun vibrate() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
        }
    }

    private fun showNotification(hit: ProximityHit) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        val target = hit.target
        val label = target.description.ifBlank { DefaultCategory.labelFor(appContext, target.category) }
        val text = appContext.getString(
            R.string.proximity_notification_text,
            label,
            Formatters.distance(hit.distanceMeters),
            target.tripName,
        )
        val openIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.proximity_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.proximity_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.proximity_channel_description)
            // The alarm plays its own tone and vibration, so the channel stays silent
            // to avoid sounding twice.
            setSound(null, null)
            enableVibration(false)
        }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "mycotrack_proximity"
        const val NOTIFICATION_ID = 4712
        const val REQUEST_OPEN = 20
        const val TONE_DURATION_MILLIS = 1_500
        const val MIN_ALARM_INTERVAL_MILLIS = 5_000L
        val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 600)
    }
}
