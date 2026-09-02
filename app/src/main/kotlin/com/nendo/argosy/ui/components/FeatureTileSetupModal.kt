package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ArgosyToggle
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The random game tile's filter questions. Owns no state, like the media setup it sits beside:
 * the step, the focus index and the chosen filters belong to the caller, so a tap and a press of
 * confirm arrive at the same [onSelect] with the same row index.
 */
@Composable
fun FeatureTileSetupModal(
    setup: FeatureTileSetup,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = setup.focusIndex)

    Modal(
        title = stringResource(R.string.ui_feature_setup_title).uppercase(),
        subtitle = stringResource(setup.subtitleRes),
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
        ) {
            when (setup.step) {
                FeatureSetupStep.FILTERS -> filterRows(setup, onSelect)
                FeatureSetupStep.PLATFORMS -> itemsIndexed(
                    setup.platforms,
                    key = { _, option -> option.id }
                ) { index, option ->
                    CheckRow(
                        label = option.label,
                        isFocused = setup.focusIndex == index,
                        isSelected = option.id in setup.filters.platformIds,
                        onClick = { onSelect(index) }
                    )
                }
                FeatureSetupStep.GENRES -> itemsIndexed(
                    setup.genres,
                    key = { _, genre -> genre }
                ) { index, genre ->
                    CheckRow(
                        label = genre,
                        isFocused = setup.focusIndex == index,
                        isSelected = genre in setup.filters.genres,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.filterRows(
    setup: FeatureTileSetup,
    onSelect: (Int) -> Unit
) {
    item(key = "downloaded") {
        ToggleRow(
            label = stringResource(R.string.ui_feature_setup_downloaded_only),
            checked = setup.filters.downloadedOnly,
            isFocused = setup.focusIndex == FeatureTileSetup.ROW_DOWNLOADED_ONLY,
            onClick = { onSelect(FeatureTileSetup.ROW_DOWNLOADED_ONLY) }
        )
    }
    item(key = "neverPlayed") {
        ToggleRow(
            label = stringResource(R.string.ui_feature_setup_never_played),
            checked = setup.filters.neverPlayed,
            isFocused = setup.focusIndex == FeatureTileSetup.ROW_NEVER_PLAYED,
            onClick = { onSelect(FeatureTileSetup.ROW_NEVER_PLAYED) }
        )
    }
    item(key = "platforms") {
        LinkRow(
            label = stringResource(R.string.ui_feature_setup_platforms),
            supporting = selectionSummary(setup.filters.platformIds.size),
            isFocused = setup.focusIndex == FeatureTileSetup.ROW_PLATFORMS,
            onClick = { onSelect(FeatureTileSetup.ROW_PLATFORMS) }
        )
    }
    item(key = "genres") {
        LinkRow(
            label = stringResource(R.string.ui_feature_setup_genres),
            supporting = selectionSummary(setup.filters.genres.size),
            isFocused = setup.focusIndex == FeatureTileSetup.ROW_GENRES,
            onClick = { onSelect(FeatureTileSetup.ROW_GENRES) }
        )
    }
    item(key = "done") {
        LinkRow(
            label = stringResource(R.string.ui_feature_setup_done),
            supporting = null,
            isFocused = setup.focusIndex == FeatureTileSetup.ROW_DONE,
            onClick = { onSelect(FeatureTileSetup.ROW_DONE) }
        )
    }
}

@Composable
private fun selectionSummary(count: Int): String =
    if (count == 0) {
        stringResource(R.string.ui_feature_setup_any)
    } else {
        stringResource(R.string.ui_feature_setup_selected_count, count)
    }

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    SetupRowFrame(isFocused = isFocused, onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalArgosyTheme.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        ArgosyToggle(checked = checked, onToggle = { onClick() }, focused = isFocused)
    }
}

@Composable
private fun LinkRow(
    label: String,
    supporting: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    SetupRowFrame(isFocused = isFocused, onClick = onClick) {
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
    }
}

@Composable
private fun CheckRow(
    label: String,
    isFocused: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    SetupRowFrame(isFocused = isFocused, onClick = onClick) {
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SetupRowFrame(
    isFocused: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
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
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        content = content
    )
}
