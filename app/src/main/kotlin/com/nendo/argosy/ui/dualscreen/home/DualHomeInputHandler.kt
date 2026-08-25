package com.nendo.argosy.ui.dualscreen.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.components.AutoGridMove
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.home.HomeGameUi

class DualHomeInputHandler(
    private val viewModel: DualHomeViewModel,
    private val homeApps: () -> List<String>,
    private val onBroadcastViewModeChange: () -> Unit,
    private val onBroadcastCollectionFocused: () -> Unit,
    private val onBroadcastCurrentGameSelection: () -> Unit,
    private val onBroadcastLibraryGameSelection: () -> Unit,
    private val onBroadcastCollectionGameSelection: () -> Unit,
    private val onBroadcastDirectAction: (String, Long) -> Unit,
    private val onSelectGame: (Long) -> Unit,
    private val onLaunchApp: (String) -> Unit,
    private val onLaunchAppAlternate: (String) -> Unit = {}
) : InputHandler {

    private fun confirmGame(game: HomeGameUi) {
        if (game.isSteamGame && !game.isPlayable) {
            onSelectGame(game.id)
        } else {
            onBroadcastDirectAction(if (game.isPlayable) "PLAY" else "DOWNLOAD", game.id)
        }
    }

    private val customGrid = com.nendo.argosy.ui.home.grid.DualCustomGridInputRouter(
        viewModel = viewModel,
        onBroadcastSelection = onBroadcastCurrentGameSelection,
        onOpenDetails = onSelectGame,
        onLaunchGame = ::confirmGame,
        onLaunchApp = onLaunchApp,
        onEnterCollectionGames = {
            onBroadcastViewModeChange()
            onBroadcastCollectionGameSelection()
        }
    )

    fun handleForViewMode(): InputResult {
        if (viewModel.forwardingMode.value != ForwardingMode.NONE) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    fun dispatch(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        val dsm = com.nendo.argosy.DualScreenManagerHolder.instance
        if (dsm?.dualSyncOverlay?.value != null) {
            when (event) {
                com.nendo.argosy.ui.input.GamepadEvent.Up -> dsm.moveSyncConflictFocus(-1)
                com.nendo.argosy.ui.input.GamepadEvent.Down -> dsm.moveSyncConflictFocus(1)
                com.nendo.argosy.ui.input.GamepadEvent.Confirm -> dsm.confirmSyncConflict()
                com.nendo.argosy.ui.input.GamepadEvent.Back -> dsm.dismissSyncConflict()
                else -> {}
            }
            return InputResult.HANDLED
        }
        if (dsm?.dualSaveConflict?.value != null) {
            when (event) {
                com.nendo.argosy.ui.input.GamepadEvent.Left,
                com.nendo.argosy.ui.input.GamepadEvent.Up -> dsm.moveSaveConflictFocus(-1)
                com.nendo.argosy.ui.input.GamepadEvent.Right,
                com.nendo.argosy.ui.input.GamepadEvent.Down -> dsm.moveSaveConflictFocus(1)
                com.nendo.argosy.ui.input.GamepadEvent.Confirm -> dsm.confirmSaveConflict()
                com.nendo.argosy.ui.input.GamepadEvent.Back -> dsm.dismissSaveConflict()
                else -> {}
            }
            return InputResult.HANDLED
        }
        if (viewModel.forwardingMode.value != ForwardingMode.NONE) {
            return InputResult.HANDLED
        }
        return when (viewModel.uiState.value.viewMode) {
            DualHomeViewMode.CAROUSEL -> handleCarousel(event)
            DualHomeViewMode.COLLECTIONS -> handleCollections(event)
            DualHomeViewMode.COLLECTION_GAMES -> handleCollectionGames(event)
            DualHomeViewMode.LIBRARY_GRID -> handleLibraryGrid(event)
            DualHomeViewMode.MEDIA_GRID -> handleMediaGrid(event)
        }
    }

    /**
     * Whether the app bar carries its media button right now. It is the last slot when it is there
     * at all, so the count the movement helpers take is the app count plus this.
     */
    private fun hasMediaSlot(): Boolean {
        val dsm = com.nendo.argosy.DualScreenManagerHolder.instance ?: return false
        return dsm.mediaPlayback.value != null || dsm.mediaSignedIn.value
    }

    private fun mediaSlotCount(): Int = if (hasMediaSlot()) 1 else 0

    private fun handleCarousel(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        val state = viewModel.uiState.value
        val inAppBar = state.focusZone == DualHomeFocusZone.APP_BAR
        if (!inAppBar) customGrid.route(event)?.let { return it }
        val apps = homeApps()
        val appBarSlots = apps.size + mediaSlotCount()
        val onMediaSlot = inAppBar && hasMediaSlot() && state.appBarIndex == apps.size
        val inGrid = !inAppBar && state.layoutKind == HomeLayoutKind.AUTO_GRID
        val reversed = state.carouselConfig.inverted

        fun moveGrid(direction: GridDirection): Boolean {
            if (!inGrid) return false
            when (viewModel.moveCarouselGridFocus(direction)) {
                is AutoGridMove.Focus -> onBroadcastCurrentGameSelection()
                AutoGridMove.PreviousSection -> viewModel.previousSection()
                AutoGridMove.NextSection -> viewModel.nextSection()
                AutoGridMove.None -> {}
            }
            return true
        }

        return when (event) {
            com.nendo.argosy.ui.input.GamepadEvent.Left -> {
                if (inAppBar) viewModel.selectPreviousApp()
                else if (!moveGrid(GridDirection.LEFT)) {
                    if (reversed) viewModel.selectNext() else viewModel.selectPrevious()
                    onBroadcastCurrentGameSelection()
                }
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Right -> {
                if (inAppBar) viewModel.selectNextApp(appBarSlots)
                else if (!moveGrid(GridDirection.RIGHT)) {
                    if (reversed) viewModel.selectPrevious() else viewModel.selectNext()
                    onBroadcastCurrentGameSelection()
                }
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Down -> {
                val isExternal = com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.isExternalDisplay == true
                if (moveGrid(GridDirection.DOWN)) {
                    InputResult.HANDLED
                } else if (!inAppBar && appBarSlots > 0 && !isExternal) {
                    viewModel.focusAppBar(appBarSlots)
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Up -> {
                if (inAppBar) {
                    viewModel.focusCarousel()
                    InputResult.HANDLED
                } else if (moveGrid(GridDirection.UP)) {
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Select -> {
                com.nendo.argosy.DualScreenManagerHolder.instance?.swapRoles()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.PrevSection -> {
                if (inAppBar) viewModel.focusCarousel()
                viewModel.previousSection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.NextSection -> {
                if (inAppBar) viewModel.focusCarousel()
                viewModel.nextSection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Confirm -> {
                if (onMediaSlot) {
                    com.nendo.argosy.DualScreenManagerHolder.instance?.toggleCompanionMediaView()
                    InputResult.HANDLED
                } else if (inAppBar) {
                    val packageName = apps.getOrNull(state.appBarIndex)
                    if (packageName != null) {
                        onLaunchApp(packageName)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                } else if (state.isViewAllFocused) {
                    val platformId = state.currentPlatformId
                    if (platformId != null) {
                        viewModel.enterLibraryGridForPlatform(platformId) {
                            onBroadcastViewModeChange()
                            onBroadcastLibraryGameSelection()
                        }
                    } else {
                        viewModel.enterLibraryGrid {
                            onBroadcastViewModeChange()
                            onBroadcastLibraryGameSelection()
                        }
                    }
                    InputResult.HANDLED
                } else {
                    val mediaItemId = viewModel.focusedMediaItemId()
                    val game = state.selectedGame
                    when {
                        mediaItemId != null -> {
                            viewModel.playFocusedMedia()
                            InputResult.HANDLED
                        }
                        game != null -> {
                            confirmGame(game)
                            InputResult.HANDLED
                        }
                        else -> InputResult.UNHANDLED
                    }
                }
            }
            com.nendo.argosy.ui.input.GamepadEvent.ContextMenu -> {
                if (inAppBar) return InputResult.UNHANDLED
                val game = state.selectedGame
                if (game != null) {
                    onSelectGame(game.id)
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.SecondaryAction -> {
                if (inAppBar) {
                    val packageName = apps.getOrNull(state.appBarIndex)
                    if (packageName != null) {
                        onLaunchAppAlternate(packageName)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                } else {
                    viewModel.toggleFavorite()
                    InputResult.HANDLED
                }
            }
            else -> InputResult.UNHANDLED
        }
    }

    private fun handleCollections(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        return when (event) {
            com.nendo.argosy.ui.input.GamepadEvent.Up -> {
                viewModel.moveCollectionFocus(-1)
                onBroadcastCollectionFocused()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Down -> {
                viewModel.moveCollectionFocus(1)
                onBroadcastCollectionFocused()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Confirm -> {
                val collection = viewModel.selectedCollectionItem()
                if (collection != null) {
                    viewModel.enterCollectionGames(collection.id) {
                        onBroadcastViewModeChange()
                        onBroadcastCollectionGameSelection()
                    }
                }
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Back -> {
                viewModel.exitToCarousel()
                onBroadcastViewModeChange()
                onBroadcastCurrentGameSelection()
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleCollectionGames(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        val columns = viewModel.uiState.value.libraryColumns
        return when (event) {
            com.nendo.argosy.ui.input.GamepadEvent.Left -> {
                viewModel.moveCollectionGamesFocus(-1)
                onBroadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Right -> {
                viewModel.moveCollectionGamesFocus(1)
                onBroadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Up -> {
                viewModel.moveCollectionGamesFocus(-columns)
                onBroadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Down -> {
                viewModel.moveCollectionGamesFocus(columns)
                onBroadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Confirm -> {
                val game = viewModel.focusedCollectionGame()
                if (game != null) confirmGame(game)
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.ContextMenu -> {
                val game = viewModel.focusedCollectionGame()
                if (game != null) onSelectGame(game.id)
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Back -> {
                val fromTile = viewModel.uiState.value.collectionOpenedFromTile
                viewModel.exitCollectionGames()
                onBroadcastViewModeChange()
                if (fromTile) {
                    onBroadcastCurrentGameSelection()
                } else {
                    onBroadcastCollectionFocused()
                }
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    /**
     * The media browser on this screen. Confirm starts the title, the shoulders change library,
     * X refreshes, Y resumes through the prompt, and Back returns to the carousel, matching the
     * single-screen media library's bindings.
     */
    private fun handleMediaGrid(event: GamepadEvent): InputResult {
        if (viewModel.uiState.value.mediaResumePrompt != null) {
            return handleMediaResumePrompt(event)
        }
        val columns = viewModel.uiState.value.mediaGridColumns
        return when (event) {
            GamepadEvent.Left ->
                if (viewModel.moveMediaGridFocus(GridDirection.LEFT, columns)) InputResult.HANDLED
                else InputResult.UNHANDLED
            GamepadEvent.Right ->
                if (viewModel.moveMediaGridFocus(GridDirection.RIGHT, columns)) InputResult.HANDLED
                else InputResult.UNHANDLED
            GamepadEvent.Up ->
                if (viewModel.moveMediaGridFocus(GridDirection.UP, columns)) InputResult.HANDLED
                else InputResult.UNHANDLED
            GamepadEvent.Down ->
                if (viewModel.moveMediaGridFocus(GridDirection.DOWN, columns)) InputResult.HANDLED
                else InputResult.UNHANDLED
            GamepadEvent.PrevSection -> {
                viewModel.cycleMediaLibrary(-1)
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                viewModel.cycleMediaLibrary(1)
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                if (viewModel.focusedMediaItemId() == null) return InputResult.UNHANDLED
                viewModel.playFocusedMedia()
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                viewModel.refreshMediaGrid()
                InputResult.HANDLED
            }
            GamepadEvent.SecondaryAction -> {
                if (viewModel.focusedMediaItemId() == null) return InputResult.UNHANDLED
                if (!viewModel.openMediaResumePromptForFocused()) {
                    viewModel.playFocusedMedia()
                }
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                viewModel.exitToCarousel()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    /**
     * The resume prompt owns the pad while it is up. Everything answers HANDLED, including the
     * events the prompt ignores, so nothing leaks through to the grid underneath it.
     */
    private fun handleMediaResumePrompt(event: GamepadEvent): InputResult {
        when (event) {
            GamepadEvent.Up -> viewModel.moveMediaResumeFocus(-1)
            GamepadEvent.Down -> viewModel.moveMediaResumeFocus(1)
            GamepadEvent.Confirm -> viewModel.confirmMediaResumePrompt()
            GamepadEvent.Back -> viewModel.dismissMediaResumePrompt()
            else -> {}
        }
        return InputResult.HANDLED
    }

    private fun handleLibraryGrid(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        if (viewModel.uiState.value.showFilterOverlay) {
            return handleFilter(event)
        }

        return when (event) {
            com.nendo.argosy.ui.input.GamepadEvent.Left -> {
                viewModel.moveLibraryFocusLeft()
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Right -> {
                viewModel.moveLibraryFocusRight()
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Up -> {
                viewModel.moveLibraryFocusUp()
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Down -> {
                viewModel.moveLibraryFocusDown()
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.PrevTrigger -> {
                viewModel.previousSortSection()
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.NextTrigger -> {
                viewModel.nextSortSection()
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Select -> {
                com.nendo.argosy.DualScreenManagerHolder.instance?.swapRoles()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Back -> {
                viewModel.exitToCarousel()
                onBroadcastViewModeChange()
                onBroadcastCurrentGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Confirm -> {
                val state = viewModel.uiState.value
                val game = state.libraryGames.getOrNull(state.libraryFocusedIndex)
                if (game != null) confirmGame(game)
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.ContextMenu -> {
                val state = viewModel.uiState.value
                val game = state.libraryGames.getOrNull(state.libraryFocusedIndex)
                if (game != null) onSelectGame(game.id)
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.PrevSection -> {
                viewModel.cycleLibraryPlatform(-1) {
                    onBroadcastLibraryGameSelection()
                }
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.NextSection -> {
                viewModel.cycleLibraryPlatform(1) {
                    onBroadcastLibraryGameSelection()
                }
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.SecondaryAction -> {
                val isViewingHidden = viewModel.uiState.value.activeFilters.source == "HIDDEN"
                if (isViewingHidden) {
                    val state = viewModel.uiState.value
                    val game = state.libraryGames.getOrNull(state.libraryFocusedIndex)
                    if (game != null) onBroadcastDirectAction("UNHIDE", game.id)
                } else {
                    viewModel.toggleFilterOverlay()
                }
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    override fun onUp() = dispatch(GamepadEvent.Up)
    override fun onDown() = dispatch(GamepadEvent.Down)
    override fun onLeft() = dispatch(GamepadEvent.Left)
    override fun onRight() = dispatch(GamepadEvent.Right)
    override fun onConfirm() = dispatch(GamepadEvent.Confirm)
    override fun onLongConfirm() = dispatch(GamepadEvent.LongConfirm)
    override fun onBack() = dispatch(GamepadEvent.Back)
    override fun onSecondaryAction() = dispatch(GamepadEvent.SecondaryAction)
    override fun onContextMenu() = dispatch(GamepadEvent.ContextMenu)
    override fun onPrevSection() = dispatch(GamepadEvent.PrevSection)
    override fun onNextSection() = dispatch(GamepadEvent.NextSection)
    override fun onPrevTrigger() = dispatch(GamepadEvent.PrevTrigger)
    override fun onNextTrigger() = dispatch(GamepadEvent.NextTrigger)
    override fun onSelect() = dispatch(GamepadEvent.Select)

    private fun handleFilter(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        return when (event) {
            com.nendo.argosy.ui.input.GamepadEvent.Up -> {
                viewModel.moveFilterFocus(-1)
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Down -> {
                viewModel.moveFilterFocus(1)
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.PrevSection -> {
                viewModel.previousFilterCategory()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.NextSection -> {
                viewModel.nextFilterCategory()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.PrevTrigger -> {
                viewModel.jumpFilterToPreviousLetter()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.NextTrigger -> {
                viewModel.jumpFilterToNextLetter()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Confirm -> {
                viewModel.confirmFilter()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Back,
            com.nendo.argosy.ui.input.GamepadEvent.SecondaryAction -> {
                viewModel.toggleFilterOverlay()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.ContextMenu -> {
                viewModel.clearCategoryFilters()
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }
}
