/**
 * DUAL-SCREEN COMPONENT - Lower display game carousel.
 * Runs in :companion process (SecondaryHomeActivity).
 * Communicates selection to upper display via broadcasts.
 * Uses custom InputHandler focus (selectedIndex from ViewModel).
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.hardware.CompanionAppBar
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.AlphabetSidebar
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.util.touchOnly
import kotlin.math.abs

private val CARD_WIDTH = 100.dp
private val FOCUSED_CARD_WIDTH = 140.dp
private val CARD_SPACING = 12.dp

private val GRID_CARD_WIDTH = 100.dp

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
    repairedCoverPaths: Map<Long, String> = emptyMap(),
    onGameTapped: (Int) -> Unit,
    onGameSelected: (Long) -> Unit,
    onCoverLoadFailed: (Long, String) -> Unit = { _, _ -> },
    onAppClick: (String) -> Unit,
    onCollectionsClick: () -> Unit,
    onLibraryToggle: () -> Unit,
    onViewAllClick: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val centerPadding = (screenWidthDp - FOCUSED_CARD_WIDTH) / 2
    val coverAspectRatio = LocalBoxArtStyle.current.aspectRatio
    val regularCardHeight = CARD_WIDTH / coverAspectRatio
    val focusedCardHeight = FOCUSED_CARD_WIDTH / coverAspectRatio

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentGames by rememberUpdatedState(games)
    val currentOnGameTapped by rememberUpdatedState(onGameTapped)
    var skipNextProgrammatic by remember { mutableStateOf(false) }
    var isUserScroll by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, games) {
        if (games.isNotEmpty()) {
            if (selectedIndex in games.indices) {
                com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.onGameSelected(games[selectedIndex].toShowcaseState())
            }
            if (!skipNextProgrammatic) {
                if (selectedIndex in games.indices) {
                    listState.animateScrollToItem(
                        index = selectedIndex,
                        scrollOffset = 0
                    )
                } else if (hasMoreGames && selectedIndex == games.size) {
                    listState.animateScrollToItem(
                        index = games.size,
                        scrollOffset = 0
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (viewMode == DualHomeViewMode.CAROUSEL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCollectionsClick) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Collections",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                IconButton(onClick = onLibraryToggle) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = "Library Grid",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "$platformName ($totalCount)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(focusedCardHeight + 16.dp),
            contentAlignment = Alignment.Center
        ) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = centerPadding),
                horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
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
            ) {
                itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                    val isSelected = index == selectedIndex
                    val cardWidth = if (isSelected) FOCUSED_CARD_WIDTH else CARD_WIDTH
                    val cardHeight = if (isSelected) focusedCardHeight else regularCardHeight
                    Box(
                        modifier = Modifier
                            .size(width = cardWidth, height = cardHeight)
                            .touchOnly {
                                onGameTapped(index)
                                onGameSelected(game.id)
                            }
                    ) {
                        GameCard(
                            game = game,
                            isFocused = isSelected && !appBarFocused,
                            modifier = Modifier.fillMaxSize(),
                            focusScale = 1f,
                            alphaOverride = if (isSelected) 1f else 0.5f,
                            showPlatformBadge = true,
                            onCoverLoadFailed = onCoverLoadFailed,
                            coverPathOverride = repairedCoverPaths[game.id],
                            downloadIndicator = game.downloadIndicator
                        )
                    }
                }
                if (hasMoreGames) {
                    item(key = "view_all") {
                        ViewAllCard(
                            remainingCount = totalCount - games.size,
                            isFocused = isViewAllFocused,
                            onClick = onViewAllClick
                        )
                    }
                }
            }
        }

        PositionIndicator(
            totalCount = games.size,
            currentIndex = if (isViewAllFocused) -1 else selectedIndex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

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
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items) { index, item ->
            when (item) {
                is DualCollectionListItem.Header -> {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            top = if (index > 0) 16.dp else 0.dp,
                            bottom = 4.dp
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        )
                } else {
                    Modifier.background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp)
                    )
                }
            )
            .touchOnly(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.platformSummary.isNotBlank()) {
                Text(
                    text = item.platformSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "${item.gameCount} games",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when {
            coverPaths.isEmpty() -> {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val gameCount = gridItems.count { it is DualLibraryGridItem.Game }
    val gridCardHeight = GRID_CARD_WIDTH / LocalBoxArtStyle.current.aspectRatio

    val targetGridIndex = gridItems.indexOfFirst {
        it is DualLibraryGridItem.Game && it.gameIndex == focusedIndex
    }.coerceAtLeast(0)

    LaunchedEffect(targetGridIndex) {
        if (gridItems.isNotEmpty() && targetGridIndex in gridItems.indices) {
            val viewportHeight = gridState.layoutInfo.viewportSize.height
            val itemHeight = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            gridState.animateScrollToItem(targetGridIndex, -centerOffset)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                text = "$platformLabel ($gameCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Row(modifier = Modifier.weight(1f)) {
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
                                        .size(width = GRID_CARD_WIDTH, height = gridCardHeight)
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
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DualFilterCategory.entries.forEach { cat ->
                val isActive = cat == category
                TextButton(onClick = { onCategoryTapped(cat) }) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
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
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(6.dp)
                                        )
                                } else {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(6.dp)
                                    )
                                }
                            )
                            .touchOnly { onOptionTapped(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (option.isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
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
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

        if (query.isNotBlank()) {
            Text(
                text = "Filtering by: \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

// --- Shared Composables ---

@Composable
private fun ViewAllCard(
    remainingCount: Int,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardHeight = CARD_WIDTH / LocalBoxArtStyle.current.aspectRatio
    Box(
        modifier = modifier
            .size(width = CARD_WIDTH, height = cardHeight)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .touchOnly(onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.GridView,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "+$remainingCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PositionIndicator(
    totalCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(totalCount) { index ->
            val isActive = index == (currentIndex % totalCount)
            Box(
                modifier = Modifier
                    .size(if (isActive) 10.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
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

