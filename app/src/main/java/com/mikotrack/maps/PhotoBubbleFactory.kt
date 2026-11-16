package com.mikotrack.maps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt

/**
 * Renders the "speech bubble" that shows the thumbnail of a photograph on the map.
 *
 * The bubble is a framed square thumbnail with a pointer at the bottom centre. The tip
 * of the pointer is the anchor of the map symbol, so it sits exactly on the place where
 * the photograph was taken.
 */
object PhotoBubbleFactory {

    /** Size of the thumbnail inside the bubble, in density independent pixels. */
    private const val THUMBNAIL_DP = 56f
    private const val FRAME_DP = 3f
    private const val CORNER_DP = 9f
    private const val POINTER_HEIGHT_DP = 10f
    private const val POINTER_HALF_WIDTH_DP = 8f
    private const val SHADOW_DP = 3f

    /** The identifier of the style image of a waypoint bubble. */
    fun imageId(waypointId: Long): String = "mycotrack-photo-bubble-$waypointId"

    /**
     * Builds the bubble of the photograph at [path], or `null` when the file cannot be
     * read. It decodes the image, so it must not run on the main thread.
     */
    fun create(path: String, frameColor: Int, density: Float): Bitmap? {
        val thumbnailPx = (THUMBNAIL_DP * density).roundToInt()
        val photo = decodeSquare(path, thumbnailPx) ?: return null

        val frame = FRAME_DP * density
        val corner = CORNER_DP * density
        val pointerHeight = POINTER_HEIGHT_DP * density
        val pointerHalfWidth = POINTER_HALF_WIDTH_DP * density
        val shadow = SHADOW_DP * density

        val bodySize = thumbnailPx + 2 * frame
        val width = (bodySize + 2 * shadow).roundToInt()
        val height = (bodySize + pointerHeight + 2 * shadow).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val left = shadow
        val top = shadow
        val body = RectF(left, top, left + bodySize, top + bodySize)
        val centerX = width / 2f
        val tipY = height - shadow

        // The body and the pointer are a single path, so the frame has no seam.
        val outline = Path().apply {
            addRoundRect(body, corner, corner, Path.Direction.CW)
            moveTo(centerX - pointerHalfWidth, body.bottom - 1f)
            lineTo(centerX, tipY)
            lineTo(centerX + pointerHalfWidth, body.bottom - 1f)
            close()
        }

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = frameColor
            setShadowLayer(shadow, 0f, shadow / 2f, Color.argb(140, 0, 0, 0))
        }
        canvas.drawPath(outline, framePaint)

        val photoRect = RectF(
            body.left + frame,
            body.top + frame,
            body.right - frame,
            body.bottom - frame,
        )
        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(photoRect.left, photoRect.top) })
            }
        }
        val innerCorner = (corner - frame).coerceAtLeast(2f)
        canvas.drawRoundRect(photoRect, innerCorner, innerCorner, photoPaint)
        photo.recycle()
        return bitmap
    }

    /** Decodes the photograph as a centred square of [size] pixels, upright. */
    private fun decodeSquare(path: String, size: Int): Bitmap? = runCatching {
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val rotation = when (
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val side = minOf(decoded.width, decoded.height)
        val scale = size.toFloat() / side
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
            matrix,
            true,
        )
        if (square !== decoded) decoded.recycle()
        // Rounding can leave the result one pixel off; the shader expects the exact size.
        if (square.width == size && square.height == size) {
            square
        } else {
            Bitmap.createScaledBitmap(square, size, size, true).also {
                if (it !== square) square.recycle()
            }
        }
    }.getOrNull()
}
