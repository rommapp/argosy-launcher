package com.nendo.argosy.ui.screens.settings.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.GripReserveMode
import com.nendo.argosy.domain.model.GripAutoController
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.screens.settings.sections.GRIP_RESERVE_PERCENT_STEP
import com.nendo.argosy.ui.screens.settings.sections.gripAutoControllerSubtitle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MAX_PERCENT
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MIN_PERCENT

private enum class GripSettingsRow { MODE, CONTROLLERS, RESERVED_HEIGHT }

private fun rowsFor(mode: GripReserveMode): List<GripSettingsRow> = buildList {
    add(GripSettingsRow.MODE)
    if (mode == GripReserveMode.AUTO) add(GripSettingsRow.CONTROLLERS)
    if (mode != GripReserveMode.OFF) add(GripSettingsRow.RESERVED_HEIGHT)
}

@Composable
fun GripSettingsModal(
    mode: GripReserveMode,
    reservePercent: Int,
    controllers: List<GripAutoController>,
    onCycleMode: (Int) -> Unit,
    onAdjustPercent: (Int) -> Unit,
    onAddController: (String, String) -> Unit,
    onRemoveController: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    var showControllers by remember { mutableStateOf(false) }

    val rows = rowsFor(mode)
    val gamepadInputHandler = LocalGamepadInputHandler.current
    val currentRows = rememberUpdatedState(rows)
    val currentOnCycleMode = rememberUpdatedState(onCycleMode)
    val currentOnAdjustPercent = rememberUpdatedState(onAdjustPercent)
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    DisposableEffect(gamepadInputHandler, showControllers) {
        if (showControllers) {
            onDispose { }
        } else {
            val listener: (KeyEvent) -> Boolean = { event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val visible = currentRows.value
                    val row = visible.getOrNull(focusedIndex)
                    when (gamepadInputHandler?.mapKeyToEvent(event.keyCode)) {
                        GamepadEvent.Back -> currentOnDismiss.value()
                        GamepadEvent.Up ->
                            focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                        GamepadEvent.Down ->
                            focusedIndex = (focusedIndex + 1).coerceAtMost(visible.size - 1)
                        GamepadEvent.Left -> when (row) {
                            GripSettingsRow.MODE -> currentOnCycleMode.value(-1)
                            GripSettingsRow.RESERVED_HEIGHT ->
                                currentOnAdjustPercent.value(-GRIP_RESERVE_PERCENT_STEP)
                            else -> {}
                        }
                        GamepadEvent.Right -> when (row) {
                            GripSettingsRow.MODE -> currentOnCycleMode.value(1)
                            GripSettingsRow.RESERVED_HEIGHT ->
                                currentOnAdjustPercent.value(GRIP_RESERVE_PERCENT_STEP)
                            else -> {}
                        }
                        GamepadEvent.Confirm -> when (row) {
                            GripSettingsRow.MODE -> currentOnCycleMode.value(1)
                            GripSettingsRow.CONTROLLERS -> showControllers = true
                            else -> {}
                        }
                        else -> {}
                    }
                }
                true
            }
            gamepadInputHandler?.setRawKeyEventListener(listener)
            onDispose { gamepadInputHandler?.setRawKeyEventListener(null) }
        }
    }

    if (showControllers) {
        GripControllerModal(
            controllers = controllers,
            onAdd = onAddController,
            onRemove = onRemoveController,
            onDismiss = { showControllers = false }
        )
        return
    }

    Modal(
        title = stringResource(R.string.settings_grip_modal_title),
        subtitle = stringResource(R.string.settings_grip_modal_subtitle),
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_HORIZONTAL to stringResource(R.string.settings_grip_modal_hint_adjust),
            InputButton.B to stringResource(R.string.settings_grip_modal_hint_back)
        ),
        inlineFooterHints = true,
        onFooterHintClick = { button -> if (button == InputButton.B) onDismiss() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            rows.forEachIndexed { index, row ->
                val rowFocused = index == focusedIndex
                when (row) {
                    GripSettingsRow.MODE -> CyclePreference(
                        title = stringResource(R.string.settings_grip_modal_mode_title),
                        value = mode.displayName,
                        subtitle = when (mode) {
                            GripReserveMode.OFF ->
                                stringResource(R.string.settings_grip_modal_mode_subtitle_off)
                            GripReserveMode.ON ->
                                stringResource(R.string.settings_grip_modal_mode_subtitle_on)
                            GripReserveMode.AUTO ->
                                stringResource(R.string.settings_grip_modal_mode_subtitle_auto)
                        },
                        isFocused = rowFocused,
                        onClick = { onCycleMode(1) },
                        onPrev = { onCycleMode(-1) }
                    )

                    GripSettingsRow.CONTROLLERS -> NavigationPreference(
                        icon = Icons.Outlined.Gamepad,
                        title = stringResource(R.string.settings_grip_modal_controllers_title),
                        subtitle = gripAutoControllerSubtitle(controllers),
                        isFocused = rowFocused,
                        onClick = { showControllers = true }
                    )

                    GripSettingsRow.RESERVED_HEIGHT -> SliderPreference(
                        title = stringResource(R.string.settings_grip_modal_reserved_height_title),
                        value = reservePercent,
                        minValue = GRIP_RESERVE_MIN_PERCENT,
                        maxValue = GRIP_RESERVE_MAX_PERCENT,
                        isFocused = rowFocused,
                        step = GRIP_RESERVE_PERCENT_STEP,
                        suffix = "%",
                        onAdjust = onAdjustPercent
                    )
                }
            }
        }
    }
}
