package com.mikotrack.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.mikotrack.MycoTrackApp
import com.mikotrack.core.AppContainer
import com.mikotrack.domain.CheckInPolicy
import kotlinx.coroutines.flow.first

/**
 * Arms and disarms the periodic "are you ok?" check-in of a recorded trip.
 *
 * The alarm is inexact on purpose ([AlarmManager.setAndAllowWhileIdle]): a check-in
 * a few minutes early or late is fine, and this way no exact alarm permission is
 * needed. The due moment is also persisted, so [CheckInReceiver] can tell a genuine
 * check-in from a stray redelivery after the trip was closed.
 */
internal object CheckInAlarm {

    /** Schedules the first check-in of a trip that has just started (or resumed). */
    suspend fun arm(context: Context) {
        val appContext = context.applicationContext
        val container = (appContext as MycoTrackApp).container
        if (!container.settingsStore.checkInEnabled.first()) return
        val minutes = container.settingsStore.checkInMinutes.first()
        val dueAt = CheckInPolicy.deadlineMillis(System.currentTimeMillis(), minutes)
        container.settingsStore.setCheckInDueAt(dueAt)
        schedule(appContext, dueAt)
    }

    /** Postpones the check-in by one interval, after the user said they are ok. */
    suspend fun postpone(context: Context) {
        val appContext = context.applicationContext
        val container = (appContext as MycoTrackApp).container
        val minutes = container.settingsStore.checkInMinutes.first()
        val dueAt = CheckInPolicy.deadlineMillis(System.currentTimeMillis(), minutes)
        container.settingsStore.setCheckInDueAt(dueAt)
        schedule(appContext, dueAt)
    }

    /** Disarms the check-in; called when the trip is closed. */
    suspend fun disarm(context: Context) {
        val appContext = context.applicationContext
        val container = (appContext as MycoTrackApp).container
        container.settingsStore.setCheckInDueAt(0L)
        cancel(appContext)
        SafetyNotifier.cancel(appContext, SafetyNotifier.CHECKIN_NOTIFICATION_ID)
    }

    private fun schedule(context: Context, dueAtMillis: Long) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtMillis, alarmIntent(context)) }
    }

    /** Re-delivers an alarm that fired too early, keeping the original due moment. */
    fun reschedule(context: Context, dueAtMillis: Long) = schedule(context.applicationContext, dueAtMillis)

    private fun cancel(context: Context) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        runCatching { manager.cancel(alarmIntent(context)) }
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_ALARM,
            Intent(context, CheckInReceiver::class.java).setAction(CheckInReceiver.ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val REQUEST_ALARM = 40

    /** Convenience for callers that only hold the container. */
    fun containerOf(context: Context): AppContainer = (context.applicationContext as MycoTrackApp).container
}
