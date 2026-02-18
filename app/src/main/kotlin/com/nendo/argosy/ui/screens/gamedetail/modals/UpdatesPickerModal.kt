package com.nendo.argosy.ui.screens.gamedetail.modals

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileType
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun UpdatesPickerModal(
    files: List<UpdateFileUi>,
    focusIndex: Int,
    isEdenGame: Boolean,
    onDownload: (UpdateFileUi) -> Unit,
    onApplyAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    FocusedScroll(
        listState = listState,
        focusedIndex = focusIndex
    )

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val focusedFile = files.getOrNull(focusIndex)
    val focusedNeedsDownload = focusedFile != null && !focusedFile.isDownloaded && focusedFile.gameFileId != null
    val focusedNeedsInstall = isEdenGame && focusedFile != null && focusedFile.isDownloaded && !focusedFile.isAppliedToEmulator
    val hasAnyDownloadable = files.any { !it.isDownloaded && it.gameFileId != null }
    val hasAnyInstallable = isEdenGame && files.any { it.isDownloaded && !it.isAppliedToEmulator }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthXl)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusLg))
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg)
        ) {
            Text(
                text = "Updates & DLC",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(Dimens.radiusMd)),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(files, key = { _, file -> file.fileName }) { index, file ->
                    UpdateFileItem(
                        file = file,
                        isFocused = focusIndex == index,
                        onClick = {
                            if (!file.isDownloaded && file.gameFileId != null) {
                                onDownload(file)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            FooterBar(
                hints = listOfNotNull(
                    InputButton.DPAD_VERTICAL to "Navigate",
                    when {
                        focusedNeedsDownload -> InputButton.A to "Download"
                        focusedNeedsInstall -> InputButton.A to "Install"
                        else -> null
                    },
                    when {
                        hasAnyDownloadable -> InputButton.X to "Download All"
                        hasAnyInstallable -> InputButton.X to "Install All"
                        else -> null
                    },
                    InputButton.B to "Back"
                )
            )
        }
    }
}

@Composable
private fun UpdateFileItem(
    file: UpdateFileUi,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val dimmed = !file.isDownloaded
    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        dimmed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when {
        file.isAppliedToEmulator -> Icons.Filled.CheckCircle
        file.isDownloaded -> Icons.Outlined.FolderZip
        else -> Icons.Outlined.CloudDownload
    }
    val iconTint = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        file.isAppliedToEmulator -> MaterialTheme.colorScheme.primary
        file.isDownloaded -> MaterialTheme.colorScheme.onSurface
        else -> secondaryColor
    }

    val typeLabel = when (file.type) {
        UpdateFileType.UPDATE -> "UPDATE"
        UpdateFileType.DLC -> "DLC"
    }
    val typeBgColor = when (file.type) {
        UpdateFileType.UPDATE -> MaterialTheme.colorScheme.tertiary
        UpdateFileType.DLC -> MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.radiusLg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Spacer(modifier = Modifier.width(Dimens.radiusLg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(typeBgColor)
                        .padding(horizontal = Dimens.radiusSm, vertical = Dimens.borderMedium)
                )
                Text(
                    text = formatFileSize(file.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
