package com.atmos.weather.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

data class RadarFrame(val time: Long, val path: String)
data class RadarTimeline(val host: String, val generated: Long, val frames: List<RadarFrame>, val cached: Boolean = false)

/** RainViewer's personal API: observed history only, Universal Blue PNGs, native zoom <= 7. */
object RadarProtocol {
    const val MAX_ZOOM = 7
    fun parse(json: String): RadarTimeline {
        val root = JsonParser.parseString(json).asJsonObject
        val host = root["host"].asString.toHttpUrl()
        require(host.scheme == "https" && host.host.endsWith(".rainviewer.com") &&
            host.username.isEmpty() && host.password.isEmpty() && host.port == 443 &&
            host.encodedPath == "/" && host.query == null && host.fragment == null)
        val generated = root["generated"].asLong
        require(generated > 0)
        val frames = root.getAsJsonObject("radar").getAsJsonArray("past").mapNotNull { item ->
            runCatching {
                val obj = item.asJsonObject
                val time = obj["time"].asLong
                val path = obj["path"].asString
                require(time > 0 && time <= generated && path.matches(Regex("/v2/radar/[A-Za-z0-9/_-]+")))
                RadarFrame(time, path)
            }.getOrNull()
        }.distinctBy { it.time }.sortedBy { it.time }.takeLast(13)
        require(frames.isNotEmpty()) { "No observed radar frames available" }
        return RadarTimeline(host.toString().trimEnd('/'), generated, frames)
    }
    fun tileUrl(timeline: RadarTimeline, frame: RadarFrame, tile: MapTile, zoom: Int): String {
        require(zoom in 2..MAX_ZOOM && frame in timeline.frames)
        val n = 1 shl zoom
        require(tile.x in 0 until n && tile.y in 0 until n)
        return "${timeline.host}${frame.path}/512/$zoom/${tile.x}/${tile.y}/2/1_1.png"
    }
}

data class MapPoint(val x: Double, val y: Double)
data class MapTile(val x: Int, val y: Int, val left: Float, val top: Float)

/** Normalized Web Mercator coordinates. Longitude wraps, latitude stops at the map poles. */
object RadarProjection {
    fun point(latitude: Double, longitude: Double): MapPoint {
        val lat = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
        return MapPoint(wrap((longitude + 180) / 360), ((1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2).coerceIn(0.0, 1.0))
    }
    fun wrap(x: Double) = ((x % 1) + 1) % 1
    fun pan(center: MapPoint, dx: Float, dy: Float, zoom: Int, tileSize: Float): MapPoint {
        val world = tileSize * (1 shl zoom)
        return MapPoint(wrap(center.x - dx / world), (center.y - dy / world).coerceIn(0.0, 1.0))
    }
    fun tiles(center: MapPoint, zoom: Int, width: Int, height: Int, size: Float): List<MapTile> {
        require(zoom in 2..RadarProtocol.MAX_ZOOM && size > 0)
        if(width <= 0 || height <= 0) return emptyList()
        val n = 1 shl zoom
        val left = center.x * n - width / (2 * size)
        val top = center.y * n - height / (2 * size)
        return buildList {
            for(y in floor(top).toInt()..floor(top + height / size).toInt()) {
                if(y !in 0 until n) continue
                for(x in floor(left).toInt()..floor(left + width / size).toInt())
                    add(MapTile(((x % n) + n) % n, y, ((x-left)*size).toFloat(), ((y-top)*size).toFloat()))
            }
        }
    }
    fun marker(point: MapPoint, center: MapPoint, zoom: Int, width: Int, height: Int, size: Float): Pair<Float,Float> {
        val dx = ((point.x - center.x + 1.5) % 1) - .5
        val world = size * (1 shl zoom)
        return Pair((width / 2 + dx * world).toFloat(), (height / 2 + (point.y-center.y)*world).toFloat())
    }
}

interface RadarSource {
    suspend fun timeline(force: Boolean = false): RadarTimeline
    suspend fun tile(url: String): Bitmap
}

class RadarRepository internal constructor(context: Context, suppliedClient: OkHttpClient? = null,
    private val manifestUrl: String = "https://api.rainviewer.com/public/weather-maps.json"): RadarSource {
    private val directory = File(context.cacheDir, "radar").apply { mkdirs() }
    private val manifest = File(directory, "timeline.json")
    private val client = suppliedClient ?: OkHttpClient.Builder()
        .cache(Cache(File(directory, "http"), 40L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
            .header("User-Agent", "AtmosWeather/2.1 (Android; https://github.com/mzoellner10-coder/Atmos-weather)").build()) }.build()
    private val mutex = Mutex()
    private val permits = Semaphore(4)
    private var current: RadarTimeline? = null
    private var fetchedAt = 0L

    override suspend fun timeline(force: Boolean): RadarTimeline = withContext(Dispatchers.IO) {
        mutex.withLock {
            if(!force && current != null && System.currentTimeMillis()-fetchedAt < 300_000) return@withLock current!!
            try {
                val raw = client.newCall(Request.Builder().url(manifestUrl)
                    .cacheControl(CacheControl.FORCE_NETWORK).build()).await().use { response ->
                    check(response.isSuccessful) { "Radar service unavailable" }
                    val body = response.body ?: error("Missing radar response")
                    require(body.contentLength() <= 256_000)
                    val input = body.source()
                    require(!input.request(256_001))
                    input.buffer.readUtf8()
                }
                val parsed = RadarProtocol.parse(raw)
                require(parsed.generated * 1000 <= System.currentTimeMillis() + 600_000)
                File(directory,"timeline.tmp").apply { writeText(raw); renameTo(manifest) }
                current = parsed
                fetchedAt = System.currentTimeMillis()
                parsed
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                val saved = current ?: runCatching { RadarProtocol.parse(manifest.readText()) }.getOrNull()
                if(saved != null && System.currentTimeMillis()-saved.generated*1000 in 0..86_400_000)
                    saved.copy(cached = true)
                else throw IOException("Radar unavailable. Check your connection and try again.",e)
            }
        }
    }

    /** Only the visible frame/viewport is requested. OkHttp honors providers' HTTP cache headers. */
    override suspend fun tile(url: String): Bitmap = permits.withPermit {
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                check(response.isSuccessful) { "Map tile unavailable" }
                val input = response.body!!.source()
                require(!input.request(2_000_001))
                val bytes = input.buffer.readByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512)
                BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("Invalid map tile")
            }
        }
    }
    companion object {
        @Volatile private var instance: RadarRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: RadarRepository(context.applicationContext).also { instance = it }
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if(!continuation.isCancelled) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
