package com.nendo.argosy.ui.components.markdown

data class NotesInline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false
)

sealed interface NotesBlock {
    data class Heading(val level: Int, val inlines: List<NotesInline>) : NotesBlock
    data class Bullet(val depth: Int, val inlines: List<NotesInline>) : NotesBlock
    data class Numbered(val number: String, val depth: Int, val inlines: List<NotesInline>) : NotesBlock
    data class Paragraph(val inlines: List<NotesInline>) : NotesBlock
    data class Code(val text: String) : NotesBlock
    data object Blank : NotesBlock
}

/**
 * Release-note markdown as blocks: headings, bullet and numbered lists, paragraphs and fenced
 * code, with bold, italics and inline code inside. Links and images keep only their text.
 */
object ReleaseNotesParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val QUOTE = Regex("^\\s*>\\s?(.*)$")
    private const val FENCE = "```"
    private const val INDENT_PER_LEVEL = 2

    fun parse(body: String): List<NotesBlock> {
        val blocks = mutableListOf<NotesBlock>()
        var inFence = false
        body.replace("\r\n", "\n").lines().forEach { raw ->
            if (raw.trimStart().startsWith(FENCE)) {
                inFence = !inFence
                return@forEach
            }
            if (inFence) {
                blocks += NotesBlock.Code(raw)
                return@forEach
            }
            val block = parseLine(raw)
            if (block == NotesBlock.Blank && (blocks.isEmpty() || blocks.last() == NotesBlock.Blank)) return@forEach
            blocks += block
        }
        while (blocks.lastOrNull() == NotesBlock.Blank) blocks.removeAt(blocks.lastIndex)
        return blocks
    }

    private fun parseLine(raw: String): NotesBlock {
        if (raw.isBlank()) return NotesBlock.Blank
        if (RULE.matches(raw)) return NotesBlock.Blank
        HEADING.matchEntire(raw.trim())?.let { match ->
            return NotesBlock.Heading(match.groupValues[1].length, inlines(match.groupValues[2]))
        }
        BULLET.matchEntire(raw)?.let { match ->
            return NotesBlock.Bullet(depthOf(match.groupValues[1]), inlines(match.groupValues[2]))
        }
        NUMBERED.matchEntire(raw)?.let { match ->
            return NotesBlock.Numbered(match.groupValues[2], depthOf(match.groupValues[1]), inlines(match.groupValues[3]))
        }
        QUOTE.matchEntire(raw)?.let { match ->
            return NotesBlock.Paragraph(inlines(match.groupValues[1]))
        }
        return NotesBlock.Paragraph(inlines(raw.trim()))
    }

    private fun depthOf(indent: String): Int =
        indent.replace("\t", " ".repeat(INDENT_PER_LEVEL)).length / INDENT_PER_LEVEL

    fun inlines(text: String): List<NotesInline> {
        val runs = mutableListOf<NotesInline>()
        val current = StringBuilder()
        var bold = false
        var italic = false

        fun flush() {
            if (current.isNotEmpty()) {
                runs += NotesInline(current.toString(), bold = bold, italic = italic)
                current.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            when {
                c == '\\' && next != null && next in ESCAPABLE -> {
                    current.append(next)
                    i += 2
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) {
                        current.append(c)
                        i++
                    } else {
                        flush()
                        runs += NotesInline(text.substring(i + 1, end), bold = bold, italic = italic, code = true)
                        i = end + 1
                    }
                }
                c == '!' && next == '[' -> {
                    val link = linkAt(text, i + 1)
                    if (link == null) {
                        current.append(c)
                        i++
                    } else {
                        current.append(link.first)
                        i = link.second
                    }
                }
                c == '[' -> {
                    val link = linkAt(text, i)
                    if (link == null) {
                        current.append(c)
                        i++
                    } else {
                        current.append(link.first)
                        i = link.second
                    }
                }
                (c == '*' || c == '_') && next == c -> {
                    flush()
                    bold = !bold
                    i += 2
                }
                c == '*' && isEmphasisMarker(text, i) -> {
                    flush()
                    italic = !italic
                    i++
                }
                c == '_' && isUnderscoreMarker(text, i) -> {
                    flush()
                    italic = !italic
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        flush()
        return runs
    }

    private fun linkAt(text: String, open: Int): Pair<String, Int>? {
        val close = text.indexOf("](", open + 1)
        if (close < 0) return null
        val end = text.indexOf(')', close + 2)
        if (end < 0) return null
        return text.substring(open + 1, close) to end + 1
    }

    private fun isEmphasisMarker(text: String, index: Int): Boolean {
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + 1)
        val opens = after != null && !after.isWhitespace()
        val closes = before != null && !before.isWhitespace()
        return opens || closes
    }

    private fun isUnderscoreMarker(text: String, index: Int): Boolean {
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + 1)
        val wordBefore = before != null && before.isLetterOrDigit()
        val wordAfter = after != null && after.isLetterOrDigit()
        return wordBefore != wordAfter
    }

    private val ESCAPABLE = setOf('\\', '`', '*', '_', '[', ']', '(', ')', '#', '+', '-', '.', '!', '>')
}
