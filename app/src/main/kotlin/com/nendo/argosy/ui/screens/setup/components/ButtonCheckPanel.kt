package com.nendo.argosy.ui.screens.setup.components

import android.view.InputDevice
import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.dualscreen.selectSwapModeState
import com.nendo.argosy.ui.input.ButtonGlyphSwaps
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The action Argosy's menus give [this] button, for the button check's status line. Select's
 * action follows [swapMode]; X, Y and buttons without a fixed menu action have none.
 */
@StringRes
fun InputButton.checkActionRes(swapMode: SelectSwapMode?): Int? = when (this) {
    InputButton.A -> R.string.setup_check_action_choose
    InputButton.B -> R.string.setup_check_action_back
    InputButton.START -> R.string.setup_check_action_menu
    InputButton.SELECT -> when (swapMode) {
        SelectSwapMode.HOLD -> R.string.setup_check_action_hold_swap_screens
        SelectSwapMode.TAP -> R.string.setup_check_action_swap_screens
        null -> null
    }
    else -> null
}

@StringRes
private fun ButtonCheckFocus.toggleTitleRes(): Int = when (this) {
    ButtonCheckFocus.SWAP_AB -> R.string.setup_check_toggle_swap_ab
    ButtonCheckFocus.SWAP_XY -> R.string.setup_check_toggle_swap_xy
    else -> R.string.setup_check_toggle_swap_start_select
}

@Composable
fun currentGlyphSwaps(): ButtonGlyphSwaps = ButtonGlyphSwaps(
    ab = LocalABIconsSwapped.current,
    xy = LocalXYIconsSwapped.current,
    startSelect = LocalSwapStartSelect.current,
)

private fun MotionEvent.padAxes(): PadAxes = PadAxes(
    leftX = getAxisValue(MotionEvent.AXIS_X),
    leftY = getAxisValue(MotionEvent.AXIS_Y),
    rightX = getAxisValue(MotionEvent.AXIS_Z),
    rightY = getAxisValue(MotionEvent.AXIS_RZ),
    leftTrigger = maxOf(getAxisValue(MotionEvent.AXIS_LTRIGGER), getAxisValue(MotionEvent.AXIS_BRAKE)),
    rightTrigger = maxOf(getAxisValue(MotionEvent.AXIS_RTRIGGER), getAxisValue(MotionEvent.AXIS_GAS)),
    hatX = getAxisValue(MotionEvent.AXIS_HAT_X),
    hatY = getAxisValue(MotionEvent.AXIS_HAT_Y),
)

/**
 * Routes raw pad input to [controller] while [state] captures keys: in test mode every pad key and
 * every joystick sample lights the drawing and nothing navigates, and after it the release of a
 * key still held is consumed. Keys with no pad button pass through.
 */
@Composable
fun ButtonCheckKeyCapture(state: ButtonCheckState, controller: ButtonCheckController) {
    val interceptor = LocalGamepadInputHandler.current
    DisposableEffect(interceptor, controller, state.capturesKeys) {
        if (!state.capturesKeys || interceptor == null) return@DisposableEffect onDispose {}
        interceptor.setRawKeyEventFilter { event ->
            val button = checkedPadButton(event.keyCode, interceptor::mapKeyToEvent)
            controller.onKey(event.keyCode, event.action, event.repeatCount, button)
        }
        onDispose { interceptor.setRawKeyEventFilter(null) }
    }
    DisposableEffect(interceptor, controller, state.testing) {
        if (!state.testing || interceptor == null) return@DisposableEffect onDispose {}
        interceptor.setRawMotionEventListener { event ->
            if (!event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) return@setRawMotionEventListener false
            controller.onMotion(event.padAxes())
            true
        }
        onDispose { interceptor.setRawMotionEventListener(null) }
    }
}

/**
 * The status under a controller drawing: the last button pressed and its menu action, or a prompt
 * while a test waits for its first press.
 */
@Composable
fun ButtonCheckLastPress(lastPress: InputButton?, testing: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val swapMode = selectSwapModeState()
    if (lastPress == null) {
        if (testing) {
            Text(
                text = stringResource(R.string.setup_check_status_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                modifier = modifier,
            )
        }
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        Text(
            text = stringResource(R.string.setup_check_status_last_label),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
        )
        InputGlyph(button = lastPress, size = Dimens.iconMd, tint = theme.textPrimary)
        lastPress.checkActionRes(swapMode)?.let { action ->
            Text(
                text = stringResource(action),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary,
            )
        }
    }
}

/**
 * The Test buttons row and the three swap toggles, dimmed while a test runs. Taps reach
 * [controller] in and out of test mode.
 */
@Composable
fun ButtonCheckRows(state: ButtonCheckState, controller: ButtonCheckController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (state.testing) ComponentDefaults.Setup.checkTestingRowAlpha else 1f),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        ActionPreference(
            title = stringResource(R.string.setup_check_test_row),
            subtitle = null,
            isFocused = state.focus == ButtonCheckFocus.TEST,
            icon = Icons.Default.SportsEsports,
            onClick = {
                controller.focus(ButtonCheckFocus.TEST)
                controller.startTest()
            },
        )
        state.toggles.forEach { toggle ->
            SwitchPreference(
                title = stringResource(toggle.toggleTitleRes()),
                isEnabled = state.isOn(toggle),
                isFocused = state.focus == toggle,
                onToggle = { on ->
                    controller.focus(toggle)
                    controller.setToggle(toggle, on)
                },
            )
        }
    }
}

/**
 * The button check with its drawing: diagram and status on the left, the test row, swap toggles
 * and exit buttons on the right. [backLabel] null leaves the Back button out.
 */
@Composable
fun ButtonCheckPanel(
    state: ButtonCheckState,
    controller: ButtonCheckController,
    @StringRes continueLabel: Int,
    @StringRes backLabel: Int?,
    onExit: (ButtonCheckExit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalArgosyTheme.current
    ButtonCheckKeyCapture(state = state, controller = controller)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            ControllerDiagram(reading = state.reading, swapped = state.swappedButtons)
            ButtonCheckLastPress(lastPress = state.reading.lastPress, testing = state.testing)
            if (state.testing) {
                Text(
                    text = stringResource(R.string.setup_check_testing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.focusAccent,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            ButtonCheckRows(state = state, controller = controller)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.End),
            ) {
                if (backLabel != null && state.showsBack) {
                    ActionButton(
                        label = stringResource(backLabel),
                        focused = state.focus == ButtonCheckFocus.BACK,
                        onClick = { onExit(ButtonCheckExit.BACK) },
                    )
                }
                ActionButton(
                    label = stringResource(continueLabel),
                    focused = state.focus == ButtonCheckFocus.CONTINUE,
                    primary = true,
                    onClick = { onExit(ButtonCheckExit.CONTINUE) },
                )
            }
        }
    }
}
