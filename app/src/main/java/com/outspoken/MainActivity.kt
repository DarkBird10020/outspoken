package com.outspoken

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.outspoken.eye.EyeReader
import com.outspoken.eye.bindFrontCamera
import com.outspoken.setup.checkOfflineVoice
import com.outspoken.setup.findModelFile
import com.outspoken.ui.EyeCheckScreen
import com.outspoken.ui.SetupStatus
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val eyeReader = EyeReader()
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private var cameraGranted by mutableStateOf(false)
    private var offlineVoice by mutableStateOf<Boolean?>(null)

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The speaker cannot touch the phone, so it must never sleep mid-conversation.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) cameraPermission.launch(Manifest.permission.CAMERA)

        checkOfflineVoice(this) { offlineVoice = it }
        val modelLine = modelStatusLine()

        setContent {
            MaterialTheme {
                val sample by eyeReader.samples.collectAsStateWithLifecycle()
                val fps by eyeReader.fps.collectAsStateWithLifecycle()
                EyeCheckScreen(
                    cameraGranted = cameraGranted,
                    sample = sample,
                    fps = fps,
                    setup = SetupStatus(modelLine, offlineVoice),
                    onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { view ->
                        bindFrontCamera(this, this, view, eyeReader, analyzerExecutor)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        eyeReader.close()
        analyzerExecutor.shutdown()
    }

    private fun modelStatusLine(): String {
        val dir = getExternalFilesDir(null)
        val model = findModelFile(dir) ?: return "missing, push a .litertlm file to ${dir?.absolutePath}"
        val gb = model.length() / 1_000_000_000.0
        return "${model.name} (${"%.2f".format(Locale.US, gb)} GB)"
    }
}
