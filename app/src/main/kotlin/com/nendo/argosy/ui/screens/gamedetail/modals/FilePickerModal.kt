package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.data.model.FilePickerRow
import com.nendo.argosy.ui.components.ArgosyCheckState
import com.nendo.argosy.ui.components.ArgosyCheckbox
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatBytes

enum class GroupCheckState { ALL, NONE, PARTIAL }

/**
 * Cherry-pick file selection for a download. Focus indices rows.size and
 * rows.size + 1 land on the Cancel and confirm footer buttons.
 */
@Composable
fun FilePickerModal(
    gameTitle: String,
    title: String,
    rows: List<FilePickerRow>,
    selectedIds: Set<Long>,
    selectedVersionIds: Set<Long>,
    focusIndex: Int,
    summary: String,
    onToggleRow: (FilePickerRow) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    allRows: List<FilePickerRow> = rows,
    collapsedGroups: Set<String> = emptySet(),
    onToggleCollapse: ((String) -> Unit)? = null,
    manageMode: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()

    val isRowSelected = { row: FilePickerRow ->
        (row.isLocked && row.isDownloaded) ||
            (row.versionRommId?.let { it in selectedVersionIds } ?: (row.rommFileId in selectedIds))
    }
    val pendingAdds = if (manageMode) {
        allRows.count { !it.isHeader && !it.isLocked && !it.isDownloaded && isRowSelected(it) }
    } else 0
    val pendingRemoves = if (manageMode) {
        allRows.count { !it.isHeader && !it.isLocked && it.isDownloaded && !isRowSelected(it) }
    } else 0
    val confirmLabel = when {
        !manageMode -> "Download"
        pendingAdds > 0 && pendingRemoves == 0 -> "Download $pendingAdds"
        pendingRemoves > 0 && pendingAdds == 0 -> "Remove $pendingRemoves"
        pendingAdds > 0 && pendingRemoves > 0 -> "Apply Changes"
        else -> "No Changes"
    }
    val confirmEnabled = !manageMode || pendingAdds + pendingRemoves > 0
    val confirmTint = if (manageMode && pendingRemoves > 0) theme.destructive else theme.focusAccent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
            .clickableNoFocus { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(Dimens.radiusPanel),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = Dimens.modalWidthLg)
                .fillMaxWidth(0.92f)
                .clickableNoFocus { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = gameTitle.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.focusAccent
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))

                FocusedScroll(listState = listState, focusedIndex = focusIndex.coerceAtMost((rows.size - 1).coerceAtLeast(0)))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    itemsIndexed(rows, key = { _, row -> "${row.groupKey}:${row.rommFileId ?: row.versionRommId ?: "h"}" }) { index, row ->
                        if (row.isHeader) {
                            FilePickerGroupHeader(
                                row = row,
                                checkState = groupCheckState(row.groupKey, allRows, selectedIds, selectedVersionIds),
                                isFocused = focusIndex == index,
                                isCollapsed = row.groupKey in collapsedGroups,
                                onClick = { onToggleRow(row) },
                                onToggleCollapse = onToggleCollapse?.let { toggle -> { toggle(row.groupKey) } }
                            )
                        } else {
                            FilePickerFileRow(
                                row = row,
                                isSelected = isRowSelected(row),
                                isFocused = focusIndex == index,
                                manageMode = manageMode,
                                onClick = { onToggleRow(row) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
                ) {
                    ModalActionButton(
                        label = "Cancel",
                        tint = theme.focusAccent,
                        restLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focused = focusIndex == rows.size,
                        onClick = onDismiss
                    )
                    ModalActionButton(
                        label = confirmLabel,
                        tint = confirmTint,
                        restLabelColor = MaterialTheme.colorScheme.onSurface,
                        focused = focusIndex == rows.size + 1,
                        onClick = onConfirm,
                        enabled = confirmEnabled
                    )
                }
            }
        }
    }
}

private fun groupCheckState(
    groupKey: String,
    rows: List<FilePickerRow>,
    selectedIds: Set<Long>,
    selectedVersionIds: Set<Long>
): GroupCheckState {
    val members = rows.filter { !it.isHeader && it.groupKey == groupKey }
    if (members.isEmpty()) return GroupCheckState.NONE
    val selectedCount = members.count { row ->
        (row.isLocked && row.isDownloaded) ||
            (row.versionRommId?.let { it in selectedVersionIds } ?: (row.rommFileId in selectedIds))
    }
    return when (selectedCount) {
        0 -> GroupCheckState.NONE
        members.size -> GroupCheckState.ALL
        else -> GroupCheckState.PARTIAL
    }
}

@Composable
private fun FilePickerGroupHeader(
    row: FilePickerRow,
    checkState: GroupCheckState,
    isFocused: Boolean,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    onToggleCollapse: (() -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(
                if (isFocused) theme.focusAccent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .clickableNoFocus { onClick() }
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        ArgosyCheckbox(
            state = when (checkState) {
                GroupCheckState.ALL -> ArgosyCheckState.CHECKED
                GroupCheckState.PARTIAL -> ArgosyCheckState.PARTIAL
                GroupCheckState.NONE -> ArgosyCheckState.UNCHECKED
            }
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = row.label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onToggleCollapse != null) {
            Icon(
                imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (isCollapsed) "Expand" else "Collapse",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickableNoFocus { onToggleCollapse() }
            )
        }
    }
}

@Composable
private fun FilePickerFileRow(
    row: FilePickerRow,
    isSelected: Boolean,
    isFocused: Boolean,
    manageMode: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val pendingAdd = manageMode && !row.isLocked && !row.isDownloaded && isSelected
    val pendingRemove = manageMode && !row.isLocked && row.isDownloaded && !isSelected
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(
                if (isFocused) theme.focusAccent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surface
            )
            .clickableNoFocus(enabled = !row.isLocked) { onClick() }
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
    ) {
        ArgosyCheckbox(
            state = if (isSelected) ArgosyCheckState.CHECKED else ArgosyCheckState.UNCHECKED,
            enabled = !row.isLocked
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (row.isDefaultVersion) {
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    Text(
                        text = "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.focusAccent
                    )
                }
            }
            when {
                pendingRemove -> Text(
                    text = "Will be removed",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.destructive
                )
                pendingAdd -> Text(
                    text = "Will download",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.focusAccent
                )
                row.isDownloaded -> Text(
                    text = "On device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        if (pendingAdd || pendingRemove) {
            Icon(
                imageVector = if (pendingAdd) Icons.Default.Download else Icons.Default.Delete,
                contentDescription = null,
                tint = if (pendingAdd) theme.focusAccent else theme.destructive,
                modifier = Modifier.width(Dimens.iconSm)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
        Text(
            text = formatBytes(row.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
