package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.StorageGamesItem
import com.nendo.argosy.ui.screens.settings.sections.StorageGamesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createStorageGamesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.storageGamesItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageGamesMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageGamesSections

internal class StorageGamesSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutInfo(): StorageGamesLayoutInfo =
        createStorageGamesLayoutInfo(viewModel.uiState.value)

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        return if (viewModel.moveFocusWrapped(delta, storageGamesMaxFocusIndex(layoutInfo()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        return when (storageGamesItemAtFocusIndex(state.focusedIndex, layoutInfo())) {
            StorageGamesItem.IntegrityToggle -> toggleLeftRight(
                direction,
                state.storage.weeklyIntegrityCheckEnabled
            ) { viewModel.toggleWeeklyIntegrityCheck(it) }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(storageGamesSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(storageGamesSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onContextMenu(): InputResult {
        viewModel.toggleGamesSortMode()
        return InputResult.HANDLED
    }
}
