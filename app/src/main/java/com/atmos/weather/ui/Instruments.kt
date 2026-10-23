package com.atmos.weather.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*
import com.atmos.weather.data.*
import com.atmos.weather.experience.*
import kotlin.math.*

fun aqiCategory(aqi: Double?): String = when {
    aqi==null -> "Unavailable"
    aqi<=50 -> "Good";aqi<=100 -> "Moderate";aqi<=150 -> "Sensitive groups";aqi<=200 -> "Unhealthy";aqi<=300 -> "Very unhealthy";else -> "Hazardous"
}
fun uvCategory(uv: Double): String = when {uv<3 -> "Low";uv<6 -> "Moderate";uv<8 -> "High";uv<11 -> "Very high";else -> "Extreme"}
private fun aqiColor(aqi: Double?)=when {aqi==null -> Color.Gray;aqi<=50 -> Color(0xFFABE3AD);aqi<=100 -> Color(0xFFFFD977);aqi<=150 -> Color(0xFFFFB17D);aqi<=200 -> Color(0xFFFF858D);else -> Color(0xFFCA9DEB)}

@Composable fun SmallInstrument(label: String,w: Weather,p: Settings,modifier: Modifier=Modifier) {
    val c=w.forecast.current
    val accent=MaterialTheme.colorScheme.primary
    val target=when(label) {
        "Humidity" -> c.relative_humidity_2m/100
        "UV index" -> w.forecast.daily.uv_index_max[0]/12
        "Air quality" -> (w.air?.current?.us_aqi ?: 0.0)/300
        "Precipitation" -> w.forecast.hourly.precipitation_probability[nextHour(w)]/100
        "Pressure" -> (c.surface_pressure-950)/100
        "Visibility" -> w.forecast.hourly.visibility[nextHour(w)]/20000
        else -> 0.0
    }.toFloat().coerceIn(0f,1f)
    var appeared by remember {mutableStateOf(false)}
    LaunchedEffect(target) {appeared=true}
    val progress by animateFloatAsState(if(appeared)target else 0f,tween(if(LocalReducedMotion.current)0 else 750),label="instrument")
    val color=when(label) {
        "Air quality" -> aqiColor(w.air?.current?.us_aqi)
        "UV index" -> when { w.forecast.daily.uv_index_max[0]<3 -> Color(0xFFA4DCA1);w.forecast.daily.uv_index_max[0]<6 -> Color(0xFFFFD576);w.forecast.daily.uv_index_max[0]<8 -> Color(0xFFFFB273);w.forecast.daily.uv_index_max[0]<11 -> Color(0xFFFF8D98);else -> Color(0xFFC4A0F1) }
        else -> accent
    }
    Canvas(modifier) {
        val center=Offset(size.width/2,size.height/2);val r=min(size.width,size.height)*.36f
        when(label) {
            "Wind" -> {
                drawCircle(color.copy(alpha=.10f),r*1.2f,center)
                repeat(24) { i -> val a=i*PI/12;val unit=Offset(cos(a).toFloat(),sin(a).toFloat());drawLine(color.copy(alpha=if(i%6==0).7f else .2f),center+unit*r,center+unit*(r+if(i%6==0)8 else 3),2f) }
                rotate(c.wind_direction_10m.toFloat(),center) {
                    val path=Path().apply {moveTo(center.x,center.y-r*.8f);lineTo(center.x-r*.3f,center.y+r*.5f);lineTo(center.x,center.y+r*.2f);lineTo(center.x+r*.3f,center.y+r*.5f);close()}
                    drawPath(path,Brush.linearGradient(listOf(Color.White,color),center-Offset(0f,r),center+Offset(0f,r)))
                }
            }
            "Humidity","Dew point" -> {
                val path=Path().apply {moveTo(center.x,center.y-r*1.2f);cubicTo(center.x-r*1.6f,center.y+r*.5f,center.x-r*.8f,center.y+r,center.x,center.y+r);cubicTo(center.x+r*.8f,center.y+r,center.x+r*1.6f,center.y+r*.5f,center.x,center.y-r*1.2f)}
                drawPath(path,Brush.linearGradient(listOf(Color(0xFFE5FAFF),Color(0xFF56C5F4),Color(0xFF254C83)),Offset(center.x-r,center.y-r),Offset(center.x+r,center.y+r)))
                if(label=="Humidity")clipPath(path) { drawRect(Color.White.copy(alpha=.15f),Offset(0f,size.height*(1-progress)),Size(size.width,size.height*progress)) }
                drawOval(Color.White.copy(alpha=.5f),Offset(center.x-r*.35f,center.y-r*.15f),Size(r*.16f,r*.5f))
            }
            "Sun & daylight" -> {
                drawArc(color.copy(alpha=.15f),180f,180f,false,Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(5f,cap=StrokeCap.Round))
                val d=w.forecast.daily;val f=daylightProgress(d.sunrise[0],d.sunset[0],w.forecast.timezone)
                val a=PI+PI*f;val at=center+Offset(cos(a).toFloat()*r,sin(a).toFloat()*r)
                drawCircle(Brush.radialGradient(listOf(color,Color.Transparent),at,20f),20f,at);drawCircle(Color(0xFFFFE5A0),9f,at)
                drawLine(color.copy(alpha=.25f),center-Offset(r*1.2f,0f),center+Offset(r*1.2f,0f),2f)
            }
            else -> {
                drawCircle(color.copy(alpha=.06f),r*1.3f,center)
                drawArc(color.copy(alpha=.12f),135f,270f,false,Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(9f,cap=StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(color.copy(alpha=.2f),color,Color.White)),135f,270f*progress,false,Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(9f,cap=StrokeCap.Round))
                if(label=="UV index") {
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.6f),Color.Transparent),center,r*.7f),r*.7f,center)
                    drawCircle(color,r*.25f,center)
                } else drawCircle(color.copy(alpha=.8f),r*.14f,center)
            }
        }
    }
}

@Composable fun MetricInstrument(label:String,w:Weather,p:Settings) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
        SmallInstrument(label,w,p,Modifier.size(210.dp))
        when(label) {
            "Air quality" -> { Text(metric(label,w,p),fontSize=42.sp);Text(aqiCategory(w.air?.current?.us_aqi),color=aqiColor(w.air?.current?.us_aqi),style=MaterialTheme.typography.headlineSmall) }
            "UV index" -> Text(uvCategory(w.forecast.daily.uv_index_max[0]),color=MaterialTheme.colorScheme.primary)
            "Pressure" -> Text("Gauge: 950–1050 hPa",color=MaterialTheme.colorScheme.primary)
            "Visibility" -> Text("Gauge: 0–20 km",color=MaterialTheme.colorScheme.primary)
            else -> Unit
        }
    }
}
@Composable fun WindInstrument(w:Weather,p:Settings) {
    val reduced=LocalReducedMotion.current
    val clock=rememberSceneClock(!reduced)
    val color=MaterialTheme.colorScheme.primary
    val angle by animateFloatAsState(w.forecast.current.wind_direction_10m.toFloat(),tween(if(reduced)0 else 800),label="wind bearing")
    Box(Modifier.fillMaxWidth().height(240.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center=Offset(size.width/2,size.height/2);val r=90.dp.toPx()
            repeat(48) { i -> val a=i*PI/24;val v=Offset(cos(a).toFloat(),sin(a).toFloat());drawLine(color.copy(alpha=if(i%12==0).8f else .25f),center+v*r,center+v*(r+if(i%12==0)12 else 5),2f) }
            rotate(angle,center) {
                val arrow=Path().apply {moveTo(center.x,center.y-r*.7f);lineTo(center.x-r*.2f,center.y+r*.35f);lineTo(center.x,center.y+r*.16f);lineTo(center.x+r*.2f,center.y+r*.35f);close()}
                drawPath(arrow,Brush.linearGradient(listOf(Color.White,color),center-Offset(0f,r),center+Offset(0f,r)))
            }
            if(!reduced)repeat(8) { i ->
                val a=angle*PI/180;val shift=((clock.value*.15f+i*.15f)%1f-.5f)*r*2
                val x=center.x+shift;val y=center.y+(i-4)*14
                drawLine(color.copy(alpha=.1f),Offset(x,y),Offset(x+sin(a).toFloat()*40,y-cos(a).toFloat()*40),2f)
            }
        }
        Text("N",Modifier.align(Alignment.TopCenter),color=color,fontSize=12.sp)
        Text("S",Modifier.align(Alignment.BottomCenter),color=color,fontSize=12.sp)
        Text("W",Modifier.align(Alignment.CenterStart),color=color,fontSize=12.sp)
        Text("E",Modifier.align(Alignment.CenterEnd),color=color,fontSize=12.sp)
    }
    val speed=w.forecast.current.wind_speed_10m.toFloat();val gust=w.forecast.current.wind_gusts_10m.toFloat()
    LinearProgressIndicator(progress={if(gust>0)(speed/gust).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth(),color=color)
    Text("Sustained wind relative to today's current gust reading",fontSize=12.sp,color=Color(0xFFA4A7AF))
}
@Composable fun SolarInstrument(w:Weather,p:Settings) {
    val d=w.forecast.daily
    val target=daylightProgress(d.sunrise[0],d.sunset[0],w.forecast.timezone)
    var appeared by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {appeared=true}
    val progress by animateFloatAsState(if(appeared)target else 0f,tween(if(LocalReducedMotion.current)0 else 900),label="daylight")
    val color=MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val cx=size.width/2;val baseline=size.height*.86f;val r=min(size.width*.42f,size.height*.75f)
        drawArc(color.copy(alpha=.13f),180f,180f,false,Offset(cx-r,baseline-r),Size(r*2,r*2),style=Stroke(5f,cap=StrokeCap.Round))
        drawArc(Brush.horizontalGradient(listOf(Color(0xFFFFAD80),Color(0xFFFFE193))),180f,180f*progress,false,Offset(cx-r,baseline-r),Size(r*2,r*2),style=Stroke(5f,cap=StrokeCap.Round))
        val a=PI+PI*progress;val sun=Offset(cx+cos(a).toFloat()*r,baseline+sin(a).toFloat()*r)
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFD584),Color.Transparent),sun,35.dp.toPx()),35.dp.toPx(),sun)
        drawCircle(Color(0xFFFFDE95),12.dp.toPx(),sun)
        drawLine(Color(0xFF404147),Offset(cx-r-15,baseline),Offset(cx+r+15,baseline),2f)
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(forecastTime(d.sunrise[0],p.time24));Text(forecastTime(d.sunset[0],p.time24))}
    Text("${(target*100).toInt()}% of today's daylight elapsed",color=color)
}
@Composable fun RainSpark(values:List<Double>,modifier:Modifier) {
    var appeared by remember(values) {mutableStateOf(false)}
    LaunchedEffect(values) {appeared=true}
    val reveal by animateFloatAsState(if(appeared)1f else 0f,tween(if(LocalReducedMotion.current)0 else 700),label="rain timeline")
    Canvas(modifier) {
        if(values.isEmpty())return@Canvas
        val gap=size.width/values.size
        values.forEachIndexed { i,v ->
            val h=(v.coerceIn(0.0,100.0)/100*size.height*reveal).toFloat()
            drawRoundRect(Color(0xFF67CFF3).copy(alpha=.2f+i.toFloat()/values.size*.6f),Offset(i*gap+3,size.height-max(h,3f)),Size((gap-6).coerceAtLeast(1f),max(h,3f)),CornerRadius(6f))
        }
    }
}
