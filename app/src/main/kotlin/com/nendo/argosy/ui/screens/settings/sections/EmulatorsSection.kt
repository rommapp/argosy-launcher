package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.EmulatorPickerPopup
import com.nendo.argosy.ui.screens.settings.components.SavePathModal
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.components.VariantPickerModal
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

internal data class EmulatorsLayoutState(
    val canAutoAssign: Boolean,
    val builtinLibretroEnabled: Boolean = true
)

internal sealed class EmulatorsItem(
    val key: String,
    val section: String,
    val visibleWhen: (EmulatorsLayoutState) -> Boolean = { true }
) {
    data object BuiltinHeader : EmulatorsItem("builtin_header", "builtin")
    data object BuiltinVideo : EmulatorsItem("builtin_video", "builtin", visibleWhen = { it.builtinLibretroEnabled })
    data object BuiltinControls : EmulatorsItem("builtin_controls", "builtin", visibleWhen = { it.builtinLibretroEnabled })
    data object BuiltinCores : EmulatorsItem("builtin_cores", "builtin", visibleWhen = { it.builtinLibretroEnabled })
    data object BuiltinCoreOptions : EmulatorsItem("builtin_core_options", "builtin", visibleWhen = { it.builtinLibretroEnabled })
    data object BuiltinToggle : EmulatorsItem("builtin_toggle", "builtin")
    data object PlatformsHeader : EmulatorsItem("platforms_header", "platforms")
    data object CheckForUpdates : EmulatorsItem("check_updates", "platforms")
    data object AutoAssign : EmulatorsItem("autoAssign", "platforms", visibleWhen = { it.canAutoAssign })

    class PlatformItem(val config: PlatformEmulatorConfig, val index: Int) : EmulatorsItem(
        key = "platform_${config.platform.id}",
        section = "platforms"
    )

    companion object {
        fun buildItems(platforms: List<PlatformEmulatorConfig>): List<EmulatorsItem> =
            listOf(BuiltinHeader, BuiltinVideo, BuiltinControls, BuiltinCores, BuiltinCoreOptions, BuiltinToggle, PlatformsHeader, CheckForUpdates, AutoAssign) +
                platforms.mapIndexed { index, config -> PlatformItem(config, index) }
    }
}

internal fun createEmulatorsLayout(items: List<EmulatorsItem>) = SettingsLayout<EmulatorsItem, EmulatorsLayoutState>(
    allItems = items,
    isFocusable = { item -> item !is EmulatorsItem.BuiltinHeader && item !is EmulatorsItem.PlatformsHeader },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "builtin" -> "Built-in Emulator"
            "platforms" -> "Platforms"
            else -> null
        }
    }
)

internal fun emulatorsMaxFocusIndex(canAutoAssign: Boolean, platformCount: Int, builtinEnabled: Boolean = true): Int {
    val toggleCount = 1
    val builtinCount = if (builtinEnabled) 3 else 0
    val checkUpdatesCount = 1
    val autoAssignCount = if (canAutoAssign) 1 else 0
    return (toggleCount + builtinCount + checkUpdatesCount + autoAssignCount + platformCount - 1).coerceAtLeast(0)
}

internal data class EmulatorsLayoutInfo(
    val layout: SettingsLayout<EmulatorsItem, EmulatorsLayoutState>,
    val state: EmulatorsLayoutState
)

internal fun createEmulatorsLayoutInfo(
    platforms: List<PlatformEmulatorConfig>,
    canAutoAssign: Boolean,
    builtinLibretroEnabled: Boolean = true
): EmulatorsLayoutInfo {
    val items = EmulatorsItem.buildItems(platforms)
    val layout = createEmulatorsLayout(items)
    val state = EmulatorsLayoutState(canAutoAssign, builtinLibretroEnabled)
    return EmulatorsLayoutInfo(layout, state)
}

internal fun emulatorsSections(info: EmulatorsLayoutInfo) = info.layout.buildSections(info.state)

internal fun emulatorsItemAtFocusIndex(index: Int, info: EmulatorsLayoutInfo): EmulatorsItem? =
    info.layout.itemAtFocusIndex(index, info.state)

@Composable
fun EmulatorsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onLaunchSavePathPicker: () -> Unit
) {
    val emulators = uiState.emulators

    val layoutState = remember(emulators.canAutoAssign, emulators.builtinLibretroEnabled) {
        EmulatorsLayoutState(emulators.canAutoAssign, emulators.builtinLibretroEnabled)
    }

    val allItems = remember(emulators.platforms) {
        EmulatorsItem.buildItems(emulators.platforms)
    }

    val layout = remember(allItems) { createEmulatorsLayout(allItems) }
    val visibleItems = remember(layoutState, allItems) { layout.visibleItems(layoutState) }
    val sections = remember(layoutState, allItems) { layout.buildSections(layoutState) }

    fun isFocused(item: EmulatorsItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, layoutState)

    val modalBlur by animateDpAsState(
        targetValue = if (emulators.showEmulatorPicker || emulators.showSavePathModal || emulators.showVariantPicker) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "emulatorPickerBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { layout.focusToListIndex(it, layoutState) },
            itemKey = { it.key },
            isNavItem = { false },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
                when (item) {
                    EmulatorsItem.BuiltinHeader -> {
                        Text(
                            text = "Built-in Emulator",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = Dimens.spacingSm,
                                top = Dimens.spacingMd,
                                bottom = Dimens.spacingXs
                            )
                        )
                    }

                    EmulatorsItem.BuiltinToggle -> SwitchPreference(
                        title = "Enable Built-in Emulator",
                        subtitle = "Use LibRetro cores for supported platforms",
                        isEnabled = emulators.builtinLibretroEnabled,
                        isFocused = isFocused(item),
                        onToggle = { viewModel.setBuiltinLibretroEnabled(it) }
                    )

                    EmulatorsItem.BuiltinVideo -> ActionPreference(
                        title = "Video Settings",
                        subtitle = "Shaders, scaling, aspect ratio",
                        isFocused = isFocused(item),
                        onClick = { viewModel.navigateToBuiltinVideo() }
                    )

                    EmulatorsItem.BuiltinControls -> ActionPreference(
                        title = "Controls",
                        subtitle = "Rumble, input mapping, hotkeys",
                        isFocused = isFocused(item),
                        onClick = { viewModel.navigateToBuiltinControls() }
                    )

                    EmulatorsItem.BuiltinCores -> {
                        val updatesAvailable = uiState.emulators.coreUpdatesAvailable
                        ActionPreference(
                            title = "Manage Cores",
                            subtitle = "${uiState.emulators.installedCoreCount} of ${uiState.emulators.totalCoreCount} cores installed",
                            isFocused = isFocused(item),
                            onClick = { viewModel.navigateToCoreManagement() },
                            badge = if (updatesAvailable > 0) "$updatesAvailable update${if (updatesAvailable > 1) "s" else ""}" else null
                        )
                    }

                    EmulatorsItem.BuiltinCoreOptions -> ActionPreference(
                        title = "Core Options",
                        subtitle = "Per-core settings and overrides",
                        isFocused = isFocused(item),
                        onClick = { viewModel.navigateToCoreOptions() }
                    )

                    EmulatorsItem.PlatformsHeader -> {
                        Text(
                            text = "Platforms",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = Dimens.spacingSm,
                                top = Dimens.spacingLg,
                                bottom = Dimens.spacingXs
                            )
                        )
                    }

                    EmulatorsItem.CheckForUpdates -> ActionPreference(
                        title = "Check for Updates",
                        subtitle = if (emulators.emulatorUpdatesAvailable > 0)
                            "${emulators.emulatorUpdatesAvailable} update${if (emulators.emulatorUpdatesAvailable > 1) "s" else ""} available"
                        else "Check for emulator updates",
                        isFocused = isFocused(item),
                        onClick = { viewModel.forceCheckEmulatorUpdates() }
                    )

                    EmulatorsItem.AutoAssign -> ActionPreference(
                        title = "Auto-assign Emulators",
                        subtitle = "Set recommended emulators for all platforms",
                        isFocused = isFocused(item),
                        onClick = { viewModel.handlePlatformItemTap(-1) }
                    )

                    is EmulatorsItem.PlatformItem -> {
                        val config = item.config
                        val gameCount = config.platform.gameCount
                        val emulatorName = config.effectiveEmulatorName ?: "Not configured"
                        val subtitle = if (gameCount > 0) "$gameCount games" else "No games"
                        val updateCount = emulators.platformUpdatesAvailable[config.platform.slug] ?: 0
                        ActionPreference(
                            title = config.platform.name,
                            subtitle = subtitle,
                            isFocused = isFocused(item),
                            trailingText = emulatorName,
                            badge = if (updateCount > 0) "$updateCount" else null,
                            onClick = { viewModel.navigateToPlatformDetail(item.index) }
                        )
                    }
                }
        }

        if (emulators.showEmulatorPicker && emulators.emulatorPickerInfo != null) {
            EmulatorPickerPopup(
                info = emulators.emulatorPickerInfo,
                focusIndex = emulators.emulatorPickerFocusIndex,
                selectedIndex = emulators.emulatorPickerSelectedIndex,
                onItemTap = { index -> viewModel.handleEmulatorPickerItemTap(index) },
                onConfirm = { viewModel.confirmEmulatorPickerSelection() },
                onDismiss = { viewModel.dismissEmulatorPicker() }
            )
        }

        if (emulators.showSavePathModal && emulators.savePathModalInfo != null) {
            SavePathModal(
                info = emulators.savePathModalInfo,
                focusIndex = emulators.savePathModalFocusIndex,
                buttonFocusIndex = emulators.savePathModalButtonIndex,
                onDismiss = { viewModel.dismissSavePathModal() },
                onChangeSavePath = onLaunchSavePathPicker,
                onResetSavePath = {
                    viewModel.resetEmulatorSavePath(emulators.savePathModalInfo.emulatorId)
                }
            )
        }

        if (emulators.showVariantPicker && emulators.variantPickerInfo != null) {
            VariantPickerModal(
                info = emulators.variantPickerInfo,
                focusIndex = emulators.variantPickerFocusIndex,
                onItemTap = { index -> viewModel.handleVariantPickerItemTap(index) },
                onConfirm = { viewModel.confirmVariantSelection() },
                onDismiss = { viewModel.dismissVariantPicker() }
            )
        }
    }
}
