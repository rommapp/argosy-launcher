package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class MusicInputHandler(
    private val viewModel: MusicViewModel,
    private val onExit: () -> Unit,
    private val onOpenSettings: () -> Unit
) : InputHandler {

    private val state: MusicUiState get() = viewModel.uiState.value

    private val boundary: InputResult get() = InputResult.handled(SoundType.BOUNDARY)

    private fun result(moved: Boolean): InputResult = if (moved) InputResult.HANDLED else boundary

    override fun onLeft(): InputResult = when {
        state.contents != null -> result(viewModel.moveAlbum(-1))
        else -> result(viewModel.moveColumnFocus(-1))
    }

    override fun onRight(): InputResult = when {
        state.contents != null -> result(viewModel.moveAlbum(1))
        else -> result(viewModel.moveColumnFocus(1))
    }

    override fun onUp(): InputResult =
        if (state.contents != null) result(viewModel.moveTrack(-1)) else result(viewModel.moveStack(-1))

    override fun onDown(): InputResult =
        if (state.contents != null) result(viewModel.moveTrack(1)) else result(viewModel.moveStack(1))

    override fun onConfirm(): InputResult {
        val current = state
        val contents = current.contents
        return when {
            contents != null && contents.loadFailed -> {
                viewModel.retryContents()
                InputResult.HANDLED
            }
            contents != null -> if (contents.focusedTrack != null) {
                viewModel.playFocusedTrack()
                InputResult.HANDLED
            } else {
                boundary
            }
            current.hasNoSources -> {
                onOpenSettings()
                InputResult.HANDLED
            }
            current.loadFailed -> {
                viewModel.retry()
                InputResult.HANDLED
            }
            current.focusedColumn != null -> {
                viewModel.openContents()
                InputResult.HANDLED
            }
            else -> boundary
        }
    }

    override fun onBack(): InputResult {
        if (state.contents != null) {
            viewModel.closeContents()
        } else {
            onExit()
        }
        return InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult {
        val current = state
        val contents = current.contents
        return when {
            contents != null -> if (contents.tracks.isNotEmpty()) {
                viewModel.playContentsAlbum()
                InputResult.HANDLED
            } else {
                boundary
            }
            current.focusedColumn != null -> {
                viewModel.playFocusedColumn()
                InputResult.HANDLED
            }
            else -> boundary
        }
    }

    override fun onContextMenu(): InputResult {
        val current = state
        if (current.contents != null || current.hasNoSources || current.sources == null) return boundary
        viewModel.actions.openFilter()
        return InputResult.HANDLED
    }

    override fun onSelect(): InputResult {
        val current = state
        val contents = current.contents
        return when {
            contents != null -> if (contents.focusedTrack != null) {
                viewModel.openFocusedTrackOptions()
                InputResult.HANDLED
            } else {
                boundary
            }
            current.focusedColumn != null -> {
                viewModel.openFocusedColumnOptions()
                InputResult.HANDLED
            }
            else -> boundary
        }
    }

    override fun onPrevSection(): InputResult = bumperJump(-1)

    override fun onNextSection(): InputResult = bumperJump(1)

    private fun bumperJump(delta: Int): InputResult =
        if (viewModel.quickNavigationEnabled.value) InputResult.UNHANDLED else result(viewModel.jumpSection(delta))

    override fun onRightStickUp(): InputResult = result(viewModel.jumpSection(-1))

    override fun onRightStickDown(): InputResult = result(viewModel.jumpSection(1))

    override fun onRightStickLeft(): InputResult = result(viewModel.jumpSection(-1))

    override fun onRightStickRight(): InputResult = result(viewModel.jumpSection(1))

    override fun onPrevTrigger(): InputResult = cycleGrouping(-1)

    override fun onNextTrigger(): InputResult = cycleGrouping(1)

    private fun cycleGrouping(delta: Int): InputResult {
        val current = state
        if (current.contents != null || current.hasNoSources || current.sources == null) return boundary
        viewModel.cycleGrouping(delta)
        return InputResult.HANDLED
    }
}
