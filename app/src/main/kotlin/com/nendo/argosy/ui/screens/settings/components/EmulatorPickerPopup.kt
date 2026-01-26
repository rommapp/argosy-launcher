package com.nendo.argosy.ui.screens.settings.components

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.settings.EmulatorPickerInfo
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
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
    @Suppress("unused") onConfirm: () -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = "SELECT EMULATOR",
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
                modifier = Modifier.heightIn(max = Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                items(visibleItems, key = { it.key }) { item ->
                    when (item) {
                        PickerItem.AutoItem -> {
                            val isTouchSelected = selectedIndex == 0
                            val isCurrentEmulator = info.selectedEmulatorName == null
                            EmulatorPickerItem(
                                name = "Auto",
                                subtitle = "Use recommended emulator",
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
                            EmulatorPickerItem(
                                name = item.emulator.def.displayName,
                                subtitle = "Installed" + (item.emulator.versionName?.let { " - v$it" } ?: ""),
                                isFocused = isFocused(item),
                                isTouchSelected = isTouchSelected,
                                isCurrentEmulator = isCurrentEmulator,
                                isDownload = false,
                                onClick = { onItemTap(item.itemIndex) }
                            )
                        }

                        PickerItem.DownloadHeader -> {
                            Column {
                                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                                Text(
                                    text = "AVAILABLE TO DOWNLOAD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                                )
                            }
                        }

                        is PickerItem.DownloadableItem -> {
                            val isTouchSelected = selectedIndex == item.itemIndex
                            val isPlayStore = item.emulator.downloadUrl?.contains("play.google.com") == true
                            EmulatorPickerItem(
                                name = item.emulator.displayName,
                                subtitle = if (isPlayStore) "Play Store" else "GitHub",
                                isFocused = isFocused(item),
                                isTouchSelected = isTouchSelected,
                                isCurrentEmulator = false,
                                isDownload = true,
                                onClick = { onItemTap(item.itemIndex) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterBar(
                hints = listOf(
                    InputButton.DPAD to "Navigate",
                    InputButton.A to "Select",
                    InputButton.B to "Close"
                )
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
    onClick: () -> Unit
) {
    val isHighlighted = isFocused || isTouchSelected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                when {
                    isHighlighted -> MaterialTheme.colorScheme.primaryContainer
                    isCurrentEmulator -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            isCurrentEmulator -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSm)
            )
            isDownload -> Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}
