package com.nendo.argosy.ui.dualscreen.home

import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

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
    private val onOpenOverlay: (String) -> Unit,
    private val onLaunchApp: (String) -> Unit,
    private val onLaunchAppAlternate: (String) -> Unit = {}
) : InputHandler {

    fun handleForViewMode(): InputResult {
        if (viewModel.forwardingMode.value != ForwardingMode.NONE) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    fun dispatch(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        if (viewModel.forwardingMode.value != ForwardingMode.NONE) {
            return InputResult.HANDLED
        }
        return when (viewModel.uiState.value.viewMode) {
            DualHomeViewMode.CAROUSEL -> handleCarousel(event)
            DualHomeViewMode.COLLECTIONS -> handleCollections(event)
            DualHomeViewMode.COLLECTION_GAMES -> handleCollectionGames(event)
            DualHomeViewMode.LIBRARY_GRID -> handleLibraryGrid(event)
        }
    }

    private fun handleCarousel(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        val state = viewModel.uiState.value
        val inAppBar = state.focusZone == DualHomeFocusZone.APP_BAR
        val apps = homeApps()

        return when (event) {
            com.nendo.argosy.ui.input.GamepadEvent.Menu,
            com.nendo.argosy.ui.input.GamepadEvent.LeftStickClick,
            com.nendo.argosy.ui.input.GamepadEvent.RightStickClick -> {
                viewModel.startDrawerForwarding()
                onOpenOverlay(event::class.simpleName ?: "Menu")
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Left -> {
                if (inAppBar) viewModel.selectPreviousApp()
                else viewModel.selectPrevious()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Right -> {
                if (inAppBar) viewModel.selectNextApp(apps.size)
                else viewModel.selectNext()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Down -> {
                val isExternal = com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.isExternalDisplay == true
                if (!inAppBar && apps.isNotEmpty() && !isExternal) {
                    viewModel.focusAppBar(apps.size)
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Up -> {
                if (inAppBar) {
                    viewModel.focusCarousel()
                    InputResult.HANDLED
                } else {
                    viewModel.enterCollections()
                    onBroadcastViewModeChange()
                    onBroadcastCollectionFocused()
                    InputResult.HANDLED
                }
            }
            com.nendo.argosy.ui.input.GamepadEvent.PrevSection -> {
                if (inAppBar) viewModel.focusCarousel()
                viewModel.previousSortSection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.NextSection -> {
                if (inAppBar) viewModel.focusCarousel()
                viewModel.nextSortSection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Select -> {
                viewModel.toggleLibraryGrid {
                    onBroadcastViewModeChange()
                    if (viewModel.uiState.value.viewMode == DualHomeViewMode.LIBRARY_GRID)
                        onBroadcastLibraryGameSelection()
                    else
                        onBroadcastCurrentGameSelection()
                }
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Confirm -> {
                if (inAppBar) {
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
                    val game = state.selectedGame
                    if (game != null) {
                        val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                        onBroadcastDirectAction(action, game.id)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
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
            com.nendo.argosy.ui.input.GamepadEvent.Menu,
            com.nendo.argosy.ui.input.GamepadEvent.LeftStickClick,
            com.nendo.argosy.ui.input.GamepadEvent.RightStickClick -> {
                viewModel.startDrawerForwarding()
                onOpenOverlay(event::class.simpleName ?: "Menu")
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
                if (game != null) {
                    val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                    onBroadcastDirectAction(action, game.id)
                }
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.ContextMenu -> {
                val game = viewModel.focusedCollectionGame()
                if (game != null) onSelectGame(game.id)
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Back -> {
                viewModel.exitCollectionGames()
                onBroadcastViewModeChange()
                onBroadcastCollectionFocused()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Menu,
            com.nendo.argosy.ui.input.GamepadEvent.LeftStickClick,
            com.nendo.argosy.ui.input.GamepadEvent.RightStickClick -> {
                viewModel.startDrawerForwarding()
                onOpenOverlay(event::class.simpleName ?: "Menu")
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleLibraryGrid(event: com.nendo.argosy.ui.input.GamepadEvent): InputResult {
        if (viewModel.uiState.value.showFilterOverlay) {
            return handleFilter(event)
        }

        val columns = viewModel.uiState.value.libraryColumns
        return when (event) {
            com.nendo.argosy.ui.input.GamepadEvent.Left -> {
                viewModel.moveLibraryFocus(-1)
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Right -> {
                viewModel.moveLibraryFocus(1)
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Up -> {
                viewModel.moveLibraryFocus(-columns)
                onBroadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Down -> {
                viewModel.moveLibraryFocus(columns)
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
                viewModel.toggleLibraryGrid {
                    onBroadcastViewModeChange()
                    onBroadcastCurrentGameSelection()
                }
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
                if (game != null) {
                    val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                    onBroadcastDirectAction(action, game.id)
                }
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.ContextMenu -> {
                val state = viewModel.uiState.value
                val game = state.libraryGames.getOrNull(state.libraryFocusedIndex)
                if (game != null) onSelectGame(game.id)
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.SecondaryAction -> {
                viewModel.toggleFilterOverlay()
                InputResult.HANDLED
            }
            com.nendo.argosy.ui.input.GamepadEvent.Menu,
            com.nendo.argosy.ui.input.GamepadEvent.LeftStickClick,
            com.nendo.argosy.ui.input.GamepadEvent.RightStickClick -> {
                viewModel.startDrawerForwarding()
                onOpenOverlay(event::class.simpleName ?: "Menu")
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
