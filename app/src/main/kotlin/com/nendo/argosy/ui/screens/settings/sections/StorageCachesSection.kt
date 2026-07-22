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
        val title: String,
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
    data object SfxCacheClear : StorageCachesItem("sfxCacheClear", "media")
    data object EmulatorApksClear : StorageCachesItem("emulatorApksClear", "media")
    data object MiscDownloadsClear : StorageCachesItem("miscDownloadsClear", "media")

    data object ShadersCatalogClear : StorageCachesItem("shadersCatalogClear", "system")
    data object FramesClear : StorageCachesItem("framesClear", "system")

    data object SteamClear : StorageCachesItem("steamClear", "steam", { it.steamVisible })

    companion object {
        const val KEY_BIOS_INFO = "biosInfo"
        const val KEY_CORES_INFO = "coresInfo"
        const val KEY_SHADERS_CUSTOM_INFO = "shadersCustomInfo"
        const val KEY_FONTS_INFO = "fontsInfo"
        const val KEY_DATABASE_INFO = "databaseInfo"
        const val KEY_STEAM_TOTAL_INFO = "steamTotalInfo"
        const val KEY_STEAM_STAGING_INFO = "steamStagingInfo"

        val ALL: List<StorageCachesItem> = listOf(
            Header("syncHeader", "sync", "SYNC CACHES"),
            PendingUploads, SaveCacheClear, StateCacheClear, PathCacheClear,
            StateCacheToggle, SaveCacheLimit,
            SectionSpacer("mediaSpacer", "media"),
            Header("mediaHeader", "media", "MEDIA CACHES"),
            ImageCacheClear, ValidateImageCache, ScreenshotsToggle, BoxArtToggle,
            RomExtractionClear, SfxCacheClear, EmulatorApksClear, MiscDownloadsClear,
            SectionSpacer("systemSpacer", "system"),
            Header("systemHeader", "system", "SYSTEM"),
            InfoRow(KEY_BIOS_INFO, "system"),
            InfoRow(KEY_CORES_INFO, "system"),
            ShadersCatalogClear,
            InfoRow(KEY_SHADERS_CUSTOM_INFO, "system"),
            FramesClear,
            InfoRow(KEY_FONTS_INFO, "system"),
            InfoRow(KEY_DATABASE_INFO, "system"),
            SectionSpacer("steamSpacer", "steam", { it.steamVisible }),
            Header("steamHeader", "steam", "STEAM", { it.steamVisible }),
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
    sectionTitle = {
        when (it) {
            "sync" -> "SYNC CACHES"
            "media" -> "MEDIA CACHES"
            "system" -> "SYSTEM"
            "steam" -> "STEAM"
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
    val visibleItems = remember(layoutState) { storageCachesLayout.visibleItems(layoutState) }
    val sections = remember(layoutState) { storageCachesLayout.buildSections(layoutState) }

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
            is StorageCachesItem.Header -> SectionHeader(item.title)

            is StorageCachesItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            is StorageCachesItem.InfoRow -> CachesInfoRow(item, uiState)

            StorageCachesItem.PendingUploads -> ActionPreference(
                icon = Icons.Default.Sync,
                title = "${syncSettings.pendingUploadsCount} saves waiting to upload",
                subtitle = if (isOnline) "Sync now before clearing sync caches" else "Reconnect to the server to sync",
                isFocused = isFocused(item),
                isEnabled = isOnline && !syncSettings.isSyncing,
                onClick = { viewModel.requestSyncSaves() }
            )

            StorageCachesItem.SaveCacheClear -> {
                val totalCached = syncSettings.saveCacheCount + syncSettings.stateCacheCount
                val pendingUploads = syncSettings.pendingUploadsCount
                ActionPreference(
                    title = "Reset Save Cache",
                    subtitle = when {
                        syncSettings.isResettingSaveCache -> "Resetting..."
                        pendingUploads > 0 -> "$pendingUploads saves waiting to upload"
                        totalCached > 0 -> "$totalCached cached saves and states"
                        else -> "No cached entries"
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
                    title = "Clear State Cache",
                    subtitle = when {
                        syncSettings.isClearingStateCache -> "Clearing..."
                        pendingUploads > 0 -> "$pendingUploads saves waiting to upload"
                        stateCount > 0 -> "$stateCount cached save states"
                        else -> "No cached states"
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
                    title = "Clear Save Path Cache",
                    subtitle = when {
                        syncSettings.isClearingPathCache -> "Clearing..."
                        pendingUploads > 0 -> "$pendingUploads saves waiting to upload"
                        pathCount > 0 -> "$pathCount cached paths"
                        else -> "No cached paths"
                    },
                    isFocused = isFocused(item),
                    isEnabled = !syncSettings.isClearingPathCache && pathCount > 0 && pendingUploads == 0,
                    onClick = { viewModel.requestClearPathCache() }
                )
            }

            StorageCachesItem.StateCacheToggle -> SwitchPreference(
                title = "Cache Save States",
                subtitle = "Keep state snapshots for cloud sync at session end",
                isEnabled = syncSettings.stateCacheEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleStateCache() }
            )

            StorageCachesItem.SaveCacheLimit -> {
                val limits = SyncSettingsDelegate.SAVE_CACHE_LIMIT_VALUES
                CyclePreference(
                    title = "Local Save Cache",
                    value = "${syncSettings.saveCacheLimit} saves per game",
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleSaveCacheLimit(1) },
                    onPrev = { viewModel.cycleSaveCacheLimit(-1) },
                    options = remember { limits.map { "$it saves per game" } },
                    onSelect = { viewModel.setSaveCacheLimit(limits[it]) },
                    pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
                )
            }

            StorageCachesItem.ImageCacheClear -> ActionPreference(
                title = "Image Cache",
                subtitle = when {
                    isBusy(CachesClearTarget.IMAGE_CACHE) -> "Clearing..."
                    else -> "${categoryFiles(snapshot, StorageCategory.IMAGE_CACHE)} files - covers re-download as you browse"
                },
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.IMAGE_CACHE)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.IMAGE_CACHE) &&
                    !syncSettings.isImageCacheMigrating && !uiState.storage.isValidatingCache,
                onClick = { viewModel.requestCachesClear(CachesClearTarget.IMAGE_CACHE) }
            )

            StorageCachesItem.ValidateImageCache -> ActionPreference(
                title = "Validate Image Cache",
                subtitle = if (uiState.storage.isValidatingCache) "Validating..." else "Fix missing or broken cached images",
                isFocused = isFocused(item),
                isEnabled = !uiState.storage.isValidatingCache && !isBusy(CachesClearTarget.IMAGE_CACHE),
                onClick = { viewModel.validateImageCache() }
            )

            StorageCachesItem.ScreenshotsToggle -> SwitchPreference(
                title = "Cache Screenshots",
                subtitle = "Boxart and backgrounds are always cached",
                isEnabled = uiState.server.syncScreenshotsEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleSyncScreenshots() }
            )

            StorageCachesItem.BoxArtToggle -> SwitchPreference(
                title = "Cache 3D Box Art",
                subtitle = "Download box back and spine scans for 3D box displays",
                isEnabled = uiState.server.boxArtCacheEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleBoxArtCache() }
            )

            StorageCachesItem.RomExtractionClear -> ActionPreference(
                title = "Extracted ROMs",
                subtitle = if (isBusy(CachesClearTarget.ROM_EXTRACTION)) "Clearing..."
                    else "Working copies of compressed games - re-extract on launch",
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.ROM_EXTRACTION)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.ROM_EXTRACTION),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.ROM_EXTRACTION) }
            )

            StorageCachesItem.SfxCacheClear -> ActionPreference(
                title = "Sound Effects Cache",
                subtitle = if (isBusy(CachesClearTarget.SFX_CACHE)) "Clearing..."
                    else "Transcoded custom sounds - rebuilt automatically",
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.SFX_CACHE)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.SFX_CACHE),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.SFX_CACHE) }
            )

            StorageCachesItem.EmulatorApksClear -> ActionPreference(
                title = "Emulator Installers",
                subtitle = if (isBusy(CachesClearTarget.EMULATOR_APKS)) "Clearing..."
                    else "Downloaded APKs - safe to remove after install",
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.EMULATOR_APKS)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.EMULATOR_APKS),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.EMULATOR_APKS) }
            )

            StorageCachesItem.MiscDownloadsClear -> ActionPreference(
                title = "Misc Downloads",
                subtitle = if (isBusy(CachesClearTarget.MISC_DOWNLOADS)) "Clearing..."
                    else "Friend presence covers and GPU driver downloads",
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.MISC_DOWNLOADS)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.MISC_DOWNLOADS),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.MISC_DOWNLOADS) }
            )

            StorageCachesItem.ShadersCatalogClear -> ActionPreference(
                title = "Shader Catalog",
                subtitle = if (isBusy(CachesClearTarget.SHADERS_CATALOG)) "Clearing..."
                    else "Downloaded shaders - re-download on demand",
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.SHADERS_CATALOG)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.SHADERS_CATALOG),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.SHADERS_CATALOG) }
            )

            StorageCachesItem.FramesClear -> ActionPreference(
                title = "Frame Overlays",
                subtitle = if (isBusy(CachesClearTarget.FRAMES)) "Clearing..."
                    else "Downloaded bezels - re-download on demand",
                trailingText = formatBytes(categoryBytes(snapshot, StorageCategory.FRAMES)),
                isFocused = isFocused(item),
                isEnabled = !isBusy(CachesClearTarget.FRAMES),
                onClick = { viewModel.requestCachesClear(CachesClearTarget.FRAMES) }
            )

            StorageCachesItem.SteamClear -> ActionPreference(
                title = "Clear Download Data",
                subtitle = when {
                    isBusy(CachesClearTarget.STEAM_DOWNLOADS) -> "Clearing..."
                    caches.steamDownloadBusy -> "Cancel Steam downloads first"
                    else -> "Removes staged downloads and the queue - installed games stay"
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
        StorageCachesItem.KEY_BIOS_INFO -> Triple(
            "BIOS Files",
            "Required by emulators - not a cache",
            formatBytes(categoryBytes(snapshot, StorageCategory.BIOS))
        )
        StorageCachesItem.KEY_CORES_INFO -> Triple(
            "Cores & System",
            "Built-in emulator cores and support files",
            formatBytes(categoryBytes(snapshot, StorageCategory.CORES_SYSTEM))
        )
        StorageCachesItem.KEY_SHADERS_CUSTOM_INFO -> Triple(
            "Custom Shaders",
            "User content - never cleared",
            formatBytes(categoryBytes(snapshot, StorageCategory.SHADERS_CUSTOM))
        )
        StorageCachesItem.KEY_FONTS_INFO -> Triple(
            "Fonts",
            "User content - never cleared",
            formatBytes(categoryBytes(snapshot, StorageCategory.FONTS))
        )
        StorageCachesItem.KEY_DATABASE_INFO -> Triple(
            "Database",
            "${categoryFiles(snapshot, StorageCategory.DATABASE)} library database files",
            formatBytes(categoryBytes(snapshot, StorageCategory.DATABASE))
        )
        StorageCachesItem.KEY_STEAM_TOTAL_INFO -> Triple(
            "Steam & PC Total",
            "Includes installed store games",
            formatBytes(categoryBytes(snapshot, StorageCategory.STEAM))
        )
        StorageCachesItem.KEY_STEAM_STAGING_INFO -> Triple(
            "Download Staging",
            "Partial downloads awaiting deploy",
            uiState.storageCaches.steamStagingBytes?.let { formatBytes(it) } ?: "Computing..."
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
