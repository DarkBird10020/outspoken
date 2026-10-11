package com.outspoken.control

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * The front camera's own lifecycle: running while Outspoken is on screen, and also while phone
 * control is on and another app is in front. Bound to the activity, the camera stopped as soon as
 * any other app opened, and the eyes could not be read there.
 */
class EyeCameraLifecycle : LifecycleOwner {

    private val registry = LifecycleRegistry(this)
    private var appShown = false
    private var controlOn = false
    private var closed = false

    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun appShown(shown: Boolean) {
        appShown = shown
        update()
    }

    fun controlOn(on: Boolean) {
        controlOn = on
        update()
    }

    fun close() {
        closed = true
        registry.currentState = Lifecycle.State.DESTROYED
    }

    private fun update() {
        if (closed) return
        registry.currentState = if (appShown || controlOn) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }
}
