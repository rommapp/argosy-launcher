package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.StorageMediaItem
import com.nendo.argosy.ui.screens.settings.sections.StorageMediaLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createStorageMediaLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.storageMediaItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageMediaMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageMediaSections

internal class StorageMediaSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutInfo(): StorageMediaLayoutInfo =
        createStorageMediaLayoutInfo(viewModel.uiState.value)

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        return if (viewModel.moveFocusWrapped(delta, storageMediaMaxFocusIndex(layoutInfo()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLongConfirm(): InputResult {
        val state = viewModel.uiState.value
        if (storageMediaItemAtFocusIndex(state.focusedIndex, layoutInfo()) != StorageMediaItem.RecomputeRow) {
            return InputResult.UNHANDLED
        }
        if (state.attribution.isRefreshing) {
            return InputResult.handled(SoundType.SILENT)
        }
        viewModel.refreshStorageAttribution(deep = true)
        return InputResult.HANDLED
    }

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(storageMediaSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(storageMediaSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
