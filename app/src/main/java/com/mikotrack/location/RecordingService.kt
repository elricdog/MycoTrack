package com.mikotrack.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mikotrack.MainActivity
import com.mikotrack.MycoTrackApp
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.data.repository.DefaultCategory
import com.mikotrack.domain.stats.TripStatsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the GPS while a trip is being recorded.
 *
 * Running the tracker inside a foreground service with the `location` type is what allows
 * the application to keep recording while the screen is off or the user switches apps.
 */
class RecordingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null

    /** The trip currently being recorded; survives service restarts within the process. */
    private var activeTripId: Long? = null

    /** The last smoothed position of the recording, used by the quick "mushroom" action. */
    @Volatile
    private var lastSmoothedPosition: CurrentPosition? = null

    /** The text of the ongoing notification, and a short-lived confirmation shown instead. */
    @Volatile
    private var progressText: String? = null
    private var feedbackText: String? = null
    private var feedbackJob: Job? = null

    private lateinit var container: com.mikotrack.core.AppContainer

    override fun onCreate() {
        super.onCreate()
        container = (application as MycoTrackApp).container
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }

            ACTION_MUSHROOM_FOUND -> {
                // Only meaningful while recording; a stray intent must not start the GPS.
                if (trackingJob == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                saveMushroomHere()
            }

            else -> {
                val tripId = intent?.getLongExtra(EXTRA_TRIP_ID, -1L) ?: -1L
                startForegroundSafely()
                if (tripId > 0) startTracking(tripId) else stopSelf()
            }
        }
        // Restart the service if the system kills it while a recording is running.
        return START_STICKY
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        serviceScope.cancel()
        RecordingController.onRecordingStopped()
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        val notification = buildNotification(
            title = getString(R.string.recording_notification_title),
            text = getString(R.string.recording_notification_waiting),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTracking(tripId: Long) {
        trackingJob?.cancel()
        activeTripId = tripId
        lastSmoothedPosition = null
        progressText = null
        trackingJob = serviceScope.launch {
            val trip = container.tripRepository.getTrip(tripId) ?: run {
                stopSelf()
                return@launch
            }

            RecordingController.onRecordingStarted(trip.id, trip.name, trip.startTime)
            RecordingController.onGpsAvailability(container.locationTracker.isLocationEnabled())

            val interval = container.settingsStore.gpsIntervalMillis.first()
            val maxAccuracy = container.settingsStore.maxAccuracyMeters.first()
            val maxJump = container.settingsStore.maxJumpMeters.first()
            val smoother = GpsTrackSmoother(maxAccuracy.toDouble(), maxJump.toDouble())
            val sampler = TrackSampler(interval.toLong())

            // A restarted service continues the track and its statistics where they were.
            val accepted = container.tripRepository.getPoints(tripId).toMutableList()
            sampler.resumeFrom(accepted.lastOrNull())

            val safetyMonitor = TripSafetyMonitor(
                context = this@RecordingService,
                tripId = tripId,
                settingsStore = container.settingsStore,
                tripRepository = container.tripRepository,
                waypointRepository = container.waypointRepository,
            )
            runCatching { safetyMonitor.start() }

            // The receiver is sampled every second whatever the stored interval is: the
            // extra fixes feed the smoothing and keep the position on the map fluid.
            container.locationTracker
                .locationUpdates(GpsTrackSmoother.SAMPLING_INTERVAL_MILLIS)
                .collect { location ->
                    val raw = location.toRawFix()
                    val smoothed = smoother.process(raw)
                    if (smoothed == null) {
                        // Before the first usable fix the user still has to see where
                        // they are and how good the signal is while the GPS settles.
                        if (!smoother.isInitialized) RecordingController.onPosition(raw.toCurrentPosition())
                        return@collect
                    }
                    val current = smoothed.toCurrentPosition()
                    lastSmoothedPosition = current
                    RecordingController.onPosition(current)
                    runCatching { safetyMonitor.onPosition(current) }

                    val stored = sampler.sample(smoothed) ?: return@collect
                    accepted += stored
                    container.tripRepository.appendPoint(tripId, stored)

                    val stats = TripStatsCalculator.compute(
                        points = accepted,
                        startTime = trip.startTime,
                        endTime = null,
                    )
                    RecordingController.onStats(stats)

                    progressText = getString(
                        R.string.recording_notification_progress,
                        Formatters.duration(stats.elapsedMillis),
                        Formatters.distance(stats.distanceMeters),
                    )
                    refreshNotification()
                }
        }
    }

    /** Shows the progress, or the confirmation of a quick action while it is fresh. */
    private fun refreshNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = getString(R.string.recording_notification_title),
                text = feedbackText ?: progressText ?: getString(R.string.recording_notification_waiting),
            ),
        )
    }

    /**
     * Adds a "mushroom found" waypoint at the last smoothed position, straight from the
     * notification, so the phone can stay in the pocket or locked.
     */
    private fun saveMushroomHere() {
        val tripId = activeTripId ?: return
        val position = lastSmoothedPosition
        serviceScope.launch {
            val message = if (position == null) {
                vibrate(this@RecordingService, QUICK_SAVE_FAILED_VIBRATION)
                getString(R.string.field_notification_no_position)
            } else {
                val waypoints = container.waypointRepository
                container.waypointRepository.add(
                    tripId = tripId,
                    latitude = position.latitude,
                    longitude = position.longitude,
                    altitude = position.altitude,
                    timestamp = System.currentTimeMillis(),
                    description = "",
                    category = DefaultCategory.MUSHROOM_FOUND.key,
                )
                val count = waypoints.countForTrip(tripId)
                container.tripRepository.updateCounters(tripId, count, count)
                val mushrooms = waypoints.getForTrip(tripId).count { it.category == DefaultCategory.MUSHROOM_FOUND.key }
                vibrate(this@RecordingService, QUICK_SAVE_VIBRATION)
                resources.getQuantityString(R.plurals.field_notification_mushroom_saved, mushrooms, mushrooms)
            }
            showFeedback(message)
        }
    }

    /** Replaces the text of the ongoing notification for a few seconds. */
    private fun showFeedback(message: String) {
        feedbackJob?.cancel()
        feedbackText = message
        refreshNotification()
        feedbackJob = serviceScope.launch {
            delay(FEEDBACK_MILLIS)
            feedbackText = null
            refreshNotification()
        }
    }

    private fun stopRecording() {
        val fallbackTripId = activeTripId ?: RecordingController.state.value.tripId
        feedbackJob?.cancel()
        serviceScope.launch {
            val tripId = fallbackTripId ?: container.tripRepository.getActiveTrip()?.id
            if (tripId != null && tripId > 0) {
                container.tripRepository.finishTrip(tripId, System.currentTimeMillis())
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val mushroomIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RecordingService::class.java).setAction(ACTION_MUSHROOM_FOUND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            // Public, so the quick action can be used from the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.field_notification_action_mushroom), mushroomIntent)
            .addAction(0, getString(R.string.recording_notification_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // The first channel was silent ("low" importance), and Android hides silent
        // notifications from the lock screen, which also hid the quick "Mushroom found"
        // action. The importance of an existing channel cannot be raised, so it is
        // replaced by a new one that is shown on the lock screen without any sound.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.recording_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "mycotrack_recording_v2"
        private const val LEGACY_CHANNEL_ID = "mycotrack_recording"
        private const val NOTIFICATION_ID = 4711
        private const val EXTRA_TRIP_ID = "com.mikotrack.extra.TRIP_ID"
        const val ACTION_STOP = "com.mikotrack.action.STOP_RECORDING"
        const val ACTION_MUSHROOM_FOUND = "com.mikotrack.action.MUSHROOM_FOUND"
        private const val FEEDBACK_MILLIS = 6_000L
        private val QUICK_SAVE_VIBRATION = longArrayOf(0, 120)
        private val QUICK_SAVE_FAILED_VIBRATION = longArrayOf(0, 300, 150, 300)

        /** Starts the foreground service that records the given trip. */
        fun start(context: Context, tripId: Long) {
            val intent = Intent(context, RecordingService::class.java)
                .putExtra(EXTRA_TRIP_ID, tripId)
            context.startForegroundService(intent)
        }

        /** Stops the recording service. */
        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
