package com.outspoken

import android.Manifest
import android.net.Uri
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
import com.outspoken.eye.FrontCamera
import com.outspoken.log.AppLog
import com.outspoken.log.exportLogs
import com.outspoken.scan.GazeStepper
import com.outspoken.scan.Scanner
import com.outspoken.setup.Tuning
import com.outspoken.setup.TuningStore
import com.outspoken.setup.checkOfflineVoice
import com.outspoken.setup.findModelFile
import com.outspoken.speech.Speaker
import com.outspoken.suggest.ModelImporter
import com.outspoken.suggest.ModelState
import com.outspoken.suggest.ModelSuggestionEngine
import com.outspoken.suggest.OnDeviceModel
import com.outspoken.suggest.SuggestionEngine
import com.outspoken.suggest.SuggestionRequest
import com.outspoken.suggest.Turn
import com.outspoken.ui.ConversationScreen
import com.outspoken.ui.EyeCheckScreen
import com.outspoken.ui.SetupStatus
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

    private enum class Screen { Conversation, EyeCheck }

    private lateinit var speaker: Speaker
    private lateinit var camera: FrontCamera
    private lateinit var tuningStore: TuningStore
    private lateinit var eyeReader: EyeReader
    private val blinkDetector = BlinkDetector(log = AppLog)
    private val scanner = Scanner()
    private val gazeStepper = GazeStepper(log = AppLog)
    private val controller = ConversationController(
        speak = { speaker.speak(it) },
        detector = blinkDetector,
        scanner = scanner,
        log = AppLog,
        gaze = gazeStepper,
        requestReplies = ::requestReplies,
    )
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private var cameraGranted by mutableStateOf(false)
    private var offlineVoice by mutableStateOf<Boolean?>(null)
    private var screen by mutableStateOf(Screen.Conversation)
    private var tuning by mutableStateOf(Tuning())
    private var lastReplyLine by mutableStateOf("none yet")
    private var importLine by mutableStateOf<String?>(null)
    private var suggestionEngine: SuggestionEngine? = null
    private var replyJob: Job? = null

    private val logSaver =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(::saveLogs) }

    private val modelPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importModel) }

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

        tuningStore = TuningStore(this)
        applyTuning(tuningStore.load())
        AppLog.write("app", "tuning $tuning")
        eyeReader = EyeReader(this) { controller.onSample(it) }
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

        checkOfflineVoice(this) {
            AppLog.write("app", "offline voice ${if (it) "ready" else "missing"}")
            offlineVoice = it
        }
        OnDeviceModel.load(findModelFile(getExternalFilesDir(null)), cacheDir)
        lifecycleScope.launch {
            OnDeviceModel.state.collect { state ->
                AppLog.write("model", modelLine(state))
                if (state is ModelState.Ready && suggestionEngine == null) {
                    suggestionEngine = ModelSuggestionEngine(state.model, ::now)
                    controller.refreshReplies()
                }
            }
        }

        setContent {
            LaunchedEffect(Unit) {
                while (true) {
                    controller.onTick(now())
                    delay(TICK_MS)
                }
            }
            if (!cameraGranted || screen == Screen.EyeCheck) {
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
                        setup = SetupStatus(importLine ?: modelLine(modelState), offlineVoice, lastReplyLine),
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
                        onChooseModel = { modelPicker.launch(arrayOf("*/*")) },
                        onSaveLogs = { logSaver.launch("outspoken-logs.txt") },
                    )
                }
            } else {
                val conversation by controller.ui.collectAsStateWithLifecycle()
                OutspokenTheme {
                    ConversationScreen(
                        ui = conversation,
                        // The practice round arrives in M3; until then this shows the live eye numbers.
                        onPractice = { show(Screen.EyeCheck) },
                        // Session stats arrive in M4; until then this opens the eye check screen.
                        onStats = { show(Screen.EyeCheck) },
                        onSelect = { controller.onTap(it, now()) },
                    )
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

    private fun now() = SystemClock.elapsedRealtime()

    private fun applyTuning(next: Tuning) {
        tuning = next
        blinkDetector.settings = next.blink
        scanner.intervalMs = next.scanMs
        gazeStepper.settings = next.gaze
        if (controller.moveByEyes != next.moveByEyes) controller.moveByEyes = next.moveByEyes
    }

    private fun show(next: Screen) {
        AppLog.write("ui", "screen $next")
        screen = next
        eyeReader.dotsOn = next == Screen.EyeCheck
    }

    private fun requestReplies(requestId: Int, turns: List<Turn>): Boolean {
        val engine = suggestionEngine ?: return false
        replyJob?.cancel()
        replyJob = lifecycleScope.launch {
            val suggestions = engine.suggest(SuggestionRequest(turns, LocalTime.now().hour))
            lastReplyLine = describeReplies(suggestions.elapsedMs, suggestions.fromModel, suggestions.tokensPerSecond)
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

    private fun importModel(uri: Uri) {
        importLine = "copying"
        lifecycleScope.launch {
            try {
                val dir = checkNotNull(getExternalFilesDir(null)) { "no app folder" }
                val file = ModelImporter(this@MainActivity).import(uri, dir) { percent ->
                    importLine = if (percent >= 0) "copying, $percent%" else "copying"
                }
                AppLog.write("model", "imported ${file.name} (${file.length() / 1_000_000} MB)")
                importLine = null
                if (OnDeviceModel.state.value is ModelState.Ready) {
                    importLine = "saved ${file.name}; close and reopen the app to switch to it"
                } else {
                    OnDeviceModel.load(file, cacheDir)
                }
            } catch (e: Exception) {
                AppLog.write("model", "import failed: ${e.message}")
                importLine = "could not import: ${e.message}"
            }
        }
    }

    private fun modelLine(state: ModelState): String = when (state) {
        ModelState.Missing -> "missing. Download a Gemma .litertlm file on this phone, then tap Choose model file"
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
