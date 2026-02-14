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
import androidx.compose.ui.platform.LocalContext
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
    onDimTapped: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isForwarding by viewModel.isForwardingToDrawer.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
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
                    onGameTapped = { index -> viewModel.setSelectedIndex(index) },
                    onGameSelected = onGameSelected,
                    onAppClick = onAppClick,
                    onCollectionsClick = onCollectionsClick,
                    onLibraryToggle = onLibraryToggle,
                    onViewAllClick = onViewAllClick
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
                    games = uiState.collectionGames,
                    focusedIndex = uiState.collectionGamesFocusedIndex,
                    columns = uiState.libraryColumns,
                    availableLetters = emptyList(),
                    currentLetter = "",
                    onGameTapped = onGridGameTapped,
                    onLetterClick = {}
                )
            }
            DualHomeViewMode.LIBRARY_GRID -> {
                if (uiState.showFilterOverlay) {
                    DualFilterOverlay(
                        category = uiState.filterCategory,
                        options = uiState.filterOptions,
                        focusedIndex = uiState.filterFocusedIndex,
                        onOptionTapped = onFilterOptionTapped,
                        onCategoryTapped = onFilterCategoryTapped
                    )
                } else {
                    DualHomeLibraryGrid(
                        games = uiState.libraryGames,
                        focusedIndex = uiState.libraryFocusedIndex,
                        columns = uiState.libraryColumns,
                        availableLetters = uiState.availableLetters,
                        currentLetter = uiState.currentLetter,
                        showLetterOverlay = uiState.showLetterOverlay,
                        overlayLetter = uiState.overlayLetter,
                        onGameTapped = onGridGameTapped,
                        onLetterClick = onLetterClick
                    )
                }
            }
        }

        if (isForwarding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .touchOnly { onDimTapped() }
            )
        }
    }
}
