package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.PlatformDetailItem
import com.nendo.argosy.ui.screens.settings.sections.PlatformDetailVisibility
import com.nendo.argosy.ui.screens.settings.sections.platformDetailItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.platformDetailSections

internal class PlatformDetailSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycleItem(-1)
    override fun onRight(): InputResult = cycleItem(1)

    // L1/R1: jump sections within the detail page
    override fun onPrevSection(): InputResult = jumpSection(-1)
    override fun onNextSection(): InputResult = jumpSection(1)

    // L2/R2: cycle platforms
    override fun onPrevTrigger(): InputResult = cyclePlatform(-1)
    override fun onNextTrigger(): InputResult = cyclePlatform(1)

    private fun jumpSection(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val config = state.emulators.platforms.getOrNull(state.platformDetail.platformIndex)
            ?: return InputResult.UNHANDLED
        val sections = platformDetailSections(config, state.platformDetail)
        val currentFocus = state.focusedIndex

        val target = if (direction > 0) {
            sections.firstOrNull { it.focusStartIndex > currentFocus }
        } else {
            sections.lastOrNull { it.focusStartIndex < currentFocus }
        }

        if (target != null) {
            viewModel.setFocusIndex(target.focusStartIndex)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cyclePlatform(direction: Int): InputResult {
        val platforms = viewModel.uiState.value.emulators.platforms
        if (platforms.size <= 1) return InputResult.UNHANDLED
        val currentIndex = viewModel.uiState.value.platformDetail.platformIndex
        val newIndex = currentIndex + direction
        if (newIndex < 0 || newIndex >= platforms.size) {
            viewModel.hapticManager.vibrate(com.nendo.argosy.ui.input.HapticPattern.BOUNDARY_HIT)
            return InputResult.HANDLED
        }
        viewModel.cyclePlatformDetail(direction)
        return InputResult.HANDLED
    }

    private fun cycleItem(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val config = state.emulators.platforms.getOrNull(state.platformDetail.platformIndex)
            ?: return InputResult.UNHANDLED
        val item = platformDetailItemAtFocusIndex(state.focusedIndex, config, state.platformDetail)
            ?: return InputResult.UNHANDLED

        return when (item) {
            PlatformDetailItem.Core -> {
                viewModel.cycleCoreForPlatform(config, direction)
                InputResult.HANDLED
            }
            PlatformDetailItem.Extension -> {
                viewModel.cycleExtensionForPlatform(config, direction)
                InputResult.HANDLED
            }
            PlatformDetailItem.DisplayTarget -> {
                viewModel.cycleDisplayTarget(config, direction)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }
}
