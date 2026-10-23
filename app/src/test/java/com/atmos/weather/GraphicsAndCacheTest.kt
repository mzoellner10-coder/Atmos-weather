package com.atmos.weather

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.atmos.weather.data.*
import com.atmos.weather.graphics.WeatherArt
import com.atmos.weather.widgets.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.time.Instant

internal fun fixtureWeather():Weather {
    val times=(0..23).map {"2026-10-23T${it.toString().padStart(2,'0')}:00"}
    return Weather(City("Chicago",41.88,-87.63,"US"),Forecast(
        Current(17.0,16.0,2,62.0,0.0,18.0,240.0,26.0,1013.0,1),
        Hourly(times,List(24){14.0+it%8},List(24){(it%7)*13.0},List(24){it%3*.2},List(24){if(it%3==0)61 else 2},List(24){10000.0},List(24){11.0},List(24){35.0}),
        Daily((23..28).map {"2026-10-$it"},List(6){20.0+it},List(6){10.0+it},List(6){if(it%2==0)2 else 61},List(6){"2026-10-23T06:00"},List(6){"2026-10-23T18:00"},List(6){43200.0},List(6){4.0},List(6){1.2}),"America/Chicago"),Air(AirCurrent(35.0,5.0,11.0)),Alerts(),Instant.parse("2026-10-23T18:00:00Z").toEpochMilli())
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphicsAndCacheTest {
    @Test fun allSculpturesRenderOriginalTransparentArtwork() {
        val output=File("build/reports/visuals").apply {mkdirs()}
        val collage=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(collage);canvas.drawColor(Color.BLACK)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val examples=listOf(0 to true,0 to false,2 to true,3 to true,45 to true,61 to true,73 to true,95 to true)
        examples.forEachIndexed {i,(code,day)->
            val image=WeatherArt.bitmap(code,day,256)
            assertEquals(0,Color.alpha(image.getPixel(0,0)))
            assertTrue((0 until 256 step 8).sumOf {x->(0 until 256 step 8).count {y->Color.alpha(image.getPixel(x,y))>0}}>80)
            val x=(i%4)*250f;val y=(i/4)*400f+60
            canvas.drawBitmap(image,null,RectF(x,y,x+250,y+250),paint)
            paint.color=Color.WHITE;paint.textSize=18f;canvas.drawText("${condition(code)} / ${if(day)"day" else "night"}",x+10,y+280,paint)
        }
        paint.color=Color.WHITE;paint.textSize=20f;canvas.drawText("Atmos original renderer — test fixtures, not live weather",20f,950f,paint)
        File(output,"weather-sculptures.png").outputStream().use {collage.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun sevenWidgetsRenderAtNominalAndResizedDimensionsWithBoundedMemory() {
        val weather=fixtureWeather();val output=File("build/reports/visuals").apply {mkdirs()}
        val sizes=listOf(60 to 140,140 to 60,140 to 140,280 to 140,280 to 210,280 to 210,280 to 210)
        sizes.forEachIndexed {kind,(width,height)->
            val image=WidgetRenderer.render(kind,width,height,weather,Settings(),WidgetAppearance())
            assertTrue(image.allocationByteCount<=650_000)
            assertTrue(image.width>0 && image.height>0)
            File(output,"widget-${kind+1}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            WidgetRenderer.render(kind,80,100,weather,Settings(time24=true),WidgetAppearance(style="Light"),1.3f)
            val transparent=WidgetRenderer.render(kind,width,height,weather,Settings(),WidgetAppearance(style="Transparent"))
            assertEquals(0,Color.alpha(transparent.getPixel(0,0)))
            val description=WidgetRenderer.description(kind,weather,Settings(),WidgetAppearance())
            assertTrue(description.contains("PM") || description.contains("AM"))
        }
    }
    @Test fun passiveMovementPreservesWidgetAppearanceAndOtherCities() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val prefs=context.getSharedPreferences("widgets",0)
        val before=City("Current location",41.88,-87.63)
        prefs.edit().putString("10_city","41.88,-87.63").putString("11_city","52.52,13.41")
            .putString("10_style","Transparent").putInt("10_opacity",72).putBoolean("10_fahrenheit",true).commit()
        moveWidgetLocation(context,before,before.copy(latitude=42.0))
        assertEquals("42.0,-87.63",prefs.getString("10_city",null))
        assertEquals("52.52,13.41",prefs.getString("11_city",null))
        assertEquals("Transparent",prefs.getString("10_style",null));assertEquals(72,prefs.getInt("10_opacity",0));assertTrue(prefs.getBoolean("10_fahrenheit",false))
    }
    @Test fun realRoomCacheSurvivesOfflineFailureAndDeduplicatesRefreshes() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val database=Room.inMemoryDatabaseBuilder(context,WeatherDatabase::class.java).build()
        val fake=object:WeatherApi {
            var calls=0;var offline=false
            override suspend fun forecast(lat:Double,lon:Double,current:String,hourly:String,daily:String,days:Int,zone:String):Forecast {
                calls++;if(offline)throw IOException("Test offline");delay(20);return fixtureWeather().forecast
            }
            override suspend fun search(name:String,count:Int)=SearchResult()
            override suspend fun air(lat:Double,lon:Double,current:String)=fixtureWeather().air!!
            override suspend fun alerts(point:String)=Alerts()
        }
        try {
            val repository=WeatherRepository(context,database,fake);val city=fixtureWeather().city
            val results=coroutineScope {List(4){async {repository.refresh(city)}}.awaitAll()}
            assertEquals(1,fake.calls);assertEquals(1,results.count {!it.fromCache})
            val saved=repository.cached(city)!!;assertEquals(17.0,saved.forecast.current.temperature_2m,0.01)
            assertEquals(saved.fetchedAt,repository.cached(city)!!.fetchedAt)
            val old=saved.copy(fetchedAt=0)
            database.weatherDao().put(CacheEntry("${city.latitude},${city.longitude}",com.google.gson.Gson().toJson(old)))
            fake.offline=true
            try {repository.refresh(city,true);fail("Offline request must fail")} catch(_:IOException) {}
            assertEquals(0L,repository.cached(city)!!.fetchedAt)
            assertEquals(17.0,repository.cached(city)!!.forecast.current.temperature_2m,0.01)
            assertNull(repository.cached(City("Different city",0.0,0.0)))
        } finally {database.close()}
    }
}
