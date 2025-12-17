package com.nendo.argosy.ui.screens.settings.sections

import android.os.Build
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.InfoPreference
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
    val showPermissionRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val baseItemCount = if (showPermissionRow) 4 else 3

    val downloadedText = if (uiState.storage.downloadedGamesCount > 0) {
        val sizeText = formatFileSize(uiState.storage.downloadedGamesSize)
        "${uiState.storage.downloadedGamesCount} games ($sizeText)"
    } else {
        "No games downloaded"
    }

    val totalItems = baseItemCount + uiState.storage.platformConfigs.size
    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0 until totalItems) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            val lazyColumnIndex = if (uiState.focusedIndex >= baseItemCount && uiState.storage.platformConfigs.isNotEmpty()) {
                uiState.focusedIndex + 2
            } else {
                uiState.focusedIndex
            }
            listState.animateScrollToItem(lazyColumnIndex, -centerOffset + paddingBuffer)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (showPermissionRow) {
            item {
                val hasAccess = uiState.storage.hasAllFilesAccess
                ActionPreference(
                    icon = Icons.Default.Security,
                    title = "All Files Access",
                    subtitle = if (hasAccess) "Permission granted" else "Required for extracting archives",
                    isFocused = uiState.focusedIndex == 0,
                    trailingText = if (hasAccess) "Enabled" else "Disabled",
                    onClick = { if (!hasAccess) viewModel.requestStoragePermission() }
                )
            }
        }
        item {
            val availableText = "${formatFileSize(uiState.storage.availableSpace)} free"
            val focusOffset = if (showPermissionRow) 1 else 0
            ActionPreference(
                icon = Icons.Default.Folder,
                title = "Download Location",
                subtitle = formatStoragePath(uiState.storage.romStoragePath),
                isFocused = uiState.focusedIndex == focusOffset,
                trailingText = availableText,
                onClick = { viewModel.openFolderPicker() }
            )
        }
        item {
            val focusOffset = if (showPermissionRow) 2 else 1
            SliderPreference(
                title = "Max Active Downloads",
                value = uiState.storage.maxConcurrentDownloads,
                minValue = 1,
                maxValue = 5,
                isFocused = uiState.focusedIndex == focusOffset,
                onClick = { viewModel.cycleMaxConcurrentDownloads() }
            )
        }
        item {
            val focusOffset = if (showPermissionRow) 3 else 2
            InfoPreference(
                title = "Downloaded",
                value = downloadedText,
                isFocused = uiState.focusedIndex == focusOffset,
                icon = Icons.Default.Storage
            )
        }

        if (uiState.storage.platformConfigs.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(Dimens.spacingMd)) }
            item { SectionHeader("PLATFORM STORAGE") }

            itemsIndexed(uiState.storage.platformConfigs) { index, config ->
                val focusOffset = baseItemCount + index
                PlatformStorageItem(
                    config = config,
                    isFocused = uiState.focusedIndex == focusOffset,
                    onClick = { viewModel.openPlatformSettingsModal(config.platformId) }
                )
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
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val pathDisplay = if (config.customRomPath != null) {
        formatStoragePath(config.customRomPath)
    } else {
        "(auto)"
    }

    val gameCountText = if (config.downloadedCount > 0) {
        "${config.downloadedCount}/${config.gameCount} installed"
    } else {
        "${config.gameCount} games"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusLg))
            .clickable(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.platformName,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = "$gameCountText  •  $pathDisplay",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }
        Text(
            text = if (config.syncEnabled) "ON" else "OFF",
            style = MaterialTheme.typography.labelMedium,
            color = if (config.syncEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                secondaryColor
            }
        )
    }
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
