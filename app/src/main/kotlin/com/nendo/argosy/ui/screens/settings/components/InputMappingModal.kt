package com.nendo.argosy.ui.screens.settings.components

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.data.repository.ControllerInfo
import com.nendo.argosy.data.repository.InputPresets
import com.nendo.argosy.data.repository.InputSource
import com.nendo.argosy.data.repository.MappingPlatforms
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.launch

private sealed class InputMappingState {
    data object ControllerList : InputMappingState()
    data class PlatformMapping(
        val controller: ControllerInfo,
        val platformIndex: Int,
        val currentMapping: Map<InputSource, Int>,
        val focusedButtonIndex: Int = 0
    ) : InputMappingState()
    data class Recording(
        val controller: ControllerInfo,
        val platformIndex: Int,
        val targetRetroButton: Int,
        val currentMapping: Map<InputSource, Int>,
        val replaceMode: Boolean = true
    ) : InputMappingState()
}

@Composable
fun InputMappingModal(
    controllers: List<ControllerInfo>,
    onGetMapping: suspend (ControllerInfo) -> Pair<Map<InputSource, Int>, String?>,
    onSaveMapping: suspend (ControllerInfo, Map<InputSource, Int>, String?, Boolean) -> Unit,
    onApplyPreset: suspend (ControllerInfo, String) -> Unit,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf<InputMappingState>(InputMappingState.ControllerList) }
    var controllerFocusIndex by remember { mutableIntStateOf(0) }
    var backPressedAt by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    val gamepadInputHandler = LocalGamepadInputHandler.current

    DisposableEffect(state, gamepadInputHandler) {
        val keyListener: (KeyEvent) -> Boolean = { event ->
            val device = event.device
            when (val currentState = state) {
                is InputMappingState.ControllerList -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> onDismiss()
                            KeyEvent.KEYCODE_BUTTON_A -> {
                                if (controllers.isNotEmpty() && controllerFocusIndex < controllers.size) {
                                    val selected = controllers[controllerFocusIndex]
                                    scope.launch {
                                        val (mapping, _) = onGetMapping(selected)
                                        state = InputMappingState.PlatformMapping(
                                            controller = selected,
                                            platformIndex = 0,
                                            currentMapping = mapping
                                        )
                                    }
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> if (controllerFocusIndex > 0) controllerFocusIndex--
                            KeyEvent.KEYCODE_DPAD_DOWN -> if (controllerFocusIndex < controllers.size - 1) controllerFocusIndex++
                        }
                    }
                }
                is InputMappingState.PlatformMapping -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val platform = MappingPlatforms.getByIndex(currentState.platformIndex)
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                                state = InputMappingState.ControllerList
                                controllerFocusIndex = controllers.indexOfFirst {
                                    it.controllerId == currentState.controller.controllerId
                                }.coerceAtLeast(0)
                            }
                            KeyEvent.KEYCODE_BUTTON_A -> {
                                if (currentState.focusedButtonIndex < platform.buttons.size) {
                                    backPressedAt = 0L
                                    state = InputMappingState.Recording(
                                        controller = currentState.controller,
                                        platformIndex = currentState.platformIndex,
                                        targetRetroButton = platform.buttons[currentState.focusedButtonIndex],
                                        currentMapping = currentState.currentMapping,
                                        replaceMode = true
                                    )
                                }
                            }
                            KeyEvent.KEYCODE_BUTTON_X -> {
                                if (currentState.focusedButtonIndex < platform.buttons.size) {
                                    backPressedAt = 0L
                                    state = InputMappingState.Recording(
                                        controller = currentState.controller,
                                        platformIndex = currentState.platformIndex,
                                        targetRetroButton = platform.buttons[currentState.focusedButtonIndex],
                                        currentMapping = currentState.currentMapping,
                                        replaceMode = false
                                    )
                                }
                            }
                            KeyEvent.KEYCODE_BUTTON_Y -> {
                                if (currentState.focusedButtonIndex < platform.buttons.size) {
                                    val targetButton = platform.buttons[currentState.focusedButtonIndex]
                                    val newMapping = currentState.currentMapping.filterValues { it != targetButton }
                                    scope.launch {
                                        onSaveMapping(currentState.controller, newMapping, null, false)
                                        state = currentState.copy(currentMapping = newMapping)
                                    }
                                }
                            }
                            KeyEvent.KEYCODE_BUTTON_L1 -> {
                                val prevIndex = MappingPlatforms.getPrevIndex(currentState.platformIndex)
                                state = currentState.copy(
                                    platformIndex = prevIndex,
                                    focusedButtonIndex = 0
                                )
                            }
                            KeyEvent.KEYCODE_BUTTON_R1 -> {
                                val nextIndex = MappingPlatforms.getNextIndex(currentState.platformIndex)
                                state = currentState.copy(
                                    platformIndex = nextIndex,
                                    focusedButtonIndex = 0
                                )
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (currentState.focusedButtonIndex > 0) {
                                    state = currentState.copy(focusedButtonIndex = currentState.focusedButtonIndex - 1)
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (currentState.focusedButtonIndex < platform.buttons.size - 1) {
                                    state = currentState.copy(focusedButtonIndex = currentState.focusedButtonIndex + 1)
                                }
                            }
                        }
                    }
                }
                is InputMappingState.Recording -> {
                    val isBackButton = event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (isBackButton) {
                            backPressedAt = System.currentTimeMillis()
                        } else if (device != null && isGamepadDevice(device) && isMappableButton(event.keyCode)) {
                            val inputSource = InputSource.Button(event.keyCode)
                            val newMapping = currentState.currentMapping.toMutableMap()
                            if (currentState.replaceMode) {
                                newMapping.entries.removeIf { it.value == currentState.targetRetroButton }
                            }
                            newMapping[inputSource] = currentState.targetRetroButton

                            scope.launch {
                                onSaveMapping(currentState.controller, newMapping, null, false)
                                val platform = MappingPlatforms.getByIndex(currentState.platformIndex)
                                val buttonIndex = platform.buttons.indexOf(currentState.targetRetroButton)
                                state = InputMappingState.PlatformMapping(
                                    controller = currentState.controller,
                                    platformIndex = currentState.platformIndex,
                                    currentMapping = newMapping,
                                    focusedButtonIndex = buttonIndex.coerceAtLeast(0)
                                )
                            }
                        }
                    } else if (event.action == KeyEvent.ACTION_UP && isBackButton && backPressedAt > 0L) {
                        val holdDuration = System.currentTimeMillis() - backPressedAt
                        backPressedAt = 0L
                        if (holdDuration >= 500) {
                            val platform = MappingPlatforms.getByIndex(currentState.platformIndex)
                            val buttonIndex = platform.buttons.indexOf(currentState.targetRetroButton)
                            state = InputMappingState.PlatformMapping(
                                controller = currentState.controller,
                                platformIndex = currentState.platformIndex,
                                currentMapping = currentState.currentMapping,
                                focusedButtonIndex = buttonIndex.coerceAtLeast(0)
                            )
                        } else if (device != null && isGamepadDevice(device)) {
                            val inputSource = InputSource.Button(event.keyCode)
                            val newMapping = currentState.currentMapping.toMutableMap()
                            if (currentState.replaceMode) {
                                newMapping.entries.removeIf { it.value == currentState.targetRetroButton }
                            }
                            newMapping[inputSource] = currentState.targetRetroButton

                            scope.launch {
                                onSaveMapping(currentState.controller, newMapping, null, false)
                                val platform = MappingPlatforms.getByIndex(currentState.platformIndex)
                                val buttonIndex = platform.buttons.indexOf(currentState.targetRetroButton)
                                state = InputMappingState.PlatformMapping(
                                    controller = currentState.controller,
                                    platformIndex = currentState.platformIndex,
                                    currentMapping = newMapping,
                                    focusedButtonIndex = buttonIndex.coerceAtLeast(0)
                                )
                            }
                        }
                    }
                }
            }
            true
        }

        val motionListener: (MotionEvent) -> Boolean = { event ->
            when (val currentState = state) {
                is InputMappingState.Recording -> {
                    val device = event.device
                    if (device != null && isGamepadDevice(device)) {
                        val analogInput = detectAnalogInput(event)
                        if (analogInput != null) {
                            val newMapping = currentState.currentMapping.toMutableMap()
                            if (currentState.replaceMode) {
                                newMapping.entries.removeIf { it.value == currentState.targetRetroButton }
                            }
                            newMapping[analogInput] = currentState.targetRetroButton

                            scope.launch {
                                onSaveMapping(currentState.controller, newMapping, null, false)
                                val platform = MappingPlatforms.getByIndex(currentState.platformIndex)
                                val buttonIndex = platform.buttons.indexOf(currentState.targetRetroButton)
                                state = InputMappingState.PlatformMapping(
                                    controller = currentState.controller,
                                    platformIndex = currentState.platformIndex,
                                    currentMapping = newMapping,
                                    focusedButtonIndex = buttonIndex.coerceAtLeast(0)
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
            false
        }

        gamepadInputHandler?.setRawKeyEventListener(keyListener)
        gamepadInputHandler?.setRawMotionEventListener(motionListener)

        onDispose {
            gamepadInputHandler?.setRawKeyEventListener(null)
            gamepadInputHandler?.setRawMotionEventListener(null)
        }
    }

    when (val currentState = state) {
        is InputMappingState.ControllerList -> ControllerListContent(
            controllers = controllers,
            focusedIndex = controllerFocusIndex,
            onSelectController = { index ->
                if (index < controllers.size) {
                    val selected = controllers[index]
                    scope.launch {
                        val (mapping, _) = onGetMapping(selected)
                        state = InputMappingState.PlatformMapping(
                            controller = selected,
                            platformIndex = 0,
                            currentMapping = mapping
                        )
                    }
                }
            },
            onDismiss = onDismiss
        )
        is InputMappingState.PlatformMapping -> PlatformMappingContent(
            controller = currentState.controller,
            platformIndex = currentState.platformIndex,
            mapping = currentState.currentMapping,
            focusedIndex = currentState.focusedButtonIndex,
            onSelectButton = { retroButton ->
                backPressedAt = 0L
                state = InputMappingState.Recording(
                    controller = currentState.controller,
                    platformIndex = currentState.platformIndex,
                    targetRetroButton = retroButton,
                    currentMapping = currentState.currentMapping
                )
            },
            onDismiss = onDismiss
        )
        is InputMappingState.Recording -> RecordingOverlay(
            targetButton = InputPresets.getRetroButtonName(currentState.targetRetroButton),
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ControllerListContent(
    controllers: List<ControllerInfo>,
    focusedIndex: Int,
    onSelectController: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "Input Mapping",
        subtitle = "Select a controller to configure",
        baseWidth = 450.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Select",
            InputButton.B to "Back"
        )
    ) {
        if (controllers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spacingLg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "No controllers connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                controllers.forEachIndexed { index, controller ->
                    ControllerRow(
                        controller = controller,
                        isFocused = index == focusedIndex,
                        onClick = { onSelectController(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControllerRow(
    controller: ControllerInfo,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Gamepad,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = controller.name,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            Text(
                text = controller.detectedLayout?.name ?: "Unknown layout",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun PlatformMappingContent(
    controller: ControllerInfo,
    platformIndex: Int,
    mapping: Map<InputSource, Int>,
    focusedIndex: Int,
    onSelectButton: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val platform = MappingPlatforms.getByIndex(platformIndex)

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && focusedIndex < platform.buttons.size) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    Modal(
        title = controller.name,
        subtitle = "Platform: ${platform.displayName}",
        baseWidth = 450.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Remap",
            InputButton.X to "Add",
            InputButton.Y to "Clear",
            InputButton.B to "Back",
            InputButton.LB to "Prev",
            InputButton.RB to "Next"
        )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(platform.buttons) { index, retroButton ->
                val boundSources = mapping.entries
                    .filter { it.value == retroButton }
                    .map { it.key }
                ButtonMappingRow(
                    retroButtonName = InputPresets.getRetroButtonName(retroButton),
                    boundSources = boundSources,
                    isFocused = index == focusedIndex,
                    onClick = { onSelectButton(retroButton) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonMappingRow(
    retroButtonName: String,
    boundSources: List<InputSource>,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(Dimens.radiusSm))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = retroButtonName,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(0.35f)
        )
        if (boundSources.isEmpty()) {
            Row(
                modifier = Modifier.weight(0.65f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Not bound",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
                if (isFocused) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(start = Dimens.spacingXs).size(16.dp),
                        tint = contentColor.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            FlowRow(
                modifier = Modifier.weight(0.65f),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                boundSources.forEach { source ->
                    InputSourceChip(
                        source = source,
                        contentColor = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun InputSourceChip(
    source: InputSource,
    contentColor: Color
) {
    val displayName = InputSource.getInputSourceDisplayName(source)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(contentColor.copy(alpha = 0.1f))
            .padding(horizontal = Dimens.spacingSm, vertical = 2.dp)
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun RecordingOverlay(
    targetButton: String,
    onDismiss: () -> Unit
) {
    Modal(
        title = "Press a button",
        subtitle = "Recording input for $targetButton",
        baseWidth = 350.dp,
        onDismiss = onDismiss,
        footerHints = listOf(InputButton.B to "Hold to Cancel")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingXl),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Waiting for input...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun isGamepadDevice(device: InputDevice): Boolean {
    val sources = device.sources
    return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
        (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
}

private fun isMappableButton(keyCode: Int): Boolean {
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
        KeyEvent.KEYCODE_DPAD_RIGHT
    )
}

private val MAPPABLE_AXES = listOf(
    MotionEvent.AXIS_X,
    MotionEvent.AXIS_Y,
    MotionEvent.AXIS_Z,
    MotionEvent.AXIS_RZ,
    MotionEvent.AXIS_HAT_X,
    MotionEvent.AXIS_HAT_Y,
    MotionEvent.AXIS_LTRIGGER,
    MotionEvent.AXIS_RTRIGGER
)

private fun detectAnalogInput(event: MotionEvent): InputSource.AnalogDirection? {
    for (axis in MAPPABLE_AXES) {
        val value = event.getAxisValue(axis)
        if (value > InputSource.ANALOG_THRESHOLD) {
            return InputSource.AnalogDirection(axis, positive = true)
        } else if (value < -InputSource.ANALOG_THRESHOLD) {
            return InputSource.AnalogDirection(axis, positive = false)
        }
    }
    return null
}
