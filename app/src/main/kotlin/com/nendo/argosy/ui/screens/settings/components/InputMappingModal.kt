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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
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
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INHERITED_EMPHASIS = 0.45f
private const val CANCEL_HOLD_MS = 1000L
private const val MIN_PRESS_MS = 75L

/**
 * [inherited] is what this scope would resolve to with no bindings of its own, so the editor can
 * render an inherited binding differently from one set at the scope being edited.
 */
data class ScopedMapping(
    val mapping: Map<InputSource, Int> = emptyMap(),
    val inherited: Map<InputSource, Int> = emptyMap()
)

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
    onGetMapping: suspend (ControllerInfo, String?) -> ScopedMapping,
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
    var inheritedMapping by remember { mutableStateOf<Map<InputSource, Int>>(emptyMap()) }
    var controllerFocusIndex by remember { mutableIntStateOf(0) }
    var cancelHoldActive by remember { mutableStateOf(false) }
    var suppressBackUntilRelease by remember { mutableStateOf(false) }
    val cancelProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val leaveRecording: (InputMappingState.Recording, Map<InputSource, Int>) -> Unit = { rec, mapping ->
        val platform = MappingPlatforms.getByIndex(rec.platformIndex)
        state = InputMappingState.PlatformMapping(
            controller = rec.controller,
            platformIndex = rec.platformIndex,
            currentMapping = mapping,
            focusedButtonIndex = platform.buttons.indexOf(rec.targetRetroButton).coerceAtLeast(0)
        )
    }

    val recordMapping: (InputMappingState.Recording, InputSource) -> Unit = { rec, source ->
        val newMapping = rec.currentMapping.toMutableMap()
        if (rec.replaceMode) {
            newMapping.entries.removeIf { it.value == rec.targetRetroButton }
        }
        newMapping[source] = rec.targetRetroButton
        scope.launch {
            onSaveMapping(
                rec.controller,
                newMapping,
                null,
                false,
                MappingPlatforms.dbPlatformId(rec.platformIndex)
            )
            leaveRecording(rec, newMapping)
        }
    }

    if (autoSelectedController != null) {
        LaunchedEffect(Unit) {
            val platformIndex = lockedPlatformIndex ?: 0
            val platformId = MappingPlatforms.dbPlatformId(platformIndex)
            val scoped = onGetMapping(autoSelectedController, platformId)
            inheritedMapping = scoped.inherited
            val current = state as? InputMappingState.PlatformMapping ?: return@LaunchedEffect
            state = current.copy(currentMapping = scoped.mapping)
        }
    }

    DisposableEffect(state, gamepadInputHandler) {
        val keyListener: (KeyEvent) -> Boolean = { event ->
            val isBackKey = event.keyCode == KeyEvent.KEYCODE_BACK ||
                gamepadInputHandler?.mapKeyToEvent(event.keyCode) == GamepadEvent.Back
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
                                        val scoped = onGetMapping(selected, platformId)
                                        inheritedMapping = scoped.inherited
                                        state = InputMappingState.PlatformMapping(
                                            controller = selected,
                                            platformIndex = platformIndex,
                                            currentMapping = scoped.mapping
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
                                        val scoped = onGetMapping(currentState.controller, prevPlatformId)
                                        inheritedMapping = scoped.inherited
                                        state = currentState.copy(
                                            platformIndex = prevIndex,
                                            focusedButtonIndex = 0,
                                            currentMapping = scoped.mapping
                                        )
                                    }
                                }
                            }
                            GamepadEvent.NextSection -> {
                                if (lockedPlatformIndex == null) {
                                    val nextIndex = MappingPlatforms.getNextIndex(currentState.platformIndex)
                                    val nextPlatformId = MappingPlatforms.dbPlatformId(nextIndex)
                                    scope.launch {
                                        val scoped = onGetMapping(currentState.controller, nextPlatformId)
                                        inheritedMapping = scoped.inherited
                                        state = currentState.copy(
                                            platformIndex = nextIndex,
                                            focusedButtonIndex = 0,
                                            currentMapping = scoped.mapping
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
                    val isGamepad = device != null && isGamepadDevice(device)
                    val heldLongEnough = event.eventTime - event.downTime >= MIN_PRESS_MS
                    when {
                        event.keyCode == KeyEvent.KEYCODE_BACK -> {
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                cancelHoldActive = false
                                suppressBackUntilRelease = true
                                leaveRecording(currentState, currentState.currentMapping)
                            }
                        }
                        isBackKey -> {
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                cancelHoldActive = true
                            } else if (event.action == KeyEvent.ACTION_UP && cancelHoldActive) {
                                cancelHoldActive = false
                                if (isGamepad && heldLongEnough && isMappableButton(event.keyCode)) {
                                    recordMapping(currentState, InputSource.Button(event.keyCode))
                                }
                            }
                        }
                        event.action == KeyEvent.ACTION_DOWN && isGamepad && isMappableButton(event.keyCode) -> {
                            recordMapping(currentState, InputSource.Button(event.keyCode))
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
                            recordMapping(currentState, analogInput)
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
            delay(MIN_PRESS_MS)
            cancelProgress.animateTo(
                1f,
                tween((CANCEL_HOLD_MS - MIN_PRESS_MS).toInt(), easing = LinearEasing)
            )
            val rec = state as? InputMappingState.Recording ?: return@LaunchedEffect
            cancelHoldActive = false
            suppressBackUntilRelease = true
            leaveRecording(rec, rec.currentMapping)
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
                        val scoped = onGetMapping(selected, platformId)
                        inheritedMapping = scoped.inherited
                        state = InputMappingState.PlatformMapping(
                            controller = selected,
                            platformIndex = platformIndex,
                            currentMapping = scoped.mapping
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
            inheritedMapping = inheritedMapping,
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
            targetButton = InputPresets.getRetroButtonName(
                currentState.targetRetroButton,
                MappingPlatforms.getByIndex(currentState.platformIndex)
            ),
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
        title = stringResource(R.string.settings_input_mapping_title),
        subtitle = stringResource(R.string.settings_input_mapping_subtitle),
        baseWidth = 450.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to stringResource(R.string.settings_input_mapping_controller_select_hint),
            InputButton.B to stringResource(R.string.settings_input_mapping_controller_back_hint)
        ),
        onFooterHintClick = { button -> if (button == InputButton.B) onDismiss() }
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
                        text = stringResource(R.string.settings_input_mapping_empty),
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
    val theme = LocalArgosyTheme.current
    val backgroundColor = if (isFocused) {
        theme.focusAccent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }
    val borderColor = if (isFocused) {
        theme.focusAccent
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (isFocused) {
        lerp(theme.focusAccent, Color.White, 0.45f)
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
                text = controller.detectedLayout?.name ?: stringResource(R.string.settings_input_mapping_unknown_layout),
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
    inheritedMapping: Map<InputSource, Int>,
    focusedIndex: Int,
    platformLocked: Boolean = false,
    onSelectButton: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val platform = MappingPlatforms.getByIndex(platformIndex)

    FocusedScroll(listState, focusedIndex)

    val footerHints = listOf(
        InputButton.A to stringResource(R.string.settings_input_mapping_platform_remap_hint),
        InputButton.X to stringResource(R.string.settings_input_mapping_platform_add_hint),
        InputButton.Y to stringResource(R.string.settings_input_mapping_platform_clear_hint),
        InputButton.B to stringResource(R.string.settings_input_mapping_platform_back_hint)
    )

    Modal(
        title = controller.name,
        fillHeight = true,
        inlineFooterHints = true,
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
                        contentDescription = stringResource(R.string.settings_input_mapping_prev_platform_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                }
                Text(
                    text = stringResource(R.string.settings_input_mapping_platform_label, platform.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!platformLocked) {
                    Icon(
                        painter = InputIcons.BumperRight,
                        contentDescription = stringResource(R.string.settings_input_mapping_next_platform_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                }
            }
        },
        baseWidth = 450.dp,
        onDismiss = onDismiss,
        footerHints = footerHints,
        onFooterHintClick = { button -> if (button == InputButton.B) onDismiss() }
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
                val inheritedSources = inheritedMapping.entries
                    .filter { it.value == retroButton }
                    .map { it.key }
                ButtonMappingRow(
                    retroButtonName = InputPresets.getRetroButtonName(retroButton, platform),
                    boundSources = boundSources,
                    isOverridden = boundSources.toSet() != inheritedSources.toSet(),
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
    isOverridden: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val emphasis = if (isOverridden) 1f else INHERITED_EMPHASIS
    val backgroundColor = if (isFocused) {
        theme.focusAccent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }
    val borderColor = if (isFocused) {
        theme.focusAccent
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isFocused) {
        lerp(theme.focusAccent, Color.White, 0.45f)
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
            color = contentColor.copy(alpha = emphasis),
            modifier = Modifier.weight(0.35f)
        )
        if (boundSources.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_input_mapping_not_bound),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f * emphasis),
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
                        contentColor = contentColor,
                        emphasis = emphasis
                    )
                }
            }
        }
    }
}

@Composable
private fun InputSourceChip(
    source: InputSource,
    contentColor: Color,
    emphasis: Float
) {
    val displayName = InputSource.getInputSourceDisplayName(source)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(contentColor.copy(alpha = 0.1f * emphasis))
            .padding(horizontal = Dimens.spacingSm, vertical = 2.dp)
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.8f * emphasis)
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
        title = stringResource(R.string.settings_input_mapping_recording_title),
        subtitle = stringResource(R.string.settings_input_mapping_recording_subtitle, targetButton),
        baseWidth = 350.dp,
        onDismiss = onDismiss,
        footerHints = listOf(InputButton.B to stringResource(R.string.settings_input_mapping_recording_back_hint)),
        onFooterHintClick = { button -> if (button == InputButton.B) onDismiss() }
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
                    text = if (isCancelling) {
                        stringResource(R.string.settings_input_mapping_recording_cancelling)
                    } else {
                        stringResource(R.string.settings_input_mapping_recording_waiting)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isCancelling) {
                    ArgosyProgressBar(
                        progress = cancelProgress,
                        modifier = Modifier.fillMaxWidth(0.6f),
                        tint = MaterialTheme.colorScheme.error
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

private fun isMappableButton(keyCode: Int): Boolean = keyCode in InputPresets.BINDABLE_KEYCODES

private val MAPPABLE_AXES = listOf(
    MotionEvent.AXIS_X,
    MotionEvent.AXIS_Y,
    MotionEvent.AXIS_Z,
    MotionEvent.AXIS_RZ,
    MotionEvent.AXIS_HAT_X,
    MotionEvent.AXIS_HAT_Y,
    MotionEvent.AXIS_LTRIGGER,
    MotionEvent.AXIS_RTRIGGER,
    MotionEvent.AXIS_BRAKE,
    MotionEvent.AXIS_GAS
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
