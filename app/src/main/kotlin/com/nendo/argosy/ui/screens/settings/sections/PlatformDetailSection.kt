package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PackageDataAccess
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.ui.common.labelRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.itemsIndexed
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.PlatformDetailState
import com.nendo.argosy.ui.screens.settings.RetroArchConfigStatus
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.EmulatorPickerPopup
import com.nendo.argosy.ui.screens.settings.components.SavePathModal
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.components.VariantPickerModal
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.NetplaySupportLevel
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.animateModalBlur
import com.nendo.argosy.ui.theme.modalScrim
import com.nendo.argosy.ui.theme.generated.ColorTokens

// -- Item definitions --

internal sealed class PlatformDetailItem(
    val key: String,
    val section: String,
    val visibleWhen: (PlatformDetailVisibility) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int, visibleWhen: (PlatformDetailVisibility) -> Boolean = { true }) : PlatformDetailItem(key, section, visibleWhen)
    class InfoItem(key: String, section: String, visibleWhen: (PlatformDetailVisibility) -> Boolean = { true }) : PlatformDetailItem(key, section, visibleWhen)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (PlatformDetailVisibility) -> Boolean = { true }
    ) : PlatformDetailItem(key, section, visibleWhen), SearchableSetting

    data object Emulator : Row("emulator", "emulator", R.string.settings_search_label_platform_emulator, { !it.isAndroid }) {
        override val keywordsRes: Int? get() = R.string.settings_platform_emulator_change
    }
    data object Core : Row("core", "emulator", R.string.settings_platform_core_title, { it.showCore && !it.isAndroid })
    data object Extension :
        Row("extension", "emulator", R.string.settings_platform_extension_title, { it.showExtension && !it.isAndroid })
    data object DisplayTarget : Row(
        "display_target",
        "emulator",
        R.string.settings_platform_display_target_title,
        { it.showDisplayTarget && !it.isAndroid }
    )
    data object LegacyMode :
        Row("legacy_mode", "emulator", R.string.settings_platform_legacy_mode_title, { it.showLegacyMode && !it.isAndroid })
    data object LaunchArgs :
        Row("launch_args", "emulator", R.string.settings_platform_launch_args_title, { !it.isBuiltin && !it.isAndroid })
    data object BuiltinVideo :
        Row("builtin_video", "emulator", R.string.settings_platform_builtin_video_title, { it.isBuiltin && !it.isAndroid })
    data object BuiltinControls : Row(
        "builtin_controls",
        "emulator",
        R.string.settings_platform_builtin_controls_title,
        { it.isBuiltin && !it.isAndroid }
    )
    data object BuiltinSaves :
        Row("builtin_saves", "emulator", R.string.settings_platform_builtin_saves_title, { it.isBuiltin && !it.isAndroid })
    data object BuiltinCoreOptions : Row(
        "builtin_core_options",
        "emulator",
        R.string.settings_platform_core_options_title,
        { it.isBuiltin && !it.isAndroid }
    )

    data object ClearArtCache : Row("clear_art_cache", "platform", R.string.settings_platform_clear_art_title)
    data object ScanFiles : Row("scan_files", "platform", R.string.settings_platform_scan_files_title, { !it.isAndroid })
    data object ScanApps : Row("scan_apps", "platform", R.string.settings_platform_scan_apps_title, { it.isAndroid })

    data object RomPath : Row("rom_path", "sync", R.string.settings_platform_rom_path_title, { !it.isAndroid })
    data object SavePath :
        Row("save_path", "sync", R.string.settings_platform_save_path_title, { it.showSavePath && !it.isAndroid })
    data object MemoryCard :
        Row("memory_card", "sync", R.string.settings_platform_memcard_title, { it.showMemoryCard && !it.isAndroid })
    data object StatePath :
        Row("state_path", "sync", R.string.settings_platform_state_path_title, { it.showStatePath && !it.isAndroid })

    data object SyncToggle : Row("sync_toggle", "sync", R.string.settings_platform_sync_toggle_title)
    data object CombineContent :
        Row("combine_content", "sync", R.string.settings_platform_combine_content_title, { it.showCombineContent })
    data object SyncNow : Row("sync_now", "sync", R.string.settings_platform_sync_now_title, { it.syncEnabled })
    data object SkipFullSync :
        Row("skip_full_sync", "sync", R.string.settings_platform_skip_full_sync_title, { it.syncEnabled })
    data object DownloadDefaults :
        Row("download_defaults", "sync", R.string.settings_platform_download_defaults_title, { !it.isAndroid })
    data object PackagePath :
        Row("package_path", "sync", R.string.settings_platform_package_path_title, { it.showSavePath && !it.isAndroid })
    data object RemoveFiles :
        Row("remove_files", "sync", R.string.settings_platform_remove_files_title, { it.hasDownloads && !it.isAndroid })

    data object BiosStatus :
        Row("bios_status", "bios", R.string.settings_platform_bios_status_title, { it.hasBios && !it.isAndroid })
    data object BiosDownload : Row(
        "bios_download",
        "bios",
        R.string.settings_platform_bios_download_title,
        { it.hasBios && it.biosMissing && !it.isAndroid }
    )
    data object BiosInstall : Row(
        "bios_install",
        "bios",
        R.string.settings_platform_bios_install_title,
        { it.hasBios && it.biosDownloaded && it.canDistribute && !it.isAndroid }
    )
    data object BiosCopy : Row(
        "bios_copy",
        "bios",
        R.string.settings_platform_bios_copy_title,
        { it.hasBios && it.biosDownloaded && !it.isAndroid }
    )

    companion object {
        val ALL: List<PlatformDetailItem>
            get() = listOf(
                Header("header_emulator", "emulator", R.string.settings_platform_section_emulator, { !it.isAndroid }),
                Emulator, Core, Extension, DisplayTarget, LegacyMode, LaunchArgs, BuiltinVideo, BuiltinControls, BuiltinSaves, BuiltinCoreOptions,
                Header("header_platform", "platform", R.string.settings_platform_section_platform),
                InfoItem("info_platform_stats", "platform"),
                ScanFiles, ScanApps, ClearArtCache,
                Header("header_bios", "bios", R.string.settings_platform_section_bios, { it.hasBios && !it.isAndroid }),
                InfoItem("info_bios_status", "bios", { it.hasBios && !it.isAndroid }), BiosDownload, BiosInstall, BiosCopy,
                Header("header_sync", "sync", R.string.settings_platform_section_sync),
                SyncToggle, SyncNow, SkipFullSync, DownloadDefaults, CombineContent,
                InfoItem("info_package_path", "sync", { it.showSavePath && !it.isBuiltin && !it.isRetroArch }),
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
    val showMemoryCard: Boolean = false,
    val showCombineContent: Boolean = false,
    /**
     * Installed apps, not rom files. Nothing on this platform is downloaded, emulated or stored
     * by us, so the emulator, BIOS and storage rows have nothing to act on and an emulator
     * chosen here would never be read - [com.nendo.argosy.data.emulator.GameLauncher] sends
     * Android games straight to their package.
     */
    val isAndroid: Boolean = false
) {
    companion object {
        fun from(
            config: PlatformEmulatorConfig,
            detail: PlatformDetailState,
            syncEnabled: Boolean,
            memcardCount: Int
        ) = PlatformDetailVisibility(
            showCore = config.showCoreSelection,
            showExtension = config.showExtensionSelection,
            showDisplayTarget = config.showDisplayTargetOption,
            showLegacyMode = config.showLegacyModeOption,
            isBuiltin = config.effectiveEmulatorId == EmulatorRegistry.BUILTIN_ID,
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
            showMemoryCard = com.nendo.argosy.data.emulator.EmulatorSettingScope.showsMemoryCard(
                config.platform.slug,
                memcardCount
            ),
            isAndroid = config.platform.id == com.nendo.argosy.data.platform.LocalPlatformIds.ANDROID,
            showCombineContent = com.nendo.argosy.data.download.ZipExtractor
                .isNswPlatform(config.platform.slug) &&
                com.nendo.argosy.data.emulator.EmulatorRegistry
                    .familyBaseIdFor(config.effectiveEmulatorId ?: "") == "eden"
        )
    }
}

internal fun createPlatformDetailLayout() = SettingsLayout<PlatformDetailItem, PlatformDetailVisibility>(
    allItems = PlatformDetailItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "emulator" -> R.string.settings_platform_section_emulator
            "platform" -> R.string.settings_platform_section_platform
            "bios" -> R.string.settings_platform_section_bios
            "sync" -> R.string.settings_platform_section_sync
            "downloads" -> R.string.settings_platform_section_downloads
            else -> null
        }
    }
)

internal fun platformDetailVisibilityOf(
    state: SettingsUiState,
    config: PlatformEmulatorConfig,
    detail: PlatformDetailState
): PlatformDetailVisibility {
    val storageConfig = state.storage.platformConfigs.find { it.platformId == config.platform.id }
    return PlatformDetailVisibility.from(
        config, detail, storageConfig?.syncEnabled ?: true, storageConfig?.folderMemcardCount ?: -1
    )
}

internal fun platformDetailMaxFocusIndex(state: SettingsUiState): Int {
    val config = state.emulators.platforms.getOrNull(state.platformDetail.platformIndex) ?: return 0
    return createPlatformDetailLayout().maxFocusIndex(platformDetailVisibilityOf(state, config, state.platformDetail))
}

internal fun platformDetailFocusIndexOf(key: String, state: SettingsUiState): Int {
    val config = state.emulators.platforms.getOrNull(state.platformDetail.platformIndex) ?: return -1
    return createPlatformDetailLayout()
        .focusableItems(platformDetailVisibilityOf(state, config, state.platformDetail))
        .indexOfFirst { it.key == key }
}

internal fun platformDetailItemAtFocusIndex(
    focusIndex: Int,
    config: PlatformEmulatorConfig,
    detail: PlatformDetailState,
    syncEnabled: Boolean,
    memcardCount: Int
): PlatformDetailItem? {
    val visibility = PlatformDetailVisibility.from(config, detail, syncEnabled, memcardCount)
    val layout = createPlatformDetailLayout()
    return layout.itemAtFocusIndex(focusIndex, visibility)
}

internal fun platformDetailSections(
    config: PlatformEmulatorConfig,
    detail: PlatformDetailState,
    syncEnabled: Boolean,
    memcardCount: Int
) = createPlatformDetailLayout().buildSections(
    PlatformDetailVisibility.from(config, detail, syncEnabled, memcardCount)
)

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
    val context = LocalContext.current

    if (config == null) {
        Text(
            text = stringResource(R.string.settings_platform_none_selected),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val storageConfig = uiState.storage.platformConfigs.find { it.platformId == config.platform.id }
    val syncEnabled = storageConfig?.syncEnabled ?: true

    val memcardCount = storageConfig?.folderMemcardCount ?: -1
    val visibility = remember(config, detail, syncEnabled, memcardCount) {
        PlatformDetailVisibility.from(config, detail, syncEnabled, memcardCount)
    }
    val layout = remember { createPlatformDetailLayout() }
    val visibleItems = remember(visibility) { layout.visibleItems(visibility) }
    val sections = remember(visibility, context) { layout.buildSections(visibility, context) }

    fun isFocused(item: PlatformDetailItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, visibility)

    fun openFrom(item: PlatformDetailItem, enter: () -> Unit) {
        viewModel.setFocusIndex(layout.focusIndexOf(item, visibility))
        enter()
    }

    fun pickerToken(item: PlatformDetailItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    val modalOpen = emulators.showEmulatorPicker || emulators.showSavePathModal ||
        emulators.showVariantPicker || emulators.updateModal != null || emulators.showLaunchArgsModal ||
        emulators.showAppPickerModal || emulators.showMemcardPicker
    val modalBlur by animateModalBlur(
        active = modalOpen,
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
            isHeader = { it is PlatformDetailItem.Header },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur).modalScrim(modalOpen),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
            when (item) {
                is PlatformDetailItem.Header ->
                    com.nendo.argosy.ui.screens.settings.components.SectionHeader(
                        stringResource(item.titleRes)
                    )

                is PlatformDetailItem.Emulator -> {
                    val hasUpdate = config.effectiveEmulatorId != null &&
                        config.effectiveEmulatorId in emulators.emulatorUpdateVersions
                    val hasInstallableKnown = config.availableEmulators.isNotEmpty() ||
                        config.downloadableEmulators.isNotEmpty()
                    ActionPreference(
                        title = if (hasInstallableKnown) {
                            stringResource(R.string.settings_platform_emulator_change)
                        } else {
                            stringResource(R.string.settings_platform_emulator_select_app)
                        },
                        subtitle = config.effectiveEmulatorName
                            ?: stringResource(R.string.settings_platform_emulator_not_installed),
                        trailingText = if (hasUpdate) {
                            stringResource(R.string.settings_platform_emulator_update_available)
                        } else {
                            null
                        },
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
                is PlatformDetailItem.Core -> {
                    val platformHasNetplay = config.effectiveEmulatorId == EmulatorRegistry.BUILTIN_ID &&
                        LibretroCoreRegistry.getCoresForPlatform(config.platform.slug)
                            .any { it.netplaySupport == NetplaySupportLevel.SUPPORTED }
                    val activeCoreId = config.selectedCore
                        ?: LibretroCoreRegistry.getDefaultCoreForPlatform(config.platform.slug)?.coreId
                    val activeNetplay = platformHasNetplay &&
                        activeCoreId != null &&
                        LibretroCoreRegistry.getCoreById(activeCoreId)?.netplaySupport == NetplaySupportLevel.SUPPORTED

                    val currentCoreIndex = config.availableCores
                        .indexOfFirst { it.id == config.selectedCore }
                        .takeIf { it >= 0 } ?: 0
                    CyclePreference(
                        title = stringResource(item.labelRes),
                        value = config.availableCores
                            .firstOrNull { it.id == config.selectedCore }
                            ?.displayName ?: stringResource(R.string.settings_platform_core_default),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleCoreForPlatform(config, 1) },
                        onPrev = { viewModel.cycleCoreForPlatform(config, -1) },
                        options = remember(config.availableCores) { config.availableCores.map { it.displayName } },
                        onSelect = { viewModel.cycleCoreForPlatform(config, it - currentCoreIndex) },
                        pickerRequestToken = pickerToken(item),
                        valueFooter = if (platformHasNetplay) {
                            {
                                CoreTag(
                                    text = stringResource(R.string.settings_platform_core_netplay_tag),
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
                is PlatformDetailItem.Extension -> CyclePreference(
                    title = stringResource(item.labelRes),
                    value = config.extensionOptions
                        .firstOrNull { it.extension == config.selectedExtension }?.label
                        ?: config.selectedExtension
                        ?: stringResource(R.string.settings_platform_extension_default),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleExtensionForPlatform(config, 1) },
                    onPrev = { viewModel.cycleExtensionForPlatform(config, -1) },
                    options = remember(config.extensionOptions) { config.extensionOptions.map { it.label } },
                    onSelect = { viewModel.changeExtensionForPlatform(config, config.extensionOptions[it].extension) },
                    pickerRequestToken = pickerToken(item)
                )
                is PlatformDetailItem.DisplayTarget -> CyclePreference(
                    title = stringResource(item.labelRes),
                    value = platformDisplayTargetLabel(config.displayTarget, uiState.display.gamesLaunchTarget),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleDisplayTarget(config, 1) },
                    onPrev = { viewModel.cycleDisplayTarget(config, -1) },
                    options = EmulatorDisplayTarget.entries.map {
                        platformDisplayTargetLabel(it, uiState.display.gamesLaunchTarget)
                    },
                    onSelect = { viewModel.cycleDisplayTarget(config, it - config.displayTarget.ordinal) },
                    pickerRequestToken = pickerToken(item)
                )
                is PlatformDetailItem.LegacyMode -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_legacy_mode_subtitle),
                    isEnabled = config.useFileUri,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleLegacyMode(config) }
                )
                is PlatformDetailItem.LaunchArgs -> NavigationPreference(
                    icon = Icons.Default.Settings,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_launch_args_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.openLaunchArgsModal(config.platform.id) }
                )
                is PlatformDetailItem.BuiltinVideo -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_builtin_video_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToBuiltinVideoForPlatform(detail.platformIndex) } }
                )
                is PlatformDetailItem.BuiltinControls -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_builtin_controls_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToBuiltinControlsForPlatform(detail.platformIndex) } }
                )
                is PlatformDetailItem.BuiltinSaves -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_builtin_saves_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToBuiltinSavesForPlatform(detail.platformIndex) } }
                )
                is PlatformDetailItem.BuiltinCoreOptions -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_core_options_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToCoreOptionsForPlatform() } }
                )

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
                                StatRow(
                                    stringResource(R.string.settings_platform_stat_total_games),
                                    "${detail.totalGames}",
                                    textColor,
                                    valueColor,
                                    style
                                )
                                StatRow(
                                    stringResource(R.string.settings_platform_stat_downloaded),
                                    "${detail.downloadedGames}",
                                    textColor,
                                    valueColor,
                                    style
                                )
                                if (detail.favorites > 0) {
                                    StatRow(
                                        stringResource(R.string.settings_platform_stat_favorites),
                                        "${detail.favorites}",
                                        textColor,
                                        valueColor,
                                        style
                                    )
                                }
                                StatRow(
                                    stringResource(R.string.settings_platform_stat_play_time),
                                    detail.playTimeFormatted,
                                    textColor,
                                    valueColor,
                                    style
                                )
                                if (isSyncingThis) {
                                    val syncText = if (syncProgress.gamesTotal > 0) {
                                        stringResource(
                                            R.string.settings_platform_stat_sync_progress,
                                            syncProgress.gamesDone,
                                            syncProgress.gamesTotal
                                        )
                                    } else {
                                        stringResource(R.string.settings_platform_stat_sync_busy)
                                    }
                                    StatRow(
                                        stringResource(R.string.settings_platform_stat_sync),
                                        syncText,
                                        textColor,
                                        MaterialTheme.colorScheme.primary,
                                        style
                                    )
                                }
                            }
                        }
                        "info_package_path" -> {
                            val access = detail.packagePathAccess
                            val status = when (access) {
                                null -> stringResource(R.string.settings_platform_package_path_checking)
                                PackageDataAccess.BLOCKED -> stringResource(R.string.settings_platform_package_path_blocked)
                                else -> stringResource(R.string.settings_platform_package_path_accessible)
                            }
                            val route = when (access) {
                                PackageDataAccess.DIRECT -> stringResource(R.string.settings_platform_package_path_via_direct)
                                PackageDataAccess.UNICODE -> stringResource(R.string.settings_platform_package_path_via_unicode)
                                PackageDataAccess.ROOT -> stringResource(R.string.settings_platform_package_path_via_root)
                                PackageDataAccess.BLOCKED, null -> null
                            }
                            InfoPreference(
                                title = stringResource(R.string.settings_platform_package_path_title),
                                value = status,
                                isFocused = false,
                                subtitle = route
                            )
                        }
                        "info_bios_status" -> {
                            val status = when {
                                detail.biosDownloaded >= detail.biosTotal ->
                                    stringResource(R.string.settings_platform_bios_status_all)
                                detail.biosDownloaded > 0 -> stringResource(
                                    R.string.settings_platform_bios_status_partial,
                                    detail.biosDownloaded,
                                    detail.biosTotal
                                )
                                else -> stringResource(R.string.settings_platform_bios_status_none)
                            }
                            InfoPreference(
                                title = stringResource(R.string.settings_platform_bios_status_title),
                                value = status,
                                isFocused = false
                            )
                        }
                    }
                }
                is PlatformDetailItem.ClearArtCache -> ActionPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_clear_art_subtitle),
                    isFocused = isFocused(item),
                    icon = Icons.Default.Refresh,
                    onClick = { viewModel.clearPlatformArtCache(config.platform.slug) }
                )
                is PlatformDetailItem.ScanFiles -> ActionPreference(
                    title = if (detail.isScanning) {
                        stringResource(R.string.settings_platform_scan_busy)
                    } else {
                        stringResource(item.labelRes)
                    },
                    subtitle = stringResource(R.string.settings_platform_scan_files_subtitle),
                    isFocused = isFocused(item),
                    icon = Icons.Default.Search,
                    isEnabled = !detail.isScanning,
                    onClick = { viewModel.scanFilesForPlatform(config.platform.id) }
                )
                is PlatformDetailItem.ScanApps -> ActionPreference(
                    title = if (detail.isScanning) {
                        stringResource(R.string.settings_platform_scan_busy)
                    } else {
                        stringResource(item.labelRes)
                    },
                    subtitle = stringResource(R.string.settings_platform_scan_apps_subtitle),
                    isFocused = isFocused(item),
                    icon = Icons.Default.Search,
                    isEnabled = !detail.isScanning,
                    onClick = { viewModel.scanInstalledAndroidGames() }
                )

                is PlatformDetailItem.RomPath -> ActionPreference(
                    title = stringResource(item.labelRes),
                    subtitle = formatPath(context, storageConfig?.effectivePath),
                    trailingText = if (storageConfig?.customRomPath != null) {
                        stringResource(R.string.settings_platform_path_custom_tag)
                    } else {
                        null
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.openPlatformFolderPicker(config.platform.id) },
                    showResetButton = storageConfig?.customRomPath != null,
                    onReset = { viewModel.resetPlatformRomPath(config.platform.id) }
                )
                is PlatformDetailItem.SavePath -> {
                    val hasOverride = storageConfig?.isUserSavePathOverride == true
                    val canReset = storageConfig?.canResetSavePath == true
                    val isBuiltinEmulator = config.effectiveEmulatorId == EmulatorRegistry.BUILTIN_ID
                    val besideRom = config.savesBesideRom && !hasOverride
                    val accessBlocked = !isBuiltinEmulator && !config.effectiveEmulatorIsRetroArch &&
                        detail.packagePathAccess == PackageDataAccess.BLOCKED && !hasOverride && !besideRom
                    ActionPreference(
                        title = stringResource(item.labelRes),
                        subtitle = when {
                            besideRom -> formatPath(context, storageConfig?.effectivePath)
                            accessBlocked ->
                                stringResource(R.string.settings_platform_save_path_blocked)
                            config.effectiveEmulatorIsRetroArch -> retroArchPathSubtitle(
                                context, config, storageConfig?.effectiveSavePath, hasOverride
                            )
                            else -> formatPath(context, storageConfig?.effectiveSavePath)
                        },
                        trailingText = if (hasOverride) {
                            stringResource(R.string.settings_platform_path_custom_tag)
                        } else {
                            null
                        },
                        isFocused = isFocused(item),
                        onClick = { viewModel.launchSavePathPicker(config.platform.id) },
                        showResetButton = canReset,
                        onReset = { viewModel.resetPlatformSavePath(config.platform.id) }
                    )
                }
                is PlatformDetailItem.MemoryCard -> {
                    val cardCount = storageConfig?.folderMemcardCount ?: -1
                    val selected = storageConfig?.selectedMemcardPath
                    val selectedName = selected?.let { java.io.File(it).name }
                    val emulatorName = config.effectiveEmulatorName
                        ?: stringResource(R.string.settings_platform_memcard_this_emulator)
                    val onlyCard = storageConfig?.onlyMemcardPath
                    val savePathCardName = onlyCard
                        ?.takeIf { storageConfig?.isUserSavePathOverride == true }
                        ?.takeIf { it.trimEnd('/') == storageConfig?.effectiveSavePath?.trimEnd('/') }
                        ?.let { java.io.File(it).name }
                    val (value, subtitle) = when {
                        savePathCardName != null ->
                            stringResource(R.string.settings_platform_memcard_from_save_path_value) to
                                savePathCardName
                        cardCount <= 0 ->
                            stringResource(R.string.settings_platform_memcard_none_value) to
                                stringResource(R.string.settings_platform_memcard_none_subtitle, emulatorName)
                        selected != null ->
                            stringResource(R.string.settings_platform_path_custom_tag) to (selectedName ?: selected)
                        onlyCard != null ->
                            stringResource(R.string.settings_platform_memcard_auto_value) to java.io.File(onlyCard).name
                        else ->
                            stringResource(R.string.settings_platform_memcard_unselected_value) to
                                stringResource(R.string.settings_platform_memcard_unselected_subtitle)
                    }
                    ActionPreference(
                        title = stringResource(item.labelRes),
                        subtitle = subtitle,
                        trailingText = value,
                        isFocused = isFocused(item),
                        onClick = { viewModel.openMemcardPicker(config) },
                        showResetButton = storageConfig?.memcardResettable == true,
                        onReset = {
                            config.effectiveEmulatorId?.let { viewModel.resetMemcardSelection(it) }
                        }
                    )
                }
                is PlatformDetailItem.StatePath -> {
                    val hasOverride = storageConfig?.isUserStatePathOverride == true
                    ActionPreference(
                        title = stringResource(item.labelRes),
                        subtitle = if (config.effectiveEmulatorIsRetroArch) {
                            retroArchPathSubtitle(context, config, storageConfig?.effectiveStatePath, hasOverride)
                        } else {
                            formatPath(context, storageConfig?.effectiveStatePath)
                        },
                        trailingText = if (hasOverride) {
                            stringResource(R.string.settings_platform_path_custom_tag)
                        } else {
                            null
                        },
                        isFocused = isFocused(item),
                        onClick = { viewModel.launchStatePathPicker(config.platform.id) },
                        showResetButton = hasOverride,
                        onReset = { viewModel.resetPlatformStatePath(config.platform.id) }
                    )
                }

                is PlatformDetailItem.SyncToggle -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_sync_toggle_subtitle),
                    isEnabled = storageConfig?.syncEnabled ?: true,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.togglePlatformSync(config.platform.id, it) }
                )
                is PlatformDetailItem.CombineContent -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_combine_content_subtitle),
                    isEnabled = storageConfig?.combineContent ?: false,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.togglePlatformCombineContent(config.platform.id, it) }
                )
                is PlatformDetailItem.SyncNow -> {
                    val isBusy = config.platform.id in uiState.storage.busyPlatformIds ||
                        uiState.storage.isLibrarySyncing
                    ActionPreference(
                        title = stringResource(item.labelRes),
                        subtitle = if (isBusy) {
                            stringResource(R.string.settings_platform_sync_now_busy)
                        } else {
                            stringResource(R.string.settings_platform_sync_now_subtitle)
                        },
                        isFocused = isFocused(item),
                        icon = Icons.Default.Sync,
                        spinIcon = isBusy,
                        isEnabled = !isBusy,
                        onClick = { viewModel.syncPlatform(config.platform.id, config.platform.getDisplayName()) }
                    )
                }
                is PlatformDetailItem.SkipFullSync -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_skip_full_sync_subtitle),
                    isEnabled = storageConfig?.skipFullSync ?: false,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.togglePlatformSkipFullSync(config.platform.id, it) }
                )
                is PlatformDetailItem.PackagePath -> {}
                is PlatformDetailItem.DownloadDefaults -> ActionPreference(
                    title = stringResource(item.labelRes),
                    subtitle = if (detail.downloadOverrides.isEmpty()) {
                        stringResource(R.string.settings_platform_download_defaults_subtitle)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_platform_download_defaults_overrides,
                            detail.downloadOverrides.size,
                            detail.downloadOverrides.size
                        )
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.openPlatformDownloadDefaults(config.platform.slug) }
                )
                is PlatformDetailItem.RemoveFiles -> ActionPreference(
                    title = stringResource(item.labelRes),
                    subtitle = pluralStringResource(
                        R.plurals.settings_platform_remove_files_subtitle,
                        detail.downloadedGames,
                        detail.downloadedGames
                    ),
                    isFocused = isFocused(item),
                    isDangerous = true,
                    onClick = { viewModel.requestRemoveLocalFiles() }
                )

                is PlatformDetailItem.BiosStatus -> {}
                is PlatformDetailItem.BiosDownload -> ActionPreference(
                    title = stringResource(item.labelRes),
                    subtitle = if (uiState.bios.isDownloading) {
                        uiState.bios.downloadingFileName
                            ?.let { stringResource(R.string.settings_platform_bios_downloading_file, it) }
                            ?: stringResource(R.string.settings_platform_bios_downloading)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_platform_bios_download_missing,
                            detail.biosTotal - detail.biosDownloaded,
                            detail.biosTotal - detail.biosDownloaded
                        )
                    },
                    isFocused = isFocused(item),
                    icon = Icons.Default.Download,
                    onClick = { viewModel.downloadBiosForPlatform(config.platform.slug) }
                )
                is PlatformDetailItem.BiosInstall -> ActionPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(
                        R.string.settings_platform_bios_install_subtitle,
                        config.effectiveEmulatorName
                            ?: stringResource(R.string.settings_platform_bios_install_fallback)
                    ),
                    isFocused = isFocused(item),
                    onClick = { viewModel.distributeAllBios() }
                )
                is PlatformDetailItem.BiosCopy -> ActionPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_platform_bios_copy_subtitle),
                    isFocused = isFocused(item),
                    icon = Icons.Default.ContentCopy,
                    onClick = { viewModel.launchBiosCopyPicker(config.platform.slug) }
                )
            }
        }

        if (uiState.bios.showDistributeResultModal) {
            DistributeResultModal(
                results = uiState.bios.distributeResults,
                onDismiss = { viewModel.dismissDistributeResultModal() }
            )
        }

        com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost(
            visible = detail.showRemoveConfirm,
            title = stringResource(R.string.settings_platform_remove_confirm_title),
            message = pluralStringResource(
                R.plurals.settings_platform_remove_confirm_message,
                detail.downloadedGames,
                detail.downloadedGames,
                config.platform.name
            ),
            confirmLabel = stringResource(R.string.settings_platform_remove_confirm_action),
            onConfirm = { viewModel.confirmRemoveLocalFiles(config.platform.id) },
            onDismiss = { viewModel.dismissRemoveConfirm() },
            destructive = true
        )

        com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost(
            visible = detail.combineRestoreCount > 0,
            title = stringResource(R.string.settings_platform_combine_restore_title),
            message = pluralStringResource(
                R.plurals.settings_platform_combine_restore_message,
                detail.combineRestoreCount,
                detail.combineRestoreCount
            ),
            confirmLabel = stringResource(R.string.settings_platform_combine_restore_action),
            onConfirm = { viewModel.confirmCombineRestore(config.platform.id) },
            onDismiss = { viewModel.dismissCombineRestore() }
        )

        if (detail.showDownloadDefaults) {
            DownloadDefaultsModal(detail = detail, viewModel = viewModel)
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
                onResetSavePath = { viewModel.resetEmulatorSavePath(emulators.savePathModalInfo.emulatorId) },
                onToggleBesideRom = { viewModel.toggleSavesBesideRom() }
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
                onOpenCustomExtras = { viewModel.openLaunchArgsCustomExtras() },
                onSaveCustomExtras = { raw -> viewModel.saveLaunchArgsCustomExtras(raw) },
                onDismissCustomExtras = { viewModel.closeLaunchArgsCustomExtras() },
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
private fun platformDisplayTargetLabel(
    target: EmulatorDisplayTarget,
    gamesDefault: EmulatorDisplayTarget
): String =
    if (target == EmulatorDisplayTarget.DEFAULT) {
        stringResource(
            R.string.settings_platform_display_target_default_resolved,
            stringResource(EmulatorDisplayTarget.gamesDefaultFrom(gamesDefault.name).labelRes)
        )
    } else {
        stringResource(target.labelRes)
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

internal fun formatPath(context: android.content.Context, path: String?): String {
    if (path == null) return context.getString(R.string.settings_platform_path_unconfigured)
    val maxLen = 40
    return if (path.length > maxLen) "...${path.takeLast(maxLen)}" else path
}

private fun formatConfigLocation(context: android.content.Context, path: String?): String {
    if (path == null) return RETROARCH_CONFIG_FILE
    val root = com.nendo.argosy.data.storage.StoragePathUtils.primaryExternalRoot
    return formatPath(context, path.removePrefix(root).trimStart('/'))
}

private const val RETROARCH_CONFIG_FILE = "retroarch.cfg"

/**
 * Second line under a RetroArch save or state path, naming the configuration the path came
 * from. A manual path speaks for itself and gets no second line.
 */
private fun retroArchPathSubtitle(
    context: android.content.Context,
    config: PlatformEmulatorConfig,
    resolvedPath: String?,
    hasOverride: Boolean
): String {
    val path = formatPath(context, resolvedPath)
    if (hasOverride) return path
    val source = when (config.retroArchConfigStatus) {
        RetroArchConfigStatus.LOADED -> context.getString(
            R.string.settings_platform_retroarch_config_loaded,
            formatConfigLocation(context, config.retroArchConfigPath)
        )
        RetroArchConfigStatus.UNREADABLE -> context.getString(
            R.string.settings_platform_retroarch_config_unreadable,
            formatConfigLocation(context, config.retroArchConfigPath)
        )
        RetroArchConfigStatus.MISSING ->
            context.getString(R.string.settings_platform_retroarch_config_missing)
    }
    return "$path\n$source"
}


@Composable
private fun DownloadDefaultsModal(
    detail: PlatformDetailState,
    viewModel: SettingsViewModel
) {
    val keys = com.nendo.argosy.data.preferences.DownloadDefaults.CONFIGURABLE_KEYS
    val context = LocalContext.current
    com.nendo.argosy.ui.components.Modal(
        title = stringResource(R.string.settings_platform_download_defaults_modal_title),
        subtitle = stringResource(R.string.settings_platform_download_defaults_modal_subtitle),
        onDismiss = viewModel::dismissPlatformDownloadDefaults
    ) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        com.nendo.argosy.ui.components.FocusedScroll(
            listState = listState,
            focusedIndex = detail.downloadDefaultsFocusIndex
        )
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(keys, key = { _, key -> key }) { index, key ->
                val override = detail.downloadOverrides[key]
                val effective = override
                    ?: detail.globalDownloadDefaults[key]
                    ?: (com.nendo.argosy.data.preferences.DownloadDefaults.FACTORY[key] ?: false)
                SwitchPreference(
                    title = downloadCategoryLabel(context, key),
                    subtitle = if (override == null) {
                        stringResource(R.string.settings_platform_download_default_inherited)
                    } else {
                        stringResource(R.string.settings_platform_download_default_override)
                    },
                    isEnabled = effective,
                    isFocused = detail.downloadDefaultsFocusIndex == index,
                    onToggle = { viewModel.setPlatformDownloadDefault(key, it) }
                )
            }
            item(key = "reset_overrides") {
                ActionPreference(
                    title = stringResource(R.string.settings_platform_download_defaults_reset_title),
                    subtitle = stringResource(R.string.settings_platform_download_defaults_reset_subtitle),
                    isFocused = detail.downloadDefaultsFocusIndex == keys.size,
                    isEnabled = detail.downloadOverrides.isNotEmpty(),
                    onClick = { viewModel.resetPlatformDownloadDefaults() }
                )
            }
        }
    }
}
