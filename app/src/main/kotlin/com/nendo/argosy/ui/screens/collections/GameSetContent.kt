package com.nendo.argosy.ui.screens.collections

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.common.LocalRunningGames
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.components.ActiveFilterChipRow
import com.nendo.argosy.ui.components.AlphabetSidebar
import com.nendo.argosy.ui.components.AlphabetSidebarWidth
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.gamelist.GameListRow
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.library.FilterCategory
import com.nendo.argosy.ui.screens.library.FilterMenuOverlay
import com.nendo.argosy.ui.screens.library.FilteredEmptyState
import com.nendo.argosy.ui.screens.library.LibraryGridItem
import com.nendo.argosy.ui.screens.library.chips
import com.nendo.argosy.ui.screens.library.components.LibraryGameGrid
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.reducedMotion

data class GameSetLabels(
    @StringRes val loading: Int,
    @StringRes val filteredEmptyTitle: Int,
    @StringRes val filteredEmptyBody: Int,
    @StringRes val filteredEmptyHint: Int
)

@Composable
internal fun GameSetGames(
    listing: GameSetListing,
    layout: GameSetLayout,
    filter: GameSetFilterUi,
    focusedIndex: Int,
    isFocusShown: Boolean,
    scrollTrigger: Int,
    labels: GameSetLabels,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    onScreenWidth: (Int) -> Unit,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onSectionClick: (String) -> Unit,
    emptyContent: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.screenWidthDp) { onScreenWidth(configuration.screenWidthDp) }

    val games = listing.listing.games
    when {
        !listing.isLoaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(labels.loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        listing.totalCount == 0 -> emptyContent()
        games.isEmpty() -> FilteredEmptyState(
            title = stringResource(labels.filteredEmptyTitle),
            body = stringResource(
                labels.filteredEmptyBody,
                filter.panel.activeFilters.summary(LocalContext.current)
            ),
            filterHint = stringResource(labels.filteredEmptyHint)
        )
        else -> GameSetPopulated(
            listing = listing,
            layout = layout,
            focusedIndex = focusedIndex,
            isFocusShown = isFocusShown,
            scrollTrigger = scrollTrigger,
            downloadIndicators = downloadIndicators,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onSectionClick = onSectionClick
        )
    }
}

@Composable
private fun GameSetPopulated(
    listing: GameSetListing,
    layout: GameSetLayout,
    focusedIndex: Int,
    isFocusShown: Boolean,
    scrollTrigger: Int,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onSectionClick: (String) -> Unit
) {
    val games = listing.listing.games
    val gridItems = listing.listing.gridItems
    val sectionLabels = listing.listing.sectionLabels
    val showSidebar = sectionLabels.size > 1
    val sidebarWidth = if (showSidebar) AlphabetSidebarWidth else 0.dp
    val reduced = reducedMotion()
    val density = LocalDensity.current
    val bottomInset = withNavBarClearance(Dimens.footerHeight)
    val bottomInsetPx = with(density) { bottomInset.toPx() }.toInt()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (layout.isListLayout) {
            val listState = rememberLazyListState()
            LaunchedEffect(focusedIndex, scrollTrigger) {
                if (focusedIndex in games.indices) {
                    listState.animateScrollToItemCentered(focusedIndex, reduced, bottomInsetPx = bottomInsetPx)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.spacingLg,
                    end = Dimens.spacingLg + sidebarWidth,
                    top = Dimens.spacingSm,
                    bottom = bottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                    GameListRow(
                        title = game.title,
                        platformSlug = game.platformSlug,
                        platformDisplayName = game.platformDisplayName,
                        coverPath = rememberResolvedCoverPath(game.id, game.coverPath),
                        details = game.listDetails,
                        isDownloaded = game.isDownloaded,
                        isFocused = isFocusShown && focusedIndex == index,
                        onClick = { onItemClick(index) },
                        onLongClick = { onItemLongClick(index) },
                        running = LocalRunningGames.current.markFor(game.id)
                    )
                }
            }
        } else {
            val gridState = rememberLazyGridState()
            val columns = layout.columnsCount
            val gridSpacing = layout.gridSpacingDp.dp
            val aspectRatio = LocalBoxArtStyle.current.aspectRatio
            val columnWidth = (maxWidth - gridSpacing * (columns + 1) - sidebarWidth) / columns
            val cardHeight = columnWidth / aspectRatio
            val focusedGridIndex = remember(gridItems, focusedIndex) {
                gridItems.indexOfFirst { it is LibraryGridItem.Game && it.gameIndex == focusedIndex }
            }
            LaunchedEffect(focusedGridIndex, scrollTrigger) {
                if (focusedGridIndex >= 0) {
                    gridState.animateScrollToItemCentered(focusedGridIndex, reduced, bottomInsetPx = bottomInsetPx)
                }
            }
            LibraryGameGrid(
                gridItems = gridItems,
                focusedIndex = if (isFocusShown) focusedIndex else -1,
                columns = columns,
                gridSpacing = gridSpacing,
                sidebarWidth = sidebarWidth,
                cardHeight = cardHeight,
                topPadding = Dimens.spacingSm,
                showFocus = isFocusShown,
                showPlatformBadge = true,
                useBoxArt = layout.boxArt3d,
                gridState = gridState,
                coverPathOverrides = emptyMap(),
                downloadIndicatorFor = { downloadIndicators[it] ?: GameDownloadIndicator.NONE },
                onTouchDown = {},
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick
            )
        }

        if (showSidebar) {
            AlphabetSidebar(
                availableLetters = sectionLabels,
                currentLetter = listing.listing.sectionLabelForGameIndex(focusedIndex),
                onLetterClick = onSectionClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                topPadding = Dimens.spacingSm,
                bottomPadding = bottomInset
            )
        }
    }
}

@Composable
internal fun GameSetFilterChips(filter: GameSetFilterUi, label: String, modifier: Modifier = Modifier) {
    val chips = remember(filter.panel.activeFilters) { filter.panel.activeFilters.chips }
    ActiveFilterChipRow(label = label, chips = chips, modifier = modifier)
}

@Composable
internal fun GameSetFilterHost(
    filter: GameSetFilterUi,
    keyboardLabel: String,
    keyboardSubmitLabel: String,
    onDismiss: () -> Unit,
    onCategorySelect: (FilterCategory) -> Unit,
    onOptionSelect: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFieldTap: () -> Unit,
    onCloseKeyboard: () -> Unit
) {
    val searchFieldFocus = remember { FocusRequester() }
    ControllerKeyboardSession(
        open = filter.isSearchKeyboardOpen && filter.showFilterMenu &&
            filter.panel.currentFilterCategory == FilterCategory.SEARCH,
        label = keyboardLabel,
        text = filter.panel.activeFilters.searchQuery,
        onTextChange = onSearchQueryChange,
        onSubmit = onCloseKeyboard,
        onClose = onCloseKeyboard,
        submitLabel = keyboardSubmitLabel,
        onSystemKeyboard = {
            onCloseKeyboard()
            searchFieldFocus.requestFocus()
        }
    )
    AnimatedVisibility(visible = filter.showFilterMenu, enter = fadeIn(), exit = fadeOut()) {
        FilterMenuOverlay(
            uiState = filter.panel,
            searchFieldFocus = searchFieldFocus,
            onDismiss = onDismiss,
            onCategorySelect = onCategorySelect,
            onOptionSelect = onOptionSelect,
            onSearchQueryChange = onSearchQueryChange,
            onSearchFieldFocused = onCloseKeyboard,
            onSearchFieldTap = onSearchFieldTap
        )
    }
}
