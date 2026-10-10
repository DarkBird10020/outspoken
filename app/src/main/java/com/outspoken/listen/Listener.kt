package com.outspoken.listen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.outspoken.log.AppLog
import java.util.Locale

/**
 * Listens to the visitor all the time, on the phone (PRD F7), and passes each finished sentence to
 * [onHeard], and the words so far, while they are being said, to [onHearing]. Uses Android's
 * on-device recogniser when the phone has one, otherwise the normal one asked to stay offline.
 * [onStatus] gets a short line for the eye check screen. Main thread only.
 *
 * On Android 13 and later it asks for one long session that hands back each sentence as it ends
 * (a segmented session) instead of a new session per sentence, so words said while a session
 * restarts are not lost; a recogniser that does not support that answers the normal way and the
 * session starts again. The log says which happened.
 */
class Listener(
    private val context: Context,
    private val onHeard: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onHearing: (String) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private var wanted = false
    private var paused = false
    private var listening = false
    private val listenNow = Runnable { listen() }

    /** The languages to listen in, best first (for example "en-IN", "en-US"); see [languageTag]. */
    var languages: List<String> = listOf(Locale.US.toLanguageTag())
        set(value) {
            if (value == field) return
            field = value
            chosenTag = null
            if (wanted) {
                halt()
                listenAfter(0)
            }
        }

    /** The language actually used, once checked against the packs on the phone. */
    private var chosenTag: String? = null
    private var checking = false

    // For the log: when speech last began and ended, and when the last words came.
    private var speechEndedMs: Long? = null
    private var lastWordsMs: Long? = null
    private var sessions = 0
    private var segments = 0
    private val errors = mutableMapOf<Int, Int>()
    private var lastSummaryMs = SystemClock.elapsedRealtime()

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
        onHearing("")
    }

    /** While the phone itself speaks, so it does not hear its own voice. */
    fun pause() {
        paused = true
        halt()
        onHearing("")
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
        if (!wanted || paused || listening || checking) return
        val active = recognizer ?: create() ?: return
        val tag = chosenTag
        if (tag == null) {
            chooseLanguage(active)
            return
        }
        listening = true
        sessions++
        summarise()
        val intent = recognizerIntent(tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askForSegments(intent)
        active.startListening(intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askForSegments(intent: Intent) {
        intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SEGMENT_SILENCE_MS)
    }

    private fun recognizerIntent(tag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /**
     * Picks the first of [languages] whose speech pack is on the phone. When none is, it uses the
     * first the phone supports and asks Android to download its pack; Android's own speech service
     * fetches it, so this app still needs no internet permission. Before Android 13, or when the
     * phone cannot say, the first language is used as it is.
     */
    private fun chooseLanguage(active: SpeechRecognizer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && onDevice) {
            checkPacks(active)
        } else {
            useLanguage(languages.first(), "no pack check on this phone")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPacks(active: SpeechRecognizer) {
        val first = languages.first()
        checking = true
        active.checkRecognitionSupport(recognizerIntent(first), context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                checking = false
                val installed = support.installedOnDeviceLanguages
                val supported = support.supportedOnDeviceLanguages
                AppLog.write("listen", "speech packs on the phone: ${installed.ifEmpty { listOf("none") }}; can download: ${supported.size}")
                val pick = languageTag(languages, installed, supported)
                if (pick.download) {
                    AppLog.write("listen", "asking Android to download the ${pick.tag} speech pack")
                    onStatus("downloading the ${pick.tag} speech pack; keep the phone online once")
                    runCatching { active.triggerModelDownload(recognizerIntent(pick.tag)) }
                        .onFailure { AppLog.write("listen", "could not ask for the ${pick.tag} pack: $it") }
                }
                useLanguage(pick.tag, if (pick.download) "pack asked for" else "pack on the phone")
            }

            override fun onError(error: Int) {
                checking = false
                useLanguage(first, "pack check failed, error ${errorName(error)}")
            }
        })
    }

    private fun useLanguage(tag: String, why: String) {
        chosenTag = tag
        AppLog.write("listen", "listening in $tag ($why)")
        listenAfter(0)
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

    /** A finished sentence, from a whole session or one segment of a long one. */
    private fun heard(results: Bundle?, segment: Boolean) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
        onHearing("")
        if (text == null) return
        val now = SystemClock.elapsedRealtime()
        val after = (speechEndedMs ?: lastWordsMs)?.let { " ${now - it} ms after the ${if (speechEndedMs != null) "speech ended" else "last words"}" } ?: ""
        AppLog.write("listen", "${if (segment) "segment" else "result"} \"$text\"$after")
        speechEndedMs = null
        lastWordsMs = null
        onHeard(text)
    }

    private val callbacks = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            listening = false
            heard(results, segment = false)
            listenAfter(RESTART_MS)
        }

        override fun onSegmentResults(segmentResults: Bundle) {
            segments++
            heard(segmentResults, segment = true)
        }

        override fun onEndOfSegmentedSession() {
            listening = false
            listenAfter(RESTART_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val words = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!words.isNullOrBlank()) {
                lastWordsMs = SystemClock.elapsedRealtime()
                onHearing(words)
            }
        }

        override fun onError(error: Int) {
            listening = false
            onHearing("")
            // Stopping the recogniser while the phone speaks reports an error too; that one is ours.
            if (paused || !wanted) return
            errors[error] = (errors[error] ?: 0) + 1
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
                    AppLog.write("listen", "recogniser reset after error ${errorName(error)}")
                    recognizer?.destroy()
                    recognizer = null
                    listenAfter(RESTART_MS)
                }
                else -> {
                    if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_SERVER
                    ) {
                        onStatus("this phone's recogniser wants the internet; download the offline speech pack, or type")
                    }
                    AppLog.write("listen", "error ${errorName(error)}, trying again")
                    listenAfter(RETRY_MS)
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() {
            speechEndedMs = null
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            speechEndedMs = SystemClock.elapsedRealtime()
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /** One line a minute: sessions started, segments heard, and errors by name. */
    private fun summarise() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSummaryMs < SUMMARY_EVERY_MS) return
        val errorText = if (errors.isEmpty()) "none" else errors.entries.joinToString(", ") { "${errorName(it.key)} ${it.value}" }
        AppLog.write("listen", "last minute: $sessions sessions, $segments segments, errors: $errorText")
        lastSummaryMs = now
        sessions = 0
        segments = 0
        errors.clear()
    }

    /** The on-device recogniser has no pack for the language: drop to the normal one, asked to stay offline. */
    private fun switchAway(error: Int) {
        recognizer?.destroy()
        recognizer = null
        if (onDevice) {
            AppLog.write("listen", "on-device recogniser failed (${errorName(error)}), using the normal one offline")
            onDevice = false
            listenAfter(RESTART_MS)
        } else {
            AppLog.write("listen", "recogniser failed (${errorName(error)}), trying again later")
            listenAfter(RETRY_MS)
        }
    }

    private companion object {
        const val RESTART_MS = 300L
        const val RETRY_MS = 5_000L
        const val SUMMARY_EVERY_MS = 60_000L

        /**
         * A pause this long ends a sentence in a long session. Questions to a person in bed are
         * short; a shorter pause would split "Are you ... in pain".
         */
        const val SEGMENT_SILENCE_MS = 800
    }
}
