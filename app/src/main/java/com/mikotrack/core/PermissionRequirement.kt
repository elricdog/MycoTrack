package com.mikotrack.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.mikotrack.R

/**
 * The runtime permissions MycoTrack needs, together with the explanation that is shown
 * to the user before the system dialog appears.
 *
 * Every entry answers three questions: what the permission unlocks, why it is needed and
 * what happens when it is refused. The order of [ALL] is the order in which they are
 * requested, because Android requires the foreground location permission before the
 * background one can be granted.
 */
enum class PermissionRequirement(
    val id: String,
    @StringRes val titleRes: Int,
    val permissions: List<String>,
    @StringRes val purposeRes: Int,
    @StringRes val rationaleRes: Int,
    @StringRes val consequenceRes: Int,
    val required: Boolean,
) {
    FOREGROUND_LOCATION(
        id = "foreground_location",
        titleRes = R.string.perm_foreground_location_title,
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        purposeRes = R.string.perm_foreground_location_purpose,
        rationaleRes = R.string.perm_foreground_location_rationale,
        consequenceRes = R.string.perm_foreground_location_consequence,
        required = true,
    ),

    NOTIFICATIONS(
        id = "notifications",
        titleRes = R.string.perm_notifications_title,
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
        purposeRes = R.string.perm_notifications_purpose,
        rationaleRes = R.string.perm_notifications_rationale,
        consequenceRes = R.string.perm_notifications_consequence,
        required = true,
    ),

    BACKGROUND_LOCATION(
        id = "background_location",
        titleRes = R.string.perm_background_location_title,
        permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        purposeRes = R.string.perm_background_location_purpose,
        rationaleRes = R.string.perm_background_location_rationale,
        consequenceRes = R.string.perm_background_location_consequence,
        required = true,
    ),

    CAMERA(
        id = "camera",
        titleRes = R.string.perm_camera_title,
        permissions = listOf(Manifest.permission.CAMERA),
        purposeRes = R.string.perm_camera_purpose,
        rationaleRes = R.string.perm_camera_rationale,
        consequenceRes = R.string.perm_camera_consequence,
        required = false,
    ),
    ;

    /**
     * Whether the permission has already been granted.
     *
     * Location accepts either the precise or the approximate variant, so for
     * that entry it is enough that one of the two is granted. The remaining
     * entries hold a single permission each.
     */
    fun isGranted(context: Context): Boolean {
        val granted = permissions.map {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        return if (this == FOREGROUND_LOCATION) granted.any { it } else granted.all { it }
    }

    companion object {
        /** All the permissions, in the order in which they have to be requested. */
        val ALL: List<PermissionRequirement> = listOf(
            FOREGROUND_LOCATION,
            NOTIFICATIONS,
            BACKGROUND_LOCATION,
            CAMERA,
        )

        /** The permissions that are needed to record a trip. */
        val REQUIRED_FOR_RECORDING: List<PermissionRequirement> = ALL.filter { it.required }

        /** The permissions that are missing. */
        fun missing(context: Context): List<PermissionRequirement> =
            ALL.filterNot { it.isGranted(context) }

        /** The permissions that are missing to be able to record a trip. */
        fun missingForRecording(context: Context): List<PermissionRequirement> =
            REQUIRED_FOR_RECORDING.filterNot { it.isGranted(context) }
    }
}
