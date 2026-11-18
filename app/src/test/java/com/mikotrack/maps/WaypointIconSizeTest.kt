package com.mikotrack.maps

import org.junit.Assert.assertTrue
import org.junit.Test

class WaypointIconSizeTest {
    @Test
    fun `parking marker has a larger size and other categories keep their size`() {
        val expected = arrayOf(
            "match",
            arrayOf("string", arrayOf("get", "category")),
            "PARKING",
            1.15f,
            0.7f,
        )
        val actual = waypointIconSizeExpression().toArray()
        assertTrue("Unexpected MapLibre expression: ${actual.contentDeepToString()}", expected.contentDeepEquals(actual))
    }
}
