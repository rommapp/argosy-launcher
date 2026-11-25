package com.nendo.argosy.domain.music

import java.text.Normalizer
import java.util.Locale

/**
 * Normalised forms of music text. [key] keeps punctuation and is the grouping key for names
 * (artists, tags); [words] also drops punctuation, keeping the text inside brackets as words.
 * Both are NFKC, case-folded with [Locale.ROOT] and whitespace-collapsed.
 */
object MusicNames {
    private val WHITESPACE = Regex("\\s+")
    private val NON_WORD = Regex("[^\\p{L}\\p{M}\\p{N}\\s]")

    fun key(name: String): String = fold(name).replace(WHITESPACE, " ").trim()

    fun words(text: String): String = fold(text).replace(NON_WORD, " ").replace(WHITESPACE, " ").trim()

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).uppercase(Locale.ROOT).lowercase(Locale.ROOT)
}
