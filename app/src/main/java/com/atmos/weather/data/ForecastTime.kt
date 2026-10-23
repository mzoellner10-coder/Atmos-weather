package com.atmos.weather.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun forecastHourIndex(times: List<String>, timezone: String, instant: Instant = Instant.now()): Int {
 val local = LocalDateTime.ofInstant(instant, ZoneId.of(timezone))
 val next = times.indexOfFirst { LocalDateTime.parse(it) >= local }
 return if(next >= 0) next else times.lastIndex.coerceAtLeast(0)
}
fun reusableCache(fetchedAt: Long, now: Long, force: Boolean): Boolean {
 val age = now - fetchedAt
 return age in 0 until if(force) 30_000L else 900_000L
}
