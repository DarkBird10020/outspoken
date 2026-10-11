package com.outspoken.eye

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.outspoken.log.AppLog
import java.util.concurrent.Executor

/** Front camera that always feeds the analyzer, with a preview only while a view asks for one. */
class FrontCamera(private val context: Context, private val owner: LifecycleOwner) {

    private val preview = Preview.Builder()
        .setResolutionSelector(fourByThree(CAMERA_SIZE))
        .build()
    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var watched: CameraInfo? = null

    /** The app is behind another one; a preview asked for now waits for [resumePreview]. */
    private var hidden = false

    fun start(analyzer: ImageAnalysis.Analyzer, analyzerExecutor: Executor) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get().also { this.provider = it }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(fourByThree(ANALYSIS_SIZE))
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analyzerExecutor, analyzer) }
            provider.unbindAll()
            val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            AppLog.write("camera", "front camera started, analysis ${analysis.resolutionInfo?.resolution}")
            watchState(camera.cameraInfo)
            if (!hidden) previewView?.let { bindPreview(provider, it) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun showPreview(view: PreviewView) {
        previewView = view
        if (!hidden) provider?.let { bindPreview(it, view) }
    }

    /** Only the view that is showing the preview can stop it, so a screen switch cannot cut the new one. */
    fun hidePreview(view: PreviewView) {
        if (previewView !== view) return
        previewView = null
        provider?.unbind(preview)
    }

    /**
     * When the app goes behind another one the preview's surface is destroyed. Left bound, it
     * stalls the whole camera session, analysis included, so phone control saw no eye frames.
     * The view is kept so [resumePreview] can put the preview back.
     */
    fun pausePreview() {
        hidden = true
        val provider = provider ?: return
        if (provider.isBound(preview)) {
            provider.unbind(preview)
            AppLog.write("camera", "preview off while the app is hidden, eye analysis keeps running")
        }
    }

    fun resumePreview() {
        hidden = false
        val view = previewView ?: return
        provider?.let { bindPreview(it, view) }
    }

    /** Logs when the camera opens, closes or fails, so a stop in the background shows its cause. */
    private fun watchState(info: CameraInfo) {
        if (watched === info) return
        watched = info
        info.cameraState.observeForever { state ->
            val error = state.error?.let { ", ${errorText(it.code)}" } ?: ""
            AppLog.write("camera", "camera ${state.type.name.lowercase().replace('_', ' ')}$error")
        }
    }

    private companion object {
        fun errorText(code: Int) = when (code) {
            CameraState.ERROR_CAMERA_IN_USE -> "another app has the camera"
            CameraState.ERROR_MAX_CAMERAS_IN_USE -> "too many cameras open"
            CameraState.ERROR_CAMERA_DISABLED -> "Android blocked the camera (app in the background, or a phone setting)"
            CameraState.ERROR_STREAM_CONFIG -> "the camera could not set up its picture streams"
            CameraState.ERROR_CAMERA_FATAL_ERROR -> "the camera failed and must be restarted"
            CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "Do Not Disturb blocks the camera"
            else -> "camera error $code"
        }

        // The face is about a quarter of the frame at arm's length; 1280x960 keeps the eyes
        // detailed enough for the face mesh. Preview and analysis share 4:3 so the debug dots
        // line up.
        val CAMERA_SIZE = Size(1280, 960)

        // Face tracking only needs a small frame: MediaPipe crops the face to 256 x 256. On the
        // phone each 1280x960 frame cut the rate from 30 to 20-25 fps once a face was in view, so
        // the analysed frames are smaller; same 4:3 shape so the dots still line up.
        val ANALYSIS_SIZE = Size(640, 480)

        fun fourByThree(size: Size) = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            )
            .build()
    }

    private fun bindPreview(provider: ProcessCameraProvider, view: PreviewView) {
        preview.setSurfaceProvider(view.surfaceProvider)
        if (!provider.isBound(preview)) {
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
        }
    }
}
