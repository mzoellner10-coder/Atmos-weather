package com.atmos.weather.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.atmos.weather.data.*
import com.atmos.weather.experience.*
import com.atmos.weather.graphics.WeatherArt
import kotlinx.coroutines.launch

@Composable fun WeatherDashboard(state: WeatherState, prefs: Settings, open: (String)->Unit, refresh: ()->Unit, places: ()->Unit) {
    val weather = state.weather
    val accent=MaterialTheme.colorScheme.primary
    val reduced=LocalReducedMotion.current
    if(weather==null) {
        Column(Modifier.fillMaxSize().padding(30.dp),verticalArrangement=Arrangement.Center) {
            Text("ATMOS / 02",letterSpacing=5.sp,color=accent,fontSize=13.sp)
            Spacer(Modifier.height(26.dp))
            Text("Weather.\nWith feeling.",fontSize=56.sp,lineHeight=58.sp,fontWeight=FontWeight.Black,letterSpacing=(-3).sp)
            Spacer(Modifier.height(24.dp))
            Text(state.error ?: if(state.loading)"Finding your forecast…" else "Your sky, in a whole new dimension. Start with a city or your current location.",color=Color(0xFFA4A6AD),lineHeight=25.sp)
            if(state.loading)LinearProgressIndicator(Modifier.padding(top=20.dp).fillMaxWidth(),color=accent)
            Button(onClick=if(prefs.city==null)places else refresh,modifier=Modifier.padding(top=24.dp),shape=RoundedCornerShape(30.dp)) {Text(if(prefs.city==null)"Find my atmosphere ↗" else "Try again")}
        }
        return
    }
    val w=weather;val c=w.forecast.current;val daily=w.forecast.daily
    val listState=rememberLazyListState()
    val pager=rememberPagerState(pageCount={3});val scope=rememberCoroutineScope();val feedback=rememberFeedback()
    var expanded by remember {mutableStateOf(false)}
    val background=if(prefs.visualTheme=="Dynamic") Brush.verticalGradient(listOf(accent.copy(alpha=.16f),Color.Black,accent.copy(alpha=.06f))) else Brush.verticalGradient(listOf(Color.Black,Color.Black))
    Box(Modifier.fillMaxSize().background(background)) {
    if(prefs.visualTheme=="Dynamic")DynamicBackdrop(accent)
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top=20.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=26.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).clickable(onClick=places)) {
                        Text("ATMOS / 02",fontSize=10.sp,letterSpacing=3.sp,color=accent)
                        Text(w.city.name+" ⌄",fontSize=25.sp,fontWeight=FontWeight.Bold,maxLines=2)
                    }
                    Surface(color=accent.copy(alpha=.12f),shape=RoundedCornerShape(50)) {Text(condition(c.weather_code),color=accent,fontSize=12.sp,modifier=Modifier.padding(horizontal=14.dp,vertical=9.dp))}
                }
                Column(Modifier.fillMaxWidth().padding(top=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    AnimatedContent(temperature(c.temperature_2m,prefs.fahrenheit),label="temperature",transitionSpec={fadeIn(tween(if(reduced)0 else 380)) togetherWith fadeOut(tween(if(reduced)0 else 180))}) { value ->
                        Text(value,fontSize=116.sp,lineHeight=128.sp,fontWeight=FontWeight.Black,letterSpacing=(-9).sp)
                    }
                    Text("H ${temperature(daily.temperature_2m_max[0],prefs.fahrenheit)}   /   L ${temperature(daily.temperature_2m_min[0],prefs.fahrenheit)}    ·    Feels ${temperature(c.apparent_temperature,prefs.fahrenheit)}",color=Color(0xFFB8B9C0),fontSize=14.sp)
                }
                Crossfade(c,label="weather sculpture",animationSpec=tween(if(reduced)0 else 650)) { current ->
                    WeatherScene(current,parallax={if(listState.firstVisibleItemIndex==0)listState.firstVisibleItemScrollOffset.toFloat() else 0f})
                }
                Text("${if(state.cached)"CACHED FORECAST" else "UPDATED"}  ·  ${updateTime(w.fetchedAt,w.forecast.timezone,prefs.time24)}",fontSize=10.sp,letterSpacing=1.sp,color=Color(0xFF92959E),modifier=Modifier.align(Alignment.CenterHorizontally))
            }
        }
        if(state.error!=null)item { WeatherCard("CONNECTION",Modifier.padding(horizontal=24.dp)) {Text(state.error);TextButton(onClick=refresh){Text("Try again")}} }
        w.alerts?.features.orEmpty().forEach { a -> item { WeatherCard("OFFICIAL WEATHER ALERT",Modifier.padding(horizontal=24.dp),{open("Alerts")}) {Text(a.properties.headline ?: a.properties.event ?: "NWS alert",fontWeight=FontWeight.SemiBold)} } }
        if(w.alertsUnavailable)item {Text("Official alerts unavailable. Check weather.gov for local warnings.",color=accent,modifier=Modifier.padding(horizontal=26.dp),fontSize=12.sp)}
        item {
            WeatherCard("RADAR · RECENT HISTORY",Modifier.padding(horizontal=24.dp),{open("Radar")}) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Watch the rain move",fontSize=23.sp,fontWeight=FontWeight.Bold)
                        Text("Observed radar around ${w.city.name}",fontSize=12.sp,color=Color(0xFFADB1BC))
                    }
                    Text("↗",fontSize=30.sp,color=accent,modifier=Modifier.padding(start=12.dp))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf("Hourly","16 days","Rain").forEachIndexed { i,name ->
                        FilterChip(selected=pager.currentPage==i,onClick={feedback();scope.launch {if(reduced)pager.scrollToPage(i) else pager.animateScrollToPage(i)}},label={Text(name)})
                    }
                }
                HorizontalPager(state=pager,modifier=Modifier.fillMaxWidth().height(300.dp),contentPadding=PaddingValues(horizontal=24.dp),pageSpacing=16.dp) { page ->
                    Surface(color=Color(0xFF101114),shape=RoundedCornerShape(32.dp),modifier=Modifier.fillMaxSize()) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                Text(listOf("THE NEXT 24 HOURS","YOUR NEXT 16 DAYS","PRECIPITATION")[page],fontSize=11.sp,letterSpacing=1.sp,color=accent)
                                TextButton(onClick={open(listOf("Hourly","16-day","Precipitation")[page])}) {Text("Explore ↗")}
                            }
                            when(page) {
                                0 -> HourlyStrip(w,prefs)
                                1 -> DailyRows(w,prefs,4)
                                else -> {
                                    val start=nextHour(w)
                                    RainSpark(w.forecast.hourly.precipitation_probability.drop(start).take(12),Modifier.fillMaxWidth().height(80.dp))
                                    Text(metric("Precipitation",w,prefs),fontSize=36.sp,fontWeight=FontWeight.Bold)
                                    Text("Today's forecast total",color=Color(0xFFA3A5AE),fontSize=13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal=26.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("A closer look",fontSize=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                    TextButton(onClick={feedback();expanded=!expanded}) {Text(if(expanded)"Less −" else "More +")}
                }
                val labels=if(expanded)listOf("Wind","Humidity","UV index","Air quality","Sun & daylight","Precipitation","Pressure","Visibility","Dew point") else listOf("Wind","Humidity","UV index","Air quality")
                Column(Modifier.animateContentSize(tween(if(reduced)0 else 350))) {
                    labels.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                            row.forEach { label ->
                                Column(Modifier.weight(1f).clickable {open(label)},horizontalAlignment=Alignment.CenterHorizontally) {
                                    SmallInstrument(label,w,prefs,Modifier.size(115.dp))
                                    Text(metric(label,w,prefs),fontSize=22.sp,fontWeight=FontWeight.Bold)
                                    Text(label+" ↗",fontSize=12.sp,color=Color(0xFFA3A5AD),modifier=Modifier.padding(top=5.dp))
                                }
                            }
                            if(row.size==1)Spacer(Modifier.weight(1f))
                        }
                        HorizontalDivider(color=Color(0xFF222327))
                    }
                }
            }
        }
        item { Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            TextButton(onClick=refresh) {Text("Refresh my sky ↻")}
            Text("Open-Meteo  /  NOAA · Local time in ${w.forecast.timezone}",fontSize=10.sp,color=Color(0xFF898D96),modifier=Modifier.padding(horizontal=24.dp))
        } }
    }
    }
}
