package com.atmos.weather

import com.atmos.weather.data.*
import com.atmos.weather.ui.aqiCategory
import com.atmos.weather.ui.uvCategory
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RedesignTest {
    @Test fun twelveHourExamplesAndOptInMilitaryFormat() {
        mapOf("15:00" to "3 PM","16:00" to "4 PM","17:00" to "5 PM","09:00" to "9 AM","12:00" to "12 PM","00:00" to "12 AM","18:30" to "6:30 PM").forEach { (input,expected) ->
            assertEquals(expected,forecastTime("2026-10-23T$input"))
        }
        assertEquals("18:30",forecastTime("2026-10-23T18:30",true))
    }
    @Test fun updateTimesUseSelectedCityAndDstTransitions() {
        val epoch=Instant.parse("2026-07-01T18:30:00Z").toEpochMilli()
        assertEquals("Jul 1 · 1:30 PM",updateTime(epoch,"America/Chicago"))
        assertEquals("Jul 2 · 3:30 AM",updateTime(epoch,"Asia/Tokyo"))
        assertEquals("Mar 8 · 1:30 AM",updateTime(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),"America/Chicago"))
        assertEquals("Mar 8 · 3:30 AM",updateTime(Instant.parse("2026-03-08T08:30:00Z").toEpochMilli(),"America/Chicago"))
        assertEquals("Nov 1 · 1:30 AM",updateTime(Instant.parse("2026-11-01T06:30:00Z").toEpochMilli(),"America/Chicago"))
        assertEquals("Nov 1 · 1:30 AM",updateTime(Instant.parse("2026-11-01T07:30:00Z").toEpochMilli(),"America/Chicago"))
        assertEquals("Oct 23 · 2 PM",alertTime("2026-10-23T19:00:00+00:00","America/Chicago"))
    }
    @Test fun daylightUsesLocationAndClampsOutsideSunriseSunset() {
        val rise="2026-10-23T06:00";val set="2026-10-23T18:00";val zone="America/Chicago"
        assertEquals(.5f,daylightProgress(rise,set,zone,Instant.parse("2026-10-23T17:00:00Z")),.001f)
        assertEquals(0f,daylightProgress(rise,set,zone,Instant.parse("2026-10-23T10:00:00Z")),.001f)
        assertEquals(1f,daylightProgress(rise,set,zone,Instant.parse("2026-10-24T01:00:00Z")),.001f)
    }
    @Test fun legacySettingsRetainLocationsUnitsAndDisabledMotion() {
        val settings=SettingsCodec.decode("""{"fahrenheit":true,"mph":true,"inches":true,"theme":"True OLED Black","motion":"Off","refresh":false,"followLocation":true,"cities":[{"name":"Chicago","latitude":41.88,"longitude":-87.63,"country_code":"US"}],"selected":0}""")
        assertTrue(settings.fahrenheit);assertTrue(settings.mph);assertTrue(settings.inches)
        assertEquals("Chicago",settings.city!!.name);assertTrue(settings.followLocation);assertFalse(settings.refresh)
        assertEquals("OLED Black",settings.visualTheme);assertEquals("Minimal",settings.motionMode)
        assertFalse(settings.time24);assertFalse(settings.sounds);assertFalse(settings.haptics)
        assertEquals("Balanced",SettingsCodec.decode("""{"motion":"Subtle","theme":"Hybrid OLED"}""").motionMode)
        assertEquals("Hybrid",SettingsCodec.decode("""{"theme":"Hybrid OLED"}""").visualTheme)
    }
    @Test fun forecastArtworkFollowsActualSolarTimes() {
        val f=fixtureWeather().forecast
        assertTrue(isDayAt(f,"2026-10-23T06:00"))
        assertFalse(isDayAt(f,"2026-10-23T05:59"))
        assertFalse(isDayAt(f,"2026-10-23T18:00"))
    }
    @Test fun aqiAndUvThresholdsAreNotInventedCategories() {
        assertEquals("Unavailable",aqiCategory(null));assertEquals("Good",aqiCategory(50.0));assertEquals("Moderate",aqiCategory(51.0))
        assertEquals("Sensitive groups",aqiCategory(150.0));assertEquals("Hazardous",aqiCategory(301.0))
        assertEquals("Low",uvCategory(2.9));assertEquals("Moderate",uvCategory(3.0));assertEquals("Extreme",uvCategory(11.0))
    }
}
