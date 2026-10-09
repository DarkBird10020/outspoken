package com.outspoken.eye

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

/** Front camera that always feeds the analyzer, with a preview only while a view asks for one. */
class FrontCamera(private val context: Context, private val owner: LifecycleOwner) {

    private val preview = Preview.Builder().build()
    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null

    fun start(analyzer: ImageAnalysis.Analyzer, analyzerExecutor: Executor) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get().also { this.provider = it }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analyzerExecutor, analyzer) }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
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

    private fun bindPreview(provider: ProcessCameraProvider, view: PreviewView) {
        preview.setSurfaceProvider(view.surfaceProvider)
        if (!provider.isBound(preview)) {
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
        }
    }
}
