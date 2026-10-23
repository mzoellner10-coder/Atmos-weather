package com.atmos.weather.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Provider local timestamps stay unchanged; formatting is a presentation concern. */
fun formatClock(time: LocalTime, twentyFour: Boolean = false): String {
    val pattern = if (twentyFour) "HH:mm" else if (time.minute == 0) "h a" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}
fun forecastTime(iso: String, twentyFour: Boolean = false): String =
    formatClock(LocalDateTime.parse(iso).toLocalTime(), twentyFour)

fun updateTime(epochMillis: Long, zone: String, twentyFour: Boolean = false): String {
    val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(zone))
    return "${local.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))} · ${formatClock(local.toLocalTime(), twentyFour)}"
}
fun alertTime(iso: String, zone: String, twentyFour: Boolean = false): String =
    updateTime(OffsetDateTime.parse(iso).toInstant().toEpochMilli(), zone, twentyFour)

fun daylightProgress(sunrise: String, sunset: String, zone: String, now: Instant = Instant.now()): Float {
    val timezone = ZoneId.of(zone)
    val start = LocalDateTime.parse(sunrise).atZone(timezone).toInstant().toEpochMilli()
    val end = LocalDateTime.parse(sunset).atZone(timezone).toInstant().toEpochMilli()
    if (end <= start) return 0f
    return ((now.toEpochMilli() - start).toDouble() / (end - start)).coerceIn(0.0, 1.0).toFloat()
}

/** Use the forecast's own sunrise/sunset rather than assuming a fixed 6 AM–6 PM day. */
fun isDayAt(forecast: Forecast, iso: String): Boolean = runCatching {
    val time = LocalDateTime.parse(iso)
    val index = forecast.daily.time.indexOf(time.toLocalDate().toString())
    val sunrise = forecast.daily.sunrise.getOrNull(index)?.let(LocalDateTime::parse) ?: return true
    val sunset = forecast.daily.sunset.getOrNull(index)?.let(LocalDateTime::parse) ?: return true
    !time.isBefore(sunrise) && time.isBefore(sunset)
}.getOrDefault(true)
