package com.mikotrack.ui.mushrooms

import kotlin.math.max
import kotlin.math.min

/**
 * The geometry of the zoomable photographs. The image is drawn with `ContentScale.Fit`
 * and transformed with a graphics layer around the centre of the container, so a point
 * at distance `d` from the centre is shown at `d * scale + offset`.
 */
internal object ZoomMath {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 5f
    const val DOUBLE_TAP_SCALE = 2.5f

    /** The size of a [contentWidth] x [contentHeight] image fitted inside the container. */
    fun fitSize(contentWidth: Float, contentHeight: Float, containerWidth: Float, containerHeight: Float): Pair<Float, Float> {
        if (contentWidth <= 0f || contentHeight <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
            return containerWidth to containerHeight
        }
        val ratio = min(containerWidth / contentWidth, containerHeight / contentHeight)
        return contentWidth * ratio to contentHeight * ratio
    }

    /** Keeps the edges of the zoomed image from leaving the container along one axis. */
    fun clampOffset(offset: Float, fitted: Float, scale: Float, container: Float): Float {
        val limit = max(0f, (fitted * scale - container) / 2f)
        return offset.coerceIn(-limit, limit)
    }

    /**
     * The offset along one axis that keeps the point under [focus] (measured from the centre
     * of the container) in place when the scale changes from [oldScale] to [newScale].
     */
    fun zoomOffset(offset: Float, focus: Float, oldScale: Float, newScale: Float): Float {
        val factor = newScale / oldScale
        return focus * (1f - factor) + offset * factor
    }
}
