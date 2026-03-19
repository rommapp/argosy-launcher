package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.EmulatorPickerPopup
import com.nendo.argosy.ui.screens.settings.components.SavePathModal
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.VariantPickerModal
import com.nendo.argosy.ui.theme.Dimens
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ContentCopy

@Composable
fun PlatformDetailSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onLaunchSavePathPicker: () -> Unit = {}
) {
    val detail = uiState.platformDetail
    val config = uiState.emulators.platforms.getOrNull(detail.platformIndex)

    if (config == null) {
        Text("No platform selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val listState = rememberLazyListState()
    val focusItems = buildPlatformDetailFocusItems(config, detail)

    val emulators = uiState.emulators

    FocusedScroll(listState = listState, focusedIndex = uiState.focusedIndex)

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd)
    ) {
        // -- EMULATOR section --
        item(key = "header_emulator") {
            SectionHeader(title = "Emulator")
        }
        item(key = "emulator_select") {
            val idx = focusItems.indexOf(PlatformDetailItem.EMULATOR)
            CyclePreference(
                title = "Emulator",
                value = config.effectiveEmulatorName ?: "Not installed",
                isFocused = uiState.focusedIndex == idx,
                onClick = { viewModel.showEmulatorPicker(config) }
            )
        }
        if (config.showCoreSelection) {
            item(key = "core_select") {
                val idx = focusItems.indexOf(PlatformDetailItem.CORE)
                CyclePreference(
                    title = "Core",
                    value = config.selectedCore ?: "Default",
                    isFocused = uiState.focusedIndex == idx,
                    onClick = { viewModel.cycleCoreForPlatform(config, 1) }
                )
            }
        }
        if (config.showExtensionSelection) {
            item(key = "extension_select") {
                val idx = focusItems.indexOf(PlatformDetailItem.EXTENSION)
                CyclePreference(
                    title = "File Extension",
                    value = config.selectedExtension ?: "Default",
                    isFocused = uiState.focusedIndex == idx,
                    onClick = {
                        val options = config.extensionOptions
                        val currentIdx = options.indexOfFirst { it.extension == config.selectedExtension }
                        val nextIdx = (currentIdx + 1).mod(options.size)
                        viewModel.changeExtensionForPlatform(config, options[nextIdx].extension)
                    }
                )
            }
        }
        if (config.showDisplayTargetOption) {
            item(key = "display_target") {
                val idx = focusItems.indexOf(PlatformDetailItem.DISPLAY_TARGET)
                CyclePreference(
                    title = "Display Target",
                    value = config.displayTarget.name,
                    isFocused = uiState.focusedIndex == idx,
                    onClick = { viewModel.cycleDisplayTarget(config, 1) }
                )
            }
        }

        // -- PLATFORM section --
        item(key = "header_platform") {
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            SectionHeader(title = "Platform")
        }
        item(key = "stat_games") {
            InfoPreference(
                title = "Games in Library",
                value = "${detail.totalGames}",
                isFocused = false
            )
        }
        item(key = "stat_downloaded") {
            InfoPreference(
                title = "Downloaded",
                value = "${detail.downloadedGames}",
                isFocused = false
            )
        }
        item(key = "stat_playtime") {
            InfoPreference(
                title = "Total Play Time",
                value = detail.playTimeFormatted,
                isFocused = false
            )
        }
        item(key = "stat_favorites") {
            InfoPreference(
                title = "Favorites",
                value = "${detail.favorites}",
                isFocused = false
            )
        }

        // -- SAVE SYNC section --
        if (config.showSavePath) {
            item(key = "header_savesync") {
                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                SectionHeader(title = "Save Sync")
            }
            item(key = "package_path") {
                val status = when (detail.packagePathAccessible) {
                    true -> "Accessible"
                    false -> "Blocked"
                    null -> "Checking..."
                }
                InfoPreference(
                    title = "Package Path",
                    value = status,
                    isFocused = false
                )
            }
            item(key = "save_path") {
                val idx = focusItems.indexOf(PlatformDetailItem.SAVE_PATH)
                val pathDisplay = config.effectiveSavePath?.let { path ->
                    val maxLen = 40
                    if (path.length > maxLen) "...${path.takeLast(maxLen)}" else path
                } ?: "Not configured"
                val subtitle = when {
                    detail.packagePathAccessible == false && !config.isUserSavePathOverride ->
                        "Access blocked -- set a custom save path"
                    config.isUserSavePathOverride -> "(custom)"
                    else -> null
                }
                CyclePreference(
                    title = "Save Path",
                    value = pathDisplay,
                    subtitle = subtitle,
                    isFocused = uiState.focusedIndex == idx,
                    onClick = { viewModel.showSavePathModal(config) }
                )
            }
        }

        // -- BIOS section --
        if (detail.hasBiosRequirements) {
            item(key = "header_bios") {
                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                SectionHeader(title = "BIOS")
            }
            item(key = "bios_status") {
                val status = when {
                    detail.biosDownloaded >= detail.biosTotal -> "All installed"
                    detail.biosDownloaded > 0 -> "${detail.biosDownloaded} of ${detail.biosTotal} downloaded"
                    else -> "Not downloaded"
                }
                InfoPreference(
                    title = "Status",
                    value = status,
                    isFocused = false
                )
            }
            if (detail.biosDownloaded < detail.biosTotal) {
                item(key = "bios_download") {
                    val idx = focusItems.indexOf(PlatformDetailItem.BIOS_DOWNLOAD)
                    ActionPreference(
                        title = "Download All",
                        subtitle = "${detail.biosTotal - detail.biosDownloaded} files",
                        isFocused = uiState.focusedIndex == idx,
                        icon = Icons.Default.Download,
                        onClick = { viewModel.downloadBiosForPlatform(config.platform.slug) }
                    )
                }
            }
            if (detail.biosDownloaded > 0) {
                item(key = "bios_copy") {
                    val idx = focusItems.indexOf(PlatformDetailItem.BIOS_COPY)
                    ActionPreference(
                        title = "Copy to...",
                        subtitle = "Copy BIOS files to another folder",
                        isFocused = uiState.focusedIndex == idx,
                        icon = Icons.Default.ContentCopy,
                        onClick = { viewModel.launchBiosCopyPicker(config.platform.slug) }
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

    if (emulators.showVariantPicker && emulators.variantPickerInfo != null) {
        VariantPickerModal(
            info = emulators.variantPickerInfo,
            focusIndex = emulators.variantPickerFocusIndex,
            onItemTap = { index -> viewModel.handleVariantPickerItemTap(index) },
            onConfirm = { viewModel.confirmVariantSelection() },
            onDismiss = { viewModel.dismissVariantPicker() }
        )
    }
    } // Box
}

internal enum class PlatformDetailItem {
    EMULATOR, CORE, EXTENSION, DISPLAY_TARGET, LEGACY_MODE,
    SAVE_PATH,
    BIOS_DOWNLOAD, BIOS_COPY
}

internal fun buildPlatformDetailFocusItems(
    config: com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig,
    detail: com.nendo.argosy.ui.screens.settings.PlatformDetailState
): List<PlatformDetailItem> = buildList {
    add(PlatformDetailItem.EMULATOR)
    if (config.showCoreSelection) add(PlatformDetailItem.CORE)
    if (config.showExtensionSelection) add(PlatformDetailItem.EXTENSION)
    if (config.showDisplayTargetOption) add(PlatformDetailItem.DISPLAY_TARGET)
    if (config.showLegacyModeOption) add(PlatformDetailItem.LEGACY_MODE)
    if (config.showSavePath) add(PlatformDetailItem.SAVE_PATH)
    if (detail.hasBiosRequirements && detail.biosDownloaded < detail.biosTotal) add(PlatformDetailItem.BIOS_DOWNLOAD)
    if (detail.hasBiosRequirements && detail.biosDownloaded > 0) add(PlatformDetailItem.BIOS_COPY)
}

internal fun platformDetailMaxFocusIndex(state: SettingsUiState): Int {
    val config = state.emulators.platforms.getOrNull(state.platformDetail.platformIndex) ?: return 0
    return (buildPlatformDetailFocusItems(config, state.platformDetail).size - 1).coerceAtLeast(0)
}
