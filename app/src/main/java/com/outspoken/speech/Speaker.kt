package com.outspoken.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.outspoken.log.AppLog
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
                AppLog.write("speech", "ready, engine ${tts.defaultEngine}, voice ${tts.voice?.name}, ${voices.size} offline English voices")
            } else {
                AppLog.write("speech", "engine failed to start, status $status")
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = AppLog.write("speech", "started \"$utteranceId\"")

            override fun onDone(utteranceId: String?) {
                AppLog.write("speech", "finished \"$utteranceId\"")
                finished()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                AppLog.write("speech", "error $errorCode on \"$utteranceId\"")
                finished()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                AppLog.write("speech", "error on \"$utteranceId\"")
                finished()
            }
        })
    }

    fun speak(text: String) {
        if (!ready) {
            AppLog.write("speech", "not ready, skipped \"$text\"")
            finished()
        } else if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text) != TextToSpeech.SUCCESS) {
            AppLog.write("speech", "speak call failed for \"$text\"")
            finished()
        }
    }

    fun shutdown() {
        if (::tts.isInitialized) tts.shutdown()
    }

    private fun finished() = mainExecutor.execute { onDone() }
}
