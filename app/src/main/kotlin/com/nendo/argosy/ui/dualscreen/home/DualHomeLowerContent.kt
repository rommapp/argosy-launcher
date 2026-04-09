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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.util.touchOnly

@Composable
fun DualHomeLowerContent(
    viewModel: DualHomeViewModel,
    homeApps: List<String>,
    onGameSelected: (Long) -> Unit,
    onAppClick: (String) -> Unit,
    onCollectionsClick: () -> Unit,
    onLibraryToggle: () -> Unit,
    onViewAllClick: () -> Unit,
    onCollectionTapped: (Int) -> Unit,
    onGridGameTapped: (Int) -> Unit,
    onLetterClick: (String) -> Unit,
    onFilterOptionTapped: (Int) -> Unit,
    onFilterCategoryTapped: (DualFilterCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onDimTapped: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val forwardingMode by viewModel.forwardingMode.collectAsState()

    val isSearchActive = uiState.viewMode == DualHomeViewMode.LIBRARY_GRID
        && uiState.showFilterOverlay
        && uiState.filterCategory == DualFilterCategory.SEARCH

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (!isSearchActive) Modifier.focusProperties { canFocus = false } else Modifier)
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
                    repairedCoverPaths = uiState.repairedCoverPaths,
                    onGameTapped = { index -> viewModel.setSelectedIndex(index) },
                    onGameSelected = onGameSelected,
                    onCoverLoadFailed = { gameId, path -> viewModel.repairCoverImage(gameId, path) },
                    onAppClick = onAppClick,
                    onCollectionsClick = onCollectionsClick,
                    onLibraryToggle = onLibraryToggle,
                    onViewAllClick = onViewAllClick,
                    onOpenDrawer = onOpenDrawer
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
                        onCoverLoadFailed = { gameId, path -> viewModel.repairCoverImage(gameId, path) },
                        onSectionClick = onLetterClick
                    )
                }
            }
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
