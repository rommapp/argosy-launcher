package com.nendo.argosy.core.input

import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "ControllerPointerGuard"

/**
 * Keeps a game controller's touchpad pointer from moving input focus to one launcher window while
 * [follow]'s flow is true. Such a pointer reaching the window while another activity is in front
 * makes the window not touchable for [PASS_THROUGH_MS].
 */
class ControllerPointerGuard(
    private val windowName: String,
    private val scope: CoroutineScope,
    private val setWindowTouchable: (Boolean) -> Unit,
    private val isControllerPointer: (MotionEvent) -> Boolean = ::isControllerTouchpadEvent
) {
    @Volatile
    private var guarding = false
    private var inFront = false
    private var passThrough: Job? = null
    private var following: Job? = null

    fun follow(guardingFlow: Flow<Boolean>) {
        following?.cancel()
        following = scope.launch {
            guardingFlow.distinctUntilChanged().collect { active ->
                guarding = active
                if (!active) release()
            }
        }
    }

    fun consumes(event: MotionEvent): Boolean {
        if (!guarding || !isControllerPointer(event)) return false
        if (!inFront && passThrough == null) passPointersThrough()
        return true
    }

    fun onInFrontChanged(isInFront: Boolean) {
        inFront = isInFront
        if (isInFront) release()
    }

    fun release() {
        val job = passThrough ?: return
        job.cancel()
        endPassThrough()
    }

    private fun passPointersThrough() {
        Log.i(TAG, "Controller pointer over the $windowName during a game session, pointer input passes through it")
        setWindowTouchable(false)
        passThrough = scope.launch {
            delay(PASS_THROUGH_MS)
            endPassThrough()
        }
    }

    private fun endPassThrough() {
        passThrough = null
        setWindowTouchable(true)
    }

    companion object {
        const val PASS_THROUGH_MS = 3_000L
    }
}

fun controllerTouchpadGuarding(
    sessions: PlaySessionTracker,
    preferences: UserPreferencesRepository
): Flow<Boolean> = combine(sessions.activeSession, preferences.userPreferences) { session, prefs ->
    session?.isStream == false && prefs.ignoreControllerTouchpad
}

fun isControllerTouchpadEvent(event: MotionEvent): Boolean {
    val pointerEvent = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
        event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
        event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
    return pointerEvent && event.device?.isControllerTouchpad() == true
}
