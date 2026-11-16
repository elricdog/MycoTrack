package com.mikotrack.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil

/**
 * Renders the name of a route as a bitmap: a rounded pill in the colour of the route
 * with white bold text and a small pointer at the bottom centre.
 *
 * The styles of the map have no glyphs, so text cannot be drawn by MapLibre itself; an
 * icon also keeps the same size on the screen at every zoom level.
 */
object RouteLabelFactory {

    private const val TEXT_SP = 13f
    private const val PADDING_H_DP = 9f
    private const val PADDING_V_DP = 4f
    private const val OUTLINE_DP = 1.5f
    private const val POINTER_HEIGHT_DP = 6f
    private const val POINTER_HALF_WIDTH_DP = 5f
    private const val SHADOW_DP = 2.5f
    private const val MAX_CHARS = 32

    /** The identifier of the style image of a route label. */
    fun imageId(tripId: Long, name: String, color: Int): String =
        "mycotrack-route-label-$tripId-${(name to color).hashCode().toUInt()}"

    /** Builds the label of a route. It is cheap enough to run on the main thread. */
    fun create(name: String, color: Int, density: Float): Bitmap {
        val text = if (name.length > MAX_CHARS) name.take(MAX_CHARS - 1).trimEnd() + "\u2026" else name
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = TEXT_SP * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val haloPaint = Paint(textPaint).apply {
            this.color = Color.argb(170, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeJoin = Paint.Join.ROUND
        }

        val paddingH = PADDING_H_DP * density
        val paddingV = PADDING_V_DP * density
        val outline = OUTLINE_DP * density
        val pointerHeight = POINTER_HEIGHT_DP * density
        val pointerHalfWidth = POINTER_HALF_WIDTH_DP * density
        val shadow = SHADOW_DP * density

        val metrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(text)
        val textHeight = metrics.descent - metrics.ascent
        val bodyWidth = textWidth + 2 * paddingH
        val bodyHeight = textHeight + 2 * paddingV

        val width = ceil(bodyWidth + 2 * (shadow + outline)).toInt()
        val height = ceil(bodyHeight + pointerHeight + 2 * (shadow + outline)).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val inset = shadow + outline
        val body = RectF(inset, inset, inset + bodyWidth, inset + bodyHeight)
        val centerX = width / 2f
        val tipY = (height - shadow).coerceAtLeast(body.bottom + 1f)
        val corner = bodyHeight / 2
        val shape = Path().apply {
            addRoundRect(body, corner, corner, Path.Direction.CW)
            moveTo(centerX - pointerHalfWidth, body.bottom - 1f)
            lineTo(centerX, tipY)
            lineTo(centerX + pointerHalfWidth, body.bottom - 1f)
            close()
        }

        // The dark outline (with a soft shadow) separates the label from any base map.
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.argb(220, 20, 20, 20)
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = outline * 2
                strokeJoin = Paint.Join.ROUND
                setShadowLayer(shadow, 0f, shadow / 2f, Color.argb(120, 0, 0, 0))
            },
        )
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color or 0xFF000000.toInt()
                style = Paint.Style.FILL
            },
        )

        val baseline = body.top + paddingV - metrics.ascent
        val textX = body.left + paddingH
        canvas.drawText(text, textX, baseline, haloPaint)
        canvas.drawText(text, textX, baseline, textPaint)
        return bitmap
    }
}
