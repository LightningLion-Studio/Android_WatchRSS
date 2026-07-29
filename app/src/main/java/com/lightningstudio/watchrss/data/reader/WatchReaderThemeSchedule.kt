package com.lightningstudio.watchrss.data.reader

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class WatchThemeScheduleMode {
    FIXED_TIME,
    SUNRISE_SUNSET
}

data class WatchReaderThemeSchedule(
    val mode: WatchThemeScheduleMode = WatchThemeScheduleMode.FIXED_TIME,
    val lightStartMinutes: Int = 7 * 60,
    val darkStartMinutes: Int = 19 * 60,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationUpdatedAt: Long = 0L
)

data class WatchSunTimes(
    val sunrise: LocalTime,
    val sunset: LocalTime
)

internal fun WatchReaderThemeSchedule.isDarkAt(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId
): Boolean {
    val currentMinutes = time.hour * 60 + time.minute
    val (lightMinutes, darkMinutes) = if (
        mode == WatchThemeScheduleMode.SUNRISE_SUNSET &&
        latitude != null &&
        longitude != null
    ) {
        WatchSunCalculator.calculate(date, latitude, longitude, zoneId)?.let {
            (it.sunrise.hour * 60 + it.sunrise.minute) to
                (it.sunset.hour * 60 + it.sunset.minute)
        } ?: (lightStartMinutes to darkStartMinutes)
    } else {
        lightStartMinutes to darkStartMinutes
    }
    return if (darkMinutes >= lightMinutes) {
        currentMinutes >= darkMinutes || currentMinutes < lightMinutes
    } else {
        currentMinutes in darkMinutes until lightMinutes
    }
}

internal object WatchSunCalculator {
    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId
    ): WatchSunTimes? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val sunriseUtc = calculateUtcHour(date, latitude, longitude, sunrise = true)
            ?: return null
        val sunsetUtc = calculateUtcHour(date, latitude, longitude, sunrise = false)
            ?: return null
        val utcStart = date.atStartOfDay(ZoneId.of("UTC"))
        val sunrise = utcStart
            .plusSeconds((sunriseUtc * 3600.0).toLong())
            .withZoneSameInstant(zoneId)
            .toLocalTime()
        val sunset = utcStart
            .plusSeconds((sunsetUtc * 3600.0).toLong())
            .withZoneSameInstant(zoneId)
            .toLocalTime()
        return WatchSunTimes(sunrise = sunrise, sunset = sunset)
    }

    private fun calculateUtcHour(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        sunrise: Boolean
    ): Double? {
        val longitudeHour = longitude / 15.0
        val approximate = date.dayOfYear +
            (((if (sunrise) 6.0 else 18.0) - longitudeHour) / 24.0)
        val meanAnomaly = 0.9856 * approximate - 3.289
        val trueLongitude = normalizeDegrees(
            meanAnomaly +
                1.916 * sinDegrees(meanAnomaly) +
                0.020 * sinDegrees(2 * meanAnomaly) +
                282.634
        )
        var rightAscension = degrees(atan(0.91764 * tan(radians(trueLongitude))))
        rightAscension = normalizeDegrees(rightAscension)
        val longitudeQuadrant = floor(trueLongitude / 90.0) * 90.0
        val ascensionQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + longitudeQuadrant - ascensionQuadrant) / 15.0

        val sinDeclination = 0.39782 * sinDegrees(trueLongitude)
        val cosDeclination = cos(asin(sinDeclination))
        val cosHourAngle = (
            cosDegrees(90.833) -
                sinDeclination * sinDegrees(latitude)
            ) / (cosDeclination * cosDegrees(latitude))
        if (cosHourAngle !in -1.0..1.0) return null
        val hourAngleDegrees = if (sunrise) {
            360.0 - degrees(acos(cosHourAngle))
        } else {
            degrees(acos(cosHourAngle))
        }
        val localMeanTime = hourAngleDegrees / 15.0 +
            rightAscension -
            0.06571 * approximate -
            6.622
        return normalizeHours(localMeanTime - longitudeHour)
    }

    private fun radians(value: Double): Double = value * PI / 180.0
    private fun degrees(value: Double): Double = value * 180.0 / PI
    private fun sinDegrees(value: Double): Double = sin(radians(value))
    private fun cosDegrees(value: Double): Double = cos(radians(value))
    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
    private fun normalizeHours(value: Double): Double = ((value % 24.0) + 24.0) % 24.0
}
