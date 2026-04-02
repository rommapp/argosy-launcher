package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

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
                isFocused = uiState.focusedIndex == 0,
                onToggle = { viewModel.setBuiltinLibretroEnabled(it) }
            )
        }
        if (builtinEnabled) {
            item(key = "builtin_video") {
                ActionPreference(
                    title = "A/V & Performance",
                    subtitle = "Shaders, display, performance, saving",
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.navigateToBuiltinVideo() }
                )
            }
            item(key = "builtin_controls") {
                ActionPreference(
                    title = "Controls",
                    subtitle = "Rumble, input mapping, hotkeys",
                    isFocused = uiState.focusedIndex == 2,
                    onClick = { viewModel.navigateToBuiltinControls() }
                )
            }
            item(key = "builtin_cores") {
                val updatesAvailable = emulators.coreUpdatesAvailable
                ActionPreference(
                    title = "Manage Cores",
                    subtitle = "${emulators.installedCoreCount} of ${emulators.totalCoreCount} cores installed",
                    isFocused = uiState.focusedIndex == 3,
                    onClick = { viewModel.navigateToCoreManagement() },
                    badge = if (updatesAvailable > 0) "$updatesAvailable update${if (updatesAvailable > 1) "s" else ""}" else null
                )
            }
            item(key = "builtin_core_options") {
                ActionPreference(
                    title = "Core Options",
                    subtitle = "Per-core settings and overrides",
                    isFocused = uiState.focusedIndex == 4,
                    onClick = { viewModel.navigateToCoreOptions() }
                )
            }
        }
    }
}
