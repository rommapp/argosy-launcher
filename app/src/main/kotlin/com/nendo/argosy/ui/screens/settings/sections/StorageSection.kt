package com.nendo.argosy.ui.screens.settings.sections

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun StorageSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val showPermissionRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    val downloadedText = if (uiState.storage.downloadedGamesCount > 0) {
        val sizeText = formatFileSize(uiState.storage.downloadedGamesSize)
        "${uiState.storage.downloadedGamesCount} games ($sizeText)"
    } else {
        "No games downloaded"
    }

    val maxIndex = if (showPermissionRow) 3 else 2
    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..maxIndex) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset + paddingBuffer)
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
