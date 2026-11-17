package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.HudCorner
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.ARCHITECTURE_OPTIONS
import com.nendo.argosy.ui.screens.settings.components.LocalSettingsTouchFocus
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.settingsRowItem
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.hudCornerFromStored

internal enum class BuiltinEmulatorItem(
    val key: String,
    @StringRes override val labelRes: Int
) : SearchableSetting {
    ENABLE("builtin_toggle", R.string.settings_builtin_enable_title),
    ARCHITECTURE("builtin_architecture", R.string.settings_builtin_architecture_title),
    VIDEO("builtin_video", R.string.settings_builtin_video_title),
    CONTROLS("builtin_controls", R.string.settings_builtin_controls_title),
    SAVES("builtin_saves", R.string.settings_builtin_saves_title),
    CORE_MANAGEMENT("builtin_cores", R.string.settings_builtin_cores_title),
    CORE_OPTIONS("builtin_core_options", R.string.settings_builtin_core_options_title),
    TWO_COLUMN("builtin_two_column_menu", R.string.settings_builtin_two_column_title),
    HUD_ENABLED("builtin_hud_enabled", R.string.settings_builtin_hud_enabled_title),
    HUD_CORNER("builtin_hud_corner", R.string.settings_builtin_hud_corner_title),
    HUD_BATTERY("builtin_hud_battery", R.string.settings_builtin_hud_battery_title),
    HUD_CLOCK("builtin_hud_clock", R.string.settings_builtin_hud_clock_title),
    HUD_PLAYTIME("builtin_hud_playtime", R.string.settings_builtin_hud_playtime_title),
    HUD_FPS("builtin_hud_fps", R.string.settings_builtin_hud_fps_title),
    HUD_LAST_SAVE("builtin_hud_last_save", R.string.settings_builtin_hud_last_save_title);

    val focusIndex: Int get() = ordinal

    fun isShown(emulators: EmulatorState): Boolean = when (this) {
        ENABLE -> true
        HUD_CORNER, HUD_BATTERY, HUD_CLOCK, HUD_PLAYTIME, HUD_FPS, HUD_LAST_SAVE ->
            emulators.builtinLibretroEnabled && emulators.hudEnabled
        else -> emulators.builtinLibretroEnabled
    }
}

internal val builtinEmulatorLayout = SettingsLayout<BuiltinEmulatorItem, EmulatorState>(
    allItems = BuiltinEmulatorItem.entries,
    isFocusable = { true },
    visibleWhen = { item, emulators -> item.isShown(emulators) }
)

@Composable
fun BuiltinEmulatorSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val emulators = uiState.emulators
    val builtinEnabled = emulators.builtinLibretroEnabled
    val listState = rememberLazyListState()

    val statusBarHeaderOffset =
        if (builtinEnabled && uiState.focusedIndex >= BuiltinEmulatorItem.HUD_ENABLED.focusIndex) 1 else 0

    FocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex + statusBarHeaderOffset,
        isTouchMode = LocalSettingsTouchFocus.current.isTouchMode
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        settingsRowItem(key = BuiltinEmulatorItem.ENABLE.key, focusIndex = BuiltinEmulatorItem.ENABLE.focusIndex) {
            SwitchPreference(
                title = stringResource(BuiltinEmulatorItem.ENABLE.labelRes),
                subtitle = stringResource(R.string.settings_builtin_enable_subtitle),
                isEnabled = builtinEnabled,
                isFocused = uiState.focusedIndex == BuiltinEmulatorItem.ENABLE.focusIndex,
                onToggle = { viewModel.setBuiltinLibretroEnabled(it) }
            )
        }
        if (BuiltinEmulatorItem.ARCHITECTURE.isShown(emulators)) {
            settingsRowItem(
                key = BuiltinEmulatorItem.ARCHITECTURE.key,
                focusIndex = BuiltinEmulatorItem.ARCHITECTURE.focusIndex
            ) {
                val architectureOptions = remember { ARCHITECTURE_OPTIONS }
                SegmentedPreference(
                    title = stringResource(BuiltinEmulatorItem.ARCHITECTURE.labelRes),
                    options = architectureOptions,
                    selectedIndex = architectureOptions.indexOf(emulators.architectureDisplay),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.ARCHITECTURE.focusIndex,
                    onSelect = { viewModel.setBuiltinArchitecture(architectureOptions[it]) }
                )
            }
            settingsRowItem(key = BuiltinEmulatorItem.VIDEO.key, focusIndex = BuiltinEmulatorItem.VIDEO.focusIndex) {
                ActionPreference(
                    title = stringResource(BuiltinEmulatorItem.VIDEO.labelRes),
                    subtitle = stringResource(R.string.settings_builtin_video_subtitle),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.VIDEO.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.VIDEO.focusIndex)
                        viewModel.navigateToBuiltinVideo()
                    }
                )
            }
            settingsRowItem(key = BuiltinEmulatorItem.CONTROLS.key, focusIndex = BuiltinEmulatorItem.CONTROLS.focusIndex) {
                ActionPreference(
                    title = stringResource(BuiltinEmulatorItem.CONTROLS.labelRes),
                    subtitle = stringResource(R.string.settings_builtin_controls_subtitle),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CONTROLS.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.CONTROLS.focusIndex)
                        viewModel.navigateToBuiltinControls()
                    }
                )
            }
            settingsRowItem(key = BuiltinEmulatorItem.SAVES.key, focusIndex = BuiltinEmulatorItem.SAVES.focusIndex) {
                ActionPreference(
                    title = stringResource(BuiltinEmulatorItem.SAVES.labelRes),
                    subtitle = stringResource(R.string.settings_builtin_saves_subtitle),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.SAVES.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.SAVES.focusIndex)
                        viewModel.navigateToBuiltinSaves()
                    }
                )
            }
            settingsRowItem(
                key = BuiltinEmulatorItem.CORE_MANAGEMENT.key,
                focusIndex = BuiltinEmulatorItem.CORE_MANAGEMENT.focusIndex
            ) {
                val updatesAvailable = emulators.coreUpdatesAvailable
                ActionPreference(
                    title = stringResource(BuiltinEmulatorItem.CORE_MANAGEMENT.labelRes),
                    subtitle = stringResource(
                        R.string.settings_builtin_cores_subtitle,
                        emulators.installedCoreCount,
                        emulators.totalCoreCount
                    ),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CORE_MANAGEMENT.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.CORE_MANAGEMENT.focusIndex)
                        viewModel.navigateToCoreManagement()
                    },
                    badge = if (updatesAvailable > 0) {
                        pluralStringResource(
                            R.plurals.settings_builtin_cores_updates_badge,
                            updatesAvailable,
                            updatesAvailable
                        )
                    } else {
                        null
                    }
                )
            }
            settingsRowItem(
                key = BuiltinEmulatorItem.CORE_OPTIONS.key,
                focusIndex = BuiltinEmulatorItem.CORE_OPTIONS.focusIndex
            ) {
                ActionPreference(
                    title = stringResource(BuiltinEmulatorItem.CORE_OPTIONS.labelRes),
                    subtitle = stringResource(R.string.settings_builtin_core_options_subtitle),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CORE_OPTIONS.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.CORE_OPTIONS.focusIndex)
                        viewModel.navigateToCoreOptions()
                    }
                )
            }
            settingsRowItem(
                key = BuiltinEmulatorItem.TWO_COLUMN.key,
                focusIndex = BuiltinEmulatorItem.TWO_COLUMN.focusIndex
            ) {
                SwitchPreference(
                    title = stringResource(BuiltinEmulatorItem.TWO_COLUMN.labelRes),
                    subtitle = if (emulators.ingameMenuTwoColumn) {
                        stringResource(R.string.settings_builtin_two_column_subtitle_on)
                    } else {
                        stringResource(R.string.settings_builtin_two_column_subtitle_off)
                    },
                    isEnabled = emulators.ingameMenuTwoColumn,
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.TWO_COLUMN.focusIndex,
                    onToggle = { viewModel.setIngameMenuTwoColumn(it) }
                )
            }
            item(key = "builtin_hud_header") {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                SectionHeader(stringResource(R.string.settings_builtin_section_hud))
            }
            settingsRowItem(
                key = BuiltinEmulatorItem.HUD_ENABLED.key,
                focusIndex = BuiltinEmulatorItem.HUD_ENABLED.focusIndex
            ) {
                SwitchPreference(
                    title = stringResource(BuiltinEmulatorItem.HUD_ENABLED.labelRes),
                    subtitle = if (emulators.hudEnabled) {
                        stringResource(R.string.settings_builtin_hud_enabled_subtitle_on)
                    } else {
                        stringResource(R.string.settings_builtin_hud_enabled_subtitle_off)
                    },
                    isEnabled = emulators.hudEnabled,
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_ENABLED.focusIndex,
                    onToggle = { viewModel.setHudEnabled(it) }
                )
            }
            if (BuiltinEmulatorItem.HUD_CORNER.isShown(emulators)) {
                settingsRowItem(
                    key = BuiltinEmulatorItem.HUD_CORNER.key,
                    focusIndex = BuiltinEmulatorItem.HUD_CORNER.focusIndex
                ) {
                    SegmentedPreference(
                        title = stringResource(BuiltinEmulatorItem.HUD_CORNER.labelRes),
                        options = HudCorner.entries.map { stringResource(it.labelRes) },
                        selectedIndex = hudCornerFromStored(emulators.hudCorner).ordinal,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_CORNER.focusIndex,
                        onSelect = { index -> viewModel.setHudCorner(HudCorner.entries[index].name) }
                    )
                }
                settingsRowItem(
                    key = BuiltinEmulatorItem.HUD_BATTERY.key,
                    focusIndex = BuiltinEmulatorItem.HUD_BATTERY.focusIndex
                ) {
                    SwitchPreference(
                        title = stringResource(BuiltinEmulatorItem.HUD_BATTERY.labelRes),
                        isEnabled = emulators.hudShowBattery,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_BATTERY.focusIndex,
                        onToggle = { viewModel.setHudShowBattery(it) }
                    )
                }
                settingsRowItem(
                    key = BuiltinEmulatorItem.HUD_CLOCK.key,
                    focusIndex = BuiltinEmulatorItem.HUD_CLOCK.focusIndex
                ) {
                    SwitchPreference(
                        title = stringResource(BuiltinEmulatorItem.HUD_CLOCK.labelRes),
                        isEnabled = emulators.hudShowClock,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_CLOCK.focusIndex,
                        onToggle = { viewModel.setHudShowClock(it) }
                    )
                }
                settingsRowItem(
                    key = BuiltinEmulatorItem.HUD_PLAYTIME.key,
                    focusIndex = BuiltinEmulatorItem.HUD_PLAYTIME.focusIndex
                ) {
                    SwitchPreference(
                        title = stringResource(BuiltinEmulatorItem.HUD_PLAYTIME.labelRes),
                        subtitle = stringResource(R.string.settings_builtin_hud_playtime_subtitle),
                        isEnabled = emulators.hudShowPlaytime,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_PLAYTIME.focusIndex,
                        onToggle = { viewModel.setHudShowPlaytime(it) }
                    )
                }
                settingsRowItem(
                    key = BuiltinEmulatorItem.HUD_FPS.key,
                    focusIndex = BuiltinEmulatorItem.HUD_FPS.focusIndex
                ) {
                    SwitchPreference(
                        title = stringResource(BuiltinEmulatorItem.HUD_FPS.labelRes),
                        subtitle = stringResource(R.string.settings_builtin_hud_fps_subtitle),
                        isEnabled = emulators.hudShowFps,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_FPS.focusIndex,
                        onToggle = { viewModel.setHudShowFps(it) }
                    )
                }
                settingsRowItem(
                    key = BuiltinEmulatorItem.HUD_LAST_SAVE.key,
                    focusIndex = BuiltinEmulatorItem.HUD_LAST_SAVE.focusIndex
                ) {
                    SwitchPreference(
                        title = stringResource(BuiltinEmulatorItem.HUD_LAST_SAVE.labelRes),
                        subtitle = stringResource(R.string.settings_builtin_hud_last_save_subtitle),
                        isEnabled = emulators.hudShowLastSave,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_LAST_SAVE.focusIndex,
                        onToggle = { viewModel.setHudShowLastSave(it) }
                    )
                }
            }
        }
    }
}
