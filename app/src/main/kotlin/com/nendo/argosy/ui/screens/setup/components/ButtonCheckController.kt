package com.nendo.argosy.ui.screens.setup.components

import android.view.KeyEvent
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.ControllerPosition
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ButtonCheckFocus { DIAGRAM, SWAP_AB, SWAP_XY, SWAP_START_SELECT, BACK, CONTINUE }

enum class ButtonCheckExit { BACK, CONTINUE }

data class ButtonCheckState(
    val focus: ButtonCheckFocus = ButtonCheckFocus.DIAGRAM,
    val lit: ControllerPosition? = null,
    val lastPress: InputButton? = null,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val swapStartSelect: Boolean = false,
    val showsBack: Boolean = true
) {
    val toggles: List<ButtonCheckFocus>
        get() = listOf(ButtonCheckFocus.SWAP_AB, ButtonCheckFocus.SWAP_XY, ButtonCheckFocus.SWAP_START_SELECT)

    val actions: List<ButtonCheckFocus>
        get() = if (showsBack) listOf(ButtonCheckFocus.BACK, ButtonCheckFocus.CONTINUE) else listOf(ButtonCheckFocus.CONTINUE)

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

private val CHECKED_KEYCODES = setOf(
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_BUTTON_B,
    KeyEvent.KEYCODE_BUTTON_X,
    KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT,
)

/**
 * The intent button the button check reads from a raw [keyCode]: face buttons, Start and Select
 * only. Every other key, d-pad included, returns null and stays with navigation.
 */
fun checkedKeyButton(keyCode: Int, mapKey: (Int) -> GamepadEvent?): InputButton? =
    if (keyCode in CHECKED_KEYCODES) mapKey(keyCode)?.checkedButton() else null

private val BOUNDARY = InputResult(
    handled = true,
    soundOverride = SoundType.BOUNDARY,
    hapticOverride = HapticPattern.BOUNDARY_HIT
)

class ButtonCheckController(
    private val preferences: UserPreferencesRepository,
    private val scope: CoroutineScope,
    showsBack: Boolean = true
) {
    private val _state = MutableStateFlow(ButtonCheckState(showsBack = showsBack))
    val state: StateFlow<ButtonCheckState> = _state.asStateFlow()

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
        _state.update { it.copy(focus = ButtonCheckFocus.DIAGRAM, lit = null, lastPress = null) }
    }

    fun light(position: ControllerPosition, intent: InputButton) {
        _state.update { it.copy(lit = position, lastPress = intent) }
    }

    fun release(position: ControllerPosition) {
        _state.update { if (it.lit == position) it.copy(lit = null) else it }
    }

    fun focus(target: ButtonCheckFocus) {
        _state.update { it.withFocus(target) }
    }

    private fun ButtonCheckState.withFocus(target: ButtonCheckFocus): ButtonCheckState =
        copy(focus = target, lit = if (target == ButtonCheckFocus.DIAGRAM) lit else null)

    fun move(dx: Int, dy: Int): InputResult {
        val current = _state.value
        val next = nextFocus(current, dx, dy)
        if (next == null) return BOUNDARY
        _state.update { it.withFocus(next) }
        return InputResult.HANDLED
    }

    private fun nextFocus(state: ButtonCheckState, dx: Int, dy: Int): ButtonCheckFocus? {
        val column = state.toggles + state.actions.last()
        return when (val focus = state.focus) {
            ButtonCheckFocus.DIAGRAM -> if (dx > 0 || dy > 0) state.toggles.first() else null
            in state.toggles -> when {
                dy < 0 && focus == state.toggles.first() -> ButtonCheckFocus.DIAGRAM
                dy != 0 -> column.getOrNull(column.indexOf(focus) + dy)
                else -> null
            }
            else -> when {
                dy < 0 -> state.toggles.last()
                dx != 0 -> state.actions.getOrNull(state.actions.indexOf(focus) + dx)
                else -> null
            }
        }
    }

    fun confirm(): Pair<InputResult, ButtonCheckExit?> = when (val focus = _state.value.focus) {
        ButtonCheckFocus.DIAGRAM -> InputResult.handled(SoundType.SILENT) to null
        ButtonCheckFocus.BACK -> InputResult.HANDLED to ButtonCheckExit.BACK
        ButtonCheckFocus.CONTINUE -> InputResult.HANDLED to ButtonCheckExit.CONTINUE
        else -> setToggle(focus, !_state.value.isOn(focus)) to null
    }

    /**
     * Start: focus moves to Continue, and a press already on Continue exits through it.
     */
    fun startPress(): ButtonCheckExit? {
        if (_state.value.focus == ButtonCheckFocus.CONTINUE) return ButtonCheckExit.CONTINUE
        focus(ButtonCheckFocus.CONTINUE)
        return null
    }

    fun back(): Boolean {
        if (_state.value.focus == ButtonCheckFocus.DIAGRAM) return false
        _state.update { it.copy(focus = ButtonCheckFocus.DIAGRAM) }
        return true
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
