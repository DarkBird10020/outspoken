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

/** Loads the model once per app process, so it survives the activity being recreated. */
object OnDeviceModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ModelState>(ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun loadOnce(file: File?, cacheDir: File) {
        if (file == null || _state.value !is ModelState.Missing) return
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
