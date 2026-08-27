package com.nendo.argosy.ui.screens.settings.components

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.domain.model.GripAutoController
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private fun InputDevice.isGamepadDevice(): Boolean =
    (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
        (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)

private fun isRemoveKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_BUTTON_X

private fun isDirectionalKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

@Composable
fun GripControllerModal(
    controllers: List<GripAutoController>,
    onAdd: (controllerId: String, controllerName: String) -> Unit,
    onRemove: (controllerId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var capturing by remember { mutableStateOf(controllers.isEmpty()) }
    var focusedIndex by remember { mutableIntStateOf(0) }

    val gamepadInputHandler = LocalGamepadInputHandler.current
    val currentControllers = rememberUpdatedState(controllers)
    val currentOnAdd = rememberUpdatedState(onAdd)
    val currentOnRemove = rememberUpdatedState(onRemove)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val defaultControllerName = stringResource(R.string.settings_grip_controller_default_name)

    fun removeFocused() {
        val entries = currentControllers.value
        entries.getOrNull(focusedIndex)?.let { entry ->
            currentOnRemove.value(entry.controllerId)
            focusedIndex = (entries.size - 2).coerceAtLeast(0).coerceAtMost(focusedIndex)
        }
    }

    fun goBack() {
        if (capturing && currentControllers.value.isNotEmpty()) capturing = false
        else currentOnDismiss.value()
    }

    val onAddRow = focusedIndex >= controllers.size

    DisposableEffect(gamepadInputHandler, defaultControllerName) {
        val listener: (KeyEvent) -> Boolean = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val device = event.device
                val isBack = event.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                    event.keyCode == KeyEvent.KEYCODE_BACK
                val entries = currentControllers.value
                when {
                    capturing && isBack -> goBack()

                    capturing && device != null && device.isGamepadDevice() -> {
                        currentOnAdd.value(controllerIdOf(device), device.name ?: defaultControllerName)
                        capturing = false
                        focusedIndex = entries.size
                    }

                    isBack -> goBack()

                    event.keyCode == KeyEvent.KEYCODE_DPAD_UP ->
                        focusedIndex = (focusedIndex - 1).coerceAtLeast(0)

                    event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ->
                        focusedIndex = (focusedIndex + 1).coerceAtMost(entries.size)

                    event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                        event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER -> {
                        if (focusedIndex >= entries.size) capturing = true
                    }

                    isRemoveKey(event.keyCode) -> removeFocused()

                    isDirectionalKey(event.keyCode) -> {}
                }
            }
            true
        }

        gamepadInputHandler?.setRawKeyEventListener(listener)

        onDispose {
            gamepadInputHandler?.setRawKeyEventListener(null)
        }
    }

    Modal(
        title = stringResource(R.string.settings_grip_controller_title),
        subtitle = if (capturing) {
            stringResource(R.string.settings_grip_controller_subtitle_capturing)
        } else {
            stringResource(R.string.settings_grip_controller_subtitle_list)
        },
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = when {
            capturing -> listOf(InputButton.B to stringResource(R.string.settings_grip_controller_hint_cancel))
            onAddRow -> listOf(
                InputButton.A to stringResource(R.string.settings_grip_controller_hint_add),
                InputButton.B to stringResource(R.string.settings_grip_controller_hint_back)
            )
            else -> listOf(
                InputButton.Y to stringResource(R.string.settings_grip_controller_hint_remove),
                InputButton.B to stringResource(R.string.settings_grip_controller_hint_back)
            )
        },
        inlineFooterHints = true,
        onFooterHintClick = { button ->
            when (button) {
                InputButton.A -> if (!capturing && onAddRow) capturing = true
                InputButton.Y -> if (!capturing && !onAddRow) removeFocused()
                InputButton.B -> goBack()
                else -> {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            if (capturing) {
                ListeningRow()
            } else {
                controllers.forEachIndexed { index, controller ->
                    GripControllerRow(
                        name = controller.name,
                        isFocused = index == focusedIndex,
                        onClick = { focusedIndex = index }
                    )
                }
                AddControllerRow(
                    isFocused = focusedIndex >= controllers.size,
                    onClick = {
                        focusedIndex = controllers.size
                        capturing = true
                    }
                )
            }
        }
    }
}

@Composable
private fun ListeningRow() {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(Dimens.borderThin, theme.focusAccent, RoundedCornerShape(Dimens.radiusMd))
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(R.string.settings_grip_controller_listening_title),
            style = MaterialTheme.typography.titleMedium,
            color = theme.focusAccent
        )
        Text(
            text = stringResource(R.string.settings_grip_controller_listening_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GripControllerRow(name: String, isFocused: Boolean, onClick: () -> Unit) {
    ModalListRow(icon = true, label = name, isFocused = isFocused, onClick = onClick)
}

@Composable
private fun AddControllerRow(isFocused: Boolean, onClick: () -> Unit) {
    ModalListRow(icon = false, label = stringResource(R.string.settings_grip_controller_add_new_label), isFocused = isFocused, onClick = onClick)
}

@Composable
private fun ModalListRow(
    icon: Boolean,
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val contentColor = if (isFocused) {
        lerp(theme.focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isFocused) theme.focusAccent.copy(alpha = 0.15f) else Color.Transparent
            )
            .border(
                Dimens.borderThin,
                if (isFocused) theme.focusAccent else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(Dimens.radiusMd)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = if (icon) Icons.Default.Gamepad else Icons.Default.Add,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.padding(end = Dimens.spacingXs)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor
        )
    }
}
