package com.mikotrack.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Creates the small bitmaps used as MapLibre style images.
 *
 * Generating them at runtime keeps the application free of binary assets and lets the
 * markers adopt the colour of the waypoint category.
 */
object MapIconFactory {

    /** Identifier of the icon used for the direction arrow. */
    const val BEARING_ICON = "mycotrack-bearing"

    /** The identifier of the style image that represents a waypoint category. */
    fun waypointIconId(category: String): String = "mycotrack-wp-${category.lowercase()}"

    /** Builds the coloured badge used for a waypoint of the given colour. */
    fun waypointIcon(color: Long): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center - 2f, paint)

        paint.color = (color and 0xFFFFFFFFL).toInt()
        canvas.drawCircle(center, center, center - 6f, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(center, center, center - 10f, paint)
        paint.style = Paint.Style.FILL

        // A small stem turns the circle into a mushroom-like marker.
        paint.color = Color.WHITE
        canvas.drawRoundRect(center - 4f, center - 1f, center + 4f, center + 9f, 3f, 3f, paint)

        return bitmap
    }

    /** Builds the arrow that shows the direction of movement. */
    fun bearingIcon(color: Int = Color.WHITE): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        paint.style = Paint.Style.FILL

        val path = Path().apply {
            moveTo(size / 2f, 4f)
            lineTo(size - 12f, size - 14f)
            lineTo(size / 2f, size - 24f)
            lineTo(12f, size - 14f)
            close()
        }
        canvas.drawPath(path, paint)

        paint.color = Color.argb(90, 0, 0, 0)
        paint.setShadowLayer(4f, 0f, 2f, Color.BLACK)
        canvas.drawPath(path, paint)

        return bitmap
    }
}
