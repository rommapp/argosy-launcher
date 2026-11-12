package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

/**
 * Setup's gamepad and remote input. Buttons setup gives no meaning are swallowed so the drawer,
 * nav ring and quick menu never open over it.
 */
class SetupInputHandler(private val viewModel: SetupViewModel) : InputHandler {

    override fun onUp(): InputResult = viewModel.move(SetupMove.UP)

    override fun onDown(): InputResult = viewModel.move(SetupMove.DOWN)

    override fun onLeft(): InputResult = viewModel.move(SetupMove.LEFT)

    override fun onRight(): InputResult = viewModel.move(SetupMove.RIGHT)

    override fun onConfirm(): InputResult =
        if (InputDispatcher.currentIsRepeat) InputResult.HANDLED else viewModel.confirm()

    override fun onBack(): InputResult = viewModel.back()

    override fun onContextMenu(): InputResult = viewModel.toggleAllPlatforms()

    override fun onMenu() = InputResult.HANDLED
    override fun onSelect() = InputResult.HANDLED
    override fun onSecondaryAction() = InputResult.HANDLED
    override fun onPrevSection() = InputResult.HANDLED
    override fun onNextSection() = InputResult.HANDLED
    override fun onPrevTrigger() = InputResult.HANDLED
    override fun onNextTrigger() = InputResult.HANDLED
    override fun onLeftStickClick() = InputResult.HANDLED
    override fun onRightStickClick() = InputResult.HANDLED
    override fun onLongConfirm() = InputResult.HANDLED
    override fun onLongSelect() = InputResult.HANDLED
}
