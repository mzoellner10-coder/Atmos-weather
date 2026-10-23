package com.atmos.weather.graphics

import android.graphics.*
import android.util.LruCache
import kotlin.math.*

/** Original, resolution-independent Atmos sculptures. Shared by Compose and RemoteViews. */
object WeatherArt {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    fun accent(code: Int, day: Boolean = true, windSpeed: Double = 0.0): Int = when {
        !day -> Color.rgb(171, 157, 255)
        code >= 95 -> Color.rgb(183, 150, 255)
        code in 71..77 || code == 85 || code == 86 -> Color.rgb(188, 235, 255)
        code in 51..67 || code in 80..82 -> Color.rgb(96, 213, 255)
        windSpeed >= 35 && code < 51 -> Color.rgb(117, 220, 211)
        code in 3..48 -> Color.rgb(194, 209, 229)
        else -> Color.rgb(255, 202, 90)
    }
    @Synchronized fun bitmap(code: Int, day: Boolean = true, size: Int = 256, style: String = "Sculpted"): Bitmap {
        val pixels = size.coerceIn(32, 768)
        val key = "$code:$day:$pixels:$style"
        cache.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
        render(Canvas(bitmap), pixels.toFloat(), code, day, style)
        cache.put(key, bitmap)
        return bitmap
    }
    private fun render(canvas: Canvas, size: Float, code: Int, day: Boolean, style: String) {
        canvas.save()
        canvas.scale(size / 1000f, size / 1000f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        val flat = style == "Soft"
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, colors: IntArray) {
            paint.alpha = 255
            paint.shader = RadialGradient(cx - rx * .25f, cy - ry * .4f, max(rx, ry) * 1.6f, colors, null, Shader.TileMode.CLAMP)
            canvas.drawOval(cx-rx, cy-ry, cx+rx, cy+ry, paint)
            paint.shader = null
        }
        fun sun(cx: Float, cy: Float, radius: Float) {
            paint.shader = RadialGradient(cx,cy,radius*1.9f,intArrayOf(0x55FFBD4A,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawCircle(cx,cy,radius*1.9f,paint);paint.shader=null
            paint.strokeWidth=radius*.09f;paint.strokeCap=Paint.Cap.ROUND
            for(i in 0..11) {
                val a=i*PI/6
                paint.color=0xB3FFC95A.toInt()
                canvas.drawLine(cx+cos(a).toFloat()*radius*1.27f,cy+sin(a).toFloat()*radius*1.27f,
                    cx+cos(a).toFloat()*radius*1.48f,cy+sin(a).toFloat()*radius*1.48f,paint)
            }
            ellipse(cx,cy,radius,radius,if(flat) intArrayOf(0xFFFFE998.toInt(),0xFFFFB73D.toInt()) else intArrayOf(0xFFFFF9D0.toInt(),0xFFFFD05B.toInt(),0xFFE98622.toInt()))
            paint.shader=RadialGradient(cx-radius*.35f,cy-radius*.48f,radius*.85f,intArrayOf(0xB3FFFFFF.toInt(),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawCircle(cx,cy,radius,paint);paint.shader=null
        }
        fun moon() {
            paint.shader=RadialGradient(480f,420f,390f,intArrayOf(0x445C61D8,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawCircle(480f,420f,390f,paint);paint.shader=null
            ellipse(490f,430f,230f,230f,intArrayOf(0xFFF3F2FF.toInt(),0xFFBFC4EB.toInt(),0xFF616D9C.toInt()))
            listOf(Triple(400f,335f,42f),Triple(565f,470f,58f),Triple(450f,555f,28f),Triple(595f,325f,20f)).forEach { (x,y,r) ->
                ellipse(x,y,r,r*.85f,intArrayOf(0x994B567F.toInt(),0x227580AD))
                paint.color=0x55FFFFFF;paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
                canvas.drawArc(x-r,y-r*.85f,x+r,y+r*.85f,10f,140f,false,paint);paint.style=Paint.Style.FILL
            }
        }
        fun cloud(cx: Float, cy: Float, scale: Float, storm: Boolean = false) {
            canvas.save();canvas.translate(cx,cy);canvas.scale(scale,scale)
            // A dark projected underside, lobe shading, and reflected rim create depth.
            ellipse(5f,125f,315f,52f,intArrayOf(0x55000000,Color.TRANSPARENT))
            val palette=if(storm) intArrayOf(0xFF818EA9.toInt(),0xFF414D68.toInt(),0xFF1A223B.toInt())
                else if(!day) intArrayOf(0xFFD0D8F4.toInt(),0xFF7A8DAE.toInt(),0xFF384C73.toInt())
                else intArrayOf(0xFFFFFFFF.toInt(),0xFFD5E3F2.toInt(),0xFF788FAE.toInt())
            ellipse(-150f,5f,142f,140f,palette)
            ellipse(40f,-76f,188f,182f,palette)
            ellipse(205f,24f,130f,129f,palette)
            paint.shader=LinearGradient(0f,-55f,0f,145f,palette,null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(-275f,0f,308f,144f,88f,88f,paint);paint.shader=null
            paint.shader=LinearGradient(-220f,15f,200f,110f,intArrayOf(0x33FFFFFF,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(-252f,15f,280f,115f,80f,80f,paint);paint.shader=null
            canvas.restore()
        }
        fun drop(x: Float,y: Float,r: Float) {
            path.reset();path.moveTo(x,y-r*1.5f)
            path.cubicTo(x-r*.2f,y-r*.7f,x-r,y,x-r,y+r*.25f)
            path.cubicTo(x-r,y+r*1.4f,x+r,y+r*1.4f,x+r,y+r*.25f)
            path.cubicTo(x+r,y,x+r*.2f,y-r*.7f,x,y-r*1.5f)
            paint.shader=LinearGradient(x-r,y-r,x+r,y+r,intArrayOf(0xFFC4F8FF.toInt(),0xFF459FE4.toInt(),0xFF1B497D.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawPath(path,paint);paint.shader=null
        }
        fun flake(x: Float,y: Float,r: Float) {
            paint.color=0xFFD7F3FF.toInt();paint.strokeWidth=8f;paint.strokeCap=Paint.Cap.ROUND
            for(i in 0..5) {
                val a=i*PI/3;val dx=cos(a).toFloat();val dy=sin(a).toFloat()
                canvas.drawLine(x,y,x+dx*r,y+dy*r,paint)
                for(sign in listOf(-1,1)) {
                    val b=a+sign*PI/3
                    canvas.drawLine(x+dx*r*.64f,y+dy*r*.64f,x+dx*r*.64f-cos(b).toFloat()*r*.3f,y+dy*r*.64f-sin(b).toFloat()*r*.3f,paint)
                }
            }
        }
        // Subtle floor shadow grounds every illustration without a rectangular asset backdrop.
        ellipse(500f,866f,270f,35f,intArrayOf(0x33000000,Color.TRANSPARENT))
        if(!day) {
            paint.color=0xCCDADEFF.toInt()
            for(i in 0..10) { val x=120f+(i*179%780);val y=100f+(i*113%420);canvas.drawCircle(x,y,if(i%3==0)5f else 2.5f,paint) }
        }
        val snow=code in 71..77 || code==85 || code==86
        val wet=code in 51..67 || code in 80..82 || code>=95
        when {
            code==0 -> if(day) sun(500f,465f,218f) else moon()
            code in 1..2 -> { if(day)sun(635f,325f,148f) else moon();cloud(440f,565f,.87f) }
            snow -> { cloud(492f,390f,1.02f);flake(340f,740f,65f);flake(585f,705f,85f);flake(740f,795f,40f) }
            wet -> {
                cloud(490f,390f,1.05f,code>=95)
                if(code>=95) {
                    path.reset();path.moveTo(535f,565f);path.lineTo(420f,745f);path.lineTo(520f,720f);path.lineTo(466f,868f);path.lineTo(654f,642f);path.lineTo(548f,668f);path.close()
                    paint.shader=LinearGradient(430f,600f,650f,850f,intArrayOf(0xFFF6E3FF.toInt(),0xFFB28DFF.toInt(),0xFF626CFF.toInt()),null,Shader.TileMode.CLAMP)
                    canvas.drawPath(path,paint);paint.shader=null
                    drop(310f,720f,28f);drop(760f,690f,25f)
                } else {drop(335f,690f,38f);drop(515f,770f,45f);drop(715f,680f,35f)}
            }
            else -> {
                cloud(625f,335f,.68f);cloud(415f,550f,1.05f)
                if(code in 45..48) {
                    paint.strokeWidth=14f;paint.strokeCap=Paint.Cap.ROUND
                    for(i in 0..2) {paint.color=Color.argb(130-i*25,185,207,230);canvas.drawLine(230f+i*55,740f+i*55,770f-i*50,740f+i*55,paint)}
                }
            }
        }
        canvas.restore()
    }
}
