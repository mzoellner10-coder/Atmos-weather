package com.atmos.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import com.atmos.weather.data.*
import com.atmos.weather.experience.*
import com.atmos.weather.radar.*
import kotlinx.coroutines.*
import kotlin.math.roundToInt

@Composable fun RadarScreen(city: City?, zone: String, prefs: Settings, source: RadarSource? = null) {
    val context = LocalContext.current
    val repository = source ?: remember(context) { RadarRepository.get(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val active = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val reduced = LocalReducedMotion.current
    var timeline by remember { mutableStateOf<RadarTimeline?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var readyFrame by remember { mutableStateOf<Long?>(null) }
    val refreshCue = rememberFeedback(UiCue.Refresh)
    val selectCue = rememberFeedback(UiCue.Select)
    val playCue = rememberFeedback(UiCue.Play)
    val pauseCue = rememberFeedback(UiCue.Pause)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP) playing=false
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(repository, reload, lifecycle, city != null) {
        if(city == null) { loading = false; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(isActive) {
                loading = true
                try {
                    val next = repository.timeline(force = reload > 0)
                    val oldTime = timeline?.frames?.getOrNull(index)?.time
                    val wasLatest = timeline == null || index == timeline!!.frames.lastIndex
                    index = if(wasLatest) next.frames.lastIndex else next.frames.indexOfFirst { it.time == oldTime }.takeIf { it >= 0 } ?: next.frames.lastIndex
                    timeline = next
                    error = if(next.cached) "Offline · showing saved radar history." else null
                } catch(e: CancellationException) { throw e }
                catch(_: Exception) { error = "Radar unavailable. Check your connection and try again."; playing = false }
                finally { loading = false }
                delay(300_000)
            }
        }
    }
    val frames = timeline?.frames.orEmpty()
    val frame = frames.getOrNull(index)
    LaunchedEffect(playing, frame?.time, readyFrame, active, reduced) {
        if(reduced || !active) { playing = false; return@LaunchedEffect }
        if(playing && frame != null && readyFrame == frame.time) {
            delay(if(index == frames.lastIndex) 1600 else 800)
            index = (index + 1) % frames.size
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rain in motion",fontSize=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                Text(city?.name ?: "Choose a place to center the map",color=MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick={refreshCue();playing=false;reload++},enabled=!loading) { Text("Refresh") }
        }
        Text("OBSERVED RADAR · RECENT HISTORY",fontSize=10.sp,letterSpacing=1.sp,color=MaterialTheme.colorScheme.primary)
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,color=MaterialTheme.colorScheme.primary,fontSize=13.sp) }
        if(city == null) {
            Text("Select a saved city in Places to see radar around it.")
            return@Column
        }
        if(timeline != null && frame != null) {
            Text(updateTime(frame.time*1000,zone,prefs.time24),fontWeight=FontWeight.Bold,fontSize=20.sp,
                modifier=Modifier.semantics {
                    contentDescription="Selected radar observation"
                    stateDescription=updateTime(frame.time*1000,zone,prefs.time24)
                })
            if(System.currentTimeMillis()-frames.last().time*1000 > 1_200_000)
                Text("Delayed radar · latest observation ${updateTime(frames.last().time*1000,zone,prefs.time24)}",fontSize=12.sp)
            RadarMap(city,timeline!!,frame,repository,reload) { ready -> readyFrame = ready }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={selectCue();playing=false;index=(index-1).coerceAtLeast(0)},enabled=index>0) { Text("Previous") }
                Button(onClick={if(playing)pauseCue() else playCue();playing=!playing},enabled=frames.size>1 && !reduced) { Text(if(playing)"Pause" else "Play") }
                TextButton(onClick={selectCue();playing=false;index=(index+1).coerceAtMost(frames.lastIndex)},enabled=index<frames.lastIndex) { Text("Next") }
            }
            Slider(value=index.toFloat(),onValueChange={ playing=false;index=it.roundToInt().coerceIn(frames.indices) },onValueChangeFinished=selectCue,
                valueRange=0f..frames.lastIndex.coerceAtLeast(1).toFloat(),steps=(frames.size-2).coerceAtLeast(0),enabled=frames.size>1,
                modifier=Modifier.semantics {contentDescription="Radar history timeline"})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(updateTime(frames.first().time*1000,zone,prefs.time24),fontSize=10.sp)
                Text("Latest observed",fontSize=10.sp)
            }
            if(reduced) Text("Playback is paused with reduced motion. Use Previous, Next, or the timeline.",fontSize=12.sp)
            Text("RainViewer’s Universal Blue palette shows precipitation intensity; snow may use different colors.",fontSize=11.sp,color=Color(0xFFADB1BC))
        } else if(!loading) {
            Text("No radar imagery is available right now. Your forecast is still available on Weather.")
        }
        Text("Regional view · drag to pan; use + / − to zoom. Blank areas can mean no precipitation or no radar coverage. This is observed history, not a forecast or an official warning map.",fontSize=12.sp,color=Color(0xFFADB1BC))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.rainviewer.com/")))}) { Text("Radar: RainViewer",fontSize=11.sp) }
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.openstreetmap.org/copyright")))}) { Text("© OpenStreetMap contributors",fontSize=11.sp) }
        }
        Text("Times in $zone. Visible map areas are sent to RainViewer and OpenStreetMap; tiles are cached on this device.",fontSize=11.sp,color=Color(0xFFADB1BC))
    }
}

private data class TileBatch(val images: Map<String,ImageBitmap> = emptyMap(),val loading: Boolean = true,val failed: Boolean = false)

@Composable private fun loadTiles(urls: List<String>, source: RadarSource, retry: Int): TileBatch {
    var batch by remember { mutableStateOf(TileBatch()) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(urls,source,lifecycle,retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        val retained = batch.images.filterKeys { it in urls }
        batch = TileBatch(retained)
        delay(120) // Do not request intermediate viewports during a drag or slider scrub.
        val loaded = coroutineScope {
            urls.distinct().map { url -> async {
                try { url to (retained[url] ?: source.tile(url).asImageBitmap()) }
                catch(e: CancellationException) { throw e }
                catch(_: Exception) { null }
            } }.awaitAll()
        }
        batch = TileBatch(loaded.filterNotNull().toMap(),loading=false,failed=loaded.any { it==null })
        }
    }
    // A frame is never treated as loaded while its effect is still waiting to start.
    return if(urls.any { it !in batch.images } && !batch.failed) batch.copy(loading=true) else batch
}

@Composable private fun RadarMap(city: City, timeline: RadarTimeline, frame: RadarFrame, source: RadarSource,
    retry: Int, onReady: (Long?)->Unit) {
    val target = remember(city.latitude,city.longitude) { RadarProjection.point(city.latitude,city.longitude) }
    var center by remember(target) { mutableStateOf(target) }
    var zoom by remember { mutableIntStateOf(6) }
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    val tileSize = with(LocalDensity.current) { 256.dp.toPx() }
    val tiles = remember(center,zoom,dimensions,tileSize) { RadarProjection.tiles(center,zoom,dimensions.width,dimensions.height,tileSize) }
    val baseUrls = tiles.map { "https://tile.openstreetmap.org/$zoom/${it.x}/${it.y}.png" }
    val radarUrls = tiles.map { RadarProtocol.tileUrl(timeline,frame,it,zoom) }
    val base = loadTiles(baseUrls,source,retry)
    val radar = loadTiles(radarUrls,source,retry)
    val ready = !radar.loading && !radar.failed && radarUrls.isNotEmpty() && radarUrls.all { it in radar.images }
    LaunchedEffect(ready,frame.time) { onReady(if(ready)frame.time else null) }
    val zoomCue = rememberFeedback(UiCue.Zoom)
    val centerCue = rememberFeedback(UiCue.Confirm)
    Box(Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1D252E))) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { dimensions=it }
            .semantics {contentDescription="Radar map around ${city.name}. Zoom $zoom of 7. Drag to pan."}
            .pointerInput(zoom,tileSize) { detectDragGestures { change, delta -> change.consume(); center=RadarProjection.pan(center,delta.x,delta.y,zoom,tileSize) } }) {
            tiles.forEachIndexed { i,tile ->
                val location = IntOffset(tile.left.roundToInt(),tile.top.roundToInt())
                val extent = IntSize(tileSize.roundToInt()+1,tileSize.roundToInt()+1)
                base.images[baseUrls[i]]?.let { drawImage(it,dstOffset=location,dstSize=extent) }
                // Never show a partial radar mosaic as if missing tiles were clear weather.
                if(ready) radar.images[radarUrls[i]]?.let { drawImage(it,dstOffset=location,dstSize=extent,alpha=.8f) }
            }
            val marker=RadarProjection.marker(target,center,zoom,dimensions.width,dimensions.height,tileSize)
            val position=Offset(marker.first,marker.second)
            drawCircle(Color.Black.copy(alpha=.6f),8.dp.toPx(),position)
            drawCircle(Color.White,5.dp.toPx(),position)
            drawCircle(Color(0xFF167CCC),3.dp.toPx(),position)
        }
        Surface(Modifier.align(Alignment.TopStart).padding(10.dp),color=Color.Black.copy(alpha=.85f),shape=RoundedCornerShape(12.dp)) {
            Text(when {radar.failed->"Radar tiles unavailable · Refresh to retry";radar.loading->"Loading radar…";base.failed->"Base map unavailable · radar loaded";else->"Observed · zoom $zoom"},Modifier.padding(10.dp),fontSize=11.sp,color=Color.White)
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(start=8.dp,end=105.dp,bottom=8.dp),color=Color.Black.copy(alpha=.85f),shape=RoundedCornerShape(6.dp)) {
            Text("© OpenStreetMap contributors\nRadar: RainViewer",Modifier.padding(6.dp),fontSize=9.sp,lineHeight=12.sp,color=Color.White)
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(8.dp),horizontalAlignment=Alignment.End) {
            FilledTonalButton(onClick={zoomCue();zoom++},enabled=zoom<RadarProtocol.MAX_ZOOM,contentPadding=PaddingValues(0.dp),modifier=Modifier.size(44.dp).semantics {contentDescription="Zoom in"}) {Text("+",fontSize=24.sp)}
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick={zoomCue();zoom--},enabled=zoom>2,contentPadding=PaddingValues(0.dp),modifier=Modifier.size(44.dp).semantics {contentDescription="Zoom out"}) {Text("−",fontSize=24.sp)}
            FilledTonalButton(onClick={centerCue();center=target},contentPadding=PaddingValues(horizontal=12.dp)) {Text("Center",fontSize=12.sp)}
        }
    }
}
