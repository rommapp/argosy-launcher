package com.nendo.argosy.ui.screens.settings.components

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.libretro.HotkeyManager
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HOTKEY_ACTIONS = listOf(
    HotkeyAction.IN_GAME_MENU,
    HotkeyAction.QUICK_SAVE,
    HotkeyAction.QUICK_LOAD,
    HotkeyAction.FAST_FORWARD,
    HotkeyAction.REWIND,
    HotkeyAction.QUICK_SUSPEND
)

private sealed class HotkeysState {
    data class ActionList(val focusedIndex: Int = 0) : HotkeysState()
    data class Recording(
        val action: HotkeyAction,
        val heldKeys: Set<Int> = emptySet(),
        val progress: Float = 0f,
        val isComplete: Boolean = false
    ) : HotkeysState()
}

@Composable
fun HotkeysModal(
    hotkeys: List<HotkeyEntity>,
    onSaveHotkey: suspend (HotkeyAction, List<Int>) -> Unit,
    onClearHotkey: suspend (HotkeyAction) -> Unit,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf<HotkeysState>(HotkeysState.ActionList()) }
    val scope = rememberCoroutineScope()
    val gamepadInputHandler = LocalGamepadInputHandler.current

    fun getComboForAction(action: HotkeyAction): List<Int> {
        val entity = hotkeys.find { it.action == action } ?: return emptyList()
        return parseComboJson(entity.buttonComboJson)
    }

    DisposableEffect(state, gamepadInputHandler) {
        val listener: (KeyEvent) -> Boolean = { event ->
            val device = event.device

            when (val currentState = state) {
                is HotkeysState.ActionList -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> onDismiss()
                            KeyEvent.KEYCODE_BUTTON_A -> {
                                val action = HOTKEY_ACTIONS.getOrNull(currentState.focusedIndex)
                                if (action != null) {
                                    state = HotkeysState.Recording(action = action)
                                }
                            }
                            KeyEvent.KEYCODE_BUTTON_Y -> {
                                val action = HOTKEY_ACTIONS.getOrNull(currentState.focusedIndex)
                                if (action != null) {
                                    scope.launch { onClearHotkey(action) }
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (currentState.focusedIndex > 0) {
                                    state = currentState.copy(focusedIndex = currentState.focusedIndex - 1)
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (currentState.focusedIndex < HOTKEY_ACTIONS.size - 1) {
                                    state = currentState.copy(focusedIndex = currentState.focusedIndex + 1)
                                }
                            }
                        }
                    }
                }
                is HotkeysState.Recording -> {
                    if (!currentState.isComplete) {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK) {
                                state = HotkeysState.ActionList(
                                    focusedIndex = HOTKEY_ACTIONS.indexOf(currentState.action)
                                )
                            } else if (device != null && isGamepadDevice(device) && isRecordableKey(event.keyCode)) {
                                val newKeys = currentState.heldKeys + event.keyCode
                                if (newKeys.size <= 3) {
                                    state = currentState.copy(heldKeys = newKeys)
                                }
                            }
                        } else if (event.action == KeyEvent.ACTION_UP) {
                            if (isRecordableKey(event.keyCode) && event.keyCode in currentState.heldKeys) {
                                val newKeys = currentState.heldKeys - event.keyCode
                                state = currentState.copy(heldKeys = newKeys)
                            }
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

    when (val currentState = state) {
        is HotkeysState.ActionList -> ActionListContent(
            hotkeys = hotkeys,
            focusedIndex = currentState.focusedIndex,
            getComboForAction = ::getComboForAction,
            onSelectAction = { action -> state = HotkeysState.Recording(action = action) },
            onDismiss = onDismiss
        )
        is HotkeysState.Recording -> RecordingContent(
            action = currentState.action,
            heldKeys = currentState.heldKeys,
            isComplete = currentState.isComplete,
            onComplete = { keys ->
                scope.launch {
                    onSaveHotkey(currentState.action, keys.toList())
                    delay(500)
                    state = HotkeysState.ActionList(
                        focusedIndex = HOTKEY_ACTIONS.indexOf(currentState.action)
                    )
                }
            },
            onCancel = {
                state = HotkeysState.ActionList(
                    focusedIndex = HOTKEY_ACTIONS.indexOf(currentState.action)
                )
            },
            onUpdateComplete = { complete ->
                state = currentState.copy(isComplete = complete)
            }
        )
    }
}

@Composable
private fun ActionListContent(
    hotkeys: List<HotkeyEntity>,
    focusedIndex: Int,
    getComboForAction: (HotkeyAction) -> List<Int>,
    onSelectAction: (HotkeyAction) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && focusedIndex < HOTKEY_ACTIONS.size) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    Modal(
        title = "Hotkeys",
        subtitle = "Configure button shortcuts",
        baseWidth = 450.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.SOUTH to "Record",
            InputButton.EAST to "Back",
            InputButton.NORTH to "Clear"
        )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(HOTKEY_ACTIONS) { index, action ->
                val combo = getComboForAction(action)
                HotkeyRow(
                    action = action,
                    combo = combo,
                    isFocused = index == focusedIndex,
                    onClick = { onSelectAction(action) }
                )
            }
        }
    }
}

@Composable
private fun HotkeyRow(
    action: HotkeyAction,
    combo: List<Int>,
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
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = getActionDisplayName(action),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = HotkeyManager.formatCombo(combo),
                style = MaterialTheme.typography.bodySmall,
                color = if (combo.isNotEmpty()) {
                    contentColor.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                }
            )
            if (isFocused) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun RecordingContent(
    action: HotkeyAction,
    heldKeys: Set<Int>,
    isComplete: Boolean,
    onComplete: (Set<Int>) -> Unit,
    onCancel: () -> Unit,
    onUpdateComplete: (Boolean) -> Unit
) {
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var savedKeys by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val animatedProgress by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(durationMillis = 100),
        label = "progress"
    )

    LaunchedEffect(heldKeys) {
        if (heldKeys.isNotEmpty() && !isComplete) {
            holdProgress = 0f
            savedKeys = heldKeys
            val holdDurationMs = 2000L
            val updateIntervalMs = 50L
            val steps = holdDurationMs / updateIntervalMs

            for (i in 1..steps) {
                delay(updateIntervalMs)
                if (heldKeys != savedKeys || heldKeys.isEmpty()) {
                    holdProgress = 0f
                    return@LaunchedEffect
                }
                holdProgress = i.toFloat() / steps.toFloat()
            }

            if (heldKeys == savedKeys && heldKeys.isNotEmpty()) {
                onUpdateComplete(true)
                onComplete(savedKeys)
            }
        } else if (heldKeys.isEmpty()) {
            holdProgress = 0f
            savedKeys = emptySet()
        }
    }

    Modal(
        title = "Recording Hotkey",
        subtitle = getActionDisplayName(action),
        baseWidth = 350.dp,
        onDismiss = onCancel,
        footerHints = listOf(InputButton.EAST to "Cancel")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingXl),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(80.dp),
                        color = if (isComplete) {
                            MaterialTheme.colorScheme.primary
                        } else if (heldKeys.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 6.dp
                    )
                    if (isComplete) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = if (heldKeys.isEmpty()) "..." else "${heldKeys.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Text(
                        text = if (isComplete) {
                            "Saved!"
                        } else if (heldKeys.isEmpty()) {
                            "Hold 1-3 buttons..."
                        } else {
                            "Keep holding..."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (heldKeys.isNotEmpty() && !isComplete) {
                        Text(
                            text = HotkeyManager.formatCombo(heldKeys.toList()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun getActionDisplayName(action: HotkeyAction): String {
    return when (action) {
        HotkeyAction.IN_GAME_MENU -> "In-Game Menu"
        HotkeyAction.QUICK_SAVE -> "Quick Save"
        HotkeyAction.QUICK_LOAD -> "Quick Load"
        HotkeyAction.FAST_FORWARD -> "Fast Forward"
        HotkeyAction.REWIND -> "Rewind"
        HotkeyAction.QUICK_SUSPEND -> "Quick Suspend"
    }
}

private fun parseComboJson(jsonStr: String): List<Int> {
    return try {
        val result = mutableListOf<Int>()
        val jsonArray = org.json.JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            result.add(jsonArray.getInt(i))
        }
        result
    } catch (e: Exception) {
        emptyList()
    }
}

private fun isGamepadDevice(device: InputDevice): Boolean {
    val sources = device.sources
    return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
        (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
}

private fun isRecordableKey(keyCode: Int): Boolean {
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
        KeyEvent.KEYCODE_BUTTON_THUMBR
    )
}
