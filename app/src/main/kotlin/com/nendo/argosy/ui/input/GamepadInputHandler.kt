package com.nendo.argosy.ui.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GamepadEvent {
    data object Up : GamepadEvent
    data object Down : GamepadEvent
    data object Left : GamepadEvent
    data object Right : GamepadEvent
    data object Confirm : GamepadEvent
    data object Back : GamepadEvent
    data object SecondaryAction : GamepadEvent
    data object ContextMenu : GamepadEvent
    data object PrevSection : GamepadEvent
    data object NextSection : GamepadEvent
    data object PrevTrigger : GamepadEvent
    data object NextTrigger : GamepadEvent
    data object Menu : GamepadEvent
    data object Select : GamepadEvent
    data object LeftStickClick : GamepadEvent
    data object RightStickClick : GamepadEvent
}

@Singleton
class GamepadInputHandler @Inject constructor(
    preferencesRepository: UserPreferencesRepository
) {

    private val _events = MutableSharedFlow<GamepadEvent>(extraBufferCapacity = 16)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var swapAB = false
    private var swapXY = false
    private var swapStartSelect = false

    private var rawKeyEventListener: ((KeyEvent) -> Boolean)? = null
    private var rawMotionEventListener: ((MotionEvent) -> Boolean)? = null

    init {
        scope.launch {
            preferencesRepository.preferences.collect { prefs ->
                swapAB = prefs.swapAB
                swapXY = prefs.swapXY
                swapStartSelect = prefs.swapStartSelect
            }
        }
    }

    fun eventFlow(): Flow<GamepadEvent> = _events.asSharedFlow()

    private val lastInputTimes = mutableMapOf<GamepadEvent, Long>()
    private val inputDebounceMs = 80L
    private var inputBlockedUntil = 0L

    fun blockInputFor(durationMs: Long) {
        inputBlockedUntil = System.currentTimeMillis() + durationMs
    }

    fun setRawKeyEventListener(listener: ((KeyEvent) -> Boolean)?) {
        rawKeyEventListener = listener
    }

    fun setRawMotionEventListener(listener: ((MotionEvent) -> Boolean)?) {
        rawMotionEventListener = listener
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        rawMotionEventListener?.let { listener ->
            return listener(event)
        }
        return false
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            android.util.Log.d("GamepadInput", "KeyEvent: keyCode=${event.keyCode}, scanCode=${event.scanCode}, device=${event.device?.name}")
        }

        rawKeyEventListener?.let { listener ->
            return listener(event)
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        val gamepadEvent = mapKeyToEvent(event.keyCode) ?: return false

        val currentTime = System.currentTimeMillis()
        if (currentTime < inputBlockedUntil) return true

        val lastTime = lastInputTimes[gamepadEvent] ?: 0L
        if (currentTime - lastTime < inputDebounceMs) return true
        lastInputTimes[gamepadEvent] = currentTime

        _events.tryEmit(gamepadEvent)
        return true
    }

    private fun mapKeyToEvent(keyCode: Int): GamepadEvent? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> GamepadEvent.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> GamepadEvent.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> GamepadEvent.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadEvent.Right

        KeyEvent.KEYCODE_BUTTON_A -> if (swapAB) GamepadEvent.Back else GamepadEvent.Confirm
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER -> GamepadEvent.Confirm

        KeyEvent.KEYCODE_BUTTON_B -> if (swapAB) GamepadEvent.Confirm else GamepadEvent.Back
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_BACK -> GamepadEvent.Back

        KeyEvent.KEYCODE_BUTTON_X -> if (swapXY) GamepadEvent.SecondaryAction else GamepadEvent.ContextMenu
        KeyEvent.KEYCODE_BUTTON_Y -> if (swapXY) GamepadEvent.ContextMenu else GamepadEvent.SecondaryAction

        KeyEvent.KEYCODE_BUTTON_L1 -> GamepadEvent.PrevSection
        KeyEvent.KEYCODE_BUTTON_R1 -> GamepadEvent.NextSection

        KeyEvent.KEYCODE_BUTTON_L2 -> GamepadEvent.PrevTrigger
        KeyEvent.KEYCODE_BUTTON_R2 -> GamepadEvent.NextTrigger

        KeyEvent.KEYCODE_BUTTON_START -> if (swapStartSelect) GamepadEvent.Select else GamepadEvent.Menu
        KeyEvent.KEYCODE_BUTTON_SELECT -> if (swapStartSelect) GamepadEvent.Menu else GamepadEvent.Select

        KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadEvent.LeftStickClick
        KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadEvent.RightStickClick

        else -> null
    }
}
