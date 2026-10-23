package com.atmos.weather.widgets

import android.appwidget.AppWidgetManager
import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.atmos.weather.AtmosApp
import kotlinx.coroutines.launch

class WidgetConfigActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
 super.onCreate(savedInstanceState)
 val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
 setResult(RESULT_CANCELED)
 if(id==AppWidgetManager.INVALID_APPWIDGET_ID) {finish();return}
 val app=application as AtmosApp
 setContent {
 val settings by app.preferences.flow.collectAsState(initial=com.atmos.weather.data.Settings())
 var selected by remember {mutableIntStateOf(0)}
 var style by remember {mutableStateOf("OLED")}
 var opacity by remember {mutableFloatStateOf(1f)}
 var f by remember {mutableStateOf(false)}
 var showCity by remember {mutableStateOf(true)}
 var metrics by remember {mutableStateOf(setOf("Feels like","Wind","Humidity","UV","Rain"))}
 LaunchedEffect(settings.city) { f=settings.fahrenheit; selected=settings.selected }
 MaterialTheme(colorScheme=darkColorScheme()) {
 Surface(color=Color.Black) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
 Text("Your Atmos widget",style=MaterialTheme.typography.headlineLarge)
 Text("Choose a saved location. Widgets share Atmos's cache and background refresh.")
 if(settings.cities.isEmpty()) {
 Text("Add a city in Atmos first, then return to your widget picker.")
 Button(onClick={startActivity(Intent(this@WidgetConfigActivity,com.atmos.weather.MainActivity::class.java));finish()}) {Text("Open Atmos")}
 }
 settings.cities.forEachIndexed { i,city -> Row {RadioButton(selected==i,{selected=i});Text(city.name,Modifier.padding(top=12.dp))} }
 Text("Appearance")
 listOf("OLED","Transparent","Dynamic","Light","Dark").forEach { choice -> Row {RadioButton(style==choice,{style=choice});Text(choice,Modifier.padding(top=12.dp))} }
 Text("Opacity ${(opacity*100).toInt()}%")
 Slider(opacity,{opacity=it})
 Row {Checkbox(f,{f=it});Text("Fahrenheit",Modifier.padding(top=12.dp))}
 Row {Checkbox(showCity,{showCity=it});Text("Show location",Modifier.padding(top=12.dp))}
 Text("Advanced dashboard metrics")
 listOf("Feels like","Wind","Humidity","UV","Rain","Pressure","Cloud cover").forEach { metric ->
 Row {Checkbox(metric in metrics,{enabled -> metrics=if(enabled) metrics+metric else metrics-metric});Text(metric,Modifier.padding(top=12.dp))}
 }
 Button(enabled=settings.cities.isNotEmpty(),onClick={
 val city=settings.cities[selected.coerceAtMost(settings.cities.lastIndex)]
 getSharedPreferences("widgets",MODE_PRIVATE).edit().putString("${id}_city","${city.latitude},${city.longitude}")
 .putStringSet("${id}_metrics",metrics).putString("${id}_style",style).putInt("${id}_opacity",(opacity*100).toInt()).putBoolean("${id}_fahrenheit",f).putBoolean("${id}_showcity",showCity).apply()
 lifecycleScope.launch {updateWidgets(this@WidgetConfigActivity);setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish()}
 }) {Text("Add widget")}
 } }
 }
 }
 }
}
