package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun StorageSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val storage = uiState.storage
    val syncSettings = uiState.syncSettings

    val downloadedText = if (storage.downloadedGamesCount > 0) {
        val sizeText = formatFileSize(storage.downloadedGamesSize)
        "${storage.downloadedGamesCount} games ($sizeText)"
    } else {
        "No games downloaded"
    }

    val focusMapping = buildFocusMapping(
        platformsExpanded = storage.platformsExpanded,
        platformCount = storage.platformConfigs.size
    )

    LaunchedEffect(uiState.focusedIndex) {
        val scrollIndex = focusMapping.focusToScrollIndex(uiState.focusedIndex)
        if (scrollIndex >= 0) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            listState.animateScrollToItem(scrollIndex, -centerOffset + paddingBuffer)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        // DOWNLOADS header
        item { SectionHeader("DOWNLOADS") }

        item {
            SliderPreference(
                title = "Max Active Downloads",
                value = storage.maxConcurrentDownloads,
                minValue = 1,
                maxValue = 5,
                isFocused = uiState.focusedIndex == focusMapping.maxDownloadsIndex,
                onClick = { viewModel.cycleMaxConcurrentDownloads() }
            )
        }

        item {
            CyclePreference(
                title = "Instant Download Threshold",
                value = "${storage.instantDownloadThresholdMb} MB",
                isFocused = uiState.focusedIndex == focusMapping.thresholdIndex,
                onClick = { viewModel.cycleInstantDownloadThreshold() },
                subtitle = "Files under this size download immediately"
            )
        }

        item {
            InfoDisplay(
                title = "Downloaded",
                value = downloadedText,
                icon = Icons.Default.Storage
            )
        }

        // FILE LOCATIONS header
        item { Spacer(modifier = Modifier.height(Dimens.spacingMd)) }
        item { SectionHeader("FILE LOCATIONS") }

        item {
            val availableText = "${formatFileSize(storage.availableSpace)} free"
            ActionPreference(
                icon = Icons.Default.Folder,
                title = "Global ROM Path",
                subtitle = formatStoragePath(storage.romStoragePath),
                isFocused = uiState.focusedIndex == focusMapping.globalRomPathIndex,
                trailingText = availableText,
                onClick = { viewModel.openFolderPicker() }
            )
        }

        item {
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
                isFocused = uiState.focusedIndex == focusMapping.imageCacheIndex,
                onClick = { viewModel.openImageCachePicker() }
            )
        }

        item {
            val validating = storage.isValidatingCache
            ActionPreference(
                title = "Validate Image Cache",
                subtitle = if (validating) "Validating..." else "Remove invalid cached images",
                isFocused = uiState.focusedIndex == focusMapping.validateCacheIndex,
                isEnabled = !validating,
                onClick = { viewModel.validateImageCache() }
            )
        }

        // PLATFORM STORAGE header
        item { Spacer(modifier = Modifier.height(Dimens.spacingMd)) }
        item { SectionHeader("PLATFORM STORAGE") }

        item {
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
                isFocused = uiState.focusedIndex == focusMapping.platformsExpandIndex,
                onToggle = { viewModel.togglePlatformsExpanded() }
            )
        }

        if (storage.platformsExpanded) {
            itemsIndexed(storage.platformConfigs) { index, config ->
                val focusIndex = focusMapping.platformsExpandIndex + 1 + index
                PlatformStorageItem(
                    config = config,
                    isFocused = uiState.focusedIndex == focusIndex,
                    onClick = { viewModel.openPlatformSettingsModal(config.platformId) }
                )
            }
        }

    }
}

private data class FocusMapping(
    val maxDownloadsIndex: Int = 0,
    val thresholdIndex: Int = 1,
    val globalRomPathIndex: Int = 2,
    val imageCacheIndex: Int = 3,
    val validateCacheIndex: Int = 4,
    val platformsExpandIndex: Int = 5,
    val platformCount: Int = 0,
    val maxIndex: Int = 5
) {
    fun focusToScrollIndex(focusIndex: Int): Int {
        return when {
            focusIndex == maxDownloadsIndex -> 1
            focusIndex == thresholdIndex -> 2
            focusIndex == globalRomPathIndex -> 5
            focusIndex == imageCacheIndex -> 6
            focusIndex == validateCacheIndex -> 7
            focusIndex == platformsExpandIndex -> 10
            focusIndex > platformsExpandIndex -> {
                11 + (focusIndex - platformsExpandIndex - 1)
            }
            else -> focusIndex
        }
    }
}

private fun buildFocusMapping(
    platformsExpanded: Boolean,
    platformCount: Int
): FocusMapping {
    val maxDownloadsIndex = 0
    val thresholdIndex = 1
    val globalRomPathIndex = 2
    val imageCacheIndex = 3
    val validateCacheIndex = 4
    val platformsExpandIndex = 5

    val expandedPlatformItems = if (platformsExpanded) platformCount else 0
    val maxIndex = platformsExpandIndex + expandedPlatformItems

    return FocusMapping(
        maxDownloadsIndex = maxDownloadsIndex,
        thresholdIndex = thresholdIndex,
        globalRomPathIndex = globalRomPathIndex,
        imageCacheIndex = imageCacheIndex,
        validateCacheIndex = validateCacheIndex,
        platformsExpandIndex = platformsExpandIndex,
        platformCount = expandedPlatformItems,
        maxIndex = maxIndex
    )
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
