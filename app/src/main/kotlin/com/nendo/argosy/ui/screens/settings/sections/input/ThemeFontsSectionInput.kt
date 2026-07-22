package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.FontSlot
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.ThemeFontsItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeFontsLayoutState
import com.nendo.argosy.ui.screens.settings.sections.themeFontsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeFontsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeFontsSections

internal class ThemeFontsSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutState() = ThemeFontsLayoutState.from(viewModel.uiState.value)

    override fun onUp(): InputResult {
        return if (viewModel.moveFocusWrapped(-1, themeFontsMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocusWrapped(1, themeFontsMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = adjust(-1)

    override fun onRight(): InputResult = adjust(1)

    private fun adjust(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val step = direction * SettingsInputHandler.FONT_SCALE_STEP
        return when (themeFontsItemAtFocusIndex(state.focusedIndex, layoutState())) {
            ThemeFontsItem.DisplayScale -> { viewModel.adjustFontScale(FontSlot.DISPLAY, step); InputResult.HANDLED }
            ThemeFontsItem.BodyScale -> { viewModel.adjustFontScale(FontSlot.BODY, step); InputResult.HANDLED }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(themeFontsSections(layoutState()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(themeFontsSections(layoutState()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
