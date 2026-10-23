package com.atmos.weather.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.atmos.weather.data.*
import com.atmos.weather.experience.*
import com.atmos.weather.graphics.WeatherArt
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable fun WeatherSculpture(code: Int, day: Boolean = true, modifier: Modifier = Modifier) {
    val image = remember(code, day) { WeatherArt.bitmap(code, day, 192).asImageBitmap() }
    Canvas(modifier.semantics { contentDescription = condition(code) }) {
        drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    }
}

@Composable fun WeatherScene(current: Current, modifier: Modifier = Modifier, parallax: () -> Float = { 0f }) {
    val prefs = LocalExperience.current
    val reduced = LocalReducedMotion.current || prefs.motionMode == "Minimal"
    val clock = rememberSceneClock(!reduced)
    val feedback = rememberFeedback(UiCue.Sculpture)
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }; val dragY = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val code = current.weather_code; val day = current.is_day == 1
    val image = remember(code, day) { WeatherArt.bitmap(code, day, 640).asImageBitmap() }
    val accent = Color(WeatherArt.accent(code, day, current.wind_speed_10m))
    Canvas(modifier.fillMaxWidth().height(330.dp)
        .semantics { contentDescription = "${condition(code)} weather sculpture. Tap for a glow, drag to tilt." }
        .pointerInput(reduced) { detectTapGestures {
            feedback()
            if (!reduced) scope.launch { pulse.snapTo(1f); pulse.animateTo(0f, spring(dampingRatio=.55f, stiffness=80f)) }
        } }
        .pointerInput(reduced) { detectHorizontalDragGestures(onDragEnd={
            scope.launch { dragX.animateTo(0f, spring(.55f,100f)) }
            scope.launch { dragY.animateTo(0f, spring(.55f,100f)) }
        }, onDragCancel={ scope.launch { dragX.animateTo(0f);dragY.animateTo(0f) } }) { change, amount ->
            if (!reduced) {
                change.consume()
                scope.launch { dragX.snapTo((dragX.value+amount).coerceIn(-70f,70f));dragY.snapTo((dragY.value+amount*.15f).coerceIn(-50f,50f)) }
            }
        } }) {
        val t = clock.value
        val center = Offset(size.width*.5f,size.height*.48f)
        val radius = min(size.width,size.height)*.65f
        val glow = (if(prefs.visualTheme=="OLED Black")0f else .10f) + pulse.value.coerceIn(0f,1f)*.15f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=glow),Color.Transparent),center,radius),radius,center)
        val diameter = min(size.width*.94f,size.height*1.16f)
        val dx = if(reduced)0f else dragX.value*.25f+sin(t*.6f)*5f
        val dy = if(reduced)0f else dragY.value*.2f+sin(t*.85f)*7f+parallax().coerceIn(-100f,100f)*.08f
        withTransform({
            translate(center.x-diameter/2+dx,center.y-diameter/2+dy)
            rotate(if(code==0 && day)t*2f else if(reduced)0f else sin(t*.45f)*1.6f,Offset(diameter/2,diameter/2))
            scale(1f+pulse.value*.055f,1f+pulse.value*.035f,Offset(diameter/2,diameter/2))
        }) { drawImage(image,dstSize=IntSize(diameter.toInt(),diameter.toInt())) }
        if(!reduced) {
            val full = prefs.motionMode=="Full"
            val snowy=code in 71..77 || code==85 || code==86
            val rainy=code in 51..67 || code in 80..82 || code>=95
            val wind=(current.wind_speed_10m/65).coerceIn(0.0,1.0).toFloat()
            val direction=sin(Math.toRadians(current.wind_direction_10m)).toFloat()
            if(rainy || snowy) repeat(if(full)42 else 18) { i ->
                val speed=if(snowy).11f else .6f+current.precipitation.toFloat().coerceAtMost(5f)*.06f
                val phase=(i*.137f+t*speed)%1f
                val x=((i*.347f+direction*t*.05f*wind)%1f+1f)%1f*size.width
                val y=phase*size.height
                if(snowy) {
                    val at=Offset(x+sin(t+i)*8,y)
                    drawCircle(Color(0xFFDBF3FF).copy(alpha=.4f),if(full)3f else 2f,at)
                } else {
                    drawLine(accent.copy(alpha=.28f),Offset(x,y),Offset(x+direction*wind*12,y+18),2f,StrokeCap.Round)
                    if(phase>.93f)drawOval(accent.copy(alpha=(1-phase)*2),Offset(x-5,size.height*.94f),androidx.compose.ui.geometry.Size(12f,3f),style=Stroke(1f))
                }
            }
            if(!day)repeat(24) { i ->
                val x=(i*137%997)/997f*size.width;val y=(i*53%811)/811f*size.height
                drawCircle(accent.copy(alpha=.22f+.12f*sin(t*.6f+i)),if(i%4==0)2.4f else 1.2f,Offset(x,y))
            }
            if(current.wind_speed_10m>=20)repeat(5) { i ->
                val x=((t*.12f+i*.27f)%1.4f-.2f)*size.width
                val y=size.height*(.3f+i*.12f)
                val path=Path().apply {moveTo(x,y);cubicTo(x+35,y-12,x+65,y+12,x+115,y)}
                drawPath(path,Color(0xFF7CDCD5).copy(alpha=.14f+wind*.1f),style=Stroke(2f))
            }
        }
    }
}

@Composable fun DynamicBackdrop(accent: Color) {
    val clock=rememberSceneClock(!LocalReducedMotion.current)
    Canvas(Modifier.fillMaxSize()) {
        val t=clock.value
        val center=Offset(size.width*(.5f+sin(t*.08f)*.18f),size.height*(.18f+cos(t*.06f)*.04f))
        val r=size.width*1.1f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=.13f),Color.Transparent),center,r),r,center)
    }
}
