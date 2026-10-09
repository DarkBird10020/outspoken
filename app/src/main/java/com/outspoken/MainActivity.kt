package com.outspoken

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.OpenableColumns
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.outspoken.blink.BlinkDetector
import com.outspoken.conversation.ConversationController
import com.outspoken.eye.EyeReader
import com.outspoken.eye.EyeSample
import com.outspoken.eye.FrontCamera
import com.outspoken.help.HelpAlarm
import com.outspoken.help.HelpStep
import com.outspoken.listen.HeardFilter
import com.outspoken.listen.Listener
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
import com.outspoken.suggest.ModelImporter
import com.outspoken.suggest.ModelState
import com.outspoken.suggest.ModelSuggestionEngine
import com.outspoken.suggest.OnDeviceModel
import com.outspoken.suggest.SuggestionEngine
import com.outspoken.suggest.SuggestionRequest
import com.outspoken.suggest.Turn
import com.outspoken.ui.CalibrationScreen
import com.outspoken.ui.ConversationScreen
import com.outspoken.ui.EyeCheckScreen
import com.outspoken.ui.EyeMonitor
import com.outspoken.ui.PracticeScreen
import com.outspoken.ui.HelpAlertScreen
import com.outspoken.ui.SetupStatus
import com.outspoken.ui.StatsScreen
import com.outspoken.ui.StatsUi
import com.outspoken.ui.TranscriptScreen
import com.outspoken.ui.TranscriptUi
import com.outspoken.ui.describeReplies
import com.outspoken.ui.theme.OutspokenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Screen { Conversation, Practice, EyeCheck, Calibrate, Help, Stats, Transcript }

    private lateinit var speaker: Speaker
    private lateinit var camera: FrontCamera
    private lateinit var tuningStore: TuningStore
    private lateinit var eyeReader: EyeReader
    private lateinit var practiceController: PracticeController
    private val blinkDetector = BlinkDetector(log = AppLog)
    private val scanner = Scanner()
    private val gazeStepper = GazeStepper(log = AppLog)
    private val controller = ConversationController(
        speak = { say(it) },
        detector = blinkDetector,
        scanner = scanner,
        log = AppLog,
        gaze = gazeStepper,
        requestReplies = ::requestReplies,
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
    private var lastReplyLine by mutableStateOf("none yet")
    private var importLine by mutableStateOf<String?>(null)
    private var suggestionEngine: SuggestionEngine? = null
    private var engineModel: Any? = null
    private lateinit var modelShelf: ModelShelf
    private var modelRows by mutableStateOf<List<ModelRow>>(emptyList())
    private var canSeeDownloads by mutableStateOf(false)
    private var replyJob: Job? = null

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
                // While the alarm screen or mirrored transcript is up, eyes pick nothing.
                Screen.Help, Screen.Transcript -> Unit
                else -> controller.onSample(sample)
            }
        }
        eyeReader.dotsOn = true
        speaker = Speaker(this) {
            heardFilter.onSpeechDone(now())
            listener.resume()
            controller.onSpeechDone(now())
        }
        alarm = HelpAlarm(this)
        // Tells the person, eyes still shut, that the help hold is done.
        cue = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, CUE_VOLUME) }.getOrNull()
        listener = Listener(
            this,
            onHeard = { text ->
                val question = heardFilter.accept(text, now())
                if (question == null) AppLog.write("listen", "ignored \"$text\" (the phone's own voice or too short)")
                question?.let { controller.onHeard(it, now()) }
            },
            onStatus = { listenLine = it },
        )
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
                    StatsScreen(statsUi(nowMs, modelState), onBack = { show(Screen.Conversation) })
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
                MaterialTheme {
                    CalibrationScreen(
                        prompt = calibrationPrompt,
                        progress = calibrationProgress,
                        outcome = calibrationOutcome,
                        failed = calibrationFailed,
                        sample = sample,
                        settings = tuning.blink,
                        onPreviewReady = camera::showPreview,
                        onPreviewGone = camera::hidePreview,
                        onRetry = ::startCalibration,
                        onSkip = { show(Screen.Conversation) },
                    )
                }
            } else if (!cameraGranted || screen == Screen.EyeCheck) {
                BackHandler(enabled = screen == Screen.EyeCheck) { show(Screen.Conversation) }
                val sample by eyeReader.samples.collectAsStateWithLifecycle()
                val fps by eyeReader.fps.collectAsStateWithLifecycle()
                val recent by AppLog.recent.collectAsStateWithLifecycle()
                val modelState by OnDeviceModel.state.collectAsStateWithLifecycle()
                MaterialTheme {
                    EyeCheckScreen(
                        cameraGranted = cameraGranted,
                        sample = sample,
                        fps = fps,
                        tuning = tuning,
                        recentLines = recent,
                        setup = SetupStatus(importLine ?: modelLine(modelState), offlineVoice, lastReplyLine, listenLine, buildLine),
                        onTuningChange = {
                            applyTuning(it)
                            tuningStore.save(it)
                        },
                        onTuningReset = {
                            tuningStore.clear()
                            applyTuning(Tuning())
                        },
                        onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                        onPreviewReady = camera::showPreview,
                        onPreviewGone = camera::hidePreview,
                        restGaze = gazeStepper.restGaze,
                        restIris = gazeStepper.restIrisDrop,
                        // The same smoothed values the steps use, so the dot is steady.
                        steadyGaze = gazeStepper.smoothedGaze,
                        steadyIris = gazeStepper.smoothedIrisDrop,
                        onChooseModel = { modelPicker.launch(arrayOf("*/*")) },
                        models = modelRows,
                        canSeeDownloads = canSeeDownloads,
                        onAllowDownloads = modelShelf::askToSeeDownloads,
                        onDownloadModel = ::downloadModel,
                        onUseModel = ::useModel,
                        onSaveLogs = { logSaver.launch("outspoken-logs.txt") },
                        onCalibrate = ::startCalibration,
                        onAsk = { question ->
                            controller.onHeard(question, now())
                            show(Screen.Conversation)
                        },
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
                OutspokenTheme {
                    ConversationScreen(
                        ui = conversation,
                        onPractice = {
                            practiceController.reset()
                            show(Screen.Practice)
                        },
                        onStats = { show(Screen.Stats) },
                        onEyeCheck = { show(Screen.EyeCheck) },
                        onTranscript = { show(Screen.Transcript) },
                        onSelect = { controller.onTap(it, now()) },
                        eyeHint = eyeHint(tuning),
                        onAsk = { question ->
                            AppLog.write("listen", "quick topic \"$question\"")
                            controller.onHeard(question, now())
                        },
                        eyeView = { modifier ->
                            val sample by eyeReader.samples.collectAsStateWithLifecycle()
                            EyeMonitor(sample, tuning.blink, camera::showPreview, camera::hidePreview, modifier)
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
                        "lid gap open ${m.restGap?.let { fmt(it) }} closed ${m.closedGap?.let { fmt(it) }} -> gap shut line " +
                        "${result.tuning.blink.shapeClosedBelow?.let { fmt(it) }}, gap open line ${result.tuning.blink.shapeOpenAbove?.let { fmt(it) }}",
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
            if (BuildConfig.FROM_CI) "CI APK" else "built on a laptop"

    private fun fmt(value: Float) = String.format(Locale.US, "%.2f", value)

    override fun onResume() {
        super.onResume()
        AppLog.write("app", "resumed")
        visible = true
        watchDownloads()
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
        speaker.shutdown()
        listener.stop()
        alarm.stop()
        cue?.release()
        // Closed on the camera thread, after any frame already being analysed, so no frame reaches
        // a closed face tracker.
        analyzerExecutor.execute { eyeReader.close() }
        analyzerExecutor.shutdown()
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun applyTuning(next: Tuning) {
        tuning = next
        blinkDetector.settings = next.blink
        scanner.intervalMs = next.scanMs
        gazeStepper.settings = next.activeGaze
        controller.upMovesNext = !next.lookDown
        if (controller.moveByEyes != next.moveByEyes) controller.moveByEyes = next.moveByEyes
        controller.winks = next.winks
    }

    private fun statsUi(nowMs: Long, model: ModelState) = StatsUi(
        replyTimeSeconds = pitStats.replyTimeSeconds,
        tokensPerSecond = pitStats.tokensPerSecond,
        repliesWritten = pitStats.repliesWritten,
        phoneTempCelsius = phoneTemperature(),
        modelName = (model as? ModelState.Ready)?.name?.removeSuffix(".litertlm") ?: "No model loaded",
        runtime = "LiteRT-LM" + ((model as? ModelState.Ready)?.let { " on ${it.backend}" } ?: ""),
        blinkAccuracyPercent = practiceController.accuracyPercent,
        sessionMillis = pitStats.sessionMillis(nowMs),
        sentencesSpoken = controller.history.size,
    )

    /** Battery temperature, the phone's own reading, in °C; null when the phone does not give it. */
    private fun phoneTemperature(): Float? {
        val battery = ContextCompat.registerReceiver(
            this,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        ) ?: return null
        val tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == Int.MIN_VALUE) null else tenths / 10f
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
        if (micGranted && visible && (screen == Screen.Conversation || screen == Screen.Transcript)) listener.start() else listener.stop()
    }

    private fun transcriptUi() = TranscriptUi(
        turns = controller.allTurns,
        isListening = micGranted && visible && (screen == Screen.Conversation || screen == Screen.Transcript),
        totalSentences = controller.history.size,
    )

    private fun show(next: Screen) {
        AppLog.write("ui", "screen $next")
        screen = next
        updateListening()
    }

    private fun requestReplies(requestId: Int, turns: List<Turn>): Boolean {
        val engine = suggestionEngine ?: return false
        replyJob?.cancel()
        replyJob = lifecycleScope.launch {
            val suggestions = engine.suggest(SuggestionRequest(turns, LocalTime.now().hour))
            lastReplyLine = describeReplies(suggestions.elapsedMs, suggestions.fromModel, suggestions.tokensPerSecond)
            pitStats.onReplies(suggestions.elapsedMs, suggestions.fromModel, suggestions.tokensPerSecond)
            AppLog.write(
                "model",
                "replies in ${suggestions.elapsedMs} ms, ${suggestions.tokensPerSecond ?: "-"} tok/s, " +
                    if (suggestions.fromModel) "from the model" else "phrase bank fallback",
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
                    modelShelf.fileToLoad()?.let { OnDeviceModel.load(it, cacheDir) }
                }
                refreshModelRows()
                delay(DOWNLOAD_CHECK_MS)
            }
        }
    }

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
                state is ModelState.Ready && state.name == choice.fileName -> "in use, on ${state.backend}"
                state is ModelState.Loading && state.name == choice.fileName -> "loading"
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
        is ModelState.Ready -> "${state.name}, ready on ${state.backend}, ${modelSize()}"
        is ModelState.Failed -> "${state.name}, failed to load: ${state.reason}"
    }

    private fun modelSize(): String {
        val gb = (modelShelf.fileToLoad()?.length() ?: 0) / 1_000_000_000.0
        return "%.2f GB".format(Locale.US, gb)
    }

    private companion object {
        const val STATS_REFRESH_MS = 1_000L
        const val DOWNLOAD_CHECK_MS = 3_000L
        const val CUE_MS = 200
        const val CUE_VOLUME = 80
        const val TICK_MS = 50L
        const val CALIBRATION_DONE_MS = 2_500L

        // Gives the offline voice time to start so the first prompt is spoken.
        const val CALIBRATION_START_DELAY_MS = 1_500L
    }
}
