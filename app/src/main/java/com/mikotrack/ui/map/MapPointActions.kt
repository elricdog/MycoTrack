package com.mikotrack.ui.map

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.mikotrack.R
import java.util.Locale

/** Builds the links that open a point of the map in other applications. */
object MapPointLinks {

    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /** The coordinates as `LAT,LON`, always with a dot as the decimal separator. */
    fun coordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

    /** Google Maps walking directions from the current position to the point. */
    fun walkingDirectionsUrl(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/dir/?api=1&destination=${coordinates(latitude, longitude)}&travelmode=walking"

    /** A generic `geo:` link understood by any map application. */
    fun geoUri(latitude: Double, longitude: Double): String {
        val point = coordinates(latitude, longitude)
        return "geo:$point?q=$point"
    }
}

/**
 * Picks the next overlaid route the camera goes to: every tap moves to the following
 * route and, after the last one, back to the position of the user (`null`).
 */
fun nextRouteFocus(current: Long?, routeIds: List<Long>): Long? {
    if (routeIds.isEmpty()) return null
    if (current == null) return routeIds.first()
    val index = routeIds.indexOf(current)
    if (index < 0) return routeIds.first()
    return routeIds.getOrNull(index + 1)
}

/**
 * The trips a waypoint created on the map can be added to: the overlaid ones first, in
 * the order they were switched on, then the most recent ones (at most [recentLimit]).
 */
fun waypointTripCandidates(
    trips: List<com.mikotrack.domain.model.Trip>,
    overlaidIds: List<Long>,
    recentLimit: Int = 30,
): List<com.mikotrack.domain.model.Trip> {
    val byId = trips.associateBy { it.id }
    val overlaid = overlaidIds.distinct().mapNotNull { byId[it] }
    val overlaidSet = overlaid.mapTo(HashSet()) { it.id }
    val recent = trips
        .filter { it.id !in overlaidSet }
        .sortedByDescending { it.startTime }
        .take(recentLimit)
    return overlaid + recent
}

/**
 * Opens Google Maps with walking directions to the point. Without Google Maps, the same
 * link opens in the browser, and as a last resort any map application gets a `geo:` link.
 */
fun openWalkingDirections(context: Context, latitude: Double, longitude: Double) {
    val url = MapPointLinks.walkingDirectionsUrl(latitude, longitude)
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(MapPointLinks.GOOGLE_MAPS_PACKAGE),
        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
        Intent(Intent.ACTION_VIEW, Uri.parse(MapPointLinks.geoUri(latitude, longitude))),
    )
    for (intent in attempts) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next way of opening the point.
        } catch (_: SecurityException) {
            // Try the next way of opening the point.
        }
    }
    Toast.makeText(context, context.getString(R.string.maproute_no_maps_app), Toast.LENGTH_SHORT).show()
}

/** Copies the coordinates of the point to the clipboard. */
fun copyCoordinates(context: Context, latitude: Double, longitude: Double) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val text = MapPointLinks.coordinates(latitude, longitude)
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.maproute_coordinates), text))
    // Android 13 and later confirm the copy on their own.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.maproute_coordinates_copied), Toast.LENGTH_SHORT).show()
    }
}
