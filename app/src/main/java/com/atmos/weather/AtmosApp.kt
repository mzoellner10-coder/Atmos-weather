package com.atmos.weather

import android.app.Application
import androidx.work.*
import com.atmos.weather.data.*
import com.atmos.weather.widgets.updateWidgets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

open class AtmosApp: Application() {
 val preferences by lazy { Preferences(this) }
 val repository by lazy { WeatherRepository(this) }
 override fun onCreate() { super.onCreate(); kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch { scheduleRefresh(this@AtmosApp, preferences.flow.first().refresh) } }
}
fun scheduleRefresh(context: android.content.Context, enabled: Boolean) {
 val manager = WorkManager.getInstance(context)
 if(!enabled) { manager.cancelUniqueWork("atmos-refresh"); return }
 val request = PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES)
 .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
 .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
 manager.enqueueUniquePeriodicWork("atmos-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
}
class WeatherWorker(context: android.content.Context, params: WorkerParameters): CoroutineWorker(context, params) {
 override suspend fun doWork(): Result {
 val app = applicationContext as AtmosApp
 var settings = app.preferences.flow.first()
 // Read only passive/last-known fixes; never start GPS from a worker.
 if(settings.followLocation && app.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
 val current = settings.cities.firstOrNull { it.name == "Current location" }
 if(current != null) {
 val manager = app.getSystemService(android.location.LocationManager::class.java)
 val fix = try { listOf(android.location.LocationManager.PASSIVE_PROVIDER,android.location.LocationManager.NETWORK_PROVIDER)
 .mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos } } catch(e: SecurityException) { null }
 if(fix != null && android.os.SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos < 2L*60*60*1_000_000_000 && fix.accuracy < 10000) {
 val distance = FloatArray(1)
 android.location.Location.distanceBetween(current.latitude,current.longitude,fix.latitude,fix.longitude,distance)
 if(distance[0]>3000) {
 val moved=current.copy(latitude=fix.latitude,longitude=fix.longitude)
 app.preferences.update { s -> s.copy(cities=s.cities.map { if(it==current)moved else it }) }
 com.atmos.weather.widgets.moveWidgetLocation(app,current,moved)
 settings=app.preferences.flow.first()
 }
 }
 }
 }
 if(!settings.refresh) { scheduleRefresh(app, false); return Result.success() }
 var failed = false
 for(city in settings.cities) try { app.repository.refresh(city) } catch(e: Exception) {
 if(e is kotlinx.coroutines.CancellationException) throw e
 failed = true
 }
 updateWidgets(app)
 return if(failed && runAttemptCount < 3) Result.retry() else if(failed) Result.failure() else Result.success()
 }
}
