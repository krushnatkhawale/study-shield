package com.kaushalya.interrupter

import android.speech.tts.TextToSpeech
import android.util.Log

/**
 * Cached snapshot of the TV's speakable TTS locale tags, refreshed whenever MainActivity
 * initialises or re-configures the engine. Lets [TvServerService] answer `TTS_CAP_CHECK`
 * probes from the mobile app without owning a TextToSpeech instance.
 */
object TtsCapabilities {
    @Volatile
    var supportedLocales: List<String> = emptyList()

    fun refresh(tts: TextToSpeech?) {
        if (tts == null) return
        supportedLocales = try {
            tts.voices.orEmpty()
                .map { it.locale.toLanguageTag() }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            Log.e("TtsCapabilities", "TTS locale enumeration failed", e)
            emptyList()
        }
    }
}