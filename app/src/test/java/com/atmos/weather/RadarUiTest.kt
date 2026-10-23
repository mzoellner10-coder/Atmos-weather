package com.atmos.weather

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.atmos.weather.data.*
import com.atmos.weather.experience.*
import com.atmos.weather.radar.*
import com.atmos.weather.ui.RadarScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=WidgetTestApplication::class,qualifiers="w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val city=City("Chicago",41.88,-87.63,"US")
    private val time=System.currentTimeMillis()/1000
    private class FixtureSource(private val time:Long,private val failTiles:Boolean=false,private val failManifest:Boolean=false):RadarSource {
        val tileCount=AtomicInteger()
        override suspend fun timeline(force:Boolean):RadarTimeline {
            if(failManifest)throw IOException("offline fixture")
            return RadarTimeline("https://tilecache.rainviewer.com",time,listOf(RadarFrame(time-600,"/v2/radar/${time-600}"),RadarFrame(time,"/v2/radar/$time")))
        }
        override suspend fun tile(url:String):Bitmap {
            tileCount.incrementAndGet()
            if(failTiles)throw IOException("missing fixture tile")
            // Synthetic tiles are used only in this test, never as a production fallback.
            return Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.CYAN)}
        }
    }
    private fun show(source:RadarSource,reduced:Boolean=true) {
        compose.setContent {
            CompositionLocalProvider(LocalExperience provides Settings(motion=if(reduced)"Minimal" else "Balanced"),LocalReducedMotion provides reduced) {
                MaterialTheme(colorScheme=darkColorScheme()) {
                    Surface(Modifier.fillMaxSize(),color=Color.Black,contentColor=Color.White) {
                        RadarScreen(city,"America/Chicago",Settings(motion=if(reduced)"Minimal" else "Balanced"),source)
                    }
                }
            }
        }
    }
    @Test fun observedTimelineCanBeScrubbedAndZoomIsCappedWhileReducedMotionBlocksAutoplay() {
        val source=FixtureSource(time)
        show(source)
        compose.waitUntil(5000) {compose.onAllNodesWithText("Observed · zoom 6").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(source.tileCount.get()>0)
        compose.onNodeWithText("Play").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Previous").performClick()
        compose.onNodeWithContentDescription("Selected radar observation").assertTextEquals(updateTime((time-600)*1000,"America/Chicago",false))
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithContentDescription("Selected radar observation").assertTextEquals(updateTime(time*1000,"America/Chicago",false))
        compose.onNodeWithContentDescription("Zoom in").performScrollTo().performClick().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Radar map around Chicago. Zoom 7 of 7. Drag to pan.").assertExists()
    }
    @Test fun playbackAdvancesLoadedFramesAndStaysPausedAfterBackgrounding() {
        show(FixtureSource(time),reduced=false)
        compose.waitUntil(5000) {compose.onAllNodesWithText("Observed · zoom 6").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Play").performScrollTo().performClick()
        compose.onNodeWithText("Pause").assertExists()
        compose.waitUntil(5000) {
            runCatching {compose.onNodeWithContentDescription("Selected radar observation").assertTextEquals(updateTime((time-600)*1000,"America/Chicago",false))}.isSuccess
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText("Play").assertIsEnabled()
    }
    @Test fun failedTilesShowAnExplicitErrorInsteadOfAFalseClearRadarFrame() {
        show(FixtureSource(time,failTiles=true))
        compose.waitUntil(5000) {compose.onAllNodesWithText("Radar tiles unavailable · Refresh to retry").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Radar tiles unavailable · Refresh to retry").assertExists()
    }
    @Test fun unavailableServiceKeepsTheRestOfTheAppUsableWithRetry() {
        show(FixtureSource(time,failManifest=true))
        compose.waitUntil(5000) {compose.onAllNodesWithText("Radar unavailable. Check your connection and try again.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Refresh").assertIsEnabled()
        compose.onNodeWithText("No radar imagery is available right now. Your forecast is still available on Weather.").assertExists()
    }
}
