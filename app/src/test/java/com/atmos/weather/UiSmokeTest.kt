package com.atmos.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.atmos.weather.data.Settings
import com.atmos.weather.experience.*
import com.atmos.weather.ui.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.flow.first

/** Test app avoids scheduling real background work while exercising actual UI/persistence. */
class WidgetTestApplication:AtmosApp() {
 override fun onCreate() {
  if(runCatching {androidx.work.WorkManager.getInstance(this)}.isFailure)
   androidx.work.WorkManager.initialize(this,androidx.work.Configuration.Builder().build())
 }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=WidgetTestApplication::class,qualifiers="w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun emptyStateNavigatesToSearchAndSettingsWithoutAnyWeatherFabrication() {
        compose.onNodeWithText("Find my atmosphere ↗").assertExists().performClick()
        compose.onNodeWithText("Search cities").assertExists()
        compose.onNodeWithText("Radar",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Select a saved city in Places to see radar around it.").assertExists()
        compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
        compose.onNodeWithText("24-hour time (default is AM/PM)").performScrollTo().assertExists()
        compose.onNodeWithText("24-hour time (default is AM/PM)").performClick()
        compose.waitForIdle()
        val app=compose.activity.application as AtmosApp
        compose.waitUntil(5000) {kotlinx.coroutines.runBlocking {app.preferences.flow.first().time24}}
        compose.onNodeWithText("24-hour time (default is AM/PM)").assertIsOn()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=WidgetTestApplication::class,qualifiers="w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardRenderTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun loadedDashboardUsesRealStateAndRendersItsDimensionalScene() {
        compose.setContent {
            CompositionLocalProvider(LocalExperience provides Settings(motion="Minimal"),LocalReducedMotion provides true) {
                MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFFFCB65))) {
                    Surface(Modifier.fillMaxSize(),color=Color.Black,contentColor=Color(0xFFF5F3ED)) {
                        WeatherDashboard(WeatherState(fixtureWeather(),cached=true),Settings(motion="Minimal"),{}, {}, {})
                    }
                }
            }
        }
        compose.onAllNodesWithText("17°")[0].assertExists()
        compose.onNodeWithText("Chicago ⌄").assertExists()
        compose.onNodeWithContentDescription("Partly cloudy weather sculpture. Tap for a glow, drag to tilt.").assertExists()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/reports/visuals").mkdirs()
            File("build/reports/visuals/dashboard-fixture.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
}
