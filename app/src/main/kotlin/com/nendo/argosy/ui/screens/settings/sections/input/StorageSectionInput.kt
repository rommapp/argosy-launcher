package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.StorageItem
import com.nendo.argosy.ui.screens.settings.sections.StorageLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createStorageLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.storageItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageSections

internal class StorageSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutInfo(): StorageLayoutInfo {
        val state = viewModel.uiState.value
        return createStorageLayoutInfo()
    }

    override fun onUp(): InputResult {
        val info = layoutInfo()
        viewModel.moveFocusWrapped(-1, info.layout.maxFocusIndex(info.state))
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        val info = layoutInfo()
        viewModel.moveFocusWrapped(1, info.layout.maxFocusIndex(info.state))
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult {
        val info = layoutInfo()
        if (viewModel.jumpToPrevSection(storageSections(info))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val info = layoutInfo()
        if (viewModel.jumpToNextSection(storageSections(info))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val info = layoutInfo()
        when (storageItemAtFocusIndex(state.focusedIndex, info)) {
            StorageItem.MaxDownloads -> { viewModel.adjustMaxConcurrentDownloads(direction); return InputResult.HANDLED }
            StorageItem.Threshold -> { viewModel.cycleInstantDownloadThreshold(direction); return InputResult.HANDLED }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
