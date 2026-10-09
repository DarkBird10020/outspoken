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
fun offlineEnglishVoices(tts: TextToSpeech): List<Voice> =
    tts.voices.orEmpty().filter { voice ->
        voice.locale.language == "en" &&
            !voice.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features
    }
