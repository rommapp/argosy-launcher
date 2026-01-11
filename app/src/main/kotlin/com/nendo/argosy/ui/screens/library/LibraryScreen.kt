package com.nendo.argosy.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.zIndex
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.ui.components.AddToCollectionModal
import com.nendo.argosy.ui.components.CollectionItem
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.DiscPickerModal
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.input.DiscPickerInputHandler
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.SourceBadge
import com.nendo.argosy.ui.screens.home.HomeGameUi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs

@Composable
fun LibraryScreen(
    isDefaultView: Boolean,
    onGameSelect: (Long) -> Unit,
    onNavigateToDefault: () -> Unit,
    onDrawerToggle: () -> Unit,
    initialPlatformId: Long? = null,
    initialSource: String? = null,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = uiState.focusedIndex)
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(initialPlatformId) {
        if (initialPlatformId != null) {
            viewModel.setInitialPlatform(initialPlatformId)
        }
    }

    LaunchedEffect(initialSource) {
        if (initialSource != null) {
            val sourceFilter = SourceFilter.entries.find { it.name == initialSource }
            if (sourceFilter != null) {
                viewModel.setInitialSourceFilter(sourceFilter)
            }
        }
    }

    LaunchedEffect(uiState.currentPlatformIndex) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(uiState.games.size) {
        if (uiState.games.isNotEmpty() && uiState.focusedIndex > 0) {
            gridState.scrollToItem(uiState.focusedIndex)
        }
    }

    LaunchedEffect(uiState.focusedIndex, uiState.lastFocusMove) {
        if (uiState.games.isEmpty()) return@LaunchedEffect
        if (uiState.lastFocusMove == FocusMove.LEFT || uiState.lastFocusMove == FocusMove.RIGHT) return@LaunchedEffect

        val layoutInfo = gridState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@LaunchedEffect

        val firstItem = visibleItems.first()
        val rowHeight = firstItem.size.height
        val spacing = visibleItems.find { it.offset.y != firstItem.offset.y }
            ?.let { it.offset.y - firstItem.offset.y - rowHeight } ?: 0
        val rowStep = rowHeight + spacing
        val scrollAnim = tween<Float>(durationMillis = 60)

        val focusedItem = visibleItems.find { it.index == uiState.focusedIndex }
        if (focusedItem != null) {
            val itemTop = focusedItem.offset.y
            val itemBottom = itemTop + rowHeight
            val viewportHeight = layoutInfo.viewportSize.height
            val paddingBuffer = (rowHeight * Motion.scrollPaddingPercent).toInt()

            when {
                itemBottom + paddingBuffer > viewportHeight -> {
                    isProgrammaticScroll = true
                    gridState.animateScrollBy(rowStep.toFloat(), scrollAnim)
                    isProgrammaticScroll = false
                }
                itemTop - paddingBuffer < 0 -> {
                    isProgrammaticScroll = true
                    gridState.animateScrollBy(-rowStep.toFloat(), scrollAnim)
                    isProgrammaticScroll = false
                }
            }
        } else {
            isProgrammaticScroll = true
            gridState.scrollToItem(uiState.focusedIndex)
            isProgrammaticScroll = false
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling && !isProgrammaticScroll) {
                    viewModel.enterTouchMode()
                }
            }
    }


    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.LaunchGame -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryScreen", "Failed to start activity", e)
                    }
                }
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onGameSelect, onDrawerToggle, isDefaultView) {
        viewModel.createInputHandler(
            isDefaultView = isDefaultView,
            onGameSelect = onGameSelect,
            onNavigateToDefault = onNavigateToDefault,
            onDrawerToggle = onDrawerToggle
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_LIBRARY)
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_LIBRARY)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val showAnyOverlay = uiState.showFilterMenu || uiState.showQuickMenu || uiState.showAddToCollectionModal || uiState.syncOverlayState != null || uiState.discPickerState != null
    val modalBlur by animateDpAsState(
        targetValue = if (showAnyOverlay) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    val swipeThreshold = with(LocalDensity.current) { 50.dp.toPx() }
    val edgeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val currentOnDrawerToggle by rememberUpdatedState(onDrawerToggle)

    val swipeGestureModifier = Modifier.pointerInput(Unit) {
        var totalDragX = 0f
        var totalDragY = 0f
        var startX = 0f
        detectDragGestures(
            onDragStart = { offset ->
                totalDragX = 0f
                totalDragY = 0f
                startX = offset.x
            },
            onDragEnd = {
                when {
                    startX < edgeThreshold && totalDragX > swipeThreshold -> currentOnDrawerToggle()
                    totalDragX > swipeThreshold && abs(totalDragX) > abs(totalDragY) -> viewModel.previousPlatform()
                    totalDragX < -swipeThreshold && abs(totalDragX) > abs(totalDragY) -> viewModel.nextPlatform()
                }
            },
            onDrag = { _, dragAmount ->
                totalDragX += dragAmount.x
                totalDragY += dragAmount.y
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().blur(modalBlur)) {
            LibraryHeader(
                platformName = uiState.currentPlatform?.displayName ?: "All Platforms",
                gameCount = uiState.games.size,
                onPreviousPlatform = { viewModel.previousPlatform() },
                onNextPlatform = { viewModel.nextPlatform() }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { clip = false }
                    .then(swipeGestureModifier)
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(Dimens.spacingXxl))
                        }
                    }
                    uiState.games.isEmpty() -> {
                        EmptyLibrary(
                            platformName = uiState.currentPlatform?.name
                        )
                    }
                    else -> {
                        key(uiState.currentPlatformIndex) {
                            val gridSpacing = uiState.gridSpacingDp.dp
                            val columnsCount = uiState.columnsCount
                            val aspectRatio = if (uiState.gridDensity == GridDensity.COMPACT) 2f / 3f else 3f / 4f

                            val configuration = LocalConfiguration.current
                            LaunchedEffect(configuration.screenWidthDp) {
                                viewModel.updateScreenWidth(configuration.screenWidthDp)
                            }

                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val totalSpacing = gridSpacing * (columnsCount + 1)
                                val columnWidth = (maxWidth - totalSpacing) / columnsCount
                                val cardHeight = columnWidth / aspectRatio

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columnsCount),
                                    state = gridState,
                                    contentPadding = PaddingValues(gridSpacing),
                                    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                                    verticalArrangement = Arrangement.spacedBy(gridSpacing),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(
                                        items = uiState.games,
                                        key = { _, game -> game.id }
                                    ) { index, game ->
                                        val isFocused = index == uiState.focusedIndex
                                        LibraryGameCard(
                                            game = game,
                                            isFocused = isFocused,
                                            showFocus = !uiState.isTouchMode || uiState.hasSelectedGame,
                                            cardHeight = cardHeight,
                                            showPlatformBadge = uiState.currentPlatformIndex < 0,
                                            coverPathOverride = uiState.repairedCoverPaths[game.id],
                                            onCoverLoadFailed = viewModel::repairCoverImage,
                                            onClick = { viewModel.handleItemTap(index, onGameSelect) },
                                            onLongClick = { viewModel.handleItemLongPress(index) },
                                            modifier = Modifier.zIndex(if (isFocused) 1f else 0f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LibraryFooter(
                focusedGame = uiState.focusedGame,
                onHintClick = { button ->
                    when (button) {
                        InputButton.SOUTH -> uiState.focusedGame?.let { onGameSelect(it.id) }
                        InputButton.NORTH -> uiState.focusedGame?.let { viewModel.toggleFavorite(it.id) }
                        InputButton.WEST -> viewModel.toggleFilterMenu()
                        InputButton.SELECT -> viewModel.toggleQuickMenu()
                        else -> {}
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = uiState.showFilterMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FilterMenuOverlay(
                uiState = uiState,
                onDismiss = { viewModel.toggleFilterMenu() },
                onCategorySelect = { viewModel.setFilterCategory(it) },
                onOptionSelect = { index ->
                    viewModel.moveFilterOptionFocus(index - uiState.filterOptionIndex)
                    viewModel.confirmFilterSelection()
                },
                onSearchQueryChange = { viewModel.updateSearchQuery(it) }
            )
        }

        AnimatedVisibility(
            visible = uiState.showQuickMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.focusedGame?.let { game ->
                QuickMenuOverlay(
                    game = game,
                    focusIndex = uiState.quickMenuFocusIndex,
                    onDismiss = { viewModel.toggleQuickMenu() },
                    onPrimaryAction = {
                        viewModel.toggleQuickMenu()
                        when {
                            game.needsInstall -> viewModel.installApk(game.id)
                            game.isDownloaded -> viewModel.launchGame(game.id)
                            else -> viewModel.downloadGame(game.id)
                        }
                    },
                    onFavorite = { viewModel.toggleFavorite(game.id) },
                    onDetails = {
                        viewModel.toggleQuickMenu()
                        onGameSelect(game.id)
                    },
                    onAddToCollection = {
                        viewModel.toggleQuickMenu()
                        viewModel.showAddToCollectionModal(game.id)
                    },
                    onRefresh = { viewModel.refreshGameData(game.id) },
                    onResyncPlatform = {
                        viewModel.toggleQuickMenu()
                        viewModel.syncCurrentPlatform()
                    },
                    onDelete = {
                        viewModel.toggleQuickMenu()
                        viewModel.deleteLocalFile(game.id)
                    },
                    onHide = {
                        viewModel.toggleQuickMenu()
                        viewModel.hideGame(game.id)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.showAddToCollectionModal,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AddToCollectionModal(
                collections = uiState.collections.map { collection ->
                    CollectionItem(
                        id = collection.id,
                        name = collection.name,
                        isInCollection = collection.isInCollection
                    )
                },
                focusIndex = uiState.collectionModalFocusIndex,
                onToggleCollection = { viewModel.toggleGameInCollection(it) },
                onCreate = { viewModel.showCreateCollectionFromModal() },
                onDismiss = { viewModel.dismissAddToCollectionModal() }
            )
        }

        if (uiState.showCreateCollectionDialog) {
            CreateCollectionDialog(
                onDismiss = { viewModel.hideCreateCollectionDialog() },
                onCreate = { name -> viewModel.createCollectionFromModal(name) }
            )
        }

        SyncOverlay(
            syncProgress = uiState.syncOverlayState?.syncProgress,
            gameTitle = uiState.syncOverlayState?.gameTitle
        )

        uiState.discPickerState?.let { pickerState ->
            DiscPickerModal(
                discs = pickerState.discs,
                focusIndex = uiState.discPickerFocusIndex,
                onSelectDisc = viewModel::selectDisc,
                onDismiss = viewModel::dismissDiscPicker
            )
        }

        val discPickerInputHandler = remember(viewModel) {
            DiscPickerInputHandler(
                getDiscs = { uiState.discPickerState?.discs ?: emptyList() },
                getFocusIndex = { uiState.discPickerFocusIndex },
                onFocusChange = { viewModel.setDiscPickerFocusIndex(it) },
                onSelect = { viewModel.selectDisc(it) },
                onDismiss = { viewModel.dismissDiscPicker() }
            )
        }

        LaunchedEffect(uiState.discPickerState) {
            if (uiState.discPickerState != null) {
                viewModel.setDiscPickerFocusIndex(0)
                inputDispatcher.pushModal(discPickerInputHandler)
            }
        }

        DisposableEffect(uiState.discPickerState) {
            onDispose {
                if (uiState.discPickerState != null) {
                    inputDispatcher.popModal()
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    platformName: String,
    gameCount: Int,
    onPreviousPlatform: () -> Unit = {},
    onNextPlatform: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIBRARY",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "$gameCount games",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clickable(
                        onClick = onPreviousPlatform,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .padding(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = InputIcons.BumperLeft,
                    contentDescription = "Previous platform",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(Dimens.spacingMd))

            Text(
                text = platformName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(Dimens.spacingMd))

            Row(
                modifier = Modifier
                    .clickable(
                        onClick = onNextPlatform,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .padding(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = InputIcons.BumperRight,
                    contentDescription = "Next platform",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGameCard(
    game: LibraryGameUi,
    isFocused: Boolean,
    showFocus: Boolean,
    cardHeight: Dp,
    showPlatformBadge: Boolean = true,
    coverPathOverride: String? = null,
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val effectiveFocused = isFocused && showFocus
    GameCard(
        game = HomeGameUi(
            id = game.id,
            title = game.title,
            platformId = game.platformId,
            platformSlug = game.platformSlug,
            platformDisplayName = game.platformDisplayName,
            coverPath = game.coverPath,
            backgroundPath = null,
            developer = null,
            releaseYear = null,
            genre = null,
            isFavorite = game.isFavorite,
            isDownloaded = game.isDownloaded
        ),
        isFocused = effectiveFocused,
        showPlatformBadge = showPlatformBadge,
        coverPathOverride = coverPathOverride,
        onCoverLoadFailed = onCoverLoadFailed,
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    )
}

@Composable
private fun LibraryFooter(
    focusedGame: LibraryGameUi?,
    onHintClick: ((InputButton) -> Unit)? = null
) {
    FooterBar(
        hints = listOf(
            InputButton.SOUTH to "Details",
            InputButton.NORTH to if (focusedGame?.isFavorite == true) "Unfavorite" else "Favorite",
            InputButton.WEST to "Filter",
            InputButton.SELECT to "Quick Menu"
        ),
        onHintClick = onHintClick
    )
}

@Composable
private fun EmptyLibrary(platformName: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (platformName != null) "No $platformName games found" else "No games yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = "Sync your library from Rom Manager in Settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun FilterMenuOverlay(
    uiState: LibraryUiState,
    onDismiss: () -> Unit,
    onCategorySelect: (FilterCategory) -> Unit,
    onOptionSelect: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val options = uiState.currentCategoryOptions
    val categories = uiState.availableCategories
    val isMultiSelect = uiState.isCurrentCategoryMultiSelect
    val selectedOptions = uiState.selectedOptionsInCurrentCategory
    val isSearchCategory = uiState.currentFilterCategory == FilterCategory.SEARCH
    val searchQuery = uiState.activeFilters.searchQuery
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.filterOptionIndex) {
        if (!isSearchCategory && options.isNotEmpty() && uiState.filterOptionIndex in options.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val itemHeight = visibleItems.firstOrNull()?.size ?: 0

            if (itemHeight > 0 && viewportHeight > 0) {
                val centerOffset = (viewportHeight - itemHeight) / 2
                val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
                listState.animateScrollToItem(
                    index = uiState.filterOptionIndex,
                    scrollOffset = -centerOffset + paddingBuffer
                )
            } else {
                listState.animateScrollToItem(uiState.filterOptionIndex)
            }
        }
    }

    LaunchedEffect(isSearchCategory) {
        if (isSearchCategory && uiState.screenWidthDp > 900) {
            focusRequester.requestFocus()
        }
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(450.dp)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false, onClick = {})
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = "FILTER GAMES",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            if (uiState.activeFilters.activeCount > 0) {
                Text(
                    text = "Active: ${uiState.activeFilters.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                categories.forEach { category ->
                    val isCurrent = category == uiState.currentFilterCategory
                    val hasActiveFilters = when (category) {
                        FilterCategory.SEARCH -> uiState.activeFilters.searchQuery.isNotEmpty()
                        FilterCategory.SOURCE -> uiState.activeFilters.source != SourceFilter.ALL
                        FilterCategory.GENRE -> uiState.activeFilters.genres.isNotEmpty()
                        FilterCategory.PLAYERS -> uiState.activeFilters.players.isNotEmpty()
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.radiusMd))
                            .clickable(
                                onClick = { onCategorySelect(category) },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .then(
                                if (hasActiveFilters && !isCurrent) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(Dimens.radiusMd)
                                    )
                                } else Modifier
                            )
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isSearchCategory) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.radiusMd))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Type to search...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (options.isNotEmpty()) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(options) { index, recentQuery ->
                        val isFocused = index == uiState.filterOptionIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimens.radiusSm))
                                .then(
                                    if (isFocused) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                    else Modifier
                                )
                                .clickable { onOptionSelect(index) }
                                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = recentQuery,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(options) { index, option ->
                        val isFocused = index == uiState.filterOptionIndex
                        val isSelected = if (isMultiSelect) {
                            option in selectedOptions
                        } else {
                            index == uiState.selectedSourceIndex
                        }
                        FilterOptionItem(
                            label = option,
                            isFocused = isFocused,
                            isSelected = isSelected,
                            onClick = { onOptionSelect(index) }
                        )
                    }
                }
            }

            FooterBar(
                hints = if (isSearchCategory) {
                    listOf(
                        InputButton.WEST to "Clear",
                        InputButton.EAST to "Close"
                    )
                } else {
                    listOf(
                        InputButton.WEST to "Reset",
                        InputButton.SOUTH to if (isMultiSelect) "Toggle" else "Select",
                        InputButton.EAST to "Close"
                    )
                }
            )
        }
    }
}

@Composable
private fun FilterOptionItem(
    label: String,
    isFocused: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .background(
                when {
                    isFocused -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun QuickMenuOverlay(
    game: LibraryGameUi,
    focusIndex: Int,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onAddToCollection: () -> Unit,
    onRefresh: () -> Unit,
    onResyncPlatform: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit
) {
    val canRefresh = game.isRommGame || game.isAndroidApp
    var currentIndex = 0
    val playIdx = currentIndex++
    val favoriteIdx = currentIndex++
    val detailsIdx = currentIndex++
    val addToCollectionIdx = currentIndex++
    val refreshIdx = if (canRefresh) currentIndex++ else -1
    val resyncPlatformIdx = currentIndex++
    val deleteIdx = if (game.isDownloaded || game.needsInstall) currentIndex++ else -1
    val hideIdx = currentIndex

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val primaryIcon = when {
        game.needsInstall -> Icons.Default.InstallMobile
        game.isDownloaded -> Icons.Default.PlayArrow
        else -> Icons.Default.Download
    }
    val primaryLabel = when {
        game.needsInstall -> "Install"
        game.isDownloaded -> "Play"
        else -> "Download"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(12.dp)
                )
                .clickable(enabled = false, onClick = {})
                .padding(24.dp)
                .width(350.dp)
        ) {
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            QuickMenuItem(
                icon = primaryIcon,
                label = primaryLabel,
                isFocused = focusIndex == playIdx,
                onClick = onPrimaryAction
            )
            QuickMenuItem(
                icon = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (game.isFavorite) "Unfavorite" else "Favorite",
                isFocused = focusIndex == favoriteIdx,
                onClick = onFavorite
            )
            QuickMenuItem(
                icon = Icons.Default.Info,
                label = "Details",
                isFocused = focusIndex == detailsIdx,
                onClick = onDetails
            )
            QuickMenuItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Add to Collection",
                isFocused = focusIndex == addToCollectionIdx,
                onClick = onAddToCollection
            )
            if (canRefresh) {
                QuickMenuItem(
                    icon = Icons.Default.Refresh,
                    label = "Refresh Data",
                    isFocused = focusIndex == refreshIdx,
                    onClick = onRefresh
                )
            }
            QuickMenuItem(
                icon = Icons.Default.Refresh,
                label = "Resync Platform",
                isFocused = focusIndex == resyncPlatformIdx,
                onClick = onResyncPlatform
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (game.isDownloaded || game.needsInstall) {
                QuickMenuItem(
                    icon = Icons.Default.DeleteOutline,
                    label = if (game.isAndroidApp && game.isDownloaded) "Uninstall" else "Delete Download",
                    isFocused = focusIndex == deleteIdx,
                    isDangerous = true,
                    onClick = onDelete
                )
            }
            QuickMenuItem(
                label = if (game.isHidden) "Show" else "Hide",
                isFocused = focusIndex == hideIdx,
                isDangerous = !game.isHidden,
                onClick = onHide
            )
        }
    }
}

@Composable
private fun QuickMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    value: String? = null,
    isFocused: Boolean = false,
    isDangerous: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.onErrorContainer
        isDangerous -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val backgroundColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.errorContainer
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = "[$value]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

