package com.mikotrack.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Finds the activity that hosts this context, when there is one.
 *
 * Some Android APIs, like [androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale],
 * only work with an activity. In a Compose hierarchy the context is usually a
 * `ContextWrapper` around the activity, so it has to be unwrapped.
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
