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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Asks a series tile what it should play.
 *
 * Owns no state, exactly as the entry picker does not: the step, the focus index and the chosen set
 * all belong to the caller, so the gamepad drives this through the same handler as the grid behind
 * it and touch and the d-pad end up at the same call.
 *
 * [onSelect] is given a focus index and answers every step, including the chooser's rows and its two
 * actions, so a tap and a press of confirm arrive at the same call.
 */
@Composable
fun MediaTileSetupModal(
    setup: MediaTileSetup,
    onSelect: (Int) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = setup.focusIndex)
    val theme = LocalArgosyTheme.current

    Modal(
        title = setup.title.uppercase(),
        subtitle = setup.subtitle,
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss
    ) {
        val message = setup.error ?: setup.emptyMessage
        when {
            setup.isLoading -> SetupLoading()
            message != null -> Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                modifier = Modifier.padding(Dimens.spacingMd)
            )
            setup.step == MediaTileStep.MODE -> ModeList(setup, listState, onSelect)
            setup.step == MediaTileStep.SEASON -> OptionList(
                options = setup.seasons,
                focusIndex = setup.focusIndex,
                selected = emptySet(),
                listState = listState,
                onSelect = onSelect
            )
            setup.step == MediaTileStep.EPISODES -> EpisodePickerContent(
                state = setup.picker,
                confirmLabel = MEDIA_TILE_EPISODES_CONFIRM_LABEL,
                onPressAt = onSelect,
                onCancel = onDismiss,
                onCommit = onCommit,
                showDownloadMarks = true
            )
            else -> Unit
        }
    }
}

@Composable
private fun SetupLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLg),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconLg),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun ColumnScope.ModeList(
    setup: MediaTileSetup,
    listState: LazyListState,
    onSelect: (Int) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
    ) {
        itemsIndexed(
            MediaTileModeOption.entries,
            key = { _, option -> option.name }
        ) { index, option ->
            SetupRow(
                label = option.label,
                supporting = option.supporting,
                isFocused = index == setup.focusIndex,
                isSelected = option.mode == setup.mode,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun ColumnScope.OptionList(
    options: List<MediaTileOption>,
    focusIndex: Int,
    selected: Set<String>,
    listState: LazyListState,
    onSelect: (Int) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
    ) {
        itemsIndexed(options, key = { _, option -> option.itemId }) { index, option ->
            SetupRow(
                label = option.label,
                supporting = option.supporting,
                isFocused = index == focusIndex,
                isSelected = option.itemId in selected,
                showDownloadMark = !option.isLocal,
                onClick = { onSelect(index) }
            )
        }
    }
}

/**
 * One row of whichever list is on screen. The download mark is a statement rather than a warning:
 * it says the row would have to be fetched, which is what the notice at the end will then ask about.
 */
@Composable
private fun SetupRow(
    label: String,
    supporting: String?,
    isFocused: Boolean,
    isSelected: Boolean,
    showDownloadMark: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.iconSm)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(if (isSelected) theme.focusAccent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = theme.textPrimary,
                    modifier = Modifier.size(Dimens.iconXs)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDownloadMark) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}

