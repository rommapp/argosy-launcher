package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.ExpandedChildItem
import com.nendo.argosy.ui.screens.settings.BiosPlatformGroup
import com.nendo.argosy.ui.screens.settings.DistributeResultItem
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun BiosSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val bios = uiState.bios

    val focusMapping = buildBiosFocusMapping(
        platformGroups = bios.platformGroups,
        expandedIndex = bios.expandedPlatformIndex
    )

    val maxPlatformFocusIndex = focusMapping.getMaxFocusIndex()
    val platformListItemCount = focusMapping.getPlatformListItemCount()

    val sections = listOf(
        ListSection(listStartIndex = 0, listEndIndex = 1, focusStartIndex = 0, focusEndIndex = 1),
        ListSection(listStartIndex = 2, listEndIndex = 3 + platformListItemCount, focusStartIndex = 2, focusEndIndex = maxPlatformFocusIndex)
    )

    val focusToListIndex: (Int) -> Int = { focusIndex ->
        focusMapping.focusToScrollIndex(focusIndex)
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = focusToListIndex,
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            BiosSummaryCard(
                totalFiles = bios.totalFiles,
                downloadedFiles = bios.downloadedFiles,
                isDownloading = bios.isDownloading,
                downloadingFileName = bios.downloadingFileName,
                downloadProgress = bios.downloadProgress,
                isDistributing = bios.isDistributing,
                isFocused = uiState.focusedIndex == 0,
                actionIndex = bios.actionIndex,
                onDownloadAll = { viewModel.downloadAllBios() },
                onDistributeAll = { viewModel.distributeAllBios() }
            )
        }

        item {
            val pathDisplay = bios.customBiosPath?.let {
                "${it.substringAfterLast("/")}/bios"
            } ?: "Internal (default)"

            ActionPreference(
                icon = Icons.Default.Folder,
                title = "BIOS Directory",
                subtitle = pathDisplay,
                isFocused = uiState.focusedIndex == 1,
                onClick = { viewModel.openBiosFolderPicker() }
            )
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "PLATFORMS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        if (bios.platformGroups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.spacingLg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No BIOS files synced yet. Sync your library to discover available firmware.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(bios.platformGroups) { index, group ->
                val baseFocusIndex = 2 + index + focusMapping.getExpandedItemsBefore(index)
                val isExpanded = bios.expandedPlatformIndex == index

                val itemFocused = uiState.focusedIndex == baseFocusIndex
                BiosPlatformItem(
                    group = group,
                    isFocused = itemFocused,
                    isExpanded = isExpanded,
                    subFocusIndex = if (itemFocused) bios.platformSubFocusIndex else 0,
                    onClick = { viewModel.toggleBiosPlatformExpanded(index) },
                    onDownload = { viewModel.downloadBiosForPlatform(group.platformSlug) }
                )

                if (isExpanded) {
                    group.firmwareItems.forEachIndexed { fileIndex, firmware ->
                        val fileFocusIndex = baseFocusIndex + 1 + fileIndex
                        ExpandedChildItem(
                            title = firmware.fileName,
                            value = if (firmware.isDownloaded) "Downloaded" else formatFileSize(firmware.fileSizeBytes),
                            isFocused = uiState.focusedIndex == fileFocusIndex,
                            onClick = {
                                if (!firmware.isDownloaded) {
                                    viewModel.downloadSingleBios(firmware.rommId)
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "BIOS files are downloaded from your RomM server and stored locally. " +
                    "Use 'Distribute' to copy them to emulator directories.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = Dimens.spacingSm)
            )
        }
    }

    if (bios.showDistributeResultModal) {
        DistributeResultModal(
            results = bios.distributeResults,
            onDismiss = { viewModel.dismissDistributeResultModal() }
        )
    }
}

@Composable
private fun DistributeResultModal(
    results: List<DistributeResultItem>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Dimens.spacingMd)
        ) {
            Text(
                text = "Distribution Complete",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            val totalFiles = results.sumOf { emulator ->
                emulator.platformResults.sumOf { it.filesCopied }
            }
            Text(
                text = "Copied $totalFiles BIOS files to ${results.size} emulators",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            LazyColumn(
                modifier = Modifier.heightIn(max = Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(results) { _, emulator ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.radiusMd))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(Dimens.spacingSm)
                    ) {
                        Text(
                            text = emulator.emulatorName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        emulator.platformResults.forEach { platform ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = Dimens.spacingMd, top = Dimens.spacingXs),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = platform.platformName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${platform.filesCopied} files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDismiss() }
                    .padding(Dimens.spacingSm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun BiosSummaryCard(
    totalFiles: Int,
    downloadedFiles: Int,
    isDownloading: Boolean,
    downloadingFileName: String?,
    downloadProgress: Float,
    isDistributing: Boolean,
    isFocused: Boolean,
    actionIndex: Int,
    onDownloadAll: () -> Unit,
    onDistributeAll: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val missingFiles = totalFiles - downloadedFiles
    val isComplete = totalFiles > 0 && missingFiles == 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(backgroundColor)
            .padding(Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(Dimens.iconMd)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Column {
                    Text(
                        text = "BIOS Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    Text(
                        text = when {
                            totalFiles == 0 -> "No BIOS files found"
                            isComplete -> "All $totalFiles files ready"
                            else -> "$downloadedFiles of $totalFiles downloaded"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }

        if (isDownloading) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = "Downloading: ${downloadingFileName ?: "..."}",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }

        if (isDistributing) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.spacingMd),
                    strokeWidth = Dimens.borderMedium,
                    color = contentColor
                )
                Text(
                    text = "Distributing to emulators...",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }

        if (totalFiles > 0 && !isDownloading && !isDistributing) {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                val downloadEnabled = missingFiles > 0
                val downloadSelected = isFocused && actionIndex == 0
                val downloadBgColor = when {
                    downloadSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val downloadTextColor = when {
                    downloadSelected -> MaterialTheme.colorScheme.onPrimary
                    !downloadEnabled -> contentColor.copy(alpha = 0.5f)
                    else -> contentColor
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(downloadBgColor)
                        .clickable(
                            enabled = downloadEnabled,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onDownloadAll() }
                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = downloadTextColor,
                            modifier = Modifier.size(Dimens.spacingMd)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacingXs))
                        Text(
                            text = if (missingFiles > 0) "Download $missingFiles" else "Complete",
                            style = MaterialTheme.typography.labelMedium,
                            color = downloadTextColor
                        )
                    }
                }

                val distributeSelected = isFocused && actionIndex == 1
                val distributeEnabled = downloadedFiles > 0
                val distributeBgColor = when {
                    distributeSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val distributeTextColor = when {
                    distributeSelected -> MaterialTheme.colorScheme.onPrimary
                    !distributeEnabled -> contentColor.copy(alpha = 0.5f)
                    else -> contentColor
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(distributeBgColor)
                        .clickable(
                            enabled = distributeEnabled,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onDistributeAll() }
                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Distribute",
                        style = MaterialTheme.typography.labelMedium,
                        color = distributeTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun BiosPlatformItem(
    group: BiosPlatformGroup,
    isFocused: Boolean,
    isExpanded: Boolean,
    subFocusIndex: Int,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val expandSubFocused = isFocused && subFocusIndex == 0
    val downloadSubFocused = isFocused && subFocusIndex == 1

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(backgroundColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (group.isComplete) Icons.Default.CheckCircle else Icons.Default.Memory,
            contentDescription = null,
            tint = if (group.isComplete) MaterialTheme.colorScheme.primary else contentColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.platformName,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = group.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }

        if (!group.isComplete) {
            val downloadBgColor = if (downloadSubFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            }
            val downloadTextColor = if (downloadSubFocused) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(downloadBgColor)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDownload() }
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
            ) {
                Text(
                    text = "Download",
                    style = MaterialTheme.typography.labelSmall,
                    color = downloadTextColor
                )
            }
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }

        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.5f),
            modifier = Modifier.size(Dimens.iconSm)
        )
    }
}

private data class BiosFocusMapping(
    val platformGroups: List<BiosPlatformGroup>,
    val expandedIndex: Int
) {
    fun getExpandedItemsBefore(platformIndex: Int): Int {
        if (expandedIndex < 0 || expandedIndex >= platformIndex) return 0
        return platformGroups.getOrNull(expandedIndex)?.firmwareItems?.size ?: 0
    }

    fun getMaxFocusIndex(): Int {
        val expandedItemCount = platformGroups.getOrNull(expandedIndex)?.firmwareItems?.size ?: 0
        return 1 + platformGroups.size + expandedItemCount
    }

    fun getPlatformListItemCount(): Int {
        val expandedItemCount = platformGroups.getOrNull(expandedIndex)?.firmwareItems?.size ?: 0
        return platformGroups.size + expandedItemCount
    }

    fun focusToScrollIndex(focusIndex: Int): Int {
        return when {
            focusIndex == 0 -> 0
            focusIndex == 1 -> 1
            else -> {
                val baseIndex = 3
                var scrollIndex = baseIndex
                var currentFocus = 2

                for ((index, group) in platformGroups.withIndex()) {
                    if (currentFocus == focusIndex) return scrollIndex

                    scrollIndex++
                    currentFocus++

                    if (index == expandedIndex) {
                        for (i in group.firmwareItems.indices) {
                            if (currentFocus == focusIndex) return scrollIndex
                            scrollIndex++
                            currentFocus++
                        }
                    }
                }
                scrollIndex
            }
        }
    }
}

private fun buildBiosFocusMapping(
    platformGroups: List<BiosPlatformGroup>,
    expandedIndex: Int
): BiosFocusMapping = BiosFocusMapping(platformGroups, expandedIndex)

internal fun biosSections(
    platformGroups: List<BiosPlatformGroup>,
    expandedIndex: Int
): List<ListSection> {
    val focusMapping = buildBiosFocusMapping(platformGroups, expandedIndex)
    val maxPlatformFocusIndex = focusMapping.getMaxFocusIndex()
    val platformListItemCount = focusMapping.getPlatformListItemCount()
    return listOf(
        ListSection(listStartIndex = 0, listEndIndex = 1, focusStartIndex = 0, focusEndIndex = 1),
        ListSection(listStartIndex = 2, listEndIndex = 3 + platformListItemCount, focusStartIndex = 2, focusEndIndex = maxPlatformFocusIndex)
    )
}
