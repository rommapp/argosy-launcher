package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.usecase.storage.GameStorageBucket
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.StoragePlatformGamesItem
import com.nendo.argosy.ui.screens.settings.sections.StoragePlatformGamesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createStoragePlatformGamesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.storagePlatformGamesItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storagePlatformGamesMaxFocusIndex

internal class StoragePlatformGamesSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutInfo(): StoragePlatformGamesLayoutInfo =
        createStoragePlatformGamesLayoutInfo(viewModel.uiState.value)

    override fun onUp(): InputResult = moveGame(-1)

    override fun onDown(): InputResult = moveGame(1)

    private fun moveGame(delta: Int): InputResult {
        val moved = viewModel.moveFocusWrapped(delta, storagePlatformGamesMaxFocusIndex(layoutInfo()))
        return if (moved) {
            viewModel.resetStoragePlatformHighlightedCategory()
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = moveCategory(-1)

    override fun onRight(): InputResult = moveCategory(1)

    private fun moveCategory(delta: Int): InputResult {
        val game = focusedGame() ?: return InputResult.UNHANDLED
        val count = game.buckets.size
        if (count <= 1) return InputResult.handled(SoundType.BOUNDARY)
        val current = viewModel.uiState.value.storagePlatformGames.highlightedCategoryIndex
            .coerceIn(0, count - 1)
        val next = (current + delta).coerceIn(0, count - 1)
        return if (next != current) {
            viewModel.setStoragePlatformHighlightedCategory(next)
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onSecondaryAction(): InputResult {
        val game = focusedGame() ?: return InputResult.UNHANDLED
        val count = game.buckets.size
        if (count == 0) return InputResult.UNHANDLED
        val index = viewModel.uiState.value.storagePlatformGames.highlightedCategoryIndex
            .coerceIn(0, count - 1)
        val bucket = game.buckets[index].bucket
        if (bucket == GameStorageBucket.BASE) {
            viewModel.requestStoragePlatformGameDelete(game.gameId)
        } else {
            viewModel.requestStoragePlatformCategoryDelete(game.gameId, bucket)
        }
        return InputResult.handled(SoundType.OPEN_MODAL)
    }

    private fun focusedGame(): com.nendo.argosy.domain.usecase.storage.GameStorageBreakdown? {
        val state = viewModel.uiState.value
        val item = storagePlatformGamesItemAtFocusIndex(state.focusedIndex, layoutInfo())
        val gameId = (item as? StoragePlatformGamesItem.GameCard)?.gameId ?: return null
        return state.storagePlatformGames.games.firstOrNull { it.gameId == gameId }
    }
}
