package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.BoxArtItem
import com.nendo.argosy.ui.screens.settings.sections.boxArtItemAtFocusIndex

internal class BoxArtSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult {
        viewModel.cycleBoxArtShape(-1)
        return InputResult.HANDLED
    }

    override fun onNextSection(): InputResult {
        viewModel.cycleBoxArtShape(1)
        return InputResult.HANDLED
    }

    override fun onPrevTrigger(): InputResult {
        viewModel.cyclePrevPreviewGame()
        return InputResult.HANDLED
    }

    override fun onNextTrigger(): InputResult {
        viewModel.cycleNextPreviewGame()
        return InputResult.HANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        when (boxArtItemAtFocusIndex(state.focusedIndex, state.display)) {
            BoxArtItem.Shape -> viewModel.cycleBoxArtShape(direction)
            BoxArtItem.CornerRadius -> viewModel.cycleBoxArtCornerRadius(direction)
            BoxArtItem.BorderThickness -> viewModel.cycleBoxArtBorderThickness(direction)
            BoxArtItem.BorderStyle -> viewModel.cycleBoxArtBorderStyle(direction)
            BoxArtItem.GlassTint -> viewModel.cycleGlassBorderTint(direction)
            BoxArtItem.GradientPresetItem -> viewModel.cycleGradientPreset(direction)
            BoxArtItem.GradientAdvanced -> viewModel.toggleGradientAdvancedMode()
            BoxArtItem.SampleGrid -> viewModel.cycleGradientSampleGrid(direction)
            BoxArtItem.SampleRadius -> viewModel.cycleGradientRadius(direction)
            BoxArtItem.MinSaturation -> viewModel.cycleGradientMinSaturation(direction)
            BoxArtItem.MinBrightness -> viewModel.cycleGradientMinValue(direction)
            BoxArtItem.HueDistance -> viewModel.cycleGradientHueDistance(direction)
            BoxArtItem.SaturationBoost -> viewModel.cycleGradientSaturationBump(direction)
            BoxArtItem.BrightnessClamp -> viewModel.cycleGradientValueClamp(direction)
            BoxArtItem.IndicatorStyle -> viewModel.cyclePlatformIndicatorStyle(direction)
            BoxArtItem.IndicatorContent -> viewModel.cyclePlatformIndicatorContent(direction)
            BoxArtItem.IconPos -> viewModel.cycleSystemIconPosition(direction)
            BoxArtItem.IconPad -> viewModel.cycleSystemIconPadding(direction)
            BoxArtItem.OuterEffect -> viewModel.cycleBoxArtOuterEffect(direction)
            BoxArtItem.OuterThickness -> viewModel.cycleBoxArtOuterEffectThickness(direction)
            BoxArtItem.GlowIntensity -> viewModel.cycleBoxArtGlowStrength(direction)
            BoxArtItem.GlowColor -> viewModel.cycleGlowColorMode(direction)
            BoxArtItem.InnerEffect -> viewModel.cycleBoxArtInnerEffect(direction)
            BoxArtItem.InnerThickness -> viewModel.cycleBoxArtInnerEffectThickness(direction)
            else -> {}
        }
        return InputResult.HANDLED
    }
}
