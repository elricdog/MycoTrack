package com.mikotrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Tests of the NOAA sunrise and sunset algorithm against published times. */
class SunCalculatorTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val barcelonaLat = 41.39
    private val barcelonaLon = 2.17

    private fun localTime(millis: Long?, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(millis!!).atZone(zone).toLocalTime()

    private fun assertNear(expected: LocalTime, actual: LocalTime, toleranceMinutes: Int = 3) {
        val difference = Math.abs(actual.toSecondOfDay() - expected.toSecondOfDay()) / 60.0
        assertEquals("expected $expected but was $actual", 0.0, difference, toleranceMinutes.toDouble())
    }

    @Test
    fun `summer solstice sunset in Barcelona`() {
        val sunset = SunCalculator.sunsetUtcMillis(LocalDate.of(2026, 6, 21), barcelonaLat, barcelonaLon)
        assertNear(LocalTime.of(21, 29), localTime(sunset, madrid))
    }

    @Test
    fun `winter solstice sunset in Barcelona`() {
        // timeanddate.com lists 17:25 CET for Barcelona on 2026-12-21.
        val sunset = SunCalculator.sunsetUtcMillis(LocalDate.of(2026, 12, 21), barcelonaLat, barcelonaLon)
        assertNear(LocalTime.of(17, 25), localTime(sunset, madrid))
    }

    @Test
    fun `winter solstice sunrise in Barcelona`() {
        val sunrise = SunCalculator.sunriseUtcMillis(LocalDate.of(2026, 12, 21), barcelonaLat, barcelonaLon)
        assertNear(LocalTime.of(8, 14), localTime(sunrise, madrid))
    }

    @Test
    fun `western longitude sunset in New York`() {
        val sunset = SunCalculator.sunsetUtcMillis(LocalDate.of(2026, 6, 21), 40.71, -74.01)
        assertNear(LocalTime.of(20, 31), localTime(sunset, ZoneId.of("America/New_York")))
    }

    @Test
    fun `there is no sunset during the polar day`() {
        assertNull(SunCalculator.sunsetUtcMillis(LocalDate.of(2026, 6, 21), 78.22, 15.65))
    }
}
