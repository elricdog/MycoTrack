package com.mikotrack.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Runtime permissions required by MycoTrack and small helpers to check them. */
object Permissions {

    val FOREGROUND_LOCATION = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val BACKGROUND_LOCATION = arrayOf(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    )

    val NOTIFICATIONS = arrayOf(
        Manifest.permission.POST_NOTIFICATIONS,
    )

    val CAMERA = arrayOf(
        Manifest.permission.CAMERA,
    )

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasForegroundLocation(context: Context): Boolean =
        FOREGROUND_LOCATION.any { isGranted(context, it) }

    fun hasBackgroundLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasNotifications(context: Context): Boolean =
        isGranted(context, Manifest.permission.POST_NOTIFICATIONS)

    fun hasCamera(context: Context): Boolean = isGranted(context, Manifest.permission.CAMERA)
}
