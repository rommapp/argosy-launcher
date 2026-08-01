package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.ARCHITECTURE_OPTIONS
import com.nendo.argosy.ui.screens.settings.BUILTIN_ARCHITECTURE_PICKER_KEY
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

internal enum class BuiltinEmulatorItem {
    ENABLE,
    ARCHITECTURE,
    VIDEO,
    CONTROLS,
    CORE_MANAGEMENT,
    CORE_OPTIONS,
    TWO_COLUMN,
    HUD_ENABLED,
    HUD_CORNER,
    HUD_BATTERY,
    HUD_CLOCK,
    HUD_PLAYTIME,
    HUD_FPS,
    HUD_LAST_SAVE;

    val focusIndex: Int get() = ordinal
}

@Composable
fun BuiltinEmulatorSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val emulators = uiState.emulators
    val builtinEnabled = emulators.builtinLibretroEnabled
    val listState = rememberLazyListState()

    FocusedScroll(listState = listState, focusedIndex = uiState.focusedIndex)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item(key = "builtin_toggle") {
            SwitchPreference(
                title = "Enable Built-in Emulator",
                subtitle = "Use LibRetro cores for supported platforms",
                isEnabled = builtinEnabled,
                isFocused = uiState.focusedIndex == BuiltinEmulatorItem.ENABLE.focusIndex,
                onToggle = { viewModel.setBuiltinLibretroEnabled(it) }
            )
        }
        if (builtinEnabled) {
            item(key = "builtin_architecture") {
                val architectureOptions = remember { ARCHITECTURE_OPTIONS }
                CyclePreference(
                    title = "Architecture",
                    value = emulators.architectureDisplay,
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.ARCHITECTURE.focusIndex,
                    onClick = { viewModel.cycleBuiltinArchitecture(1) },
                    onPrev = { viewModel.cycleBuiltinArchitecture(-1) },
                    options = architectureOptions,
                    onSelect = {
                        val current = architectureOptions.indexOf(emulators.architectureDisplay).coerceAtLeast(0)
                        viewModel.cycleBuiltinArchitecture(it - current)
                    },
                    pickerRequestToken = if (uiState.enumPickerKey == BUILTIN_ARCHITECTURE_PICKER_KEY) uiState.enumPickerToken else 0
                )
            }
            item(key = "builtin_video") {
                ActionPreference(
                    title = "A/V & Performance",
                    subtitle = "Shaders, display, performance, saving",
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.VIDEO.focusIndex,
                    onClick = { viewModel.navigateToBuiltinVideo() }
                )
            }
            item(key = "builtin_controls") {
                ActionPreference(
                    title = "Controls",
                    subtitle = "Rumble, input mapping, hotkeys",
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CONTROLS.focusIndex,
                    onClick = { viewModel.navigateToBuiltinControls() }
                )
            }
            item(key = "builtin_cores") {
                val updatesAvailable = emulators.coreUpdatesAvailable
                ActionPreference(
                    title = "Manage Cores",
                    subtitle = "${emulators.installedCoreCount} of ${emulators.totalCoreCount} cores installed",
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CORE_MANAGEMENT.focusIndex,
                    onClick = { viewModel.navigateToCoreManagement() },
                    badge = if (updatesAvailable > 0) "$updatesAvailable update${if (updatesAvailable > 1) "s" else ""}" else null
                )
            }
            item(key = "builtin_core_options") {
                ActionPreference(
                    title = "Core Options",
                    subtitle = "Per-core settings and overrides",
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CORE_OPTIONS.focusIndex,
                    onClick = { viewModel.navigateToCoreOptions() }
                )
            }
            item(key = "builtin_two_column_menu") {
                SwitchPreference(
                    title = "Two-Column Menu",
                    subtitle = if (emulators.ingameMenuTwoColumn) {
                        "In-game menu uses two columns on wide displays"
                    } else {
                        "In-game menu uses a single column"
                    },
                    isEnabled = emulators.ingameMenuTwoColumn,
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.TWO_COLUMN.focusIndex,
                    onToggle = { viewModel.setIngameMenuTwoColumn(it) }
                )
            }
            item(key = "builtin_hud_enabled") {
                SwitchPreference(
                    title = "In-Game Status Bar",
                    subtitle = if (emulators.hudEnabled) {
                        "Shows a small status bar over the game"
                    } else {
                        "No status bar while playing"
                    },
                    isEnabled = emulators.hudEnabled,
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_ENABLED.focusIndex,
                    onToggle = { viewModel.setHudEnabled(it) }
                )
            }
            if (emulators.hudEnabled) {
                item(key = "builtin_hud_corner") {
                    CyclePreference(
                        title = "Status Bar Corner",
                        value = emulators.hudCorner,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_CORNER.focusIndex,
                        onClick = { viewModel.cycleHudCorner(true) },
                        onPrev = { viewModel.cycleHudCorner(false) },
                        options = HUD_CORNERS,
                        onSelect = { index -> HUD_CORNERS.getOrNull(index)?.let { viewModel.setHudCorner(it) } }
                    )
                }
                item(key = "builtin_hud_battery") {
                    SwitchPreference(
                        title = "Show Battery",
                        isEnabled = emulators.hudShowBattery,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_BATTERY.focusIndex,
                        onToggle = { viewModel.setHudShowBattery(it) }
                    )
                }
                item(key = "builtin_hud_clock") {
                    SwitchPreference(
                        title = "Show Clock",
                        isEnabled = emulators.hudShowClock,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_CLOCK.focusIndex,
                        onToggle = { viewModel.setHudShowClock(it) }
                    )
                }
                item(key = "builtin_hud_playtime") {
                    SwitchPreference(
                        title = "Show Session Time",
                        subtitle = "Time since this session started",
                        isEnabled = emulators.hudShowPlaytime,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_PLAYTIME.focusIndex,
                        onToggle = { viewModel.setHudShowPlaytime(it) }
                    )
                }
                item(key = "builtin_hud_fps") {
                    SwitchPreference(
                        title = "Show FPS",
                        subtitle = "Presented frames per second; hidden while fast-forwarding",
                        isEnabled = emulators.hudShowFps,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_FPS.focusIndex,
                        onToggle = { viewModel.setHudShowFps(it) }
                    )
                }
                item(key = "builtin_hud_last_save") {
                    SwitchPreference(
                        title = "Show Last Save State",
                        subtitle = "How long ago a save state was written",
                        isEnabled = emulators.hudShowLastSave,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_LAST_SAVE.focusIndex,
                        onToggle = { viewModel.setHudShowLastSave(it) }
                    )
                }
            }
        }
    }
}

internal val HUD_CORNERS = listOf("Top Left", "Top Right", "Bottom Left", "Bottom Right")
