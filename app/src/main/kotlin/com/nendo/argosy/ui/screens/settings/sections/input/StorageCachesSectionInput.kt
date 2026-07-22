package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.StorageCachesItem
import com.nendo.argosy.ui.screens.settings.sections.StorageCachesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createStorageCachesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.storageCachesItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageCachesMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageCachesSections

internal class StorageCachesSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutInfo(): StorageCachesLayoutInfo =
        createStorageCachesLayoutInfo(viewModel.uiState.value)

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        return if (viewModel.moveFocusWrapped(delta, storageCachesMaxFocusIndex(layoutInfo()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        return when (storageCachesItemAtFocusIndex(state.focusedIndex, layoutInfo())) {
            StorageCachesItem.StateCacheToggle -> toggleLeftRight(
                direction,
                state.syncSettings.stateCacheEnabled
            ) { viewModel.toggleStateCache() }
            StorageCachesItem.ScreenshotsToggle -> toggleLeftRight(
                direction,
                state.server.syncScreenshotsEnabled
            ) { viewModel.toggleSyncScreenshots() }
            StorageCachesItem.BoxArtToggle -> toggleLeftRight(
                direction,
                state.server.boxArtCacheEnabled
            ) { viewModel.toggleBoxArtCache() }
            StorageCachesItem.SaveCacheLimit -> {
                viewModel.cycleSaveCacheLimit(direction)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(storageCachesSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(storageCachesSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
