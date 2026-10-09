package com.outspoken.eye

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
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

    fun start(analyzer: ImageAnalysis.Analyzer, analyzerExecutor: Executor) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get().also { this.provider = it }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(fourByThree(CAMERA_SIZE))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analyzerExecutor, analyzer) }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            AppLog.write("camera", "front camera started, analysis ${analysis.resolutionInfo?.resolution}")
            previewView?.let { bindPreview(provider, it) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun showPreview(view: PreviewView) {
        previewView = view
        provider?.let { bindPreview(it, view) }
    }

    fun hidePreview() {
        previewView = null
        provider?.unbind(preview)
    }

    private companion object {
        // ML Kit wants a face at least 100 px wide for eye-open values and 200 px for the eye
        // outline. With the phone at arm's length the face is about a quarter of the frame, so
        // 640x480 is too small. Preview and analysis share 4:3 so the debug dots line up.
        val CAMERA_SIZE = Size(1280, 960)

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
