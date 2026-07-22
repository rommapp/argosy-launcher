package com.nendo.argosy.ui.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface RawInputInterceptor {
    val lastInputDevice: InputDevice?
    fun setRawKeyEventListener(listener: ((KeyEvent) -> Boolean)?)
    fun setRawMotionEventListener(listener: ((MotionEvent) -> Boolean)?)
    fun mapKeyToEvent(keyCode: Int): GamepadEvent?
}

data class GamepadInput(
    val event: GamepadEvent,
    val isRepeat: Boolean = false,
    val signature: InputSignature? = null
)

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
    data object Home : GamepadEvent
    data object LongConfirm : GamepadEvent
}

@Singleton
class GamepadInputHandler @Inject constructor(
    preferencesRepository: UserPreferencesRepository
) : RawInputInterceptor {

    private val _events = MutableSharedFlow<GamepadInput>(extraBufferCapacity = 16)
    private val _homeEvents = Channel<Unit>(Channel.BUFFERED)
    private val scope = SafeCoroutineScope(Dispatchers.Main.immediate, "GamepadInputHandler")

    var homeEventEnabled: Boolean = true

    private var swapAB = false
    private var swapXY = false
    private var swapStartSelect = false

    private enum class ModifierState { IDLE, HELD, COMBO_FIRED }
    private var modifierState = ModifierState.IDLE
    private var comboMap: Map<GamepadEvent, GamepadEvent> = emptyMap()

    var onActivity: (() -> Unit)? = null
    override var lastInputDevice: InputDevice? = null
        private set

    private var rawKeyEventListener: ((KeyEvent) -> Boolean)? = null
    private var rawMotionEventListener: ((MotionEvent) -> Boolean)? = null

    init {
        scope.launch {
            preferencesRepository.preferences.collect { prefs ->
                swapAB = prefs.swapAB
                swapXY = prefs.swapXY
                swapStartSelect = prefs.swapStartSelect
                comboMap = buildComboMap(prefs.selectLCombo, prefs.selectRCombo)
            }
        }
    }

    private fun buildComboMap(selectL: String, selectR: String): Map<GamepadEvent, GamepadEvent> {
        val map = mutableMapOf<GamepadEvent, GamepadEvent>()
        comboActionToEvent(selectL)?.let { map[GamepadEvent.PrevSection] = it }
        comboActionToEvent(selectR)?.let { map[GamepadEvent.NextSection] = it }
        return map
    }

    private fun comboActionToEvent(action: String): GamepadEvent? = when (action) {
        "quick_menu" -> GamepadEvent.LeftStickClick
        "quick_settings" -> GamepadEvent.RightStickClick
        else -> null
    }

    fun eventFlow(): Flow<GamepadInput> = _events.asSharedFlow()
    fun homeEventFlow(): Flow<Unit> = _homeEvents.receiveAsFlow()

    fun injectEvent(event: GamepadEvent) {
        emitWithDebounce(event, isRepeat = false)
    }

    private val lastInputTimes = mutableMapOf<GamepadEvent, Long>()
    private val inputDebounceMs = 140L
    private var inputBlockedUntil = 0L

    fun blockInputFor(durationMs: Long) {
        inputBlockedUntil = System.currentTimeMillis() + durationMs
    }

    fun emitHomeEvent() {
        if (homeEventEnabled) {
            _homeEvents.trySend(Unit)
        }
    }

    override fun setRawKeyEventListener(listener: ((KeyEvent) -> Boolean)?) {
        rawKeyEventListener = listener
        if (listener != null) modifierState = ModifierState.IDLE
    }

    override fun setRawMotionEventListener(listener: ((MotionEvent) -> Boolean)?) {
        rawMotionEventListener = listener
    }

    private var confirmDownTime = 0L
    private var confirmFired = false
    private var confirmDeferred = false
    private var confirmDeferJob: kotlinx.coroutines.Job? = null
    private val longPressThresholdMs = 500L

    private var lastStickDirection: GamepadEvent? = null
    private val stickDeadZone = 0.5f

    fun processStickMotion(event: MotionEvent): GamepadEvent? {
        if (event.source and InputDevice.SOURCE_JOYSTICK == 0) return null

        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)

        val direction = when {
            y < -stickDeadZone -> GamepadEvent.Up
            y > stickDeadZone -> GamepadEvent.Down
            x < -stickDeadZone -> GamepadEvent.Left
            x > stickDeadZone -> GamepadEvent.Right
            else -> null
        }

        if (direction == lastStickDirection) return null
        lastStickDirection = direction
        return direction
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        rawMotionEventListener?.let { listener ->
            return listener(event)
        }
        return false
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastInputDevice = event.device
            onActivity?.invoke()
            com.nendo.argosy.util.Logger.verbose("GamepadInput") { "KeyEvent: keyCode=${event.keyCode}, scanCode=${event.scanCode}, device=${event.device?.name}" }
        }

        rawKeyEventListener?.let { listener ->
            return listener(event)
        }

        val gamepadEvent = mapKeyToEvent(event.keyCode) ?: return false

        // Select modifier: hold Select to enter alt-mode for combo shortcuts
        if (gamepadEvent == GamepadEvent.Select && comboMap.isNotEmpty()) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    modifierState = ModifierState.HELD
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    val wasHeld = modifierState == ModifierState.HELD
                    modifierState = ModifierState.IDLE
                    if (wasHeld) {
                        emitWithDebounce(GamepadEvent.Select)
                    }
                    return true
                }
            }
        }

        // Confirm long-press: defer Confirm by the threshold duration. If the button is still
        // held when the timer fires, emit LongConfirm instead. If released (no more repeats),
        // the deferred Confirm fires. Uses a coroutine timer since ACTION_UP is unreliable
        // (controllers send repeated ACTION_DOWN instead).
        if (gamepadEvent == GamepadEvent.Confirm && event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                confirmDownTime = System.currentTimeMillis()
                confirmFired = false
                confirmDeferred = true
                confirmDeferJob?.cancel()
                confirmDeferJob = scope.launch {
                    kotlinx.coroutines.delay(longPressThresholdMs)
                    if (confirmDeferred && !confirmFired) {
                        confirmFired = true
                        confirmDeferred = false
                        emitWithDebounce(GamepadEvent.LongConfirm)
                    }
                }
                return true
            } else if (confirmDeferred) {
                return true
            } else {
                return true
            }
        }
        if (gamepadEvent == GamepadEvent.Confirm && event.action == KeyEvent.ACTION_UP) {
            confirmDeferJob?.cancel()
            if (confirmDeferred && !confirmFired) {
                confirmFired = true
                confirmDeferred = false
                emitWithDebounce(GamepadEvent.Confirm)
            }
            return true
        }

        if (event.action != KeyEvent.ACTION_DOWN) return true

        val isRepeat = event.repeatCount > 0

        val signature = InputSignature.of(event)

        // While Select is held, check combo map before normal dispatch
        if (modifierState == ModifierState.HELD || modifierState == ModifierState.COMBO_FIRED) {
            val comboEvent = comboMap[gamepadEvent]
            if (comboEvent != null) {
                modifierState = ModifierState.COMBO_FIRED
                emitWithDebounce(comboEvent, isRepeat, signature)
                return true
            }
        }

        emitWithDebounce(gamepadEvent, isRepeat, signature)
        return true
    }

    private fun emitWithDebounce(
        event: GamepadEvent,
        isRepeat: Boolean = false,
        signature: InputSignature? = null
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime < inputBlockedUntil) return
        val lastTime = lastInputTimes[event] ?: 0L
        if (currentTime - lastTime < inputDebounceMs) return
        lastInputTimes[event] = currentTime
        _events.tryEmit(GamepadInput(event, isRepeat, signature))
    }

    override fun mapKeyToEvent(keyCode: Int): GamepadEvent? =
        mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)
}

fun mapKeycodeToGamepadEvent(
    keyCode: Int,
    swapAB: Boolean = false,
    swapXY: Boolean = false,
    swapStartSelect: Boolean = false
): GamepadEvent? = when (keyCode) {
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
    KeyEvent.KEYCODE_HOME -> GamepadEvent.Home

    else -> null
}

fun gamepadEventToKeyCode(event: GamepadEvent): Int? = when (event) {
    GamepadEvent.Up -> KeyEvent.KEYCODE_DPAD_UP
    GamepadEvent.Down -> KeyEvent.KEYCODE_DPAD_DOWN
    GamepadEvent.Left -> KeyEvent.KEYCODE_DPAD_LEFT
    GamepadEvent.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
    else -> null
}
