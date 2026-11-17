package com.mikotrack.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.mikotrack.data.prefs.ParkingIconStyles
import com.mikotrack.data.repository.DefaultCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates the small bitmaps used as MapLibre style images.
 *
 * Generating them at runtime keeps the application free of binary assets and lets the
 * markers adopt the colour of the waypoint category.
 */
object MapIconFactory {

    /** Identifier of the icon used for the direction arrow. */
    const val BEARING_ICON = "mycotrack-bearing"

    /**
     * The icon style of the parking badge, set from the settings. It takes part in the
     * identifier of the parking icon, so a new style gets its own style image.
     */
    @Volatile
    var parkingIconStyle: String = ParkingIconStyles.DEFAULT

    /** The identifier of the style image that represents a waypoint category. */
    fun waypointIconId(category: String): String = if (category == DefaultCategory.PARKING.key) {
        "mycotrack-wp-parking-$parkingIconStyle"
    } else {
        "mycotrack-wp-${category.lowercase()}"
    }

    /** Builds the coloured badge used for a waypoint of the given colour. */
    fun waypointIcon(context: Context, color: Long, glyphStyle: String? = null): Bitmap {
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

        val glyph = glyphStyle?.let { parkingGlyph(context, it) }
        if (glyph != null) {
            val glyphSize = (size * 0.52f).toInt()
            canvas.drawBitmap(glyph, center - glyphSize / 2f, center - glyphSize / 2f - 1f, paint)
        } else {
            // A small stem turns the circle into a mushroom-like marker.
            paint.color = Color.WHITE
            canvas.drawRoundRect(center - 4f, center - 1f, center + 4f, center + 9f, 3f, 3f, paint)
        }

        return bitmap
    }

    /** The white glyph of a parking style, rendered once per style. */
    private val glyphs = ConcurrentHashMap<String, Bitmap?>()

    private fun parkingGlyph(context: Context, style: String): Bitmap? =
        glyphs.getOrPut(style) {
            runCatching {
                ParkingIconStyles.drawableRes(style)?.let { res ->
                    val drawable = context.getDrawable(res) ?: return@let null
                    drawable.setTint(Color.WHITE)
                    val bitmap = Bitmap.createBitmap(GLYPH_SIZE, GLYPH_SIZE, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, GLYPH_SIZE, GLYPH_SIZE)
                    drawable.draw(canvas)
                    bitmap
                }
            }.getOrNull()
        }

    private const val GLYPH_SIZE = 26

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
