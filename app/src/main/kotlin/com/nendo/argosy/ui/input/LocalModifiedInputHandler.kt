package com.nendo.argosy.ui.input

class LocalModifiedInputHandler(
    private val getFocusIndex: () -> Int,
    private val onFocusChange: (Int) -> Unit,
    private val onKeepLocal: () -> Unit,
    private val onRestoreSelected: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        val currentIndex = getFocusIndex()
        if (currentIndex > 0) {
            onFocusChange(currentIndex - 1)
        }
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        val currentIndex = getFocusIndex()
        if (currentIndex < 1) {
            onFocusChange(currentIndex + 1)
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        when (getFocusIndex()) {
            0 -> onKeepLocal()
            1 -> onRestoreSelected()
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onKeepLocal()
        return InputResult.HANDLED
    }

    override fun onMenu(): InputResult = InputResult.HANDLED
    override fun onSelect(): InputResult = InputResult.HANDLED
    override fun onLeft(): InputResult = InputResult.HANDLED
    override fun onRight(): InputResult = InputResult.HANDLED
    override fun onPrevSection(): InputResult = InputResult.HANDLED
    override fun onNextSection(): InputResult = InputResult.HANDLED
    override fun onPrevTrigger(): InputResult = InputResult.HANDLED
    override fun onNextTrigger(): InputResult = InputResult.HANDLED
}
