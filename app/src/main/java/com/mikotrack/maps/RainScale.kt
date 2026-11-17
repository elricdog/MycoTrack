package com.mikotrack.maps

import kotlin.math.min

/**
 * The colour ramp of the rainfall overlay.
 *
 * The colours depend on the intensity of the rain (accumulated millimetres divided by
 * the length of the window), so the same ramp fits the 3-day and the 30-day windows:
 * 3 mm in 3 days is heavy rain, while 3 mm in 30 days is barely a drizzle.
 */
object RainScale {

    /** Intensity stops of the ramp, in millimetres per day, and their ARGB colours. */
    private val stops = arrayOf(
        0f to 0x00000000,
        0.03f to 0x297FB2E5.toInt(),
        0.1f to 0x594A90D9.toInt(),
        0.3f to 0x802E6FCF.toInt(),
        0.8f to 0xA82E4FBF.toInt(),
        2.0f to 0xC86A2E9E.toInt(),
        4f to 0xE08E2452.toInt(),
    )

    /** The representative intensities of the bands shown in the legend. */
    val legendIntensities = floatArrayOf(0.01f, 0.06f, 0.2f, 0.5f, 1.4f, 3f)

    /** The ARGB colour of an intensity of millimetres per day (linear between stops). */
    fun color(millimetersPerDay: Float): Int {
        val intensity = min(millimetersPerDay, stops.last().first)
        for (i in 1 until stops.size) {
            val (high, highColor) = stops[i]
            if (intensity <= high) {
                val (low, lowColor) = stops[i - 1]
                val t = if (high == low) 0f else (intensity - low) / (high - low)
                return mix(lowColor, highColor, t)
            }
        }
        return stops.last().second
    }

    /** Mixes two ARGB colours, `t` from 0 (fully [from]) to 1 (fully [to]). */
    private fun mix(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int) =
            (from shr shift and 0xFF) + ((to shr shift and 0xFF) - (from shr shift and 0xFF)) * t
        return (channel(24).toInt() shl 24) or (channel(16).toInt() shl 16) or
            (channel(8).toInt() shl 8) or channel(0).toInt()
    }

    /** Qualitative label of an intensity, used at a long-pressed point. */
    fun intensityOf(millimetersPerDay: Float): Level = when {
        millimetersPerDay < 0.05f -> Level.DRY
        millimetersPerDay < 0.2f -> Level.DAMP
        millimetersPerDay < 0.8f -> Level.WET
        else -> Level.VERY_WET
    }

    /** Qualitative bands of the accumulated rain, from dry to torrential. */
    enum class Level { DRY, DAMP, WET, VERY_WET }
}
