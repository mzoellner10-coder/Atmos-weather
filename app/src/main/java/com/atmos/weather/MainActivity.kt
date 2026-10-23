package com.atmos.weather

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.atmos.weather.data.City
import com.atmos.weather.ui.*

class MainActivity: ComponentActivity() {
 private val model: WeatherViewModel by viewModels()
 private var locationMessage by mutableStateOf<String?>(null)
 private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
 if(result.values.any { it }) locate() else { locationMessage = "Location permission denied. Search for a city instead." }
 }
 private fun locate() {
 if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
 permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)); return
 }
 val manager = getSystemService(LocationManager::class.java)
 val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { manager.isProviderEnabled(it) }
 if(providers.isEmpty()) { locationMessage = "Turn on device location, or search for a city."; return }
 locationMessage = "Finding your location…"
 try {
 androidx.core.location.LocationManagerCompat.getCurrentLocation(manager, providers.first(), null as android.os.CancellationSignal?, mainExecutor) { loc: android.location.Location? ->
 if(loc != null) { model.select(City("Current location", loc.latitude, loc.longitude)); locationMessage = null }
 else locationMessage = "Location unavailable. Search for a city instead."
 }
 } catch(e: SecurityException) { locationMessage = "Location permission unavailable. Search for a city instead." }
 }
 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)
 if(intent.hasExtra("latitude")) model.select(City(intent.getStringExtra("city") ?: "Saved location",intent.getDoubleExtra("latitude",0.0),intent.getDoubleExtra("longitude",0.0),intent.getStringExtra("country") ?: ""))
 setContent { AtmosRoot(model, locationMessage, { locate() }, intent.getStringExtra("screen")) }
 }
 override fun onStart() { super.onStart(); model.refresh() }
}
