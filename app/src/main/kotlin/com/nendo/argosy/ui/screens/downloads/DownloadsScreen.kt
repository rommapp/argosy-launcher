package com.nendo.argosy.ui.screens.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.resolve
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.toNotificationText
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatBytes

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onDrawerToggle: () -> Unit,
    onNavigateToGame: (Long) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onNavigateToGame) {
        viewModel.createInputHandler(
            onBack = onBack,
            onNavigateToGame = onNavigateToGame
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_DOWNLOADS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_DOWNLOADS)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.downloadState
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.allItems.isNotEmpty()) {
            listState.animateScrollToItem(uiState.focusedIndex)
        }
    }

    val hasAnyDownloads = state.activeDownloads.isNotEmpty() ||
        state.queue.isNotEmpty() ||
        state.completed.isNotEmpty()

    if (!hasAnyDownloads) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconXl),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text(
                    text = stringResource(R.string.downloads_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = stringResource(R.string.downloads_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Dimens.spacingLg, end = Dimens.spacingLg, top = Dimens.spacingLg, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
        ) {
            val activeGroups = uiState.activeGroups
            val queuedGroups = uiState.queuedGroups
            val completedGroups = uiState.completedGroups

            if (activeGroups.isNotEmpty()) {
                val hasExtracting = activeGroups.any { it.aggregate(context).state == DownloadState.EXTRACTING }
                val hasMoving = activeGroups.any { it.aggregate(context).state == DownloadState.MOVING }
                val totalSpeed = activeGroups.sumOf { it.aggregate(context).bytesPerSecond }
                item {
                    val headerText = stringResource(
                        when {
                            hasMoving -> R.string.downloads_section_header_moving
                            hasExtracting -> R.string.downloads_section_header_extracting
                            totalSpeed > 0 -> R.string.downloads_section_header_downloading
                            else -> R.string.downloads_section_header_active
                        }
                    )
                    SectionHeader(headerText, if (totalSpeed > 0) formatSpeed(totalSpeed) else null)
                }
                itemsIndexed(activeGroups, key = { _, g -> g.primary.id }) { index, group ->
                    Column {
                        DownloadItem(
                            download = group.aggregate(context),
                            isInActiveList = true,
                            isFocused = index == uiState.focusedIndex,
                            availableStorage = state.availableStorageBytes
                        )
                        if (group.isGroup) GroupFileRows(group)
                    }
                }
            }

            if (queuedGroups.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.downloads_section_header_queued)) }
                itemsIndexed(queuedGroups, key = { _, g -> g.primary.id }) { index, group ->
                    Column {
                        DownloadItem(
                            download = group.aggregate(context),
                            isInActiveList = false,
                            isFocused = (activeGroups.size + index) == uiState.focusedIndex,
                            availableStorage = state.availableStorageBytes
                        )
                        if (group.isGroup) GroupFileRows(group)
                    }
                }
            }

            if (completedGroups.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.downloads_section_header_finished)) }
                val completedStartIndex = activeGroups.size + queuedGroups.size
                itemsIndexed(completedGroups, key = { _, g -> g.primary.id }) { index, group ->
                    CompletedDownloadItem(
                        download = group.aggregate(context),
                        isFocused = (completedStartIndex + index) == uiState.focusedIndex
                    )
                }
            }
        }

        if (uiState.allItems.isNotEmpty()) {
            val footerHints = buildList {
                add(InputButton.DPAD_VERTICAL to stringResource(R.string.downloads_hint_navigate))
                if (uiState.focusedItem != null) {
                    add(InputButton.A to stringResource(uiState.confirmLabelRes))
                }
                if (uiState.canRemove) {
                    add(InputButton.X to stringResource(R.string.downloads_hint_remove))
                } else if (uiState.canCancel) {
                    add(InputButton.X to stringResource(R.string.downloads_hint_cancel))
                }
                if (uiState.hasFinishedItems) {
                    add(InputButton.Y to stringResource(R.string.downloads_hint_clear_finished))
                }
                add(InputButton.B to stringResource(R.string.downloads_hint_back))
            }

            FooterHints(
                hints = footerHints,
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> { inputHandler.onConfirm() }
                        InputButton.X -> { inputHandler.onContextMenu() }
                        InputButton.Y -> { inputHandler.onSecondaryAction() }
                        InputButton.B -> { inputHandler.onBack() }
                        else -> Unit
                    }
                }
            )
        }

        val failedItem = uiState.focusedItem
        ArgosyConfirmModalHost(
            visible = uiState.showFailedActionDialog,
            title = stringResource(R.string.downloads_failed_dialog_title),
            message = stringResource(R.string.downloads_failed_dialog_message, failedItem?.displayTitle ?: ""),
            confirmLabel = stringResource(R.string.downloads_failed_dialog_confirm),
            onConfirm = {
                failedItem?.let { viewModel.retryDownload(it.id) }
                viewModel.dismissFailedActionDialog()
            },
            onDismiss = { viewModel.dismissFailedActionDialog() },
            neutralLabel = stringResource(R.string.downloads_failed_dialog_clear),
            onNeutral = {
                failedItem?.let { viewModel.removeFromCompleted(it.id) }
                viewModel.dismissFailedActionDialog()
            }
        )
    }
}

@Composable
private fun GroupFileRows(group: DownloadGroup) {
    Column(modifier = Modifier.padding(start = Dimens.spacingXl, top = Dimens.spacingXs)) {
        group.items.forEach { file ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
            ) {
                Text(
                    text = file.fileName.ifEmpty { file.gameTitle },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when (file.state) {
                        DownloadState.COMPLETED -> stringResource(R.string.downloads_file_state_done)
                        DownloadState.EXTRACTING -> stringResource(R.string.downloads_file_state_extracting)
                        DownloadState.MOVING -> stringResource(R.string.downloads_file_state_moving)
                        DownloadState.DOWNLOADING -> if (file.isAwaitingServer) {
                            stringResource(R.string.downloads_file_state_waiting_for_server)
                        } else {
                            stringResource(R.string.downloads_file_state_percent, (file.progressPercent * 100).toInt())
                        }
                        DownloadState.FAILED -> stringResource(R.string.downloads_file_state_failed)
                        else -> stringResource(R.string.downloads_file_state_queued)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, speedSuffix: String? = null) {
    Row(
        modifier = Modifier.padding(bottom = Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (speedSuffix != null) {
            Text(
                text = speedSuffix,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DownloadCard(
    isFocused: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val theme = LocalArgosyTheme.current
    val scale = LocalUiScale.current.scale
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) theme.focusAccent else theme.hairlineLow,
        animationSpec = Motion.focusColorSpec,
        label = "download-border"
    )
    val washColor by animateColorAsState(
        targetValue = if (isFocused) theme.focusAccent.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = Motion.focusColorSpec,
        label = "download-wash"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height((ComponentDefaults.DownloadItem.rowHeight * scale).dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .background(washColor)
            .border(width = Dimens.borderThin, color = borderColor, shape = shape)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun DownloadItemHeader(download: DownloadProgress) {
    val theme = LocalArgosyTheme.current
    Text(
        text = download.displayTitle,
        style = MaterialTheme.typography.titleSmall,
        color = theme.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = download.platformSlug.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        color = theme.textDim
    )
}

@Composable
private fun DownloadItem(
    download: DownloadProgress,
    isInActiveList: Boolean,
    isFocused: Boolean,
    availableStorage: Long
) {
    val theme = LocalArgosyTheme.current
    val workingColor = if (theme.isDark) ColorTokens.Semantic.Dark.progress else ColorTokens.Semantic.Light.progress
    val isMoving = download.state == DownloadState.MOVING
    val isExtracting = download.state == DownloadState.EXTRACTING || isMoving
    val isActiveDownload = isInActiveList && !isExtracting && download.state != DownloadState.PAUSED &&
        download.state != DownloadState.WAITING_FOR_STORAGE && download.state != DownloadState.FAILED
    val byteText = "${formatBytes(download.bytesDownloaded)} / ${formatBytes(download.totalBytes)}"

    val (statusIcon, iconTint) = when {
        isMoving -> Icons.Filled.DriveFileMove to workingColor
        isExtracting -> Icons.Filled.FolderZip to workingColor
        isActiveDownload -> Icons.Default.Download to theme.focusAccent
        download.state == DownloadState.PAUSED -> Icons.Default.Pause to theme.textMute
        download.state == DownloadState.WAITING_FOR_STORAGE -> Icons.Default.Warning to LocalArgosyTheme.current.destructive
        download.state == DownloadState.FAILED -> Icons.Default.Error to LocalArgosyTheme.current.destructive
        else -> Icons.Default.Schedule to theme.textMute
    }

    DownloadCard(isFocused = isFocused) {
        DownloadCover(download)
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            DownloadItemHeader(download)
            when {
                isMoving -> {
                    ArgosyProgressBar(progress = download.extractionPercent, style = ProgressBarStyle.Working)
                    Text(
                        text = stringResource(R.string.downloads_item_status_moving),
                        style = MaterialTheme.typography.bodySmall,
                        color = workingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                isExtracting -> {
                    ArgosyProgressBar(progress = null, style = ProgressBarStyle.Working)
                    Text(
                        text = download.statusMessage ?: stringResource(R.string.downloads_item_status_extracting_fallback),
                        style = MaterialTheme.typography.bodySmall,
                        color = workingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                isActiveDownload -> {
                    ArgosyProgressBar(progress = download.progressPercent, style = ProgressBarStyle.Active)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val status = download.statusMessage
                            ?: stringResource(R.string.downloads_item_status_waiting_for_server).takeIf { download.isAwaitingServer }
                        Text(
                            text = if (status != null) "$byteText $status" else byteText,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.focusAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (download.bytesPerSecond > 0) {
                            Text(
                                text = formatSpeed(download.bytesPerSecond),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textMute
                            )
                        }
                    }
                }
                download.state == DownloadState.PAUSED -> {
                    ArgosyProgressBar(progress = download.progressPercent, style = ProgressBarStyle.Paused)
                    Text(
                        text = byteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMute
                    )
                }
                download.state == DownloadState.WAITING_FOR_STORAGE -> Text(
                    text = stringResource(
                        R.string.downloads_item_status_insufficient_storage,
                        formatBytes(
                            download.requiredStorageBytes
                                ?: (download.totalBytes - download.bytesDownloaded)
                        ),
                        formatBytes(availableStorage)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalArgosyTheme.current.destructive,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                download.state == DownloadState.FAILED -> Text(
                    text = download.errorReason?.toNotificationText()?.resolve()
                        ?: stringResource(R.string.downloads_item_status_failed_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalArgosyTheme.current.destructive,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                else -> Text(
                    text = download.statusMessage ?: stringResource(R.string.downloads_item_status_queued_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}

@Composable
private fun CompletedDownloadItem(
    download: DownloadProgress,
    isFocused: Boolean
) {
    val theme = LocalArgosyTheme.current
    val (icon, iconColor) = when (download.state) {
        DownloadState.COMPLETED -> Icons.Default.CheckCircle to theme.focusAccent
        DownloadState.FAILED -> Icons.Default.Error to LocalArgosyTheme.current.destructive
        else -> Icons.Default.CheckCircle to theme.textMute
    }

    DownloadCard(isFocused = isFocused) {
        DownloadCover(download)
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            DownloadItemHeader(download)
            when {
                download.state == DownloadState.FAILED -> Text(
                    text = download.errorReason?.toNotificationText()?.resolve()
                        ?: stringResource(R.string.downloads_completed_status_failed_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalArgosyTheme.current.destructive,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                download.state == DownloadState.COMPLETED -> Text(
                    text = stringResource(R.string.downloads_completed_status_installed),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.focusAccent
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}

@Composable
private fun DownloadCover(download: DownloadProgress) {
    val theme = LocalArgosyTheme.current
    val scale = LocalUiScale.current.scale
    val thumbModifier = Modifier
        .size((ComponentDefaults.DownloadItem.thumbSize * scale).dp)
        .clip(RoundedCornerShape(Dimens.radiusPanel))
    if (download.coverPath != null) {
        AsyncImage(
            model = rememberFileImageModel(download.coverPath),
            contentDescription = download.gameTitle,
            modifier = thumbModifier.background(theme.surfaceElevated),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = thumbModifier.background(theme.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconLg),
                tint = theme.focusAccent
            )
        }
    }
}

@Composable
private fun formatSpeed(bytesPerSecond: Long): String {
    return stringResource(R.string.downloads_speed_suffix, formatBytes(bytesPerSecond))
}

