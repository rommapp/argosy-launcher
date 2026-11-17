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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.primitives.ArgosyToggle
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * A feature tile's setup questions. Owns no state, like the media setup it sits beside: the step,
 * the focus index and the chosen answers belong to the caller, so a tap and a press of confirm
 * arrive at the same [onSelect] with the same row index.
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
        title = stringResource(setup.titleRes).uppercase(),
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
                FeatureSetupStep.MODE -> modeRows(setup, onSelect)
                FeatureSetupStep.FILTERS -> filterRows(setup, onSelect)
                FeatureSetupStep.PLATFORMS -> itemsIndexed(
                    setup.platforms,
                    key = { _, option -> option.id }
                ) { index, option ->
                    CheckRow(
                        label = option.label,
                        isFocused = setup.focusIndex == index,
                        isSelected = option.id in setup.selectedPlatformIds,
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
                        isSelected = genre in setup.selectedGenres,
                        onClick = { onSelect(index) }
                    )
                }
                FeatureSetupStep.SERIES -> itemsIndexed(
                    setup.series,
                    key = { _, name -> name }
                ) { index, name ->
                    CheckRow(
                        label = name,
                        isFocused = setup.focusIndex == index,
                        isSelected = name in setup.libraryLink.series,
                        onClick = { onSelect(index) }
                    )
                }
                FeatureSetupStep.PLAYERS -> itemsIndexed(
                    PLAYER_ROWS,
                    key = { _, bucket -> bucket?.name ?: "any" }
                ) { index, bucket ->
                    CheckRow(
                        label = bucket?.let { stringResource(it.labelRes) }
                            ?: stringResource(R.string.ui_feature_setup_any),
                        isFocused = setup.focusIndex == index,
                        isSelected = setup.libraryLink.players == bucket,
                        onClick = { onSelect(index) }
                    )
                }
                FeatureSetupStep.SORT -> itemsIndexed(
                    SortOption.entries,
                    key = { _, option -> option.name }
                ) { index, option ->
                    CheckRow(
                        label = stringResource(option.labelRes),
                        supporting = sortDirectionLabel(setup, option),
                        isFocused = setup.focusIndex == index,
                        isSelected = setup.libraryLink.sort.option == option,
                        onClick = { onSelect(index) }
                    )
                }
                FeatureSetupStep.DEVELOPERS -> itemsIndexed(
                    setup.developers,
                    key = { _, name -> name }
                ) { index, name ->
                    CheckRow(
                        label = name,
                        isFocused = setup.focusIndex == index,
                        isSelected = name in setup.libraryLink.developers,
                        onClick = { onSelect(index) }
                    )
                }
                FeatureSetupStep.RELEASE_YEAR -> itemsIndexed(
                    ReleaseYearPreset.entries,
                    key = { _, preset -> preset.name }
                ) { index, preset ->
                    CheckRow(
                        label = stringResource(preset.labelRes),
                        isFocused = setup.focusIndex == index,
                        isSelected = ReleaseYearPreset.of(setup.libraryLink) == preset,
                        onClick = { onSelect(index) }
                    )
                }
                FeatureSetupStep.PLAY_STATUS -> itemsIndexed(
                    CompletionStatus.entries,
                    key = { _, status -> status.name }
                ) { index, status ->
                    CheckRow(
                        label = stringResource(status.labelRes),
                        isFocused = setup.focusIndex == index,
                        isSelected = status.apiValue in setup.libraryLink.playStatuses,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.modeRows(
    setup: FeatureTileSetup,
    onSelect: (Int) -> Unit
) {
    item(key = "account") {
        CheckRow(
            label = stringResource(R.string.ui_feature_setup_mode_account),
            isFocused = setup.focusIndex == FeatureTileSetup.ROW_MODE_ACCOUNT,
            isSelected = setup.pickedGameId == null,
            onClick = { onSelect(FeatureTileSetup.ROW_MODE_ACCOUNT) }
        )
    }
    item(key = "track") {
        CheckRow(
            label = stringResource(R.string.ui_feature_setup_mode_track),
            isFocused = setup.focusIndex == FeatureTileSetup.ROW_MODE_TRACK,
            isSelected = setup.pickedGameId != null,
            onClick = { onSelect(FeatureTileSetup.ROW_MODE_TRACK) }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.filterRows(
    setup: FeatureTileSetup,
    onSelect: (Int) -> Unit
) {
    setup.filterRows.forEachIndexed { index, row ->
        item(key = row.name) {
            val focused = setup.focusIndex == index
            when (row) {
                FeatureSetupRow.DOWNLOADED_ONLY -> ToggleRow(
                    label = stringResource(R.string.ui_feature_setup_downloaded_only),
                    checked = setup.filters.downloadedOnly,
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.NEVER_PLAYED -> ToggleRow(
                    label = stringResource(R.string.ui_feature_setup_never_played),
                    checked = setup.filters.neverPlayed,
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.SOURCE -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_source),
                    supporting = stringResource(setup.libraryLink.source.labelRes),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.PLATFORMS -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_platforms),
                    supporting = selectionSummary(setup.selectedPlatformIds.size),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.GENRES -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_genres),
                    supporting = selectionSummary(setup.selectedGenres.size),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.SERIES -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_series),
                    supporting = selectionSummary(setup.libraryLink.series.size),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.PLAYERS -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_players),
                    supporting = setup.libraryLink.players
                        ?.let { stringResource(it.labelRes) }
                        ?: stringResource(R.string.ui_feature_setup_any),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.SORT -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_sort),
                    supporting = stringResource(setup.libraryLink.sort.option.labelRes),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.DEVELOPERS -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_developers),
                    supporting = selectionSummary(setup.libraryLink.developers.size),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.RELEASE_YEAR -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_release_year),
                    supporting = stringResource(
                        (ReleaseYearPreset.of(setup.libraryLink) ?: ReleaseYearPreset.ANY).labelRes
                    ),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.PLAY_STATUS -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_play_status),
                    supporting = selectionSummary(setup.libraryLink.playStatuses.size),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.RECENTLY_ADDED -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_recently_added),
                    supporting = setup.libraryLink.addedWithinDays
                        ?.let { pluralStringResource(R.plurals.ui_feature_setup_recently_added_days, it, it) }
                        ?: stringResource(R.string.ui_feature_setup_any),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.RA_PROGRESS -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_ra_progress),
                    supporting = setup.libraryLink.raProgress
                        ?.let { stringResource(it.labelRes) }
                        ?: stringResource(R.string.ui_feature_setup_any),
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
                FeatureSetupRow.DONE -> LinkRow(
                    label = stringResource(R.string.ui_feature_setup_done),
                    supporting = null,
                    isFocused = focused,
                    onClick = { onSelect(index) }
                )
            }
        }
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
    onClick: () -> Unit,
    supporting: String? = null
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
        supporting?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                maxLines = 1
            )
        }
    }
}

private val PLAYER_ROWS: List<PlayerCountBucket?> =
    listOf<PlayerCountBucket?>(null) + PlayerCountBucket.entries

@Composable
private fun sortDirectionLabel(setup: FeatureTileSetup, option: SortOption): String? {
    if (setup.libraryLink.sort.option != option) return null
    return stringResource(
        if (setup.libraryLink.sort.descending) {
            R.string.ui_feature_setup_sort_descending
        } else {
            R.string.ui_feature_setup_sort_ascending
        }
    )
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
