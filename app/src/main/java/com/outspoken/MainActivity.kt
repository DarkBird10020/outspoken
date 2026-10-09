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
import com.outspoken.log.AppLog
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
import com.outspoken.ui.EyeLines
import com.outspoken.ui.PracticeScreen
import com.outspoken.ui.PracticeUi
import com.outspoken.ui.SetupStatus
import com.outspoken.ui.describeReplies
import com.outspoken.ui.formatWhole
import com.outspoken.ui.theme.OutspokenTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Screen { Practice, Conversation, EyeCheck }

    private lateinit var speaker: Speaker
    private lateinit var camera: FrontCamera

    private val detector = BlinkDetector(log = AppLog)
    private val scanner = Scanner()
    private val controller = ConversationController(
        speak = { speaker.speak(it) },
        requestReplies = ::requestReplies,
        detector = detector,
        scanner = scanner,
        log = AppLog,
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
            AppLog.write("app", "camera permission ${if (granted) "granted" else "denied"}")
            if (granted) camera.start(eyeReader, analyzerExecutor)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The speaker cannot touch the phone, so it must never sleep mid-conversation.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        speaker = Speaker(this) { controller.onSpeechDone(now()) }
        camera = FrontCamera(this, this)
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        AppLog.write("app", "camera permission ${if (cameraGranted) "already granted" else "requested"}")
        if (cameraGranted) {
            camera.start(eyeReader, analyzerExecutor)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        startPractice()

        checkOfflineVoice(this) {
            AppLog.write("app", "offline voice ${if (it) "ready" else "missing"}")
            offlineVoice = it
        }
        val modelDir = getExternalFilesDir(null)
        OnDeviceModel.loadOnce(findModelFile(modelDir), cacheDir)
        lifecycleScope.launch {
            OnDeviceModel.state.collect { state ->
                AppLog.write("model", modelLine(state, modelDir?.absolutePath))
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
                if (screen == Screen.Practice) finishPractice() else show(Screen.Conversation)
            }
            when {
                !cameraGranted || screen == Screen.EyeCheck -> {
                    val sample by eyeReader.samples.collectAsStateWithLifecycle()
                    val fps by eyeReader.fps.collectAsStateWithLifecycle()
                    val modelState by OnDeviceModel.state.collectAsStateWithLifecycle()
                    val recent by AppLog.recent.collectAsStateWithLifecycle()
                    MaterialTheme {
                        EyeCheckScreen(
                            cameraGranted = cameraGranted,
                            sample = sample,
                            fps = fps,
                            settings = eyeLines(),
                            recentLines = recent,
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
                            onStatusLongPress = { show(Screen.EyeCheck) },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLog.write("app", "resumed")
    }

    override fun onPause() {
        super.onPause()
        AppLog.write("app", "paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.write("app", "closed")
        speaker.shutdown()
        eyeReader.close()
        analyzerExecutor.shutdown()
    }

    private fun onSample(sample: EyeSample) {
        val session = practice
        if (screen == Screen.Practice && session != null) session.onSample(sample) else controller.onSample(sample)
    }

    private fun startPractice() {
        val now = now()
        practice = PracticeSession(
            detector = detector,
            startMs = now,
            scanIntervalMs = scanner.intervalMs,
            log = AppLog,
        ).also { practiceUi = it.update(now) }
        show(Screen.Practice)
    }

    private fun finishPractice() {
        practice?.let { session ->
            practiceAccuracy = session.accuracyPercent ?: practiceAccuracy
            AppLog.write("practice", "ended: ${session.caught} caught, ${session.missed} missed, ${session.falseBlinks} stray")
        }
        practice = null
        show(Screen.Conversation)
    }

    private fun requestReplies(requestId: Int, turns: List<Turn>): Boolean {
        val engine = suggestionEngine ?: return false
        replyJob?.cancel()
        replyJob = lifecycleScope.launch {
            val suggestions = engine.suggest(SuggestionRequest(turns, LocalTime.now().hour))
            lastReplyLine = describeReplies(suggestions.elapsedMs, suggestions.fromModel, suggestions.tokensPerSecond)
            AppLog.write("model", "replies in ${suggestions.elapsedMs} ms, ${suggestions.tokensPerSecond ?: "-"} tok/s, ${if (suggestions.fromModel) "from the model" else "phrase bank fallback"}")
            controller.onReplies(requestId, suggestions.replies, suggestions.fromModel, now())
        }
        return true
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun show(next: Screen) {
        AppLog.write("ui", "screen $next")
        screen = next
        eyeReader.dotsOn = next == Screen.EyeCheck
    }

    private fun detectorLines(): List<String> {
        val closures = detector.recentClosures.joinToString { "${it.durationMs} ms ${if (it.accepted) "picked" else "ignored"}" }
        return listOf(
            "Open level: ${"%.2f".format(Locale.US, detector.openLevel)}, shut below ${"%.2f".format(Locale.US, detector.closedBelow)}, open above ${"%.2f".format(Locale.US, detector.openAbove)}",
            "Last closures: ${closures.ifEmpty { "none" }}",
            "Practice accuracy: ${formatWhole(practiceAccuracy, "%")}",
        )
    }

    private fun eyeLines() = with(detector.settings) {
        EyeLines(detector.closedBelow, detector.openAbove, minBlinkMs, maxBlinkMs, maxYawDeg, maxPitchDeg)
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
