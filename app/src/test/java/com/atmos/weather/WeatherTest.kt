package com.atmos.weather

import com.atmos.weather.data.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class WeatherTest {
 @Test fun hourlySelectionUsesCityTimezoneAndNeverWrapsExpiredForecast() {
 val hours=listOf("2026-10-23T18:00","2026-10-23T19:00","2026-10-23T20:00")
 assertEquals(1,forecastHourIndex(hours,"America/Chicago",java.time.Instant.parse("2026-10-24T00:00:00Z")))
 assertEquals(2,forecastHourIndex(hours,"America/Chicago",java.time.Instant.parse("2026-10-25T00:00:00Z")))
 }
 @Test fun cacheFreshnessBoundariesAndClockChanges() {
 assertTrue(reusableCache(1000,30999,true))
 assertFalse(reusableCache(1000,31000,true))
 assertTrue(reusableCache(1000,900999,false))
 assertFalse(reusableCache(1000,901000,false))
 assertFalse(reusableCache(1000,999,false))
 }
 @Test fun newSettingsHaveNoInventedLocation() { assertNull(Settings().city) }
 @Test fun temperatureConversions() {
 assertEquals("32°", temperature(0.0,true))
 assertEquals("212°", temperature(100.0,true))
 assertEquals("-40°", temperature(-40.0,true))
 assertEquals("21°", temperature(21.4,false))
 }
 @Test fun wmoConditionsCoverSevereAndUnknown() {
 assertEquals("Thunderstorms",condition(95))
 assertEquals("Snow",condition(73))
 assertEquals("Unknown conditions",condition(1000))
 }
 @Test fun parseForecastRetainsProviderValues() {
 val forecast=Gson().fromJson("""{
 "timezone":"America/Chicago",
 "current":{"temperature_2m":12.3,"apparent_temperature":10.4,"weather_code":61,"relative_humidity_2m":82,"precipitation":0.5,"wind_speed_10m":15,"wind_direction_10m":240,"wind_gusts_10m":28,"surface_pressure":1007,"is_day":1},
 "hourly":{"time":["2026-10-23T12:00"],"temperature_2m":[13.2],"precipitation_probability":[75],"precipitation":[1.2],"weather_code":[61],"visibility":[10000],"dew_point_2m":[9],"cloud_cover":[92]},
 "daily":{"time":["2026-10-23"],"temperature_2m_max":[16],"temperature_2m_min":[8],"weather_code":[61],"sunrise":["2026-10-23T07:15"],"sunset":["2026-10-23T18:00"],"daylight_duration":[38700],"uv_index_max":[3],"precipitation_sum":[4.6]}
 }""",Forecast::class.java)
 assertEquals(12.3,forecast.current.temperature_2m,0.001)
 assertEquals(75.0,forecast.hourly.precipitation_probability.single(),0.001)
 assertEquals(4.6,forecast.daily.precipitation_sum.single(),0.001)
 assertEquals("America/Chicago",forecast.timezone)
 }
 @Test fun parseGeocodingAndMissingAirQuality() {
 val result=Gson().fromJson("""{"results":[{"name":"Berlin","latitude":52.52,"longitude":13.41,"country_code":"DE","admin1":"Berlin"}]}""",SearchResult::class.java)
 assertEquals("DE",result.results.single().country_code)
 val air=Gson().fromJson("""{"current":{"us_aqi":null,"pm2_5":4.3,"pm10":8.1}}""",Air::class.java)
 assertNull(air.current?.us_aqi)
 assertEquals(4.3,air.current!!.pm2_5!!,0.001)
 }
}
