package com.outspoken.setup

import android.content.Context
import android.speech.tts.TextToSpeech
import java.io.File

/** The Gemma model is pushed by hand into the app's external files folder. */
fun findModelFile(dir: File?): File? =
    dir?.listFiles { file -> file.extension == "litertlm" }?.maxByOrNull { it.length() }

/** Reports whether the TTS engine has an English voice that works without a network. */
fun checkOfflineVoice(context: Context, onResult: (Boolean) -> Unit) {
    var tts: TextToSpeech? = null
    tts = TextToSpeech(context.applicationContext) { status ->
        val engine = tts
        val found = status == TextToSpeech.SUCCESS && engine != null &&
            engine.voices.orEmpty().any { voice ->
                voice.locale.language == "en" &&
                    !voice.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features
            }
        engine?.shutdown()
        onResult(found)
    }
}
