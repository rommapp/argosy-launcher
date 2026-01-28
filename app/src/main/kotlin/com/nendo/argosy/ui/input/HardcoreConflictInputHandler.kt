package com.nendo.argosy.ui.input

class HardcoreConflictInputHandler(
    private val getFocusIndex: () -> Int,
    private val onFocusChange: (Int) -> Unit,
    private val onKeepHardcore: () -> Unit,
    private val onDowngradeToCasual: () -> Unit,
    private val onKeepLocal: () -> Unit
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
        if (currentIndex < 2) {
            onFocusChange(currentIndex + 1)
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        when (getFocusIndex()) {
            0 -> onKeepHardcore()
            1 -> onDowngradeToCasual()
            2 -> onKeepLocal()
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onKeepLocal()
        return InputResult.HANDLED
    }
}
