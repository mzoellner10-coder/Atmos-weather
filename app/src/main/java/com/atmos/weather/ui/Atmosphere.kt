package com.atmos.weather.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.atmos.weather.data.Settings
import kotlin.math.*

@Composable fun Atmosphere(code:Int,day:Boolean,prefs:Settings) {
 val context=LocalContext.current
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var time by remember { mutableFloatStateOf(0f) }
 var reduced by remember { mutableStateOf(AndroidSettings.Global.getFloat(context.contentResolver,AndroidSettings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
 DisposableEffect(context) {
 val observer=object:android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
 override fun onChange(selfChange:Boolean) { reduced=AndroidSettings.Global.getFloat(context.contentResolver,AndroidSettings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
 }
 context.contentResolver.registerContentObserver(AndroidSettings.Global.getUriFor(AndroidSettings.Global.ANIMATOR_DURATION_SCALE),false,observer)
 onDispose {context.contentResolver.unregisterContentObserver(observer)}
 }
 val animate=prefs.motion!="Off" && prefs.theme!="True OLED Black" && !reduced
 LaunchedEffect(animate,lifecycle) {
 if(animate) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
 val start=withFrameNanos { it }
 while(true) { withFrameNanos { time=(it-start)/1_000_000_000f } }
 }
 }
 val colors=when {
 prefs.theme=="True OLED Black" -> listOf(androidx.compose.ui.graphics.Color.Black,androidx.compose.ui.graphics.Color.Black)
 !day -> listOf(Color(0xFF182441),Color(0xFF02040C))
 code>=51 -> listOf(Color(0xFF304957),Color(0xFF060C16))
 else -> listOf(Color(0xFF295779),Color(0xFF061222))
 }
 Canvas(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
 if(prefs.theme=="True OLED Black")return@Canvas
 val t=time * if(prefs.motion=="Full")1f else .35f
 if(day && code<3) drawCircle(Brush.radialGradient(listOf(Color(0x66FFDA96),Color.Transparent),Offset(size.width*.8f,size.height*.16f),size.width*.5f),size.width*.5f,Offset(size.width*.8f,size.height*.16f))
 if(!day) repeat(45) { i ->
 val x=((i*73)%997)/997f*size.width; val y=((i*113)%499)/499f*size.height
 drawCircle(Color.White.copy(alpha=.25f+.2f*sin(t+i)),1.2f,Offset(x,y))
 }
 if(code in 1..48) repeat(5) { i ->
 val x=((i*.27f+t*.01f)%1.5f-.2f)*size.width
 drawOval(Color.White.copy(alpha=if(code in 45..48).08f else .035f),Offset(x,size.height*(.1f+i*.15f)),androidx.compose.ui.geometry.Size(size.width*.7f,35f))
 }
 if(code>=95) {
 val bolt=Path().apply {moveTo(size.width*.78f,size.height*.08f);lineTo(size.width*.72f,size.height*.23f);lineTo(size.width*.79f,size.height*.21f);lineTo(size.width*.74f,size.height*.36f)}
 drawPath(bolt,Color(0x25D3E6FF),style=androidx.compose.ui.graphics.drawscope.Stroke(3f,cap=StrokeCap.Round))
 }
 if(code>=51) repeat(if(prefs.motion=="Full")65 else 28) { i ->
 val x=((i*97)%997)/997f*size.width
 val y=(((i*53)%499)/499f+t*if(code in 71..77 || code==85 || code==86).08f else .5f)%1f*size.height
 if(code in 71..77 || code==85 || code==86)drawCircle(Color.White.copy(alpha=.4f),3f,Offset(x,y))
 else drawLine(Color(0x448AC4ED),Offset(x,y),Offset(x-5,y+20),2f)
 }
 }
}
