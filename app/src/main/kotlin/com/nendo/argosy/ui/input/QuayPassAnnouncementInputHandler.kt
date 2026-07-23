package com.nendo.argosy.ui.input

class QuayPassAnnouncementInputHandler(
    private val getFocusIndex: () -> Int,
    private val onFocusChange: (Int) -> Unit,
    private val onLearnMore: () -> Unit,
    private val onDismiss: () -> Unit
) : InputHandler {

    override fun onLeft(): InputResult {
        onFocusChange(0)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        onFocusChange(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        when (getFocusIndex()) {
            0 -> onDismiss()
            else -> onLearnMore()
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onDismiss()
        return InputResult.HANDLED
    }
}
