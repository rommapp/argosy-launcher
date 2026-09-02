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
    fun advanceFocusGame()
    fun rerollRandomTile()
    fun movePageChooserFocus(delta: Int)
    fun confirmPageChooser()
    fun backOutOfPageChooser()
    fun openTileMenu()
    fun closeTileMenu()
    fun moveTileMenuFocus(delta: Int)
    fun confirmTileMenu()
    fun confirmAddPage()
    fun openTilePicker()
    fun closeTilePicker()
    fun toggleTilePickerSearch()
    fun cycleTilePickerCategory(delta: Int)

    fun jumpTilePickerLetter(forward: Boolean)

    fun engageFocusedTile(): Boolean

    fun disengageTile(): Boolean

    fun toggleEngagedPlayback()

    fun seekEngagedTile(forward: Boolean)

    fun openEngagedFullscreen()
    fun launchTileApp(packageName: String)
    fun openTileCollection(collectionId: Long)
    fun playTileMedia(itemId: String)
    fun confirmPendingTileAdd()
    fun dismissPendingTileAdd()
    fun movePendingTileAddFocus(delta: Int)
    fun moveTilePickerFocus(delta: Int)
    fun confirmTilePickerSelection()
    fun moveMediaTileSetupFocus(delta: Int)
    fun moveMediaTileSetupSideways(towardsEnd: Boolean)
    fun confirmMediaTileSetup()
    fun backFromMediaTileSetup()
    fun confirmMediaTileNotice()
    fun dismissMediaTileNotice()
    fun moveMediaTileNoticeFocus(delta: Int)
    fun focusedTileGameId(): Long?
    fun installApk(gameId: Long)
    fun launchGame(gameId: Long, channelName: String? = null)
    fun resumeDownload(gameId: Long)
    fun queueDownload(gameId: Long)
    fun queueSteamDownload(gameId: Long)
    fun navigateToLibrary(platformId: Long?, sourceFilter: String?)
    fun toggleFavorite(gameId: Long)
    fun unfavoriteMedia(itemId: String)
    fun setNavigationContext(gameIds: List<Long>)
    fun scrollToFirst(): Boolean
    fun navigateToContinuePlaying(): Boolean
    fun syncFromRomm()
    fun playFocusedMedia(startOver: Boolean = false)
    fun confirmFocusedMedia()
    fun openMediaResumePrompt(): Boolean
    fun openFocusedMediaDetail()
    fun refreshMediaRails()
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
        if (state.customGrid.engagedTileId != null) return InputResult.handled(SoundType.BOUNDARY)
        if (state.customGrid.mediaTileNotice != null) return InputResult.HANDLED
        if (state.customGrid.isMediaSetupOpen) {
            actions.moveMediaTileSetupFocus(-1)
            return InputResult.HANDLED
        }
        if (state.customGrid.pageChooser != null) {
            actions.movePageChooserFocus(-1)
            return InputResult.HANDLED
        }
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
        if (state.customGrid.engagedTileId != null) return InputResult.handled(SoundType.BOUNDARY)
        if (state.customGrid.mediaTileNotice != null) return InputResult.HANDLED
        if (state.customGrid.isMediaSetupOpen) {
            actions.moveMediaTileSetupFocus(1)
            return InputResult.HANDLED
        }
        if (state.customGrid.pageChooser != null) {
            actions.movePageChooserFocus(1)
            return InputResult.HANDLED
        }
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
        if (actions.uiState.value.customGrid.engagedTileId != null) {
            actions.seekEngagedTile(false)
            return InputResult.HANDLED
        }
        if (actions.uiState.value.customGrid.pendingAdd != null) {
            actions.movePendingTileAddFocus(-1)
            return InputResult.HANDLED
        }
        val state = actions.uiState.value
        if (state.customGrid.mediaTileNotice != null) {
            actions.moveMediaTileNoticeFocus(-1)
            return InputResult.HANDLED
        }
        if (state.customGrid.isMediaSetupOpen) {
            actions.moveMediaTileSetupSideways(false)
            return InputResult.HANDLED
        }
        if (state.showAddToCollectionModal || state.showGameMenu) return InputResult.HANDLED
        if (isCustomGrid(state)) return customMove(GridDirection2D.LEFT)
        if (isGrid(state)) return gridMove(GridDirection.LEFT)
        val moved = if (railIsReversed(state)) actions.nextGame() else actions.previousGame()
        return if (moved) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onRight(): InputResult {
        if (actions.uiState.value.customGrid.engagedTileId != null) {
            actions.seekEngagedTile(true)
            return InputResult.HANDLED
        }
        if (actions.uiState.value.customGrid.pendingAdd != null) {
            actions.movePendingTileAddFocus(1)
            return InputResult.HANDLED
        }
        val state = actions.uiState.value
        if (state.customGrid.mediaTileNotice != null) {
            actions.moveMediaTileNoticeFocus(1)
            return InputResult.HANDLED
        }
        if (state.customGrid.isMediaSetupOpen) {
            actions.moveMediaTileSetupSideways(true)
            return InputResult.HANDLED
        }
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
        if (actions.engageFocusedTile()) return
        when (val target = state.customGrid.focusedTile?.target) {
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Game ->
                actions.launchGame(target.gameId)
            is com.nendo.argosy.domain.model.HomeTileTargetRef.App ->
                actions.launchTileApp(target.packageName)
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Collection ->
                if (target.focusGameId != null) {
                    actions.launchGame(target.focusGameId)
                } else {
                    actions.openTileCollection(target.collectionId)
                }
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Media ->
                actions.playTileMedia(target.itemId)
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Feature -> confirmFeatureTile(target, state)
            else -> actions.openTilePicker()
        }
    }

    private fun confirmFeatureTile(
        target: com.nendo.argosy.domain.model.HomeTileTargetRef.Feature,
        state: HomeUiState
    ) {
        when (target.kind) {
            com.nendo.argosy.domain.model.FeatureTileKind.RANDOM_GAME -> {
                val picked = target.pickedGameId
                if (picked != null) actions.launchGame(picked) else actions.rerollRandomTile()
            }
            com.nendo.argosy.domain.model.FeatureTileKind.CONTINUE -> {
                state.continueGameId?.let { actions.launchGame(it) }
            }
            com.nendo.argosy.domain.model.FeatureTileKind.RA_SUMMARY -> {
                state.raTileSummary?.latestGameId?.let { onGameSelect(it) }
            }
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
        if (state.customGrid.engagedTileId != null) {
            actions.toggleEngagedPlayback()
            return InputResult.HANDLED
        }
        if (state.customGrid.pendingAdd != null) {
            if (state.customGrid.pendingAddFocusIndex == 0) {
                actions.confirmPendingTileAdd()
            } else {
                actions.dismissPendingTileAdd()
            }
            return InputResult.HANDLED
        }
        val notice = state.customGrid.mediaTileNotice
        if (notice != null) {
            if (notice.buttonIndex == 0) {
                actions.dismissMediaTileNotice()
            } else {
                actions.confirmMediaTileNotice()
            }
            return InputResult.HANDLED
        }
        when {
            state.customGrid.pageChooser != null -> actions.confirmPageChooser()
            state.customGrid.isMediaSetupOpen -> actions.confirmMediaTileSetup()
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
                    is HomeRowItem.Media -> actions.confirmFocusedMedia()
                    is HomeRowItem.ViewAll -> actions.navigateToLibrary(item.platformId, item.sourceFilter)
                    null -> when {
                        state.isMediaRow -> actions.refreshMediaRails()
                        state.currentRow is HomeRow.PinnedRegular ||
                            state.currentRow is HomeRow.PinnedVirtual -> Unit
                        state.isRommConfigured -> actions.syncFromRomm()
                        else -> Unit
                    }
                }
            }
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        val state = actions.uiState.value
        if (actions.disengageTile()) return InputResult.handled(SoundType.CLOSE_MODAL)
        if (state.customGrid.pendingAdd != null) {
            actions.dismissPendingTileAdd()
            return InputResult.HANDLED
        }
        if (state.customGrid.mediaTileNotice != null) {
            actions.dismissMediaTileNotice()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
        if (state.customGrid.isMediaSetupOpen) {
            actions.backFromMediaTileSetup()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
        if (state.customGrid.pickerSearchActive) {
            actions.toggleTilePickerSearch()
            return InputResult.HANDLED
        }
        if (state.showTilePicker) {
            actions.closeTilePicker()
            return InputResult.HANDLED
        }
        if (state.customGrid.pageChooser != null) {
            actions.backOutOfPageChooser()
            return InputResult.handled(SoundType.CLOSE_MODAL)
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
        if (state.customGrid.mediaSetup != null) return InputResult.HANDLED
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

    /**
     * Swaps which display holds which role, the same meaning Select carries on every home surface.
     *
     * A device with one display has no roles to trade, so there the button keeps its older job of
     * opening the focused game's menu. Holding A reaches that menu on both kinds of device, which
     * is what keeps the actions on a controller when a second screen claims the press.
     */
    override fun onSelect(): InputResult {
        val state = actions.uiState.value
        if (state.showAddToCollectionModal) return InputResult.HANDLED
        if (state.customGrid.mediaSetup != null) return InputResult.HANDLED
        if (state.customGrid.engagedTileId != null) return InputResult.HANDLED
        val dualScreen = com.nendo.argosy.DualScreenManagerHolder.instance
            ?.takeIf { it.isDualScreenDevice.value }
        if (dualScreen != null) {
            dualScreen.swapRoles()
            return InputResult.handled(SoundType.TOGGLE)
        }
        if (isCustomGrid(state)) return InputResult.UNHANDLED
        if (state.isMediaRow) return InputResult.HANDLED
        if (state.focusedGame != null) {
            actions.toggleGameMenu()
        }
        return InputResult.HANDLED
    }

    /**
     * Whether the thing under the cursor is a title rather than a game. Read off the item, not off
     * the row: the Favorites row carries both, so a row-level answer would give the wrong buttons to
     * half of it.
     */
    private fun focusedIsMedia(state: HomeUiState): Boolean =
        state.focusedItem is HomeRowItem.Media

    /**
     * Holding confirm opens the menu for whatever is under the cursor: the tile menu on the grid,
     * where arranging is its first entry, and the game menu on a row. A menu that names its
     * actions is reachable in a way a hold that silently starts a drag never was.
     *
     * Committing on the second hold still matches the press that started an arrange. On a title
     * the hold asks whether to start over instead, since a plain press already resumes, and a
     * title with nothing stored is played rather than asked about twice.
     */
    override fun onLongConfirm(): InputResult {
        val state = actions.uiState.value
        if (isCustomGrid(state)) {
            if (state.customGrid.mediaSetup != null) return InputResult.HANDLED
            if (state.showTilePicker || state.customGrid.showMenu) return InputResult.UNHANDLED
            if (state.customGrid.isEditing) {
                actions.commitTileEdit()
                return InputResult.handled(SoundType.TOGGLE)
            }
            actions.openTileMenu()
            return InputResult.handled(SoundType.OPEN_MODAL)
        }
        if (!focusedIsMedia(state)) {
            if (state.focusedGame == null) return InputResult.UNHANDLED
            actions.toggleGameMenu()
            return InputResult.handled(SoundType.OPEN_MODAL)
        }
        if (actions.openMediaResumePrompt()) return InputResult.handled(SoundType.OPEN_MODAL)
        actions.playFocusedMedia()
        return InputResult.HANDLED
    }

    /**
     * The row's second verb. On a row of games it marks and unmarks a favourite; on a media rail it
     * is the second way to the Start Over prompt, alongside holding confirm.
     *
     * The Favorites row is a row of favourites whichever kind is under the cursor, so it keeps the
     * first meaning for both halves: the button that put a title there is the button that takes it
     * back out, which is what the media half was missing.
     */
    override fun onSecondaryAction(): InputResult {
        val state = actions.uiState.value
        if (state.customGrid.mediaSetup != null) return InputResult.HANDLED
        if (state.showTilePicker) {
            actions.toggleTilePickerSearch()
            return InputResult.HANDLED
        }
        if (isCustomGrid(state)) {
            val collection = state.customGrid.focusedCollection
            if (collection?.focusGameId != null) {
                actions.advanceFocusGame()
                return InputResult.HANDLED
            }
            val feature = state.customGrid.focusedTile?.target
                as? com.nendo.argosy.domain.model.HomeTileTargetRef.Feature
            if (feature?.kind == com.nendo.argosy.domain.model.FeatureTileKind.RANDOM_GAME) {
                actions.rerollRandomTile()
                return InputResult.handled(SoundType.TOGGLE)
            }
        }
        if (!isCustomGrid(state) && focusedIsMedia(state)) {
            val media = state.focusedMedia ?: return InputResult.UNHANDLED
            if (state.currentRow == HomeRow.Favorites) {
                actions.unfavoriteMedia(media.itemId)
                return InputResult.HANDLED
            }
            if (actions.openMediaResumePrompt()) return InputResult.handled(SoundType.OPEN_MODAL)
            return InputResult.handled(SoundType.BOUNDARY)
        }
        val game = state.focusedGame ?: return InputResult.UNHANDLED
        actions.toggleFavorite(game.id)
        return InputResult.HANDLED
    }

    override fun onPrevTrigger(): InputResult {
        if (!actions.uiState.value.showTilePicker) return InputResult.UNHANDLED
        actions.jumpTilePickerLetter(false)
        return InputResult.handled(SoundType.SECTION_CHANGE)
    }

    override fun onNextTrigger(): InputResult {
        if (!actions.uiState.value.showTilePicker) return InputResult.UNHANDLED
        actions.jumpTilePickerLetter(true)
        return InputResult.handled(SoundType.SECTION_CHANGE)
    }

    override fun onPrevSection(): InputResult {
        val state = actions.uiState.value
        if (state.showAddToCollectionModal || state.showGameMenu) return InputResult.HANDLED
        if (state.customGrid.mediaSetup != null) return InputResult.HANDLED
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
        if (state.customGrid.mediaSetup != null) return InputResult.HANDLED
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
        if (state.customGrid.engagedTileId != null) {
            actions.openEngagedFullscreen()
            return InputResult.HANDLED
        }
        if (state.customGrid.mediaSetup != null) return InputResult.HANDLED
        if (isCustomGrid(state) && state.customGrid.isEditing) {
            actions.toggleTileEditMode()
            return InputResult.handled(SoundType.TOGGLE)
        }
        if (isCustomGrid(state)) {
            state.customGrid.focusedGameId?.let { gameId ->
                onGameSelect(gameId)
                return InputResult.HANDLED
            }
        }
        if (state.focusedMedia != null) {
            actions.openFocusedMediaDetail()
            return InputResult.HANDLED
        }
        if (!isCustomGrid(state) && state.isMediaRow) return InputResult.UNHANDLED
        val game = state.focusedGame ?: return InputResult.UNHANDLED
        actions.setNavigationContext(
            state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game.id }
        )
        onGameSelect(game.id)
        return InputResult.HANDLED
    }
}
