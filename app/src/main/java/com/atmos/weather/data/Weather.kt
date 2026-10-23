package com.atmos.weather.data

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.OkHttpClient
import okhttp3.Cache
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

// Provider names are preserved so parsing is explicit and testable.
data class City(val name: String, val latitude: Double, val longitude: Double, val country_code: String? = null, val admin1: String? = null)
data class SearchResult(val results: List<City> = emptyList())
data class Current(val temperature_2m: Double, val apparent_temperature: Double, val weather_code: Int,
 val relative_humidity_2m: Double, val precipitation: Double, val wind_speed_10m: Double,
 val wind_direction_10m: Double, val wind_gusts_10m: Double, val surface_pressure: Double, val is_day: Int)
data class Hourly(val time: List<String>, val temperature_2m: List<Double>, val precipitation_probability: List<Double>,
 val precipitation: List<Double>, val weather_code: List<Int>, val visibility: List<Double>, val dew_point_2m: List<Double>, val cloud_cover: List<Double>, val rain: List<Double>? = null, val snowfall: List<Double>? = null)
data class Daily(val time: List<String>, val temperature_2m_max: List<Double>, val temperature_2m_min: List<Double>,
 val weather_code: List<Int>, val sunrise: List<String>, val sunset: List<String>, val daylight_duration: List<Double>, val uv_index_max: List<Double>, val precipitation_sum: List<Double>)
data class Forecast(val current: Current, val hourly: Hourly, val daily: Daily, val timezone: String)
data class AirCurrent(val us_aqi: Double?, val pm2_5: Double?, val pm10: Double?)
data class Air(val current: AirCurrent?)
data class AlertProperties(val event: String?, val headline: String?, val description: String?, val instruction: String?, val sent: String? = null, val expires: String? = null)
data class AlertFeature(val properties: AlertProperties)
data class Alerts(val features: List<AlertFeature> = emptyList())
data class Weather(val city: City, val forecast: Forecast, val air: Air?, val alerts: Alerts?, val fetchedAt: Long,
 val airUnavailable: Boolean = false, val alertsUnavailable: Boolean = false)

interface WeatherApi {
 @GET("https://api.open-meteo.com/v1/forecast") suspend fun forecast(
 @Query("latitude") lat: Double, @Query("longitude") lon: Double,
 @Query("current") current: String = "temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,precipitation,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,is_day",
 @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,precipitation,weather_code,visibility,dew_point_2m,cloud_cover,rain,snowfall",
 @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset,daylight_duration,uv_index_max,precipitation_sum",
 @Query("forecast_days") days: Int = 16, @Query("timezone") zone: String = "auto"): Forecast
 @GET("https://geocoding-api.open-meteo.com/v1/search") suspend fun search(@Query("name") name: String, @Query("count") count: Int = 12): SearchResult
 @GET("https://air-quality-api.open-meteo.com/v1/air-quality") suspend fun air(@Query("latitude") lat: Double, @Query("longitude") lon: Double, @Query("current") current: String = "us_aqi,pm2_5,pm10"): Air
 @GET("https://api.weather.gov/alerts/active") suspend fun alerts(@Query("point") point: String): Alerts
}
@Entity(tableName = "forecasts") data class CacheEntry(@PrimaryKey val key: String, val json: String)
@Dao interface WeatherDao {
 @androidx.room.Query("SELECT * FROM forecasts WHERE `key` = :key") suspend fun get(key: String): CacheEntry?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: CacheEntry)
}
@Database(entities = [CacheEntry::class], version = 1, exportSchema = false)
abstract class WeatherDatabase: RoomDatabase() { abstract fun weatherDao(): WeatherDao }
data class RefreshResult(val weather: Weather, val fromCache: Boolean)
class WeatherRepository(context: Context, database: WeatherDatabase? = null, service: WeatherApi? = null) {
 private val gson = Gson()
 private val dao = (database ?: Room.databaseBuilder(context, WeatherDatabase::class.java, "atmos.db").build()).weatherDao()
 private val mutex = Mutex()
 private val client = OkHttpClient.Builder().cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
 .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "AtmosWeather/2.0 (personal non-commercial Android weather application)").build()) }.build()
 val api: WeatherApi = service ?: Retrofit.Builder().baseUrl("https://api.open-meteo.com/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(WeatherApi::class.java)
 private fun key(city: City) = "${city.latitude},${city.longitude}"
 suspend fun cached(city: City): Weather? = dao.get(key(city))?.let { runCatching { gson.fromJson(it.json, Weather::class.java) }.getOrNull() }
 suspend fun refresh(city: City, force: Boolean = false): RefreshResult = mutex.withLock {
 val cached = cached(city)
 // Even simultaneous manual/widget requests share the freshly fetched result.
 if(cached != null && reusableCache(cached.fetchedAt, System.currentTimeMillis(), force)) return@withLock RefreshResult(cached, true)
 var forecast: Forecast? = null
 var failure: Exception? = null
 for (attempt in 0..2) {
  try { forecast = api.forecast(city.latitude, city.longitude); break }
  catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; failure = e; if(attempt < 2) delay(1000L shl attempt) }
 }
 val f = forecast ?: throw (failure ?: IllegalStateException("Weather unavailable"))
 val air = try { api.air(city.latitude, city.longitude) } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; null }
 val us = city.country_code == "US" || city.country_code.isNullOrEmpty()
 val alerts = if(us) try { api.alerts("${city.latitude},${city.longitude}") } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; null } else null
 val result = Weather(city, f, air, alerts, System.currentTimeMillis(), air == null, us && alerts == null)
 dao.put(CacheEntry(key(city), gson.toJson(result))); RefreshResult(result, false)
 }
}
fun condition(code: Int): String = when(code) {
 0 -> "Clear skies"; 1,2 -> "Partly cloudy"; 3 -> "Overcast"; 45,48 -> "Fog"; in 51..57 -> "Drizzle";
 in 61..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Rain showers"; 85,86 -> "Snow showers"; in 95..99 -> "Thunderstorms"; else -> "Unknown conditions"
}
fun weatherIcon(code: Int): String = when(code) { 0 -> "☀"; 1,2 -> "⛅"; 3 -> "☁"; 45,48 -> "≋"; in 51..67,in 80..82 -> "☂"; in 71..77,85,86 -> "❄"; in 95..99 -> "ϟ"; else -> "◌" }
fun temperature(c: Double, fahrenheit: Boolean): String = "${kotlin.math.round(if(fahrenheit) c * 9 / 5 + 32 else c).toInt()}°"
