package com.nendo.argosy.ui.quickmenu

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

/**
 * Gamepad input for the quick menu, plus the touch entry points that share its actions.
 * [onOpenSearch] takes the query to open Search with, null for an empty field.
 */
class QuickMenuInputHandler(
    private val viewModel: QuickMenuViewModel,
    private val onGameSelect: (Long) -> Unit,
    private val onGamePlay: (Long) -> Unit,
    private val onOpenSearch: (String?) -> Unit,
    private val onDismiss: () -> Unit
) : InputHandler {

    private inline fun whenVisible(block: (QuickMenuUiState) -> InputResult): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED
        viewModel.exitTouchMode()
        if (state.picker != null) return InputResult.HANDLED
        return block(state)
    }

    private inline fun outsideActionsMenu(state: QuickMenuUiState, block: () -> InputResult): InputResult =
        if (state.actionsMenu != null) InputResult.HANDLED else block()

    private fun moved(result: Boolean): InputResult =
        if (result) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

    override fun onUp(): InputResult = whenVisible { state ->
        if (state.actionsMenu != null) {
            viewModel.moveActionsFocus(-1)
            return@whenVisible InputResult.HANDLED
        }
        moved(viewModel.step(FocusStep.UP))
    }

    override fun onDown(): InputResult = whenVisible { state ->
        if (state.actionsMenu != null) {
            viewModel.moveActionsFocus(1)
            return@whenVisible InputResult.HANDLED
        }
        moved(viewModel.step(FocusStep.DOWN))
    }

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    private fun horizontal(delta: Int): InputResult = whenVisible { state ->
        outsideActionsMenu(state) {
            when {
                state.selectedOrb == QuickMenuOrb.RANDOM -> randomHorizontal(state, delta)
                state.isHeaderFocused -> cycleHeader(state, delta)
                else -> moved(viewModel.step(if (delta < 0) FocusStep.LEFT else FocusStep.RIGHT))
            }
        }
    }

    private fun randomHorizontal(state: QuickMenuUiState, delta: Int): InputResult = when (state.random.focus) {
        RandomStop.PLATFORM -> {
            viewModel.cycleRandomPlatform(delta)
            InputResult.handled(SoundType.TOGGLE)
        }
        RandomStop.INSTALLED ->
            if (viewModel.setRandomInstalledOnly(delta > 0)) InputResult.handled(SoundType.TOGGLE)
            else InputResult.handled(SoundType.BOUNDARY)
        else -> moved(viewModel.step(if (delta < 0) FocusStep.LEFT else FocusStep.RIGHT))
    }

    private fun cycleHeader(state: QuickMenuUiState, delta: Int): InputResult {
        when (state.selectedOrb) {
            QuickMenuOrb.MOST_PLAYED -> viewModel.cycleMostPlayedRange(delta)
            QuickMenuOrb.FAVORITES -> viewModel.cycleFavoritesSort(delta)
            else -> return InputResult.handled(SoundType.BOUNDARY)
        }
        return InputResult.handled(SoundType.TOGGLE)
    }

    override fun onConfirm(): InputResult = whenVisible { state ->
        val menu = state.actionsMenu
        if (menu != null) {
            menu.focusedAction?.let { performAction(menu, it) }
            return@whenVisible InputResult.HANDLED
        }
        when (state.selectedOrb) {
            QuickMenuOrb.SEARCH -> confirmSearch(state, state.focusIndex)
            QuickMenuOrb.RANDOM -> confirmRandom(state, state.random.focus)
            else -> confirmGame(state)
        }
    }

    /**
     * Acts on Search tab stop [index]: the field at [SEARCH_FIELD_INDEX], then the rows.
     */
    fun confirmSearch(state: QuickMenuUiState, index: Int): InputResult {
        if (index == SEARCH_FIELD_INDEX) {
            onOpenSearch(null)
            return InputResult.HANDLED
        }
        val row = index - 1
        if (state.search.showsRecentlyAdded) {
            val game = state.search.recentlyAdded.getOrNull(row) ?: return InputResult.handled(SoundType.BOUNDARY)
            onGameSelect(game.id)
        } else {
            val search = state.search.recentSearches.getOrNull(row) ?: return InputResult.handled(SoundType.BOUNDARY)
            onOpenSearch(search.query)
        }
        return InputResult.HANDLED
    }

    /**
     * Acts on Random [stop] the way A does, and the way a tap on it does.
     */
    fun confirmRandom(state: QuickMenuUiState, stop: RandomStop): InputResult {
        val random = state.random
        if (!random.isEnabled(stop)) return InputResult.handled(SoundType.BOUNDARY)
        val current = random.current
        return when (stop) {
            RandomStop.PLAY -> {
                current?.let { onGamePlay(it.id) }
                InputResult.HANDLED
            }
            RandomStop.ROLL -> {
                viewModel.rollAgain()
                InputResult.handled(SoundType.TOGGLE)
            }
            RandomStop.DETAILS -> {
                current?.let { onGameSelect(it.id) }
                InputResult.handled(SoundType.SELECT)
            }
            RandomStop.FAVORITE -> toggleFavorite(current)
            RandomStop.HISTORY -> {
                viewModel.pickRandom(random.historyFocus)
                InputResult.handled(SoundType.SELECT)
            }
            RandomStop.PLATFORM -> {
                viewModel.openPicker(QuickMenuPicker.PLATFORM)
                InputResult.handled(SoundType.OPEN_MODAL)
            }
            RandomStop.INSTALLED -> {
                viewModel.toggleRandomInstalledOnly()
                InputResult.handled(SoundType.TOGGLE)
            }
        }
    }

    private fun confirmGame(state: QuickMenuUiState): InputResult {
        if (state.isHeaderFocused) {
            val picker = when (state.selectedOrb) {
                QuickMenuOrb.MOST_PLAYED -> QuickMenuPicker.RANKING
                QuickMenuOrb.FAVORITES -> QuickMenuPicker.SORT
                else -> return InputResult.handled(SoundType.BOUNDARY)
            }
            viewModel.openPicker(picker)
            return InputResult.handled(SoundType.OPEN_MODAL)
        }
        val game = state.focusedGame() ?: return InputResult.handled(SoundType.BOUNDARY)
        if (state.selectedOrb.confirmPlays) onGamePlay(game.id) else onGameSelect(game.id)
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult = whenVisible { state ->
        outsideActionsMenu(state) {
            when (state.selectedOrb) {
                QuickMenuOrb.RANDOM, QuickMenuOrb.RECENT -> {
                    val game = state.actionGame() ?: return@outsideActionsMenu InputResult.HANDLED
                    onGameSelect(game.id)
                    InputResult.handled(SoundType.SELECT)
                }
                QuickMenuOrb.MOST_PLAYED -> {
                    viewModel.cycleMostPlayedRange(1)
                    InputResult.handled(SoundType.TOGGLE)
                }
                QuickMenuOrb.TOP_UNPLAYED -> {
                    val game = state.actionGame() ?: return@outsideActionsMenu InputResult.HANDLED
                    viewModel.toggleNotInterested(game.id)
                    InputResult.handled(SoundType.TOGGLE)
                }
                QuickMenuOrb.FAVORITES -> {
                    viewModel.cycleFavoritesSort(1)
                    InputResult.handled(SoundType.TOGGLE)
                }
                QuickMenuOrb.SEARCH -> InputResult.HANDLED
            }
        }
    }

    override fun onSecondaryAction(): InputResult = whenVisible { state ->
        outsideActionsMenu(state) {
            if (state.selectedOrb == QuickMenuOrb.SEARCH) return@outsideActionsMenu InputResult.HANDLED
            toggleFavorite(state.actionGame())
        }
    }

    private fun toggleFavorite(game: QuickMenuGameUi?): InputResult {
        game ?: return InputResult.HANDLED
        viewModel.toggleFavorite(game.id)
        return InputResult.handled(if (game.isFavorite) SoundType.UNFAVORITE else SoundType.FAVORITE)
    }

    override fun onLongConfirm(): InputResult = whenVisible { state ->
        outsideActionsMenu(state) {
            val game = state.actionGame() ?: return@outsideActionsMenu InputResult.HANDLED
            viewModel.openActions(game)
            InputResult.handled(SoundType.OPEN_MODAL)
        }
    }

    /**
     * Runs [action] from the game actions menu, then closes the menu.
     */
    fun performAction(menu: GameActionsMenuState, action: QuickMenuGameAction) {
        viewModel.closeActions()
        val game = menu.game
        when (action) {
            QuickMenuGameAction.PRIMARY -> onGamePlay(game.id)
            QuickMenuGameAction.FAVORITE -> viewModel.toggleFavorite(game.id)
            QuickMenuGameAction.DETAILS -> onGameSelect(game.id)
            QuickMenuGameAction.NOT_INTERESTED -> viewModel.toggleNotInterested(game.id)
        }
    }

    override fun onBack(): InputResult = whenVisible { state ->
        if (state.actionsMenu != null) {
            viewModel.closeActions()
            return@whenVisible InputResult.handled(SoundType.CLOSE_MODAL)
        }
        onDismiss()
        InputResult.HANDLED
    }

    override fun onLeftStickClick(): InputResult = whenVisible {
        onDismiss()
        InputResult.HANDLED
    }

    override fun onPrevSection(): InputResult = whenVisible { state ->
        outsideActionsMenu(state) {
            viewModel.moveSection(-1)
            InputResult.handled(SoundType.SECTION_CHANGE)
        }
    }

    override fun onNextSection(): InputResult = whenVisible { state ->
        outsideActionsMenu(state) {
            viewModel.moveSection(1)
            InputResult.handled(SoundType.SECTION_CHANGE)
        }
    }

    override fun onPrevTrigger(): InputResult = whenVisible { InputResult.HANDLED }

    override fun onNextTrigger(): InputResult = whenVisible { InputResult.HANDLED }

    override fun onMenu(): InputResult = whenVisible { InputResult.HANDLED }

    override fun onSelect(): InputResult = whenVisible { InputResult.HANDLED }
}
