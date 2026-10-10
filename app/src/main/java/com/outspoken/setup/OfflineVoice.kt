package com.outspoken.setup

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

/** Reports whether the TTS engine has an English voice that works without a network. */
fun checkOfflineVoice(context: Context, onResult: (Boolean) -> Unit) {
    var tts: TextToSpeech? = null
    tts = TextToSpeech(context.applicationContext) { status ->
        val engine = tts
        val found = status == TextToSpeech.SUCCESS && engine != null && offlineEnglishVoices(engine).isNotEmpty()
        engine?.shutdown()
        onResult(found)
    }
}

/** English voices that are installed and work without a network. */
fun offlineEnglishVoices(tts: TextToSpeech): List<Voice> = offlineVoices(tts, "en")

/** Voices for [language] (ISO 639, "en", "hi") that are installed and work without a network. */
fun offlineVoices(tts: TextToSpeech, language: String): List<Voice> =
    tts.voices.orEmpty().filter { voice ->
        voice.locale.language == language &&
            !voice.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features
    }
