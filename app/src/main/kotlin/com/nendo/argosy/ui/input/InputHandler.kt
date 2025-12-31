package com.nendo.argosy.ui.input

data class InputResult(
    val handled: Boolean,
    val soundOverride: SoundType? = null
) {
    companion object {
        val UNHANDLED = InputResult(handled = false)
        val HANDLED = InputResult(handled = true)

        fun handled(soundOverride: SoundType? = null) = InputResult(
            handled = true,
            soundOverride = soundOverride
        )
    }
}

interface InputHandler {
    fun onUp(): InputResult = InputResult.UNHANDLED
    fun onDown(): InputResult = InputResult.UNHANDLED
    fun onLeft(): InputResult = InputResult.UNHANDLED
    fun onRight(): InputResult = InputResult.UNHANDLED
    fun onConfirm(): InputResult = InputResult.UNHANDLED
    fun onBack(): InputResult = InputResult.UNHANDLED
    fun onMenu(): InputResult = InputResult.UNHANDLED
    fun onSecondaryAction(): InputResult = InputResult.UNHANDLED
    fun onContextMenu(): InputResult = InputResult.UNHANDLED
    fun onPrevSection(): InputResult = InputResult.UNHANDLED
    fun onNextSection(): InputResult = InputResult.UNHANDLED
    fun onSelect(): InputResult = InputResult.UNHANDLED
    fun onLeftStickClick(): InputResult = InputResult.UNHANDLED
    fun onRightStickClick(): InputResult = InputResult.UNHANDLED
}
