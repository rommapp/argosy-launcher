package com.nendo.argosy.ui.input

class QuayPassAvatarCustomizerInputHandler(
    private val onSectionStep: (Int) -> Unit,
    private val onAdjustWithinSection: (Int) -> Unit,
    private val onConfirm: () -> Unit,
    private val onBack: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        onSectionStep(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        onSectionStep(1)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        onAdjustWithinSection(-1)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        onAdjustWithinSection(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        onConfirm()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onBack()
        return InputResult.HANDLED
    }
}
