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
 val appearance=WidgetAppearance(
 style=preferences.getString("${id}_style","OLED") ?: "OLED",
 opacity=preferences.getInt("${id}_opacity",100),
 illustration=preferences.getString("${id}_illustration","Sculpted") ?: "Sculpted",
 showCity=preferences.getBoolean("${id}_showcity",true),
 fahrenheit=preferences.getBoolean("${id}_fahrenheit",settings.fahrenheit),
 metrics=preferences.getStringSet("${id}_metrics",setOf("Feels like","Wind","Humidity","UV","Rain")).orEmpty())
 val options=manager.getAppWidgetOptions(id)
 val width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,listOf(60,140,140,280,280,280,280)[kind]).coerceAtLeast(50)
 val height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,listOf(140,60,140,140,210,210,210)[kind]).coerceAtLeast(50)
 val views=RemoteViews(context.packageName,R.layout.widget)
 views.setViewVisibility(R.id.empty,android.view.View.GONE)
 views.setImageViewBitmap(R.id.artboard,WidgetRenderer.render(kind,width,height,w,settings,appearance,context.resources.configuration.fontScale))
 views.setContentDescription(R.id.artboard,WidgetRenderer.description(kind,w,settings,appearance))
 val target=when(kind) {3->"Hourly";4->"16-day";5->"Precipitation";else->"Weather"}
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

/** Passive location changes must keep existing widget bindings on that location. */
fun moveWidgetLocation(context:Context,before:City,after:City) {
 val prefs=context.getSharedPreferences("widgets",Context.MODE_PRIVATE)
 val oldKey="${before.latitude},${before.longitude}"
 val newKey="${after.latitude},${after.longitude}"
 val editor=prefs.edit()
 prefs.all.forEach { (key,value) -> if(key.endsWith("_city") && value==oldKey)editor.putString(key,newKey) }
 editor.apply()
}
