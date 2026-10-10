package com.outspoken.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.outspoken.log.AppLog
import com.outspoken.setup.offlineVoices

/**
 * Says sentences out loud with an offline voice. [onVoice] hears whether the language has one;
 * [onDone] runs on the main thread.
 */
class Speaker(
    context: Context,
    private val onVoice: (found: Boolean) -> Unit = {},
    private val onDone: () -> Unit,
) {

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private lateinit var tts: TextToSpeech
    private var ready = false
    private var hasVoice = true

    /** ISO 639 code of the language to speak; an Indian voice is preferred, then any offline one. */
    var language = "en"
        set(value) {
            if (value == field) return
            field = value
            if (ready) pickVoice()
        }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                AppLog.write("speech", "ready, engine ${tts.defaultEngine}")
                pickVoice()
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

    /** The voice engine's package name, once it has started. */
    val engine: String? get() = if (ready) tts.defaultEngine else null

    /** Looks again for a voice that was missing, after the person may have downloaded it. */
    fun checkVoice() {
        if (ready && !hasVoice) pickVoice()
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

    private fun pickVoice() {
        val voices = offlineVoices(tts, language)
        val voice = voices.firstOrNull { it.locale.country == "IN" } ?: voices.firstOrNull()
        hasVoice = voice != null
        mainExecutor.execute { onVoice(voice != null) }
        if (voice == null) {
            AppLog.write("speech", "no offline \"$language\" voice on this phone; install it in the phone's text-to-speech settings")
            return
        }
        tts.voice = voice
        AppLog.write("speech", "voice ${voice.name}, ${voices.size} offline \"$language\" voices")
    }

    fun shutdown() {
        if (::tts.isInitialized) tts.shutdown()
    }

    private fun finished() = mainExecutor.execute { onDone() }
}
