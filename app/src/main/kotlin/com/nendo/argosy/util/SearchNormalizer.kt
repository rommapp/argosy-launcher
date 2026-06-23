package com.nendo.argosy.util

import java.text.Normalizer

object SearchNormalizer {
    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun normalize(text: String): String =
        DIACRITICS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
}
