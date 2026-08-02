package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.components.AutoGridMove
import com.nendo.argosy.ui.components.TileEditMode
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
    fun moveGridFocus(direction: GridDirection): AutoGridMove
    fun moveCustomGridFocus(direction: com.nendo.argosy.domain.model.GridDirection2D): Boolean
    fun turnCustomGridPage(delta: Int): Boolean
    fun moveFocusedTile(direction: com.nendo.argosy.domain.model.GridDirection2D): Boolean
    fun resizeFocusedTile(direction: com.nendo.argosy.domain.model.GridDirection2D): Boolean
    fun enterTileMoveMode()
    fun exitTileMoveMode()
    fun commitTileEdit()
    fun cancelTileEdit()
    fun toggleTileEditMode()
    fun openTileMenu()
    fun closeTileMenu()
    fun moveTileMenuFocus(delta: Int)
    fun confirmTileMenu()
    fun confirmAddPage()
    fun openTilePicker()
    fun closeTilePicker()
    fun toggleTilePickerSearch()
    fun cycleTilePickerCategory(delta: Int)
    fun launchTileApp(packageName: String)
    fun openTileCollection(collectionId: Long)
    fun confirmPendingTileAdd()
    fun dismissPendingTileAdd()
    fun movePendingTileAddFocus(delta: Int)
    fun moveTilePickerFocus(delta: Int)
    fun confirmTilePickerSelection()
    fun focusedTileGameId(): Long?
    fun installApk(gameId: Long)
    fun launchGame(gameId: Long, channelName: String? = null)
    fun resumeDownload(gameId: Long)
    fun queueDownload(gameId: Long)
    fun queueSteamDownload(gameId: Long)
    fun navigateToLibrary(platformId: Long?, sourceFilter: String?)
    fun toggleFavorite(gameId: Long)
    fun setNavigationContext(gameIds: List<Long>)
    fun scrollToFirst(): Boolean
    fun navigateToContinuePlaying(): Boolean
    fun syncFromRomm()
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
        if (state.customGrid.showMenu) {
            actions.moveTileMenuFocus(-1)
            return InputResult.HANDLED
        }
        return when {
            state.showAddToCollectionModal -> {
                actions.moveCollectionFocusUp()
                InputResult.HANDLED
            }
            state.showGameMenu -> {
                actions.moveGameMenuFocus(-1)
                InputResult.HANDLED
            }
            state.showTilePicker -> {
                actions.moveTilePickerFocus(-1)
                InputResult.HANDLED
            }
            isCustomGrid(state) -> customMove(GridDirection2D.UP)
            isGrid(state) -> gridMove(GridDirection.UP)
            else -> {
                actions.previousRow()
                InputResult.handled(SoundType.SECTION_CHANGE)
            }
        }
    }

    override fun onDown(): InputResult {
        val state = actions.uiState.value
        if (state.customGrid.showMenu) {
            actions.moveTileMenuFocus(1)
            return InputResult.HANDLED
        }
        return when {
            state.showAddToCollectionModal -> {
                actions.moveCollectionFocusDown()
                InputResult.HANDLED
            }
            state.showGameMenu -> {
                actions.moveGameMenuFocus(1)
                InputResult.HANDLED
            }
            state.showTilePicker -> {
                actions.moveTilePickerFocus(1)
                InputResult.HANDLED
            }
            isCustomGrid(state) -> customMove(GridDirection2D.DOWN)
            isGrid(state) -> gridMove(GridDirection.DOWN)
            else -> {
                actions.nextRow()
                InputResult.handled(SoundType.SECTION_CHANGE)
            }
        }
    }

    override fun onLeft(): InputResult {
        if (actions.uiState.value.customGrid.pendingAdd != null) {
            actions.movePendingTileAddFocus(-1)
            return InputResult.HANDLED
        }
        val state = actions.uiState.value
        if (state.showAddToCollectionModal || state.showGameMenu) return InputResult.HANDLED
        if (isCustomGrid(state)) return customMove(GridDirection2D.LEFT)
        if (isGrid(state)) return gridMove(GridDirection.LEFT)
        val moved = if (railIsReversed(state)) actions.nextGame() else actions.previousGame()
        return if (moved) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onRight(): InputResult {
        if (actions.uiState.value.customGrid.pendingAdd != null) {
            actions.movePendingTileAddFocus(1)
            return InputResult.HANDLED
        }
        val state = actions.uiState.value
        if (state.showAddToCollectionModal || state.showGameMenu) return InputResult.HANDLED
        if (isCustomGrid(state)) return customMove(GridDirection2D.RIGHT)
        if (isGrid(state)) return gridMove(GridDirection.RIGHT)
        val moved = if (railIsReversed(state)) actions.previousGame() else actions.nextGame()
        return if (moved) InputResult.HANDLED else InputResult.UNHANDLED
    }

    private fun isGrid(state: HomeUiState): Boolean = state.layoutKind == HomeLayoutKind.AUTO_GRID

    private fun isCustomGrid(state: HomeUiState): Boolean =
        state.layoutKind == HomeLayoutKind.CUSTOM_GRID

    /**
     * The press is always consumed: a curated page's edges are the end of the grid, and there is no
     * neighbouring zone for an unhandled move to fall through to.
     */
    /**
     * Confirm on a curated cell either launches what is there or offers to fill it, so the same
     * button reads as "use this" whether or not the cell holds anything yet.
     */
    private fun confirmCustomGridCell() {
        val state = actions.uiState.value
        if (state.customGrid.isOnAddPage) {
            actions.confirmAddPage()
            return
        }
        when (val target = state.customGrid.focusedTile?.target) {
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Game ->
                actions.launchGame(target.gameId)
            is com.nendo.argosy.domain.model.HomeTileTargetRef.App ->
                actions.launchTileApp(target.packageName)
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Collection ->
                actions.openTileCollection(target.collectionId)
            else -> actions.openTilePicker()
        }
    }

    private fun customMove(direction: GridDirection2D): InputResult {
        val moved = when (actions.uiState.value.customGrid.editMode) {
            TileEditMode.MOVE -> actions.moveFocusedTile(direction)
            TileEditMode.RESIZE -> actions.resizeFocusedTile(direction)
            TileEditMode.NONE -> actions.moveCustomGridFocus(direction)
        }
        return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    /**
     * A reversed rail draws later items to the left, so the stick has to be read the same way round
     * or pressing right walks the highlight left.
     */
    private fun railIsReversed(state: HomeUiState): Boolean = state.carouselConfig.inverted

    private fun gridMove(direction: GridDirection): InputResult =
        when (actions.moveGridFocus(direction)) {
            is AutoGridMove.Focus -> InputResult.HANDLED
            AutoGridMove.PreviousSection -> {
                actions.previousRow()
                InputResult.handled(SoundType.SECTION_CHANGE)
            }
            AutoGridMove.NextSection -> {
                actions.nextRow()
                InputResult.handled(SoundType.SECTION_CHANGE)
            }
            AutoGridMove.None -> InputResult.HANDLED
        }

    override fun onConfirm(): InputResult {
        val state = actions.uiState.value
        if (state.customGrid.pendingAdd != null) {
            if (state.customGrid.pendingAddFocusIndex == 0) {
                actions.confirmPendingTileAdd()
            } else {
                actions.dismissPendingTileAdd()
            }
            return InputResult.HANDLED
        }
        when {
            state.showTilePicker -> actions.confirmTilePickerSelection()
            state.customGrid.showMenu -> actions.confirmTileMenu()
            state.customGrid.isEditing -> {
                actions.commitTileEdit()
                return InputResult.handled(SoundType.SELECT)
            }
            state.showAddToCollectionModal -> actions.confirmCollectionSelection()
            state.showGameMenu -> actions.confirmGameMenuSelection(onGameSelect)
            isCustomGrid(state) -> confirmCustomGridCell()
            else -> {
                when (val item = state.focusedItem) {
                    is HomeRowItem.Game -> {
                        val game = item.game
                        val indicator = state.downloadIndicatorFor(game.id)
                        when {
                            game.needsInstall -> actions.installApk(game.id)
                            game.isDownloaded -> actions.launchGame(game.id)
                            indicator.isPaused || indicator.isQueued -> actions.resumeDownload(game.id)
                            game.isSteamGame -> actions.queueSteamDownload(game.id)
                            else -> actions.queueDownload(game.id)
                        }
                    }
                    is HomeRowItem.ViewAll -> actions.navigateToLibrary(item.platformId, item.sourceFilter)
                    null -> {
                        val isPinnedRow = state.currentRow is HomeRow.PinnedRegular ||
                            state.currentRow is HomeRow.PinnedVirtual
                        if (state.isRommConfigured && !isPinnedRow) actions.syncFromRomm()
                    }
                }
            }
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        val state = actions.uiState.value
        if (state.customGrid.pendingAdd != null) {
            actions.dismissPendingTileAdd()
            return InputResult.HANDLED
        }
        if (state.customGrid.pickerSearchActive) {
            actions.toggleTilePickerSearch()
            return InputResult.HANDLED
        }
        if (state.showTilePicker) {
            actions.closeTilePicker()
            return InputResult.HANDLED
        }
        if (state.customGrid.showMenu) {
            actions.closeTileMenu()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
        if (state.customGrid.isEditing) {
            actions.cancelTileEdit()
            return InputResult.handled(SoundType.BACK)
        }
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
        val state = actions.uiState.value
        if (state.showAddToCollectionModal) return InputResult.HANDLED
        if (isCustomGrid(state)) {
            if (state.customGrid.isEditing) return InputResult.HANDLED
            actions.openTileMenu()
            return InputResult.handled(SoundType.OPEN_MODAL)
        }
        if (state.focusedGame != null) {
            actions.toggleGameMenu()
        }
        return InputResult.HANDLED
    }

    /**
     * Holding confirm picks a tile up and puts it down again, so arranging never has to go through
     * the select menu. Committing on the second hold matches the press that started it.
     */
    override fun onLongConfirm(): InputResult {
        val state = actions.uiState.value
        if (!isCustomGrid(state) || state.showTilePicker || state.customGrid.showMenu) {
            return InputResult.UNHANDLED
        }
        if (state.customGrid.isEditing) {
            actions.commitTileEdit()
        } else {
            actions.enterTileMoveMode()
        }
        return InputResult.handled(SoundType.TOGGLE)
    }

    override fun onSecondaryAction(): InputResult {
        if (actions.uiState.value.showTilePicker) {
            actions.toggleTilePickerSearch()
            return InputResult.HANDLED
        }
        val game = actions.uiState.value.focusedGame ?: return InputResult.UNHANDLED
        actions.toggleFavorite(game.id)
        return InputResult.HANDLED
    }

    override fun onPrevSection(): InputResult {
        val state = actions.uiState.value
        if (state.showAddToCollectionModal || state.showGameMenu) return InputResult.HANDLED
        if (state.showTilePicker) {
            actions.cycleTilePickerCategory(-1)
            return InputResult.handled(SoundType.SECTION_CHANGE)
        }
        if (isCustomGrid(state)) {
            actions.turnCustomGridPage(-1)
            return InputResult.handled(SoundType.SECTION_CHANGE)
        }
        actions.previousRow()
        return InputResult.handled(SoundType.SECTION_CHANGE)
    }

    override fun onNextSection(): InputResult {
        val state = actions.uiState.value
        if (state.showAddToCollectionModal || state.showGameMenu) return InputResult.HANDLED
        if (state.showTilePicker) {
            actions.cycleTilePickerCategory(1)
            return InputResult.handled(SoundType.SECTION_CHANGE)
        }
        if (isCustomGrid(state)) {
            actions.turnCustomGridPage(1)
            return InputResult.handled(SoundType.SECTION_CHANGE)
        }
        actions.nextRow()
        return InputResult.handled(SoundType.SECTION_CHANGE)
    }

    override fun onContextMenu(): InputResult {
        val state = actions.uiState.value
        if (isCustomGrid(state) && state.customGrid.isEditing) {
            actions.toggleTileEditMode()
            return InputResult.handled(SoundType.TOGGLE)
        }
        val game = state.focusedGame ?: return InputResult.UNHANDLED
        actions.setNavigationContext(
            state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game.id }
        )
        onGameSelect(game.id)
        return InputResult.HANDLED
    }
}
