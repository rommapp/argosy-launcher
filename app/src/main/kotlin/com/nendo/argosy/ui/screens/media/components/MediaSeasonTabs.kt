package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.media.MediaSeasonUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The seasons of a series as a scrolling tab row. Ten seasons fit here where a picker modal would
 * have hidden them, and the shoulder buttons reach the same selection without focus leaving the
 * episode list.
 */
@Composable
fun MediaSeasonTabs(
    seasons: List<MediaSeasonUi>,
    selectedIndex: Int,
    isSectionFocused: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in seasons.indices) listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().height(Dimens.mediaSeasonTabHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(seasons, key = { _, season -> season.itemId }) { index, season ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(Dimens.radiusPill)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .argosyFocusIndicators(
                        focused = isSectionFocused && selected,
                        indicators = FocusIndicators.TabRow,
                        selected = selected,
                        shape = shape
                    )
                    .clickableNoFocus { onSelect(index) }
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = seasonLabel(season),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) theme.textPrimary else theme.textDim,
                    maxLines = 1
                )
            }
        }
    }
}

private fun seasonLabel(season: MediaSeasonUi): String =
    season.seasonNumber?.let { "Season $it" } ?: season.name
