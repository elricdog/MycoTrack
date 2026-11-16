package com.mikotrack.core

import com.mikotrack.core.CoordinateParser.Error
import com.mikotrack.core.CoordinateParser.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateParserTest {

    private fun assertParsed(input: String, latitude: Double, longitude: Double) {
        val result = CoordinateParser.parse(input)
        assertTrue("$input -> $result", result is Result.Success)
        result as Result.Success
        assertEquals(input, latitude, result.latitude, 1e-5)
        assertEquals(input, longitude, result.longitude, 1e-5)
    }

    private fun assertError(input: String, error: Error) {
        assertEquals(input, Result.Failure(error), CoordinateParser.parse(input))
    }

    @Test
    fun decimalWithCommaOrSpaces() {
        assertParsed("41.61, 2.29", 41.61, 2.29)
        assertParsed("41.61,2.29", 41.61, 2.29)
        assertParsed("  41.61   2.29 ", 41.61, 2.29)
        assertParsed("-33.9249; 18.4241", -33.9249, 18.4241)
        assertParsed("41.61 -2.29", 41.61, -2.29)
    }

    @Test
    fun decimalCommaAsDecimalSeparator() {
        assertParsed("41,61 2,29", 41.61, 2.29)
        assertParsed("41,2", 41.0, 2.0)
    }

    @Test
    fun degreesMinutesSeconds() {
        val latitude = 41 + 36 / 60.0 + 44 / 3600.0
        val longitude = 2 + 17 / 60.0 + 25 / 3600.0
        assertParsed("41°36'44\"N 2°17'25\"E", latitude, longitude)
        assertParsed("41° 36′ 44″ N, 2° 17′ 25″ E", latitude, longitude)
        assertParsed("41º36'44''N 2º17'25''E", latitude, longitude)
        assertParsed("41 36 44 N 2 17 25 E", latitude, longitude)
        assertParsed("41°36'44\" 2°17'25\"", latitude, longitude)
        assertParsed("41 36 44 2 17 25", latitude, longitude)
    }

    @Test
    fun hemispheresSetSignsAndOrder() {
        assertParsed("2°17'25\"W 41°36'44\"S", -(41 + 36 / 60.0 + 44 / 3600.0), -(2 + 17 / 60.0 + 25 / 3600.0))
        assertParsed("N 41° 36.5' O 2° 17.5'", 41 + 36.5 / 60.0, -(2 + 17.5 / 60.0))
        assertParsed("41.61N 2.29E", 41.61, 2.29)
        assertParsed("41.61 n 3.7 w", 41.61, -3.7)
    }

    @Test
    fun rejectsInvalidInput() {
        assertError("", Error.EMPTY)
        assertError("   ", Error.EMPTY)
        assertError("hello", Error.FORMAT)
        assertError("41.61", Error.FORMAT)
        assertError("1 2 3", Error.FORMAT)
        assertError("41.6N 2.2N", Error.FORMAT)
        assertError("91, 2", Error.LATITUDE_RANGE)
        assertError("41, 181", Error.LONGITUDE_RANGE)
        assertError("41°61'0\"N 2°0'0\"E", Error.MINUTES_SECONDS_RANGE)
        assertError("41°30'75\"N 2°0'0\"E", Error.MINUTES_SECONDS_RANGE)
        assertError("41.5°30'N 2°E", Error.FORMAT)
    }
}
