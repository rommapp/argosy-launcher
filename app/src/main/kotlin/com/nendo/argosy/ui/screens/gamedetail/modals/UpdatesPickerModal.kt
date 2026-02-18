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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
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
    val itemHeight = 64.dp
    val maxVisibleItems = 5

    FocusedScroll(
        listState = listState,
        focusedIndex = focusIndex
    )

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val focusedFile = files.getOrNull(focusIndex)
    val canDownload = focusedFile?.isDownloaded == false && focusedFile.gameFileId != null
    val canApply = isEdenGame && focusedFile?.isDownloaded == true && !focusedFile.isAppliedToEmulator
    val allApplied = isEdenGame && files.all { !it.isDownloaded || it.isAppliedToEmulator }
    val hasUnapplied = isEdenGame && files.any { it.isDownloaded && !it.isAppliedToEmulator }

    val subtitle = when {
        isEdenGame && allApplied -> "Applied to Eden"
        isEdenGame -> "Apply to register with Eden"
        else -> "Must be installed through the emulator manually"
    }

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
                text = "UPDATES & DLC",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isEdenGame && allApplied)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(max = itemHeight * maxVisibleItems)
                    .clip(RoundedCornerShape(Dimens.radiusMd)),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(files, key = { _, file -> file.fileName }) { index, file ->
                    UpdateFileItem(
                        file = file,
                        isEdenGame = isEdenGame,
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
                        canDownload -> InputButton.A to "Download"
                        canApply -> InputButton.A to "Apply"
                        else -> null
                    },
                    if (hasUnapplied) InputButton.X to "Apply All" else null,
                    InputButton.B to "Back"
                )
            )
        }
    }
}

@Composable
private fun UpdateFileItem(
    file: UpdateFileUi,
    isEdenGame: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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
            .clickableNoFocus(enabled = !file.isDownloaded, onClick = onClick)
            .padding(horizontal = Dimens.radiusLg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDownloaded) Icons.Outlined.Check else Icons.Outlined.CloudDownload,
            contentDescription = if (file.isDownloaded) "Downloaded" else "Available to download",
            tint = if (file.isDownloaded) {
                MaterialTheme.colorScheme.primary
            } else {
                secondaryColor
            },
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
                if (isEdenGame && file.isDownloaded && file.isAppliedToEmulator) {
                    Text(
                        text = "APPLIED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.radiusSm))
                            .background(Color(0xFF4CAF50))
                            .padding(horizontal = Dimens.radiusSm, vertical = Dimens.borderMedium)
                    )
                }
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
