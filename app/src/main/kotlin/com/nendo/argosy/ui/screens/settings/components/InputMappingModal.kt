package com.nendo.argosy.ui.screens.settings.components

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.data.repository.ControllerInfo
import com.nendo.argosy.data.repository.InputPresets
import com.nendo.argosy.data.repository.InputSource
import com.nendo.argosy.data.repository.MappingPlatforms
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.icons.InputIcons
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
    lockedPlatformIndex: Int? = null,
    onGetMapping: suspend (ControllerInfo, String?) -> Pair<Map<InputSource, Int>, String?>,
    onSaveMapping: suspend (ControllerInfo, Map<InputSource, Int>, String?, Boolean, String?) -> Unit,
    onApplyPreset: suspend (ControllerInfo, String) -> Unit,
    onDismiss: () -> Unit
) {
    val gamepadInputHandler = LocalGamepadInputHandler.current

    val autoSelectedController = remember {
        val device = gamepadInputHandler?.lastInputDevice ?: return@remember null
        controllers.find { it.deviceId == device.id }
    }
    var state by remember {
        val initial = if (autoSelectedController != null) {
            InputMappingState.PlatformMapping(
                controller = autoSelectedController,
                platformIndex = lockedPlatformIndex ?: 0,
                currentMapping = emptyMap()
            )
        } else {
            InputMappingState.ControllerList
        }
        mutableStateOf(initial)
    }
    var controllerFocusIndex by remember { mutableIntStateOf(0) }
    var cancelHoldActive by remember { mutableStateOf(false) }
    var suppressBackUntilRelease by remember { mutableStateOf(false) }
    val cancelProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    if (autoSelectedController != null) {
        LaunchedEffect(Unit) {
            val platformIndex = lockedPlatformIndex ?: 0
            val platformId = MappingPlatforms.dbPlatformId(platformIndex)
            val (mapping, _) = onGetMapping(autoSelectedController, platformId)
            val current = state as? InputMappingState.PlatformMapping ?: return@LaunchedEffect
            state = current.copy(currentMapping = mapping)
        }
    }

    DisposableEffect(state, gamepadInputHandler) {
        val keyListener: (KeyEvent) -> Boolean = { event ->
            val isBackKey = event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK
            if (suppressBackUntilRelease && isBackKey) {
                if (event.action == KeyEvent.ACTION_UP) suppressBackUntilRelease = false
                true
            } else {
            val device = event.device
            when (val currentState = state) {
                is InputMappingState.ControllerList -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (gamepadInputHandler?.mapKeyToEvent(event.keyCode)) {
                            GamepadEvent.Back -> onDismiss()
                            GamepadEvent.Confirm -> {
                                if (controllers.isNotEmpty() && controllerFocusIndex < controllers.size) {
                                    val selected = controllers[controllerFocusIndex]
                                    val platformIndex = lockedPlatformIndex ?: 0
                                    val platformId = MappingPlatforms.dbPlatformId(platformIndex)
                                    scope.launch {
                                        val (mapping, _) = onGetMapping(selected, platformId)
                                        state = InputMappingState.PlatformMapping(
                                            controller = selected,
                                            platformIndex = platformIndex,
                                            currentMapping = mapping
                                        )
                                    }
                                }
                            }
                            GamepadEvent.Up -> if (controllerFocusIndex > 0) controllerFocusIndex--
                            GamepadEvent.Down -> if (controllerFocusIndex < controllers.size - 1) controllerFocusIndex++
                            else -> {}
                        }
                    }
                }
                is InputMappingState.PlatformMapping -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val platform = MappingPlatforms.getByIndex(currentState.platformIndex)
                        val platformId = MappingPlatforms.dbPlatformId(currentState.platformIndex)
                        when (gamepadInputHandler?.mapKeyToEvent(event.keyCode)) {
                            GamepadEvent.Back -> {
                                if (autoSelectedController != null) {
                                    onDismiss()
                                } else {
                                    state = InputMappingState.ControllerList
                                    controllerFocusIndex = controllers.indexOfFirst {
                                        it.controllerId == currentState.controller.controllerId
                                    }.coerceAtLeast(0)
                                }
                            }
                            GamepadEvent.Confirm -> {
                                if (currentState.focusedButtonIndex < platform.buttons.size) {
                                    state = InputMappingState.Recording(
                                        controller = currentState.controller,
                                        platformIndex = currentState.platformIndex,
                                        targetRetroButton = platform.buttons[currentState.focusedButtonIndex],
                                        currentMapping = currentState.currentMapping,
                                        replaceMode = true
                                    )
                                }
                            }
                            GamepadEvent.ContextMenu -> {
                                if (currentState.focusedButtonIndex < platform.buttons.size) {
                                    state = InputMappingState.Recording(
                                        controller = currentState.controller,
                                        platformIndex = currentState.platformIndex,
                                        targetRetroButton = platform.buttons[currentState.focusedButtonIndex],
                                        currentMapping = currentState.currentMapping,
                                        replaceMode = false
                                    )
                                }
                            }
                            GamepadEvent.SecondaryAction -> {
                                if (currentState.focusedButtonIndex < platform.buttons.size) {
                                    val targetButton = platform.buttons[currentState.focusedButtonIndex]
                                    val newMapping = currentState.currentMapping.filterValues { it != targetButton }
                                    scope.launch {
                                        onSaveMapping(currentState.controller, newMapping, null, false, platformId)
                                        state = currentState.copy(currentMapping = newMapping)
                                    }
                                }
                            }
                            GamepadEvent.PrevSection -> {
                                if (lockedPlatformIndex == null) {
                                    val prevIndex = MappingPlatforms.getPrevIndex(currentState.platformIndex)
                                    val prevPlatformId = MappingPlatforms.dbPlatformId(prevIndex)
                                    scope.launch {
                                        val (mapping, _) = onGetMapping(currentState.controller, prevPlatformId)
                                        state = currentState.copy(
                                            platformIndex = prevIndex,
                                            focusedButtonIndex = 0,
                                            currentMapping = mapping
                                        )
                                    }
                                }
                            }
                            GamepadEvent.NextSection -> {
                                if (lockedPlatformIndex == null) {
                                    val nextIndex = MappingPlatforms.getNextIndex(currentState.platformIndex)
                                    val nextPlatformId = MappingPlatforms.dbPlatformId(nextIndex)
                                    scope.launch {
                                        val (mapping, _) = onGetMapping(currentState.controller, nextPlatformId)
                                        state = currentState.copy(
                                            platformIndex = nextIndex,
                                            focusedButtonIndex = 0,
                                            currentMapping = mapping
                                        )
                                    }
                                }
                            }
                            GamepadEvent.Up -> {
                                if (currentState.focusedButtonIndex > 0) {
                                    state = currentState.copy(focusedButtonIndex = currentState.focusedButtonIndex - 1)
                                }
                            }
                            GamepadEvent.Down -> {
                                if (currentState.focusedButtonIndex < platform.buttons.size - 1) {
                                    state = currentState.copy(focusedButtonIndex = currentState.focusedButtonIndex + 1)
                                }
                            }
                            else -> {}
                        }
                    }
                }
                is InputMappingState.Recording -> {
                    val isBackButton = event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK
                    val recordingPlatformId = MappingPlatforms.dbPlatformId(currentState.platformIndex)
                    if (isBackButton) {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            cancelHoldActive = true
                        } else if (event.action == KeyEvent.ACTION_UP && cancelHoldActive) {
                            cancelHoldActive = false
                            if (device != null && isGamepadDevice(device)) {
                                val inputSource = InputSource.Button(event.keyCode)
                                val newMapping = currentState.currentMapping.toMutableMap()
                                if (currentState.replaceMode) {
                                    newMapping.entries.removeIf { it.value == currentState.targetRetroButton }
                                }
                                newMapping[inputSource] = currentState.targetRetroButton
                                scope.launch {
                                    onSaveMapping(currentState.controller, newMapping, null, false, recordingPlatformId)
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
                    } else if (event.action == KeyEvent.ACTION_DOWN && device != null && isGamepadDevice(device) && isMappableButton(event.keyCode)) {
                        val inputSource = InputSource.Button(event.keyCode)
                        val newMapping = currentState.currentMapping.toMutableMap()
                        if (currentState.replaceMode) {
                            newMapping.entries.removeIf { it.value == currentState.targetRetroButton }
                        }
                        newMapping[inputSource] = currentState.targetRetroButton
                        scope.launch {
                            onSaveMapping(currentState.controller, newMapping, null, false, recordingPlatformId)
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
            true
            }
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
                            val motionPlatformId = MappingPlatforms.dbPlatformId(currentState.platformIndex)

                            scope.launch {
                                onSaveMapping(currentState.controller, newMapping, null, false, motionPlatformId)
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

    LaunchedEffect(cancelHoldActive, state) {
        if (cancelHoldActive && state is InputMappingState.Recording) {
            cancelProgress.snapTo(0f)
            cancelProgress.animateTo(1f, tween(1000, easing = LinearEasing))
            val rec = state as? InputMappingState.Recording ?: return@LaunchedEffect
            cancelHoldActive = false
            suppressBackUntilRelease = true
            val platform = MappingPlatforms.getByIndex(rec.platformIndex)
            val buttonIndex = platform.buttons.indexOf(rec.targetRetroButton)
            state = InputMappingState.PlatformMapping(
                controller = rec.controller,
                platformIndex = rec.platformIndex,
                currentMapping = rec.currentMapping,
                focusedButtonIndex = buttonIndex.coerceAtLeast(0)
            )
        } else {
            cancelProgress.snapTo(0f)
        }
    }

    when (val currentState = state) {
        is InputMappingState.ControllerList -> ControllerListContent(
            controllers = controllers,
            focusedIndex = controllerFocusIndex,
            onSelectController = { index ->
                if (index < controllers.size) {
                    val selected = controllers[index]
                    val platformIndex = lockedPlatformIndex ?: 0
                    val platformId = MappingPlatforms.dbPlatformId(platformIndex)
                    scope.launch {
                        val (mapping, _) = onGetMapping(selected, platformId)
                        state = InputMappingState.PlatformMapping(
                            controller = selected,
                            platformIndex = platformIndex,
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
            platformLocked = lockedPlatformIndex != null,
            onSelectButton = { retroButton ->
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
            cancelProgress = cancelProgress.value,
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
    platformLocked: Boolean = false,
    onSelectButton: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val platform = MappingPlatforms.getByIndex(platformIndex)

    FocusedScroll(listState, focusedIndex)

    val footerHints = listOf(
        InputButton.A to "Remap",
        InputButton.X to "Add",
        InputButton.Y to "Clear",
        InputButton.B to "Back"
    )

    Modal(
        title = controller.name,
        fillHeight = true,
        titleContent = {
            Text(
                text = controller.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                if (!platformLocked) {
                    Icon(
                        painter = InputIcons.BumperLeft,
                        contentDescription = "Previous platform",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                }
                Text(
                    text = "Platform: ${platform.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!platformLocked) {
                    Icon(
                        painter = InputIcons.BumperRight,
                        contentDescription = "Next platform",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                }
            }
        },
        baseWidth = 450.dp,
        onDismiss = onDismiss,
        footerHints = footerHints
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
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
            Text(
                text = "Not bound",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.weight(0.65f),
                textAlign = TextAlign.End
            )
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
    cancelProgress: Float,
    onDismiss: () -> Unit
) {
    val isCancelling = cancelProgress > 0f
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
                    tint = if (isCancelling) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isCancelling) "Cancelling..." else "Waiting for input...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isCancelling) {
                    LinearProgressIndicator(
                        progress = { cancelProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
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
