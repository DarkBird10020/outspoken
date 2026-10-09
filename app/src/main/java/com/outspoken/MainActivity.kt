package com.outspoken

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.outspoken.blink.BlinkDetector
import com.outspoken.conversation.ConversationController
import com.outspoken.eye.EyeReader
import com.outspoken.eye.EyeSample
import com.outspoken.eye.FrontCamera
import com.outspoken.log.SessionLog
import com.outspoken.practice.PracticeSession
import com.outspoken.scan.Scanner
import com.outspoken.setup.checkOfflineVoice
import com.outspoken.setup.findModelFile
import com.outspoken.speech.Speaker
import com.outspoken.suggest.ModelState
import com.outspoken.suggest.ModelSuggestionEngine
import com.outspoken.suggest.OnDeviceModel
import com.outspoken.suggest.SuggestionEngine
import com.outspoken.suggest.SuggestionRequest
import com.outspoken.suggest.Turn
import com.outspoken.ui.ConversationScreen
import com.outspoken.ui.EyeCheckScreen
import com.outspoken.ui.PracticeScreen
import com.outspoken.ui.PracticeUi
import com.outspoken.ui.SetupStatus
import com.outspoken.ui.describeReplies
import com.outspoken.ui.formatWhole
import com.outspoken.ui.theme.OutspokenTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Screen { Practice, Conversation, EyeCheck }

    private lateinit var speaker: Speaker
    private lateinit var camera: FrontCamera
    private lateinit var sessionLog: SessionLog

    private val detector = BlinkDetector()
    private val scanner = Scanner()
    private val controller = ConversationController(
        speak = { speaker.speak(it) },
        requestReplies = ::requestReplies,
        detector = detector,
        scanner = scanner,
        log = { time, text -> sessionLog.event(time, text) },
    )
    private val eyeReader = EyeReader(onSample = ::onSample)
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private var cameraGranted by mutableStateOf(false)
    private var offlineVoice by mutableStateOf<Boolean?>(null)
    private var screen by mutableStateOf(Screen.Practice)
    private var lastReplyLine by mutableStateOf("none yet")
    private var practice: PracticeSession? = null
    private var practiceUi by mutableStateOf<PracticeUi?>(null)
    private var practiceAccuracy: Float? = null

    private var suggestionEngine: SuggestionEngine? = null
    private var replyJob: Job? = null

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
            if (granted) camera.start(eyeReader, analyzerExecutor)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The speaker cannot touch the phone, so it must never sleep mid-conversation.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sessionLog = SessionLog(getExternalFilesDir("logs") ?: File(filesDir, "logs"))
        speaker = Speaker(this) { controller.onSpeechDone(now()) }
        camera = FrontCamera(this, this)
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            camera.start(eyeReader, analyzerExecutor)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        startPractice()

        checkOfflineVoice(this) { offlineVoice = it }
        val modelDir = getExternalFilesDir(null)
        OnDeviceModel.loadOnce(findModelFile(modelDir), cacheDir)
        lifecycleScope.launch {
            OnDeviceModel.state.collect { state ->
                sessionLog.event(now(), "model ${state.javaClass.simpleName}")
                if (state is ModelState.Ready && suggestionEngine == null) {
                    suggestionEngine = ModelSuggestionEngine(state.model, ::now)
                    controller.refreshReplies()
                }
            }
        }

        setContent {
            LaunchedEffect(Unit) {
                while (true) {
                    val now = now()
                    controller.onTick(now)
                    practice?.let { practiceUi = it.update(now) }
                    delay(TICK_MS)
                }
            }
            BackHandler(enabled = screen != Screen.Conversation) {
                if (screen == Screen.Practice) finishPractice() else screen = Screen.Conversation
            }
            when {
                !cameraGranted || screen == Screen.EyeCheck -> {
                    val sample by eyeReader.samples.collectAsStateWithLifecycle()
                    val fps by eyeReader.fps.collectAsStateWithLifecycle()
                    val modelState by OnDeviceModel.state.collectAsStateWithLifecycle()
                    MaterialTheme {
                        EyeCheckScreen(
                            cameraGranted = cameraGranted,
                            sample = sample,
                            fps = fps,
                            setup = SetupStatus(
                                modelLine = modelLine(modelState, modelDir?.absolutePath),
                                offlineVoice = offlineVoice,
                                lastReplyLine = lastReplyLine,
                                details = detectorLines(),
                            ),
                            onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                            onPreviewReady = camera::showPreview,
                            onPreviewGone = camera::hidePreview,
                        )
                    }
                }
                screen == Screen.Practice -> practiceUi?.let { ui ->
                    OutspokenTheme {
                        PracticeScreen(ui, onBack = ::finishPractice, onStart = ::finishPractice)
                    }
                }
                else -> {
                    val conversation by controller.ui.collectAsStateWithLifecycle()
                    OutspokenTheme {
                        ConversationScreen(
                            ui = conversation,
                            onPractice = ::startPractice,
                            onStats = {},
                            onSelect = { controller.onTap(it, now()) },
                            onStatusLongPress = { screen = Screen.EyeCheck },
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sessionLog.flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        speaker.shutdown()
        eyeReader.close()
        analyzerExecutor.shutdown()
        sessionLog.close()
    }

    private fun onSample(sample: EyeSample) {
        sessionLog.sample(sample)
        val session = practice
        if (screen == Screen.Practice && session != null) session.onSample(sample) else controller.onSample(sample)
    }

    private fun startPractice() {
        val now = now()
        sessionLog.event(now, "practice started")
        practice = PracticeSession(
            detector = detector,
            startMs = now,
            scanIntervalMs = scanner.intervalMs,
            log = sessionLog::event,
        ).also { practiceUi = it.update(now) }
        screen = Screen.Practice
    }

    private fun finishPractice() {
        practice?.let { session ->
            practiceAccuracy = session.accuracyPercent ?: practiceAccuracy
            sessionLog.event(now(), "practice ended: ${session.caught} caught, ${session.missed} missed, ${session.falseBlinks} stray")
        }
        practice = null
        screen = Screen.Conversation
    }

    private fun requestReplies(requestId: Int, turns: List<Turn>): Boolean {
        val engine = suggestionEngine ?: return false
        replyJob?.cancel()
        replyJob = lifecycleScope.launch {
            val suggestions = engine.suggest(SuggestionRequest(turns, LocalTime.now().hour))
            lastReplyLine = describeReplies(suggestions.elapsedMs, suggestions.fromModel, suggestions.tokensPerSecond)
            sessionLog.event(now(), "reply time ${suggestions.elapsedMs} ms, ${suggestions.tokensPerSecond ?: "-"} tok/s")
            controller.onReplies(requestId, suggestions.replies, suggestions.fromModel, now())
        }
        return true
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun detectorLines(): List<String> {
        val closures = detector.recentClosures.joinToString { "${it.durationMs} ms ${if (it.accepted) "picked" else "ignored"}" }
        return listOf(
            "Open level: ${"%.2f".format(Locale.US, detector.openLevel)}, shut below ${"%.2f".format(Locale.US, detector.closedBelow)}, open above ${"%.2f".format(Locale.US, detector.openAbove)}",
            "Blink must last ${detector.settings.minBlinkMs} to ${detector.settings.maxBlinkMs} ms",
            "Last closures: ${closures.ifEmpty { "none" }}",
            "Practice accuracy: ${formatWhole(practiceAccuracy, "%")}",
            "Session log: ${sessionLog.file.absolutePath}",
        )
    }

    private fun modelLine(state: ModelState, dir: String?): String = when (state) {
        ModelState.Missing -> "missing, push a .litertlm file to $dir"
        is ModelState.Loading -> "${state.name}, loading"
        is ModelState.Ready -> "${state.name}, ready on ${state.backend}, ${modelSize()}"
        is ModelState.Failed -> "${state.name}, failed to load: ${state.reason}"
    }

    private fun modelSize(): String {
        val gb = (findModelFile(getExternalFilesDir(null))?.length() ?: 0) / 1_000_000_000.0
        return "%.2f GB".format(Locale.US, gb)
    }

    private companion object {
        const val TICK_MS = 50L
    }
}
