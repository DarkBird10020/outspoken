package com.outspoken

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkSettings
import com.outspoken.conversation.AppLanguage
import com.outspoken.conversation.ConversationController
import com.outspoken.conversation.spokenLanguage
import com.outspoken.eye.EyeReader
import com.outspoken.eye.EyeSample
import com.outspoken.eye.FrontCamera
import com.outspoken.hand.GestureHold
import com.outspoken.hand.HandReader
import com.outspoken.hand.HandReading
import com.outspoken.help.HelpAlarm
import com.outspoken.help.HelpStep
import com.outspoken.listen.HeardFilter
import com.outspoken.listen.Listener
import com.outspoken.listen.SpeechWish
import com.outspoken.log.AppLog
import com.outspoken.log.exportLogs
import com.outspoken.scan.GazeStepper
import com.outspoken.scan.Scanner
import com.outspoken.setup.Calibration
import com.outspoken.setup.Tuning
import com.outspoken.setup.TuningStore
import com.outspoken.setup.checkOfflineVoice
import com.outspoken.setup.ModelCatalog
import com.outspoken.setup.ModelChoice
import com.outspoken.setup.ModelShelf
import com.outspoken.ui.ModelRow
import com.outspoken.speech.Speaker
import com.outspoken.practice.PracticeController
import com.outspoken.stats.PitStats
import com.outspoken.stats.ThermalSensor
import com.outspoken.stats.ThermalZones
import com.outspoken.suggest.ModelImporter
import com.outspoken.suggest.ModelState
import com.outspoken.suggest.ModelSuggestionEngine
import com.outspoken.suggest.OnDeviceModel
import com.outspoken.suggest.SuggestionEngine
import com.outspoken.suggest.SuggestionRequest
import com.outspoken.suggest.Turn
import com.outspoken.suggest.WordRequest
import com.outspoken.ui.CalibrationScreen
import com.outspoken.ui.ConversationScreen
import com.outspoken.ui.EyeCheckScreen
import com.outspoken.ui.EyeMonitor
import com.outspoken.ui.PracticeScreen
import com.outspoken.ui.HelpAlertScreen
import com.outspoken.ui.LiveStatsLine
import com.outspoken.ui.ModelPageUi
import com.outspoken.ui.ModelsScreen
import com.outspoken.ui.SayAnythingScreen
import com.outspoken.ui.SettingsScreen
import com.outspoken.ui.StatsScreen
import com.outspoken.ui.StatsUi
import com.outspoken.ui.TranscriptScreen
import com.outspoken.ui.TranscriptUi
import com.outspoken.ui.describeStats
import com.outspoken.ui.liveStatsLine
import com.outspoken.ui.theme.OutspokenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Screen { Conversation, Practice, EyeCheck, Settings, Models, Calibrate, Help, Stats, Transcript }

    private lateinit var speaker: Speaker
    private lateinit var camera: FrontCamera
    private lateinit var tuningStore: TuningStore
    private lateinit var eyeReader: EyeReader
    private lateinit var practiceController: PracticeController
    private val blinkDetector = BlinkDetector(log = AppLog)
    private val scanner = Scanner()
    private val gazeStepper = GazeStepper(log = AppLog)

    /**
     * The eye check page tries looks and closes on its own stepper and detector, started from the
     * main page's rest, so testing there moves neither the main page's highlight nor its resting
     * point (17:51:08 on the phone: a test look up there became the main page's rest).
     */
    private val checkStepper = GazeStepper(log = AppLog)
    private val checkBlink = BlinkDetector(log = AppLog)
    private val controller = ConversationController(
        speak = { say(it) },
        detector = blinkDetector,
        scanner = scanner,
        log = AppLog,
        gaze = gazeStepper,
        requestReplies = ::requestReplies,
        requestWords = ::requestWords,
        onHelp = ::onHelpStep,
    )
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private var cameraGranted by mutableStateOf(false)
    private var offlineVoice by mutableStateOf<Boolean?>(null)
    private var screen by mutableStateOf(Screen.Conversation)
    private var tuning by mutableStateOf(Tuning())
    private lateinit var listener: Listener
    private lateinit var alarm: HelpAlarm
    private val pitStats = PitStats(SystemClock.elapsedRealtime())
    private var cue: ToneGenerator? = null
    private val heardFilter = HeardFilter()
    private var listenLine by mutableStateOf("off")
    private var micGranted = false
    private var visible = false

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micGranted = granted
            AppLog.write("app", "microphone permission ${if (granted) "granted" else "denied"}")
            if (!granted) listenLine = "no microphone permission; type the question instead"
            updateListening()
        }
    private val calibration = Calibration()
    private var calibrationPrompt by mutableStateOf("")
    private var calibrationProgress by mutableStateOf(0f)
    private var calibrationOutcome by mutableStateOf<String?>(null)
    private var calibrationFailed by mutableStateOf(false)
    /** Seconds and tokens per second of the newest reply, for the Model screen. */
    private var lastReply by mutableStateOf<Pair<Float, Float?>?>(null)
    private var importLine by mutableStateOf<String?>(null)
    private var suggestionEngine: SuggestionEngine? = null
    private var engineModel: Any? = null
    private lateinit var modelShelf: ModelShelf
    private var modelRows by mutableStateOf<List<ModelRow>>(emptyList())
    private var canSeeDownloads by mutableStateOf(false)
    private var replyJob: Job? = null
    private var wordJob: Job? = null

    private val logSaver =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(::saveLogs) }

    private val modelPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importModel) }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
            AppLog.write("app", "camera permission ${if (granted) "granted" else "denied"}")
            if (granted) {
                camera.start(eyeReader, analyzerExecutor)
                startCalibration()
            }
            askForMicrophone()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The speaker cannot touch the phone, so it must never sleep mid-conversation.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tuningStore = TuningStore(this)
        applyTuning(tuningStore.load())
        AppLog.write("app", "build $buildLine")
        AppLog.write("app", "tuning $tuning")
        practiceController = PracticeController(
            initialTuning = tuning,
            log = AppLog,
            speak = ::say,
            onCalibrated = { calibrated ->
                applyTuning(calibrated)
                tuningStore.save(calibrated)
            },
        )

        eyeReader = EyeReader(this) { sample ->
            when (screen) {
                Screen.Practice -> practiceController.onSample(sample)
                Screen.Calibrate -> onCalibrationSample(sample)
                Screen.EyeCheck -> {
                    checkBlink.onSample(sample)
                    checkStepper.onSample(sample.gaze, !checkBlink.eitherEyeShut(sample), sample.timeMs, sample.irisY)
                }
                Screen.Conversation -> {
                    controller.onSample(sample)
                    val missed = blinkDetector.missedInARow >= BlinkDetector.MISSED_BEFORE_ASKING
                    if (missed != closesMissed) closesMissed = missed
                }
                // Alarm, transcript, settings, stats and model pages: eyes pick nothing. Looks on
                // the settings page moved the main page's highlight and set its resting point
                // (17:48:46, 17:48:51), and back on the main page three looks down fired by
                // themselves (17:49:05 to 17:49:06).
                else -> Unit
            }
        }
        eyeReader.dotsOn = true
        handReader = HandReader(this, ::onHandReading)
        handReader.enabled = tuning.handGestures
        eyeReader.alsoFrame = handReader::onFrame
        speaker = Speaker(this, onVoice = { voiceMissing = !it }) {
            heardFilter.onSpeechDone(now())
            listener.resume()
            controller.onSpeechDone(now())
        }
        speaker.language = cardLanguage.voice
        alarm = HelpAlarm(this)
        // Tells the person, eyes still shut, that the help hold is done.
        cue = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, CUE_VOLUME) }.getOrNull()
        listener = Listener(
            this,
            onHeard = { text ->
                val question = heardFilter.accept(text, now())
                if (question == null) AppLog.write("listen", "ignored \"$text\" (the phone's own voice or too short)")
                question?.let {
                    if (tuning.autoLanguage) followVisitor(it)
                    controller.onHeard(it, now())
                }
            },
            onStatus = { listenLine = it },
            onHearing = { hearingText = it.ifBlank { null } },
        )
        listener.wish = speechWish(tuning)
        camera = FrontCamera(this, this)
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        AppLog.write("app", "camera permission ${if (cameraGranted) "already granted" else "requested"}")
        if (cameraGranted) {
            camera.start(eyeReader, analyzerExecutor)
            // Eye readings change with where the phone sits, so each start measures them again.
            lifecycleScope.launch {
                delay(CALIBRATION_START_DELAY_MS)
                startCalibration()
            }
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        if (cameraGranted) askForMicrophone()

        power.addThermalStatusListener(mainExecutor, thermalWatch)
        logThermalSensors()
        checkOfflineVoice(this) {
            AppLog.write("app", "offline voice ${if (it) "ready" else "missing"}")
            offlineVoice = it
        }
        modelShelf = ModelShelf(this)
        OnDeviceModel.load(modelShelf.fileToLoad(), cacheDir)
        // After a reinstall the app cannot see Downloads until access is given again.
        if (modelShelf.fileToLoad() == null && modelShelf.shouldAskForAccess()) {
            AppLog.write("model", "asking for access to Downloads to load the model")
            modelShelf.askToSeeDownloads()
        }
        lifecycleScope.launch {
            OnDeviceModel.state.collect { state ->
                AppLog.write("model", modelLine(state))
                refreshModelRows()
                // A new engine for each model loaded, so switching models takes effect at once.
                if (state is ModelState.Ready && engineModel !== state.model) {
                    engineModel = state.model
                    suggestionEngine = ModelSuggestionEngine(state.model, frequentPhrases = { controller.frequentPhrases }, clockMs = ::now)
                    controller.refreshReplies()
                } else if (state !is ModelState.Ready) {
                    engineModel = null
                    suggestionEngine = null
                }
            }
        }

        // First launch opens the practice / tutorial round directly
        if (!tuningStore.hasCompletedPractice()) {
            screen = Screen.Practice
        }

        setContent {
            LaunchedEffect(Unit) {
                while (true) {
                    controller.onTick(now())
                    delay(TICK_MS)
                }
            }
            if (screen == Screen.Stats) {
                BackHandler { show(Screen.Conversation) }
                var nowMs by remember { mutableStateOf(now()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        nowMs = now()
                        delay(STATS_REFRESH_MS)
                    }
                }
                val modelState by OnDeviceModel.state.collectAsStateWithLifecycle()
                OutspokenTheme {
                    StatsScreen(
                        statsUi(nowMs, modelState, phoneTempNow),
                        onBack = { show(Screen.Conversation) },
                        onAsk = { question ->
                            AppLog.write("listen", "quick topic \"$question\" on the stats screen")
                            controller.onHeard(question, now())
                        },
                        topics = cardLanguage.topics,
                    )
                }
            } else if (screen == Screen.Help) {
                BackHandler { stopHelp() }
                OutspokenTheme {
                    HelpAlertScreen(
                        lastSaid = controller.history.lastOrNull() ?: "",
                        onSoundOff = alarm::stop,
                        onDismiss = ::stopHelp,
                    )
                }
            } else if (cameraGranted && screen == Screen.Calibrate) {
                BackHandler { show(Screen.Conversation) }
                val sample by eyeReader.samples.collectAsStateWithLifecycle()
                OutspokenTheme {
                    CalibrationScreen(
                        prompt = calibrationPrompt,
                        stage = calibration.step.stage,
                        secondsLeft = ((1 - calibrationProgress) * calibration.totalMs / 1000).roundToInt(),
                        outcome = calibrationOutcome,
                        failed = calibrationFailed,
                        sample = sample,
                        settings = tuning.blink,
                        onPreviewReady = camera::showPreview,
                        onPreviewGone = camera::hidePreview,
                        onRetry = ::startCalibration,
                        onSkip = {
                            // The same button leaves after "Done"; only a calibration not finished is skipped.
                            if (calibrationOutcome == null || calibrationFailed) {
                                val b = tuning.blink
                                AppLog.write(
                                    "calibration",
                                    "skipped; the last lines stay: shut ${fmt(b.closedBelow)}, open ${fmt(b.openAbove)}, " +
                                        "lid gap left ${gapLines(b, rightEye = false)}, right ${gapLines(b, rightEye = true)}",
                                )
                            }
                            show(Screen.Conversation)
                        },
                    )
                }
            } else if (!cameraGranted || screen == Screen.EyeCheck) {
                BackHandler(enabled = screen == Screen.EyeCheck) { show(Screen.Conversation) }
                val sample by eyeReader.samples.collectAsStateWithLifecycle()
                val fps by eyeReader.fps.collectAsStateWithLifecycle()
                val recent by AppLog.recent.collectAsStateWithLifecycle()
                OutspokenTheme {
                    EyeCheckScreen(
                        cameraGranted = cameraGranted,
                        sample = sample,
                        fps = fps,
                        tuning = tuning,
                        recentLines = recent,
                        onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                        onPreviewReady = camera::showPreview,
                        onPreviewGone = camera::hidePreview,
                        restGaze = checkStepper.restGaze,
                        restIris = checkStepper.restIrisDrop,
                        // The same smoothed values the steps use, so the dot is steady.
                        steadyGaze = checkStepper.smoothedGaze,
                        steadyIris = checkStepper.smoothedIrisDrop,
                        onBack = { show(Screen.Conversation) },
                        onSettings = { show(Screen.Settings) },
                    )
                }
            } else if (screen == Screen.Settings) {
                BackHandler { show(Screen.EyeCheck) }
                val handReading by handReader.latest.collectAsStateWithLifecycle()
                OutspokenTheme {
                    SettingsScreen(
                        tuning = tuning,
                        listenLine = listenLine,
                        missingVoice = if (voiceMissing) cardLanguage.label else null,
                        onInstallVoice = ::installVoice,
                        handReading = handReading,
                        onTuningChange = {
                            applyTuning(it)
                            tuningStore.save(it)
                        },
                        onTuningReset = {
                            tuningStore.clear()
                            applyTuning(Tuning())
                        },
                        onAsk = { question ->
                            controller.onHeard(question, now())
                            show(Screen.Conversation)
                        },
                        onCalibrate = ::startCalibration,
                        onModels = { show(Screen.Models) },
                        onBack = { show(Screen.Conversation) },
                    )
                }
            } else if (screen == Screen.Models) {
                BackHandler { show(Screen.Settings) }
                val modelState by OnDeviceModel.state.collectAsStateWithLifecycle()
                OutspokenTheme {
                    ModelsScreen(
                        ui = ModelPageUi(
                            state = modelState,
                            modelLine = importLine ?: modelLine(modelState),
                            replySeconds = lastReply?.first,
                            tokensPerSecond = lastReply?.second,
                            offlineVoice = offlineVoice,
                            buildLine = buildLine,
                        ),
                        models = modelRows,
                        canSeeDownloads = canSeeDownloads,
                        onAllowDownloads = modelShelf::askToSeeDownloads,
                        onDownload = ::downloadModel,
                        onUse = ::useModel,
                        onChooseFile = { modelPicker.launch(arrayOf("*/*")) },
                        onShareLogs = ::shareLogs,
                        onSaveLogs = { logSaver.launch("outspoken-logs.txt") },
                        onBack = { show(Screen.Conversation) },
                    )
                }
            } else if (screen == Screen.Practice) {
                BackHandler { show(Screen.Conversation) }
                val practiceUi by practiceController.ui.collectAsStateWithLifecycle()
                OutspokenTheme {
                    PracticeScreen(
                        ui = practiceUi,
                        onBack = { show(Screen.Conversation) },
                        onStart = {
                            tuningStore.markPracticeCompleted()
                            show(Screen.Conversation)
                        },
                    )
                }
            } else if (screen == Screen.Transcript) {
                BackHandler { show(Screen.Conversation) }
                OutspokenTheme {
                    TranscriptScreen(
                        ui = transcriptUi(),
                        onBack = { show(Screen.Conversation) },
                    )
                }
            } else {
                val conversation by controller.ui.collectAsStateWithLifecycle()
                val building = conversation.builder
                if (building != null) {
                    OutspokenTheme {
                        SayAnythingScreen(
                            ui = building,
                            highlighted = conversation.highlighted,
                            onSelect = { controller.onTap(it, now()) },
                            onExit = { controller.closeSayAnything(now()) },
                        )
                    }
                } else OutspokenTheme {
                    ConversationScreen(
                        ui = conversation,
                        hearing = hearingText,
                        micOn = micOn,
                        onMic = ::switchMic,
                        topics = cardLanguage.topics,
                        fixedCards = cardLanguage.fixedCards,
                        onPractice = {
                            practiceController.reset()
                            show(Screen.Practice)
                        },
                        onStats = { show(Screen.Stats) },
                        onEyeCheck = { show(Screen.EyeCheck) },
                        onTranscript = { show(Screen.Transcript) },
                        onSelect = { controller.onTap(it, now()) },
                        eyeHint = if (closesMissed) CLOSES_MISSED_HINT else eyeHint(tuning) + handHint(tuning),
                        onEyeHint = if (closesMissed) ::startCalibration else null,
                        onAsk = { question ->
                            AppLog.write("listen", "quick topic \"$question\"")
                            controller.onHeard(question, now())
                        },
                        eyeView = { modifier ->
                            val sample by eyeReader.samples.collectAsStateWithLifecycle()
                            EyeMonitor(sample, tuning.blink, camera::showPreview, camera::hidePreview, modifier)
                        },
                        liveStats = {
                            LiveStatsLine(STATS_REFRESH_MS) {
                                liveStatsLine(pitStats.replyTimeSeconds(now()), pitStats.tokensPerSecond, pitStats.repliesWritten)
                            }
                        },
                    )
                }
            }
        }
    }

    /** Spoken steps that set the look and blink lines from this person's eyes (PRD F6). */
    private fun startCalibration() {
        calibration.start(now())
        calibrationOutcome = null
        calibrationFailed = false
        calibrationProgress = 0f
        calibrationPrompt = calibration.step.prompt
        AppLog.write("calibration", "started")
        say(calibrationPrompt)
        show(Screen.Calibrate)
    }

    private fun onCalibrationSample(sample: EyeSample) {
        if (calibrationOutcome != null) return
        calibrationProgress = calibration.progress(sample.timeMs)
        val step = calibration.onSample(sample) ?: return
        if (step != Calibration.Step.Done) {
            calibrationPrompt = step.prompt
            say(step.prompt)
            return
        }
        when (val result = calibration.result(tuning)) {
            is Calibration.Result.Ok -> {
                applyTuning(result.tuning)
                tuningStore.save(result.tuning)
                gazeStepper.restAt(result.measured.restGaze, result.measured.restIris)
                val m = result.measured
                AppLog.write(
                    "calibration",
                    "ok: rest gaze ${fmt(m.restGaze)}, look up reach ${fmt(m.upReach)}, look down reach ${fmt(m.downReach)}, open ${fmt(m.restOpen)}, " +
                        "closed ${fmt(m.closedOpen)} -> look ${fmt(result.tuning.gaze.lookStrength)}, " +
                        "look down ${result.tuning.gaze.downStrength?.let { fmt(it) } ?: "off"}, iris rest ${m.restIris?.let { fmt(it) }} down reach ${m.irisDownReach?.let { fmt(it) }} -> iris look down ${result.tuning.gaze.irisDownStrength?.let { String.format(Locale.US, "%.3f", it) } ?: "off"}, shut line ${fmt(result.tuning.blink.closedBelow)}, open line ${fmt(result.tuning.blink.openAbove)}, " +
                        "lid gap left ${describeGaps(m.leftGaps)}, right ${describeGaps(m.rightGaps)} -> gap lines left " +
                        "${gapLines(result.tuning.blink, rightEye = false)}, right ${gapLines(result.tuning.blink, rightEye = true)}",
                )
                calibrationOutcome = "Done. ${eyeHint(tuning)}"
                say("Done")
                lifecycleScope.launch {
                    delay(CALIBRATION_DONE_MS)
                    if (screen == Screen.Calibrate) show(Screen.Conversation)
                }
            }
            is Calibration.Result.Failed -> {
                AppLog.write("calibration", "failed: ${result.reason}")
                calibrationOutcome = result.reason
                calibrationFailed = true
                say(result.reason)
            }
        }
    }

    /** One eye's lid gap shut / open lines for the log, or "off". */
    private fun gapLines(blink: BlinkSettings, rightEye: Boolean): String {
        val shut = blink.gapShutLine(rightEye) ?: return "off"
        return "${fmt(shut)} / ${blink.gapOpenLine(rightEye)?.let { fmt(it) } ?: "off"}"
    }

    private fun describeGaps(gaps: Calibration.EyeGaps?) =
        gaps?.let { "open ${fmt(it.rest)} closed ${fmt(it.closed)} down ${it.down?.let { d -> fmt(d) } ?: "not seen"}" } ?: "not read"

    private fun handHint(tuning: Tuning) =
        if (tuning.handGestures) "  Hand: 1 to 4 fingers (thumb folded) light a card, a fist says it, an open hand: please wait." else ""

    private fun eyeHint(tuning: Tuning) = when {
        !tuning.moveByEyes -> "Close your eyes when your choice lights up."
        tuning.lookDown && tuning.winks -> "Look up or down, or wink, to move.  Close both eyes: choose."
        tuning.lookDown -> "Look up or down to move.  Close both eyes: choose."
        tuning.winks -> "Look up or wink to move (left wink down, right wink up).  Close both eyes: choose."
        else -> "Look up to move to the next card.  Close both eyes: choose."
    }

    /** Which code this APK came from, so two phones can be checked for the same build. */
    private val buildLine =
        "${BuildConfig.COMMIT}${if (BuildConfig.CHANGED) " with uncommitted edits" else ""}, " +
            (if (BuildConfig.FROM_CI) "CI APK" else "built on a laptop") +
            if (BuildConfig.SHARED_KEY) ", shared key" else ", this laptop's own key: will not install over a CI APK"

    private fun fmt(value: Float) = String.format(Locale.US, "%.2f", value)

    override fun onResume() {
        super.onResume()
        AppLog.write("app", "resumed")
        visible = true
        watchDownloads()
        watchTemperature()
        if (::speaker.isInitialized) speaker.checkVoice()
        updateListening()
    }

    override fun onPause() {
        super.onPause()
        AppLog.write("app", "paused")
        visible = false
        updateListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.write("app", "closed")
        power.removeThermalStatusListener(thermalWatch)
        speaker.shutdown()
        listener.release()
        alarm.stop()
        cue?.release()
        // Closed on the camera thread, after any frame already being analysed, so no frame reaches
        // a closed face tracker.
        analyzerExecutor.execute {
            eyeReader.close()
            handReader.close()
        }
        analyzerExecutor.shutdown()
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun applyTuning(next: Tuning) {
        val before = tuning
        tuning = next
        if (::listener.isInitialized) listener.wish = speechWish(next)
        // Only a change of the choice itself moves the cards; in auto mode they follow the visitor.
        if (next.language != before.language || next.autoLanguage != before.autoLanguage) useCardLanguage(next.language)
        if (::handReader.isInitialized && handReader.enabled != next.handGestures) {
            handReader.enabled = next.handGestures
            AppLog.write("hand", "hand signs ${if (next.handGestures) "on" else "off"}")
        }
        blinkDetector.settings = next.blink
        checkBlink.settings = next.blink
        scanner.intervalMs = next.scanMs
        gazeStepper.settings = next.activeGaze
        checkStepper.settings = next.activeGaze
        controller.upMovesNext = !next.lookDown
        if (controller.moveByEyes != next.moveByEyes) controller.moveByEyes = next.moveByEyes
        controller.winks = next.winks
    }

    private fun statsUi(nowMs: Long, model: ModelState, phoneTemp: Float?) = StatsUi(
        replyTimeSeconds = pitStats.replyTimeSeconds(nowMs),
        tokensPerSecond = pitStats.tokensPerSecond,
        repliesWritten = pitStats.repliesWritten,
        phoneTempCelsius = phoneTemp,
        modelName = (model as? ModelState.Ready)?.name?.removeSuffix(".litertlm") ?: "No model loaded",
        runtime = "LiteRT-LM" + ((model as? ModelState.Ready)?.let { " on ${it.backend}" } ?: ""),
        // The last practice round's stars and every try at a card since the app started.
        blinkAccuracyPercent = (practiceController.blinks + controller.blinks).percent,
        // Whole seconds, so the screen only redraws when a number on it changes.
        sessionMillis = pitStats.sessionMillis(nowMs) / 1_000 * 1_000,
        sentencesSpoken = controller.history.size,
    )

    // Replies slowed from about 65 to 45 tok/s a few minutes into the 05:45 and 06:03 phone runs
    // and were fast again after a long pause, which looks like heat. These lines show whether.
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val thermalWatch = PowerManager.OnThermalStatusChangedListener {
        AppLog.write("app", "thermal ${thermalLabel(it)}, ${temperatures()}")
    }

    /** Android's thermal status in a word; from "light" up the phone is slowing its chips. */
    private fun thermalLabel(status: Int) = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> "unknown"
    }

    /**
     * The phone's shell (body) temperature, or the battery's where there is no shell sensor. The
     * battery's changed only about once a minute and in 0.1 °C steps: 34.3 to 34.2 °C through the
     * 08:48 run, so the stats showed "34 °C" throughout, while the shell sensor read 34.4, 35.1 and
     * 36.3 °C at 08:48:10, 08:48:22 and 08:49:12.
     */
    private fun phoneTemperature(): Float? = shellSensor.celsius() ?: batteryTemperature()

    /** The latest [phoneTemperature], for the stats screen and its log lines. Main thread only. */
    private var phoneTempNow: Float? = null
    private var temperatureWatch: Job? = null

    /** Reads the temperature once a second off the main thread while the app is on screen. */
    private fun watchTemperature() {
        if (temperatureWatch?.isActive == true) return
        temperatureWatch = lifecycleScope.launch {
            while (visible) {
                phoneTempNow = withContext(Dispatchers.IO) { phoneTemperature() }
                delay(TEMPERATURE_REFRESH_MS)
            }
        }
    }

    private val shellSensor = ThermalSensor("tz_shell")

    /** Both readings for the log: "battery 34.2 °C, shell 36.3 °C". */
    private fun temperatures() =
        "battery ${batteryTemperature() ?: "-"} °C, shell ${shellSensor.celsius()?.let { String.format(Locale.US, "%.1f", it) } ?: "-"} °C"

    /** Battery temperature, the phone's own reading, in °C; null when the phone does not give it. */
    private fun batteryTemperature(): Float? {
        val battery = ContextCompat.registerReceiver(
            this,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        ) ?: return null
        val tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == Int.MIN_VALUE) null else tenths / 10f
    }

    private lateinit var handReader: HandReader
    private val handHold = GestureHold()

    /** Main thread. Only the conversation page acts on hand signs; elsewhere they let the hold go. */
    private fun onHandReading(reading: HandReading, timeMs: Long) {
        val acting = screen == Screen.Conversation
        if (acting && (reading.sign != null || reading.fingers != null)) controller.onHandInView(timeMs)
        val held = handHold.onFrame(reading.sign.takeIf { acting }, reading.score, timeMs, tuning.handSigns) ?: return
        val sign = held.sign
        AppLog.write("hand", "${sign.key} held ${held.heldMs} ms, ${held.steadyReadings} of ${held.readings} readings, ${sign.meaning}")
        controller.onHandSign(sign.action, now())
    }

    private fun onHelpStep(step: HelpStep) {
        when (step) {
            HelpStep.HoldReached -> cue?.startTone(ToneGenerator.TONE_PROP_BEEP2, CUE_MS)
            HelpStep.Alarm -> {
                alarm.start()
                show(Screen.Help)
            }
        }
    }

    private fun stopHelp() {
        alarm.stop()
        AppLog.write("help", "someone came")
        show(Screen.Conversation)
    }

    /** Everything the phone says goes through here, so the microphone does not take it for a question. */
    private fun say(text: String) {
        heardFilter.onSpeechStart(text)
        listener.pause()
        speaker.speak(text)
    }

    private fun askForMicrophone() {
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (micGranted) updateListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    /** Listens on the conversation and mirrored transcript pages while the app is on screen. */
    private fun updateListening() {
        if (listeningNow()) listener.start() else listener.stop()
    }

    private fun listeningNow() = micGranted && visible && micOn && screen in LISTENING_SCREENS

    /** Words being heard now, shown live in the "Heard" card. */
    private var hearingText by mutableStateOf<String?>(null)

    /** Owner request: listening goes on until the person at the bedside switches it off. */
    private var micOn by mutableStateOf(true)

    private fun switchMic() {
        micOn = !micOn
        AppLog.write("listen", "microphone switched ${if (micOn) "on" else "off"} by hand")
        updateListening()
    }

    /**
     * The language's own speech tags (Hindi: hi-IN). For English, English as spoken in India
     * first: "Are you in pain" was heard as "Aryan paint" with the US English pack (09:15:08).
     * Then the phone's own English, then US English. The listener takes the first whose pack is
     * on the phone.
     */
    private fun speechLanguages(language: AppLanguage): List<String> {
        language.speechTags?.let { return it }
        val phone = Locale.getDefault().takeIf { it.language == "en" }?.toLanguageTag()
        return listOfNotNull("en-IN", phone, Locale.US.toLanguageTag()).distinct()
    }

    /** The chosen language; with auto on, the other one too, so the recogniser can follow the visitor. */
    private fun speechWish(t: Tuning): SpeechWish {
        val others = if (t.autoLanguage) AppLanguage.entries.filter { it != t.language }.map { speechLanguages(it) } else emptyList()
        return SpeechWish(speechLanguages(t.language), others)
    }

    /** The language of the cards, the topics and the voice right now; in auto mode the visitor's. */
    private var cardLanguage by mutableStateOf(AppLanguage.English)

    /** Closes are being missed under the current lines (glasses put on after calibrating, for one). */
    private var closesMissed by mutableStateOf(false)

    /** Whether the phone lacks an offline voice for [cardLanguage]. */
    private var voiceMissing by mutableStateOf(false)

    private fun useCardLanguage(language: AppLanguage) {
        cardLanguage = language
        if (::speaker.isInitialized) speaker.language = language.voice
        controller.language = language
    }

    /** Owner request: the cards follow the language the visitor speaks, told apart by its letters. */
    private fun followVisitor(text: String) {
        val spoken = spokenLanguage(text) ?: return
        if (spoken == cardLanguage) return
        AppLog.write("listen", "the visitor spoke ${spoken.name}, cards follow")
        useCardLanguage(spoken)
    }

    /** Opens the voice engine's own download screen; the engine fetches the voice, not this app. */
    private fun installVoice() {
        val engine = speaker.engine
        val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply { engine?.let { setPackage(it) } }
        val opened = runCatching { startActivity(install) }.isSuccess ||
            runCatching { startActivity(Intent(TTS_SETTINGS)) }.isSuccess
        AppLog.write(
            "speech",
            if (opened) "opened the voice download screen for ${cardLanguage.label} (engine $engine)" else "no screen to download voices on this phone",
        )
    }

    private fun transcriptUi() = TranscriptUi(
        turns = controller.allTurns,
        isListening = listeningNow(),
        totalSentences = controller.history.size,
    )

    private fun show(next: Screen) {
        // What the stats screen showed when it opened and closed, so a report that a number did
        // not move can be checked against the logs.
        if (screen == Screen.Stats && next != Screen.Stats) AppLog.write("stats", "closed showing ${describeStats(currentStats())}")
        AppLog.write("ui", "screen $next")
        if (next == Screen.EyeCheck) gazeStepper.restGaze?.let { checkStepper.restAt(it, gazeStepper.restIrisDrop) }
        screen = next
        updateListening()
        if (next == Screen.Stats) AppLog.write("stats", "opened showing ${describeStats(currentStats())}")
    }

    private fun currentStats() = statsUi(now(), OnDeviceModel.state.value, phoneTempNow)

    /**
     * Which of the phone's thermal sensors the app can read, once at start, and the shell sensor the
     * stats screen shows (looking it up here keeps that off the main thread).
     */
    private fun logThermalSensors() {
        lifecycleScope.launch(Dispatchers.IO) {
            AppLog.write("app", ThermalZones.describe())
            AppLog.write("app", shellSensor.celsius()?.let { "phone temperature from the shell sensor tz_shell" } ?: "no shell sensor, phone temperature from the battery")
        }
    }

    /** Next words for "Say anything"; each step's reply time goes in the log. */
    private fun requestWords(requestId: Int, turns: List<Turn>, sentence: String): Boolean {
        val engine = suggestionEngine ?: return false
        wordJob?.cancel()
        wordJob = lifecycleScope.launch {
            val words = engine.nextWords(WordRequest(turns, sentence, controller.language))
            AppLog.write(
                "model",
                "words in ${words.elapsedMs} ms, ${words.tokensPerSecond ?: "-"} tok/s, " +
                    (if (words.fromModel) "from the model" else "built-in words only") +
                    " for \"$sentence\"" + (words.timing?.let { ", $it" } ?: "") +
                    ", sentences that fit: ${words.sentences}" +
                    (if (words.unfit.isNotEmpty()) ", did not fit: ${words.unfit}" else ""),
            )
            controller.onWords(requestId, words.words, words.completion, now())
        }
        return true
    }

    private fun requestReplies(requestId: Int, turns: List<Turn>): Boolean {
        val engine = suggestionEngine ?: return false
        replyJob?.cancel()
        pitStats.onAsked(now())
        replyJob = lifecycleScope.launch {
            val suggestions = engine.suggest(SuggestionRequest(turns, LocalTime.now().hour, controller.language))
            lastReply = suggestions.elapsedMs / 1000f to suggestions.tokensPerSecond
            pitStats.onReplies(suggestions.elapsedMs, suggestions.fromModel, suggestions.tokensPerSecond, suggestions.modelReplies)
            val toppedUp = suggestions.replies.size - suggestions.modelReplies
            AppLog.write(
                "model",
                "replies in ${suggestions.elapsedMs} ms, ${suggestions.tokensPerSecond ?: "-"} tok/s, " +
                    (if (suggestions.fromModel) "from the model" else "phrase bank fallback") +
                    (if (suggestions.fromModel && toppedUp > 0) " ($toppedUp topped up from the phrase bank)" else "") +
                    ", ${temperatures()}, thermal ${thermalLabel(power.currentThermalStatus)}" +
                    (suggestions.timing?.let { ", $it" } ?: ""),
            )
            controller.onReplies(requestId, suggestions.replies, suggestions.fromModel, now())
        }
        return true
    }

    private fun saveLogs(uri: Uri) {
        AppLog.write("app", "saving logs")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dir = File(checkNotNull(getExternalFilesDir(null)) { "no app folder" }, "logs")
                val out = checkNotNull(contentResolver.openOutputStream(uri)) { "could not open the file" }
                val count = out.use { exportLogs(dir, it) }
                AppLog.write("app", "saved $count run logs")
            } catch (e: Exception) {
                AppLog.write("app", "could not save logs: ${e.message}")
            }
        }
    }

    /** Puts every run log in one file and opens the share sheet, so it can go straight into a chat. */
    private fun shareLogs() {
        AppLog.write("app", "sharing logs")
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(checkNotNull(getExternalFilesDir(null)) { "no app folder" }, "logs")
                    val out = File(cacheDir, "shared").apply { mkdirs() }.resolve("outspoken-logs.txt")
                    val count = out.outputStream().use { exportLogs(dir, it) }
                    AppLog.write("app", "sharing $count run logs")
                    out
                }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.logs", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(send, "Share logs"))
            } catch (e: Exception) {
                AppLog.write("app", "could not share logs: ${e.message}")
            }
        }
    }

    private var downloadWatch: Job? = null

    /**
     * While the app is open, checks every few seconds for a finished download and loads it if it
     * is the one asked for, and loads the chosen model once it can be seen (for example right
     * after "All files access" is switched on). Files are never moved or copied.
     */
    private fun watchDownloads() {
        // Restarting the check on every resume cut a 2.6 GB copy short on the phone (03:20:30).
        if (downloadWatch?.isActive == true) return
        downloadWatch = lifecycleScope.launch {
            while (visible) {
                canSeeDownloads = modelShelf.canSeeDownloads()
                val waiting = modelShelf.waitingFor
                if (waiting != null && modelShelf.installed(waiting) != null) {
                    AppLog.write("model", "${waiting.title} downloaded")
                    modelShelf.waitingFor = null
                    useModel(waiting)
                }
                val state = OnDeviceModel.state.value
                if (state is ModelState.Missing || state is ModelState.Failed) {
                    // A failed file is not tried again every few seconds (it was, on the phone at
                    // 04:04, every 3 s); a new or changed file is.
                    modelShelf.fileToLoad()?.let { file ->
                        val key = "${file.path}|${file.length()}|${file.lastModified()}"
                        if (key != lastAutoLoad) {
                            lastAutoLoad = key
                            OnDeviceModel.load(file, cacheDir)
                        }
                    }
                }
                refreshModelRows()
                delay(DOWNLOAD_CHECK_MS)
            }
        }
    }

    private var lastAutoLoad: String? = null

    private fun downloadModel(choice: ModelChoice) {
        AppLog.write("model", "download ${choice.title} in the browser")
        if (!modelShelf.download(choice)) importLine = "no browser on this phone to download with"
        refreshModelRows()
    }

    private fun useModel(choice: ModelChoice) {
        val file = modelShelf.installed(choice) ?: return
        modelShelf.chosen = choice
        AppLog.write("model", "switching to ${choice.title}")
        OnDeviceModel.switchTo(file, cacheDir)
        refreshModelRows()
    }

    private fun refreshModelRows() {
        val state = OnDeviceModel.state.value
        val waiting = modelShelf.waitingFor
        modelRows = ModelCatalog.all.map { choice ->
            val here = modelShelf.installed(choice) != null
            val status = when {
                state is ModelState.Ready && ModelCatalog.isFileOf(choice, state.name) -> "in use, on ${state.backend}"
                state is ModelState.Loading && ModelCatalog.isFileOf(choice, state.name) -> "loading"
                here -> "downloaded"
                waiting == choice -> "downloading in the browser, loads by itself when done"
                else -> "not downloaded"
            }
            ModelRow(choice, status, downloaded = here, inUse = status.startsWith("in use") || status == "loading")
        }
    }

    private fun importModel(uri: Uri) {
        lifecycleScope.launch {
            try {
                // A model already in Downloads (the usual case) is used where it is, not copied.
                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                val known = ModelCatalog.all.firstOrNull { it.fileName == name && modelShelf.installed(it) != null }
                if (known != null) {
                    useModel(known)
                    return@launch
                }
                importLine = "copying"
                // Copied into Downloads when the app can see it, so the model outlives the app.
                val dir = if (modelShelf.canSeeDownloads()) modelShelf.downloadsDir else checkNotNull(getExternalFilesDir(null)) { "no app folder" }
                val file = ModelImporter(this@MainActivity).import(uri, dir) { percent ->
                    importLine = if (percent >= 0) "copying, $percent%" else "copying"
                }
                AppLog.write("model", "imported ${file.name} (${file.length() / 1_000_000} MB) into ${dir.name}")
                importLine = null
                ModelCatalog.byFileName(file.name)?.let { modelShelf.chosen = it }
                OnDeviceModel.switchTo(file, cacheDir)
            } catch (e: Exception) {
                AppLog.write("model", "import failed: ${e.message}")
                importLine = "could not import: ${e.message}"
            }
        }
    }

    private fun modelLine(state: ModelState): String = when (state) {
        ModelState.Missing -> if (modelShelf.canSeeDownloads()) "missing. Tap Download next to a model below" else "not found. Tap Allow access to Downloads below"
        is ModelState.Loading -> "${state.name}, loading"
        is ModelState.Ready -> "${state.name}, ready on ${state.backend}, ${gigabytes(state.sizeBytes)}"
        is ModelState.Failed -> "${state.name}, failed to load: ${state.reason}"
    }

    // Measured from the file that was loaded: the one in Downloads can be unreadable or gone by
    // then, which showed "0.00 GB" on the phone (03:20:37).
    private fun gigabytes(bytes: Long) = "%.2f GB".format(Locale.US, bytes / 1_000_000_000.0)

    private companion object {
        // Fast enough for the reply time to count up in tenths while the model writes.
        const val STATS_REFRESH_MS = 100L

        // The battery reports its temperature far less often than this.
        const val TEMPERATURE_REFRESH_MS = 1_000L

        // The stats screen listens too: without it nothing new could happen while it was open, and
        // only the session length moved (owner's screenshot, 08:00). A question asked there now
        // brings replies, and the reply numbers change in front of the judges.
        val LISTENING_SCREENS = setOf(Screen.Conversation, Screen.Transcript, Screen.Stats)
        const val DOWNLOAD_CHECK_MS = 3_000L
        const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
        const val CLOSES_MISSED_HINT = "Closes are not being read. Wearing glasses? Tap here to calibrate with them on."
        const val CUE_MS = 200
        const val CUE_VOLUME = 80
        const val TICK_MS = 50L
        const val CALIBRATION_DONE_MS = 2_500L

        // Gives the offline voice time to start so the first prompt is spoken.
        const val CALIBRATION_START_DELAY_MS = 1_500L
    }
}
