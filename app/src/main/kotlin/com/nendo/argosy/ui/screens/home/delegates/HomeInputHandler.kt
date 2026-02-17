package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.home.HomeRow
import com.nendo.argosy.ui.screens.home.HomeRowItem
import com.nendo.argosy.ui.screens.home.HomeUiState
import kotlinx.coroutines.flow.StateFlow

interface HomeInputActions {
    val uiState: StateFlow<HomeUiState>
    fun moveCollectionFocusUp()
    fun moveCollectionFocusDown()
    fun confirmCollectionSelection()
    fun dismissAddToCollectionModal()
    fun moveGameMenuFocus(delta: Int)
    fun toggleGameMenu()
    fun confirmGameMenuSelection(onGameSelect: (Long) -> Unit)
    fun previousRow()
    fun nextRow()
    fun previousGame(): Boolean
    fun nextGame(): Boolean
    fun installApk(gameId: Long)
    fun launchGame(gameId: Long, channelName: String? = null)
    fun resumeDownload(gameId: Long)
    fun queueDownload(gameId: Long)
    fun navigateToLibrary(platformId: Long?, sourceFilter: String?)
    fun toggleFavorite(gameId: Long)
    fun setNavigationContext(gameIds: List<Long>)
    fun scrollToFirst(): Boolean
    fun navigateToContinuePlaying(): Boolean
}

class HomeInputHandler(
    private val actions: HomeInputActions,
    private val isDefaultView: Boolean,
    private val onGameSelect: (Long) -> Unit,
    private val onNavigateToDefault: () -> Unit,
    private val onDrawerToggle: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        val state = actions.uiState.value
        return when {
            state.showAddToCollectionModal -> {
                actions.moveCollectionFocusUp()
                InputResult.HANDLED
            }
            state.showGameMenu -> {
                actions.moveGameMenuFocus(-1)
                InputResult.HANDLED
            }
            else -> {
                actions.previousRow()
                InputResult.handled(SoundType.SECTION_CHANGE)
            }
        }
    }

    override fun onDown(): InputResult {
        val state = actions.uiState.value
        return when {
            state.showAddToCollectionModal -> {
                actions.moveCollectionFocusDown()
                InputResult.HANDLED
            }
            state.showGameMenu -> {
                actions.moveGameMenuFocus(1)
                InputResult.HANDLED
            }
            else -> {
                actions.nextRow()
                InputResult.handled(SoundType.SECTION_CHANGE)
            }
        }
    }

    override fun onLeft(): InputResult {
        val state = actions.uiState.value
        if (state.showAddToCollectionModal || state.showGameMenu) return InputResult.HANDLED
        return if (actions.previousGame()) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onRight(): InputResult {
        val state = actions.uiState.value
        if (state.showAddToCollectionModal || state.showGameMenu) return InputResult.HANDLED
        return if (actions.nextGame()) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val state = actions.uiState.value
        when {
            state.showAddToCollectionModal -> actions.confirmCollectionSelection()
            state.showGameMenu -> actions.confirmGameMenuSelection(onGameSelect)
            else -> {
                when (val item = state.focusedItem) {
                    is HomeRowItem.Game -> {
                        val game = item.game
                        val indicator = state.downloadIndicatorFor(game.id)
                        when {
                            game.needsInstall -> actions.installApk(game.id)
                            game.isDownloaded -> actions.launchGame(game.id)
                            indicator.isPaused || indicator.isQueued -> actions.resumeDownload(game.id)
                            else -> actions.queueDownload(game.id)
                        }
                    }
                    is HomeRowItem.ViewAll -> actions.navigateToLibrary(item.platformId, item.sourceFilter)
                    null -> { }
                }
            }
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        val state = actions.uiState.value
        if (state.showAddToCollectionModal) {
            actions.dismissAddToCollectionModal()
            return InputResult.HANDLED
        }
        if (state.showGameMenu) {
            actions.toggleGameMenu()
            return InputResult.HANDLED
        }
        if (actions.scrollToFirst()) {
            return InputResult.HANDLED
        }
        if (actions.navigateToContinuePlaying()) {
            return InputResult.handled(SoundType.SECTION_CHANGE)
        }
        if (!isDefaultView) {
            onNavigateToDefault()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onMenu(): InputResult {
        val state = actions.uiState.value
        if (state.showAddToCollectionModal) {
            actions.dismissAddToCollectionModal()
            return InputResult.UNHANDLED
        }
        if (state.showGameMenu) {
            actions.toggleGameMenu()
            return InputResult.UNHANDLED
        }
        onDrawerToggle()
        return InputResult.HANDLED
    }

    override fun onSelect(): InputResult {
        if (actions.uiState.value.showAddToCollectionModal) return InputResult.HANDLED
        if (actions.uiState.value.focusedGame != null) {
            actions.toggleGameMenu()
        }
        return InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult {
        val game = actions.uiState.value.focusedGame ?: return InputResult.UNHANDLED
        actions.toggleFavorite(game.id)
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult {
        val state = actions.uiState.value
        val game = state.focusedGame ?: return InputResult.UNHANDLED
        actions.setNavigationContext(
            state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game.id }
        )
        onGameSelect(game.id)
        return InputResult.HANDLED
    }
}
