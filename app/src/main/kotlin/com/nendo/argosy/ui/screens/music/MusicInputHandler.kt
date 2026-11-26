package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.dualscreen.selectSwapsRoles
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

    override fun onUp(): InputResult = vertical(-1)

    override fun onDown(): InputResult = vertical(1)

    private fun vertical(delta: Int): InputResult {
        val contents = state.contents
        return when {
            contents == null -> result(viewModel.moveStack(delta))
            contents.isMoving -> result(viewModel.playlistActions.moveFocused(delta))
            else -> result(viewModel.moveTrack(delta))
        }
    }

    private val moving: Boolean get() = state.contents?.isMoving == true

    override fun onConfirm(): InputResult {
        val current = state
        val contents = current.contents
        if (moving) {
            viewModel.playlistActions.endMove()
            return InputResult.HANDLED
        }
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
        when {
            moving -> viewModel.playlistActions.endMove()
            state.contents != null -> viewModel.closeContents()
            else -> onExit()
        }
        return InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult {
        val current = state
        val contents = current.contents
        return when {
            moving -> boundary
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
        val contents = current.contents
        if (contents != null) {
            val plays = !contents.isMoving && contents.canPlayColumn && viewModel.playContentsColumn()
            return if (plays) InputResult.HANDLED else boundary
        }
        if (current.hasNoSources || current.sources == null) return boundary
        viewModel.actions.openFilter()
        return InputResult.HANDLED
    }

    override fun onSelect(): InputResult {
        if (moving) return boundary
        if (selectSwapsRoles()) return InputResult.UNHANDLED
        return openOptions()
    }

    override fun onLongConfirm(): InputResult = if (moving) boundary else openOptions()

    override fun onLongSelect(): InputResult = if (moving) InputResult.HANDLED else InputResult.UNHANDLED

    private fun openOptions(): InputResult {
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
