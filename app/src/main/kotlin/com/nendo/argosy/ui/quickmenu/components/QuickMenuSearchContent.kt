package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.quickmenu.QuickMenuFormFactor
import com.nendo.argosy.ui.quickmenu.RecentSearchUi
import com.nendo.argosy.ui.quickmenu.SEARCH_FIELD_INDEX
import com.nendo.argosy.ui.quickmenu.SearchResultsPreview
import com.nendo.argosy.ui.quickmenu.SearchTabState
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus

private const val LANDSCAPE_RESULT_COLUMNS = 4
private const val FLAT_TILE_SCALE = 1f

/**
 * The Search tab landing: the field button and the recent searches, or the newest games when
 * there are none. [focusIndex] 0 is the field. On [QuickMenuFormFactor.LANDSCAPE] the focused
 * query's [results] fill the right half.
 */
@Composable
fun QuickMenuSearchContent(
    search: SearchTabState,
    results: SearchResultsPreview?,
    formFactor: QuickMenuFormFactor,
    focusIndex: Int,
    showCursor: Boolean,
    gridSpacingDp: Int,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    onStopTap: (Int) -> Unit,
    onGameLongPress: (Int) -> Unit,
    onResultTap: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (formFactor != QuickMenuFormFactor.LANDSCAPE) {
        SearchLanding(search, formFactor, focusIndex, showCursor, downloadIndicators, onStopTap, onGameLongPress, modifier.fillMaxSize())
        return
    }
    Row(
        modifier = modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        SearchLanding(
            search,
            formFactor,
            focusIndex,
            showCursor,
            downloadIndicators,
            onStopTap,
            onGameLongPress,
            Modifier.weight(ComponentDefaults.QuickMenu.listPaneRatio).fillMaxHeight()
        )
        Box(modifier = Modifier.weight(1f - ComponentDefaults.QuickMenu.listPaneRatio).fillMaxHeight()) {
            val games = results?.games
            if (results != null && !games.isNullOrEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.ui_quick_menu_search_results_heading,
                            games.size,
                            games.size,
                            results.query
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalArgosyTheme.current.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val aspectRatio = LocalBoxArtStyle.current.aspectRatio
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(LANDSCAPE_RESULT_COLUMNS),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(gridSpacingDp.dp)
                    ) {
                        itemsIndexed(games, key = { _, game -> game.id }) { _, game ->
                            GameCard(
                                game = game.toCard(),
                                isFocused = false,
                                focusScale = FLAT_TILE_SCALE,
                                showDownloadedIndicator = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                                    .clickableNoFocus { onResultTap(game.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLanding(
    search: SearchTabState,
    formFactor: QuickMenuFormFactor,
    focusIndex: Int,
    showCursor: Boolean,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    onStopTap: (Int) -> Unit,
    onGameLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()
    val reduced = reducedMotion()
    LaunchedEffect(focusIndex, showCursor) {
        if (showCursor && focusIndex > SEARCH_FIELD_INDEX) listState.animateScrollToItemCentered(focusIndex + 1, reduced)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        item(key = "field") {
            SearchField(focused = showCursor && focusIndex == SEARCH_FIELD_INDEX, onTap = { onStopTap(SEARCH_FIELD_INDEX) })
        }
        if (!search.isLoaded || search.rowCount == 0) return@LazyColumn
        item(key = "heading") {
            Text(
                text = stringResource(
                    if (search.showsRecentlyAdded) R.string.ui_quick_menu_search_recently_added_heading
                    else R.string.ui_quick_menu_search_recent_heading
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
                modifier = Modifier.padding(start = Dimens.spacingMd, top = Dimens.spacingSm)
            )
        }
        if (search.showsRecentlyAdded) {
            itemsIndexed(search.recentlyAdded, key = { _, game -> "game:${game.id}" }) { row, game ->
                QuickMenuGameRow(
                    game = game,
                    isFocused = showCursor && focusIndex == row + 1,
                    onClick = { onStopTap(row + 1) },
                    onLongClick = { onGameLongPress(row + 1) },
                    downloadIndicator = downloadIndicators[game.id] ?: GameDownloadIndicator.NONE
                )
            }
        } else {
            itemsIndexed(search.recentSearches, key = { _, entry -> "query:${entry.query}" }) { row, entry ->
                RecentSearchRow(
                    entry = entry,
                    focused = showCursor && focusIndex == row + 1,
                    showCovers = formFactor == QuickMenuFormFactor.PORTRAIT,
                    onTap = { onStopTap(row + 1) }
                )
            }
        }
    }
}

@Composable
private fun SearchField(focused: Boolean, onTap: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.menuRowHeightLg)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators(fill = true, ring = true), shape = shape)
            .border(Dimens.borderThin, theme.hairlineHigh, shape)
            .clickableNoFocus(onClick = onTap)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (focused) theme.focusAccent else theme.textDim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = stringResource(R.string.ui_quick_menu_search_field),
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) theme.focusAccent else theme.textDim,
            maxLines = 1
        )
    }
}

@Composable
private fun RecentSearchRow(entry: RecentSearchUi, focused: Boolean, showCovers: Boolean, onTap: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val count = pluralStringResource(R.plurals.ui_quick_menu_search_result_count, entry.resultCount, entry.resultCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showCovers) Dimens.menuRowHeightLg else Dimens.menuRowHeight)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators(fill = true, ring = true), shape = shape)
            .clickableNoFocus(onClick = onTap)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = theme.textDim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        if (showCovers) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (focused) theme.focusAccent else theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = count, style = MaterialTheme.typography.labelSmall, color = theme.textDim, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                entry.coverPaths.forEach { path ->
                    QuickMenuCover(
                        path = path,
                        title = entry.query,
                        showTitle = false,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(ComponentDefaults.QuickMenuRandom.coverAspectRatio, matchHeightConstraintsFirst = true)
                    )
                }
            }
        } else {
            Text(
                text = entry.query,
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) theme.focusAccent else theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(text = count, style = MaterialTheme.typography.labelSmall, color = theme.textDim, maxLines = 1)
        }
    }
}
