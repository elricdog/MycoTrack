package com.mikotrack.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mikotrack.R
import com.mikotrack.core.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Delivers the "are you ok?" check-in of a recorded trip.
 *
 * When the alarm rings the receiver checks that a trip is still being recorded and
 * that the persisted due moment has really passed; a trip that was closed in the
 * meantime silences the check-in without any noise. The warning offers an "I am ok"
 * button that postpones the next check-in by one interval. It sounds with the high
 * importance of the safety channel, so a phone forgotten in the pocket gets attention.
 */
class CheckInReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_ALARM -> {
                val pending = goAsync()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                scope.launch {
                    try {
                        handleAlarm(appContext)
                    } finally {
                        pending.finish()
                        scope.cancel()
                    }
                }
            }

            ACTION_OK -> {
                val pending = goAsync()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                scope.launch {
                    try {
                        SafetyNotifier.cancel(appContext, SafetyNotifier.CHECKIN_NOTIFICATION_ID)
                        // Only postpone while a trip is really still open.
                        val container = CheckInAlarm.containerOf(appContext)
                        if (container.tripRepository.getActiveTrip() != null) {
                            CheckInAlarm.postpone(appContext)
                            vibrate(appContext, ACK_VIBRATION)
                        }
                    } finally {
                        pending.finish()
                        scope.cancel()
                    }
                }
            }
        }
    }

    private suspend fun handleAlarm(appContext: Context) {
        val container = CheckInAlarm.containerOf(appContext)
        val store = container.settingsStore
        // The user may have turned the check-in off since the alarm was armed.
        if (!store.checkInEnabled.first()) {
            store.setCheckInDueAt(0L)
            return
        }
        val dueAt = store.checkInDueAt.first()
        val now = System.currentTimeMillis()
        val trip = container.tripRepository.getActiveTrip()
        // A closed trip silences the check-in for good.
        if (trip == null) {
            store.setCheckInDueAt(0L)
            return
        }
        // An alarm redelivered before its moment only has to fire again, not reset the timer.
        if (dueAt <= 0L || now < dueAt - TOLERANCE_MILLIS) {
            if (dueAt > 0L) CheckInAlarm.reschedule(appContext, dueAt)
            return
        }
        val elapsed = now - trip.startTime
        val okIntent = android.app.PendingIntent.getBroadcast(
            appContext,
            REQUEST_OK,
            Intent(appContext, CheckInReceiver::class.java).setAction(ACTION_OK),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val action = NotificationCompat.Action(0, appContext.getString(R.string.field_checkin_action_ok), okIntent)
        SafetyNotifier.warn(
            appContext,
            SafetyNotifier.CHECKIN_NOTIFICATION_ID,
            appContext.getString(R.string.field_checkin_title),
            appContext.getString(R.string.field_checkin_text, Formatters.duration(elapsed)),
            action,
        )
        vibrate(appContext, CHECKIN_VIBRATION)
    }

    companion object {
        const val ACTION_ALARM = "com.mikotrack.action.CHECKIN_ALARM"
        const val ACTION_OK = "com.mikotrack.action.CHECKIN_OK"
        private const val REQUEST_OK = 41
        /** Alarms may fire a little early; accept a small drift instead of rescheduling. */
        private const val TOLERANCE_MILLIS = 5 * 60_000L
        private val CHECKIN_VIBRATION = longArrayOf(0, 500, 250, 500, 250, 500)
        private val ACK_VIBRATION = longArrayOf(0, 120)
    }
}
