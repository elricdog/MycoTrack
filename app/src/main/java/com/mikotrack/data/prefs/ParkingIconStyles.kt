package com.mikotrack.data.prefs

import com.mikotrack.R
import com.mikotrack.data.repository.DefaultCategory

/**
 * The icon styles the user can pick for parking waypoints, and the defaults of the
 * parking marker (the plain car in the default blue of the category).
 */
object ParkingIconStyles {

    const val CAR = "car"
    const val MOTORCYCLE = "motorcycle"
    const val BICYCLE = "bicycle"
    const val CAMPERVAN = "campervan"
    const val GARAGE = "garage"

    /** Every style key, in the order they are shown in the settings. */
    val ALL = listOf(CAR, MOTORCYCLE, BICYCLE, CAMPERVAN, GARAGE)

    const val DEFAULT = CAR

    /** The default badge colour: the one of the parking category. */
    val DEFAULT_COLOR: Long = DefaultCategory.PARKING.color

    fun isKnown(style: String): Boolean = style in ALL

    /** The vector drawable of a style, or `null` for unknown styles. */
    fun drawableRes(style: String): Int? = when (style) {
        CAR -> R.drawable.ic_parking_car
        MOTORCYCLE -> R.drawable.ic_parking_motorcycle
        BICYCLE -> R.drawable.ic_parking_bicycle
        CAMPERVAN -> R.drawable.ic_parking_campervan
        GARAGE -> R.drawable.ic_parking_garage
        else -> null
    }
}
