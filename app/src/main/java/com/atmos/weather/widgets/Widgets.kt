package com.atmos.weather.widgets

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import com.atmos.weather.*
import com.atmos.weather.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

val widgetClasses = listOf(VerticalWidget::class.java, HorizontalWidget::class.java, CurrentWidget::class.java,
 HourlyWidget::class.java, DailyWidget::class.java, RainWidget::class.java, AdvancedWidget::class.java)
val widgetNames = listOf("Vertical compact", "Horizontal compact", "Current weather", "Hourly forecast", "Multi-day forecast", "Rain tracker", "Advanced dashboard")
suspend fun updateWidgets(context:Context) {
 val app=context.applicationContext as AtmosApp
 val settings=app.preferences.flow.first()
 val manager=AppWidgetManager.getInstance(context)
 val preferences=context.getSharedPreferences("widgets",Context.MODE_PRIVATE)
 for((kind,cls) in widgetClasses.withIndex()) for(id in manager.getAppWidgetIds(ComponentName(context,cls))) {
 val savedName=preferences.getString("${id}_city",null)
 val city=settings.cities.firstOrNull { "${it.latitude},${it.longitude}"==savedName } ?: settings.city
 val w=city?.let { app.repository.cached(it) }
 val f=preferences.getBoolean("${id}_fahrenheit",settings.fahrenheit)
 val showCity=preferences.getBoolean("${id}_showcity",true)
 val style=preferences.getString("${id}_style","OLED")
 val opacity=preferences.getInt("${id}_opacity",100)
 val views=RemoteViews(context.packageName,when(kind) { 0 -> R.layout.widget_vertical; 1 -> R.layout.widget_horizontal; else -> R.layout.widget })
 views.setTextViewText(R.id.city,if(showCity)city?.name ?: "Atmos Weather" else "")
 val light=style=="Light"
 val base=when(style) { "Light" -> Color.rgb(235,242,250); "Dynamic" -> Color.rgb(30,65,94); else -> Color.BLACK }
 views.setInt(R.id.root,"setBackgroundColor",if(style=="Transparent")Color.TRANSPARENT else Color.argb(opacity*255/100,Color.red(base),Color.green(base),Color.blue(base)))
 listOf(R.id.city,R.id.temp,R.id.details).forEach { views.setTextColor(it,if(light)Color.rgb(15,28,46) else Color.WHITE) }
 var target="Weather"
 if(w==null) { views.setTextViewText(R.id.temp,"—");views.setTextViewText(R.id.details,"Open Atmos to fetch weather") }
 else {
 val c=w.forecast.current;val d=w.forecast.daily;val h=w.forecast.hourly;val start=com.atmos.weather.ui.nextHour(w)
 val small=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,140)<110
 views.setTextViewText(R.id.temp,if(kind==1)temperature(c.temperature_2m,f) else "${temperature(c.temperature_2m,f)} ${weatherIcon(c.weather_code)}")
 val highLow="H ${temperature(d.temperature_2m_max[0],f)}  L ${temperature(d.temperature_2m_min[0],f)}"
 val details=when(kind) {
 0 -> if(small) condition(c.weather_code) else "$highLow\n${condition(c.weather_code)}"
 1 -> "${weatherIcon(c.weather_code)} $highLow"
 2 -> "${condition(c.weather_code)}\n$highLow\nFeels ${temperature(c.apparent_temperature,f)}"
 3 -> { target="Hourly"; condition(c.weather_code) }
 4 -> { target="16-day"; (0 until minOf(5,d.time.size)).joinToString("\n") { "${d.time[it].takeLast(5)}  ${weatherIcon(d.weather_code[it])}  ${temperature(d.temperature_2m_min[it],f)} ━ ${temperature(d.temperature_2m_max[it],f)}" } }
 5 -> { target="Precipitation"; (start until minOf(start+5,h.time.size)).joinToString("\n") { "${h.time[it].substringAfter('T')}  ${h.precipitation_probability[it].toInt()}% ${"▰".repeat((h.precipitation_probability[it]/20).toInt())}  ${"%.2f".format(if(settings.inches)h.precipitation[it]/25.4 else h.precipitation[it])}${if(settings.inches)"in" else "mm"}" } }
 else -> {
 val selected=preferences.getStringSet("${id}_metrics",setOf("Feels like","Wind","Humidity","UV","Rain")).orEmpty()
 selected.mapNotNull { metric -> when(metric) {
 "Feels like" -> "Feels ${temperature(c.apparent_temperature,f)}"
 "Wind" -> "Wind ${"%.0f".format(if(settings.mph)c.wind_speed_10m/1.609344 else c.wind_speed_10m)} ${if(settings.mph)"mph" else "km/h"}"
 "Humidity" -> "Humidity ${c.relative_humidity_2m.toInt()}%"
 "UV" -> "UV ${d.uv_index_max[0]}"
 "Rain" -> "Rain ${h.precipitation_probability[start].toInt()}%"
 "Pressure" -> "${c.surface_pressure.toInt()} hPa"
 "Cloud cover" -> "Clouds ${h.cloud_cover[start].toInt()}%"
 else -> null
 } }.chunked(2).joinToString("\n") { it.joinToString(" · ") }
 }
 }
 if(kind==3) {
 views.removeAllViews(R.id.forecast)
 for(i in start until minOf(start+5,h.time.size)) {
 val item=RemoteViews(context.packageName,R.layout.widget_hour)
 item.setTextViewText(R.id.hour,h.time[i].substringAfter('T'))
 item.setTextViewText(R.id.value,"${weatherIcon(h.weather_code[i])}\n${temperature(h.temperature_2m[i],f)}")
 item.setTextViewText(R.id.chance,"${h.precipitation_probability[i].toInt()}%")
 listOf(R.id.hour,R.id.value,R.id.chance).forEach { item.setTextColor(it,if(light)Color.BLACK else Color.WHITE) }
 views.addView(R.id.forecast,item)
 }
 }
 if(kind==5) {
 val bitmap=android.graphics.Bitmap.createBitmap(500,72,android.graphics.Bitmap.Config.ARGB_8888)
 val canvas=android.graphics.Canvas(bitmap)
 val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=if(light)Color.rgb(30,90,150) else Color.rgb(160,210,255);strokeWidth=4f}
 val values=h.precipitation_probability.drop(start).take(6)
 for(i in 0 until values.lastIndex) canvas.drawLine(i*100f,70-values[i].toFloat()*.65f,(i+1)*100f,70-values[i+1].toFloat()*.65f,paint)
 views.setViewVisibility(R.id.chart,android.view.View.VISIBLE);views.setImageViewBitmap(R.id.chart,bitmap)
 }
 val age=java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(w.fetchedAt))
 views.setTextViewText(R.id.details,"$details\nSaved $age${if(System.currentTimeMillis()-w.fetchedAt>900000)" · Cached" else ""}")
 }
 val intent=Intent(context,MainActivity::class.java).putExtra("screen",target).apply {
 if(city!=null) {putExtra("city",city.name);putExtra("latitude",city.latitude);putExtra("longitude",city.longitude);putExtra("country",city.country_code)}
 }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
 views.setOnClickPendingIntent(R.id.root,PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
 manager.updateAppWidget(id,views)
 }
}
open class AtmosWidget:AppWidgetProvider() {
 override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { update(context) }
 override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) { update(context) }
 override fun onDeleted(context:Context,ids:IntArray) {
 val prefs=context.getSharedPreferences("widgets",Context.MODE_PRIVATE)
 val editor=prefs.edit();ids.forEach { id -> prefs.all.keys.filter { it.startsWith("${id}_") }.forEach {editor.remove(it)} };editor.apply()
 }
 private fun update(context:Context) {
 val result=goAsync()
 CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { try {updateWidgets(context)} finally {result.finish()} }
 }
}
class VerticalWidget:AtmosWidget()
class HorizontalWidget:AtmosWidget()
class CurrentWidget:AtmosWidget()
class HourlyWidget:AtmosWidget()
class DailyWidget:AtmosWidget()
class RainWidget:AtmosWidget()
class AdvancedWidget:AtmosWidget()
