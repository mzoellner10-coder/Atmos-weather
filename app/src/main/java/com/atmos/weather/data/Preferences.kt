package com.atmos.weather.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("atmos_preferences")
data class Settings(val fahrenheit: Boolean = false, val mph: Boolean = false, val inches: Boolean = false,
 val followLocation: Boolean = false, val theme: String = "Hybrid", val motion: String = "Balanced", val refresh: Boolean = true,
 val cities: List<City> = emptyList(), val selected: Int = 0,
 val time24: Boolean = false, val sounds: Boolean = false, val haptics: Boolean = false, val ambience: Boolean = true) {
 val city: City? get() = cities.getOrNull(selected)
 val visualTheme: String get() = when(theme) {
  "Dynamic Weather", "Dynamic" -> "Dynamic"
  "True OLED Black", "OLED Black" -> "OLED Black"
  else -> "Hybrid"
 }
 val motionMode: String get() = when(motion) { "Full" -> "Full"; "Off", "Minimal" -> "Minimal"; else -> "Balanced" }
}
/** Merge additive defaults before decoding so upgrades never reset v1 settings. */
object SettingsCodec {
 private val gson = Gson()
 fun decode(raw: String?): Settings = runCatching {
  if(raw.isNullOrBlank()) return Settings()
  val merged = gson.toJsonTree(Settings()).asJsonObject
  JsonParser.parseString(raw).asJsonObject.entrySet().forEach { (key, value) ->
   if(!value.isJsonNull && merged.has(key)) merged.add(key, value)
  }
  gson.fromJson(merged, Settings::class.java)
 }.getOrElse { Settings() }
}
class Preferences(private val context: Context) {
 private val key = stringPreferencesKey("settings")
 private val gson = Gson()
 val flow = context.store.data.map { SettingsCodec.decode(it[key]) }
 suspend fun update(transform: (Settings) -> Settings) { context.store.edit { p ->
 val previous = SettingsCodec.decode(p[key])
 p[key] = gson.toJson(transform(previous))
 } }
 suspend fun select(city: City) = update { s ->
 val index = s.cities.indexOfFirst { it.latitude == city.latitude && it.longitude == city.longitude }
 if(index >= 0) s.copy(selected = index) else s.copy(cities = s.cities + city, selected = s.cities.size)
 }
}
