package com.atmos.weather

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.atmos.weather.data.SettingsCodec
import com.atmos.weather.experience.*
import com.atmos.weather.radar.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private fun manifest(time: Long = System.currentTimeMillis()/1000): String = """{
 "host":"https://tilecache.rainviewer.com","generated":$time,
 "radar":{"past":[{"time":${time-600},"path":"/v2/radar/${time-600}"},{"time":$time,"path":"/v2/radar/$time"}],
 "nowcast":[{"time":${time+600},"path":"/v2/radar/future"}]}}
"""

class RadarProtocolTest {
    @Test fun observedFramesAreSortedDeduplicatedAndFutureLayersAreExcluded() {
        val timeline=RadarProtocol.parse(manifest(2000))
        assertEquals(listOf(1400L,2000L),timeline.frames.map {it.time})
        assertEquals("https://tilecache.rainviewer.com/v2/radar/2000/512/6/33/22/2/1_1.png",
            RadarProtocol.tileUrl(timeline,timeline.frames.last(),MapTile(33,22,0f,0f),6))
        assertThrows(IllegalArgumentException::class.java) {
            RadarProtocol.tileUrl(timeline,timeline.frames.last(),MapTile(33,22,0f,0f),8)
        }
        val many=(1L..20).reversed().joinToString(",") {"""{"time":$it,"path":"/v2/radar/$it"}"""}
        val result=RadarProtocol.parse("""{"host":"https://tilecache.rainviewer.com","generated":20,"radar":{"past":[$many,$many]}}""")
        assertEquals((8L..20).toList(),result.frames.map {it.time})
    }
    @Test fun metadataRejectsUntrustedHostsUnsafePathsAndEmptyCoverage() {
        listOf("https://rainviewer.com.evil.example","http://tilecache.rainviewer.com","https://user@tilecache.rainviewer.com","https://tilecache.rainviewer.com/path").forEach {host->
            assertThrows(IllegalArgumentException::class.java) {RadarProtocol.parse(manifest().replace("https://tilecache.rainviewer.com",host))}
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadarProtocol.parse("""{"host":"https://tilecache.rainviewer.com","generated":2000,"radar":{"past":[{"time":3000,"path":"/v2/radar/3000"},{"time":1400,"path":"/v2/radar/../../outside"}]}}""")
        }
    }
    @Test fun webMercatorWrapsDatelineAndClampsPolarTilesWithoutLosingMarker() {
        assertEquals(.5,RadarProjection.point(0.0,0.0).y,1e-8)
        assertEquals(RadarProjection.point(0.0,-180.0),RadarProjection.point(0.0,180.0))
        val center=RadarProjection.point(0.0,179.8)
        val tiles=RadarProjection.tiles(center,7,400,330,256f)
        assertTrue(tiles.any {it.x==127});assertTrue(tiles.any {it.x==0})
        val marker=RadarProjection.marker(RadarProjection.point(0.0,-179.8),center,7,400,330,256f)
        assertTrue(abs(marker.first-200)<50);assertEquals(165f,marker.second,1e-3f)
        val pole=RadarProjection.point(90.0,0.0)
        assertTrue(pole.y in 0.0..1.0)
        assertTrue(RadarProjection.tiles(pole,2,400,330,256f).all {it.x in 0..3 && it.y in 0..3})
        val moved=RadarProjection.pan(center,-200000f,200000f,2,256f)
        assertTrue(moved.x in 0.0..1.0);assertEquals(0.0,moved.y,0.0)
    }
    @Test fun allNineSoundCuesAreDistinctQuietPcmWithValidWavHeaders() {
        val signatures=UiCue.entries.map {cue->
            val samples=UiSoundSynth.samples(cue)
            assertTrue(samples.size in 1000..6000)
            assertTrue(samples.any {it.toInt()!=0})
            assertTrue(samples.all {abs(it.toInt())<=10500})
            val wav=UiSoundSynth.wav(cue)
            assertEquals("RIFF",String(wav,0,4));assertEquals("WAVE",String(wav,8,4))
            val header=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(samples.size*2,header.getInt(40));assertEquals(UiSoundSynth.RATE,header.getInt(24))
            assertEquals(44+samples.size*2,wav.size)
            samples.contentHashCode()
        }
        assertEquals(UiCue.entries.size,signatures.distinct().size)
    }
    @Test fun upgradePreservesSoundPreferenceAndAddsIndependentAmbienceControl() {
        val prefs=SettingsCodec.decode("""{"sounds":true,"fahrenheit":true,"theme":"OLED Black"}""")
        assertTrue(prefs.sounds);assertTrue(prefs.ambience);assertTrue(prefs.fahrenheit)
        assertEquals("OLED Black",prefs.visualTheme)
        assertFalse(SettingsCodec.decode(null).sounds)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=WidgetTestApplication::class)
class RadarRepositoryTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    @Test fun metadataUsesFreshCacheAndFallsBackToSavedHistoryOnServiceFailure() = runBlocking {
        MockWebServer().use {server->
            server.start()
            val repo=RadarRepository(context,OkHttpClient(),server.url("/manifest").toString())
            server.enqueue(MockResponse().setBody(manifest()))
            val first=repo.timeline()
            assertFalse(first.cached);assertEquals(2,first.frames.size)
            assertEquals(first,repo.timeline());assertEquals(1,server.requestCount)
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(repo.timeline(force=true).cached)
            assertEquals(2,server.requestCount)
            val restarted=RadarRepository(context,OkHttpClient(),server.url("/manifest").toString())
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(first.frames,restarted.timeline().frames)
            server.enqueue(MockResponse().setBody(manifest()))
            assertFalse(restarted.timeline(force=true).cached)
        }
    }
    @Test fun tileDecoderHandlesPngAndRejectsMissingOrOversizedTiles() = runBlocking {
        MockWebServer().use {server->
            server.start()
            val repo=RadarRepository(context,OkHttpClient(),server.url("/manifest").toString())
            val png=ByteArrayOutputStream().apply {
                Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}.compress(Bitmap.CompressFormat.PNG,100,this)
            }.toByteArray()
            server.enqueue(MockResponse().setBody(Buffer().write(png)))
            assertEquals(64,repo.tile(server.url("/tile").toString()).width)
            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(runCatching {repo.tile(server.url("/missing").toString())}.isFailure)
            server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(2_000_001))))
            assertTrue(runCatching {repo.tile(server.url("/oversized").toString())}.isFailure)
        }
    }
    @Test fun leavingRadarCancelsItsPendingHttpCall() = runBlocking {
        MockWebServer().use {server->
            server.start();server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client=OkHttpClient()
            val repo=RadarRepository(context,client,server.url("/manifest").toString())
            val result=withTimeoutOrNull(300) {repo.tile(server.url("/slow").toString())}
            assertNull(result)
            withTimeout(2000) {while(client.dispatcher.runningCallsCount()>0) delay(10)}
            assertEquals(0,client.dispatcher.runningCallsCount())
        }
    }
}
