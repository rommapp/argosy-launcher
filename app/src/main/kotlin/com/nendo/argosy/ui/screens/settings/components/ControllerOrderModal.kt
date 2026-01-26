package com.nendo.argosy.ui.screens.settings.components

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.theme.Dimens

data class AssignedController(
    val controllerId: String,
    val controllerName: String,
    val device: InputDevice
)

@Composable
fun ControllerOrderModal(
    existingOrder: List<ControllerOrderEntity>,
    onAssign: (port: Int, device: InputDevice) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val assignments = remember {
        mutableStateMapOf<Int, AssignedController>().apply {
            existingOrder.forEach { entity ->
                val device = findDeviceByControllerId(entity.controllerId)
                if (device != null) {
                    put(entity.port, AssignedController(
                        controllerId = entity.controllerId,
                        controllerName = entity.controllerName,
                        device = device
                    ))
                }
            }
        }
    }
    var activeSlot by remember { mutableIntStateOf(findFirstEmptySlot(assignments)) }

    val gamepadInputHandler = LocalGamepadInputHandler.current

    DisposableEffect(gamepadInputHandler) {
        val listener: (KeyEvent) -> Boolean = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val device = event.device
                val controllerId = device?.let { getControllerId(it) }
                val registeredPort = controllerId?.let { id ->
                    assignments.entries.find { it.value.controllerId == id }?.key
                }

                when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                        if (registeredPort != null) {
                            assignments.remove(registeredPort)
                            activeSlot = findFirstEmptySlot(assignments)
                        } else {
                            onDismiss()
                        }
                    }
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        if (assignments.isNotEmpty()) {
                            onDismiss()
                        }
                    }
                    else -> {
                        if (registeredPort == null &&
                            device != null &&
                            isGamepadDevice(device) &&
                            isGamepadButton(event.keyCode) &&
                            activeSlot < 4
                        ) {
                            assignments[activeSlot] = AssignedController(
                                controllerId = controllerId!!,
                                controllerName = device.name ?: "Controller",
                                device = device
                            )
                            onAssign(activeSlot, device)
                            activeSlot = findFirstEmptySlot(assignments)
                        }
                    }
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
        title = "Controller Order",
        subtitle = "Press any button to assign a controller",
        baseWidth = 500.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Confirm",
            InputButton.B to "Unregister/Back"
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            for (port in 0 until 4) {
                ControllerSlot(
                    port = port,
                    assignment = assignments[port],
                    isActive = port == activeSlot,
                    onClick = { activeSlot = port },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ControllerSlot(
    port: Int,
    assignment: AssignedController?,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        assignment != null -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        assignment != null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (assignment != null) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (assignment != null) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = "${port + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
            }
        }

        Text(
            text = "P${port + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )

        Text(
            text = when {
                assignment != null -> assignment.controllerName.take(12)
                isActive -> "Press..."
                else -> "Empty"
            },
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

private fun findFirstEmptySlot(assignments: Map<Int, AssignedController>): Int {
    for (i in 0 until 4) {
        if (!assignments.containsKey(i)) return i
    }
    return 4
}

private fun getControllerId(device: InputDevice): String {
    return "${device.vendorId}:${device.productId}:${device.descriptor}"
}

private fun findDeviceByControllerId(controllerId: String): InputDevice? {
    val deviceIds = InputDevice.getDeviceIds()
    for (deviceId in deviceIds) {
        val device = InputDevice.getDevice(deviceId) ?: continue
        if (getControllerId(device) == controllerId) {
            return device
        }
    }
    return null
}

private fun isGamepadDevice(device: InputDevice): Boolean {
    val sources = device.sources
    return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
        (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
}

private fun isGamepadButton(keyCode: Int): Boolean {
    return keyCode in listOf(
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_C,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_Z,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BACK
    )
}
