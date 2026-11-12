package com.nendo.argosy.ui.components.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesParserTest {

    @Test
    fun `headings lists and paragraphs become blocks`() {
        val blocks = ReleaseNotesParser.parse(
            "## Fixes\n\n- first\n  * nested\n1. numbered\nplain text"
        )
        assertEquals(
            listOf(
                NotesBlock.Heading(2, listOf(NotesInline("Fixes"))),
                NotesBlock.Blank,
                NotesBlock.Bullet(0, listOf(NotesInline("first"))),
                NotesBlock.Bullet(1, listOf(NotesInline("nested"))),
                NotesBlock.Numbered("1", 0, listOf(NotesInline("numbered"))),
                NotesBlock.Paragraph(listOf(NotesInline("plain text")))
            ),
            blocks
        )
    }

    @Test
    fun `bold italics and inline code are styled runs`() {
        assertEquals(
            listOf(
                NotesInline("a "),
                NotesInline("bold", bold = true),
                NotesInline(" "),
                NotesInline("it", italic = true),
                NotesInline(" "),
                NotesInline("code", code = true)
            ),
            ReleaseNotesParser.inlines("a **bold** *it* `code`")
        )
    }

    @Test
    fun `links and images keep only their text`() {
        assertEquals(
            listOf(NotesInline("see the docs and logo")),
            ReleaseNotesParser.inlines("see [the docs](https://example/docs) and ![logo](https://example/l.png)")
        )
    }

    @Test
    fun `underscores inside words stay literal`() {
        assertEquals(listOf(NotesInline("snake_case_name")), ReleaseNotesParser.inlines("snake_case_name"))
    }

    @Test
    fun `a lone asterisk between spaces stays literal`() {
        assertEquals(listOf(NotesInline("2 * 3")), ReleaseNotesParser.inlines("2 * 3"))
    }

    @Test
    fun `fenced code keeps its lines verbatim`() {
        assertEquals(
            listOf(NotesBlock.Code("**raw**")),
            ReleaseNotesParser.parse("```\n**raw**\n```")
        )
    }

    @Test
    fun `repeated and trailing blank lines collapse`() {
        assertEquals(
            listOf(
                NotesBlock.Paragraph(listOf(NotesInline("a"))),
                NotesBlock.Blank,
                NotesBlock.Paragraph(listOf(NotesInline("b")))
            ),
            ReleaseNotesParser.parse("\n\na\n\n\n\nb\n\n")
        )
    }
}
