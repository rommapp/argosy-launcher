package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
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

    private fun layoutInfo(): StorageLayoutInfo =
        createStorageLayoutInfo(viewModel.uiState.value)

    override fun onUp(): InputResult {
        val info = layoutInfo()
        return if (viewModel.moveFocusWrapped(-1, info.layout.maxFocusIndex(info.state))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        val info = layoutInfo()
        return if (viewModel.moveFocusWrapped(1, info.layout.maxFocusIndex(info.state))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(storageSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(storageSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onContextMenu(): InputResult {
        if (viewModel.uiState.value.attribution.isRefreshing) {
            return InputResult.handled(SoundType.SILENT)
        }
        viewModel.refreshStorageAttribution()
        return InputResult.HANDLED
    }

    override fun onLongConfirm(): InputResult {
        val state = viewModel.uiState.value
        if (storageItemAtFocusIndex(state.focusedIndex, layoutInfo()) != StorageItem.RecomputeRow) {
            return InputResult.UNHANDLED
        }
        if (state.attribution.isRefreshing) {
            return InputResult.handled(SoundType.SILENT)
        }
        viewModel.refreshStorageAttribution(deep = true)
        return InputResult.HANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        when (storageItemAtFocusIndex(state.focusedIndex, layoutInfo())) {
            StorageItem.MaxDownloads -> {
                viewModel.adjustMaxConcurrentDownloads(direction)
                return InputResult.HANDLED
            }
            StorageItem.Threshold -> {
                viewModel.cycleInstantDownloadThreshold(direction)
                return InputResult.HANDLED
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
