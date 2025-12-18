package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.settings.EmulatorPickerInfo
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion

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
    val installedCount = info.installedEmulators.size
    val hasDownloadSection = info.downloadableEmulators.isNotEmpty()
    val hasInstalled = installedCount > 0

    LaunchedEffect(focusIndex) {
        val downloadStartFocusIndex = if (hasInstalled) 1 + installedCount else 0
        val scrollIndex = when {
            !hasInstalled -> {
                if (hasDownloadSection) focusIndex + 1 else focusIndex
            }
            hasDownloadSection && focusIndex >= downloadStartFocusIndex -> {
                focusIndex + 1
            }
            else -> focusIndex
        }
        val safeIndex = scrollIndex.coerceAtLeast(0)
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0

        if (itemHeight == 0 || viewportHeight == 0) {
            listState.animateScrollToItem(safeIndex)
            return@LaunchedEffect
        }

        val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
        val centerOffset = (viewportHeight - itemHeight) / 2
        listState.animateScrollToItem(safeIndex, -centerOffset + paddingBuffer)
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false, onClick = {})
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
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                if (installedCount > 0) {
                    item {
                        val isFocused = focusIndex == 0
                        val isTouchSelected = selectedIndex == 0
                        val isCurrentEmulator = info.selectedEmulatorName == null
                        EmulatorPickerItem(
                            name = "Auto",
                            subtitle = "Use recommended emulator",
                            isFocused = isFocused,
                            isTouchSelected = isTouchSelected,
                            isCurrentEmulator = isCurrentEmulator,
                            isDownload = false,
                            onClick = { onItemTap(0) }
                        )
                    }
                }

                itemsIndexed(info.installedEmulators) { index, emulator ->
                    val itemIndex = 1 + index
                    val isFocused = focusIndex == itemIndex
                    val isTouchSelected = selectedIndex == itemIndex
                    val isCurrentEmulator = emulator.def.displayName == info.selectedEmulatorName
                    EmulatorPickerItem(
                        name = emulator.def.displayName,
                        subtitle = "Installed" + (emulator.versionName?.let { " - v$it" } ?: ""),
                        isFocused = isFocused,
                        isTouchSelected = isTouchSelected,
                        isCurrentEmulator = isCurrentEmulator,
                        isDownload = false,
                        onClick = { onItemTap(itemIndex) }
                    )
                }

                if (info.downloadableEmulators.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Dimens.spacingSm))
                        Text(
                            text = "AVAILABLE TO DOWNLOAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                        )
                    }

                    itemsIndexed(info.downloadableEmulators) { index, emulator ->
                        val baseIndex = if (installedCount > 0) 1 + installedCount else 0
                        val itemIndex = baseIndex + index
                        val isFocused = focusIndex == itemIndex
                        val isTouchSelected = selectedIndex == itemIndex
                        val isPlayStore = emulator.downloadUrl?.contains("play.google.com") == true
                        EmulatorPickerItem(
                            name = emulator.displayName,
                            subtitle = if (isPlayStore) "Play Store" else "GitHub",
                            isFocused = isFocused,
                            isTouchSelected = isTouchSelected,
                            isCurrentEmulator = false,
                            isDownload = true,
                            onClick = { onItemTap(itemIndex) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterBar(
                hints = listOf(
                    InputButton.DPAD to "Navigate",
                    InputButton.SOUTH to "Select",
                    InputButton.EAST to "Close"
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
            .clickable(onClick = onClick)
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
                modifier = Modifier.size(20.dp)
            )
            isDownload -> Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
