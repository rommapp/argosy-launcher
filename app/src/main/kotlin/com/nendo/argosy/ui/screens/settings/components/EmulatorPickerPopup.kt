package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.remote.github.VersionFormatter
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.core.emulator.EmulatorDownloadState
import com.nendo.argosy.ui.screens.settings.EmulatorPickerInfo
import com.nendo.argosy.ui.screens.settings.EmulatorUpdateInfo
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme

private data class PickerLayoutState(
    val hasInstalled: Boolean,
    val hasDownloadable: Boolean
)

private sealed class PickerItem(
    val key: String,
    val visibleWhen: (PickerLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is DownloadHeader

    data object AutoItem : PickerItem("auto", { it.hasInstalled })

    class InstalledItem(val emulator: InstalledEmulator, val itemIndex: Int) : PickerItem(
        key = "installed_${emulator.def.displayName}"
    )

    data object DownloadHeader : PickerItem("downloadHeader", { it.hasDownloadable })

    class DownloadableItem(val emulator: EmulatorDef, val itemIndex: Int) : PickerItem(
        key = "downloadable_${emulator.displayName}"
    )

    class OtherAppItem(val itemIndex: Int) : PickerItem(key = "otherApp")

    companion object {
        fun buildItems(info: EmulatorPickerInfo): List<PickerItem> {
            val items = mutableListOf<PickerItem>()
            items.add(AutoItem)
            info.installedEmulators.forEachIndexed { index, emulator ->
                items.add(InstalledItem(emulator, 1 + index))
            }
            items.add(DownloadHeader)
            val downloadBaseIndex = if (info.installedEmulators.isNotEmpty()) 1 + info.installedEmulators.size else 0
            info.downloadableEmulators.forEachIndexed { index, emulator ->
                items.add(DownloadableItem(emulator, downloadBaseIndex + index))
            }
            items.add(OtherAppItem(downloadBaseIndex + info.downloadableEmulators.size))
            return items
        }
    }
}

private fun createPickerLayout(items: List<PickerItem>) = SettingsLayout<PickerItem, PickerLayoutState>(
    allItems = items,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) }
)

@Composable
fun EmulatorPickerPopup(
    info: EmulatorPickerInfo,
    focusIndex: Int,
    selectedIndex: Int?,
    onItemTap: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    val layoutState = remember(info.installedEmulators.size, info.downloadableEmulators.size) {
        PickerLayoutState(
            hasInstalled = info.installedEmulators.isNotEmpty(),
            hasDownloadable = info.downloadableEmulators.isNotEmpty()
        )
    }

    val allItems = remember(info) { PickerItem.buildItems(info) }
    val layout = remember(allItems) { createPickerLayout(allItems) }
    val visibleItems = remember(layoutState, allItems) { layout.visibleItems(layoutState) }

    fun isFocused(item: PickerItem): Boolean =
        focusIndex == layout.focusIndexOf(item, layoutState)

    FocusedScroll(
        listState = listState,
        focusedIndex = layout.focusToListIndex(focusIndex, layoutState)
    )

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        val popupMaxHeight = maxHeight * 0.85f
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .heightIn(max = popupMaxHeight)
                .clip(RoundedCornerShape(Dimens.radiusPanel))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.settings_emulator_picker_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = info.platformName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                items(visibleItems, key = { it.key }) { item ->
                    when (item) {
                        PickerItem.AutoItem -> {
                            val isTouchSelected = selectedIndex == 0
                            val isCurrentEmulator = info.selectedEmulatorName == null
                            EmulatorPickerItem(
                                name = stringResource(R.string.settings_emulator_picker_auto_name),
                                subtitle = stringResource(R.string.settings_emulator_picker_auto_subtitle),
                                isFocused = isFocused(item),
                                isTouchSelected = isTouchSelected,
                                isCurrentEmulator = isCurrentEmulator,
                                isDownload = false,
                                onClick = { onItemTap(0) }
                            )
                        }

                        is PickerItem.InstalledItem -> {
                            val isTouchSelected = selectedIndex == item.itemIndex
                            val isCurrentEmulator = item.emulator.def.displayName == info.selectedEmulatorName
                            val updateInfo = info.updates[item.emulator.def.id]
                            val isDownloading = info.downloadingEmulatorId == item.emulator.def.id
                            val downloadState = if (isDownloading) info.downloadState else EmulatorDownloadState.Idle
                            val isDisabled = info.downloadState !is EmulatorDownloadState.Idle && !isDownloading

                            val subtitle = when {
                                downloadState is EmulatorDownloadState.Downloading ->
                                    stringResource(
                                        R.string.settings_emulator_picker_installed_downloading_progress,
                                        (downloadState.progress * 100).toInt()
                                    )
                                downloadState is EmulatorDownloadState.WaitingForInstall ->
                                    stringResource(R.string.settings_emulator_picker_installed_installing)
                                downloadState is EmulatorDownloadState.Failed ->
                                    stringResource(R.string.settings_emulator_picker_installed_download_error)
                                updateInfo != null -> {
                                    val current = updateInfo.currentVersion?.let { VersionFormatter.formatForDisplay(it) }
                                        ?: stringResource(R.string.settings_emulator_picker_version_unknown)
                                    val latest = VersionFormatter.formatForDisplay(updateInfo.latestVersion)
                                    stringResource(R.string.settings_emulator_picker_version_update_arrow, current, latest)
                                }
                                else -> {
                                    val versionSuffix = item.emulator.versionName?.let {
                                        " - ${VersionFormatter.formatForDisplay(it)}"
                                    } ?: ""
                                    stringResource(R.string.settings_emulator_picker_installed_label) + versionSuffix
                                }
                            }

                            EmulatorPickerItem(
                                name = item.emulator.def.displayName,
                                subtitle = subtitle,
                                isFocused = isFocused(item),
                                isTouchSelected = isTouchSelected,
                                isCurrentEmulator = isCurrentEmulator,
                                isDownload = false,
                                hasUpdate = updateInfo != null,
                                downloadState = downloadState,
                                isDisabled = isDisabled,
                                onClick = { if (!isDisabled) onItemTap(item.itemIndex) }
                            )
                        }

                        PickerItem.DownloadHeader -> {
                            Column {
                                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                                Text(
                                    text = stringResource(R.string.settings_emulator_picker_download_header),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                                )
                            }
                        }

                        is PickerItem.DownloadableItem -> {
                            val isTouchSelected = selectedIndex == item.itemIndex
                            val isPlayStore = item.emulator.downloadUrl?.contains("play.google.com") == true
                            val isDownloading = info.downloadingEmulatorId == item.emulator.id
                            val downloadState = if (isDownloading) info.downloadState else EmulatorDownloadState.Idle
                            val isDisabled = info.downloadState !is EmulatorDownloadState.Idle && !isDownloading

                            val subtitle = when {
                                downloadState is EmulatorDownloadState.Downloading ->
                                    stringResource(
                                        R.string.settings_emulator_picker_downloadable_downloading_progress,
                                        (downloadState.progress * 100).toInt()
                                    )
                                downloadState is EmulatorDownloadState.WaitingForInstall ->
                                    stringResource(R.string.settings_emulator_picker_downloadable_installing)
                                downloadState is EmulatorDownloadState.Failed ->
                                    stringResource(R.string.settings_emulator_picker_downloadable_download_error)
                                isPlayStore -> stringResource(R.string.settings_emulator_picker_source_play_store)
                                else -> stringResource(R.string.settings_emulator_picker_source_github)
                            }

                            EmulatorPickerItem(
                                name = item.emulator.displayName,
                                subtitle = subtitle,
                                isFocused = isFocused(item),
                                isTouchSelected = isTouchSelected,
                                isCurrentEmulator = false,
                                isDownload = true,
                                downloadState = downloadState,
                                isDisabled = isDisabled,
                                onClick = { if (!isDisabled) onItemTap(item.itemIndex) }
                            )
                        }

                        is PickerItem.OtherAppItem -> {
                            EmulatorPickerItem(
                                name = stringResource(R.string.settings_emulator_picker_other_app_name),
                                subtitle = stringResource(R.string.settings_emulator_picker_other_app_subtitle),
                                isFocused = isFocused(item),
                                isTouchSelected = selectedIndex == item.itemIndex,
                                isCurrentEmulator = false,
                                isDownload = false,
                                onClick = { onItemTap(item.itemIndex) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterHints(
                hints = listOf(
                    InputButton.DPAD to stringResource(R.string.settings_emulator_picker_hint_navigate),
                    InputButton.A to stringResource(R.string.settings_emulator_picker_hint_select),
                    InputButton.B to stringResource(R.string.settings_emulator_picker_hint_close)
                ),
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> onConfirm()
                        InputButton.B -> onDismiss()
                        else -> Unit
                    }
                }
            )
        }
    }
}

@Composable
private fun EmulatorPickerItem(
    name: String,
    subtitle: String,
    isFocused: Boolean,
    isTouchSelected: Boolean,
    isCurrentEmulator: Boolean,
    isDownload: Boolean,
    hasUpdate: Boolean = false,
    downloadState: EmulatorDownloadState = EmulatorDownloadState.Idle,
    isDisabled: Boolean = false,
    onClick: () -> Unit
) {
    val isHighlighted = (isFocused || isTouchSelected) && !isDisabled
    val isDownloading = downloadState is EmulatorDownloadState.Downloading
    val isFailed = downloadState is EmulatorDownloadState.Failed
    val focusContent = lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)

    val contentAlpha = if (isDisabled) 0.5f else 1f

    val animatedProgress by animateFloatAsState(
        targetValue = when (downloadState) {
            is EmulatorDownloadState.Downloading -> downloadState.progress
            is EmulatorDownloadState.WaitingForInstall -> 1f
            else -> 0f
        },
        label = "download_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                when {
                    isHighlighted -> LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                    isCurrentEmulator -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickableNoFocus(enabled = !isDisabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = (if (isHighlighted) focusContent
                            else MaterialTheme.colorScheme.onSurface).copy(alpha = contentAlpha)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isFailed -> MaterialTheme.colorScheme.error
                        isHighlighted -> focusContent.copy(alpha = 0.7f * contentAlpha)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                    }
                )
            }

            when {
                isFailed -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimens.iconSm)
                )
                isCurrentEmulator && !hasUpdate -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = (if (isHighlighted) focusContent
                           else MaterialTheme.colorScheme.primary).copy(alpha = contentAlpha),
                    modifier = Modifier.size(Dimens.iconSm)
                )
                hasUpdate -> Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = stringResource(R.string.settings_emulator_picker_update_available_desc),
                    tint = (if (isHighlighted) focusContent
                           else LocalLauncherTheme.current.semanticColors.info).copy(alpha = contentAlpha),
                    modifier = Modifier.size(Dimens.iconSm)
                )
                isDownload -> Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = (if (isHighlighted) focusContent
                           else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)).copy(alpha = contentAlpha),
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }

        if (isDownloading || downloadState is EmulatorDownloadState.WaitingForInstall) {
            val progressBarHeight = Dimens.spacingSm - Dimens.borderMedium
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressBarHeight)
                    .clip(RoundedCornerShape(bottomStart = Dimens.radiusMd, bottomEnd = Dimens.radiusMd))
                    .background(Color.Gray.copy(alpha = 0.6f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .height(progressBarHeight)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
