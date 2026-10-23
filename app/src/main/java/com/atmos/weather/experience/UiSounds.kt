package com.atmos.weather.experience

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

enum class UiCue { Select, Navigate, Confirm, Toggle, Refresh, Play, Pause, Zoom, Sculpture }

/** Small, original synthesized notes. Distinct envelopes avoid repetitive system clicks. */
object UiSoundSynth {
    const val RATE = 22050
    fun samples(cue: UiCue): ShortArray {
        val tones = when(cue) {
            UiCue.Select -> doubleArrayOf(760.0)
            UiCue.Navigate -> doubleArrayOf(440.0,660.0)
            UiCue.Confirm -> doubleArrayOf(523.25,659.25,783.99)
            UiCue.Toggle -> doubleArrayOf(620.0,830.0)
            UiCue.Refresh -> doubleArrayOf(392.0,523.25,659.25)
            UiCue.Play -> doubleArrayOf(523.25,783.99)
            UiCue.Pause -> doubleArrayOf(659.25,440.0)
            UiCue.Zoom -> doubleArrayOf(880.0,1046.5)
            UiCue.Sculpture -> doubleArrayOf(261.63,392.0,523.25)
        }
        val step = if(cue==UiCue.Select || cue==UiCue.Zoom) .034 else .052
        val duration = step * tones.size + .045
        return ShortArray((RATE*duration).toInt()) { i ->
            val t=i.toDouble()/RATE
            var signal=0.0
            tones.forEachIndexed { index,freq ->
                val age=t-index*step
                if(age>=0 && age<.085) {
                    val envelope=min(1.0,age/.004)*exp(-age*48)*min(1.0,(.085-age)/.012)
                    signal += sin(2*PI*freq*age)*envelope*.16
                }
            }
            (signal.coerceIn(-.32,.32)*Short.MAX_VALUE).roundToInt().toShort()
        }
    }
    fun wav(cue: UiCue): ByteArray {
        val pcm=samples(cue)
        return ByteBuffer.allocate(44+pcm.size*2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray());putInt(36+pcm.size*2);put("WAVEfmt ".toByteArray());putInt(16)
            putShort(1);putShort(1);putInt(RATE);putInt(RATE*2);putShort(2);putShort(16)
            put("data".toByteArray());putInt(pcm.size*2);pcm.forEach {putShort(it)}
        }.array()
    }
}

/** SoundPool lets a UI note coexist with ambience without competing audio-focus requests. */
class UiSoundPlayer(private val context: Context): AutoCloseable {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val sounds=ConcurrentHashMap<UiCue,Int>()
    private val ready=ConcurrentHashMap.newKeySet<Int>()
    private var closed=false
    private var stream=0
    private var lastPlay=-1000L
    init {pool.setOnLoadCompleteListener {_,id,status->if(status==0)ready.add(id)}}
    suspend fun prepare() = withContext(Dispatchers.IO) {
        val directory=File(context.cacheDir,"ui-sounds-v1").apply {mkdirs()}
        for(cue in UiCue.entries) {
            ensureActive()
            val file=File(directory,"${cue.name}.wav")
            if(!file.exists()) file.writeBytes(UiSoundSynth.wav(cue))
            synchronized(this@UiSoundPlayer) {
                if(!closed) sounds[cue]=pool.load(file.absolutePath,1)
            }
        }
    }
    @Synchronized fun play(cue: UiCue) {
        if(closed || manager.ringerMode!=AudioManager.RINGER_MODE_NORMAL || manager.isMusicActive) return
        val id=sounds[cue] ?: return
        val now=SystemClock.uptimeMillis()
        if(id !in ready || now-lastPlay<75) return
        lastPlay=now
        if(stream!=0)pool.stop(stream)
        stream=pool.play(id,.5f,.5f,1,0,1f)
    }
    @Synchronized override fun close() {if(!closed) {closed=true;pool.release();sounds.clear();ready.clear()}}
}

val LocalUiSounds=staticCompositionLocalOf<UiSoundPlayer?> {null}

@Composable fun ProvideUiSounds(enabled: Boolean,content: @Composable ()->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var player by remember {mutableStateOf<UiSoundPlayer?>(null)}
    LaunchedEffect(enabled,lifecycle) {
        if(enabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val soundPlayer=runCatching {UiSoundPlayer(context)}.getOrNull() ?: return@repeatOnLifecycle
            try {soundPlayer.prepare();player=soundPlayer;awaitCancellation()}
            catch(e: CancellationException) {throw e}
            catch(_: Exception) { /* Optional device audio must never interrupt weather. */ }
            finally {player=null;soundPlayer.close()}
        }
    }
    CompositionLocalProvider(LocalUiSounds provides if(enabled)player else null,content=content)
}
