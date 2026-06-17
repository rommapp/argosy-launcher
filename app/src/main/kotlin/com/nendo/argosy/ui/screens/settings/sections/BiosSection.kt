package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.nendo.argosy.ui.components.ExpandedChildItem
import com.nendo.argosy.ui.components.ImageCachePreference
import com.nendo.argosy.ui.screens.settings.BiosFirmwareItem
import com.nendo.argosy.ui.screens.settings.BiosPlatformGroup
import com.nendo.argosy.ui.screens.settings.DistributeResultItem
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme

// --- Item definitions ---

internal sealed class BiosItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = this !is PlatformsHeader && this !is EmptyNotice && this !is FooterNote

    data object Summary : BiosItem("summary", "actions")
    data object BiosPath : BiosItem("biosPath", "actions")
    data object PlatformsHeader : BiosItem("platformsHeader", "platforms")
    data object EmptyNotice : BiosItem("emptyNotice", "platforms")
    data class Platform(val group: BiosPlatformGroup, val index: Int) : BiosItem("platform_${group.platformSlug}", "platforms")
    data class FirmwareFile(val firmware: BiosFirmwareItem, val platformIndex: Int, val fileIndex: Int) :
        BiosItem("firmware_${firmware.id}", "platforms")
    data object FooterNote : BiosItem("footerNote", "footer")
}

internal fun buildBiosItems(
    platformGroups: List<BiosPlatformGroup>,
    expandedIndex: Int
): List<BiosItem> = buildList {
    add(BiosItem.Summary)
    add(BiosItem.BiosPath)
    add(BiosItem.PlatformsHeader)

    if (platformGroups.isEmpty()) {
        add(BiosItem.EmptyNotice)
    } else {
        for ((index, group) in platformGroups.withIndex()) {
            add(BiosItem.Platform(group, index))
            if (index == expandedIndex) {
                for ((fileIndex, firmware) in group.firmwareItems.withIndex()) {
                    add(BiosItem.FirmwareFile(firmware, index, fileIndex))
                }
            }
        }
    }

    add(BiosItem.FooterNote)
}

internal fun createBiosLayout(items: List<BiosItem>) =
    SettingsLayout<BiosItem, Unit>(
        allItems = items,
        isFocusable = { it.isFocusable },
        visibleWhen = { _, _ -> true },
        sectionOf = { it.section }
    )

internal data class BiosLayoutInfo(
    val layout: SettingsLayout<BiosItem, Unit>,
    val items: List<BiosItem>
)

internal fun createBiosLayoutInfo(
    platformGroups: List<BiosPlatformGroup>,
    expandedIndex: Int
): BiosLayoutInfo {
    val items = buildBiosItems(platformGroups, expandedIndex)
    return BiosLayoutInfo(createBiosLayout(items), items)
}

internal fun biosItemAtFocusIndex(
    index: Int,
    platformGroups: List<BiosPlatformGroup>,
    expandedIndex: Int
): BiosItem? {
    val items = buildBiosItems(platformGroups, expandedIndex)
    return createBiosLayout(items).itemAtFocusIndex(index, Unit)
}

internal fun biosMaxFocusIndex(
    platformGroups: List<BiosPlatformGroup>,
    expandedIndex: Int
): Int {
    val items = buildBiosItems(platformGroups, expandedIndex)
    return createBiosLayout(items).maxFocusIndex(Unit)
}

internal fun biosSections(
    platformGroups: List<BiosPlatformGroup>,
    expandedIndex: Int
): List<ListSection> {
    val items = buildBiosItems(platformGroups, expandedIndex)
    return createBiosLayout(items).buildSections(Unit)
}

@Composable
fun BiosSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val bios = uiState.bios

    val allItems = remember(bios.platformGroups, bios.expandedPlatformIndex) {
        buildBiosItems(bios.platformGroups, bios.expandedPlatformIndex)
    }
    val layout = remember(allItems) { createBiosLayout(allItems) }
    val visibleItems = remember(allItems) { layout.visibleItems(Unit) }
    val sections = remember(allItems) { layout.buildSections(Unit) }

    fun isFocused(item: BiosItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, Unit)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems.size, key = { visibleItems[it].key }) { index ->
            val item = visibleItems[index]
            when (item) {
                BiosItem.Summary -> BiosSummaryCard(
                    totalFiles = bios.totalFiles,
                    downloadedFiles = bios.downloadedFiles,
                    isDownloading = bios.isDownloading,
                    downloadingFileName = bios.downloadingFileName,
                    downloadProgress = bios.downloadProgress,
                    isDistributing = bios.isDistributing,
                    isFocused = isFocused(item),
                    actionIndex = bios.actionIndex,
                    onDownloadAll = { viewModel.downloadAllBios() },
                    onDistributeAll = { viewModel.distributeAllBios() }
                )

                BiosItem.BiosPath -> {
                    val pathDisplay = bios.customBiosPath?.let { path ->
                        val folderName = path.substringAfterLast("/")
                        if (folderName.equals("bios", ignoreCase = true)) {
                            folderName
                        } else {
                            "$folderName/bios"
                        }
                    } ?: "Internal (default)"

                    ImageCachePreference(
                        title = "BIOS Directory",
                        displayPath = pathDisplay,
                        hasCustomPath = bios.customBiosPath != null,
                        isFocused = isFocused(item),
                        actionIndex = bios.biosPathActionIndex,
                        isMigrating = bios.isBiosMigrating,
                        onChange = { viewModel.openBiosFolderPicker() },
                        onReset = { viewModel.resetBiosToDefault() }
                    )
                }

                BiosItem.PlatformsHeader -> {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    Text(
                        text = "PLATFORMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Dimens.spacingSm)
                    )
                }

                BiosItem.EmptyNotice -> {
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

                is BiosItem.Platform -> {
                    val isExpanded = bios.expandedPlatformIndex == item.index
                    val itemFocused = isFocused(item)
                    BiosPlatformItem(
                        group = item.group,
                        isFocused = itemFocused,
                        isExpanded = isExpanded,
                        subFocusIndex = if (itemFocused) bios.platformSubFocusIndex else 0,
                        onClick = { viewModel.toggleBiosPlatformExpanded(item.index) },
                        onDownload = { viewModel.downloadBiosForPlatform(item.group.platformSlug) }
                    )
                }

                is BiosItem.FirmwareFile -> {
                    val isThisDownloading = bios.isDownloading &&
                        bios.downloadingFileName == item.firmware.fileName
                    ExpandedChildItem(
                        title = item.firmware.fileName,
                        value = when {
                            item.firmware.isDownloaded -> "Downloaded"
                            isThisDownloading -> "Downloading..."
                            else -> formatFileSize(item.firmware.fileSizeBytes)
                        },
                        isFocused = isFocused(item),
                        onClick = {
                            if (!item.firmware.isDownloaded && !isThisDownloading) {
                                viewModel.downloadSingleBios(item.firmware.rommId)
                            }
                        }
                    )
                }

                BiosItem.FooterNote -> {
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
        }
    }

    if (bios.showDistributeResultModal) {
        DistributeResultModal(
            results = bios.distributeResults,
            onDismiss = { viewModel.dismissDistributeResultModal() }
        )
    }

    if (bios.showGpuDriverPrompt) {
        GpuDriverPromptModal(
            gpuName = bios.deviceGpuName,
            driverName = bios.gpuDriverInfo?.name,
            driverVersion = bios.gpuDriverInfo?.version,
            isInstalling = bios.gpuDriverInfo?.isInstalling == true,
            installProgress = bios.gpuDriverInfo?.installProgress ?: 0f,
            focusIndex = bios.gpuDriverPromptFocusIndex,
            onInstallRecommended = { viewModel.installGpuDriver() },
            onInstallFromFile = { viewModel.openGpuDriverFilePicker() },
            onSkip = { viewModel.dismissGpuDriverPrompt() }
        )
    }
}

@Composable
internal fun DistributeResultModal(
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
                items(results.size) { index ->
                    val emulator = results[index]
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
                    .clickableNoFocus { onDismiss() }
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
private fun GpuDriverPromptModal(
    gpuName: String?,
    driverName: String?,
    driverVersion: String?,
    isInstalling: Boolean,
    installProgress: Float,
    focusIndex: Int,
    onInstallRecommended: () -> Unit,
    onInstallFromFile: () -> Unit,
    onSkip: () -> Unit
) {
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(enabled = !isInstalling) { onSkip() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidth)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingMd)
        ) {
            Text(
                text = "GPU Driver Available",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            if (gpuName != null) {
                Text(
                    text = "Detected: $gpuName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
            }

            Text(
                text = "A custom GPU driver can improve Switch emulation performance on Snapdragon 8 devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            if (isInstalling) {
                Text(
                    text = "Installing driver...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                LinearProgressIndicator(
                    progress = { installProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    GpuDriverOptionButton(
                        text = if (driverName != null && driverVersion != null) {
                            "Install Recommended ($driverVersion)"
                        } else {
                            "Install Recommended"
                        },
                        isSelected = focusIndex == 0,
                        onClick = onInstallRecommended
                    )

                    GpuDriverOptionButton(
                        text = "Install from File",
                        isSelected = focusIndex == 1,
                        onClick = onInstallFromFile
                    )

                    GpuDriverOptionButton(
                        text = "Skip",
                        isSelected = focusIndex == 2,
                        onClick = onSkip
                    )
                }
            }
        }
    }
}

@Composable
private fun GpuDriverOptionButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickableNoFocus { onClick() }
            .padding(Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
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
                val downloadSelected = isFocused && actionIndex == 0
                val downloadBgColor = when {
                    downloadSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val downloadTextColor = when {
                    downloadSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> contentColor
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(downloadBgColor)
                        .clickableNoFocus { onDownloadAll() }
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
                            text = if (missingFiles > 0) "Download $missingFiles" else "Redownload",
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
                        .clickableNoFocus(enabled = distributeEnabled) { onDistributeAll() }
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
            .clickableNoFocus { onClick() }
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
                .clickableNoFocus { onDownload() }
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
        ) {
            Text(
                text = if (group.isComplete) "Redownload" else "Download",
                style = MaterialTheme.typography.labelSmall,
                color = downloadTextColor
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.5f),
            modifier = Modifier.size(Dimens.iconSm)
        )
    }
}

