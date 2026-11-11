package com.nendo.argosy.ui.components.keyboard

data class ComposerEdit(val text: String, val caret: Int)

/**
 * Folds typed keys into composed characters in place. [input] returns null for text the composer
 * does not compose, after committing any composition, and the caller inserts that text itself.
 * [backspace] returns null when nothing is being composed at [caret], and the caller deletes as usual.
 */
interface KeyboardComposer {
    fun input(text: String, caret: Int, jamo: String): ComposerEdit?
    fun backspace(text: String, caret: Int): ComposerEdit?
    fun commit()
}

internal fun keyboardComposerFor(composerId: String): KeyboardComposer? = when (composerId) {
    HangulComposer.COMPOSER_ID -> HangulComposer()
    else -> null
}
