package com.nendo.argosy.ui.screens.setup.components

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.input.ButtonGlyphSwaps
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.intentButton
import com.nendo.argosy.ui.input.positionUnder
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@StringRes
fun InputButton.checkActionRes(): Int = when (this) {
    InputButton.A -> R.string.setup_check_action_confirm
    InputButton.B -> R.string.setup_check_action_back
    InputButton.X -> R.string.setup_check_action_options
    InputButton.Y -> R.string.setup_check_action_quick
    InputButton.START -> R.string.setup_check_action_menu
    else -> R.string.setup_check_action_select
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

/**
 * Lights the diagram while a face, Start or Select button is held and [state] holds focus on the
 * diagram, consuming those presses so none of them navigates or activates anything. D-pad keys
 * pass through to navigation.
 */
@Composable
fun ButtonCheckKeyCapture(state: ButtonCheckState, controller: ButtonCheckController) {
    val interceptor = LocalGamepadInputHandler.current
    val swaps by rememberUpdatedState(currentGlyphSwaps())
    val capturing = state.focus == ButtonCheckFocus.DIAGRAM
    DisposableEffect(interceptor, controller, capturing) {
        if (!capturing || interceptor == null) return@DisposableEffect onDispose {}
        interceptor.setRawKeyEventFilter { event ->
            val intent = checkedKeyButton(event.keyCode, interceptor::mapKeyToEvent)
                ?: return@setRawKeyEventFilter false
            val position = intent.positionUnder(swaps)
            when {
                position == null -> Unit
                event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> controller.light(position, intent)
                event.action == KeyEvent.ACTION_UP -> controller.release(position)
            }
            true
        }
        onDispose { interceptor.setRawKeyEventFilter(null) }
    }
}

/**
 * The button check: diagram and status on the left, swap toggles and the exit buttons on the
 * right. [backLabel] null leaves the Back button out.
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
            ControllerDiagram(
                litPosition = state.lit,
                focused = state.focus == ButtonCheckFocus.DIAGRAM,
                onPress = null,
                modifier = Modifier.clickableNoFocus { controller.focus(ButtonCheckFocus.DIAGRAM) },
            )
            Text(
                text = state.lastPress?.let {
                    stringResource(R.string.setup_check_status_last, stringResource(it.checkActionRes()))
                } ?: stringResource(R.string.setup_check_status_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        ) {
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
