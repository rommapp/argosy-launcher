package com.nendo.argosy.ui.input

class QuayPassAvatarCustomizerInputHandler(
    private val onSectionStep: (Int) -> Unit,
    private val onAdjustWithinSection: (Int) -> Unit,
    private val onPageStep: (Int) -> Unit,
    private val onConfirmPressed: () -> Unit,
    private val onBackPressed: () -> Unit
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

    override fun onPrevSection(): InputResult {
        onPageStep(-1)
        return InputResult.HANDLED
    }

    override fun onNextSection(): InputResult {
        onPageStep(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        onConfirmPressed()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onBackPressed()
        return InputResult.HANDLED
    }
}
