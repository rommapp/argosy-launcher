package com.nendo.argosy.ui.screens.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.navigation.navTransitionOrigin
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.library.LibraryGameUi
import com.nendo.argosy.ui.screens.library.LibraryGridItem
import com.nendo.argosy.ui.screens.library.SectionDivider
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.observeTouchDown

@Composable
internal fun LibraryGameGrid(
    gridItems: List<LibraryGridItem>,
    focusedIndex: Int,
    columns: Int,
    gridSpacing: Dp,
    sidebarWidth: Dp,
    cardHeight: Dp,
    topPadding: Dp,
    showFocus: Boolean,
    showPlatformBadge: Boolean,
    useBoxArt: Boolean,
    gridState: LazyGridState,
    coverPathOverrides: Map<Long, String>,
    downloadIndicatorFor: (Long) -> GameDownloadIndicator,
    onTouchDown: () -> Unit,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onCoverLoadFailed: ((Long, String) -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = PaddingValues(
            start = gridSpacing,
            end = gridSpacing + sidebarWidth,
            top = topPadding,
            bottom = withNavBarClearance(cardHeight + gridSpacing)
        ),
        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
        verticalArrangement = Arrangement.spacedBy(gridSpacing),
        modifier = Modifier
            .fillMaxSize()
            .observeTouchDown(onTouchDown)
    ) {
        items(
            count = gridItems.size,
            key = { i ->
                when (val item = gridItems[i]) {
                    is LibraryGridItem.Header -> "header-${item.label}"
                    is LibraryGridItem.Game -> item.game.id
                }
            },
            span = { i ->
                when (gridItems[i]) {
                    is LibraryGridItem.Header -> GridItemSpan(maxLineSpan)
                    is LibraryGridItem.Game -> GridItemSpan(1)
                }
            }
        ) { index ->
            when (val item = gridItems[index]) {
                is LibraryGridItem.Header -> SectionDivider(label = item.label)
                is LibraryGridItem.Game -> {
                    val isFocused = item.gameIndex == focusedIndex
                    LibraryGameCard(
                        game = item.game,
                        isFocused = isFocused,
                        showFocus = showFocus,
                        cardHeight = cardHeight,
                        showPlatformBadge = showPlatformBadge,
                        coverPathOverride = coverPathOverrides[item.game.id],
                        onCoverLoadFailed = onCoverLoadFailed,
                        downloadIndicatorFor = downloadIndicatorFor,
                        useBoxArt = useBoxArt,
                        onClick = { onItemClick(item.gameIndex) },
                        onLongClick = { onItemLongClick(item.gameIndex) },
                        modifier = Modifier.zIndex(if (isFocused) 1f else 0f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryGameCard(
    game: LibraryGameUi,
    isFocused: Boolean,
    showFocus: Boolean,
    cardHeight: Dp?,
    showPlatformBadge: Boolean = true,
    coverPathOverride: String? = null,
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    downloadIndicatorFor: (Long) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    useBoxArt: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val effectiveFocused = isFocused && showFocus
    val saturation = if (showFocus && !isFocused) 0.4f else null
    val indicatorFor by rememberUpdatedState(downloadIndicatorFor)
    val downloadIndicator by remember(game.id) { derivedStateOf { indicatorFor(game.id) } }
    GameCard(
        game = HomeGameUi(
            id = game.id,
            title = game.title,
            platformId = game.platformId,
            platformSlug = game.platformSlug,
            platformDisplayName = game.platformDisplayName,
            platformShortName = game.platformDisplayName,
            coverPath = game.coverPath,
            boxSpinePath = game.boxSpinePath,
            box3dPath = game.box3dPath,
            gradientColors = game.gradientColors,
            backgroundPath = null,
            developer = null,
            releaseYear = null,
            genre = null,
            isFavorite = game.isFavorite,
            isDownloaded = game.isDownloaded,
            isRommGame = game.isRommGame,
            needsInstall = game.needsInstall
        ),
        isFocused = effectiveFocused,
        showPlatformBadge = showPlatformBadge,
        coverPathOverride = coverPathOverride,
        onCoverLoadFailed = onCoverLoadFailed,
        downloadIndicator = downloadIndicator,
        saturationOverride = saturation,
        useBoxArt = useBoxArt,
        modifier = modifier
            .fillMaxWidth()
            .then(if (cardHeight != null) Modifier.height(cardHeight) else Modifier)
            .navTransitionOrigin(game.id, isFocused)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
    )
}
