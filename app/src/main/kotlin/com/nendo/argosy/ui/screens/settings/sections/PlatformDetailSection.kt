package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import com.nendo.argosy.data.local.entity.getDisplayName
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.PlatformDetailState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.EmulatorPickerPopup
import com.nendo.argosy.ui.screens.settings.components.SavePathModal
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.components.VariantPickerModal
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.NetplaySupportLevel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion

// -- Item definitions --

internal sealed class PlatformDetailItem(
    val key: String,
    val section: String,
    val visibleWhen: (PlatformDetailVisibility) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header && this !is InfoItem

    class Header(key: String, section: String, val title: String, visibleWhen: (PlatformDetailVisibility) -> Boolean = { true }) : PlatformDetailItem(key, section, visibleWhen)
    class InfoItem(key: String, section: String, visibleWhen: (PlatformDetailVisibility) -> Boolean = { true }) : PlatformDetailItem(key, section, visibleWhen)

    data object Emulator : PlatformDetailItem("emulator", "emulator")
    data object Core : PlatformDetailItem("core", "emulator", { it.showCore })
    data object Extension : PlatformDetailItem("extension", "emulator", { it.showExtension })
    data object DisplayTarget : PlatformDetailItem("display_target", "emulator", { it.showDisplayTarget })
    data object LegacyMode : PlatformDetailItem("legacy_mode", "emulator", { it.showLegacyMode })
    data object LaunchArgs : PlatformDetailItem("launch_args", "emulator", { !it.isBuiltin })
    data object BuiltinVideo : PlatformDetailItem("builtin_video", "emulator", { it.isBuiltin })
    data object BuiltinControls : PlatformDetailItem("builtin_controls", "emulator", { it.isBuiltin })
    data object BuiltinCoreOptions : PlatformDetailItem("builtin_core_options", "emulator", { it.isBuiltin })

    data object ScanFiles : PlatformDetailItem("scan_files", "platform")

    data object RomPath : PlatformDetailItem("rom_path", "sync")
    data object SavePath : PlatformDetailItem("save_path", "sync", { it.showSavePath })
    data object MemoryCard : PlatformDetailItem("memory_card", "sync", { it.isPs2 && it.showSavePath })
    data object StatePath : PlatformDetailItem("state_path", "sync", { it.showStatePath })

    data object SyncToggle : PlatformDetailItem("sync_toggle", "sync")
    data object SyncNow : PlatformDetailItem("sync_now", "sync", { it.syncEnabled })
    data object PackagePath : PlatformDetailItem("package_path", "sync", { it.showSavePath })
    data object RemoveFiles : PlatformDetailItem("remove_files", "sync", { it.hasDownloads })

    data object BiosStatus : PlatformDetailItem("bios_status", "bios", { it.hasBios })
    data object BiosDownload : PlatformDetailItem("bios_download", "bios", { it.hasBios && it.biosMissing })
    data object BiosInstall : PlatformDetailItem("bios_install", "bios", { it.hasBios && it.biosDownloaded && it.canDistribute })
    data object BiosCopy : PlatformDetailItem("bios_copy", "bios", { it.hasBios && it.biosDownloaded })

    companion object {
        val ALL: List<PlatformDetailItem> = listOf(
            Header("header_emulator", "emulator", "Emulator"),
            Emulator, Core, Extension, DisplayTarget, LegacyMode, LaunchArgs, BuiltinVideo, BuiltinControls, BuiltinCoreOptions,
            Header("header_platform", "platform", "Platform"),
            InfoItem("info_platform_stats", "platform"),
            ScanFiles,
            Header("header_bios", "bios", "BIOS", { it.hasBios }),
            InfoItem("info_bios_status", "bios", { it.hasBios }), BiosDownload, BiosInstall, BiosCopy,
            Header("header_sync", "sync", "Storage & Sync"),
            SyncToggle, SyncNow, InfoItem("info_package_path", "sync", { it.showSavePath && !it.isBuiltin && !it.isRetroArch }),
            RomPath, SavePath, MemoryCard, StatePath,
            RemoveFiles
        )
    }
}

internal data class PlatformDetailVisibility(
    val showCore: Boolean = false,
    val showExtension: Boolean = false,
    val showDisplayTarget: Boolean = false,
    val showLegacyMode: Boolean = false,
    /** In-app libretro host. Does NOT include external RetroArch -- those are separate emulators. */
    val isBuiltin: Boolean = false,
    /** External RetroArch app. Config lives in retroarch.cfg, not Argosy's storage. */
    val isRetroArch: Boolean = false,
    val showSavePath: Boolean = false,
    val showStatePath: Boolean = false,
    val hasDownloads: Boolean = false,
    val syncEnabled: Boolean = true,
    val hasBios: Boolean = false,
    val biosMissing: Boolean = false,
    val biosDownloaded: Boolean = false,
    val canDistribute: Boolean = false,
    val isPs2: Boolean = false
) {
    companion object {
        fun from(
            config: PlatformEmulatorConfig,
            detail: PlatformDetailState,
            syncEnabled: Boolean
        ) = PlatformDetailVisibility(
            showCore = config.showCoreSelection,
            showExtension = config.showExtensionSelection,
            showDisplayTarget = config.showDisplayTargetOption,
            showLegacyMode = config.showLegacyModeOption,
            isBuiltin = config.effectiveEmulatorId == "builtin",
            isRetroArch = config.effectiveEmulatorIsRetroArch,
            showSavePath = config.showSavePath,
            showStatePath = detail.supportsStatePath,
            hasDownloads = detail.downloadedGames > 0,
            syncEnabled = syncEnabled,
            hasBios = detail.hasBiosRequirements,
            biosMissing = detail.biosDownloaded < detail.biosTotal,
            biosDownloaded = detail.biosDownloaded > 0,
            canDistribute = com.nendo.argosy.data.emulator.BiosPathRegistry
                .getEmulatorsForPlatform(config.platform.slug).isNotEmpty(),
            isPs2 = config.platform.slug == "ps2"
        )
    }
}

private fun createPlatformDetailLayout() = SettingsLayout<PlatformDetailItem, PlatformDetailVisibility>(
    allItems = PlatformDetailItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "emulator" -> "Emulator"
            "platform" -> "Platform"
            "bios" -> "BIOS"
            "sync" -> "Storage & Sync"
            else -> null
        }
    }
)

internal fun platformDetailMaxFocusIndex(state: SettingsUiState): Int {
    val config = state.emulators.platforms.getOrNull(state.platformDetail.platformIndex) ?: return 0
    val syncEnabled = state.storage.platformConfigs
        .find { it.platformId == config.platform.id }?.syncEnabled ?: true
    val visibility = PlatformDetailVisibility.from(config, state.platformDetail, syncEnabled)
    val layout = createPlatformDetailLayout()
    return layout.maxFocusIndex(visibility)
}

internal fun platformDetailItemAtFocusIndex(
    focusIndex: Int,
    config: PlatformEmulatorConfig,
    detail: PlatformDetailState,
    syncEnabled: Boolean
): PlatformDetailItem? {
    val visibility = PlatformDetailVisibility.from(config, detail, syncEnabled)
    val layout = createPlatformDetailLayout()
    return layout.itemAtFocusIndex(focusIndex, visibility)
}

internal fun platformDetailSections(
    config: PlatformEmulatorConfig,
    detail: PlatformDetailState,
    syncEnabled: Boolean
) = createPlatformDetailLayout().buildSections(PlatformDetailVisibility.from(config, detail, syncEnabled))

// -- Composable --

@Composable
fun PlatformDetailSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onLaunchSavePathPicker: () -> Unit = {}
) {
    val detail = uiState.platformDetail
    val config = uiState.emulators.platforms.getOrNull(detail.platformIndex)
    val emulators = uiState.emulators

    if (config == null) {
        Text("No platform selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val storageConfig = uiState.storage.platformConfigs.find { it.platformId == config.platform.id }
    val syncEnabled = storageConfig?.syncEnabled ?: true

    val visibility = remember(config, detail, syncEnabled) {
        PlatformDetailVisibility.from(config, detail, syncEnabled)
    }
    val layout = remember { createPlatformDetailLayout() }
    val visibleItems = remember(visibility) { layout.visibleItems(visibility) }
    val sections = remember(visibility) { layout.buildSections(visibility) }

    fun isFocused(item: PlatformDetailItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, visibility)

    val modalBlur by animateDpAsState(
        targetValue = if (emulators.showEmulatorPicker || emulators.showSavePathModal || emulators.showVariantPicker || emulators.updateModal != null || emulators.showLaunchArgsModal || emulators.showAppPickerModal || emulators.showMemcardPicker) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "platformDetailBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { layout.focusToListIndex(it, visibility) },
            itemKey = { it.key },
            isNavItem = { false },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
            when (item) {
                is PlatformDetailItem.Header -> com.nendo.argosy.ui.screens.settings.components.SectionHeader(item.title)

                // -- EMULATOR section --
                PlatformDetailItem.Emulator -> {
                    val hasUpdate = config.effectiveEmulatorId != null &&
                        config.effectiveEmulatorId in emulators.emulatorUpdateVersions
                    val hasInstallableKnown = config.availableEmulators.isNotEmpty() ||
                        config.downloadableEmulators.isNotEmpty()
                    CyclePreference(
                        title = if (hasInstallableKnown) "Change Emulator" else "Select App",
                        value = config.effectiveEmulatorName ?: "Not installed",
                        subtitle = if (hasUpdate) "Update available" else null,
                        isFocused = isFocused(item),
                        onClick = {
                            if (hasInstallableKnown) {
                                viewModel.showEmulatorPicker(config)
                            } else {
                                viewModel.openAppPickerModal(config.platform.id)
                            }
                        }
                    )
                }
                PlatformDetailItem.Core -> {
                    val platformHasNetplay = config.effectiveEmulatorId == "builtin" &&
                        LibretroCoreRegistry.getCoresForPlatform(config.platform.slug)
                            .any { it.netplaySupport == NetplaySupportLevel.SUPPORTED }
                    val activeCoreId = config.selectedCore
                        ?: LibretroCoreRegistry.getDefaultCoreForPlatform(config.platform.slug)?.coreId
                    val activeNetplay = platformHasNetplay &&
                        activeCoreId != null &&
                        LibretroCoreRegistry.getCoreById(activeCoreId)?.netplaySupport == NetplaySupportLevel.SUPPORTED

                    CyclePreference(
                        title = "Core",
                        value = config.selectedCore ?: "Default",
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleCoreForPlatform(config, 1) },
                        valueFooter = if (platformHasNetplay) {
                            {
                                CoreTag(
                                    text = "Netplay",
                                    color = if (activeNetplay) {
                                        LocalLauncherTheme.current.semanticColors.success
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    }
                                )
                            }
                        } else null
                    )
                }
                PlatformDetailItem.Extension -> CyclePreference(
                    title = "File Extension",
                    value = config.selectedExtension ?: "Default",
                    isFocused = isFocused(item),
                    onClick = {
                        val options = config.extensionOptions
                        val currentIdx = options.indexOfFirst { it.extension == config.selectedExtension }
                        val nextIdx = (currentIdx + 1).mod(options.size)
                        viewModel.changeExtensionForPlatform(config, options[nextIdx].extension)
                    }
                )
                PlatformDetailItem.DisplayTarget -> CyclePreference(
                    title = "Display Target",
                    value = config.displayTarget.name,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleDisplayTarget(config, 1) }
                )
                PlatformDetailItem.LegacyMode -> SwitchPreference(
                    title = "Legacy Mode",
                    subtitle = "Use file:// URI for Drastic",
                    isEnabled = config.useFileUri,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleLegacyMode(config) }
                )
                PlatformDetailItem.LaunchArgs -> NavigationPreference(
                    icon = Icons.Default.Settings,
                    title = "Launch Args",
                    subtitle = "Customize how Argosy launches this emulator",
                    isFocused = isFocused(item),
                    onClick = { viewModel.openLaunchArgsModal(config.platform.id) }
                )
                PlatformDetailItem.BuiltinVideo -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = "Built-in A/V & Performance",
                    subtitle = "Display, performance, and saving overrides",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToBuiltinVideoForPlatform(detail.platformIndex) }
                )
                PlatformDetailItem.BuiltinControls -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = "Built-in Controls",
                    subtitle = "Rumble, stick mapping overrides",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToBuiltinControlsForPlatform(detail.platformIndex) }
                )
                PlatformDetailItem.BuiltinCoreOptions -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = "Core Options",
                    subtitle = "Per-core settings for this platform",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToCoreOptionsForPlatform() }
                )

                // -- PLATFORM section (info items + scan) --
                is PlatformDetailItem.InfoItem -> {
                    when (item.key) {
                        "info_platform_stats" -> {
                            val syncProgress = viewModel.librarySyncProgress.collectAsState().value
                            val isSyncingThis = syncProgress.isSyncing && syncProgress.currentPlatform == config.platform.name
                            val textColor = MaterialTheme.colorScheme.onSurfaceVariant
                            val valueColor = MaterialTheme.colorScheme.onSurface
                            val style = MaterialTheme.typography.bodySmall

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
                                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                            ) {
                                StatRow("Games in Library", "${detail.totalGames}", textColor, valueColor, style)
                                StatRow("Downloaded", "${detail.downloadedGames}", textColor, valueColor, style)
                                if (detail.favorites > 0) {
                                    StatRow("Favorites", "${detail.favorites}", textColor, valueColor, style)
                                }
                                StatRow("Play Time", detail.playTimeFormatted, textColor, valueColor, style)
                                if (isSyncingThis) {
                                    val syncText = if (syncProgress.gamesTotal > 0) {
                                        "Syncing ${syncProgress.gamesDone}/${syncProgress.gamesTotal}"
                                    } else "Syncing..."
                                    StatRow("Sync", syncText, textColor, MaterialTheme.colorScheme.primary, style)
                                }
                            }
                        }
                        "info_package_path" -> {
                            val status = when (detail.packagePathAccessible) {
                                true -> "Accessible"
                                false -> "Blocked"
                                null -> "Checking..."
                            }
                            InfoPreference(title = "Package Path", value = status, isFocused = false)
                        }
                        "info_bios_status" -> {
                            val status = when {
                                detail.biosDownloaded >= detail.biosTotal -> "All installed"
                                detail.biosDownloaded > 0 -> "${detail.biosDownloaded} of ${detail.biosTotal} downloaded"
                                else -> "Not downloaded"
                            }
                            InfoPreference(title = "Status", value = status, isFocused = false)
                        }
                    }
                }
                PlatformDetailItem.ScanFiles -> ActionPreference(
                    title = if (detail.isScanning) "Scanning..." else "Scan for Files",
                    subtitle = "Check for new or missing ROM files",
                    isFocused = isFocused(item),
                    icon = Icons.Default.Search,
                    isEnabled = !detail.isScanning,
                    onClick = { viewModel.scanFilesForPlatform(config.platform.id) }
                )


                // -- PATHS section --
                PlatformDetailItem.RomPath -> CyclePreference(
                    title = "ROM Path",
                    value = formatPath(storageConfig?.effectivePath),
                    subtitle = if (storageConfig?.customRomPath != null) "(custom)" else null,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openPlatformFolderPicker(config.platform.id) },
                    showResetButton = storageConfig?.customRomPath != null,
                    onReset = { viewModel.resetPlatformRomPath(config.platform.id) }
                )
                PlatformDetailItem.SavePath -> {
                    if (config.effectiveEmulatorIsRetroArch) {
                        InfoPreference(
                            title = "Save Path",
                            value = formatPath(storageConfig?.effectiveSavePath),
                            isFocused = isFocused(item),
                            subtitle = if (storageConfig?.effectiveSavePath == "(ROM directory)") {
                                "content dir (from retroarch.cfg)"
                            } else {
                                "from retroarch.cfg"
                            }
                        )
                    } else {
                        val isBuiltinEmulator = config.effectiveEmulatorId == "builtin"
                        CyclePreference(
                            title = "Save Path",
                            value = formatPath(storageConfig?.effectiveSavePath),
                            subtitle = when {
                                !isBuiltinEmulator && detail.packagePathAccessible == false && storageConfig?.isUserSavePathOverride != true -> "Access blocked -- set a custom save path"
                                storageConfig?.isUserSavePathOverride == true -> "(custom)"
                                else -> null
                            },
                            isFocused = isFocused(item),
                            onClick = { viewModel.launchSavePathPicker(config.platform.id) },
                            showResetButton = storageConfig?.isUserSavePathOverride == true,
                            onReset = { viewModel.resetPlatformSavePath(config.platform.id) }
                        )
                    }
                }
                PlatformDetailItem.MemoryCard -> {
                    val cardCount = storageConfig?.folderMemcardCount ?: -1
                    val selected = storageConfig?.selectedMemcardPath
                    val selectedName = selected?.let { java.io.File(it).name }
                    val emulatorName = config.effectiveEmulatorName ?: "this emulator"
                    val isOverridingSavePath = storageConfig?.isUserSavePathOverride == true
                    val (value, subtitle) = when {
                        isOverridingSavePath ->
                            "Using save-path override" to "Memory card pinned via custom save path"
                        cardCount <= 0 ->
                            "None found" to "No folder memory cards found -- convert one in $emulatorName to enable save sync"
                        selected != null ->
                            (selectedName ?: selected) to "(custom)"
                        cardCount == 1 ->
                            "Auto" to null
                        else ->
                            "Not selected" to "Multiple memory cards detected -- select one to enable save sync"
                    }
                    CyclePreference(
                        title = "Memory Card",
                        value = value,
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        onClick = { viewModel.openMemcardPicker(config) },
                        showResetButton = selected != null && !isOverridingSavePath,
                        onReset = {
                            config.effectiveEmulatorId?.let { viewModel.resetMemcardSelection(it) }
                        }
                    )
                }
                PlatformDetailItem.StatePath -> {
                    if (config.effectiveEmulatorIsRetroArch) {
                        InfoPreference(
                            title = "State Path",
                            value = formatPath(storageConfig?.effectiveStatePath),
                            isFocused = isFocused(item),
                            subtitle = if (storageConfig?.effectiveStatePath == "(ROM directory)") {
                                "content dir (from retroarch.cfg)"
                            } else {
                                "from retroarch.cfg"
                            }
                        )
                    } else {
                        CyclePreference(
                            title = "State Path",
                            value = formatPath(storageConfig?.effectiveStatePath),
                            subtitle = if (storageConfig?.isUserStatePathOverride == true) "(custom)" else null,
                            isFocused = isFocused(item),
                            onClick = { viewModel.launchStatePathPicker(config.platform.id) },
                            showResetButton = storageConfig?.isUserStatePathOverride == true,
                            onReset = { viewModel.resetPlatformStatePath(config.platform.id) }
                        )
                    }
                }

                // -- SYNC section --
                PlatformDetailItem.SyncToggle -> SwitchPreference(
                    title = "Sync Enabled",
                    subtitle = "Include this platform in library sync",
                    isEnabled = storageConfig?.syncEnabled ?: true,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.togglePlatformSync(config.platform.id, it) }
                )
                PlatformDetailItem.SyncNow -> {
                    val isBusy = config.platform.id in uiState.storage.busyPlatformIds ||
                        uiState.storage.isLibrarySyncing
                    ActionPreference(
                        title = "Sync Now",
                        subtitle = if (isBusy) "Sync in progress..." else "Sync this platform with RomM",
                        isFocused = isFocused(item),
                        icon = Icons.Default.Sync,
                        spinIcon = isBusy,
                        isEnabled = !isBusy,
                        onClick = { viewModel.syncPlatform(config.platform.id, config.platform.getDisplayName()) }
                    )
                }
                PlatformDetailItem.PackagePath -> {} // rendered as InfoItem
                PlatformDetailItem.RemoveFiles -> ActionPreference(
                    title = "Remove Local Files",
                    subtitle = "${detail.downloadedGames} downloaded files",
                    isFocused = isFocused(item),
                    isDangerous = true,
                    onClick = { viewModel.requestRemoveLocalFiles() }
                )

                // -- BIOS section --
                PlatformDetailItem.BiosStatus -> {} // rendered as InfoItem
                PlatformDetailItem.BiosDownload -> ActionPreference(
                    title = "Download All",
                    subtitle = "${detail.biosTotal - detail.biosDownloaded} files",
                    isFocused = isFocused(item),
                    icon = Icons.Default.Download,
                    onClick = { viewModel.downloadBiosForPlatform(config.platform.slug) }
                )
                PlatformDetailItem.BiosInstall -> ActionPreference(
                    title = "Install to Emulator",
                    subtitle = "Copy BIOS to ${config.effectiveEmulatorName ?: "emulator"}",
                    isFocused = isFocused(item),
                    onClick = { viewModel.distributeBiosForPlatformWithNotification(config.platform.slug) }
                )
                PlatformDetailItem.BiosCopy -> ActionPreference(
                    title = "Copy to...",
                    subtitle = "Copy BIOS files to another folder",
                    isFocused = isFocused(item),
                    icon = Icons.Default.ContentCopy,
                    onClick = { viewModel.launchBiosCopyPicker(config.platform.slug) }
                )
            }
        }

        // Remove files confirmation
        if (detail.showRemoveConfirm) {
            com.nendo.argosy.ui.components.CenteredModal(
                title = "Remove Local Files",
                onDismiss = { viewModel.dismissRemoveConfirm() }
            ) {
                Text(
                    text = "Delete ${detail.downloadedGames} ROM file${if (detail.downloadedGames > 1) "s" else ""} for ${config.platform.name}? Games will remain in your library but must be re-downloaded to play.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { viewModel.dismissRemoveConfirm() }
                    ) { Text("Cancel") }
                    androidx.compose.material3.Button(
                        onClick = { viewModel.confirmRemoveLocalFiles(config.platform.id) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete") }
                }
            }
        }

        // Modals
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
                onResetSavePath = { viewModel.resetEmulatorSavePath(emulators.savePathModalInfo.emulatorId) }
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

        if (emulators.updateModal != null) {
            com.nendo.argosy.ui.screens.settings.components.EmulatorUpdateModal(
                modal = emulators.updateModal,
                focusIndex = emulators.updateModalFocusIndex,
                onVariantTap = { index -> viewModel.moveUpdateModalFocus(index - emulators.updateModalFocusIndex) },
                onConfirmVariant = { viewModel.selectUpdateModalVariant() },
                onDismiss = { viewModel.dismissUpdateModal() }
            )
        }

        if (emulators.showLaunchArgsModal && emulators.launchArgsModalState != null) {
            com.nendo.argosy.ui.screens.settings.components.LaunchArgsModal(
                state = emulators.launchArgsModalState,
                onCycleDataBinding = { viewModel.cycleLaunchArgsDataBinding() },
                onCycleExtraBinding = { viewModel.cycleLaunchArgsExtraBinding() },
                onCycleClipDataBinding = { viewModel.cycleLaunchArgsClipDataBinding() },
                onToggleFlag = { bit -> viewModel.toggleLaunchArgsFlag(bit) },
                onCycleMimeType = { viewModel.cycleLaunchArgsMimeType() },
                onDismiss = { viewModel.closeLaunchArgsModal() }
            )
        }

        if (emulators.showAppPickerModal && emulators.appPickerModalState != null) {
            com.nendo.argosy.ui.screens.settings.components.AppPickerModal(
                state = emulators.appPickerModalState,
                onItemTap = { index ->
                    viewModel.moveAppPickerFocus(index - emulators.appPickerModalState.focusIndex)
                },
                onConfirm = { viewModel.confirmAppPickerSelection() },
                onDismiss = { viewModel.closeAppPickerModal() }
            )
        }

        if (emulators.showMemcardPicker && emulators.memcardPickerInfo != null) {
            com.nendo.argosy.ui.components.MemcardPickerModal(
                cards = emulators.memcardPickerInfo.cards,
                focusIndex = emulators.memcardPickerFocusIndex,
                selectedCardPath = emulators.memcardPickerInfo.selectedCardPath,
                onSelectCard = { path -> viewModel.confirmMemcardSelection(path) },
                onDismiss = { viewModel.dismissMemcardPicker() }
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    style: TextStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = style, color = labelColor)
        Text(text = value, style = style, color = valueColor)
    }
}

internal fun formatPath(path: String?): String {
    if (path == null) return "Not configured"
    val maxLen = 40
    return if (path.length > maxLen) "...${path.takeLast(maxLen)}" else path
}

// Keep old function name for confirm router compatibility
internal fun buildPlatformDetailFocusItems(
    config: PlatformEmulatorConfig,
    detail: PlatformDetailState,
    syncEnabled: Boolean
): List<PlatformDetailItem> {
    val visibility = PlatformDetailVisibility.from(config, detail, syncEnabled)
    val layout = createPlatformDetailLayout()
    return layout.focusableItems(visibility)
}
