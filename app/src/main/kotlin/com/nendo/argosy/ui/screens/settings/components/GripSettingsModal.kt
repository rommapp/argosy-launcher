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
import com.nendo.argosy.data.preferences.GripReserveMode
import com.nendo.argosy.domain.model.GripAutoController
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
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
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> currentOnDismiss.value()
                        KeyEvent.KEYCODE_DPAD_UP ->
                            focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                        KeyEvent.KEYCODE_DPAD_DOWN ->
                            focusedIndex = (focusedIndex + 1).coerceAtMost(visible.size - 1)
                        KeyEvent.KEYCODE_DPAD_LEFT -> when (row) {
                            GripSettingsRow.MODE -> currentOnCycleMode.value(-1)
                            GripSettingsRow.RESERVED_HEIGHT ->
                                currentOnAdjustPercent.value(-GRIP_RESERVE_PERCENT_STEP)
                            else -> {}
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> when (row) {
                            GripSettingsRow.MODE -> currentOnCycleMode.value(1)
                            GripSettingsRow.RESERVED_HEIGHT ->
                                currentOnAdjustPercent.value(GRIP_RESERVE_PERCENT_STEP)
                            else -> {}
                        }
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> when (row) {
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
        title = "Controller Grip",
        subtitle = "Shift the UI up out of the area a grip covers",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_HORIZONTAL to "Adjust",
            InputButton.B to "Back"
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
                        title = "Mode",
                        value = mode.displayName,
                        subtitle = when (mode) {
                            GripReserveMode.OFF -> "Never shift the UI up"
                            GripReserveMode.ON -> "Always shift the UI up in portrait"
                            GripReserveMode.AUTO -> "Shift the UI up when a chosen controller is connected"
                        },
                        isFocused = rowFocused,
                        onClick = { onCycleMode(1) },
                        onPrev = { onCycleMode(-1) }
                    )

                    GripSettingsRow.CONTROLLERS -> NavigationPreference(
                        icon = Icons.Outlined.Gamepad,
                        title = "Controllers",
                        subtitle = gripAutoControllerSubtitle(controllers),
                        isFocused = rowFocused,
                        onClick = { showControllers = true }
                    )

                    GripSettingsRow.RESERVED_HEIGHT -> SliderPreference(
                        title = "Reserved Height",
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
