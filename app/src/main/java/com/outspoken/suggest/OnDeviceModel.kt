package com.outspoken.suggest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface ModelState {
    data object Missing : ModelState
    data class Loading(val name: String) : ModelState
    data class Ready(val name: String, val backend: String, val model: LiteRtLmModel) : ModelState
    data class Failed(val name: String, val reason: String) : ModelState
}

/** Holds the model for the whole app process, so it survives the activity being recreated. */
object OnDeviceModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ModelState>(ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Loads [file] unless a model is already loading or loaded. A failed load can be retried. */
    fun load(file: File?, cacheDir: File) {
        val current = _state.value
        if (file == null || current is ModelState.Loading || current is ModelState.Ready) return
        start(file, cacheDir)
    }

    /**
     * Loads [file] in place of the model in use, freeing that one first: two Gemma models at once
     * would not fit in memory. Does nothing while a model is still loading.
     */
    fun switchTo(file: File, cacheDir: File) {
        val current = _state.value
        if (current is ModelState.Loading) return
        if (current is ModelState.Ready && current.name == file.name) return
        _state.value = ModelState.Loading(file.name)
        scope.launch {
            if (current is ModelState.Ready) current.model.close()
            start(file, cacheDir)
        }
    }

    private fun start(file: File, cacheDir: File) {
        _state.value = ModelState.Loading(file.name)
        scope.launch {
            val model = LiteRtLmModel(file.path, cacheDir.path)
            _state.value = try {
                model.load()
                ModelState.Ready(file.name, model.backendName, model)
            } catch (e: Exception) {
                ModelState.Failed(file.name, e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
