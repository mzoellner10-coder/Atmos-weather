package com.atmos.weather.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("atmos_preferences")
data class Settings(val fahrenheit: Boolean = false, val mph: Boolean = false, val inches: Boolean = false,
 val followLocation: Boolean = false, val theme: String = "Hybrid OLED", val motion: String = "Subtle", val refresh: Boolean = true,
 val cities: List<City> = emptyList(), val selected: Int = 0) {
 val city: City? get() = cities.getOrNull(selected)
}
class Preferences(private val context: Context) {
 private val key = stringPreferencesKey("settings")
 private val gson = Gson()
 val flow = context.store.data.map { runCatching { gson.fromJson(it[key], Settings::class.java) }.getOrNull() ?: Settings() }
 suspend fun update(transform: (Settings) -> Settings) { context.store.edit { p ->
 val previous = runCatching { gson.fromJson(p[key], Settings::class.java) }.getOrNull() ?: Settings()
 p[key] = gson.toJson(transform(previous))
 } }
 suspend fun select(city: City) = update { s ->
 val index = s.cities.indexOfFirst { it.latitude == city.latitude && it.longitude == city.longitude }
 if(index >= 0) s.copy(selected = index) else s.copy(cities = s.cities + city, selected = s.cities.size)
 }
}
