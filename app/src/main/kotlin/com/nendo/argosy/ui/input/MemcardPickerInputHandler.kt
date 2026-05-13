package com.nendo.argosy.ui.input

import com.nendo.argosy.data.sync.platform.MemcardInfo

class MemcardPickerInputHandler(
    private val getCards: () -> List<MemcardInfo>,
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
        val cards = getCards()
        val currentIndex = getFocusIndex()
        if (currentIndex < cards.size - 1) {
            onFocusChange(currentIndex + 1)
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val cards = getCards()
        val index = getFocusIndex()
        val card = cards.getOrNull(index)
        if (card != null) {
            onSelect(card.path)
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onDismiss()
        return InputResult.HANDLED
    }
}
