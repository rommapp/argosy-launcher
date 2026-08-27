package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.storage.StorageSnapshot
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.CachesClearTarget
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.delegates.SyncSettingsDelegate
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.formatBytes

internal data class StorageCachesLayoutState(
    val steamVisible: Boolean,
    val pendingUploadsVisible: Boolean
) {
    companion object {
        fun from(state: SettingsUiState) = StorageCachesLayoutState(
            steamVisible = storageSteamVisible(state),
            pendingUploadsVisible = state.syncSettings.pendingUploadsCount > 0
        )
    }
}

internal sealed class StorageCachesItem(
    val key: String,
    val section: String,
    val visibleWhen: (StorageCachesLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer, is InfoRow -> false
        else -> true
    }

    class Header(
        key: String,
        section: String,
        val titleRes: Int,
        visibleWhen: (StorageCachesLayoutState) -> Boolean = { true }
    ) : StorageCachesItem(key, section, visibleWhen)

    class SectionSpacer(
        key: String,
        section: String,
        visibleWhen: (StorageCachesLayoutState) -> Boolean = { true }
    ) : StorageCachesItem(key, section, visibleWhen)

    class InfoRow(
        key: String,
        section: String,
        visibleWhen: (StorageCachesLayoutState) -> Boolean = { true }
    ) : StorageCachesItem(key, section, visibleWhen)

    data object PendingUploads : StorageCachesItem(
        "pendingUploads", "sync", { it.pendingUploadsVisible }
    )
    data object SaveCacheClear : StorageCachesItem("saveCacheClear", "sync")
    data object StateCacheClear : StorageCachesItem("stateCacheClear", "sync")
    data object PathCacheClear : StorageCachesItem("pathCacheClear", "sync")
    data object StateCacheToggle : StorageCachesItem("stateCacheToggle", "sync")
    data object SaveCacheLimit : StorageCachesItem("cachesSaveCacheLimit", "sync")

    data object ImageCacheClear : StorageCachesItem("imageCacheClear", "media")
    data object ValidateImageCache : StorageCachesItem("validateImageCache", "media")
    data object ScreenshotsToggle : StorageCachesItem("screenshotsToggle", "media")
    data object BoxArtToggle : StorageCachesItem("boxArtToggle", "media")
    data object RomExtractionClear : StorageCachesItem("romExtractionClear", "media")
    data object RomStagingClear : StorageCachesItem("romStagingClear", "media")
    data object SfxCacheClear : StorageCachesItem("sfxCacheClear", "media")
    data object EmulatorApksClear : StorageCachesItem("emulatorApksClear", "media")
    data object MiscDownloadsClear : StorageCachesItem("miscDownloadsClear", "media")

    data object ShadersCatalogClear : StorageCachesItem("shadersCatalogClear", "system")
    data object FramesClear : StorageCachesItem("framesClear", "system")

    data object SteamClear : StorageCachesItem("steamClear", "steam", { it.steamVisible })

    companion object {
        const val KEY_ARTWORK_CACHE_INFO = "artworkCacheInfo"
        const val KEY_BIOS_INFO = "biosInfo"
        const val KEY_CORES_INFO = "coresInfo"
        const val KEY_SHADERS_CUSTOM_INFO = "shadersCustomInfo"
        const val KEY_FONTS_INFO = "fontsInfo"
        const val KEY_DATABASE_INFO = "databaseInfo"
        const val KEY_STEAM_TOTAL_INFO = "steamTotalInfo"
        const val KEY_STEAM_STAGING_INFO = "steamStagingInfo"

        val ALL: List<StorageCachesItem>
            get() = listOf(
                Header("syncHeader", "sync", R.string.settings_caches_section_sync),
                PendingUploads, SaveCacheClear, StateCacheClear, PathCacheClear,
                StateCacheToggle, SaveCacheLimit,
                SectionSpacer("mediaSpacer", "media"),
                Header("mediaHeader", "media", R.string.settings_caches_section_media),
                ImageCacheClear, ValidateImageCache,
                InfoRow(KEY_ARTWORK_CACHE_INFO, "media"),
                ScreenshotsToggle, BoxArtToggle,
                RomExtractionClear, RomStagingClear, SfxCacheClear, EmulatorApksClear, MiscDownloadsClear,
                SectionSpacer("systemSpacer", "system"),
                Header("systemHeader", "system", R.string.settings_caches_section_system),
                InfoRow(KEY_BIOS_INFO, "system"),
                InfoRow(KEY_CORES_INFO, "system"),
                ShadersCatalogClear,
                InfoRow(KEY_SHADERS_CUSTOM_INFO, "system"),
                FramesClear,
                InfoRow(KEY_FONTS_INFO, "system"),
                InfoRow(KEY_DATABASE_INFO, "system"),
                SectionSpacer("steamSpacer", "steam", { it.steamVisible }),
                Header("steamHeader", "steam", R.string.settings_caches_section_steam, { it.steamVisible }),
                InfoRow(KEY_STEAM_TOTAL_INFO, "steam", { it.steamVisible }),
                InfoRow(KEY_STEAM_STAGING_INFO, "steam", { it.steamVisible }),
                SteamClear
            )
    }
}

private val storageCachesLayout = SettingsLayout<StorageCachesItem, StorageCachesLayoutState>(
    allItems = StorageCachesItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "sync" -> R.string.settings_caches_section_sync
            "media" -> R.string.settings_caches_section_media
            "system" -> R.string.settings_caches_section_system
            "steam" -> R.string.settings_caches_section_steam
            else -> null
        }
    }
)

internal data class StorageCachesLayoutInfo(
    val layout: SettingsLayout<StorageCachesItem, StorageCachesLayoutState>,
    val state: StorageCachesLayoutState
)

internal fun createStorageCachesLayoutInfo(state: SettingsUiState): StorageCachesLayoutInfo =
    StorageCachesLayoutInfo(storageCachesLayout, StorageCachesLayoutState.from(state))

internal fun storageCachesItemAtFocusIndex(index: Int, info: StorageCachesLayoutInfo): StorageCachesItem? =
    info.layout.itemAtFocusIndex(index, info.state)

internal fun storageCachesMaxFocusIndex(info: StorageCachesLayoutInfo): Int =
    info.layout.maxFocusIndex(info.state)

internal fun storageCachesSections(info: StorageCachesLayoutInfo) =
    info.layout.buildSections(info.state)

internal fun storageCachesFocusIndexOfSteam(info: StorageCachesLayoutInfo): Int =
    info.layout.focusIndexOf(StorageCachesItem.SteamClear, info.state)

private fun categoryBytes(snapshot: StorageSnapshot?, category: StorageCategory): Long =
    snapshot?.categories?.get(category)?.bytes ?: 0L

private fun categoryFiles(snapshot: StorageSnapshot?, category: StorageCategory): Int =
    snapshot?.categories?.get(category)?.fileCount ?: 0

@Composable
fun StorageCachesSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val snapshot = uiState.attribution.snapshot
    val syncSettings = uiState.syncSettings
    val caches = uiState.storageCaches
    val isOnline = uiState.server.connectionStatus == ConnectionStatus.ONLINE

    val layoutState = remember(uiState.attribution.steamTileLatched, syncSettings.pendingUploadsCount > 0) {
        StorageCachesLayoutState.from(uiState)
    }
    val context = LocalContext.current
    val visibleItems = remember(layoutState) { storageCachesLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) {
        storageCachesLayout.buildSections(layoutState, context)
    }

    fun isFocused(item: StorageCachesItem): Boolean =
        uiState.focusedIndex == storageCachesLayout.focusIndexOf(item, layoutState)

    fun isBusy(target: CachesClearTarget): Boolean = target in caches.busyClears

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { storageCachesLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is StorageCachesItem.SectionSpacer },
        isHeader = { it is StorageCachesItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is StorageCachesItem.Header -> SectionHeader(stringResource(item.titleRes))

            is StorageCachesItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            is StorageCachesItem.InfoRow -> CachesInfoRow(item, uiState)

            StorageCachesItem.PendingUploads -> ActionPreference(
                icon = Icons.Default.Sync,
                title = pluralStringResource(
                    R.plurals.settings_caches_pending_uploads_title,
                    syncSettings.pendingUploadsCount,
                    syncSettings.pendingUploadsCount
                ),
                subtitle = if (isOnline) {
                    stringResource(R.string.settings_caches_pending_uploads_subtitle_online)
                } else {
                    stringResource(R.string.settings_caches_pending_uploads_subtitle_offline)
                },
                isFocused = isFocused(item),
                isEnabled = isOnline && !syncSettings.isSyncing,
                onClick = { viewModel.requestSyncSaves() }
            )

            StorageCachesItem.SaveCacheClear -> {
                val totalCached = syncSettings.saveCacheCount + syncSettings.stateCacheCount
                val pendingUploads = syncSettings.pendingUploadsCount
                ActionPreference(
                    title = stringResource(R.string.settings_caches_save_cache_title),
                    subtitle = when {
                        syncSettings.isResettingSaveCache ->
                            stringResource(R.string.settings_caches_save_cache_busy)
                        pendingUploads > 0 -> pluralStringResource(
                            R.plurals.settings_caches_save_cache_pending,
                            pendingUploads,
                            pendingUploads
                        )
                        totalCached > 0 -> pluralStringResource(
                            R.plurals.settings_caches_save_cache_count,
                            totalCached,
                            totalCached
                        )
                        else -> stringResource(R.string.settings_caches_save_cache_empty)
                    },
                    trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.SAVE_STATE_CACHE)),
                    isFocused = isFocused(item),
                    isEnabled = !syncSettings.isResettingSaveCache && totalCached > 0 && pendingUploads == 0,
                    isDangerous = true,
                    onClick = { viewModel.requestResetSaveCache() }
                )
            }

            StorageCachesItem.StateCacheClear -> {
                val stateCount = syncSettings.stateCacheCount
                val pendingUploads = syncSettings.pendingUploadsCount
                ActionPreference(
                    title = stringResource(R.string.settings_caches_state_cache_title),
                    subtitle = when {
                        syncSettings.isClearingStateCache ->
                            stringResource(R.string.settings_caches_state_cache_busy)
                        pendingUploads > 0 -> pluralStringResource(
                            R.plurals.settings_caches_state_cache_pending,
                            pendingUploads,
                            pendingUploads
                        )
                        stateCount > 0 -> pluralStringResource(
                            R.plurals.settings_caches_state_cache_count,
                            stateCount,
                            stateCount
                        )
                        else -> stringResource(R.string.settings_caches_state_cache_empty)
                    },
                    isFocused = isFocused(item),
                    isEnabled = !syncSettings.isClearingStateCache && stateCount > 0 && pendingUploads == 0,
                    isDangerous = true,
                    onClick = { viewModel.requestClearStateCache() }
                )
            }

            StorageCachesItem.PathCacheClear -> {
                val pathCount = syncSettings.pathCacheCount
                val pendingUploads = syncSettings.pendingUploadsCount
                ActionPreference(
                    title = stringResource(R.string.settings_caches_path_cache_title),
                    subtitle = when {
                        syncSettings.isClearingPathCache ->
                            stringResource(R.string.settings_caches_path_cache_busy)
                        pendingUploads > 0 -> pluralStringResource(
                            R.plurals.settings_caches_path_cache_pending,
                            pendingUploads,
                            pendingUploads
                        )
                        pathCount > 0 -> pluralStringResource(
                            R.plurals.settings_caches_path_cache_count,
                            pathCount,
                            pathCount
                        )
                        else -> stringResource(R.string.settings_caches_path_cache_empty)
                    },
                    isFocused = isFocused(item),
                    isEnabled = !syncSettings.isClearingPathCache && pathCount > 0 && pendingUploads == 0,
                    onClick = { viewModel.requestClearPathCache() }
                )
            }

            StorageCachesItem.StateCacheToggle -> SwitchPreference(
                title = stringResource(R.string.settings_caches_state_toggle_title),
                subtitle = stringResource(R.string.settings_caches_state_toggle_subtitle),
                isEnabled = syncSettings.stateCacheEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleStateCache() }
            )

            StorageCachesItem.SaveCacheLimit -> {
                val limits = SyncSettingsDelegate.SAVE_CACHE_LIMIT_VALUES
                CyclePreference(
                    title = stringResource(R.string.settings_caches_save_limit_title),
                    value = pluralStringResource(
                        R.plurals.settings_caches_save_limit_value,
                        syncSettings.saveCacheLimit,
                        syncSettings.saveCacheLimit
                    ),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleSaveCacheLimit(1) },
                    onPrev = { viewModel.cycleSaveCacheLimit(-1) },
                    options = remember(context) {
                        limits.map {
                            context.resources.getQuantityString(
                                R.plurals.settings_caches_save_limit_value,
                                it,
                                it
                            )
                        }
                    },
                    onSelect = { viewModel.setSaveCacheLimit(limits[it]) },
                    pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
                )
            }

            StorageCachesItem.ImageCacheClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_image_cache_title),
                subtitle = if (isBusy(CachesClearTarget.IMAGE_CACHE)) {
                    stringResource(R.string.settings_caches_image_cache_busy)
                } else {
                    val imageFiles = categoryFiles(snapshot, StorageCategory.IMAGE_CACHE)
                    pluralStringResource(
                        R.plurals.settings_caches_image_cache_subtitle,
                        imageFiles,
                        imageFiles
                    )
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.IMAGE_CACHE)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.IMAGE_CACHE) &&
                    !syncSettings.isImageCacheMigrating && !uiState.storage.isValidatingCache,
                onClick = { viewModel.requestCachesClear(CachesClearTarget.IMAGE_CACHE) }
            )

            StorageCachesItem.ValidateImageCache -> ActionPreference(
                title = stringResource(R.string.settings_caches_validate_title),
                subtitle = if (uiState.storage.isValidatingCache) {
                    stringResource(R.string.settings_caches_validate_busy)
                } else {
                    stringResource(R.string.settings_caches_validate_subtitle)
                },
                isFocused = isFocused(item),
                isEnabled = !uiState.storage.isValidatingCache && !isBusy(CachesClearTarget.IMAGE_CACHE),
                onClick = { viewModel.validateImageCache() }
            )

            StorageCachesItem.ScreenshotsToggle -> SwitchPreference(
                title = stringResource(R.string.settings_caches_screenshots_title),
                subtitle = stringResource(R.string.settings_caches_screenshots_subtitle),
                isEnabled = uiState.server.syncScreenshotsEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleSyncScreenshots() }
            )

            StorageCachesItem.BoxArtToggle -> SwitchPreference(
                title = stringResource(R.string.settings_caches_box_art_title),
                subtitle = stringResource(R.string.settings_caches_box_art_subtitle),
                isEnabled = uiState.server.boxArtCacheEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleBoxArtCache() }
            )

            StorageCachesItem.RomExtractionClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_rom_extraction_title),
                subtitle = if (isBusy(CachesClearTarget.ROM_EXTRACTION)) {
                    stringResource(R.string.settings_caches_rom_extraction_busy)
                } else {
                    stringResource(R.string.settings_caches_rom_extraction_subtitle)
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.ROM_EXTRACTION)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.ROM_EXTRACTION),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.ROM_EXTRACTION) }
            )

            StorageCachesItem.RomStagingClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_rom_staging_title),
                subtitle = if (isBusy(CachesClearTarget.ROM_STAGING)) {
                    stringResource(R.string.settings_caches_rom_staging_busy)
                } else {
                    stringResource(R.string.settings_caches_rom_staging_subtitle)
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.ROM_STAGING)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.ROM_STAGING),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.ROM_STAGING) }
            )

            StorageCachesItem.SfxCacheClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_sfx_title),
                subtitle = if (isBusy(CachesClearTarget.SFX_CACHE)) {
                    stringResource(R.string.settings_caches_sfx_busy)
                } else {
                    stringResource(R.string.settings_caches_sfx_subtitle)
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.SFX_CACHE)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.SFX_CACHE),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.SFX_CACHE) }
            )

            StorageCachesItem.EmulatorApksClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_emulator_apks_title),
                subtitle = if (isBusy(CachesClearTarget.EMULATOR_APKS)) {
                    stringResource(R.string.settings_caches_emulator_apks_busy)
                } else {
                    stringResource(R.string.settings_caches_emulator_apks_subtitle)
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.EMULATOR_APKS)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.EMULATOR_APKS),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.EMULATOR_APKS) }
            )

            StorageCachesItem.MiscDownloadsClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_misc_downloads_title),
                subtitle = if (isBusy(CachesClearTarget.MISC_DOWNLOADS)) {
                    stringResource(R.string.settings_caches_misc_downloads_busy)
                } else {
                    stringResource(R.string.settings_caches_misc_downloads_subtitle)
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.MISC_DOWNLOADS)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.MISC_DOWNLOADS),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.MISC_DOWNLOADS) }
            )

            StorageCachesItem.ShadersCatalogClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_shaders_title),
                subtitle = if (isBusy(CachesClearTarget.SHADERS_CATALOG)) {
                    stringResource(R.string.settings_caches_shaders_busy)
                } else {
                    stringResource(R.string.settings_caches_shaders_subtitle)
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.SHADERS_CATALOG)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.SHADERS_CATALOG),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.SHADERS_CATALOG) }
            )

            StorageCachesItem.FramesClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_frames_title),
                subtitle = if (isBusy(CachesClearTarget.FRAMES)) {
                    stringResource(R.string.settings_caches_frames_busy)
                } else {
                    stringResource(R.string.settings_caches_frames_subtitle)
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.FRAMES)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.FRAMES),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.FRAMES) }
            )

            StorageCachesItem.SteamClear -> ActionPreference(
                title = stringResource(R.string.settings_caches_steam_clear_title),
                subtitle = when {
                    isBusy(CachesClearTarget.STEAM_DOWNLOADS) ->
                        stringResource(R.string.settings_caches_steam_clear_busy)
                    caches.steamDownloadBusy ->
                        stringResource(R.string.settings_caches_steam_clear_blocked)
                    else -> stringResource(R.string.settings_caches_steam_clear_subtitle)
                },
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.STEAM_DOWNLOADS) && !caches.steamDownloadBusy,
                isDangerous = true,
                onClick = { viewModel.requestCachesClear(CachesClearTarget.STEAM_DOWNLOADS) }
            )
        }
    }
}

@Composable
private fun CachesInfoRow(item: StorageCachesItem.InfoRow, uiState: SettingsUiState) {
    val snapshot = uiState.attribution.snapshot
    val (title, subtitle, value) = when (item.key) {
        StorageCachesItem.KEY_ARTWORK_CACHE_INFO -> Triple(
            stringResource(R.string.settings_caches_info_artwork_title),
            stringResource(R.string.settings_caches_info_artwork_subtitle),
            formatBytes(categoryBytes(snapshot, StorageCategory.REMOTE_IMAGE_CACHE))
        )
        StorageCachesItem.KEY_BIOS_INFO -> Triple(
            stringResource(R.string.settings_caches_info_bios_title),
            stringResource(R.string.settings_caches_info_bios_subtitle),
            formatBytes(categoryBytes(snapshot, StorageCategory.BIOS))
        )
        StorageCachesItem.KEY_CORES_INFO -> Triple(
            stringResource(R.string.settings_caches_info_cores_title),
            stringResource(R.string.settings_caches_info_cores_subtitle),
            formatBytes(categoryBytes(snapshot, StorageCategory.CORES_SYSTEM))
        )
        StorageCachesItem.KEY_SHADERS_CUSTOM_INFO -> Triple(
            stringResource(R.string.settings_caches_info_shaders_title),
            stringResource(R.string.settings_caches_info_shaders_subtitle),
            formatBytes(categoryBytes(snapshot, StorageCategory.SHADERS_CUSTOM))
        )
        StorageCachesItem.KEY_FONTS_INFO -> Triple(
            stringResource(R.string.settings_caches_info_fonts_title),
            stringResource(R.string.settings_caches_info_fonts_subtitle),
            formatBytes(categoryBytes(snapshot, StorageCategory.FONTS))
        )
        StorageCachesItem.KEY_DATABASE_INFO -> {
            val databaseFiles = categoryFiles(snapshot, StorageCategory.DATABASE)
            Triple(
                stringResource(R.string.settings_caches_info_database_title),
                pluralStringResource(
                    R.plurals.settings_caches_info_database_subtitle,
                    databaseFiles,
                    databaseFiles
                ),
                formatBytes(categoryBytes(snapshot, StorageCategory.DATABASE))
            )
        }
        StorageCachesItem.KEY_STEAM_TOTAL_INFO -> Triple(
            stringResource(R.string.settings_caches_info_steam_total_title),
            stringResource(R.string.settings_caches_info_steam_total_subtitle),
            formatBytes(categoryBytes(snapshot, StorageCategory.STEAM))
        )
        StorageCachesItem.KEY_STEAM_STAGING_INFO -> Triple(
            stringResource(R.string.settings_caches_info_steam_staging_title),
            stringResource(R.string.settings_caches_info_steam_staging_subtitle),
            uiState.storageCaches.steamStagingBytes?.let { formatBytes(it) }
                ?: stringResource(R.string.settings_caches_info_steam_staging_computing)
        )
        else -> Triple(item.key, "", "")
    }
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
    }
}
