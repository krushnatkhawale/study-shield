package com.kaushalya.interrupter.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

/**
 * Lifecycle-aware on-device TTS helper for mobile (SS-EXP-07).
 *
 * Mirrors the idea from TV's TextToSpeech usage — one short sentence per screen,
 * then silence. Never blocks setup if TTS is unavailable.
 */
class SetupTts(context: Context, private val onReady: () -> Unit = {}) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    var isReady = false
        private set

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            onReady()
        } else {
            Log.w(TAG, "TTS init failed: $status")
        }
    }

    /**
     * Speak [text] in the given [locale]. Silently no-ops if TTS is not ready.
     * Stops any previous utterance first so step transitions don't overlap.
     */
    fun speak(text: String, locale: Locale) {
        val engine = tts ?: return
        if (!isReady) return
        engine.stop()
        engine.language = locale
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "setup_step")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    companion object {
        private const val TAG = "SetupTts"
    }
}

/**
 * Remember a [SetupTts] tied to the composition lifecycle. Exactly one instance is created;
 * it shuts down when the composable leaves composition or the lifecycle is destroyed.
 */
@Composable
fun rememberSetupTts(onReady: () -> Unit = {}): SetupTts {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tts = remember(context) { SetupTts(context) { onReady() } }
    DisposableEffect(lifecycleOwner, tts) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                tts.shutdown()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tts.shutdown()
        }
    }
    return tts
}
