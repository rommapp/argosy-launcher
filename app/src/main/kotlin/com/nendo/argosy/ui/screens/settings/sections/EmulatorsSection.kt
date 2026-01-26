package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.EmulatorPickerPopup
import com.nendo.argosy.ui.screens.settings.components.SavePathModal
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

internal data class EmulatorsLayoutState(val canAutoAssign: Boolean)

internal sealed class EmulatorsItem(
    val key: String,
    val section: String,
    val visibleWhen: (EmulatorsLayoutState) -> Boolean = { true }
) {
    data object BuiltinHeader : EmulatorsItem("builtin_header", "builtin")
    data object BuiltinVideo : EmulatorsItem("builtin_video", "builtin")
    data object BuiltinControls : EmulatorsItem("builtin_controls", "builtin")
    data object BuiltinCores : EmulatorsItem("builtin_cores", "builtin")
    data object PlatformsHeader : EmulatorsItem("platforms_header", "platforms")
    data object AutoAssign : EmulatorsItem("autoAssign", "platforms", visibleWhen = { it.canAutoAssign })

    class PlatformItem(val config: PlatformEmulatorConfig, val index: Int) : EmulatorsItem(
        key = "platform_${config.platform.id}",
        section = "platforms"
    )

    companion object {
        fun buildItems(platforms: List<PlatformEmulatorConfig>): List<EmulatorsItem> =
            listOf(BuiltinHeader, BuiltinVideo, BuiltinControls, BuiltinCores, PlatformsHeader, AutoAssign) +
                platforms.mapIndexed { index, config -> PlatformItem(config, index) }
    }
}

internal fun createEmulatorsLayout(items: List<EmulatorsItem>) = SettingsLayout<EmulatorsItem, EmulatorsLayoutState>(
    allItems = items,
    isFocusable = { item -> item !is EmulatorsItem.BuiltinHeader && item !is EmulatorsItem.PlatformsHeader },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun emulatorsMaxFocusIndex(canAutoAssign: Boolean, platformCount: Int): Int {
    val builtinCount = 3  // Video, Audio, Cores (header not focusable)
    val autoAssignCount = if (canAutoAssign) 1 else 0
    return (builtinCount + autoAssignCount + platformCount - 1).coerceAtLeast(0)
}

internal data class EmulatorsLayoutInfo(
    val layout: SettingsLayout<EmulatorsItem, EmulatorsLayoutState>,
    val state: EmulatorsLayoutState
)

internal fun createEmulatorsLayoutInfo(
    platforms: List<PlatformEmulatorConfig>,
    canAutoAssign: Boolean
): EmulatorsLayoutInfo {
    val items = EmulatorsItem.buildItems(platforms)
    val layout = createEmulatorsLayout(items)
    val state = EmulatorsLayoutState(canAutoAssign)
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
    val listState = rememberLazyListState()
    val emulators = uiState.emulators

    val layoutState = remember(emulators.canAutoAssign) {
        EmulatorsLayoutState(emulators.canAutoAssign)
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
        targetValue = if (emulators.showEmulatorPicker || emulators.showSavePathModal) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "emulatorPickerBlur"
    )

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, layoutState) },
        sections = sections
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            items(visibleItems, key = { it.key }) { item ->
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

                    EmulatorsItem.AutoAssign -> ActionPreference(
                        title = "Auto-assign Emulators",
                        subtitle = "Set recommended emulators for all platforms",
                        isFocused = isFocused(item),
                        onClick = { viewModel.handlePlatformItemTap(-1) }
                    )

                    is EmulatorsItem.PlatformItem -> {
                        val itemFocused = isFocused(item)
                        PlatformEmulatorItem(
                            config = item.config,
                            isFocused = itemFocused,
                            subFocusIndex = if (itemFocused) emulators.platformSubFocusIndex else 0,
                            onEmulatorClick = { viewModel.handlePlatformItemTap(item.index) },
                            onCycleCore = { direction -> viewModel.cycleCoreForPlatform(item.config, direction) },
                            onExtensionChange = { extension -> viewModel.changeExtensionForPlatform(item.config, extension) },
                            onSavePathClick = { viewModel.showSavePathModal(item.config) }
                        )
                    }
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
    }
}

@Composable
private fun PlatformEmulatorItem(
    config: PlatformEmulatorConfig,
    isFocused: Boolean,
    subFocusIndex: Int,
    onEmulatorClick: () -> Unit,
    onCycleCore: (Int) -> Unit,
    onExtensionChange: (String) -> Unit,
    onSavePathClick: () -> Unit
) {
    val disabledAlpha = 0.45f
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        !config.hasInstalledEmulators -> MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = when {
        !config.hasInstalledEmulators -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val emulatorSubFocused = isFocused && subFocusIndex == 0
    val savesSubFocused = isFocused && subFocusIndex == 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusLg))
            .clickableNoFocus(onClick = onEmulatorClick)
            .padding(Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = config.platform.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    text = if (config.hasInstalledEmulators) "${config.availableEmulators.size} emulators available" else "No emulators installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
            val emulatorDisplay = when {
                !config.hasInstalledEmulators -> "Download"
                config.selectedEmulator != null -> config.selectedEmulator
                config.effectiveEmulatorName != null -> config.effectiveEmulatorName
                else -> "Auto"
            }
            if (config.hasInstalledEmulators) {
                when {
                    emulatorSubFocused -> {
                        Button(
                            onClick = onEmulatorClick,
                            modifier = Modifier.height(Dimens.iconLg),
                            contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = Dimens.elevationNone),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(text = emulatorDisplay, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    isFocused -> {
                        Button(
                            onClick = onEmulatorClick,
                            modifier = Modifier.height(Dimens.iconLg),
                            contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = Dimens.elevationNone),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(text = emulatorDisplay, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    else -> {
                        Text(
                            text = emulatorDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Text(
                    text = emulatorDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }

        if (config.showCoreSelection) {
            val selectedCoreId = config.selectedCore ?: config.availableCores.firstOrNull()?.id
            val selectedCoreName = config.availableCores.find { it.id == selectedCoreId }?.displayName
                ?: config.availableCores.firstOrNull()?.displayName ?: "Default"

            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(
                    text = "Core",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
                if (isFocused) {
                    config.availableCores.forEach { core ->
                        val isSelected = core.id == selectedCoreId
                        if (isSelected) {
                            OutlinedButton(
                                onClick = { },
                                modifier = Modifier.height(Dimens.iconLg - Dimens.spacingXs),
                                contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.elevationNone),
                                border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Text(
                                    text = core.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    val currentIdx = config.availableCores.indexOfFirst { it.id == selectedCoreId }
                                    val targetIdx = config.availableCores.indexOf(core)
                                    onCycleCore(targetIdx - currentIdx)
                                },
                                modifier = Modifier.height(Dimens.iconLg - Dimens.spacingXs),
                                contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.elevationNone)
                            ) {
                                Text(
                                    text = core.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = selectedCoreName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (config.showExtensionSelection) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(
                    text = "File Extension",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
                if (isFocused) {
                    config.extensionOptions.forEach { option ->
                        val isSelected = option.extension == config.selectedExtension.orEmpty()
                        if (isSelected) {
                            OutlinedButton(
                                onClick = { },
                                modifier = Modifier.height(Dimens.iconLg - Dimens.spacingXs),
                                contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.elevationNone),
                                border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            TextButton(
                                onClick = { onExtensionChange(option.extension) },
                                modifier = Modifier.height(Dimens.iconLg - Dimens.spacingXs),
                                contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.elevationNone)
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    val displayLabel = config.extensionOptions.find {
                        it.extension == config.selectedExtension.orEmpty()
                    }?.label ?: "Unchanged"
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (config.showSavePath && config.hasInstalledEmulators) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .clickableNoFocus(onClick = onSavePathClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Text(
                            text = "Saves",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryColor
                        )
                        if (config.isUserSavePathOverride) {
                            Text(
                                text = "(custom)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = config.effectiveSavePath?.let { formatStoragePath(it) } ?: "Not configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            config.effectiveSavePath == null -> secondaryColor.copy(alpha = 0.6f)
                            isFocused -> secondaryColor
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                if (isFocused) {
                    Button(
                        onClick = onSavePathClick,
                        modifier = Modifier.height(Dimens.iconLg - Dimens.spacingXs),
                        contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = Dimens.elevationNone),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (savesSubFocused) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                            },
                            contentColor = if (savesSubFocused) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    ) {
                        Text(text = "Change", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
