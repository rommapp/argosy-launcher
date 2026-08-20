/**
 * DUAL-SCREEN COMPONENT - Lower display home content.
 * Runs in :companion process (SecondaryHomeActivity).
 * Footer hints are on upper screen only.
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.util.touchOnly

@Composable
fun DualHomeLowerContent(
    viewModel: DualHomeViewModel,
    homeApps: List<String>,
    onGameSelected: (Long) -> Unit,
    onAppClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    onCollectionTapped: (Int) -> Unit,
    onGridGameTapped: (Int) -> Unit,
    onLetterClick: (String) -> Unit,
    onFilterOptionTapped: (Int) -> Unit,
    onFilterCategoryTapped: (DualFilterCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onDimTapped: () -> Unit = {},
    onCustomGridActivate: () -> Unit = {},
    mediaToggle: com.nendo.argosy.hardware.CompanionMediaToggle? = null,
    onMediaToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val forwardingMode by viewModel.forwardingMode.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (uiState.viewMode) {
            DualHomeViewMode.CAROUSEL -> {
                DualHomeLowerScreen(
                    games = uiState.games,
                    selectedIndex = uiState.selectedIndex,
                    platformName = uiState.platformName,
                    totalCount = uiState.totalCount,
                    hasMoreGames = uiState.hasMoreGames,
                    isViewAllFocused = uiState.isViewAllFocused,
                    homeApps = homeApps,
                    appBarFocused = uiState.focusZone == DualHomeFocusZone.APP_BAR,
                    appBarIndex = uiState.appBarIndex,
                    viewMode = uiState.viewMode,
                    sectionLabels = uiState.sections.map { it.shortTitle },
                    currentSectionIndex = uiState.currentSectionIndex,
                    onPreviousSection = { viewModel.previousSection() },
                    onNextSection = { viewModel.nextSection() },
                    onSelectSection = { index -> viewModel.setSectionIndex(index) },
                    repairedCoverPaths = uiState.repairedCoverPaths,
                    onGameTapped = { index -> viewModel.selectByTouch(index) },
                    onGameSelected = onGameSelected,
                    onCoverLoadFailed = { gameId, path -> viewModel.repairCoverImage(gameId, path) },
                    onAppClick = onAppClick,
                    onViewAllClick = onViewAllClick,
                    onOpenDrawer = onOpenDrawer,
                    mediaToggle = mediaToggle,
                    onMediaToggle = onMediaToggle,
                    carouselConfig = uiState.carouselConfig,
                    autoGridConfig = uiState.autoGridConfig,
                    layoutKind = uiState.layoutKind,
                    customGridState = uiState.customGrid,
                    customGridContentFor = { tile -> uiState.tileContentFor(tile) },
                    customGridConfig = uiState.customGridConfig,
                    onCustomGridCellTap = { cell ->
                        val grid = uiState.customGrid
                        val onFocused = grid.tileAt(cell)
                            ?.let { it == grid.focusedTile }
                            ?: (cell == grid.cell)
                        when {
                            grid.isEditing -> viewModel.moveEditingTileTo(cell)
                            onFocused -> onCustomGridActivate()
                            else -> viewModel.setCustomGridCell(cell)
                        }
                    },
                    onCustomGridSwipePage = { delta -> viewModel.turnCustomGridPage(delta) },
                    onCustomGridTileDrag = { cell -> viewModel.moveEditingTileTo(cell) },
                    onCustomGridTileResize = { cell -> viewModel.resizeEditingTileTo(cell) },
                    onCustomGridToggleEditMode = { viewModel.toggleTileEditMode() },
                    onCustomGridCommitEdit = { viewModel.commitTileEdit() },
                    onCustomGridShape = { columns, rows ->
                        viewModel.setCustomGridShape(columns, rows)
                    },
                    onCustomGridAddPage = { viewModel.confirmAddPage() },
                    onCustomGridTileLongPress = { cell ->
                        viewModel.setCustomGridCell(cell)
                        viewModel.openTileMenu()
                    },
                    isPlatformSection = when (uiState.currentSection) {
                        is DualHomeSection.Platform,
                        DualHomeSection.Android,
                        DualHomeSection.Steam -> true
                        else -> false
                    }
                )
            }
            DualHomeViewMode.COLLECTIONS -> {
                DualHomeCollectionList(
                    items = uiState.collectionItems,
                    selectedIndex = uiState.selectedCollectionIndex,
                    onCollectionTapped = onCollectionTapped
                )
            }
            DualHomeViewMode.COLLECTION_GAMES -> {
                DualHomeLibraryGrid(
                    gridItems = uiState.collectionGames.mapIndexed { i, game ->
                        DualLibraryGridItem.Game(game, gameIndex = i)
                    },
                    focusedIndex = uiState.collectionGamesFocusedIndex,
                    columns = uiState.libraryColumns,
                    sectionLabels = emptyList(),
                    currentSectionLabel = "",
                    repairedCoverPaths = uiState.repairedCoverPaths,
                    onGameTapped = onGridGameTapped,
                    onCoverLoadFailed = { gameId, path -> viewModel.repairCoverImage(gameId, path) },
                    onSectionClick = {}
                )
            }
            DualHomeViewMode.LIBRARY_GRID -> {
                if (uiState.showFilterOverlay) {
                    DualFilterOverlay(
                        category = uiState.filterCategory,
                        options = uiState.filterOptions,
                        focusedIndex = uiState.filterFocusedIndex,
                        searchQuery = uiState.activeFilters.searchQuery,
                        onOptionTapped = onFilterOptionTapped,
                        onCategoryTapped = onFilterCategoryTapped,
                        onSearchQueryChange = onSearchQueryChange
                    )
                } else {
                    DualHomeLibraryGrid(
                        gridItems = uiState.libraryGridItems,
                        focusedIndex = uiState.libraryFocusedIndex,
                        columns = uiState.libraryColumns,
                        sectionLabels = uiState.sectionLabels,
                        currentSectionLabel = uiState.currentSectionLabel,
                        platformLabel = uiState.libraryPlatformLabel,
                        showSectionOverlay = uiState.showSectionOverlay,
                        overlaySectionLabel = uiState.overlaySectionLabel,
                        repairedCoverPaths = uiState.repairedCoverPaths,
                        onGameTapped = onGridGameTapped,
                        onGameLongPressed = { index ->
                            viewModel.setLibraryFocusIndex(index)
                            viewModel.openLibraryGameMenu()
                        },
                        onCoverLoadFailed = { gameId, path -> viewModel.repairCoverImage(gameId, path) },
                        onSectionClick = onLetterClick
                    )
                }
            }
        }

        val pendingTileAdd = uiState.customGrid.pendingAdd
        if (pendingTileAdd != null) {
            com.nendo.argosy.ui.primitives.ArgosyConfirmModal(
                title = "Add to home grid?",
                message = "${pendingTileAdd.title} finished downloading.",
                confirmLabel = "Add",
                cancelLabel = "Not now",
                focusedIndex = uiState.customGrid.pendingAddFocusIndex,
                onConfirm = viewModel::confirmPendingTileAdd,
                onDismiss = viewModel::dismissPendingTileAdd
            )
        }

        if (uiState.collectionPickerGameId != null) {
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                title = "Add to Collection",
                entries = uiState.collectionPickerEntries.map { entry ->
                    if (entry.isMember) "${entry.name}  -  added" else entry.name
                },
                focusIndex = uiState.collectionPickerFocusIndex,
                onSelect = { index ->
                    viewModel.moveCollectionPickerFocus(index - uiState.collectionPickerFocusIndex)
                    viewModel.confirmCollectionPicker()
                },
                onDismiss = viewModel::closeCollectionPicker
            )
        }

        if (uiState.showLibraryMenu) {
            val libraryGame = viewModel.focusedLibraryGame()
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                title = libraryGame?.title.orEmpty(),
                entries = viewModel.libraryMenuActions().map { it.label },
                focusIndex = uiState.libraryMenuFocusIndex,
                onSelect = { index ->
                    viewModel.moveLibraryMenuFocus(index - uiState.libraryMenuFocusIndex)
                    val action = viewModel.confirmLibraryMenu()
                    libraryGame?.let { game ->
                        viewModel.applyLibraryMenuAction(action, game, onGameSelected)
                    }
                },
                onDismiss = viewModel::closeLibraryGameMenu
            )
        }

        if (uiState.showTileMenu) {
            val tile = viewModel.focusedTile()
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                title = tile?.let { uiState.tileContentFor(it)?.label }.orEmpty(),
                entries = uiState.customGrid.menuActions.map { it.label },
                focusIndex = uiState.tileMenuFocusIndex,
                onSelect = { index ->
                    viewModel.moveTileMenuFocus(index - uiState.tileMenuFocusIndex)
                    viewModel.confirmTileMenu()
                },
                onDismiss = viewModel::closeTileMenu,
                dangerFromIndex = uiState.customGrid.menuDangerFromIndex
            )
        }

        if (uiState.showTilePicker) {
            com.nendo.argosy.ui.components.HomeTilePickerModal(
                entries = uiState.tilePickerEntries,
                query = uiState.tilePickerQuery,
                focusIndex = uiState.tilePickerFocusIndex,
                onSelect = { entry -> viewModel.selectTilePickerEntry(entry) },
                onDismiss = viewModel::closeTilePicker,
                searchActive = uiState.customGrid.pickerSearchActive,
                onQueryChange = viewModel::setTilePickerQuery,
                category = uiState.customGrid.pickerCategory,
                categories = uiState.customGrid.pickerCategories,
                onSelectCategory = { viewModel.setTilePickerCategory(it) },
                canDeletePage = uiState.customGrid.canDeletePage,
                onDeletePage = viewModel::deleteCustomGridPage
            )
        }

        uiState.customGrid.pageChooser?.let { chooser ->
            com.nendo.argosy.ui.components.PageChooserModal(
                state = chooser,
                onSelect = { index ->
                    viewModel.movePageChooserFocus(index - chooser.focusIndex)
                    viewModel.confirmPageChooser()
                },
                onQueryChange = viewModel::setPageChooserQuery,
                onDismiss = viewModel::closePageChooser
            )
        }

        if (forwardingMode == ForwardingMode.OVERLAY) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .touchOnly { onDimTapped() }
            )
        }
    }
}
