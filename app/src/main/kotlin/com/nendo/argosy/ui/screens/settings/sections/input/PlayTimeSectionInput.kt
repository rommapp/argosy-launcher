package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.PlayTimeFigure
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.PlayTimeItem
import com.nendo.argosy.ui.screens.settings.sections.PlayTimeLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createPlayTimeLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.playTimeFigureOf
import com.nendo.argosy.ui.screens.settings.sections.playTimeHorizontalScrubOf
import com.nendo.argosy.ui.screens.settings.sections.playTimeItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.playTimeMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.playTimeScrubStep
import com.nendo.argosy.ui.screens.settings.sections.playTimeSections
import com.nendo.argosy.ui.screens.settings.sections.playTimeVerticalScrubOf

internal class PlayTimeSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutInfo(): PlayTimeLayoutInfo =
        createPlayTimeLayoutInfo(viewModel.uiState.value)

    private fun focusedItem(): PlayTimeItem? {
        val state = viewModel.uiState.value
        return playTimeItemAtFocusIndex(state.focusedIndex, layoutInfo())
    }

    private fun engagedFigure(): PlayTimeFigure? {
        val engaged = viewModel.uiState.value.playTime.engagedFigure ?: return null
        return engaged.takeIf { it == playTimeFigureOf(focusedItem()) }
    }

    private fun disengage() {
        if (viewModel.uiState.value.playTime.engagedFigure != null) {
            viewModel.setPlayTimeEngagedFigure(null)
        }
    }

    override fun onUp(): InputResult = vertical(-1)

    override fun onDown(): InputResult = vertical(1)

    private fun vertical(direction: Int): InputResult {
        engagedFigure()?.let { figure ->
            if (figure == PlayTimeFigure.MOSAIC) {
                viewModel.movePlayTimeMosaic(dx = 0, dy = direction)
                return InputResult.HANDLED
            }
            val scrub = playTimeVerticalScrubOf(figure)
            viewModel.scrubPlayTime(scrub, direction, playTimeScrubStep(scrub, horizontal = false))
            return InputResult.HANDLED
        }
        disengage()
        return if (viewModel.moveFocusWrapped(direction, playTimeMaxFocusIndex(layoutInfo()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    private fun horizontal(direction: Int): InputResult {
        val item = focusedItem()
        val figure = playTimeFigureOf(item)
        if (figure != null && figure != viewModel.uiState.value.playTime.engagedFigure) {
            return InputResult.handled(SoundType.BOUNDARY)
        }
        if (figure == PlayTimeFigure.MOSAIC) {
            viewModel.movePlayTimeMosaic(dx = direction, dy = 0)
            return InputResult.HANDLED
        }
        playTimeHorizontalScrubOf(item)?.let { scrub ->
            viewModel.scrubPlayTime(scrub, direction, playTimeScrubStep(scrub, horizontal = true))
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult {
        disengage()
        if (viewModel.jumpToPrevSection(playTimeSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        disengage()
        if (viewModel.jumpToNextSection(playTimeSections(layoutInfo()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        if (state.playTime.isPulling || state.server.connectionStatus != ConnectionStatus.ONLINE) {
            return InputResult.handled(SoundType.SILENT)
        }
        viewModel.refreshPlaySessionsFromRomm()
        return InputResult.HANDLED
    }
}
