package com.outspoken.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.outspoken.setup.offlineEnglishVoices

/** Says sentences out loud with an offline voice. [onDone] runs on the main thread. */
class Speaker(context: Context, private val onDone: () -> Unit) {

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private lateinit var tts: TextToSpeech
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val voices = offlineEnglishVoices(tts)
                (voices.firstOrNull { it.locale.country == "IN" } ?: voices.firstOrNull())?.let { tts.voice = it }
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
        })
    }

    fun speak(text: String) {
        if (!ready || tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text) != TextToSpeech.SUCCESS) {
            finished()
        }
    }

    fun shutdown() {
        if (::tts.isInitialized) tts.shutdown()
    }

    private fun finished() = mainExecutor.execute { onDone() }
}
