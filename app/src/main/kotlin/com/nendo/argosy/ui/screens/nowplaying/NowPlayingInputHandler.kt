package com.nendo.argosy.ui.screens.nowplaying

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.dualscreen.selectSwapsRoles
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class NowPlayingInputHandler(
    private val viewModel: NowPlayingViewModel,
    private val onBack: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult = vertical(-1)

    override fun onDown(): InputResult = vertical(1)

    private fun vertical(delta: Int): InputResult =
        if (viewModel.moveVertical(delta)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    private fun horizontal(delta: Int): InputResult =
        if (viewModel.moveTransport(delta)) InputResult.HANDLED else InputResult.handled(SoundType.SILENT)

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        if (!state.hasQueue) return InputResult.handled(SoundType.BOUNDARY)
        viewModel.confirm()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        if (viewModel.leaveQueue()) return InputResult.HANDLED
        onBack.invoke()
        return InputResult.HANDLED
    }

    override fun onPrevTrigger(): InputResult = result(viewModel.skipPrevious())

    override fun onNextTrigger(): InputResult = result(viewModel.skipNext())

    override fun onContextMenu(): InputResult = result(viewModel.focusQueue())

    override fun onPrevSection(): InputResult = bumperSeek(-1)

    override fun onNextSection(): InputResult = bumperSeek(1)

    private fun bumperSeek(direction: Int): InputResult =
        if (viewModel.quickNavigationEnabled.value) InputResult.UNHANDLED else result(viewModel.seekBy(direction))

    override fun onRightStickLeft(): InputResult = result(viewModel.seekBy(-1))

    override fun onRightStickRight(): InputResult = result(viewModel.seekBy(1))

    override fun onSelect(): InputResult {
        if (selectSwapsRoles()) return InputResult.UNHANDLED
        return openOptions()
    }

    override fun onLongConfirm(): InputResult = openOptions()

    private fun openOptions(): InputResult {
        viewModel.openOptions()
        return InputResult.handled(SoundType.OPEN_MODAL)
    }

    private fun result(acted: Boolean): InputResult =
        if (acted) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
}

class NowPlayingOptionsInputHandler(private val viewModel: NowPlayingViewModel) : InputHandler {

    private val focused: NowPlayingOption?
        get() = viewModel.uiState.value.navigation.options?.focused

    override fun onUp(): InputResult {
        viewModel.moveOptionFocus(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        viewModel.moveOptionFocus(1)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult = adjust(-1)

    override fun onRight(): InputResult = adjust(1)

    private fun adjust(delta: Int): InputResult = when (focused) {
        NowPlayingOption.STYLE -> {
            viewModel.cycleStyle(delta)
            InputResult.HANDLED
        }
        NowPlayingOption.LAYOUT -> {
            viewModel.cycleLayout(delta)
            InputResult.HANDLED
        }
        NowPlayingOption.SPIN -> {
            val target = delta > 0
            if (viewModel.uiState.value.prefs.spin == target) {
                InputResult.handled(SoundType.BOUNDARY)
            } else {
                viewModel.setSpin(target)
                InputResult.toggled(target)
            }
        }
        null -> InputResult.HANDLED
    }

    override fun onConfirm(): InputResult = when (val option = focused) {
        NowPlayingOption.STYLE, NowPlayingOption.LAYOUT -> {
            viewModel.requestPicker(option)
            InputResult.handled(SoundType.OPEN_MODAL)
        }
        NowPlayingOption.SPIN -> InputResult.toggled(viewModel.toggleSpin())
        null -> InputResult.HANDLED
    }

    override fun onBack(): InputResult = close()

    override fun onSelect(): InputResult = close()

    private fun close(): InputResult {
        viewModel.closeOptions()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }

    override fun onMenu(): InputResult = InputResult.HANDLED
    override fun onSecondaryAction(): InputResult = InputResult.HANDLED
    override fun onContextMenu(): InputResult = InputResult.HANDLED
    override fun onPrevSection(): InputResult = InputResult.HANDLED
    override fun onNextSection(): InputResult = InputResult.HANDLED
    override fun onPrevTrigger(): InputResult = InputResult.HANDLED
    override fun onNextTrigger(): InputResult = InputResult.HANDLED
    override fun onLeftStickClick(): InputResult = InputResult.HANDLED
    override fun onRightStickClick(): InputResult = InputResult.HANDLED
    override fun onLongConfirm(): InputResult = InputResult.HANDLED
    override fun onLongSelect(): InputResult = InputResult.HANDLED
}
