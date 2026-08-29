package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.util.formatBytes
import com.nendo.argosy.ui.components.ExpandedChildItem
import com.nendo.argosy.ui.components.ImageCachePreference
import com.nendo.argosy.ui.screens.settings.BiosFirmwareItem
import com.nendo.argosy.ui.screens.settings.BiosPlatformGroup
import com.nendo.argosy.ui.screens.settings.BiosDownloadFailureItem
import com.nendo.argosy.ui.screens.settings.DistributeResultItem
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import kotlinx.coroutines.launch

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
                    downloadingFileIndex = bios.downloadingFileIndex,
                    downloadingFileCount = bios.downloadingFileCount,
                    downloadedBytes = bios.downloadedBytes,
                    downloadTotalBytes = bios.downloadTotalBytes,
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
                    } ?: stringResource(R.string.settings_bios_path_internal)

                    ImageCachePreference(
                        title = stringResource(R.string.settings_bios_path_title),
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
                        text = stringResource(R.string.settings_bios_section_platforms),
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
                            text = stringResource(R.string.settings_bios_empty_notice),
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
                            item.firmware.isDownloaded ->
                                stringResource(R.string.settings_bios_firmware_downloaded)
                            isThisDownloading ->
                                stringResource(R.string.settings_bios_firmware_downloading)
                            else -> formatBytes(item.firmware.fileSizeBytes)
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
                        text = stringResource(R.string.settings_bios_footer_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                    )
                }
            }
        }
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
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { (Dimens.menuRowHeight * 3).toPx() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            private fun scroll(direction: Int) {
                scope.launch { listState.animateScrollBy(direction * scrollStepPx) }
            }

            override fun onUp(): InputResult {
                scroll(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                scroll(1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnDismiss()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = true, handler = inputHandler)

    ModalScaffold(
        visible = true,
        onDismiss = onDismiss,
        maxWidth = Dimens.modalWidth
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.settings_bios_distribute_result_title),
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            val totalFiles = results.sumOf { emulator ->
                emulator.platformResults.sumOf { it.filesCopied }
            }
            Text(
                text = stringResource(
                    R.string.settings_bios_distribute_result_message,
                    totalFiles,
                    results.size
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim
            )

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                items(results.size, key = { results[it].emulatorName }) { index ->
                    val emulator = results[index]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.radiusMd))
                            .background(theme.surfaceElevated)
                            .padding(Dimens.spacingSm)
                    ) {
                        Text(
                            text = emulator.emulatorName,
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.textPrimary
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
                                    color = theme.textDim
                                )
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.settings_bios_distribute_result_files,
                                        platform.filesCopied,
                                        platform.filesCopied
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.textMute
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ActionButton(
                label = stringResource(R.string.settings_bios_distribute_result_dismiss),
                onClick = onDismiss,
                primary = true,
                focused = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun BiosDownloadFailureModal(
    failures: List<BiosDownloadFailureItem>,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { (Dimens.menuRowHeight * 3).toPx() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            private fun scroll(direction: Int) {
                scope.launch { listState.animateScrollBy(direction * scrollStepPx) }
            }

            override fun onUp(): InputResult {
                scroll(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                scroll(1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnDismiss()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = true, handler = inputHandler)

    ModalScaffold(
        visible = true,
        onDismiss = onDismiss,
        maxWidth = Dimens.modalWidth
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.settings_bios_download_failed_title),
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Text(
                text = pluralStringResource(
                    R.plurals.settings_bios_download_failed_message,
                    failures.size,
                    failures.size
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim
            )

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                items(failures.size, key = { failures[it].fileName }) { index ->
                    val failure = failures[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.radiusMd))
                            .background(theme.surfaceElevated)
                            .padding(Dimens.spacingSm),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = failure.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textPrimary
                        )
                        Text(
                            text = failure.platformName,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textMute
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ActionButton(
                label = stringResource(R.string.settings_bios_download_failed_dismiss),
                onClick = onDismiss,
                primary = true,
                focused = true,
                modifier = Modifier.fillMaxWidth()
            )
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
                .clip(RoundedCornerShape(Dimens.radiusPanel))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.settings_bios_gpu_prompt_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            if (gpuName != null) {
                Text(
                    text = stringResource(R.string.settings_bios_gpu_prompt_detected, gpuName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
            }

            Text(
                text = stringResource(R.string.settings_bios_gpu_prompt_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            if (isInstalling) {
                Text(
                    text = stringResource(R.string.settings_bios_gpu_prompt_installing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                ArgosyProgressBar(progress = installProgress)
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    ActionButton(
                        label = if (driverName != null && driverVersion != null) {
                            stringResource(R.string.settings_bios_gpu_prompt_install_versioned, driverVersion)
                        } else {
                            stringResource(R.string.settings_bios_gpu_prompt_install)
                        },
                        onClick = onInstallRecommended,
                        focused = focusIndex == 0,
                        primary = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ActionButton(
                        label = stringResource(R.string.settings_bios_gpu_prompt_install_from_file),
                        onClick = onInstallFromFile,
                        focused = focusIndex == 1,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ActionButton(
                        label = stringResource(R.string.settings_bios_gpu_prompt_skip),
                        onClick = onSkip,
                        focused = focusIndex == 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
    downloadingFileIndex: Int,
    downloadingFileCount: Int,
    downloadedBytes: Long,
    downloadTotalBytes: Long,
    isDistributing: Boolean,
    isFocused: Boolean,
    actionIndex: Int,
    onDownloadAll: () -> Unit,
    onDistributeAll: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isFocused) {
        lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val missingFiles = totalFiles - downloadedFiles
    val isComplete = totalFiles > 0 && missingFiles == 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusControl))
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
                        text = stringResource(R.string.settings_bios_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    Text(
                        text = when {
                            totalFiles == 0 -> stringResource(R.string.settings_bios_summary_empty)
                            isComplete ->
                                stringResource(R.string.settings_bios_summary_complete, totalFiles)
                            else -> stringResource(
                                R.string.settings_bios_summary_partial,
                                downloadedFiles,
                                totalFiles
                            )
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
                text = stringResource(
                    R.string.settings_bios_summary_downloading,
                    downloadingFileName ?: "..."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            ArgosyProgressBar(progress = downloadProgress)
            if (downloadTotalBytes > 0 || downloadingFileCount > 1) {
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (downloadTotalBytes > 0) {
                            stringResource(
                                R.string.settings_bios_summary_downloading_size,
                                formatBytes(downloadedBytes),
                                formatBytes(downloadTotalBytes)
                            )
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.5f)
                    )
                    if (downloadingFileCount > 1) {
                        Text(
                            text = stringResource(
                                R.string.settings_bios_summary_downloading_count,
                                downloadingFileIndex,
                                downloadingFileCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
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
                    text = stringResource(R.string.settings_bios_summary_distributing),
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
                ActionButton(
                    onClick = onDownloadAll,
                    focused = downloadSelected,
                    primary = true,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(Dimens.spacingMd)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = if (missingFiles > 0) {
                            stringResource(R.string.settings_bios_summary_download_missing, missingFiles)
                        } else {
                            stringResource(R.string.settings_bios_summary_redownload)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                val distributeSelected = isFocused && actionIndex == 1
                val distributeEnabled = downloadedFiles > 0
                ActionButton(
                    label = stringResource(R.string.settings_bios_summary_distribute),
                    onClick = onDistributeAll,
                    focused = distributeSelected,
                    enabled = distributeEnabled,
                    modifier = Modifier.weight(1f)
                )
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
        LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isFocused) {
        lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(RoundedCornerShape(Dimens.radiusControl))
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
                text = if (group.isComplete) {
                    stringResource(R.string.settings_bios_platform_redownload)
                } else {
                    stringResource(R.string.settings_bios_platform_download)
                },
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

