package com.mikotrack.ui.mushrooms

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {

    @Test
    fun `fit keeps the aspect ratio inside the container`() {
        assertEquals(1000f to 750f, ZoomMath.fitSize(400f, 300f, 1000f, 2000f))
        assertEquals(500f to 1000f, ZoomMath.fitSize(300f, 600f, 1000f, 1000f))
        assertEquals(1000f to 2000f, ZoomMath.fitSize(0f, 0f, 1000f, 2000f))
    }

    @Test
    fun `offset is clamped to the edges of the zoomed image`() {
        // A 1000 px wide image zoomed 2x overflows a 1000 px container by 500 px on each side.
        assertEquals(500f, ZoomMath.clampOffset(800f, 1000f, 2f, 1000f), 0f)
        assertEquals(-500f, ZoomMath.clampOffset(-800f, 1000f, 2f, 1000f), 0f)
        assertEquals(120f, ZoomMath.clampOffset(120f, 1000f, 2f, 1000f), 0f)
        // Not zoomed, or smaller than the container: it stays centred.
        assertEquals(0f, ZoomMath.clampOffset(300f, 1000f, 1f, 1000f), 0f)
        assertEquals(0f, ZoomMath.clampOffset(300f, 750f, 1.2f, 2000f), 0f)
    }

    @Test
    fun `zooming keeps the focus point in place`() {
        val focus = 200f
        val offset = ZoomMath.zoomOffset(0f, focus, 1f, 2.5f)
        // The image point under the focus at scale 1 is 200 px from the centre.
        assertEquals(focus, 200f * 2.5f + offset, 1e-4f)

        val back = ZoomMath.zoomOffset(offset, focus, 2.5f, 1f)
        assertEquals(0f, back, 1e-4f)
    }
}
