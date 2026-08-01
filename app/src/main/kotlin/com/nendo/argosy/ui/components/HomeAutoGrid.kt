package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.domain.model.AutoGridConfig
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeSectionStyle
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Focus order for the auto grid, expressed as visual rows of item indices. The renderer and the
 * caller's navigator must agree on this or the highlight and the covers drift apart, so both read
 * it from here.
 *
 * A vertical grid fills row by row, so a row is a contiguous slice. A horizontal grid fills column
 * by column, so visual row `r` is every index congruent to `r` modulo the lane count.
 */
fun autoGridFocusRows(itemCount: Int, config: AutoGridConfig): List<List<Int>> {
    if (itemCount <= 0) return emptyList()
    val lanes = config.laneCount.coerceAtLeast(1)
    return when (config.scrollAxis) {
        HomeScrollAxis.VERTICAL -> (0 until itemCount).chunked(lanes)
        HomeScrollAxis.HORIZONTAL -> (0 until lanes).mapNotNull { lane ->
            (lane until itemCount step lanes).toList().takeIf { it.isNotEmpty() }
        }
    }
}

/**
 * The auto grid: one home section's covers flowing in a fixed number of lanes, scrolling on the
 * configured axis. Lanes read as columns when scrolling vertically and as rows when scrolling
 * horizontally, so the same number means the same thing the picker previewed.
 *
 * Owns no focus state; [focusedIndex] indexes [items] and the caller drives it.
 */
@Composable
fun HomeAutoGrid(
    items: List<CarouselItem>,
    focusedIndex: Int,
    config: AutoGridConfig,
    gridState: LazyGridState,
    sectionTitle: String,
    onItemTap: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showPlatformBadge: Boolean = true,
    downloadIndicatorFor: (CarouselItem) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (config.sectionStyle == HomeSectionStyle.HEADINGS) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = Dimens.spacingMd,
                    bottom = Dimens.spacingSm
                )
            )
        }
        val cells = GridCells.Fixed(config.laneCount.coerceAtLeast(1))
        val spacing = Arrangement.spacedBy(Dimens.spacingSm)
        val padding = PaddingValues(
            horizontal = Dimens.spacingMd,
            vertical = Dimens.spacingSm
        )
        val cell: @Composable (Int, CarouselItem) -> Unit = { index, item ->
            AutoGridCell(
                item = item,
                isFocused = index == focusedIndex,
                showTitle = config.showTitles,
                showPlatformBadge = showPlatformBadge,
                downloadIndicator = downloadIndicatorFor(item),
                onTap = { onItemTap(index) },
                onLongPress = { onItemLongPress(index) },
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded
            )
        }
        when (config.scrollAxis) {
            HomeScrollAxis.VERTICAL -> LazyVerticalGrid(
                columns = cells,
                state = gridState,
                contentPadding = padding,
                horizontalArrangement = spacing,
                verticalArrangement = spacing,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { _, item -> item.key }) { index, item -> cell(index, item) }
            }
            HomeScrollAxis.HORIZONTAL -> LazyHorizontalGrid(
                rows = cells,
                state = gridState,
                contentPadding = padding,
                horizontalArrangement = spacing,
                verticalArrangement = spacing,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { _, item -> item.key }) { index, item -> cell(index, item) }
            }
        }
    }
}

@Composable
private fun AutoGridCell(
    item: CarouselItem,
    isFocused: Boolean,
    showTitle: Boolean,
    showPlatformBadge: Boolean,
    downloadIndicator: GameDownloadIndicator,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCoverLoadFailed: ((Long, String) -> Unit)?,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)?
) {
    val coverAspectRatio = LocalBoxArtStyle.current.aspectRatio
    Column(modifier = Modifier.fillMaxWidth()) {
        when (item) {
            is CarouselItem.Game -> GameCard(
                game = item.game,
                isFocused = isFocused,
                focusScale = ComponentDefaults.Focus.scaleFocused,
                downloadIndicator = downloadIndicator,
                showPlatformBadge = showPlatformBadge,
                coverPathOverride = item.coverPathOverride,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .clickableNoFocus(onClick = onTap, onLongClick = onLongPress)
            )
            is CarouselItem.ViewAll -> ViewAllCard(
                isFocused = isFocused,
                onClick = onTap,
                remainingCount = item.remainingCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
            )
        }
        if (showTitle && item is CarouselItem.Game) {
            Text(
                text = item.game.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}
