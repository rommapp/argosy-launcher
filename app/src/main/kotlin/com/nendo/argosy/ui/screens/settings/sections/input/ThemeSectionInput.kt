package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.ThemeItem
import com.nendo.argosy.ui.screens.settings.sections.themeItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeSections

internal class ThemeSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onUp(): InputResult {
        return if (viewModel.moveFocusWrapped(-1, themeMaxFocusIndex())) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocusWrapped(1, themeMaxFocusIndex())) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onConfirm(): InputResult {
        return when (themeItemAtFocusIndex(viewModel.uiState.value.focusedIndex)) {
            ThemeItem.AccentColor -> { viewModel.resetToDefaultColor(); InputResult.HANDLED }
            ThemeItem.SecondaryColor -> { viewModel.resetToDefaultSecondaryColor(); InputResult.HANDLED }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(themeSections())) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(themeSections())) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val hueStep = SettingsInputHandler.HUE_STEP
        when (themeItemAtFocusIndex(state.focusedIndex)) {
            ThemeItem.Mode -> { viewModel.cycleThemeMode(direction); return InputResult.HANDLED }
            ThemeItem.AccentColor -> { viewModel.adjustHue(direction * hueStep); return InputResult.HANDLED }
            ThemeItem.SecondaryColor -> { viewModel.adjustSecondaryHue(direction * hueStep); return InputResult.HANDLED }
            ThemeItem.TintBleed -> { viewModel.adjustSurfaceTintBleed(direction * SettingsInputHandler.SLIDER_STEP); return InputResult.HANDLED }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
