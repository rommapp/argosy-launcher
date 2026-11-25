package com.nendo.argosy.ui.screens.musicstats

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class MusicStatsInputHandler(
    private val viewModel: MusicStatsViewModel,
    private val onExit: () -> Unit
) : InputHandler {

    private val state: MusicStatsUiState get() = viewModel.uiState.value

    private val boundary: InputResult get() = InputResult.handled(SoundType.BOUNDARY)

    private fun result(acted: Boolean): InputResult = if (acted) InputResult.HANDLED else boundary

    override fun onUp(): InputResult = result(viewModel.moveShelf(-1))

    override fun onDown(): InputResult = result(viewModel.moveShelf(1))

    override fun onLeft(): InputResult = result(viewModel.moveItem(-1))

    override fun onRight(): InputResult = result(viewModel.moveItem(1))

    override fun onConfirm(): InputResult {
        if (state.page.loadFailed) {
            viewModel.retry()
            return InputResult.HANDLED
        }
        return result(viewModel.playFocusedItem())
    }

    override fun onBack(): InputResult {
        onExit()
        return InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult = result(viewModel.playFocusedShelf())

    override fun onSelect(): InputResult = result(viewModel.openOptions())

    override fun onPrevTrigger(): InputResult = cycleTab(-1)

    override fun onNextTrigger(): InputResult = cycleTab(1)

    private fun cycleTab(delta: Int): InputResult {
        viewModel.cycleTab(delta)
        return InputResult.HANDLED
    }
}
