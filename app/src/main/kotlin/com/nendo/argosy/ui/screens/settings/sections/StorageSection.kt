package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.ExpandablePreference
import com.nendo.argosy.ui.components.ExpandedChildItem
import com.nendo.argosy.ui.components.InfoDisplay
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.PlatformStorageConfig
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal data class StorageLayoutState(val platformsExpanded: Boolean)

internal sealed class StorageItem(
    val key: String,
    val section: String,
    val visibleWhen: (StorageLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer, DownloadedInfo -> false
        else -> true
    }

    class Header(key: String, section: String, val title: String) : StorageItem(key, section)
    class SectionSpacer(key: String, section: String) : StorageItem(key, section)

    data object MaxDownloads : StorageItem("maxDownloads", "downloads")
    data object Threshold : StorageItem("threshold", "downloads")
    data object DownloadedInfo : StorageItem("downloadedInfo", "downloads")

    data object GlobalRomPath : StorageItem("globalRomPath", "locations")
    data object ImageCache : StorageItem("imageCache", "locations")
    data object ValidateCache : StorageItem("validateCache", "locations")

    data object PlatformsExpand : StorageItem("platformsExpand", "platforms")
    class PlatformItem(val config: PlatformStorageConfig) : StorageItem(
        key = "platform_${config.platformId}",
        section = "platforms",
        visibleWhen = { it.platformsExpanded }
    )

    data object PurgeAll : StorageItem("purgeAll", "danger")

    companion object {
        private val DownloadsHeader = Header("downloadsHeader", "downloads", "DOWNLOADS")
        private val LocationsSpacer = SectionSpacer("locationsSpacer", "locations")
        private val LocationsHeader = Header("locationsHeader", "locations", "FILE LOCATIONS")
        private val PlatformsSpacer = SectionSpacer("platformsSpacer", "platforms")
        private val PlatformsHeader = Header("platformsHeader", "platforms", "PLATFORM STORAGE")
        private val DangerSpacer = SectionSpacer("dangerSpacer", "danger")
        private val DangerHeader = Header("dangerHeader", "danger", "DANGER ZONE")

        fun buildItems(platformConfigs: List<PlatformStorageConfig>): List<StorageItem> = listOf(
            DownloadsHeader, MaxDownloads, Threshold, DownloadedInfo,
            LocationsSpacer, LocationsHeader, GlobalRomPath, ImageCache, ValidateCache,
            PlatformsSpacer, PlatformsHeader, PlatformsExpand
        ) + platformConfigs.map { PlatformItem(it) } + listOf(
            DangerSpacer, DangerHeader, PurgeAll
        )
    }
}

private fun createStorageLayout(items: List<StorageItem>) = SettingsLayout<StorageItem, StorageLayoutState>(
    allItems = items,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun storageMaxFocusIndex(platformsExpanded: Boolean, platformCount: Int): Int {
    val fixedFocusableCount = 7
    val expandedItems = if (platformsExpanded) platformCount else 0
    return (fixedFocusableCount + expandedItems - 1).coerceAtLeast(0)
}

internal data class StorageLayoutInfo(
    val layout: SettingsLayout<StorageItem, StorageLayoutState>,
    val state: StorageLayoutState
)

internal fun createStorageLayoutInfo(
    platformConfigs: List<PlatformStorageConfig>,
    platformsExpanded: Boolean
): StorageLayoutInfo {
    val items = StorageItem.buildItems(platformConfigs)
    val layout = createStorageLayout(items)
    val state = StorageLayoutState(platformsExpanded)
    return StorageLayoutInfo(layout, state)
}

internal fun storageItemAtFocusIndex(index: Int, info: StorageLayoutInfo): StorageItem? =
    info.layout.itemAtFocusIndex(index, info.state)

internal fun storageSections(info: StorageLayoutInfo): List<com.nendo.argosy.ui.components.ListSection> =
    info.layout.buildSections(info.state)

@Composable
fun StorageSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val storage = uiState.storage
    val syncSettings = uiState.syncSettings

    val layoutState = remember(storage.platformsExpanded) {
        StorageLayoutState(storage.platformsExpanded)
    }

    val allItems = remember(storage.platformConfigs) {
        StorageItem.buildItems(storage.platformConfigs)
    }

    val layout = remember(allItems) { createStorageLayout(allItems) }
    val visibleItems = remember(layoutState, allItems) { layout.visibleItems(layoutState) }
    val sections = remember(layoutState, allItems) { layout.buildSections(layoutState) }

    fun isFocused(item: StorageItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, layoutState)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, layoutState) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is StorageItem.Header -> SectionHeader(item.title)

                is StorageItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                StorageItem.MaxDownloads -> SliderPreference(
                    title = "Max Active Downloads",
                    value = storage.maxConcurrentDownloads,
                    minValue = 1,
                    maxValue = 5,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleMaxConcurrentDownloads() }
                )

                StorageItem.Threshold -> CyclePreference(
                    title = "Instant Download Threshold",
                    value = "${storage.instantDownloadThresholdMb} MB",
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleInstantDownloadThreshold() },
                    subtitle = "Files under this size download immediately"
                )

                StorageItem.DownloadedInfo -> {
                    val downloadedText = if (storage.downloadedGamesCount > 0) {
                        val sizeText = formatFileSize(storage.downloadedGamesSize)
                        "${storage.downloadedGamesCount} games ($sizeText)"
                    } else {
                        "No games downloaded"
                    }
                    InfoDisplay(
                        title = "Downloaded",
                        value = downloadedText,
                        icon = Icons.Default.Storage
                    )
                }

                StorageItem.GlobalRomPath -> {
                    val availableText = "${formatFileSize(storage.availableSpace)} free"
                    ActionPreference(
                        icon = Icons.Default.Folder,
                        title = "Global ROM Path",
                        subtitle = formatStoragePath(storage.romStoragePath),
                        isFocused = isFocused(item),
                        trailingText = availableText,
                        onClick = { viewModel.openFolderPicker() }
                    )
                }

                StorageItem.ImageCache -> {
                    val cachePath = syncSettings.imageCachePath
                    val displayPath = if (cachePath != null) {
                        "${cachePath.substringAfterLast("/")}/argosy_images"
                    } else {
                        "Internal (default)"
                    }
                    ActionPreference(
                        icon = Icons.Default.Image,
                        title = "Image Cache",
                        subtitle = displayPath,
                        isFocused = isFocused(item),
                        onClick = { viewModel.openImageCachePicker() }
                    )
                }

                StorageItem.ValidateCache -> {
                    val validating = storage.isValidatingCache
                    ActionPreference(
                        title = "Validate Image Cache",
                        subtitle = if (validating) "Validating..." else "Remove invalid cached images",
                        isFocused = isFocused(item),
                        isEnabled = !validating,
                        onClick = { viewModel.validateImageCache() }
                    )
                }

                StorageItem.PlatformsExpand -> {
                    val customCount = storage.customPlatformCount
                    val subtitle = if (customCount > 0) {
                        "$customCount customized"
                    } else {
                        "All using global path"
                    }
                    ExpandablePreference(
                        title = "Platform Overrides",
                        subtitle = subtitle,
                        isExpanded = storage.platformsExpanded,
                        isFocused = isFocused(item),
                        onToggle = { viewModel.togglePlatformsExpanded() }
                    )
                }

                is StorageItem.PlatformItem -> PlatformStorageItem(
                    config = item.config,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openPlatformSettingsModal(item.config.platformId) }
                )

                StorageItem.PurgeAll -> {
                    val isPurging = storage.isPurgingAll
                    ActionPreference(
                        title = "Reset Library",
                        subtitle = if (isPurging) "Resetting..." else "Clear database and image cache, keep downloaded files",
                        isFocused = isFocused(item),
                        isDangerous = true,
                        isEnabled = !isPurging,
                        onClick = { viewModel.requestPurgeAll() }
                    )
                }
            }
        }
    }
}


@Composable
private fun PlatformStorageItem(
    config: PlatformStorageConfig,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val pathDisplay = if (config.customRomPath != null) {
        formatStoragePath(config.customRomPath)
    } else {
        "(auto)"
    }
    val valueText = if (config.downloadedCount > 0) {
        "${config.downloadedCount}/${config.gameCount}  •  $pathDisplay"
    } else {
        "${config.gameCount} games  •  $pathDisplay"
    }

    ExpandedChildItem(
        title = config.platformName,
        value = valueText,
        isFocused = isFocused,
        onClick = onClick
    )
}

internal fun formatFileSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${size.toLong()} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(size, units[unitIndex])
    }
}

internal fun formatStoragePath(rawPath: String): String {
    return when {
        rawPath.startsWith("/storage/emulated/0") ->
            rawPath.replace("/storage/emulated/0", "Internal")
        rawPath.startsWith("/storage/") -> {
            val parts = rawPath.removePrefix("/storage/").split("/", limit = 2)
            if (parts.size == 2) {
                "/storage/${parts[0]}/${parts[1]}"
            } else null
        }
        else -> null
    } ?: rawPath
}
