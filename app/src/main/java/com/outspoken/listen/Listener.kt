package com.outspoken.listen

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.ModelDownloadListener
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
 * [onHeard], and the words so far, while they are being said, to [onHearing]. [onStatus] gets a
 * short line for the settings and eye check screens. Main thread only.
 *
 * On Android 13 and later the app records the microphone itself and feeds it to the recogniser
 * ([MicStream]), so one session runs until listening is switched off and each sentence comes back
 * as it ends. While the phone speaks, the feed is silenced instead of stopped. A recogniser that
 * will not take the app's sound records the microphone itself, one session per sentence.
 *
 * The language comes from [wish], matched against the speech packs on the phone ([planSpeech]).
 * A missing pack is asked for from Android, whose own speech service downloads it (this app has no
 * internet permission), and listening goes on meanwhile in a pack the phone has. On Android 14 and
 * later the recogniser may switch between the wished languages as the visitor talks.
 */
class Listener(
    private val context: Context,
    private val onHeard: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onHearing: (String) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val onDeviceOnPhone = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private var onDevice = onDeviceOnPhone
    private var recognizer: SpeechRecognizer? = null

    /** Checks and downloads speech packs; kept apart so a download is still followed after a reset. */
    private var packs: SpeechRecognizer? = null
    private var wanted = false
    private var paused = false
    private var listening = false
    private val listenNow = Runnable { listen() }
    private val summaryTick = object : Runnable {
        override fun run() {
            summarise()
            main.postDelayed(this, SUMMARY_EVERY_MS)
        }
    }
    private val checkTimeout = Runnable {
        if (checking) checkFailed("no answer in ${CHECK_TIMEOUT_MS / 1000} s")
    }

    /** The languages to listen in; see [planSpeech]. */
    var wish = SpeechWish(listOf(Locale.US.toLanguageTag()))
        set(value) {
            if (value == field) return
            field = value
            // A new choice starts clean: a pack that failed for the old one may suit this one.
            broken.clear()
            switching = true
            useRecogniser(onDevice = onDeviceOnPhone)
            replan("the language changed")
        }

    private var plan: SpeechPlan? = null
    private var checking = false
    private var checkRetried = false
    private val askedFor = mutableSetOf<String>()
    private val broken = mutableSetOf<String>()
    private var switching = true
    private var supportedLogged = false

    private var mic: MicStream? = null
    private var micFeed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    private var micOpenFailed = false
    private var micFailures = 0
    private var sessionStartMs = 0L
    private var sessionSentences = 0
    private var sessionHeard = false

    // For the log: when the words of the sentence being said first showed and last changed.
    private var firstWordsMs: Long? = null
    private var lastChangeMs: Long? = null
    private var liveWords = ""
    private var liveUpdates = 0
    private var lastLanguage: String? = null

    private var sessions = 0
    private var sentences = 0
    private var updates = 0
    private val errors = mutableMapOf<Int, Int>()
    private val unsureLanguages = mutableMapOf<String, Int>()
    private var lastSummaryMs = SystemClock.elapsedRealtime()

    fun start() {
        if (wanted) return
        wanted = true
        AppLog.write("listen", "start, ${if (onDevice) "on-device recogniser" else "offline-preferred recogniser"}")
        lastSummaryMs = SystemClock.elapsedRealtime()
        main.postDelayed(summaryTick, SUMMARY_EVERY_MS)
        listenAfter(0)
    }

    fun stop() {
        if (!wanted) return
        wanted = false
        main.removeCallbacks(summaryTick)
        if (sessions > 0) summarise()
        halt("listening stopped")
        recognizer?.destroy()
        recognizer = null
        onStatus("off")
    }

    /** When the app closes; [stop] keeps the pack checker so a download in progress is still followed. */
    fun release() {
        stop()
        packs?.destroy()
        packs = null
    }

    /** While the phone itself speaks, so it does not hear its own voice. */
    fun pause() {
        paused = true
        val feed = mic
        if (feed != null) feed.muted = true else halt(null)
        clearLive()
    }

    fun resume() {
        paused = false
        mic?.muted = false
        listenAfter(RESTART_MS)
    }

    private fun listenAfter(delayMs: Long) {
        main.removeCallbacks(listenNow)
        if (wanted && !paused) main.postDelayed(listenNow, delayMs)
    }

    private fun halt(why: String?) {
        main.removeCallbacks(listenNow)
        if (listening) {
            // Destroyed, not only cancelled: a cancelled recogniser still reported an error
            // afterwards, which would land on the next session. Destroying drops its callbacks.
            recognizer?.destroy()
            recognizer = null
        }
        listening = false
        endFeed(why)
        clearLive()
    }

    private fun replan(why: String) {
        plan = null
        if (wanted) {
            halt(why)
            listenAfter(RESTART_MS)
        }
    }

    private fun useRecogniser(onDevice: Boolean) {
        if (this.onDevice == onDevice) return
        halt(null)
        recognizer?.destroy()
        recognizer = null
        this.onDevice = onDevice
    }

    private fun listen() {
        if (!wanted || paused || listening || checking) return
        val active = recognizer ?: create() ?: return
        val current = plan
        if (current == null) {
            choosePlan()
            return
        }
        listening = true
        sessions++
        val intent = recognizerIntent(current.listenIn)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && switching && current.switchTo.isNotEmpty()) {
            askToSwitch(intent, current)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askForSegments(intent)
        sessionStartMs = SystemClock.elapsedRealtime()
        sessionSentences = 0
        sessionHeard = false
        active.startListening(intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askForSegments(intent: Intent) {
        val feed = if (micFeed) MicStream.open(context) else null
        if (feed != null) {
            micOpenFailed = false
            mic = feed
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, feed.source)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, MicStream.SAMPLE_RATE)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            return
        }
        if (micFeed && !micOpenFailed) {
            micOpenFailed = true
            AppLog.write("listen", "the app could not open the microphone itself; the recogniser records it this time")
        }
        intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SEGMENT_SILENCE_MS)
    }

    /**
     * Lets the recogniser follow the visitor between the planned languages. High precision: in a
     * published Hindi-English test the quicker setting wrote an English word in Hindi letters.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun askToSwitch(intent: Intent, current: SpeechPlan) {
        val languages = arrayListOf(current.listenIn).apply { addAll(current.switchTo) }
        intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION)
            .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, languages)
            .putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, languages)
    }

    private fun recognizerIntent(tag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    private fun choosePlan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && onDeviceOnPhone) {
            checkPacks()
        } else {
            guessPlan("no pack check on this phone")
        }
    }

    /** Without word from the phone on its packs: the first wished language that has not failed. */
    private fun guessPlan(why: String) {
        val tag = (wish.main + wish.others.flatten()).firstOrNull { it !in broken }
        if (tag == null) useRecogniser(onDevice = false)
        usePlan(SpeechPlan(tag ?: wish.main.first(), emptyList(), emptyList(), mainMissing = false, hasPack = false), why)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPacks() {
        val checker = packs ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
            it.setRecognitionListener(packCallbacks)
            packs = it
        }
        checking = true
        main.postDelayed(checkTimeout, CHECK_TIMEOUT_MS)
        checker.checkRecognitionSupport(recognizerIntent(wish.main.first()), context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                if (!checking) return
                checking = false
                checkRetried = false
                main.removeCallbacks(checkTimeout)
                val installed = support.installedOnDeviceLanguages
                val pending = support.pendingOnDeviceLanguages
                val supported = support.supportedOnDeviceLanguages
                AppLog.write(
                    "listen",
                    "speech packs on the phone: ${installed.ifEmpty { listOf("none") }}; " +
                        "downloading: ${pending.ifEmpty { listOf("none") }}" +
                        if (supportedLogged) "" else "; can download: $supported",
                )
                supportedLogged = true
                val next = planSpeech(wish, installed, supported, broken)
                next.download.filter { it !in askedFor && it !in pending }.forEach { download(checker, it) }
                // With no pack to listen with, the normal recogniser, asked to stay offline, until one arrives.
                useRecogniser(onDevice = next.hasPack)
                usePlan(next, if (next.hasPack) "pack on the phone" else "no usable pack on the phone, normal recogniser")
            }

            override fun onError(error: Int) {
                if (checking) checkFailed("error ${errorName(error)}")
            }
        })
    }

    /**
     * The check failed: once more with a new checker, then a plan without it. On the phone the
     * checker failed with "server disconnected" after Android installed a pack (16:38:06), so the
     * plan was guessed and no language switching was set up.
     */
    private fun checkFailed(why: String) {
        checking = false
        main.removeCallbacks(checkTimeout)
        packs?.destroy()
        packs = null
        if (!checkRetried && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkRetried = true
            AppLog.write("listen", "speech pack check failed ($why); checking again")
            checkPacks()
            return
        }
        checkRetried = false
        guessPlan("pack check failed, $why")
    }

    /** The pack checker reports a lost connection here, not to the check's own callback. */
    private val packCallbacks = object : RecognitionListener {
        override fun onError(error: Int) {
            AppLog.write("listen", "speech pack checker error ${errorName(error)}")
            if (checking) checkFailed("checker error ${errorName(error)}")
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onResults(results: Bundle?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun download(checker: SpeechRecognizer, tag: String) {
        askedFor += tag
        AppLog.write("listen", "asking Android to download the $tag speech pack")
        onStatus("asking Android for the $tag speech pack; keep the phone online")
        val intent = recognizerIntent(tag)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) followDownload(checker, intent, tag) else checker.triggerModelDownload(intent)
        }.onFailure { AppLog.write("listen", "could not ask for the $tag pack: $it") }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun followDownload(checker: SpeechRecognizer, intent: Intent, tag: String) {
        var loggedQuarter = -1
        checker.triggerModelDownload(intent, context.mainExecutor, object : ModelDownloadListener {
            override fun onProgress(completedPercent: Int) {
                onStatus("downloading the $tag speech pack, $completedPercent%")
                if (completedPercent / 25 != loggedQuarter) {
                    loggedQuarter = completedPercent / 25
                    AppLog.write("listen", "$tag speech pack $completedPercent% downloaded")
                }
            }

            override fun onSuccess() {
                AppLog.write("listen", "$tag speech pack ready")
                onStatus("$tag speech pack ready")
                replan("the $tag pack arrived")
            }

            override fun onScheduled() {
                AppLog.write("listen", "Android scheduled the $tag speech pack for later; it reports no progress until then")
                onStatus("Android will download the $tag speech pack later")
            }

            override fun onError(error: Int) {
                AppLog.write("listen", "$tag speech pack download failed: ${errorName(error)}")
                onStatus("the $tag speech pack did not download (${errorName(error)})")
            }
        })
    }

    private fun usePlan(next: SpeechPlan, why: String) {
        plan = next
        val also = if (next.switchTo.isEmpty()) "" else ", may switch to ${next.switchTo.joinToString()}"
        AppLog.write("listen", "listening in ${next.listenIn}$also ($why)")
        if (next.mainMissing) {
            AppLog.write("listen", "no ${wish.main.first()} pack on the phone yet; listening in ${next.listenIn} until it is there")
            onStatus("listening in ${next.listenIn} until the ${wish.main.first()} speech pack is on the phone")
        }
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
        val now = SystemClock.elapsedRealtime()
        val timing = firstWordsMs?.let { first ->
            ", words shown live from ${now - first} ms before, last new word ${now - (lastChangeMs ?: first)} ms before, $liveUpdates updates"
        } ?: ", no live words before it"
        clearLive()
        if (text == null) return
        sentences++
        sessionSentences++
        sessionHeard = true
        AppLog.write("listen", "${if (segment) "segment" else "result"} \"$text\"$timing")
        onHeard(text)
    }

    private fun clearLive() {
        firstWordsMs = null
        lastChangeMs = null
        liveWords = ""
        liveUpdates = 0
        onHearing("")
    }

    /** Closes the app's microphone feed, which ends a long session; [why] goes in the log. */
    private fun endFeed(why: String?) {
        val feed = mic ?: return
        mic = null
        feed.close()
        if (why != null) {
            val seconds = (SystemClock.elapsedRealtime() - sessionStartMs) / 1000
            AppLog.write("listen", "long session ended after $seconds s, $sessionSentences sentences: $why" + (feed.failure?.let { " ($it)" } ?: ""))
        }
    }

    private val callbacks = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            listening = false
            heard(results, segment = false)
            endFeed("final result")
            listenAfter(RESTART_MS)
        }

        override fun onSegmentResults(segmentResults: Bundle) {
            heard(segmentResults, segment = true)
        }

        override fun onEndOfSegmentedSession() {
            listening = false
            val fed = mic != null
            endFeed("the recogniser ended it")
            feedFailed(fed, "the session ending")
            listenAfter(RESTART_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val words = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (words.isNullOrBlank() || words == liveWords) return
            val now = SystemClock.elapsedRealtime()
            if (firstWordsMs == null) firstWordsMs = now
            lastChangeMs = now
            liveWords = words
            liveUpdates++
            updates++
            sessionHeard = true
            onHearing(words)
        }

        override fun onLanguageDetection(results: Bundle) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) languageHeard(results)
        }

        override fun onError(error: Int) {
            listening = false
            val fed = mic != null
            endFeed(if (fed) "error ${errorName(error)}" else null)
            clearLive()
            // Without the app's feed, stopping the recogniser while the phone speaks reports an
            // error too; that one is ours.
            if (!wanted || (paused && !fed)) return
            errors[error] = (errors[error] ?: 0) + 1
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listenAfter(RESTART_MS)
                // The last session may still be closing after a language change; try again soon.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> listenAfter(BUSY_RETRY_MS)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    AppLog.write("listen", "no microphone permission")
                    onStatus("no microphone permission")
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                -> languageFailed(error)
                // On the phone this followed every pause for speech, while the on-device
                // recogniser itself was working ("Hello hello" heard just before). Start it afresh.
                SpeechRecognizer.ERROR_CLIENT -> {
                    feedFailed(fed, errorName(error))
                    AppLog.write("listen", "recogniser reset after error ${errorName(error)}")
                    recognizer?.destroy()
                    recognizer = null
                    listenAfter(RESTART_MS)
                }
                else -> {
                    feedFailed(fed, errorName(error))
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
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * Logs what the recogniser detects when it is at least fairly sure or switched; guesses it is
     * not sure of only count toward the summary. On the phone they flipped between en-in and hi-in
     * seven times in 1.6 s, all "not sure" (17:51:51 to 17:51:53).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun languageHeard(results: Bundle) {
        val tag = results.getString(SpeechRecognizer.DETECTED_LANGUAGE) ?: return
        val switch = switchName(results.getInt(SpeechRecognizer.LANGUAGE_SWITCH_RESULT, 0))
        val level = results.getInt(SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL)
        if (level < SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT && switch == null) {
            unsureLanguages[tag] = (unsureLanguages[tag] ?: 0) + 1
            return
        }
        val key = "$tag $switch"
        if (key == lastLanguage) return
        lastLanguage = key
        AppLog.write("listen", "language heard: $tag, ${confidenceName(level)}" + (switch?.let { ", $it" } ?: ""))
    }

    /**
     * A session on the app's feed that failed at once, before any word: twice in a row means this
     * recogniser will not take the app's sound, so it records the microphone itself from then on.
     */
    private fun feedFailed(fed: Boolean, what: String) {
        if (!fed) return
        if (sessionHeard || SystemClock.elapsedRealtime() - sessionStartMs > FEED_TRIAL_MS) {
            micFailures = 0
            return
        }
        val current = plan
        if (switching && current != null && current.switchTo.isNotEmpty()) {
            switching = false
            AppLog.write("listen", "a session with language switching failed at once ($what); listening without switching")
            return
        }
        micFailures++
        if (micFailures < 2) return
        micFeed = false
        AppLog.write("listen", "the recogniser would not take the app's microphone sound ($what twice at the start); it records the microphone itself now, one session per sentence")
    }

    /** The recogniser has no working pack for the language it was given: try another plan. */
    private fun languageFailed(error: Int) {
        val current = plan
        when {
            current != null && switching && current.switchTo.isNotEmpty() -> {
                switching = false
                AppLog.write("listen", "${errorName(error)} with language switching on; listening in ${current.listenIn} alone now")
            }
            current != null && onDevice -> {
                broken += current.listenIn
                AppLog.write("listen", "the ${current.listenIn} pack failed (${errorName(error)}); planning again without it")
            }
            else -> {
                AppLog.write("listen", "recogniser failed (${errorName(error)}), trying again later")
                listenAfter(RETRY_MS)
                return
            }
        }
        plan = null
        listenAfter(RESTART_MS)
    }

    /** About once a minute: sessions started, sentences heard, live word updates and errors by name. */
    private fun summarise() {
        val now = SystemClock.elapsedRealtime()
        val errorText = if (errors.isEmpty()) "none" else errors.entries.joinToString(", ") { "${errorName(it.key)} ${it.value}" }
        val unsure = if (unsureLanguages.isEmpty()) "" else ", languages guessed, not sure: " + unsureLanguages.entries.joinToString(", ") { "${it.key} ${it.value}" }
        unsureLanguages.clear()
        AppLog.write("listen", "last ${(now - lastSummaryMs) / 1000} s: $sessions sessions, $sentences sentences, $updates live word updates, errors: $errorText$unsure")
        lastSummaryMs = now
        sessions = 0
        sentences = 0
        updates = 0
        errors.clear()
    }

    private companion object {
        const val RESTART_MS = 300L
        const val RETRY_MS = 5_000L
        const val BUSY_RETRY_MS = 1_000L
        const val SUMMARY_EVERY_MS = 60_000L
        const val CHECK_TIMEOUT_MS = 3_000L

        /** A session on the app's feed that fails sooner than this, hearing nothing, counts against the feed. */
        const val FEED_TRIAL_MS = 3_000L

        /**
         * Without the app's feed, a pause this long ends a sentence. Questions to a person in bed
         * are short; a shorter pause would split "Are you ... in pain".
         */
        const val SEGMENT_SILENCE_MS = 800
    }
}
