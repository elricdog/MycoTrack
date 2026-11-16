package com.mikotrack.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.mikotrack.R

/** The groups of the optional controls of the map, listed apart in the settings. */
enum class MapButtonGroup { BUTTON, WIDGET }

/**
 * The optional buttons of the map screen. The user can hide any of them in the
 * settings; START and END are not listed because a trip must always be controllable.
 *
 * [key] is persisted, so it must never change.
 */
enum class MapButton(
    val key: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    /** The status widgets are listed apart from the buttons of the top row. */
    val group: MapButtonGroup = MapButtonGroup.BUTTON,
) {
    LAYERS("layers", R.string.map_button_layers, Icons.Filled.Layers),
    ROUTES("routes", R.string.overlay_routes, Icons.Filled.Route),
    ROUTE_FOCUS("route_focus", R.string.maproute_focus_button, Icons.Filled.TravelExplore),
    CENTRE("centre", R.string.follow_position, Icons.Filled.MyLocation),
    PHOTO_BUBBLES("photo_bubbles", R.string.map_button_photo_bubbles, Icons.Filled.PhotoLibrary),
    VIEW("view", R.string.map_button_view, Icons.AutoMirrored.Filled.ShowChart),
    ZOOM_IN("zoom_in", R.string.map_zoom_in, Icons.Filled.ZoomIn),
    ZOOM_OUT("zoom_out", R.string.map_zoom_out, Icons.Filled.ZoomOut),
    ZOOM_DEFAULT("zoom_default", R.string.map_zoom_default, Icons.Filled.FilterCenterFocus),
    ZOOM_FIT("zoom_fit", R.string.map_zoom_fit, Icons.Filled.FitScreen),
    ZOOM_LEVEL("zoom_level", R.string.map_button_zoom_level, Icons.Filled.Straighten),
    TAKE_PHOTO("take_photo", R.string.take_photo, Icons.Filled.PhotoCamera),
    ADD_WAYPOINT("add_waypoint", R.string.add_waypoint, Icons.Filled.Add),
    STATS("stats", R.string.map_button_stats, Icons.Filled.Insights),
    PLACE_SEARCH("place_search", R.string.map_button_place_search, Icons.Filled.Search),
    CAR("car", R.string.field_map_button_car, Icons.Filled.DirectionsCar),
    GPS_STATUS(
        "gps_status",
        R.string.map_button_gps_status,
        Icons.Filled.GpsFixed,
        MapButtonGroup.WIDGET,
    ),
    HABITAT_LEGEND(
        "habitat_legend",
        R.string.map_button_habitat_legend,
        Icons.Filled.Palette,
        MapButtonGroup.WIDGET,
    ),
    ;

    /** The translated label of the button. */
    val label: String
        @Composable get() = stringResource(labelRes)

    companion object {
        /** The buttons that are visible, given the keys the user hid. */
        fun visible(hiddenKeys: Set<String>): Set<MapButton> =
            entries.filterTo(LinkedHashSet()) { it.key !in hiddenKeys }
    }
}
