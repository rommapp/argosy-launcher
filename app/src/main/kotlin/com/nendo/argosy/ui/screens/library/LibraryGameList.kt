package com.nendo.argosy.ui.screens.library

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.nendo.argosy.ui.util.observeTouchDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.LibraryListStyle
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.components.fastAnimateScrollToItem
import com.nendo.argosy.ui.components.gamelist.CompactGameListRow
import com.nendo.argosy.ui.components.gamelist.GameCoverImage
import com.nendo.argosy.ui.components.gamelist.GameListRow
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

@Composable
internal fun LibraryGameList(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    sidebarWidth: Dp,
    headerHeightPx: Int,
    footerHeightPx: Int,
    onGameSelect: (Long) -> Unit
) {
    val isCompact = uiState.libraryListStyle == LibraryListStyle.COMPACT
    val headerPadding = with(LocalDensity.current) { headerHeightPx.toDp() }
    val initialIndex = remember { viewModel.gameIndexToGridIndex(uiState.focusedIndex) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val downloadIndicators = viewModel.downloadIndicators.collectAsState()

    suspend fun revealCompactSelection() {
        val listIndex = viewModel.gameIndexToGridIndex(uiState.focusedIndex)
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == listIndex }
        if (item == null) {
            listState.fastAnimateScrollToItem(index = listIndex, scrollOffset = 0)
        } else {
            val top = layout.viewportStartOffset + headerHeightPx
            val bottom = layout.viewportEndOffset - footerHeightPx
            val distance = when {
                item.offset < top -> item.offset - top
                item.offset + item.size > bottom -> item.offset + item.size - bottom
                else -> 0
            }
            if (distance != 0) listState.animateScrollBy(distance.toFloat())
        }
    }

    LaunchedEffect(uiState.currentPlatformIndex) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(uiState.games.size) {
        if (uiState.games.isNotEmpty() && uiState.focusedIndex > 0) {
            listState.scrollToItem(viewModel.gameIndexToGridIndex(uiState.focusedIndex))
        }
    }

    LaunchedEffect(uiState.focusedIndex, uiState.lastFocusMove, isCompact) {
        if (uiState.lastFocusMove == null || uiState.games.isEmpty()) return@LaunchedEffect
        if (isCompact) {
            revealCompactSelection()
            return@LaunchedEffect
        }
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        if (viewportHeight == 0) return@LaunchedEffect
        val listIndex = viewModel.gameIndexToGridIndex(uiState.focusedIndex)
        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull { it.index == listIndex }?.size
            ?: layoutInfo.visibleItemsInfo.firstOrNull()?.size
            ?: return@LaunchedEffect
        val centeringOffset = (viewportHeight - headerHeightPx - footerHeightPx - itemHeight) / 2
        listState.animateScrollToItem(index = listIndex, scrollOffset = -centeringOffset)
    }

    LaunchedEffect(uiState.sectionJumpTrigger) {
        if (uiState.sectionJumpTrigger == 0 || uiState.games.isEmpty()) return@LaunchedEffect
        if (isCompact) {
            revealCompactSelection()
            return@LaunchedEffect
        }
        val listIndex = viewModel.gameIndexToGridIndex(uiState.focusedIndex)
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        if (viewportHeight == 0) {
            listState.scrollToItem(listIndex)
            return@LaunchedEffect
        }
        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
        val centeringOffset = if (itemHeight > 0) {
            (viewportHeight - headerHeightPx - footerHeightPx - itemHeight) / 2
        } else {
            0
        }
        listState.fastAnimateScrollToItem(index = listIndex, scrollOffset = -centeringOffset)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showArtwork = isCompact && maxWidth > maxHeight
        val artworkTop = headerPadding + Dimens.spacingXs
        val artworkBottom = withNavBarClearance(Dimens.footerHeight)
        val artworkWidth = minOf(
            (maxHeight - artworkTop - artworkBottom) * ComponentDefaults.GameListRow.compactArtworkAspectRatio +
                Dimens.spacingMd,
            (maxWidth - sidebarWidth) * ComponentDefaults.GameListRow.compactArtworkMaxWidthRatio
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (showArtwork) sidebarWidth else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(if (showArtwork) Dimens.spacingLg else 0.dp)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = Dimens.spacingMd,
                    end = if (showArtwork) 0.dp else Dimens.spacingMd + sidebarWidth,
                    top = if (isCompact) headerPadding + Dimens.spacingXs else Dimens.headerHeightLg,
                    bottom = withNavBarClearance(if (isCompact) Dimens.footerHeight else Dimens.headerHeightLg)
                ),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) Dimens.spacingXs else Dimens.spacingSm),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .observeTouchDown { viewModel.enterTouchMode() }
            ) {
                items(
                    count = uiState.gridItems.size,
                    key = { i ->
                        when (val item = uiState.gridItems[i]) {
                            is LibraryGridItem.Header -> "header-${item.label}"
                            is LibraryGridItem.Game -> item.game.id
                        }
                    },
                    contentType = { i ->
                        when (uiState.gridItems[i]) {
                            is LibraryGridItem.Header -> "header"
                            is LibraryGridItem.Game -> "game"
                        }
                    }
                ) { index ->
                    when (val item = uiState.gridItems[index]) {
                        is LibraryGridItem.Header -> SectionDivider(label = item.label, compact = isCompact)
                        is LibraryGridItem.Game -> {
                            val game = item.game
                            val indicator by remember(game.id) {
                                derivedStateOf { downloadIndicators.value[game.id] ?: GameDownloadIndicator.NONE }
                            }
                            val isFocused = item.gameIndex == uiState.focusedIndex &&
                                (!uiState.isTouchMode || uiState.hasSelectedGame)
                            val onClick = { viewModel.handleItemTap(item.gameIndex, onGameSelect) }
                            val onLongClick = { viewModel.handleItemLongPress(item.gameIndex) }
                            if (isCompact) {
                                CompactGameListRow(
                                    title = game.title,
                                    isDownloaded = game.isDownloaded,
                                    needsInstall = game.needsInstall,
                                    isFocused = isFocused,
                                    downloadIndicator = indicator,
                                    saveState = uiState.saveStates[game.id],
                                    onClick = onClick,
                                    onLongClick = onLongClick
                                )
                            } else {
                                GameListRow(
                                    title = game.title,
                                    platformSlug = game.platformSlug,
                                    platformDisplayName = game.platformDisplayName,
                                    coverPath = uiState.coverPathFor(game),
                                    details = game.listDetails,
                                    isDownloaded = game.isDownloaded,
                                    needsInstall = game.needsInstall,
                                    isFocused = isFocused,
                                    downloadIndicator = indicator,
                                    friends = game.listDetails.igdbId
                                        ?.let { uiState.friendsActivity[it.toInt()] }
                                        .orEmpty(),
                                    saveState = uiState.saveStates[game.id],
                                    onCoverLoadFailed = { path -> viewModel.repairCoverImage(game.id, path) },
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    running = com.nendo.argosy.ui.common.LocalRunningGames.current.markFor(game.id)
                                )
                            }
                        }
                    }
                }
            }
            if (showArtwork) {
                uiState.focusedGame?.let { game ->
                    GameCoverImage(
                        title = game.title,
                        coverPath = uiState.coverPathFor(game),
                        contentScale = ContentScale.Fit,
                        onLoadFailed = { viewModel.repairCoverImage(game.id, it) },
                        modifier = Modifier
                            .width(artworkWidth)
                            .fillMaxHeight()
                            .padding(top = artworkTop, bottom = artworkBottom, end = Dimens.spacingMd)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryUiState.coverPathFor(game: LibraryGameUi): String? =
    repairedCoverPaths[game.id] ?: rememberResolvedCoverPath(game.id, game.coverPath)
