package com.nendo.argosy.ui.input

class QuayPassDetailsInputHandler(
    private val getFocusIndex: () -> Int,
    private val getMaxIndex: () -> Int,
    private val onFocusChange: (Int) -> Unit,
    private val onConfirmAt: (Int) -> Unit,
    private val onBack: () -> Unit
) : InputHandler {

    override fun onLeft(): InputResult {
        val current = getFocusIndex()
        if (current > 0) onFocusChange(current - 1)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        val current = getFocusIndex()
        if (current < getMaxIndex()) onFocusChange(current + 1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        onConfirmAt(getFocusIndex())
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onBack()
        return InputResult.HANDLED
    }
}
