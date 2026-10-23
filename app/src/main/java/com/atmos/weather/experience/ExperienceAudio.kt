package com.atmos.weather.experience

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.atmos.weather.data.Current
import com.atmos.weather.data.Settings
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/** Procedurally synthesized original audio. No downloads, recordings, or background playback. */
object ExperienceAudio {
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private const val RATE = 22050
    private fun allowed(manager: AudioManager) = manager.ringerMode == AudioManager.RINGER_MODE_NORMAL && !manager.isMusicActive
    private suspend fun play(context: Context, kind: String, forever: Boolean) = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(AudioManager::class.java)
        if (!allowed(manager)) return@withContext
        val focused = AtomicBoolean(true)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ focused.set(it == AudioManager.AUDIOFOCUS_GAIN) }, Handler(Looper.getMainLooper())).build()
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@withContext
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(max(4096, AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            track.setVolume(.16f)
            track.play()
            var seed = 1387L; var low = 0.0; var sample = 0L
            val buffer = ShortArray(1024)
            while (currentCoroutineContext().isActive && focused.get() && allowed(manager) && (forever || sample < RATE / 8)) {
                for (i in buffer.indices) {
                    seed = (seed * 1664525 + 1013904223) and 0xffffffffL
                    val noise = seed.toDouble() / 0xffffffffL * 2 - 1
                    low = low * .97 + noise * .03
                    val t = sample++ / RATE.toDouble()
                    val signal = when (kind) {
                        "cue" -> sin(2 * PI * (660 + 330 * t) * t) * exp(-t * 40) * .18
                        "rain" -> noise * .045 + low * .15
                        "thunder" -> low * .35 * (.5 + .5 * sin(t * .7)) + noise * .025
                        else -> low * .3 * (.65 + .35 * sin(t * .8))
                    }
                    buffer[i] = (signal.coerceIn(-.3, .3) * Short.MAX_VALUE).toInt().toShort()
                }
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        } catch (_: IllegalStateException) {
            // Audio is an optional enhancement; focus/device changes must not affect forecasts.
        } finally {
            runCatching { track?.stop() }; track?.release(); manager.abandonAudioFocusRequest(request)
        }
    }
    fun cue(context: Context, scope: CoroutineScope) { scope.launch { play(context, "cue", false) } }
    suspend fun ambience(context: Context, kind: String) = play(context, kind, true)
}

@Composable fun WeatherAmbience(current: Current?, settings: Settings) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val kind = when {
        current == null -> null
        current.weather_code >= 95 -> "thunder"
        current.weather_code in 51..67 || current.weather_code in 80..82 -> "rain"
        current.wind_speed_10m >= 20 -> "wind"
        else -> null
    }
    LaunchedEffect(settings.sounds, kind, lifecycle) {
        if (settings.sounds && kind != null) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            ExperienceAudio.ambience(context, kind)
        }
    }
}
