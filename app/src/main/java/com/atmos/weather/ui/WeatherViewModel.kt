package com.atmos.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atmos.weather.*
import com.atmos.weather.data.*
import com.atmos.weather.widgets.updateWidgets
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WeatherState(val weather: Weather? = null, val loading: Boolean = false, val error: String? = null, val cached: Boolean = true)
class WeatherViewModel(application: Application): AndroidViewModel(application) {
 private val app = application as AtmosApp
 val settings = app.preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
 private val mutable = MutableStateFlow(WeatherState())
 val state = mutable.asStateFlow()
 val searchResults = MutableStateFlow<List<City>>(emptyList())
 val searchError = MutableStateFlow<String?>(null)
 private var loadJob: Job? = null
 private var searchJob: Job? = null
 init { viewModelScope.launch { settings.map { it.city }.distinctUntilChanged().collect { refresh() } } }
 fun refresh(force: Boolean = false) {
 loadJob?.cancel()
 val city = settings.value.city ?: run { mutable.value = WeatherState(); return }
 loadJob = viewModelScope.launch {
 val cached = app.repository.cached(city)
 mutable.value = WeatherState(cached, true, cached = true)
 try {
 val result = app.repository.refresh(city, force)
 mutable.value = WeatherState(result.weather, cached = result.fromCache)
 updateWidgets(app)
 } catch(e: CancellationException) { throw e }
 catch(e: Exception) { mutable.value = WeatherState(cached, false, "Unable to refresh. Check your connection and try again.", true) }
 }
 }
 fun search(query: String) {
 searchJob?.cancel(); searchResults.value = emptyList(); searchError.value = null
 if(query.trim().length < 2) return
 searchJob = viewModelScope.launch {
 delay(350)
 try { searchResults.value = app.repository.api.search(query.trim()).results.orEmpty(); if(searchResults.value.isEmpty()) searchError.value = "No cities found." }
 catch(e: CancellationException) { throw e }
 catch(e: Exception) { searchError.value = "City search is unavailable. Try again when connected." }
 }
 }
 fun select(city: City) { viewModelScope.launch { app.preferences.select(city) } }
 fun change(transform: (Settings) -> Settings) { viewModelScope.launch { app.preferences.update(transform); scheduleRefresh(app, app.preferences.flow.first().refresh); updateWidgets(app) } }
 fun remove(city: City) = change { it.copy(cities = it.cities - city, selected = 0) }
}
