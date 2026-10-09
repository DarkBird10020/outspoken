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
import com.outspoken.conversation.ConversationController
import com.outspoken.eye.EyeReader
import com.outspoken.eye.FrontCamera
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
import com.outspoken.ui.SetupStatus
import com.outspoken.ui.describeReplies
import com.outspoken.ui.theme.OutspokenTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Screen { Conversation, EyeCheck }

    private lateinit var speaker: Speaker
    private lateinit var camera: FrontCamera
    private val controller = ConversationController(
        speak = { speaker.speak(it) },
        requestReplies = ::requestReplies,
    )
    private val eyeReader = EyeReader(onSample = { controller.onSample(it) })
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private var cameraGranted by mutableStateOf(false)
    private var offlineVoice by mutableStateOf<Boolean?>(null)
    private var screen by mutableStateOf(Screen.Conversation)
    private var lastReplyLine by mutableStateOf("none yet")

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

        speaker = Speaker(this) { controller.onSpeechDone(now()) }
        camera = FrontCamera(this, this)
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            camera.start(eyeReader, analyzerExecutor)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }

        checkOfflineVoice(this) { offlineVoice = it }
        val modelDir = getExternalFilesDir(null)
        val modelFile = findModelFile(modelDir)
        OnDeviceModel.loadOnce(modelFile, cacheDir)
        lifecycleScope.launch {
            OnDeviceModel.state.collect { state ->
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
                BackHandler(enabled = screen == Screen.EyeCheck) { screen = Screen.Conversation }
                val sample by eyeReader.samples.collectAsStateWithLifecycle()
                val fps by eyeReader.fps.collectAsStateWithLifecycle()
                val modelState by OnDeviceModel.state.collectAsStateWithLifecycle()
                MaterialTheme {
                    EyeCheckScreen(
                        cameraGranted = cameraGranted,
                        sample = sample,
                        fps = fps,
                        setup = SetupStatus(modelLine(modelState, modelDir?.absolutePath), offlineVoice, lastReplyLine),
                        onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                        onPreviewReady = camera::showPreview,
                        onPreviewGone = camera::hidePreview,
                    )
                }
            } else {
                val conversation by controller.ui.collectAsStateWithLifecycle()
                OutspokenTheme {
                    ConversationScreen(
                        ui = conversation,
                        // The practice round arrives in M3; until then this shows the live eye numbers.
                        onPractice = { screen = Screen.EyeCheck },
                        onStats = {},
                        onSelect = { controller.onTap(it, now()) },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speaker.shutdown()
        eyeReader.close()
        analyzerExecutor.shutdown()
    }

    private fun requestReplies(requestId: Int, turns: List<Turn>) {
        val engine = suggestionEngine ?: return
        replyJob?.cancel()
        replyJob = lifecycleScope.launch {
            val suggestions = engine.suggest(SuggestionRequest(turns, LocalTime.now().hour))
            lastReplyLine = describeReplies(suggestions.elapsedMs, suggestions.fromModel, suggestions.tokensPerSecond)
            controller.onReplies(requestId, suggestions.replies, suggestions.fromModel, now())
        }
    }

    private fun now() = SystemClock.elapsedRealtime()

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
