@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.atmos.weather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.atmos.weather.experience.*
import com.atmos.weather.graphics.WeatherArt
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atmos.weather.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

private val Ice = Color(0xFFB8DAFF)
@Composable fun AtmosRoot(vm: WeatherViewModel, locationMessage: String?, locate: () -> Unit, initialScreen: String?) {
 val state by vm.state.collectAsStateWithLifecycle()
 val prefs by vm.settings.collectAsStateWithLifecycle()
 var screen by remember { mutableStateOf(initialScreen ?: "Weather") }
 val reduced = systemReducedMotion() || prefs.motionMode=="Minimal"
 val accent=Color(WeatherArt.accent(state.weather?.forecast?.current?.weather_code ?: 0,state.weather?.forecast?.current?.is_day!=0,state.weather?.forecast?.current?.wind_speed_10m ?: 0.0))
 val scheme=darkColorScheme(primary=accent,secondary=accent,secondaryContainer=accent.copy(alpha=.14f),onSecondaryContainer=accent,background=Color.Black,surface=Color(0xFF111215),onSurface=Color(0xFFF5F3ED))
 BackHandler(screen!="Weather") {screen="Weather"}
 CompositionLocalProvider(LocalExperience provides prefs,LocalReducedMotion provides reduced) {
 MaterialTheme(colorScheme=scheme,typography=Typography()) {
 val feedback=rememberFeedback()
 WeatherAmbience(state.weather?.forecast?.current,prefs)
 Scaffold(containerColor=Color.Black,contentColor=scheme.onSurface,bottomBar={
 Surface(color=Color(0xFF101114),shape=RoundedCornerShape(32.dp),modifier=Modifier.navigationBarsPadding().padding(horizontal=44.dp,vertical=10.dp)) {
 Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
 listOf("Weather","Places","Settings").forEach { name ->
 TextButton(modifier=Modifier.weight(1f),onClick={feedback();screen=name},colors=ButtonDefaults.textButtonColors(contentColor=if(screen==name)accent else Color(0xFF8E9199))) {Text(name,fontWeight=if(screen==name)FontWeight.Bold else FontWeight.Normal)}
 }
 }
 }
 }) { padding ->
 AnimatedContent(targetState=screen,modifier=Modifier.padding(padding).fillMaxSize(),label="page",
 transitionSpec={ (fadeIn(tween(if(reduced)0 else 230))+slideInHorizontally(tween(if(reduced)0 else 260)){it/14}) togetherWith fadeOut(tween(if(reduced)0 else 120)) }) { route ->
 when(route) {
 "Places" -> Places(vm,prefs,locationMessage,locate) {screen="Weather"}
 "Settings" -> SettingsScreen(prefs,vm::change)
 "Weather" -> PullToRefreshBox(isRefreshing=state.loading,onRefresh={vm.refresh(true)}) {
 WeatherDashboard(state,prefs,{feedback();screen=it},{vm.refresh(true)},{feedback();screen="Places"})
 }
 else -> DetailScreen(route,state.weather,prefs) {screen="Weather"}
 }
 }
 }
 }
 }
}
@Composable fun WeatherCard(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
 Surface(modifier = modifier.animateContentSize(animationSpec=tween(if(LocalReducedMotion.current)0 else 260)).then(if(onClick != null) Modifier.clickable(onClick=onClick) else Modifier), shape = RoundedCornerShape(30.dp), color = Color(0xFF111215), border=BorderStroke(1.dp,Color(0xFF222328))) {
 Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
 Text(title, fontSize=11.sp,color=MaterialTheme.colorScheme.primary,letterSpacing=1.sp); content()
 }
 }
}
fun nextHour(w: Weather): Int {
 return forecastHourIndex(w.forecast.hourly.time,w.forecast.timezone)
}
@Composable internal fun HourlyStrip(w: Weather,p: Settings) {
 val h=w.forecast.hourly; val start=nextHour(w)
 LazyRow(horizontalArrangement=Arrangement.spacedBy(22.dp)) { items(min(24,h.time.size-start)) { offset ->
 val i=start+offset
 Column(horizontalAlignment=Alignment.CenterHorizontally) {
 Text(forecastTime(h.time[i],p.time24),fontSize=12.sp,color=Ice)
 WeatherSculpture(h.weather_code[i],day=isDayAt(w.forecast,h.time[i]),modifier=Modifier.size(52.dp))
 Text(temperature(h.temperature_2m[i],p.fahrenheit))
 Text("${h.precipitation_probability[i].toInt()}%",fontSize=11.sp,color=Ice)
 }
 } }
}
@Composable internal fun DailyRows(w:Weather,p:Settings,count:Int) {
 val d=w.forecast.daily
 val low=d.temperature_2m_min.min(); val span=(d.temperature_2m_max.max()-low).coerceAtLeast(1.0)
 repeat(min(count,d.time.size)) { i -> Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
 Text(if(i==0) "Today" else LocalDate.parse(d.time[i]).format(DateTimeFormatter.ofPattern("EEE d")),modifier=Modifier.width(62.dp),fontSize=13.sp)
 WeatherSculpture(d.weather_code[i],modifier=Modifier.size(34.dp))
 Text(temperature(d.temperature_2m_min[i],p.fahrenheit),color=Ice,modifier=Modifier.width(35.dp))
 Canvas(Modifier.weight(1f).height(5.dp)) {
 drawLine(Color(0xFF253348),Offset(0f,size.height/2),Offset(size.width,size.height/2),strokeWidth=size.height,cap=StrokeCap.Round)
 drawLine(Brush.horizontalGradient(listOf(Ice,Color(0xFFFFC580))),Offset(((d.temperature_2m_min[i]-low)/span*size.width).toFloat(),size.height/2),Offset(((d.temperature_2m_max[i]-low)/span*size.width).toFloat(),size.height/2),strokeWidth=size.height,cap=StrokeCap.Round)
 }
 Text(temperature(d.temperature_2m_max[i],p.fahrenheit),modifier=Modifier.width(35.dp))
 } }
}
fun metric(label:String,w:Weather,p:Settings):String {
 val c=w.forecast.current; val d=w.forecast.daily; val h=w.forecast.hourly; val i=nextHour(w)
 return when(label) {
 "Wind" -> "${"%.0f".format(if(p.mph)c.wind_speed_10m/1.609344 else c.wind_speed_10m)} ${if(p.mph)"mph" else "km/h"}"
 "Precipitation" -> "${"%.1f".format(if(p.inches)d.precipitation_sum[0]/25.4 else d.precipitation_sum[0])} ${if(p.inches)"in" else "mm"}"
 "Air quality" -> w.air?.current?.us_aqi?.toInt()?.toString() ?: "Unavailable"
 "Sun & daylight" -> forecastTime(d.sunset[0],p.time24)
 "Humidity" -> "${c.relative_humidity_2m.toInt()}%"
 "UV index" -> "${d.uv_index_max[0]}"
 "Pressure" -> "${c.surface_pressure.toInt()} hPa"
 "Visibility" -> "${"%.1f".format(h.visibility[i]/1000)} km"
 "Dew point" -> temperature(h.dew_point_2m[i],p.fahrenheit)
 else -> ""
 }
}
@Composable private fun DetailScreen(screen:String,w:Weather?,p:Settings,back:()->Unit) {
 Column(Modifier.fillMaxSize()) {
 TextButton(onClick=back) { Text("‹  Weather") }
 if(w==null) { Text("Choose a location to view details.",Modifier.padding(24.dp)); return }
 val h=w.forecast.hourly; val d=w.forecast.daily; val start=nextHour(w)
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
 item { Text(screen,fontSize=34.sp,fontWeight=FontWeight.Light); Text(w.city.name,color=Ice) }
 when(screen) {
 "Hourly" -> {
 item { InteractiveGraph(h.temperature_2m.drop(start).take(24), h.time.drop(start).take(24), "Temperature") { temperature(it,p.fahrenheit) } }
 item { HourlyStrip(w,p) }
 }
 "16-day" -> item { WeatherCard("DAILY OUTLOOK") { DailyRows(w,p,16) } }
 "Precipitation" -> {
 item { Text(metric(screen,w,p),fontSize=48.sp); Text("Forecast total today. Radar is a future feature.",color=Ice) }
 item { InteractiveGraph(h.precipitation_probability.drop(start).take(24),h.time.drop(start).take(24),"Chance of precipitation") { "${it.toInt()}%" } }
 item { InteractiveGraph(h.precipitation.drop(start).take(24),h.time.drop(start).take(24),"Precipitation amount") { "%.2f %s".format(if(p.inches)it/25.4 else it,if(p.inches)"in" else "mm") } }
 item { WeatherCard("RAIN & SNOW · NEXT HOUR") {
 val rain=h.rain?.getOrNull(start)
 val snow=h.snowfall?.getOrNull(start)
 Text("Rain: ${rain?.let { "%.2f %s".format(if(p.inches)it/25.4 else it,if(p.inches)"in" else "mm") } ?: "Unavailable"}")
 Text("Snowfall: ${snow?.let { "%.2f %s".format(if(p.inches)it/2.54 else it,if(p.inches)"in" else "cm") } ?: "Unavailable"}")
 } }
 }
 "Wind" -> item { WeatherCard("WIND DIRECTION") {
 WindInstrument(w,p)
 Text(metric(screen,w,p),fontSize=42.sp)

 Text("From ${w.forecast.current.wind_direction_10m.toInt()}° · Gusts ${"%.0f".format(if(p.mph)w.forecast.current.wind_gusts_10m/1.609344 else w.forecast.current.wind_gusts_10m)} ${if(p.mph)"mph" else "km/h"}")
 } }
 "Air quality" -> item { WeatherCard("AIR QUALITY · US AQI") {
 MetricInstrument("Air quality",w,p)
 Text("PM2.5: ${w.air?.current?.pm2_5 ?: "Unavailable"} µg/m³\nPM10: ${w.air?.current?.pm10 ?: "Unavailable"} µg/m³")
 Text("0–50 Good · 51–100 Moderate · 101–150 Unhealthy for sensitive groups · 151+ Unhealthy",color=Ice)
 } }
 "Sun & daylight" -> item { WeatherCard("SOLAR CYCLE") {
 SolarInstrument(w,p)
 Text("Sunrise  ${forecastTime(d.sunrise[0],p.time24)}",fontSize=25.sp)
 Text("Sunset   ${forecastTime(d.sunset[0],p.time24)}",fontSize=25.sp)
 Text("${"%.1f".format(d.daylight_duration[0]/3600)} hours of daylight",color=Ice)
 } }
 "Alerts" -> w.alerts?.features?.forEach { a -> item { WeatherCard(a.properties.event ?: "Official alert") { a.properties.sent?.let { Text("Issued ${alertTime(it,w.forecast.timezone,p.time24)}",color=Ice) }
 a.properties.expires?.let { Text("Expires ${alertTime(it,w.forecast.timezone,p.time24)}",color=Ice) }
 Text(a.properties.description ?: "No description"); Text(a.properties.instruction ?: "Follow local official guidance.",color=Ice) } } }
 else -> item { WeatherCard(screen.uppercase()) {
 MetricInstrument(screen,w,p)
 Text(metric(screen,w,p),fontSize=42.sp)
 Text(when(screen) { "Humidity" -> "Dew point ${temperature(h.dew_point_2m[start],p.fahrenheit)}"; "Visibility" -> "Cloud cover ${h.cloud_cover[start].toInt()}%"; "UV index" -> "Use sun protection when UV is 3 or higher."; else -> "Surface atmospheric pressure" },color=Ice)
 } }
 }
 item { Text("Provider local time · ${w.forecast.timezone}\nOpen-Meteo / NOAA · ${if(System.currentTimeMillis()-w.fetchedAt>900000)"Cached forecast" else "Latest saved forecast"}",color=Ice,fontSize=12.sp) }
 }
 }
}
@Composable private fun Places(vm:WeatherViewModel,p:Settings,message:String?,locate:()->Unit,done:()->Unit) {
 var query by remember { mutableStateOf("") }
 val results by vm.searchResults.collectAsStateWithLifecycle(); val error by vm.searchError.collectAsStateWithLifecycle()
 LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
 item { Text("Your places",fontSize=36.sp,fontWeight=FontWeight.Light) }
 item { OutlinedTextField(value=query,onValueChange={query=it;vm.search(it)},label={Text("Search cities")},modifier=Modifier.fillMaxWidth(),singleLine=true) }
 item { Button(onClick=locate) { Text("Use current location") }; Text("Uses a single foreground location request, including approximate location. Saved coordinates support background weather refresh without GPS tracking.",fontSize=12.sp,color=Ice); if(message!=null)Text(message) }
 if(error!=null) item { Text(error!!,color=Ice) }
 results.forEach { city -> item { WeatherCard(city.country_code.orEmpty(), onClick={ vm.select(city);done() }) { Text(city.name,fontSize=23.sp); Text(city.admin1.orEmpty(),color=Ice) } } }
 if(p.cities.isNotEmpty()) item { Text("SAVED LOCATIONS",color=Ice,fontSize=12.sp) }
 p.cities.forEach { city -> item { WeatherCard(if(city==p.city)"DEFAULT LOCATION" else "SAVED",onClick={vm.select(city);done()}) {
 Text(city.name,fontSize=24.sp)
 Row { TextButton(onClick={vm.select(city);done()}) { Text("Open / set default") }; TextButton(onClick={vm.remove(city)}) { Text("Remove") } }
 } } }
 }
}
@Composable private fun SettingsScreen(p:Settings,change:((Settings)->Settings)->Unit) {
 val context=LocalContext.current
 val feedback=rememberFeedback()
 LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
 item { Text("Make it yours",fontSize=36.sp,fontWeight=FontWeight.Light) }
 item { WeatherCard("UNITS") {
 Toggle("Fahrenheit",p.fahrenheit) { v -> change { it.copy(fahrenheit=v) } }
 Toggle("Miles per hour",p.mph) { v -> change { it.copy(mph=v) } }
 Toggle("Inches",p.inches) { v -> change { it.copy(inches=v) } }
 } }
 item { WeatherCard("APPEARANCE") {
 listOf("Hybrid","Dynamic","OLED Black").forEach { theme -> Row(Modifier.fillMaxWidth().clickable {feedback();change{it.copy(theme=theme)}},verticalAlignment=Alignment.CenterVertically) { RadioButton(p.visualTheme==theme,{feedback();change{it.copy(theme=theme)}});Text(theme) } }
 Toggle("24-hour time (default is AM/PM)",p.time24) { v -> change { it.copy(time24=v) } }
 Toggle("Subtle haptics",p.haptics) { v -> change { it.copy(haptics=v) } }
 Toggle("Weather sounds",p.sounds) { v -> change { it.copy(sounds=v) } }
 Text("Original soft rain, wind and thunder ambience plays only while visible, in normal sound mode, and when other media is not playing. Off by default.",fontSize=12.sp,color=Ice)
 Text("Animation intensity",color=Ice)
 Row { listOf("Full","Balanced","Minimal").forEach { mode -> FilterChip(p.motionMode==mode,{feedback();change{it.copy(motion=mode)}},label={Text(mode)},modifier=Modifier.padding(end=8.dp)) } }
 } }
 item { WeatherCard("BACKGROUND REFRESH") {
 Toggle("Refresh saved cities",p.refresh) { v -> change { it.copy(refresh=v) } }
 Text("Requests updates about every 30 minutes. Android may defer work to conserve battery. Widgets use the same cached forecasts.",color=Ice)
 Toggle("Follow passive location changes",p.followLocation) { v -> change { it.copy(followLocation=v) } }
 Text("Optional: after granting foreground location, allow location all the time in Android settings to follow meaningful movement. Background work only reads recent fixes from other system activity; it does not start GPS. Without permission, your last coordinates and saved cities continue working.",color=Ice)
 TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}) { Text("Android location permissions") }
 } }
 item { WeatherCard("WIDGETS") { Text("Add Atmos widgets from your launcher's widget picker. Each widget has its own location, theme, opacity and unit settings. Resize with your launcher's handles.") } }
 item { WeatherCard("DATA & PRIVACY") {
 Text("Weather and geocoding: Open-Meteo (CC BY 4.0). Air quality: Open-Meteo / CAMS ENSEMBLE. U.S. alerts: NOAA National Weather Service.")
 TextButton(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/")))}) {Text("Open-Meteo attribution & information")}
 TextButton(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.weather.gov/")))}) {Text("National Weather Service") }
 Text("For personal non-commercial use under Open-Meteo's free API terms. No API key required. Forecasts are predictions; follow official local warnings.")
 Text("Coordinates and search queries are sent to the relevant providers. Saved locations, forecasts and preferences remain on this device. No ads, analytics or account. Removing a city removes it from refresh scheduling; Android's Clear storage removes all local data.",color=Ice)
 } }
 }
}
@Composable private fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth().toggleable(value=checked,role=Role.Switch,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f));Switch(checked,null) } }
