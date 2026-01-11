package com.nendo.argosy.ui.input

import com.nendo.argosy.data.emulator.DiscOption

class DiscPickerInputHandler(
    private val getDiscs: () -> List<DiscOption>,
    private val getFocusIndex: () -> Int,
    private val onFocusChange: (Int) -> Unit,
    private val onSelect: (String) -> Unit,
    private val onDismiss: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        val currentIndex = getFocusIndex()
        if (currentIndex > 0) {
            onFocusChange(currentIndex - 1)
        }
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        val discs = getDiscs()
        val currentIndex = getFocusIndex()
        if (currentIndex < discs.size - 1) {
            onFocusChange(currentIndex + 1)
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val discs = getDiscs()
        val index = getFocusIndex()
        val disc = discs.getOrNull(index)
        if (disc != null) {
            onSelect(disc.filePath)
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onDismiss()
        return InputResult.HANDLED
    }
}
