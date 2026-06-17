package com.nendo.argosy.ui.screens.settings.components

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.nendo.argosy.data.local.entity.CoreInputMode
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.local.entity.HotkeyScopeType
import com.nendo.argosy.libretro.HotkeyManager
import com.nendo.argosy.libretro.coreoptions.CoreControlDef
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HOTKEY_ACTIONS = listOf(
    HotkeyAction.IN_GAME_MENU,
    HotkeyAction.RESET_GAME,
    HotkeyAction.QUICK_SAVE,
    HotkeyAction.QUICK_LOAD,
    HotkeyAction.FAST_FORWARD,
    HotkeyAction.REWIND,
    HotkeyAction.QUICK_SUSPEND
)

private val HOLD_DELAY_CYCLE = listOf(0L, 1000L, 2000L, 3000L)

private sealed interface RecordTarget {
    val label: String

    data class System(val action: HotkeyAction) : RecordTarget {
        override val label get() = getActionDisplayName(action)
    }

    data class Core(val def: CoreControlDef) : RecordTarget {
        override val label get() = def.label
    }
}

private sealed interface MenuRow {
    val focusable: Boolean

    data class Header(val title: String, val dimmed: Boolean) : MenuRow {
        override val focusable get() = false
    }

    data class Placeholder(val text: String) : MenuRow {
        override val focusable get() = false
    }

    data class System(val action: HotkeyAction, val combo: List<Int>, val holdMs: Long, val conflicting: Boolean) : MenuRow {
        override val focusable get() = true
    }

    data class Core(val def: CoreControlDef, val boundEntity: HotkeyEntity?) : MenuRow {
        override val focusable get() = true
    }
}

private sealed class HotkeysState {
    data class ActionList(val focusedIndex: Int = 0) : HotkeysState()
    data class Recording(
        val target: RecordTarget,
        val returnFocusIndex: Int,
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
    onSetHoldMs: suspend (HotkeyAction, Long) -> Unit,
    onDismiss: () -> Unit,
    coreId: String? = null,
    coreName: String? = null,
    coreControls: List<CoreControlDef> = emptyList(),
    onSaveCoreControl: suspend (Int, CoreInputMode, List<Int>) -> Unit = { _, _, _ -> },
    onClearCoreBind: suspend (Long) -> Unit = {}
) {
    var state by remember { mutableStateOf<HotkeysState>(HotkeysState.ActionList()) }
    val scope = rememberCoroutineScope()
    val gamepadInputHandler = LocalGamepadInputHandler.current

    val conflictingActions = remember(hotkeys) { findConflictingActions(hotkeys) }

    val rows = remember(hotkeys, coreControls, coreId, coreName, conflictingActions) {
        buildRows(hotkeys, coreControls, coreId, coreName, conflictingActions)
    }
    val focusableRows = remember(rows) { rows.filter { it.focusable } }

    fun cycleHoldDelay(action: HotkeyAction) {
        val current = hotkeys.find { it.action == action }?.holdMs ?: 0L
        val currentIdx = HOLD_DELAY_CYCLE.indexOf(current).coerceAtLeast(0)
        val nextHoldMs = HOLD_DELAY_CYCLE[(currentIdx + 1) % HOLD_DELAY_CYCLE.size]
        scope.launch { onSetHoldMs(action, nextHoldMs) }
    }

    fun startRecording(focusableIndex: Int) {
        val target = when (val row = focusableRows.getOrNull(focusableIndex)) {
            is MenuRow.System -> RecordTarget.System(row.action)
            is MenuRow.Core -> RecordTarget.Core(row.def)
            else -> return
        }
        state = HotkeysState.Recording(target = target, returnFocusIndex = focusableIndex)
    }

    fun clearBind(focusableIndex: Int) {
        when (val row = focusableRows.getOrNull(focusableIndex)) {
            is MenuRow.System -> scope.launch { onClearHotkey(row.action) }
            is MenuRow.Core -> row.boundEntity?.let { entity -> scope.launch { onClearCoreBind(entity.id) } }
            else -> {}
        }
    }

    fun cycleHoldDelayAt(focusableIndex: Int) {
        (focusableRows.getOrNull(focusableIndex) as? MenuRow.System)?.let { cycleHoldDelay(it.action) }
    }

    DisposableEffect(state, gamepadInputHandler, focusableRows) {
        val listener: (KeyEvent) -> Boolean = { event ->
            val device = event.device

            when (val currentState = state) {
                is HotkeysState.ActionList -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> onDismiss()
                            KeyEvent.KEYCODE_BUTTON_A -> startRecording(currentState.focusedIndex)
                            KeyEvent.KEYCODE_BUTTON_Y -> clearBind(currentState.focusedIndex)
                            KeyEvent.KEYCODE_BUTTON_X -> cycleHoldDelayAt(currentState.focusedIndex)
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (currentState.focusedIndex > 0) {
                                    state = currentState.copy(focusedIndex = currentState.focusedIndex - 1)
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (currentState.focusedIndex < focusableRows.size - 1) {
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
                                state = HotkeysState.ActionList(focusedIndex = currentState.returnFocusIndex)
                            } else if (device != null && isGamepadDevice(device) && isRecordableKey(event.keyCode)) {
                                val newKeys = currentState.heldKeys + event.keyCode
                                if (newKeys.size <= 3) {
                                    state = currentState.copy(heldKeys = newKeys)
                                }
                            }
                        } else if (event.action == KeyEvent.ACTION_UP) {
                            if (isRecordableKey(event.keyCode) && event.keyCode in currentState.heldKeys) {
                                state = currentState.copy(heldKeys = currentState.heldKeys - event.keyCode)
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
        is HotkeysState.ActionList -> MenuListContent(
            rows = rows,
            focusableRows = focusableRows,
            focusedIndex = currentState.focusedIndex,
            onSelect = ::startRecording,
            onCycleHoldDelay = ::cycleHoldDelayAt,
            onDismiss = onDismiss
        )
        is HotkeysState.Recording -> RecordingContent(
            title = currentState.target.label,
            heldKeys = currentState.heldKeys,
            isComplete = currentState.isComplete,
            onComplete = { keys ->
                scope.launch {
                    when (val target = currentState.target) {
                        is RecordTarget.System -> onSaveHotkey(target.action, keys.toList())
                        is RecordTarget.Core -> onSaveCoreControl(target.def.retropadId, target.def.mode, keys.toList())
                    }
                    delay(500)
                    state = HotkeysState.ActionList(focusedIndex = currentState.returnFocusIndex)
                }
            },
            onCancel = {
                state = HotkeysState.ActionList(focusedIndex = currentState.returnFocusIndex)
            },
            onUpdateComplete = { complete ->
                state = currentState.copy(isComplete = complete)
            }
        )
    }
}

private fun buildRows(
    hotkeys: List<HotkeyEntity>,
    coreControls: List<CoreControlDef>,
    coreId: String?,
    coreName: String?,
    conflictingActions: Set<HotkeyAction>
): List<MenuRow> = buildList {
    add(MenuRow.Header("System Hotkeys", dimmed = false))
    HOTKEY_ACTIONS.forEach { action ->
        val entity = hotkeys.find { it.action == action }
        add(
            MenuRow.System(
                action = action,
                combo = entity?.let { parseComboJson(it.buttonComboJson) } ?: emptyList(),
                holdMs = entity?.holdMs ?: 0L,
                conflicting = action in conflictingActions
            )
        )
    }

    if (coreId != null) {
        val coreBinds = hotkeys.filter {
            it.scopeType == HotkeyScopeType.CORE && it.scopeKey == coreId &&
                it.action == HotkeyAction.SEND_CORE_INPUT
        }
        val controlRows = coreControls.map { def ->
            MenuRow.Core(def, coreBinds.find { it.coreInputRetropadId == def.retropadId })
        }
        val headerTitle = coreName?.let { "Core Hotkeys — $it" } ?: "Core Hotkeys"
        add(MenuRow.Header(headerTitle, dimmed = controlRows.isEmpty()))
        if (controlRows.isEmpty()) {
            add(MenuRow.Placeholder("-- no settings available --"))
        } else {
            addAll(controlRows)
        }
    }
}

@Composable
private fun MenuListContent(
    rows: List<MenuRow>,
    focusableRows: List<MenuRow>,
    focusedIndex: Int,
    onSelect: (Int) -> Unit,
    onCycleHoldDelay: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val focusedRow = focusableRows.getOrNull(focusedIndex)
    val focusedRowPosition = focusedRow?.let { rows.indexOf(it) } ?: 0

    FocusedScroll(listState = listState, focusedIndex = focusedRowPosition)

    Modal(
        title = "Hotkeys",
        subtitle = "Configure button shortcuts",
        baseWidth = 520.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Record",
            InputButton.B to "Back",
            InputButton.X to "Hold delay",
            InputButton.Y to "Clear"
        )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(rows) { _, row ->
                when (row) {
                    is MenuRow.Header -> SectionHeaderRow(row.title, row.dimmed)
                    is MenuRow.Placeholder -> PlaceholderRow(row.text)
                    is MenuRow.System -> {
                        val fIndex = focusableRows.indexOf(row)
                        HotkeyRow(
                            title = getActionDisplayName(row.action),
                            combo = row.combo,
                            holdMs = row.holdMs,
                            isFocused = fIndex == focusedIndex,
                            isConflicting = row.conflicting,
                            onClick = { onSelect(fIndex) },
                            onSecondaryClick = { onCycleHoldDelay(fIndex) }
                        )
                    }
                    is MenuRow.Core -> {
                        val fIndex = focusableRows.indexOf(row)
                        HotkeyRow(
                            title = row.def.label,
                            combo = row.boundEntity?.let { parseComboJson(it.buttonComboJson) } ?: emptyList(),
                            holdMs = 0L,
                            isFocused = fIndex == focusedIndex,
                            isConflicting = false,
                            onClick = { onSelect(fIndex) },
                            onSecondaryClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(title: String, dimmed: Boolean) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dimmed) 0.4f else 1f),
        modifier = Modifier.padding(
            top = Dimens.spacingSm,
            bottom = Dimens.spacingXs,
            start = Dimens.spacingXs
        )
    )
}

@Composable
private fun PlaceholderRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    )
}

@Composable
private fun HotkeyRow(
    title: String,
    combo: List<Int>,
    holdMs: Long,
    isFocused: Boolean,
    isConflicting: Boolean,
    onClick: () -> Unit,
    onSecondaryClick: () -> Unit
) {
    val backgroundColor = when {
        isFocused && isConflicting -> MaterialTheme.colorScheme.errorContainer
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val borderColor = when {
        isConflicting -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val contentColor = when {
        isFocused && isConflicting -> MaterialTheme.colorScheme.onErrorContainer
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        isConflicting -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryAlpha = if (isFocused) 1.0f else 0.7f

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
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (holdMs > 0L) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickableNoFocus(onClick = onSecondaryClick)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Hold delay",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor.copy(alpha = secondaryAlpha)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = "${holdMs / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = secondaryAlpha)
                    )
                }
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = secondaryAlpha * 0.6f)
                )
            }
            Text(
                text = HotkeyManager.formatCombo(combo),
                style = MaterialTheme.typography.bodySmall,
                color = if (combo.isNotEmpty()) {
                    contentColor.copy(alpha = secondaryAlpha)
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
    title: String,
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
        subtitle = title,
        baseWidth = 350.dp,
        onDismiss = onCancel,
        footerHints = listOf(InputButton.B to "Cancel")
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
        HotkeyAction.RESET_GAME -> "Reset Game"
        HotkeyAction.QUICK_SAVE -> "Quick Save"
        HotkeyAction.QUICK_LOAD -> "Quick Load"
        HotkeyAction.FAST_FORWARD -> "Fast Forward"
        HotkeyAction.REWIND -> "Rewind"
        HotkeyAction.QUICK_SUSPEND -> "Quick Suspend"
        HotkeyAction.CYCLE_CORE_OPTION -> "Cycle Core Option"
        HotkeyAction.SEND_CORE_INPUT -> "Core Input"
    }
}

private fun findConflictingActions(hotkeys: List<HotkeyEntity>): Set<HotkeyAction> {
    val grouped = hotkeys
        .filter { it.action != HotkeyAction.CYCLE_CORE_OPTION && it.action != HotkeyAction.SEND_CORE_INPUT }
        .filter { it.buttonComboJson.isNotBlank() }
        .mapNotNull { entity ->
            val combo = parseComboJson(entity.buttonComboJson)
            if (combo.isEmpty()) return@mapNotNull null
            val key = HotkeyManager.canonicalizeCombo(combo) to entity.controllerId
            key to entity
        }
        .groupBy({ it.first }, { it.second })

    val result = mutableSetOf<HotkeyAction>()
    for ((_, group) in grouped) {
        if (group.size < 2) continue
        val instants = group.count { it.holdMs == 0L }
        val holds = group.count { it.holdMs > 0L }
        if (group.size > 2 || instants > 1 || holds > 1) {
            result.addAll(group.map { it.action })
        }
    }
    return result
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
