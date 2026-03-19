package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.PlatformDetailItem
import com.nendo.argosy.ui.screens.settings.sections.buildPlatformDetailFocusItems

internal class PlatformDetailSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycleItem(-1)
    override fun onRight(): InputResult = cycleItem(1)

    override fun onPrevSection(): InputResult = cyclePlatform(-1)
    override fun onNextSection(): InputResult = cyclePlatform(1)

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
        val focusItems = buildPlatformDetailFocusItems(config, state.platformDetail)
        val item = focusItems.getOrNull(state.focusedIndex) ?: return InputResult.UNHANDLED

        return when (item) {
            PlatformDetailItem.CORE -> {
                viewModel.cycleCoreForPlatform(config, direction)
                InputResult.HANDLED
            }
            PlatformDetailItem.EXTENSION -> {
                viewModel.cycleExtensionForPlatform(config, direction)
                InputResult.HANDLED
            }
            PlatformDetailItem.DISPLAY_TARGET -> {
                viewModel.cycleDisplayTarget(config, direction)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }
}
