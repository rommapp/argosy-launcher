package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.ui.components.TileEditMode
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.home.HomeGameUi

/**
 * Gamepad routing for the curated grid on a companion display, held once for both handlers.
 *
 * The lower screen is driven by two different handlers depending on which display has the control
 * role, and the grid behaves identically on both. Written twice it drifted silently - the swapped
 * display simply did nothing - so the routing lives here and each handler supplies only the actions
 * it alone can perform: launching a game, opening details, starting an app, and telling its own
 * display that a collection tile changed the view.
 */
class DualCustomGridInputRouter(
    private val viewModel: DualHomeViewModel,
    private val onBroadcastSelection: () -> Unit,
    private val onOpenDetails: (Long) -> Unit,
    private val onLaunchGame: (HomeGameUi) -> Unit,
    private val onLaunchApp: (String) -> Unit,
    private val onEnterCollectionGames: () -> Unit = onBroadcastSelection
) {

    private val isActive: Boolean
        get() = viewModel.uiState.value.layoutKind == HomeLayoutKind.CUSTOM_GRID

    /**
     * Routes [event] when the curated grid owns it, and returns null when it does not, so the
     * caller can fall through to whatever the carousel or the app bar would have done.
     */
    fun route(event: GamepadEvent): InputResult? {
        if (!isActive) return null
        val state = viewModel.uiState.value
        val grid = state.customGrid

        return when (event) {
            GamepadEvent.Up -> move(GridDirection2D.UP)
            GamepadEvent.Down -> move(GridDirection2D.DOWN)
            GamepadEvent.Left -> move(GridDirection2D.LEFT)
            GamepadEvent.Right -> move(GridDirection2D.RIGHT)

            GamepadEvent.Confirm -> when {
                grid.pendingAdd != null -> {
                    if (grid.pendingAddFocusIndex == 0) {
                        viewModel.confirmPendingTileAdd()
                    } else {
                        viewModel.dismissPendingTileAdd()
                    }
                    InputResult.HANDLED
                }
                grid.showMenu -> {
                    viewModel.confirmTileMenu()
                    InputResult.HANDLED
                }
                grid.showPicker -> {
                    viewModel.confirmTilePickerSelection()
                    InputResult.HANDLED
                }
                grid.isEditing -> {
                    viewModel.commitTileEdit()
                    InputResult.handled(SoundType.SELECT)
                }
                grid.isOnAddPage -> {
                    viewModel.confirmAddPage()
                    InputResult.HANDLED
                }
                else -> activateFocusedTile()
            }

            GamepadEvent.LongConfirm -> {
                if (grid.showPicker || grid.showMenu) return InputResult.HANDLED
                if (grid.isEditing) viewModel.commitTileEdit() else viewModel.enterTileMoveMode()
                InputResult.handled(SoundType.TOGGLE)
            }

            GamepadEvent.Back -> when {
                grid.pendingAdd != null -> {
                    viewModel.dismissPendingTileAdd()
                    InputResult.HANDLED
                }
                grid.showMenu -> {
                    viewModel.closeTileMenu()
                    InputResult.handled(SoundType.CLOSE_MODAL)
                }
                grid.pickerSearchActive -> {
                    viewModel.toggleTilePickerSearch()
                    InputResult.HANDLED
                }
                grid.showPicker -> {
                    viewModel.closeTilePicker()
                    InputResult.handled(SoundType.CLOSE_MODAL)
                }
                grid.isEditing -> {
                    viewModel.cancelTileEdit()
                    InputResult.handled(SoundType.BACK)
                }
                else -> null
            }

            GamepadEvent.Select -> {
                if (grid.isEditing || grid.showPicker || grid.pendingAdd != null) {
                    return InputResult.HANDLED
                }
                viewModel.openTileMenu()
                InputResult.handled(SoundType.OPEN_MODAL)
            }

            GamepadEvent.ContextMenu -> {
                if (grid.isEditing) {
                    viewModel.toggleTileEditMode()
                    return InputResult.handled(SoundType.TOGGLE)
                }
                val gameId = grid.focusedGameId ?: return InputResult.HANDLED
                onOpenDetails(gameId)
                InputResult.HANDLED
            }

            GamepadEvent.SecondaryAction -> {
                if (grid.showPicker) {
                    viewModel.toggleTilePickerSearch()
                    return InputResult.HANDLED
                }
                grid.focusedGameId?.let { viewModel.toggleFavoriteById(it) }
                InputResult.HANDLED
            }

            GamepadEvent.PrevSection -> turnPage(-1)
            GamepadEvent.NextSection -> turnPage(1)

            else -> null
        }
    }

    private fun turnPage(delta: Int): InputResult {
        if (viewModel.uiState.value.customGrid.showPicker) {
            viewModel.cycleTilePickerCategory(delta)
        } else {
            viewModel.turnCustomGridPage(delta)
            onBroadcastSelection()
        }
        return InputResult.handled(SoundType.SECTION_CHANGE)
    }

    private fun move(direction: GridDirection2D): InputResult {
        val grid = viewModel.uiState.value.customGrid
        val vertical = direction == GridDirection2D.UP || direction == GridDirection2D.DOWN
        val delta = if (direction == GridDirection2D.UP) -1 else 1

        if (grid.pendingAdd != null) {
            if (!vertical) viewModel.movePendingTileAddFocus(delta)
            return InputResult.HANDLED
        }
        if (grid.showMenu) {
            if (vertical) viewModel.moveTileMenuFocus(delta)
            return InputResult.HANDLED
        }
        if (grid.showPicker) {
            if (vertical) viewModel.moveTilePickerFocus(delta)
            return InputResult.HANDLED
        }
        when (grid.editMode) {
            TileEditMode.MOVE -> {
                viewModel.moveFocusedTile(direction)
                onBroadcastSelection()
                return InputResult.HANDLED
            }
            TileEditMode.RESIZE -> {
                viewModel.resizeFocusedTileBy(direction)
                return InputResult.HANDLED
            }
            TileEditMode.NONE -> Unit
        }
        viewModel.moveCustomGridFocus(direction)
        onBroadcastSelection()
        return InputResult.HANDLED
    }

    /**
     * Confirm on a cell: what is there decides. An empty cell offers to fill itself, which is the
     * only way a tile gets added by hand.
     */
    private fun activateFocusedTile(): InputResult {
        val state = viewModel.uiState.value
        when (val target = viewModel.focusedTile()?.target) {
            is HomeTileTargetRef.Game -> {
                val game = state.tileGames[target.gameId]
                if (game == null) viewModel.openTilePicker() else onLaunchGame(game)
            }
            is HomeTileTargetRef.App -> onLaunchApp(target.packageName)
            is HomeTileTargetRef.Collection ->
                viewModel.enterCollectionGames(target.collectionId, fromTile = true) {
                    onEnterCollectionGames()
                }
            else -> viewModel.openTilePicker()
        }
        return InputResult.HANDLED
    }
}
