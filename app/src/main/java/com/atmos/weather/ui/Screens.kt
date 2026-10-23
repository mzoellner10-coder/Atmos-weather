@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.atmos.weather.ui

import android.provider.Settings as AndroidSettings
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
 val scheme = darkColorScheme(primary = Ice, background = Color.Black, surface = Color(0xFF111925), onSurface = Color(0xFFF3F5FC))
 MaterialTheme(colorScheme = scheme, typography = Typography()) {
 Scaffold(containerColor = if(prefs.theme=="Dynamic Weather") Color(0xFF12283B) else Color.Black, bottomBar = {
 NavigationBar(containerColor = Color(0xFF080D15)) {
 listOf("Weather" to "◉", "Places" to "⌖", "Settings" to "☷").forEach { (name, icon) ->
 NavigationBarItem(selected = screen == name, onClick = { screen = name }, icon = { Text(icon, fontSize = 24.sp) }, label = { Text(name) })
 }
 }
 }) { padding -> Box(Modifier.padding(padding).fillMaxSize()) {
 when(screen) {
 "Places" -> Places(vm, prefs, locationMessage, locate) { screen = "Weather" }
 "Settings" -> SettingsScreen(prefs, vm::change)
 "Weather" -> PullToRefreshBox(isRefreshing = state.loading, onRefresh = { vm.refresh(true) }) {
 Dashboard(state, prefs, { screen = it }, { vm.refresh(true) }, { screen = "Places" })
 }
 else -> DetailScreen(screen, state.weather, prefs) { screen = "Weather" }
 }
 } }
 }
}
@Composable private fun Dashboard(state: WeatherState, prefs: Settings, open: (String) -> Unit, refresh: () -> Unit, places: () -> Unit) {
 var expanded by remember { mutableStateOf(false) }
 val w = state.weather
 if(w == null) {
 Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
 Text("ATMOS", color = Ice, letterSpacing = 6.sp)
 Text("A clearer view\nof your day.", fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light)
 Spacer(Modifier.height(24.dp))
 Text(state.error ?: if(state.loading) "Fetching your forecast…" else "Choose a city or use your location to get started.")
 if(state.loading) LinearProgressIndicator(Modifier.padding(top = 24.dp))
 Button(onClick = if(prefs.city == null) places else refresh, modifier = Modifier.padding(top = 24.dp)) { Text(if(prefs.city == null) "Find a location" else "Try again") }
 }; return
 }
 val f = w.forecast; val c = f.current
 LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
 item { Box(Modifier.fillMaxWidth().height(355.dp)) {
 Atmosphere(c.weather_code, c.is_day == 1, prefs)
 Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
 Text("ATMOS  /  ${w.city.name.uppercase()}", color = Ice, letterSpacing = 2.sp, fontSize = 12.sp, modifier = Modifier.clickable(onClick = places))
 Text(temperature(c.temperature_2m, prefs.fahrenheit), fontSize = 110.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-7).sp)
 Text(condition(c.weather_code), fontSize = 22.sp)
 Text("Feels like ${temperature(c.apparent_temperature,prefs.fahrenheit)}   ·   H ${temperature(f.daily.temperature_2m_max[0],prefs.fahrenheit)}  L ${temperature(f.daily.temperature_2m_min[0],prefs.fahrenheit)}", color = Ice, modifier = Modifier.padding(top = 8.dp))
 Text("${if(state.cached) "Cached" else "Updated"} · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(w.fetchedAt))}", fontSize = 11.sp, color = Ice, modifier = Modifier.padding(top = 16.dp))
 }
 } }
 if(state.error != null) item { WeatherCard("Connection", Modifier.padding(horizontal=16.dp)) { Text(state.error); TextButton(onClick=refresh) { Text("Retry") } } }
 w.alerts?.features?.forEach { alert -> item { WeatherCard("⚠ ${alert.properties.event ?: "Weather alert"}", Modifier.padding(horizontal=16.dp), { open("Alerts") }) { Text(alert.properties.headline ?: "Official NWS alert") } } }
 if(w.alertsUnavailable) item { Text("Official alerts unavailable — check weather.gov for current warnings.", color = Color(0xFFFFCD99), modifier=Modifier.padding(horizontal=24.dp)) }
 item { WeatherCard("HOURLY OUTLOOK  ↗", Modifier.padding(horizontal = 16.dp), { open("Hourly") }) { HourlyStrip(w, prefs) } }
 item { WeatherCard("16-DAY FORECAST  ↗", Modifier.padding(horizontal = 16.dp), { open("16-day") }) { DailyRows(w, prefs, 5) } }
 item { Column(Modifier.padding(horizontal=16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
 listOf(listOf("Precipitation", "Wind"),listOf("Air quality", "Sun & daylight"),listOf("Humidity", "UV index"),listOf("Pressure", "Visibility")).forEach { row ->
 Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { label -> WeatherCard(label.uppercase()+"  ↗", Modifier.weight(1f), { open(label) }) {
 Text(metric(label,w,prefs), fontSize=25.sp, fontWeight=FontWeight.Light)
 Text(metricCaption(label), color=Ice, fontSize=12.sp)
 } } }
 }
 } }
 item { WeatherCard("ADDITIONAL CONDITIONS",Modifier.padding(horizontal=16.dp)) {
 TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Show less −" else "Explore more +")}
 if(expanded) {
 val hour=nextHour(w)
 Text("Dew point ${temperature(f.hourly.dew_point_2m[hour],prefs.fahrenheit)} · Cloud cover ${f.hourly.cloud_cover[hour].toInt()}%",color=Ice)
 Text("Gusts ${"%.0f".format(if(prefs.mph)c.wind_gusts_10m/1.609344 else c.wind_gusts_10m)} ${if(prefs.mph)"mph" else "km/h"}",color=Ice)
 }
 } }
 item { TextButton(onClick=refresh,modifier=Modifier.fillMaxWidth()) { Text("Refresh forecast") } }
 item { Text("Open-Meteo · NOAA / NWS\nForecasts may differ from local observations", color=Ice, fontSize=11.sp, modifier=Modifier.padding(horizontal=24.dp)) }
 }
}
@Composable fun WeatherCard(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
 Surface(modifier = modifier.then(if(onClick != null) Modifier.clickable(onClick=onClick) else Modifier), shape = RoundedCornerShape(24.dp), color = Color(0xFF101722), border=BorderStroke(1.dp,Color(0xFF222E40))) {
 Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
 Text(title, fontSize=11.sp,color=Ice,letterSpacing=1.sp); content()
 }
 }
}
fun nextHour(w: Weather): Int {
 return forecastHourIndex(w.forecast.hourly.time,w.forecast.timezone)
}
@Composable private fun HourlyStrip(w: Weather,p: Settings) {
 val h=w.forecast.hourly; val start=nextHour(w)
 LazyRow(horizontalArrangement=Arrangement.spacedBy(22.dp)) { items(min(24,h.time.size-start)) { offset ->
 val i=start+offset
 Column(horizontalAlignment=Alignment.CenterHorizontally) {
 Text(h.time[i].substringAfter('T'),fontSize=12.sp,color=Ice)
 Text(weatherIcon(h.weather_code[i]),fontSize=26.sp,modifier=Modifier.padding(vertical=8.dp))
 Text(temperature(h.temperature_2m[i],p.fahrenheit))
 Text("${h.precipitation_probability[i].toInt()}%",fontSize=11.sp,color=Ice)
 }
 } }
}
@Composable private fun DailyRows(w:Weather,p:Settings,count:Int) {
 val d=w.forecast.daily
 val low=d.temperature_2m_min.min(); val span=(d.temperature_2m_max.max()-low).coerceAtLeast(1.0)
 repeat(min(count,d.time.size)) { i -> Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
 Text(if(i==0) "Today" else LocalDate.parse(d.time[i]).format(DateTimeFormatter.ofPattern("EEE d")),modifier=Modifier.width(62.dp),fontSize=13.sp)
 Text(weatherIcon(d.weather_code[i]),fontSize=21.sp)
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
 "Sun & daylight" -> d.sunset[0].substringAfter('T')
 "Humidity" -> "${c.relative_humidity_2m.toInt()}%"
 "UV index" -> "${d.uv_index_max[0]}"
 "Pressure" -> "${c.surface_pressure.toInt()} hPa"
 "Visibility" -> "${"%.1f".format(h.visibility[i]/1000)} km"
 else -> ""
 }
}
private fun metricCaption(label:String)=when(label) { "Sun & daylight" -> "Sunset · local time"; "Precipitation" -> "Today's total"; "Air quality" -> "US AQI"; "UV index" -> "Today's maximum"; else -> "Explore details" }

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
 Text(metric(screen,w,p),fontSize=42.sp)
 Canvas(Modifier.fillMaxWidth().height(190.dp)) {
 val center=Offset(size.width/2,size.height/2); val radius=75.dp.toPx()
 drawCircle(Color(0xFF243449),radius,center,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
 val angle=(w.forecast.current.wind_direction_10m-90)*PI/180
 val tip=center+Offset(cos(angle).toFloat(),sin(angle).toFloat())*radius
 drawLine(Ice,center,tip,strokeWidth=5.dp.toPx(),cap=StrokeCap.Round)
 }
 Text("From ${w.forecast.current.wind_direction_10m.toInt()}° · Gusts ${"%.0f".format(if(p.mph)w.forecast.current.wind_gusts_10m/1.609344 else w.forecast.current.wind_gusts_10m)} ${if(p.mph)"mph" else "km/h"}")
 } }
 "Air quality" -> item { WeatherCard("AIR QUALITY · US AQI") {
 Text(metric(screen,w,p),fontSize=48.sp)
 Text("PM2.5: ${w.air?.current?.pm2_5 ?: "Unavailable"} µg/m³\nPM10: ${w.air?.current?.pm10 ?: "Unavailable"} µg/m³")
 Text("0–50 Good · 51–100 Moderate · 101–150 Unhealthy for sensitive groups · 151+ Unhealthy",color=Ice)
 } }
 "Sun & daylight" -> item { WeatherCard("SOLAR CYCLE") {
 Text("Sunrise  ${d.sunrise[0].substringAfter('T')}",fontSize=25.sp)
 Text("Sunset   ${d.sunset[0].substringAfter('T')}",fontSize=25.sp)
 Text("${"%.1f".format(d.daylight_duration[0]/3600)} hours of daylight",color=Ice)
 } }
 "Alerts" -> w.alerts?.features?.forEach { a -> item { WeatherCard(a.properties.event ?: "Official alert") { Text(a.properties.description ?: "No description"); Text(a.properties.instruction ?: "Follow local official guidance.",color=Ice) } } }
 else -> item { WeatherCard(screen.uppercase()) {
 Text(metric(screen,w,p),fontSize=42.sp)
 Text(when(screen) { "Humidity" -> "Dew point ${temperature(h.dew_point_2m[start],p.fahrenheit)}"; "Visibility" -> "Cloud cover ${h.cloud_cover[start].toInt()}%"; "UV index" -> "Use sun protection when UV is 3 or higher."; else -> "Surface atmospheric pressure" },color=Ice)
 } }
 }
 item { Text("Provider local time · ${w.forecast.timezone}\nOpen-Meteo / NOAA · ${if(System.currentTimeMillis()-w.fetchedAt>900000)"Cached forecast" else "Latest saved forecast"}",color=Ice,fontSize=12.sp) }
 }
 }
}
@Composable private fun InteractiveGraph(values:List<Double>,times:List<String>,label:String,format:(Double)->String) {
 if(values.isEmpty()) return
 var selected by remember(values) { mutableIntStateOf(0) }
 WeatherCard(label.uppercase()) {
 Text("${times[selected].substringAfter('T')} · ${format(values[selected])}",fontSize=24.sp)
 Canvas(Modifier.fillMaxWidth().height(160.dp).pointerInput(values) { detectTapGestures { selected=((it.x/size.width)*(values.size-1)).roundToInt().coerceIn(values.indices) } }) {
 val min=values.min(); val span=(values.max()-min).coerceAtLeast(1.0)
 fun pt(i:Int)=Offset(i.toFloat()/(values.size-1).coerceAtLeast(1)*size.width,(size.height-12)-(values[i]-min).toFloat()/span.toFloat()*(size.height-24))
 val path=Path(); values.indices.forEach { i -> val a=pt(i); if(i==0)path.moveTo(a.x,a.y) else path.lineTo(a.x,a.y) }
 drawPath(path,Ice,style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(),cap=StrokeCap.Round))
 drawCircle(Color.White,6.dp.toPx(),pt(selected))
 }
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
 TextButton(onClick={ selected=(selected-1).coerceAtLeast(0) }) { Text("Previous hour") }
 TextButton(onClick={ selected=(selected+1).coerceAtMost(values.lastIndex) }) { Text("Next hour") }
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
 LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
 item { Text("Make it yours",fontSize=36.sp,fontWeight=FontWeight.Light) }
 item { WeatherCard("UNITS") {
 Toggle("Fahrenheit",p.fahrenheit) { v -> change { it.copy(fahrenheit=v) } }
 Toggle("Miles per hour",p.mph) { v -> change { it.copy(mph=v) } }
 Toggle("Inches",p.inches) { v -> change { it.copy(inches=v) } }
 } }
 item { WeatherCard("APPEARANCE") {
 listOf("Hybrid OLED","Dynamic Weather","True OLED Black").forEach { theme -> Row(Modifier.fillMaxWidth().clickable {change{it.copy(theme=theme)}},verticalAlignment=Alignment.CenterVertically) { RadioButton(p.theme==theme,{change{it.copy(theme=theme)}});Text(theme) } }
 Text("Animation intensity",color=Ice)
 Row { listOf("Off","Subtle","Full").forEach { mode -> FilterChip(p.motion==mode,{change{it.copy(motion=mode)}},label={Text(mode)},modifier=Modifier.padding(end=8.dp)) } }
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
@Composable private fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f));Switch(checked,onChange) } }
