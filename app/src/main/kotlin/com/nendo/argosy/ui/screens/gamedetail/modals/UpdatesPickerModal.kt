package com.nendo.argosy.ui.screens.gamedetail.modals

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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileType
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun UpdatesPickerModal(
    files: List<UpdateFileUi>,
    focusIndex: Int,
    onDownload: (UpdateFileUi) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val itemHeight = 64.dp
    val maxVisibleItems = 5

    LaunchedEffect(focusIndex, files.size) {
        if (files.isNotEmpty()) {
            val centerOffset = maxVisibleItems / 2
            val maxScrollIndex = (files.size - maxVisibleItems).coerceAtLeast(0)
            val targetScrollIndex = (focusIndex - centerOffset).coerceIn(0, maxScrollIndex)
            listState.animateScrollToItem(targetScrollIndex)
        }
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val focusedFile = files.getOrNull(focusIndex)
    val canDownload = focusedFile?.isDownloaded == false && focusedFile.gameFileId != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .clickable(
                    enabled = false,
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(24.dp)
        ) {
            Text(
                text = "UPDATES & DLC",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Must be installed through the emulator manually",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(max = itemHeight * maxVisibleItems)
                    .clip(RoundedCornerShape(8.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(files) { index, file ->
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

            Spacer(modifier = Modifier.height(16.dp))

            FooterBar(
                hints = listOfNotNull(
                    InputButton.DPAD_VERTICAL to "Navigate",
                    if (canDownload) InputButton.SOUTH to "Download" else null,
                    InputButton.EAST to "Back"
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
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(enabled = !file.isDownloaded, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(typeBgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
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
