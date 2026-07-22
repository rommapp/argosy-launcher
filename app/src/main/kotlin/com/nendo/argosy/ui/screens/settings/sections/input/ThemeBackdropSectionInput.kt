package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.DIRECTION_STEP_FINE_DEGREES
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.delegates.DisplaySettingsDelegate
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.ThemeBackdropItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeBackdropLayoutState
import com.nendo.argosy.ui.screens.settings.sections.themeBackdropItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeBackdropMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeBackdropSections
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

internal class ThemeBackdropSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutState() = ThemeBackdropLayoutState.from(viewModel.uiState.value)

    override fun onUp(): InputResult {
        return if (viewModel.moveFocusWrapped(-1, themeBackdropMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocusWrapped(1, themeBackdropMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = adjust(-1)

    override fun onRight(): InputResult = adjust(1)

    private fun adjust(direction: Int): InputResult {
        val state = viewModel.uiState.value
        return when (themeBackdropItemAtFocusIndex(state.focusedIndex, layoutState())) {
            ThemeBackdropItem.Enabled -> { viewModel.setBackdropEnabled(direction > 0); InputResult.HANDLED }
            ThemeBackdropItem.Preset -> { viewModel.cycleBackdropPreset(direction); InputResult.HANDLED }
            ThemeBackdropItem.Density -> {
                viewModel.adjustBackdropCellSize(direction * ComponentDefaults.SurfaceBackdrop.cellSizeStepDp)
                InputResult.HANDLED
            }
            ThemeBackdropItem.Scatter -> {
                viewModel.adjustBackdropScatter(direction * SettingsInputHandler.SLIDER_STEP)
                InputResult.HANDLED
            }
            ThemeBackdropItem.ScaleJitter -> {
                viewModel.adjustBackdropScaleJitter(direction * SettingsInputHandler.SLIDER_STEP)
                InputResult.HANDLED
            }
            ThemeBackdropItem.Strength -> {
                viewModel.adjustBackdropStrength(direction * SettingsInputHandler.SLIDER_STEP)
                InputResult.HANDLED
            }
            ThemeBackdropItem.EdgeLines -> { viewModel.cycleBackdropEdgeStyle(direction); InputResult.HANDLED }
            ThemeBackdropItem.CornerIcons -> { viewModel.cycleBackdropVertexIcons(direction); InputResult.HANDLED }
            ThemeBackdropItem.Motion -> { viewModel.cycleBackdropMotion(direction); InputResult.HANDLED }
            ThemeBackdropItem.Speed -> {
                viewModel.adjustBackdropMotionSpeed(direction * DisplaySettingsDelegate.MOTION_SPEED_STEP)
                InputResult.HANDLED
            }
            ThemeBackdropItem.Direction -> {
                viewModel.adjustBackdropDriftAngle(direction * DIRECTION_STEP_FINE_DEGREES)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(themeBackdropSections(layoutState()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(themeBackdropSections(layoutState()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
