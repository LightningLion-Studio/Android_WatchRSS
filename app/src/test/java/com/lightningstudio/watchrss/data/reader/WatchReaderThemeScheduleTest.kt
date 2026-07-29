package com.lightningstudio.watchrss.data.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WatchReaderThemeScheduleTest {
    @Test
    fun fixedTimeUsesLightIntervalAndDarkOvernight() {
        val schedule = WatchReaderThemeSchedule(
            mode = WatchThemeScheduleMode.FIXED_TIME,
            lightStartMinutes = 7 * 60,
            darkStartMinutes = 19 * 60
        )
        val date = LocalDate.of(2026, 7, 29)
        val zone = ZoneId.of("Asia/Shanghai")

        assertFalse(schedule.isDarkAt(date, LocalTime.of(12, 0), zone))
        assertTrue(schedule.isDarkAt(date, LocalTime.of(22, 0), zone))
        assertTrue(schedule.isDarkAt(date, LocalTime.of(6, 59), zone))
    }

    @Test
    fun sunriseSunsetCalculationProducesPlausibleBeijingTimes() {
        val result = WatchSunCalculator.calculate(
            date = LocalDate.of(2026, 6, 21),
            latitude = 39.9042,
            longitude = 116.4074,
            zoneId = ZoneId.of("Asia/Shanghai")
        )

        assertNotNull(result)
        assertTrue(result!!.sunrise.hour in 4..6)
        assertTrue(result.sunset.hour in 18..20)
    }

    @Test
    fun missingLocationFallsBackToFixedTimes() {
        val schedule = WatchReaderThemeSchedule(
            mode = WatchThemeScheduleMode.SUNRISE_SUNSET,
            lightStartMinutes = 8 * 60,
            darkStartMinutes = 18 * 60
        )

        assertFalse(
            schedule.isDarkAt(
                LocalDate.of(2026, 7, 29),
                LocalTime.of(12, 0),
                ZoneId.of("UTC")
            )
        )
        assertTrue(
            schedule.isDarkAt(
                LocalDate.of(2026, 7, 29),
                LocalTime.of(20, 0),
                ZoneId.of("UTC")
            )
        )
    }
}
