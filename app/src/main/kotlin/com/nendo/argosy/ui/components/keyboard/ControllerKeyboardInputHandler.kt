package com.nendo.argosy.ui.components.keyboard

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

internal class ControllerKeyboardInputHandler(
    private val engine: KeyboardEngine,
    private val onFeedback: (KeyboardFeedback) -> Unit,
    private val onCycleLayout: () -> KeyboardFeedback
) : InputHandler {

    private fun run(feedback: KeyboardFeedback): InputResult {
        onFeedback(feedback)
        return feedback.toInputResult()
    }

    override fun onUp(): InputResult = run(engine.move(0, -1))
    override fun onDown(): InputResult = run(engine.move(0, 1))
    override fun onLeft(): InputResult = run(engine.move(-1, 0))
    override fun onRight(): InputResult = run(engine.move(1, 0))
    override fun onConfirm(): InputResult = run(engine.activate())
    override fun onLongConfirm(): InputResult = run(engine.longActivate())
    override fun onBack(): InputResult = run(engine.back())
    override fun onContextMenu(): InputResult = run(engine.backspace())
    override fun onSecondaryAction(): InputResult = run(engine.space())
    override fun onLeftStickClick(): InputResult = run(engine.shiftClick())
    override fun onPrevTrigger(): InputResult = run(engine.toggleSymbols())
    override fun onNextTrigger(): InputResult = run(engine.pasteClipboard())
    override fun onSelect(): InputResult = onCycleLayout().toInputResult()
    override fun onMenu(): InputResult = run(engine.submit())
    override fun onPrevSection(): InputResult = run(engine.moveCaret(-1))
    override fun onNextSection(): InputResult = run(engine.moveCaret(1))
    override fun onRightStickLeft(): InputResult = run(engine.moveCaret(-1))
    override fun onRightStickRight(): InputResult = run(engine.moveCaret(1))
    override fun onRightStickUp(): InputResult = InputResult.HANDLED
    override fun onRightStickDown(): InputResult = InputResult.HANDLED
}

internal fun KeyboardFeedback.toInputResult(): InputResult = when (this) {
    KeyboardFeedback.NAVIGATE -> InputResult.handled(SoundType.NAVIGATE)
    KeyboardFeedback.SELECT, KeyboardFeedback.SUBMITTED -> InputResult.handled(SoundType.SELECT)
    KeyboardFeedback.BACK -> InputResult.handled(SoundType.BACK)
    KeyboardFeedback.TOGGLE, KeyboardFeedback.LAYOUT_REQUESTED -> InputResult.handled(SoundType.TOGGLE)
    KeyboardFeedback.TOGGLE_ON -> InputResult.toggled(enabled = true)
    KeyboardFeedback.TOGGLE_OFF -> InputResult.toggled(enabled = false)
    KeyboardFeedback.BOUNDARY -> InputResult(true, SoundType.BOUNDARY, HapticPattern.BOUNDARY_HIT)
    KeyboardFeedback.ERROR -> InputResult(true, SoundType.ERROR, HapticPattern.ERROR)
    KeyboardFeedback.CLOSED -> InputResult.handled(SoundType.CLOSE_MODAL)
    KeyboardFeedback.NONE -> InputResult.handled(SoundType.SILENT)
}
