package dev.steenbakker.mobile_scanner

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A custom [LifecycleOwner] that controls the camera lifecycle independently
 * of the Activity lifecycle.
 *
 * This allows the camera to be paused and resumed by transitioning the lifecycle
 * state (RESUMED <-> CREATED) without unbinding/rebinding CameraX use cases,
 * which preserves the Surface and avoids the "Surface was abandoned" error.
 *
 * Activity lifecycle events are observed and propagated so that the camera
 * still stops when the app goes to background.
 */
class CameraLifecycleOwner : LifecycleOwner, DefaultLifecycleObserver {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var userPaused = false
    private var activityResumed = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    /**
     * Bind to the Activity lifecycle and start the camera lifecycle.
     *
     * The lifecycle is set up to RESUMED BEFORE adding the Activity observer
     * to avoid the observer's onResume callback interfering with initialization.
     */
    fun bind(activityOwner: LifecycleOwner) {
        activityResumed = activityOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        activityOwner.lifecycle.addObserver(this)
    }

    /**
     * Unbind from the Activity lifecycle and destroy the camera lifecycle.
     */
    fun unbind(activityOwner: LifecycleOwner) {
        activityOwner.lifecycle.removeObserver(this)
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    /**
     * Pause the camera (user-initiated).
     * Transitions to CREATED state: CameraX stops the capture session
     * but keeps use cases bound and surface alive.
     */
    fun pause() {
        userPaused = true
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    /**
     * Resume the camera (user-initiated).
     * Transitions back to RESUMED state: CameraX restarts with the same
     * use cases and surface (seamless, no flicker).
     */
    fun resume() {
        userPaused = false
        if (activityResumed) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    // Activity lifecycle callbacks — propagate unless user-paused

    override fun onResume(owner: LifecycleOwner) {
        activityResumed = true
        if (!userPaused && lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        activityResumed = false
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        activityResumed = false
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}
