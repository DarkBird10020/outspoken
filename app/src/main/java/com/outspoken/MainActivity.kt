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
import com.outspoken.blink.BlinkDetector
import com.outspoken.conversation.ConversationController
import com.outspoken.eye.EyeReader
import com.outspoken.eye.FrontCamera
import com.outspoken.log.AppLog
import com.outspoken.setup.checkOfflineVoice
import com.outspoken.setup.findModelFile
import com.outspoken.speech.Speaker
import com.outspoken.ui.ConversationScreen
import com.outspoken.ui.EyeCheckScreen
import com.outspoken.ui.SetupStatus
import com.outspoken.ui.theme.OutspokenTheme
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Screen { Conversation, EyeCheck }

    private lateinit var speaker: Speaker
    private lateinit var camera: FrontCamera
    private val controller = ConversationController(
        speak = { speaker.speak(it) },
        detector = BlinkDetector(log = AppLog),
        log = AppLog,
    )
    private val eyeReader = EyeReader(onSample = { controller.onSample(it) })
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private var cameraGranted by mutableStateOf(false)
    private var offlineVoice by mutableStateOf<Boolean?>(null)
    private var screen by mutableStateOf(Screen.Conversation)

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

        checkOfflineVoice(this) {
            AppLog.write("app", "offline voice ${if (it) "ready" else "missing"}")
            offlineVoice = it
        }
        val modelLine = modelStatusLine()
        AppLog.write("app", "model $modelLine")

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
                MaterialTheme {
                    EyeCheckScreen(
                        cameraGranted = cameraGranted,
                        sample = sample,
                        fps = fps,
                        setup = SetupStatus(modelLine, offlineVoice),
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
                        onPractice = { show(Screen.EyeCheck) },
                        onStats = {},
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

    private fun show(next: Screen) {
        AppLog.write("ui", "screen $next")
        screen = next
    }

    private fun modelStatusLine(): String {
        val dir = getExternalFilesDir(null)
        val model = findModelFile(dir) ?: return "missing, push a .litertlm file to ${dir?.absolutePath}"
        val gb = model.length() / 1_000_000_000.0
        return "${model.name} (${"%.2f".format(Locale.US, gb)} GB)"
    }

    private companion object {
        const val TICK_MS = 50L
    }
}
