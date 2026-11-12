package com.nendo.argosy.ui.screens.setup.components

import android.view.KeyEvent
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ButtonCheckFocus { TEST, SWAP_AB, SWAP_XY, SWAP_START_SELECT, BACK, CONTINUE }

enum class ButtonCheckExit { BACK, CONTINUE }

data class PadAxes(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val hatX: Float = 0f,
    val hatY: Float = 0f
)

/**
 * What the pad is doing during a button test. [held] holds intent buttons pressed as key events;
 * [holding] is the one button counting toward the hold that ends the test, null while none or more
 * than one is down. [axes] carries stick, trigger and hat deflection, which never counts toward it.
 */
data class PadReading(
    val held: Set<InputButton> = emptySet(),
    val holding: InputButton? = null,
    val lastPress: InputButton? = null,
    val axes: PadAxes = PadAxes()
) {
    fun isLit(button: InputButton): Boolean = button in held || when (button) {
        InputButton.DPAD_UP -> axes.hatY < -HAT_PRESSED
        InputButton.DPAD_DOWN -> axes.hatY > HAT_PRESSED
        InputButton.DPAD_LEFT -> axes.hatX < -HAT_PRESSED
        InputButton.DPAD_RIGHT -> axes.hatX > HAT_PRESSED
        else -> false
    }
}

private const val HAT_PRESSED = 0.5f

/**
 * The button check. [testing] is test mode, where every input only lights the drawing. [releasing]
 * holds after the test ends while a key pressed during it is still down, and consumes its release.
 */
data class ButtonCheckState(
    val focus: ButtonCheckFocus = ButtonCheckFocus.TEST,
    val testing: Boolean = false,
    val releasing: Boolean = false,
    val reading: PadReading = PadReading(),
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val swapStartSelect: Boolean = false,
    val showsBack: Boolean = true
) {
    val toggles: List<ButtonCheckFocus>
        get() = listOf(ButtonCheckFocus.SWAP_AB, ButtonCheckFocus.SWAP_XY, ButtonCheckFocus.SWAP_START_SELECT)

    val rows: List<ButtonCheckFocus>
        get() = listOf(ButtonCheckFocus.TEST) + toggles

    val actions: List<ButtonCheckFocus>
        get() = if (showsBack) listOf(ButtonCheckFocus.BACK, ButtonCheckFocus.CONTINUE) else listOf(ButtonCheckFocus.CONTINUE)

    val capturesKeys: Boolean
        get() = testing || releasing

    val swappedButtons: Set<InputButton>
        get() = buildSet {
            if (swapAB) addAll(listOf(InputButton.A, InputButton.B))
            if (swapXY) addAll(listOf(InputButton.X, InputButton.Y))
            if (swapStartSelect) addAll(listOf(InputButton.START, InputButton.SELECT))
        }

    fun isOn(toggle: ButtonCheckFocus): Boolean = when (toggle) {
        ButtonCheckFocus.SWAP_AB -> swapAB
        ButtonCheckFocus.SWAP_XY -> swapXY
        ButtonCheckFocus.SWAP_START_SELECT -> swapStartSelect
        else -> false
    }
}

/**
 * The intent button a dispatched gamepad event carries, for the face and menu buttons the button
 * check lights. Other events carry none.
 */
fun GamepadEvent.checkedButton(): InputButton? = when (this) {
    GamepadEvent.Confirm -> InputButton.A
    GamepadEvent.Back -> InputButton.B
    GamepadEvent.ContextMenu -> InputButton.X
    GamepadEvent.SecondaryAction -> InputButton.Y
    GamepadEvent.Menu -> InputButton.START
    GamepadEvent.Select -> InputButton.SELECT
    else -> null
}

private val FACE_AND_MENU_KEYCODES = setOf(
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_BUTTON_B,
    KeyEvent.KEYCODE_BUTTON_X,
    KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT,
)

private val FIXED_KEYCODES = mapOf(
    KeyEvent.KEYCODE_DPAD_UP to InputButton.DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN to InputButton.DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT to InputButton.DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT to InputButton.DPAD_RIGHT,
    KeyEvent.KEYCODE_BUTTON_L1 to InputButton.LB,
    KeyEvent.KEYCODE_BUTTON_R1 to InputButton.RB,
    KeyEvent.KEYCODE_BUTTON_L2 to InputButton.LT,
    KeyEvent.KEYCODE_BUTTON_R2 to InputButton.RT,
    KeyEvent.KEYCODE_BUTTON_THUMBL to InputButton.LEFT_STICK_CLICK,
    KeyEvent.KEYCODE_BUTTON_THUMBR to InputButton.RIGHT_STICK_CLICK,
)

/**
 * The pad button a raw [keyCode] lights in test mode. Face, Start and Select keys read through the
 * swap mapping [mapKey]; the d-pad, shoulders, triggers and stick clicks have fixed buttons. Every
 * other key, Back, Enter and the d-pad centre included, returns null.
 */
fun checkedPadButton(keyCode: Int, mapKey: (Int) -> GamepadEvent?): InputButton? =
    FIXED_KEYCODES[keyCode] ?: if (keyCode in FACE_AND_MENU_KEYCODES) mapKey(keyCode)?.checkedButton() else null

private val BOUNDARY = InputResult(
    handled = true,
    soundOverride = SoundType.BOUNDARY,
    hapticOverride = HapticPattern.BOUNDARY_HIT
)

private val SILENT = InputResult.handled(SoundType.SILENT)

class ButtonCheckController(
    private val preferences: UserPreferencesRepository,
    private val scope: CoroutineScope,
    showsBack: Boolean = true
) {
    private val _state = MutableStateFlow(ButtonCheckState(showsBack = showsBack))
    val state: StateFlow<ButtonCheckState> = _state.asStateFlow()

    private var pressedKeys: Map<Int, InputButton> = emptyMap()
    private var awaitingRelease: Set<Int> = emptySet()
    private var holdJob: Job? = null

    init {
        scope.launch {
            preferences.userPreferences.collect { prefs ->
                _state.update {
                    it.copy(swapAB = prefs.swapAB, swapXY = prefs.swapXY, swapStartSelect = prefs.swapStartSelect)
                }
            }
        }
    }

    fun reset() {
        clearPad()
        awaitingRelease = emptySet()
        _state.update {
            it.copy(focus = ButtonCheckFocus.TEST, testing = false, releasing = false, reading = PadReading())
        }
    }

    fun startTest() {
        if (_state.value.testing) return
        clearPad()
        awaitingRelease = emptySet()
        _state.update {
            it.copy(focus = ButtonCheckFocus.TEST, testing = true, releasing = false, reading = PadReading())
        }
    }

    fun finishTest() {
        if (!_state.value.testing) return
        awaitingRelease = pressedKeys.keys
        clearPad()
        _state.update {
            it.copy(
                focus = ButtonCheckFocus.TEST,
                testing = false,
                releasing = awaitingRelease.isNotEmpty(),
                reading = PadReading(lastPress = it.reading.lastPress)
            )
        }
    }

    private fun clearPad() {
        holdJob?.cancel()
        holdJob = null
        pressedKeys = emptyMap()
    }

    /**
     * One raw key while the check captures keys. True consumes it. In test mode a key with a pad
     * [button] lights while down and is consumed; a key without one is declined. After the test a
     * key still down from it is consumed until its release.
     */
    fun onKey(keyCode: Int, action: Int, repeatCount: Int, button: InputButton?): Boolean {
        if (!_state.value.testing) return swallowRelease(keyCode, action)
        if (button == null) return false
        when (action) {
            KeyEvent.ACTION_DOWN -> if (repeatCount == 0 && keyCode !in pressedKeys) {
                pressedKeys = pressedKeys + (keyCode to button)
                press(button)
            }
            KeyEvent.ACTION_UP -> pressedKeys[keyCode]?.let { released ->
                pressedKeys = pressedKeys - keyCode
                if (released !in pressedKeys.values) release(released)
            }
        }
        return true
    }

    private fun swallowRelease(keyCode: Int, action: Int): Boolean {
        if (keyCode !in awaitingRelease) return false
        if (action == KeyEvent.ACTION_UP) {
            awaitingRelease = awaitingRelease - keyCode
            if (awaitingRelease.isEmpty()) _state.update { it.copy(releasing = false) }
        }
        return true
    }

    fun onMotion(axes: PadAxes) {
        if (!_state.value.testing) return
        _state.update { it.copy(reading = it.reading.copy(axes = axes)) }
    }

    private fun press(button: InputButton) {
        holdJob?.cancel()
        holdJob = null
        val single = _state.value.reading.held.isEmpty()
        _state.update {
            it.copy(
                reading = it.reading.copy(
                    held = it.reading.held + button,
                    holding = if (single) button else null,
                    lastPress = button
                )
            )
        }
        if (single) {
            holdJob = scope.launch {
                delay(ComponentDefaults.Setup.checkExitHoldMs.toLong())
                finishTest()
            }
        }
    }

    private fun release(button: InputButton) {
        if (_state.value.reading.holding == button) {
            holdJob?.cancel()
            holdJob = null
        }
        _state.update {
            val reading = it.reading
            it.copy(
                reading = reading.copy(
                    held = reading.held - button,
                    holding = if (reading.holding == button) null else reading.holding
                )
            )
        }
    }

    fun focus(target: ButtonCheckFocus) {
        _state.update { it.copy(focus = target) }
    }

    fun move(dx: Int, dy: Int): InputResult {
        val current = _state.value
        if (current.testing) return SILENT
        val next = nextFocus(current, dx, dy) ?: return BOUNDARY
        _state.update { it.copy(focus = next) }
        return InputResult.HANDLED
    }

    private fun nextFocus(state: ButtonCheckState, dx: Int, dy: Int): ButtonCheckFocus? {
        val column = state.rows + state.actions.last()
        return when (val focus = state.focus) {
            in state.rows -> if (dy != 0) column.getOrNull(column.indexOf(focus) + dy) else null
            else -> when {
                dy < 0 -> state.rows.last()
                dx != 0 -> state.actions.getOrNull(state.actions.indexOf(focus) + dx)
                else -> null
            }
        }
    }

    fun confirm(): Pair<InputResult, ButtonCheckExit?> {
        val current = _state.value
        if (current.testing) return SILENT to null
        return when (val focus = current.focus) {
            ButtonCheckFocus.TEST -> {
                startTest()
                InputResult.HANDLED to null
            }
            ButtonCheckFocus.BACK -> InputResult.HANDLED to ButtonCheckExit.BACK
            ButtonCheckFocus.CONTINUE -> InputResult.HANDLED to ButtonCheckExit.CONTINUE
            else -> setToggle(focus, !current.isOn(focus)) to null
        }
    }

    /**
     * Start: focus moves to Continue, and a press already on Continue exits through it. Does
     * nothing in test mode.
     */
    fun startPress(): ButtonCheckExit? {
        val current = _state.value
        if (current.testing) return null
        if (current.focus == ButtonCheckFocus.CONTINUE) return ButtonCheckExit.CONTINUE
        focus(ButtonCheckFocus.CONTINUE)
        return null
    }

    fun setToggle(toggle: ButtonCheckFocus, on: Boolean): InputResult {
        _state.update {
            when (toggle) {
                ButtonCheckFocus.SWAP_AB -> it.copy(swapAB = on)
                ButtonCheckFocus.SWAP_XY -> it.copy(swapXY = on)
                ButtonCheckFocus.SWAP_START_SELECT -> it.copy(swapStartSelect = on)
                else -> it
            }
        }
        scope.launch {
            when (toggle) {
                ButtonCheckFocus.SWAP_AB -> preferences.setSwapAB(on)
                ButtonCheckFocus.SWAP_XY -> preferences.setSwapXY(on)
                ButtonCheckFocus.SWAP_START_SELECT -> preferences.setSwapStartSelect(on)
                else -> Unit
            }
        }
        return InputResult.toggled(on)
    }
}
