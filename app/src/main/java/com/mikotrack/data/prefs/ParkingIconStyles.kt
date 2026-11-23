package com.mikotrack.data.prefs

import com.mikotrack.R
import com.mikotrack.data.repository.DefaultCategory

/**
 * The icon styles the user can pick for parking waypoints, and the defaults of the
 * parking marker (the plain car in the default blue of the category).
 */
object ParkingIconStyles {

    const val HOME = "home"
    const val CAR = "car"
    const val BICYCLE = "bicycle"
    const val PICKUP = "pickup"
    const val FLAG = "flag"

    /** Every style key, in the order they are shown in the settings. */
    val ALL = listOf(HOME, CAR, BICYCLE, PICKUP, FLAG)

    const val DEFAULT = CAR

    /** The default badge colour: the one of the parking category. */
    val DEFAULT_COLOR: Long = DefaultCategory.PARKING.color

    fun isKnown(style: String): Boolean = style in ALL

    /** The vector drawable of a style, or `null` for unknown styles. */
    fun drawableRes(style: String): Int? = when (style) {
        HOME -> R.drawable.ic_parking_home
        CAR -> R.drawable.ic_parking_car
        BICYCLE -> R.drawable.ic_parking_bicycle
        PICKUP -> R.drawable.ic_parking_pickup
        FLAG -> R.drawable.ic_parking_flag
        else -> null
    }
}
