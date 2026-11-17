package com.mikotrack.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.mikotrack.R

/** The main sections of the application. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    MAP("map", R.string.nav_map, Icons.Filled.Map),
    HISTORY("history", R.string.nav_history, Icons.Filled.History),
    GALLERY("gallery", R.string.nav_gallery, Icons.Filled.PhotoLibrary),
    MUSHROOMS("mushrooms", R.string.nav_mushrooms, Icons.Filled.Spa),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

/** The routes of the application. */
object Routes {
    const val TRIP_DETAIL = "trip/{tripId}"

    /** The screen that explains and requests the runtime permissions. */
    const val PERMISSIONS = "permissions"

    /** The GPS and sensors diagnostics screen. */
    const val DIAGNOSTICS = "diagnostics"

    /** Builds the route that opens the detail of a trip. */
    fun tripDetail(tripId: Long): String = "trip/$tripId"

    /** The detail of a species of the mushroom guide. */
    const val MUSHROOM_DETAIL = "mushroom/{id}"

    /** Builds the route that opens the detail of a species of the mushroom guide. */
    fun mushroomDetail(id: String): String = "mushroom/$id"

    /** The easter egg games hub, unlocked from the About section of the settings. */
    const val GAMES = "games"

    /** One of the easter egg games. */
    const val GAME = "games/{game}"

    /** Builds the route that opens one of the easter egg games. */
    fun game(game: String): String = "games/$game"
}
