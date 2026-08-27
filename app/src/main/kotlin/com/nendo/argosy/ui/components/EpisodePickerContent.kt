package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The one episode chooser: seasons that fold, episodes that tick, and the actions that end the run.
 *
 * Owns no state. The focus index, the ticked set and the folded seasons all live in [state], which
 * its caller holds in a view model, so touch and the d-pad arrive at the same call.
 *
 * [onPressAt] is given a focus index and covers both the rows and the shortcuts; the two end actions
 * are separate because they are separate verbs and each flow answers them differently.
 */
@Composable
fun ColumnScope.EpisodePickerContent(
    state: EpisodePickerState,
    confirmLabel: String,
    onPressAt: (Int) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
    cancelLabel: String = stringResource(R.string.ui_episode_picker_cancel),
    showDownloadMarks: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()
    val rows = state.visibleRows
    FocusedScroll(
        listState = listState,
        focusedIndex = state.focusedIndex.coerceAtMost((rows.size - 1).coerceAtLeast(0))
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row -> row.itemId ?: "season-${row.seasonKey}" }
        ) { index, row ->
            EpisodePickerRowView(
                row = row,
                isSelected = row.itemId != null && row.itemId in state.selection.selected,
                isCollapsed = row.seasonKey in state.selection.collapsed,
                isFocused = index == state.focusedIndex,
                showDownloadMark = showDownloadMarks,
                onClick = { onPressAt(index) }
            )
        }
    }
    Text(
        text = if (state.selectedCount == 0) {
            stringResource(R.string.ui_episode_picker_selection_none)
        } else {
            pluralStringResource(
                R.plurals.ui_episode_picker_selection_count,
                state.selectedCount,
                state.selectedCount
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = theme.textDim,
        modifier = Modifier.padding(vertical = Dimens.spacingSm)
    )
    if (state.quickActions.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            state.quickActions.forEachIndexed { index, action ->
                val focusIndex = rows.size + index
                EpisodePickerActionButton(
                    label = action.label,
                    isPrimary = false,
                    isEnabled = action.enabled,
                    isFocused = state.focusedIndex == focusIndex,
                    onClick = { onPressAt(focusIndex) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        EpisodePickerActionButton(
            label = cancelLabel,
            isPrimary = false,
            isEnabled = true,
            isFocused = state.isCancelFocused,
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        EpisodePickerActionButton(
            label = confirmLabel,
            isPrimary = state.hasSelection,
            isEnabled = state.hasSelection,
            isFocused = state.isConfirmFocused,
            onClick = onCommit,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * A season to fold, or an episode to tick. A season carries no tick of its own: pressing it takes the
 * whole season on or off, which reads better as a heading than as a third checkbox state.
 */
@Composable
private fun EpisodePickerRowView(
    row: EpisodePickerRow,
    isSelected: Boolean,
    isCollapsed: Boolean,
    isFocused: Boolean,
    showDownloadMark: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (row.isHeader) theme.surfaceRaised else theme.surfaceElevated)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = when {
                row.isHeader && isCollapsed -> Icons.Default.ChevronRight
                row.isHeader -> Icons.Default.ExpandMore
                row.isLocked -> Icons.Default.DownloadDone
                isSelected -> Icons.Default.CheckBox
                else -> Icons.Default.CheckBoxOutlineBlank
            },
            contentDescription = null,
            tint = if (row.isLocked) theme.focusAccent else theme.textDim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = if (row.isHeader) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (row.isLocked) theme.textMute else theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            row.supporting?.let { supporting ->
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDownloadMark && !row.isHeader && !row.isDownloaded) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}

/**
 * One of the chooser's end actions, as a surface a finger can press.
 */
@Composable
private fun EpisodePickerActionButton(
    label: String,
    isPrimary: Boolean,
    isEnabled: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = modifier
            .clip(shape)
            .background(theme.surfaceElevated)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Button,
                selected = isPrimary,
                shape = shape
            )
            .clickableNoFocus(enabled = isEnabled, onClick = onClick)
            .padding(vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !isEnabled -> theme.textMute
                isFocused || isPrimary -> theme.textPrimary
                else -> theme.textDim
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
