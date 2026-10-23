package com.atmos.weather.experience

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.atmos.weather.data.Settings

val LocalExperience = staticCompositionLocalOf { Settings() }
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable fun systemReducedMotion(): Boolean {
    val context = LocalContext.current
    fun disabled() = AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(disabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = disabled() }
        }
        context.contentResolver.registerContentObserver(AndroidSettings.Global.getUriFor(AndroidSettings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** Frame state is read in drawing lambdas; scrolling/content do not recompose per frame. */
@Composable fun rememberSceneClock(enabled: Boolean): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled, lifecycle) {
        if (enabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
        } else clock.floatValue = 0f
    }
    return clock
}

@Composable fun rememberFeedback(cue: UiCue = UiCue.Select): () -> Unit {
    val view = LocalView.current
    val settings = LocalExperience.current
    val sounds = LocalUiSounds.current
    return {
        // No IGNORE_GLOBAL_SETTING flag: Android's own haptic setting remains authoritative.
        if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (settings.sounds) sounds?.play(cue)
    }
}
