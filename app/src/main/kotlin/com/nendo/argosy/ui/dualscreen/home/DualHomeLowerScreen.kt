/**
 * DUAL-SCREEN COMPONENT - Lower display game carousel.
 * Runs in :companion process (SecondaryHomeActivity).
 * Communicates selection to upper display via broadcasts.
 * Uses custom InputHandler focus (selectedIndex from ViewModel).
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.hardware.CompanionAppBar
import com.nendo.argosy.ui.common.rememberCoverAspectRatio
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.AlphabetSidebar
import com.nendo.argosy.ui.components.CarouselAnchor
import com.nendo.argosy.ui.components.CarouselItem
import com.nendo.argosy.ui.components.CarouselMetrics
import com.nendo.argosy.ui.components.CarouselOverrides
import com.nendo.argosy.ui.components.CarouselRail
import com.nendo.argosy.ui.components.HomeAutoGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.nendo.argosy.ui.components.CarouselTapMode
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.PositionIndicator
import com.nendo.argosy.ui.components.ViewAllCardStyle
import com.nendo.argosy.ui.components.fastAnimateScrollToItem
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.components.SectionBreadcrumb
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.touchOnly
import kotlin.math.abs

@Composable
fun DualHomeLowerScreen(
    games: List<HomeGameUi>,
    selectedIndex: Int,
    platformName: String,
    totalCount: Int,
    hasMoreGames: Boolean,
    isViewAllFocused: Boolean,
    homeApps: List<String>,
    appBarFocused: Boolean,
    appBarIndex: Int,
    viewMode: DualHomeViewMode,
    sectionLabels: List<String> = emptyList(),
    currentSectionIndex: Int = 0,
    onPreviousSection: () -> Unit = {},
    onNextSection: () -> Unit = {},
    onSelectSection: (Int) -> Unit = {},
    repairedCoverPaths: Map<Long, String> = emptyMap(),
    onGameTapped: (Int) -> Unit,
    onGameSelected: (Long) -> Unit,
    onCoverLoadFailed: (Long, String) -> Unit = { _, _ -> },
    onAppClick: (String) -> Unit,
    onCollectionsClick: () -> Unit,
    onLibraryToggle: () -> Unit,
    onViewAllClick: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    carouselConfig: com.nendo.argosy.domain.model.CarouselConfig =
        com.nendo.argosy.domain.model.CarouselConfig(),
    autoGridConfig: com.nendo.argosy.domain.model.AutoGridConfig =
        com.nendo.argosy.domain.model.AutoGridConfig(),
    layoutKind: com.nendo.argosy.domain.model.HomeLayoutKind =
        com.nendo.argosy.domain.model.HomeLayoutKind.CAROUSEL,
    isPlatformSection: Boolean = false,
    customGridState: com.nendo.argosy.ui.components.CustomGridState =
        com.nendo.argosy.ui.components.CustomGridState(),
    customGridContentFor: (com.nendo.argosy.domain.model.HomeTile) ->
    com.nendo.argosy.ui.components.CustomGridTileContent? = { null },
    customGridConfig: com.nendo.argosy.domain.model.CustomGridConfig =
        com.nendo.argosy.domain.model.CustomGridConfig(),
    onCustomGridCellTap: (com.nendo.argosy.domain.model.GridCell) -> Unit = {},
    onCustomGridShape: (Int, Int) -> Unit = { _, _ -> },
    onCustomGridAddPage: () -> Unit = {},
    onCustomGridTileLongPress: (com.nendo.argosy.domain.model.GridCell) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isAutoGrid = layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.AUTO_GRID
    val isCustomGrid = layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var railBand by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val metrics = CarouselMetrics.centered(
        coverAspectRatio = LocalBoxArtStyle.current.aspectRatio,
        config = carouselConfig,
        availableHeight = with(density) { railBand.height.toDp() },
        availableWidth = with(density) { railBand.width.toDp() }
    )
    val railItems = rememberCompanionCarouselItems(
        games = games,
        hasMoreGames = hasMoreGames,
        totalCount = totalCount,
        repairedCoverPaths = repairedCoverPaths
    )

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentGames by rememberUpdatedState(games)
    val currentOnGameTapped by rememberUpdatedState(onGameTapped)
    var skipNextProgrammatic by remember { mutableStateOf(false) }
    var isUserScroll by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, games, isCustomGrid) {
        if (games.isNotEmpty() && !isCustomGrid) {
            if (selectedIndex in games.indices) {
                com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.onGameSelected(games[selectedIndex].toShowcaseState())
            }
            if (!skipNextProgrammatic) {
                if (selectedIndex in games.indices) {
                    listState.animateScrollToItem(
                        index = selectedIndex,
                        scrollOffset = CarouselAnchor.CENTER.snapOffsetPx
                    )
                } else if (hasMoreGames && selectedIndex == games.size) {
                    listState.animateScrollToItem(
                        index = games.size,
                        scrollOffset = CarouselAnchor.CENTER.snapOffsetPx
                    )
                }
            } else {
                skipNextProgrammatic = false
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(listState.isScrollInProgress, isUserScroll, listState.layoutInfo)
        }.collect { (isScrolling, userScroll, layoutInfo) ->
            if (isScrolling && userScroll) {
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = layoutInfo.visibleItemsInfo.minByOrNull {
                    abs((it.offset + it.size / 2) - viewportCenter)
                }
                if (closest != null &&
                    closest.index != currentSelectedIndex &&
                    closest.index < currentGames.size
                ) {
                    skipNextProgrammatic = true
                    currentOnGameTapped(closest.index)
                }
            }
            if (!isScrolling && userScroll) {
                isUserScroll = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalArgosyTheme.current.surfaceBase)
            .surfaceBackdrop(BackdropRole.CONTENT)
    ) {
        if (viewMode == DualHomeViewMode.CAROUSEL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalArgosyTheme.current.surfaceRaised.copy(alpha = 0.4f))
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(onClick = onCollectionsClick) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = LocalArgosyTheme.current.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Collections",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalArgosyTheme.current.textPrimary
                    )
                }
                IconButton(onClick = onLibraryToggle) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = "Library Grid",
                        tint = LocalArgosyTheme.current.textDim
                    )
                }
            }
        }

        if (viewMode == DualHomeViewMode.CAROUSEL && !isCustomGrid && sectionLabels.isNotEmpty()) {
            SectionBreadcrumb(
                labels = sectionLabels,
                currentIndex = currentSectionIndex,
                onPrevious = onPreviousSection,
                onNext = onNextSection,
                onSelect = onSelectSection,
                fillAvailableWidth = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg)
            )
        }

        if (!isCustomGrid) {
            Text(
                text = "$platformName ($totalCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        if (isCustomGrid) {
            com.nendo.argosy.ui.components.CustomGridSurface(
                state = customGridState,
                contentFor = customGridContentFor,
                laneCount = customGridConfig.laneCount,
                onCellTap = onCustomGridCellTap,
                onShapeResolved = onCustomGridShape,
                onAddPage = onCustomGridAddPage,
                onTileLongPress = onCustomGridTileLongPress,
                onCoverLoadFailed = onCoverLoadFailed,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else if (isAutoGrid) {
            HomeAutoGrid(
                items = railItems,
                focusedIndex = selectedIndex,
                config = autoGridConfig,
                gridState = gridState,
                sectionTitle = platformName,
                showSectionTitle = false,
                showPlatformBadge = false,
                onItemTap = { index ->
                    val game = games.getOrNull(index)
                    if (game != null) {
                        onGameTapped(index)
                        onGameSelected(game.id)
                    } else {
                        onViewAllClick()
                    }
                },
                onItemLongPress = { index -> onGameTapped(index) },
                onCoverLoadFailed = onCoverLoadFailed,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = Dimens.spacingSm)
                .onSizeChanged { railBand = it },
            contentAlignment = Alignment.Center
        ) {
            CarouselRail(
                items = railItems,
                focusedIndex = selectedIndex,
                listState = listState,
                metrics = metrics,
                tapMode = CarouselTapMode.TOUCH,
                overrides = CarouselOverrides(focusedAlpha = 1f, unfocusedAlpha = 0.5f),
                showFocusVisuals = !appBarFocused,
                showPlatformBadge = carouselConfig.showPlatformBadge && !isPlatformSection,
                showNewBadge = false,
                viewAllStyle = ViewAllCardStyle.ACCENT_COUNT,
                onItemTap = { index ->
                    val game = games.getOrNull(index)
                    if (game != null) {
                        onGameTapped(index)
                        onGameSelected(game.id)
                    } else {
                        onViewAllClick()
                    }
                },
                onCoverLoadFailed = onCoverLoadFailed,
                modifier = Modifier
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(
                                    androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                )
                                isUserScroll = true
                            }
                        }
                    }
            )
        }

        PositionIndicator(
            totalCount = games.size,
            currentIndex = if (isViewAllFocused) -1 else selectedIndex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
        )
        }

        val showAppBar = com.nendo.argosy.DualScreenManagerHolder.instance
            ?.isExternalDisplay != true
        if (showAppBar) {
            CompanionAppBar(
                apps = homeApps,
                onAppClick = onAppClick,
                focusedIndex = if (appBarFocused) appBarIndex else -2,
                onOpenDrawer = onOpenDrawer
            )
        }
    }
}

// --- Collection List ---

@Composable
fun DualHomeCollectionList(
    items: List<DualCollectionListItem>,
    selectedIndex: Int,
    onCollectionTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (items.isNotEmpty() && selectedIndex in items.indices) {
            listState.animateScrollToItem(selectedIndex, scrollOffset = -200)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(LocalArgosyTheme.current.surfaceBase)
            .surfaceBackdrop(BackdropRole.CONTENT),
        contentPadding = PaddingValues(vertical = Dimens.spacingLg, horizontal = Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        itemsIndexed(items) { index, item ->
            when (item) {
                is DualCollectionListItem.Header -> {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalArgosyTheme.current.focusAccent,
                        modifier = Modifier.padding(
                            top = if (index > 0) Dimens.spacingMd else 0.dp,
                            bottom = Dimens.spacingXs
                        )
                    )
                }
                is DualCollectionListItem.Collection -> {
                    DualCollectionRow(
                        item = item,
                        isSelected = index == selectedIndex,
                        onClick = { onCollectionTapped(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DualCollectionRow(
    item: DualCollectionListItem.Collection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            theme.focusAccent.copy(alpha = 0.15f)
                                .compositeOver(theme.surfaceRaised),
                            RoundedCornerShape(Dimens.radiusControl)
                        )
                        .border(
                            Dimens.borderMedium,
                            theme.focusAccent,
                            RoundedCornerShape(Dimens.radiusControl)
                        )
                } else {
                    Modifier.background(
                        theme.surfaceRaised,
                        RoundedCornerShape(Dimens.radiusControl)
                    )
                }
            )
            .touchOnly(onClick)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        CollectionCoverMosaic(
            coverPaths = item.coverPaths,
            modifier = Modifier.size(56.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.platformSummary.isNotBlank()) {
                Text(
                    text = item.platformSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "${item.gameCount} games",
            style = MaterialTheme.typography.labelMedium,
            color = theme.textDim
        )
    }
}

@Composable
private fun CollectionCoverMosaic(
    coverPaths: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(LocalArgosyTheme.current.surfaceElevated)
    ) {
        when {
            coverPaths.isEmpty() -> {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = LocalArgosyTheme.current.textDim,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
            coverPaths.size == 1 -> {
                AsyncImage(
                    model = rememberFileImageModel(coverPaths[0]),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val displayed = coverPaths.take(4)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        displayed.getOrNull(0)?.let { path ->
                            AsyncImage(
                                model = rememberFileImageModel(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        displayed.getOrNull(1)?.let { path ->
                            AsyncImage(
                                model = rememberFileImageModel(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                    if (displayed.size > 2) {
                        Row(modifier = Modifier.weight(1f)) {
                            displayed.getOrNull(2)?.let { path ->
                                AsyncImage(
                                    model = rememberFileImageModel(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            }
                            displayed.getOrNull(3)?.let { path ->
                                AsyncImage(
                                    model = rememberFileImageModel(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            } ?: Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// --- Library Grid ---

@Composable
fun DualHomeLibraryGrid(
    gridItems: List<DualLibraryGridItem>,
    focusedIndex: Int,
    columns: Int,
    sectionLabels: List<String>,
    currentSectionLabel: String,
    platformLabel: String = "All",
    showSectionOverlay: Boolean = false,
    overlaySectionLabel: String = "",
    repairedCoverPaths: Map<Long, String> = emptyMap(),
    onGameTapped: (Int) -> Unit,
    onCoverLoadFailed: (Long, String) -> Unit = { _, _ -> },
    onSectionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val staggeredState = rememberLazyStaggeredGridState()
    val gameCount = gridItems.count { it is DualLibraryGridItem.Game }
    val boxArtStyle = LocalBoxArtStyle.current
    val nativeAspect = boxArtStyle.nativeAspectRatio
    val coverAspectRatio = boxArtStyle.aspectRatio

    val targetGridIndex = gridItems.indexOfFirst {
        it is DualLibraryGridItem.Game && it.gameIndex == focusedIndex
    }.coerceAtLeast(0)

    LaunchedEffect(targetGridIndex, nativeAspect) {
        if (gridItems.isNotEmpty() && targetGridIndex in gridItems.indices) {
            if (nativeAspect) {
                val viewportHeight = staggeredState.layoutInfo.viewportSize.height
                val itemHeight = staggeredState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetGridIndex }?.size?.height
                    ?: staggeredState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
                val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
                staggeredState.fastAnimateScrollToItem(targetGridIndex, -centerOffset)
            } else {
                val viewportHeight = gridState.layoutInfo.viewportSize.height
                val itemHeight = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
                val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
                gridState.fastAnimateScrollToItem(targetGridIndex, -centerOffset)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalArgosyTheme.current.surfaceBase)
                .surfaceBackdrop(BackdropRole.CONTENT)
        ) {
            Text(
                text = "$platformLabel ($gameCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Row(modifier = Modifier.weight(1f)) {
                if (nativeAspect) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(columns),
                        state = staggeredState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp
                    ) {
                        gridItems.forEachIndexed { _, gridItem ->
                            when (gridItem) {
                                is DualLibraryGridItem.Header -> item(
                                    key = "header-${gridItem.label}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    DualSectionDivider(label = gridItem.label)
                                }
                                is DualLibraryGridItem.Game -> item(
                                    key = gridItem.game.id,
                                    span = StaggeredGridItemSpan.SingleLane
                                ) {
                                    val coverPath = repairedCoverPaths[gridItem.game.id] ?: gridItem.game.coverPath
                                    val ratio = rememberCoverAspectRatio(coverPath, coverAspectRatio)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(ratio)
                                            .touchOnly { onGameTapped(gridItem.gameIndex) }
                                    ) {
                                        GameCard(
                                            game = gridItem.game,
                                            isFocused = gridItem.gameIndex == focusedIndex,
                                            modifier = Modifier.fillMaxSize(),
                                            focusScale = 1f,
                                            showPlatformBadge = false,
                                            onCoverLoadFailed = onCoverLoadFailed,
                                            coverPathOverride = repairedCoverPaths[gridItem.game.id],
                                            downloadIndicator = gridItem.game.downloadIndicator
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        count = gridItems.size,
                        key = { i ->
                            when (val item = gridItems[i]) {
                                is DualLibraryGridItem.Header -> "header-${item.label}"
                                is DualLibraryGridItem.Game -> item.game.id
                            }
                        },
                        span = { i ->
                            when (gridItems[i]) {
                                is DualLibraryGridItem.Header -> GridItemSpan(maxLineSpan)
                                is DualLibraryGridItem.Game -> GridItemSpan(1)
                            }
                        }
                    ) { index ->
                        when (val item = gridItems[index]) {
                            is DualLibraryGridItem.Header -> {
                                DualSectionDivider(label = item.label)
                            }
                            is DualLibraryGridItem.Game -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(coverAspectRatio)
                                        .touchOnly { onGameTapped(item.gameIndex) }
                                ) {
                                    GameCard(
                                        game = item.game,
                                        isFocused = item.gameIndex == focusedIndex,
                                        modifier = Modifier.fillMaxSize(),
                                        focusScale = 1f,
                                        showPlatformBadge = false,
                                        onCoverLoadFailed = onCoverLoadFailed,
                                        coverPathOverride = repairedCoverPaths[item.game.id],
                                        downloadIndicator = item.game.downloadIndicator
                                    )
                                }
                            }
                        }
                    }
                }
                }

                if (sectionLabels.size >= 3) {
                    AlphabetSidebar(
                        availableLetters = sectionLabels,
                        currentLetter = currentSectionLabel,
                        onLetterClick = onSectionClick,
                        modifier = Modifier.fillMaxHeight(),
                        topPadding = 0.dp,
                        bottomPadding = 0.dp
                    )
                }
            }
        }

        LetterJumpOverlay(
            letter = overlaySectionLabel,
            visible = showSectionOverlay
        )
    }
}

@Composable
private fun DualSectionDivider(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalArgosyTheme.current.focusAccent,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = LocalArgosyTheme.current.hairlineLow
        )
    }
}

@Composable
private fun LetterJumpOverlay(
    letter: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(100)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.displayLarge,
                fontSize = 120.sp,
                color = LocalArgosyTheme.current.focusAccent.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// --- Filter Overlay ---

@Composable
fun DualFilterOverlay(
    category: DualFilterCategory,
    options: List<DualFilterOption>,
    focusedIndex: Int,
    searchQuery: String = "",
    onOptionTapped: (Int) -> Unit,
    onCategoryTapped: (DualFilterCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (options.isNotEmpty() && focusedIndex in options.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val itemHeight = visibleItems.firstOrNull()?.size ?: 0
            if (itemHeight > 0 && viewportHeight > 0) {
                val centerOffset = (viewportHeight - itemHeight) / 2
                val paddingBuffer = (itemHeight * 0.2f).toInt()
                listState.animateScrollToItem(
                    index = focusedIndex,
                    scrollOffset = -centerOffset + paddingBuffer
                )
            } else {
                listState.animateScrollToItem(focusedIndex)
            }
        }
    }

    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surfaceBase)
            .surfaceBackdrop(BackdropRole.CONTENT)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surfaceRaised)
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            DualFilterCategory.entries.forEach { cat ->
                val isActive = cat == category
                TextButton(onClick = { onCategoryTapped(cat) }) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) theme.focusAccent else theme.textDim
                    )
                }
            }
        }

        if (category == DualFilterCategory.SEARCH) {
            DualSearchContent(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(options) { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == focusedIndex) {
                                    Modifier
                                        .background(
                                            theme.focusAccent.copy(alpha = 0.15f)
                                                .compositeOver(theme.surfaceRaised),
                                            RoundedCornerShape(Dimens.radiusSm)
                                        )
                                        .border(
                                            Dimens.borderThin,
                                            theme.focusAccent,
                                            RoundedCornerShape(Dimens.radiusSm)
                                        )
                                } else {
                                    Modifier.background(
                                        theme.surfaceRaised,
                                        RoundedCornerShape(Dimens.radiusSm)
                                    )
                                }
                            )
                            .touchOnly { onOptionTapped(index) }
                            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingMd),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textPrimary
                        )
                        if (option.isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(theme.focusAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DualSearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search games...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LocalArgosyTheme.current.focusAccent,
                    unfocusedBorderColor = LocalArgosyTheme.current.hairlineHigh
                )
            )

        if (query.isNotBlank()) {
            Text(
                text = "Filtering by: \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = LocalArgosyTheme.current.textDim
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

// --- Shared Composables ---

@Composable
private fun rememberCompanionCarouselItems(
    games: List<HomeGameUi>,
    hasMoreGames: Boolean,
    totalCount: Int,
    repairedCoverPaths: Map<Long, String>
): List<CarouselItem> = remember(games, hasMoreGames, totalCount, repairedCoverPaths) {
    buildList {
        games.forEach { game ->
            add(
                CarouselItem.Game(
                    key = game.id.toString(),
                    game = game,
                    downloadIndicator = game.downloadIndicator,
                    coverPathOverride = repairedCoverPaths[game.id]
                )
            )
        }
        if (hasMoreGames) {
            add(
                CarouselItem.ViewAll(
                    key = "view_all",
                    remainingCount = totalCount - games.size
                )
            )
        }
    }
}

fun HomeGameUi.toShowcaseState() = DualHomeShowcaseState(
    gameId = id,
    title = title,
    coverPath = coverPath,
    backgroundPath = backgroundPath,
    boxBackPath = boxBackPath,
    boxSpinePath = boxSpinePath,
    platformName = platformDisplayName,
    platformSlug = platformSlug,
    playTimeMinutes = playTimeMinutes,
    lastPlayedAt = lastPlayedAt ?: 0,
    status = status,
    communityRating = rating,
    userRating = userRating,
    userDifficulty = userDifficulty,
    description = description,
    developer = developer,
    releaseYear = releaseYear,
    titleId = titleId,
    isFavorite = isFavorite,
    isDownloaded = isPlayable
)

