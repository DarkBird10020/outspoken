package com.outspoken.listen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.outspoken.log.AppLog
import java.util.Locale

/**
 * Listens to the visitor all the time, on the phone (PRD F7), and passes each finished sentence to
 * [onHeard]. Uses Android's on-device recogniser when the phone has one, otherwise the normal one
 * asked to stay offline. [onStatus] gets a short line for the eye check screen. Main thread only.
 */
class Listener(
    private val context: Context,
    private val onHeard: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private var wanted = false
    private var paused = false
    private var listening = false
    private val listenNow = Runnable { listen() }

    fun start() {
        if (wanted) return
        wanted = true
        AppLog.write("listen", "start, ${if (onDevice) "on-device recogniser" else "offline-preferred recogniser"}")
        listenAfter(0)
    }

    fun stop() {
        if (!wanted) return
        wanted = false
        halt()
        recognizer?.destroy()
        recognizer = null
        onStatus("off")
    }

    /** While the phone itself speaks, so it does not hear its own voice. */
    fun pause() {
        paused = true
        halt()
    }

    fun resume() {
        paused = false
        listenAfter(RESTART_MS)
    }

    private fun listenAfter(delayMs: Long) {
        main.removeCallbacks(listenNow)
        if (wanted && !paused) main.postDelayed(listenNow, delayMs)
    }

    private fun halt() {
        main.removeCallbacks(listenNow)
        if (listening) recognizer?.cancel()
        listening = false
    }

    private fun listen() {
        if (!wanted || paused || listening) return
        val active = recognizer ?: create() ?: return
        listening = true
        active.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1),
        )
    }

    private fun create(): SpeechRecognizer? {
        val created = when {
            onDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> {
                AppLog.write("listen", "no speech recogniser on this phone")
                onStatus("not available on this phone; type the question instead")
                return null
            }
        }
        created.setRecognitionListener(callbacks)
        recognizer = created
        onStatus(if (onDevice) "listening on the phone" else "listening, offline preferred")
        return created
    }

    private fun language(): String {
        val phone = Locale.getDefault()
        return if (phone.language == "en") phone.toLanguageTag() else Locale.US.toLanguageTag()
    }

    private val callbacks = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            listening = false
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onHeard)
            listenAfter(RESTART_MS)
        }

        override fun onError(error: Int) {
            listening = false
            // Stopping the recogniser while the phone speaks reports an error too; that one is ours.
            if (paused || !wanted) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listenAfter(RESTART_MS)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    AppLog.write("listen", "no microphone permission")
                    onStatus("no microphone permission")
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                -> switchAway(error)
                // On the phone this followed every pause for speech, while the on-device
                // recogniser itself was working ("Hello hello" heard just before). Start it afresh.
                SpeechRecognizer.ERROR_CLIENT -> {
                    AppLog.write("listen", "recogniser reset after error $error")
                    recognizer?.destroy()
                    recognizer = null
                    listenAfter(RESTART_MS)
                }
                else -> {
                    if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_SERVER
                    ) {
                        onStatus("this phone's recogniser wants the internet; download the offline English speech pack, or type")
                    }
                    AppLog.write("listen", "error $error, trying again")
                    listenAfter(RETRY_MS)
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /** The on-device recogniser has no English pack: drop to the normal one, asked to stay offline. */
    private fun switchAway(error: Int) {
        recognizer?.destroy()
        recognizer = null
        if (onDevice) {
            AppLog.write("listen", "on-device recogniser failed ($error), using the normal one offline")
            onDevice = false
            listenAfter(RESTART_MS)
        } else {
            AppLog.write("listen", "recogniser failed ($error), trying again later")
            listenAfter(RETRY_MS)
        }
    }

    private companion object {
        const val RESTART_MS = 300L
        const val RETRY_MS = 5_000L
    }
}
