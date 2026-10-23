package com.atmos.weather.widgets

import android.graphics.*
import com.atmos.weather.data.*
import com.atmos.weather.graphics.WeatherArt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

data class WidgetAppearance(val style:String="OLED",val opacity:Int=100,val illustration:String="Sculpted",val showCity:Boolean=true,val fahrenheit:Boolean=false,
    val metrics:Set<String> = linkedSetOf("Feels like","Wind","Humidity","UV","Rain"))

/** Each widget is a size-aware static artboard. Total bitmap bytes are bounded for Binder. */
object WidgetRenderer {
    fun render(kind:Int,widthDp:Int,heightDp:Int,weather:Weather?,settings:Settings,appearance:WidgetAppearance,fontScale:Float=1f):Bitmap {
        val w=widthDp.coerceIn(50,600).toFloat();val h=heightDp.coerceIn(50,600).toFloat()
        val scale=min(2f,sqrt(160_000f/(w*h)))
        val bitmap=Bitmap.createBitmap((w*scale).toInt().coerceAtLeast(1),(h*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.scale(scale,scale)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG);val path=Path()
        val light=appearance.style=="Light"
        val c=weather?.forecast?.current
        val accent=WeatherArt.accent(if(kind==5)61 else c?.weather_code ?: 0,c?.is_day!=0,c?.wind_speed_10m ?: 0.0)
        val foreground=if(light)Color.rgb(20,25,34) else Color.rgb(248,247,241)
        val secondary=if(light)Color.rgb(71,83,100) else Color.rgb(167,173,185)
        val padding=if(w<110)7f else 12f
        val radius=min(24f,min(w,h)*.26f)
        paint.color=when(appearance.style) {"Light"->Color.rgb(239,244,250);"Dark"->Color.rgb(17,18,23);"Dynamic"->Color.rgb(Color.red(accent)/7,Color.green(accent)/7,Color.blue(accent)/7);else->Color.BLACK}
        paint.alpha=if(appearance.style=="Transparent")0 else appearance.opacity.coerceIn(0,100)*255/100
        canvas.drawRoundRect(0f,0f,w,h,radius,radius,paint);paint.alpha=255
        if(appearance.style=="Dynamic") {
            paint.shader=RadialGradient(w*.85f,h*.1f,w*.7f,intArrayOf(Color.argb(appearance.opacity*50/100,Color.red(accent),Color.green(accent),Color.blue(accent)),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f,0f,w,h,radius,radius,paint);paint.shader=null
        }
        fun text(value:String,x:Float,y:Float,size:Float=12f,color:Int=foreground,bold:Boolean=false,maxWidth:Float=w-x-padding) {
            paint.shader=null;paint.color=color;paint.textSize=size*fontScale.coerceIn(.85f,1.35f)
            paint.typeface=Typeface.create(if(bold)"sans-serif-black" else "sans-serif-medium",if(bold)Typeface.BOLD else Typeface.NORMAL)
            var shown=value
            if(paint.measureText(shown)>maxWidth) {
                while(shown.isNotEmpty() && paint.measureText(shown+"…")>maxWidth)shown=shown.dropLast(1)
                shown+="…"
            }
            canvas.drawText(shown,x,y,paint)
        }
        fun art(code:Int,day:Boolean,x:Float,y:Float,size:Float) {
            paint.color=Color.WHITE;paint.shader=null
            canvas.drawBitmap(WeatherArt.bitmap(code,day,if(size>90)256 else 128,appearance.illustration),null,RectF(x,y,x+size,y+size),paint)
        }
        if(weather==null) {
            text("ATMOS",padding,h*.4f,if(w<110)12f else 17f,accent,true)
            text("Open to refresh",padding,h*.7f,11f,secondary)
            return bitmap
        }
        val f=weather.forecast;val d=f.daily;val hourly=f.hourly
        val current=f.current
        val start=forecastHourIndex(hourly.time,f.timezone)
        fun temp(v:Double)=temperature(v,appearance.fahrenheit)
        val highLow="H ${temp(d.temperature_2m_max[0])}  L ${temp(d.temperature_2m_min[0])}"
        val city=if(appearance.showCity)weather.city.name else "ATMOS"
        fun stamp() {
            if(h>=130)text("${if(System.currentTimeMillis()-weather.fetchedAt>900000)"Cached" else "Updated"} ${updateTime(weather.fetchedAt,f.timezone,settings.time24)}",padding,h-7f,10f,secondary)
        }
        // Very small resized widgets retain a readable summary instead of clipping a timeline.
        if((kind==0 && h<125) || (kind==1 && w<120) || (kind>=2 && (w<130 || h<110))) {
            val value=if(kind==5)"${hourly.precipitation_probability[start].toInt()}%" else temp(current.temperature_2m)
            if(w<130) {
                text(value,padding,30f,25f,foreground,true)
                if(h>=85)art(current.weather_code,current.is_day==1,(w-42)/2,32f,42f)
            } else {
                val artSize=min(h*.9f,w*.46f)
                art(current.weather_code,current.is_day==1,w-artSize,0f,artSize)
                text(value,padding,32f,25f,foreground,true,w*.56f)
                if(h>=70)text(if(appearance.showCity)city else condition(current.weather_code),padding,h-27,11f,secondary)
            }
            text(if(kind==5)"Rain chance" else highLow,padding,h-10,10f,secondary)
            return bitmap
        }
        when(kind) {
            0 -> {
                if(appearance.showCity)text(city,padding,17f,11f,secondary)
                text(temp(current.temperature_2m),padding,if(appearance.showCity)48f else 38f,30f,foreground,true)
                val size=min(w-4,(h-85).coerceAtLeast(32f))
                art(current.weather_code,current.is_day==1,(w-size)/2,if(appearance.showCity)49f else 39f,size)
                text("H ${temp(d.temperature_2m_max[0])}",padding,h-30,11f,secondary)
                text("L ${temp(d.temperature_2m_min[0])}",padding,h-16,11f,secondary)
            }
            1 -> {
                val artSize=min(h+4,w*.44f)
                art(current.weather_code,current.is_day==1,w-artSize-2,(h-artSize)/2,artSize)
                val max=w-artSize-padding
                text(temp(current.temperature_2m),padding,min(38f,h*.56f),min(35f,h*.55f),foreground,true,max)
                text(if(appearance.showCity)city else condition(current.weather_code),padding,h-17,11f,secondary,false,max)
                text(highLow,padding,h-4,10f,if(light)secondary else accent,false,max)
            }
            2 -> {
                if(appearance.showCity)text(city,padding,19f,12f,secondary)
                text(temp(current.temperature_2m),padding,62f,42f,foreground,true,w*.6f)
                art(current.weather_code,current.is_day==1,w-76f,20f,76f)
                text(condition(current.weather_code),padding,87f,12f,foreground)
                text(highLow,padding,105f,12f,if(light)secondary else accent)
                if(h>=150) {text("Feels ${temp(current.apparent_temperature)}",padding,124f,11f,secondary);stamp()}
                else text("Feels ${temp(current.apparent_temperature)}",padding,h-10,11f,secondary)
            }
            3 -> {
                if(appearance.showCity)text(city,padding,18f,12f,secondary)
                text(temp(current.temperature_2m),padding,53f,34f,foreground,true)
                art(current.weather_code,current.is_day==1,w-80,0f,74f)
                val count=((w-padding*2)/48).toInt().coerceIn(1,6).coerceAtMost(hourly.time.size-start)
                val cell=(w-padding*2)/count
                val top=if(h<140)54f else 65f
                for(offset in 0 until count) {
                    val i=start+offset;val x=padding+offset*cell
                    text(forecastTime(hourly.time[i],settings.time24),x,top+12,11f,secondary,maxWidth=cell-1)
                    art(hourly.weather_code[i],isDayAt(f,hourly.time[i]),x+cell/2-19,top+13,38f)
                    text(temp(hourly.temperature_2m[i]),x+3,top+65,14f,foreground,true,cell-3)
                }
                if(h>170)stamp()
            }
            4 -> {
                text(city,padding,18f,12f,secondary)
                text(temp(current.temperature_2m),padding,54f,34f,foreground,true)
                art(current.weather_code,current.is_day==1,w-85,-3f,83f)
                val count=((h-82)/25).toInt().coerceIn(1,6).coerceAtMost(d.time.size)
                val low=d.temperature_2m_min.min();val range=(d.temperature_2m_max.max()-low).coerceAtLeast(1.0)
                for(i in 0 until count) {
                    val y=79f+i*25
                    text(if(i==0)"Today" else LocalDate.parse(d.time[i]).format(DateTimeFormatter.ofPattern("EEE",Locale.US)),padding,y,12f,foreground,maxWidth=45f)
                    art(d.weather_code[i],true,padding+42,y-20,26f)
                    text(temp(d.temperature_2m_min[i]),padding+73,y,12f,secondary,maxWidth=28f)
                    val left=padding+104;val right=w-padding-34;val span=(right-left).coerceAtLeast(8f)
                    paint.shader=null;paint.color=if(light)0x22445566 else 0x22FFFFFF;paint.strokeWidth=5f;paint.strokeCap=Paint.Cap.ROUND
                    canvas.drawLine(left,y-4,right,y-4,paint)
                    paint.alpha=255
                    paint.shader=LinearGradient(left,y,right,y,intArrayOf(0xFF84CBF2.toInt(),0xFFFFCE7A.toInt()),null,Shader.TileMode.CLAMP)
                    canvas.drawLine(left+((d.temperature_2m_min[i]-low)/range*span).toFloat(),y-4,left+((d.temperature_2m_max[i]-low)/range*span).toFloat(),y-4,paint);paint.shader=null
                    text(temp(d.temperature_2m_max[i]),w-padding-26,y,12f,foreground,true,30f)
                }
                stamp()
            }
            5 -> {
                text("RAIN TRACKER",padding,19f,11f,if(light)secondary else accent,true)
                val chance=hourly.precipitation_probability[start]
                text("${chance.toInt()}%",padding,56f,34f,foreground,true)
                art(if(current.weather_code>=95)current.weather_code else 61,current.is_day==1,w-90,-6f,92f)
                val amount=hourly.precipitation[start]
                text("${"%.2f".format(if(settings.inches)amount/25.4 else amount)} ${if(settings.inches)"in" else "mm"} · next hour",padding,74f,11f,secondary)
                val count=min(6,hourly.time.size-start);val cell=(w-padding*2)/count
                val top=88f;val bottom=(h-25).coerceAtLeast(top+12)
                for(offset in 0 until count) {
                    val i=start+offset;val x=padding+offset*cell
                    paint.color=accent;paint.alpha=if(light)210 else 190;paint.shader=null
                    val bar=((hourly.precipitation_probability[i]/100)*(bottom-top)).toFloat().coerceAtLeast(2f)
                    canvas.drawRoundRect(x+4,bottom-bar,x+cell-4,bottom,4f,4f,paint);paint.alpha=255
                    text(forecastTime(hourly.time[i],settings.time24),x,bottom+15,10f,secondary,maxWidth=cell)
                }
            }
            else -> {
                text(city,padding,18f,12f,secondary)
                text(temp(current.temperature_2m),padding,55f,34f,foreground,true)
                art(current.weather_code,current.is_day==1,w-80,-4f,80f)
                val columns=if(w>=250)3 else 2
                val metrics=appearance.metrics.toList().sortedBy { listOf("Feels like","Wind","Humidity","UV","Rain","Pressure","Cloud cover").indexOf(it) }
                val cell=(w-padding*2)/columns
                metrics.take(((h-85)/40).toInt().coerceAtLeast(1)*columns).forEachIndexed {i,name ->
                    val x=padding+i%columns*cell;val y=80f+i/columns*40
                    val value=when(name) {
                        "Feels like"->temp(current.apparent_temperature)
                        "Wind"->"${"%.0f".format(if(settings.mph)current.wind_speed_10m/1.609344 else current.wind_speed_10m)} ${if(settings.mph)"mph" else "km/h"}"
                        "Humidity"->"${current.relative_humidity_2m.toInt()}%"
                        "UV"->"${d.uv_index_max[0]}"
                        "Rain"->"${hourly.precipitation_probability[start].toInt()}%"
                        "Pressure"->"${current.surface_pressure.toInt()} hPa"
                        else->"${hourly.cloud_cover[start].toInt()}%"
                    }
                    text(name,x,y,10f,secondary,maxWidth=cell-7)
                    text(value,x,y+21,16f,foreground,true,cell-7)
                }
                stamp()
            }
        }
        if(System.currentTimeMillis()-weather.fetchedAt>900000) {
            paint.shader=null;paint.color=if(light)0xFF906331.toInt() else 0xFFE8BC7A.toInt()
            canvas.drawCircle(w-8f,8f,2f,paint)
        }
        return bitmap
    }
    fun description(kind:Int,w:Weather?,p:Settings,a:WidgetAppearance):String {
        if(w==null)return "Atmos Weather. Open the app to fetch weather."
        val f=w.forecast;val c=f.current;val start=forecastHourIndex(f.hourly.time,f.timezone)
        val summary="${w.city.name}. ${temperature(c.temperature_2m,a.fahrenheit)}. ${condition(c.weather_code)}. High ${temperature(f.daily.temperature_2m_max[0],a.fahrenheit)}, low ${temperature(f.daily.temperature_2m_min[0],a.fahrenheit)}. Feels like ${temperature(c.apparent_temperature,a.fahrenheit)}."
        val timeline=if(kind==3 || kind==5)(start until min(start+6,f.hourly.time.size)).joinToString(". ") {"${forecastTime(f.hourly.time[it],p.time24)}: ${temperature(f.hourly.temperature_2m[it],a.fahrenheit)}, rain ${f.hourly.precipitation_probability[it].toInt()}%"} else ""
        return "$summary $timeline. Saved ${updateTime(w.fetchedAt,f.timezone,p.time24)}, ${f.timezone}. Tap to open Atmos."
    }
}
