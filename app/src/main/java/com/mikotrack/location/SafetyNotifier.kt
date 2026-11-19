package com.mikotrack.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mikotrack.MainActivity
import com.mikotrack.R

/**
 * Posts the warnings shown while a trip is being recorded: sunset, battery and the
 * "are you ok?" check-in all share one notification channel, so the user manages
 * them together in the system settings.
 */
internal object SafetyNotifier {

    const val CHANNEL_ID = "mycotrack_safety"
    const val SUNSET_NOTIFICATION_ID = 4713
    const val BATTERY_NOTIFICATION_ID = 4714
    const val CHECKIN_NOTIFICATION_ID = 4715

    /** Posts one high importance warning, optionally with extra action buttons. */
    fun warn(context: Context, id: Int, title: String, text: String, vararg actions: NotificationCompat.Action) {
        val appContext = context.applicationContext
        createChannel(appContext)
        val notification = baseBuilder(appContext, title, text)
            .apply { actions.forEach { addAction(it) } }
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(id, notification) }
    }

    /** Removes one of the warnings, for instance after the user confirmed the check-in. */
    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(id) }
    }

    /** Whether notifications may be posted at all on this device right now. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** The PendingIntent that opens the application from a warning. */
    fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context.applicationContext,
            requestCode,
            Intent(context.applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun baseBuilder(context: Context, title: String, text: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, REQUEST_OPEN))

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.field_safety_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.field_safety_channel_description)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private const val REQUEST_OPEN = 21
}
