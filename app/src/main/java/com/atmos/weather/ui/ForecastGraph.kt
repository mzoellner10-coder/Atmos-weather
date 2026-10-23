package com.atmos.weather.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.atmos.weather.data.forecastTime
import com.atmos.weather.experience.*
import kotlin.math.*

@Composable internal fun InteractiveGraph(values:List<Double>,times:List<String>,label:String,format:(Double)->String) {
    if(values.isEmpty() || times.isEmpty())return
    var selected by remember(values) {mutableIntStateOf(0)}
    var appeared by remember(values) {mutableStateOf(false)}
    LaunchedEffect(values) {appeared=true}
    val reveal by animateFloatAsState(if(appeared)1f else 0f,tween(if(LocalReducedMotion.current)0 else 700),label="draw forecast")
    val feedback=rememberFeedback();val prefs=LocalExperience.current;val accent=MaterialTheme.colorScheme.primary
    fun select(x:Float,width:Int) {val next=(x/width.coerceAtLeast(1)*(values.size-1)).roundToInt().coerceIn(values.indices);if(next!=selected){selected=next;feedback()}}
    WeatherCard(label.uppercase()) {
        Text("${forecastTime(times[selected],prefs.time24)} · ${format(values[selected])}",fontSize=27.sp)
        Canvas(Modifier.fillMaxWidth().height(175.dp)
            .semantics {contentDescription="$label. ${forecastTime(times[selected],prefs.time24)}, ${format(values[selected])}. Use previous and next hour controls to explore."}
            .pointerInput(values) {detectTapGestures {select(it.x,size.width)}}
            .pointerInput(values) {detectDragGestures {change,_->change.consume();select(change.position.x,size.width)}}) {
            val min=values.min();val span=(values.max()-min).coerceAtLeast(1.0)
            fun point(i:Int)=Offset(i.toFloat()/(values.size-1).coerceAtLeast(1)*size.width,size.height-15-(values[i]-min).toFloat()/span.toFloat()*(size.height-30))
            repeat(4) {i->val y=15+i*(size.height-30)/3;drawLine(Color(0xFF2B2D33),Offset(0f,y),Offset(size.width,y),1f)}
            val path=Path()
            values.indices.forEach {i->val pt=point(i);if(i==0)path.moveTo(pt.x,pt.y) else {val previous=point(i-1);val mid=(pt.x+previous.x)/2;path.cubicTo(mid,previous.y,mid,pt.y,pt.x,pt.y)}}
            clipRect(right=size.width*reveal) {
                val fill=Path().apply {addPath(path);lineTo(size.width,size.height);lineTo(0f,size.height);close()}
                drawPath(fill,Brush.verticalGradient(listOf(accent.copy(alpha=.2f),Color.Transparent)))
                drawPath(path,accent,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
            }
            val pt=point(selected)
            drawLine(Color.White.copy(alpha=.2f),Offset(pt.x,0f),Offset(pt.x,size.height),1f)
            drawCircle(Color(0xFF121316),7.dp.toPx(),pt);drawCircle(accent,4.dp.toPx(),pt)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {listOf(0,values.lastIndex/2,values.lastIndex).forEach {Text(forecastTime(times[it],prefs.time24),fontSize=11.sp,color=accent)}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={feedback();selected=(selected-1).coerceAtLeast(0)},enabled=selected>0) {Text("Previous hour")}
            TextButton(onClick={feedback();selected=(selected+1).coerceAtMost(values.lastIndex)},enabled=selected<values.lastIndex) {Text("Next hour")}
        }
    }
}
