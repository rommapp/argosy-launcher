package com.nendo.argosy.domain.music

/**
 * The one splitter for artist credit tags, shared by every music source and the listen log.
 * Splits on `, ; / |`, the ideographic comma U+3001 and " feat. " / " ft. " (any case), never
 * inside `( )`, `[ ]` or full-width U+FF08/U+FF09 brackets, and never on `&`. Parts are
 * trimmed, blank parts dropped, and repeats (by [artistKey]) collapse onto the first spelling.
 */
object CreditSplitter {
    private val SEPARATORS = setOf(',', ';', '/', '|', Char(0x3001))
    private val OPENERS = setOf('(', '[', Char(0xFF08))
    private val CLOSERS = setOf(')', ']', Char(0xFF09))
    private val PHRASE_SEPARATORS = listOf(" feat. ", " ft. ")

    fun split(credit: String?): List<String> {
        val text = credit?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val phrase = if (depth == 0) phraseAt(text, i) else null
            when {
                phrase != null -> {
                    parts += current.toString()
                    current.clear()
                    i += phrase.length
                    continue
                }
                depth == 0 && c in SEPARATORS -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> {
                    if (c in OPENERS) depth++ else if (c in CLOSERS) depth = (depth - 1).coerceAtLeast(0)
                    current.append(c)
                }
            }
            i++
        }
        parts += current.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { artistKey(it) }
    }

    fun artistKey(name: String): String = MusicNames.key(name)

    private fun phraseAt(text: String, index: Int): String? =
        PHRASE_SEPARATORS.firstOrNull { text.regionMatches(index, it, 0, it.length, ignoreCase = true) }
}
