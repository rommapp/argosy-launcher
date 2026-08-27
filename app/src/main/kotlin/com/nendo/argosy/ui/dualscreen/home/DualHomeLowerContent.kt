/**
 * DUAL-SCREEN COMPONENT - Lower display home content.
 * Runs in :companion process (SecondaryHomeActivity).
 * Footer hints are on upper screen only.
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
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
    dualMediaViewModel: com.nendo.argosy.ui.dualscreen.media.DualMediaViewModel? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val forwardingMode by viewModel.forwardingMode.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaDownloadIndicators = androidx.compose.runtime.remember(
        uiState.mediaItems,
        uiState.mediaDownloadProgress
    ) {
        uiState.mediaItems.associate { it.itemId to uiState.mediaDownloadIndicatorFor(it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (uiState.viewMode) {
            DualHomeViewMode.CAROUSEL -> {
                DualHomeLowerScreen(
                    games = uiState.games,
                    mediaItems = uiState.mediaItems,
                    mediaDownloadIndicators = mediaDownloadIndicators,
                    onMediaTapped = { index ->
                        viewModel.selectByTouch(index)
                        viewModel.playFocusedMedia()
                    },
                    onMediaLongPressed = { index ->
                        viewModel.selectByTouch(index)
                        viewModel.openMediaMenuForFocused()
                    },
                    selectedIndex = uiState.selectedIndex,
                    platformName = uiState.platformName(context),
                    totalCount = uiState.totalCount,
                    hasMoreGames = uiState.hasMoreGames,
                    isViewAllFocused = uiState.isViewAllFocused,
                    homeApps = homeApps,
                    appBarFocused = uiState.focusZone == DualHomeFocusZone.APP_BAR,
                    appBarIndex = uiState.appBarIndex,
                    viewMode = uiState.viewMode,
                    sectionLabels = uiState.sections.map { it.resolveShortTitle(context) },
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
                    customGridContentFor = { tile -> uiState.tileContentFor(tile, context) },
                    customGridConfig = uiState.customGridConfig,
                    backgroundBlur = uiState.backgroundBlur,
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
            DualHomeViewMode.MEDIA_GRID -> {
                com.nendo.argosy.ui.dualscreen.media.DualMediaGrid(
                    items = uiState.mediaGridItems,
                    focusedIndex = uiState.mediaGridFocusedIndex,
                    libraryLabel = uiState.mediaLibraries
                        .getOrNull(uiState.mediaLibraryIndex)?.name.orEmpty(),
                    onColumnsChanged = { viewModel.setMediaGridColumns(it) },
                    onItemTapped = { index ->
                        viewModel.setMediaGridFocus(index)
                        viewModel.playFocusedMedia()
                    },
                    onItemLongPressed = { index ->
                        viewModel.setMediaGridFocus(index)
                        viewModel.openMediaMenuForFocused()
                    },
                    onPosterLoaded = viewModel::onMediaPosterLoaded
                )
                com.nendo.argosy.ui.screens.media.modals.MediaResumeModalContent(
                    prompt = uiState.mediaResumePrompt,
                    focusedIndex = uiState.mediaResumeFocusIndex,
                    onStartOver = { itemId ->
                        viewModel.startMediaFromPrompt(itemId, startOver = true)
                    },
                    onResume = { itemId ->
                        viewModel.startMediaFromPrompt(itemId, startOver = false)
                    },
                    onDismiss = viewModel::dismissMediaResumePrompt
                )
            }
            DualHomeViewMode.MEDIA_INFO -> {
                val mediaVm = dualMediaViewModel
                if (mediaVm != null) {
                    val mediaState by mediaVm.uiState.collectAsState()
                    com.nendo.argosy.ui.dualscreen.media.DualMediaLowerScreen(
                        state = mediaState,
                        isInteractive = true,
                        onRowTapped = { index -> mediaVm.focusRow(index) },
                        onRowConfirmed = { index ->
                            val row = mediaState.rows.getOrNull(index)
                            if (row is com.nendo.argosy.ui.dualscreen.media.DualMediaRow.Item) {
                                viewModel.confirmMediaInfoRow(row.item.itemId)
                            }
                        },
                        onSeasonSelected = { mediaVm.selectSeason(it) },
                        onEpisodeTapped = { viewModel.confirmMediaInfoRow(it) },
                        onBackTapped = { viewModel.exitMediaInfo() },
                        backHint = stringResource(R.string.dual_media_footer_back_home)
                    )
                }
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
                title = stringResource(R.string.dual_home_tile_add_title),
                message = stringResource(
                    R.string.dual_home_tile_add_message,
                    pendingTileAdd.title
                ),
                confirmLabel = stringResource(R.string.dual_home_tile_add_confirm),
                cancelLabel = stringResource(R.string.dual_home_tile_add_cancel),
                focusedIndex = uiState.customGrid.pendingAddFocusIndex,
                onConfirm = viewModel::confirmPendingTileAdd,
                onDismiss = viewModel::dismissPendingTileAdd
            )
        }

        if (uiState.collectionPickerGameId != null) {
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                title = stringResource(R.string.dual_home_collection_picker_title),
                entries = uiState.collectionPickerEntries.map { entry ->
                    if (entry.isMember) {
                        context.getString(
                            R.string.dual_home_collection_picker_member,
                            entry.name
                        )
                    } else {
                        entry.name
                    }
                },
                focusIndex = uiState.collectionPickerFocusIndex,
                onSelect = { index ->
                    viewModel.moveCollectionPickerFocus(index - uiState.collectionPickerFocusIndex)
                    viewModel.confirmCollectionPicker()
                },
                onDismiss = viewModel::closeCollectionPicker
            )
        }

        uiState.mediaMenu?.let { menu ->
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                title = menu.item.title,
                entries = menu.actions.map { context.getString(it.labelRes) },
                focusIndex = menu.focusIndex,
                onSelect = { index ->
                    viewModel.moveMediaMenuFocus(index - menu.focusIndex)
                    viewModel.confirmMediaMenu()
                },
                onDismiss = viewModel::closeMediaMenu,
                header = stringResource(R.string.dual_home_media_menu_header)
            )
        }

        com.nendo.argosy.ui.screens.media.modals.MediaDownloadModalContent(
            prompt = uiState.mediaDownloadPrompt,
            onFocus = viewModel::focusMediaDownloadOption,
            onConfirm = viewModel::confirmMediaDownloadOption,
            onDismiss = viewModel::dismissMediaDownloadPrompt,
            onCommitSelection = viewModel::commitMediaEpisodeSelection
        )

        if (uiState.showLibraryMenu) {
            val libraryGame = viewModel.focusedLibraryGame()
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                title = libraryGame?.title.orEmpty(),
                entries = viewModel.libraryMenuActions().map { context.getString(it.labelRes) },
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
                title = tile?.let { uiState.tileContentFor(it, context)?.label }.orEmpty(),
                entries = uiState.customGrid.menuActions.map { context.getString(it.labelRes) },
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

        uiState.mediaNotice?.let { notice ->
            MediaNoticeBanner(
                message = notice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimens.spacingXl)
            )
        }
    }
}

/**
 * A short-lived line of feedback for a media action that could not proceed. Non-interactive and
 * self-dismissing, so it captures no input on either modality.
 */
@Composable
private fun MediaNoticeBanner(message: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        androidx.compose.material3.Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                horizontal = Dimens.spacingLg,
                vertical = Dimens.spacingSm
            )
        )
    }
}
