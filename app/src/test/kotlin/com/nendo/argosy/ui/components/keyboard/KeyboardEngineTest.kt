package com.nendo.argosy.ui.components.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardEngineTest {

    private val assetDir = listOf(File("src/main/assets/keyboard"), File("app/src/main/assets/keyboard"))
        .first { it.isDirectory }

    private fun readFile(id: String): String? = File(assetDir, "$id.json").takeIf { it.isFile }?.readText()

    private fun layout(id: String): KeyboardLayout = parseLayout(readFile(id)!!, ::readFile)

    private val qwerty = layout(KeyboardLayout.QWERTY_LAYOUT_ID)

    private fun session(
        fieldType: KeyboardFieldType = KeyboardFieldType.TEXT,
        initialText: String = "",
        maxLength: Int? = null,
        canSubmit: Boolean = true,
        initialFocus: KeyPosition? = null
    ) = KeyboardSession(
        label = "Name",
        submitLabel = "Done",
        languageLabel = "English",
        initialText = initialText,
        fieldType = fieldType,
        canSubmit = canSubmit,
        maxLength = maxLength,
        initialFocus = initialFocus
    )

    private fun engine(
        session: KeyboardSession = session(),
        layout: KeyboardLayout = qwerty,
        onTextChange: (String) -> Unit = {}
    ) = KeyboardEngine(layout, session, onTextChange = onTextChange)

    private fun KeyboardEngine.focusedKey(): KeyboardKey = state.value.run { rows[focus.row].keys[focus.index] }

    @Test
    fun `first open focuses the middle key of the home row`() {
        val engine = engine()
        assertEquals(KeyPosition(2, 5), engine.state.value.focus)
        assertEquals(KeyboardKey.Character("g"), engine.focusedKey())
    }

    @Test
    fun `up into an offset row goes up-left and down goes down-right`() {
        val fromHome = engine()
        fromHome.move(0, -1)
        assertEquals(KeyboardKey.Character("t"), fromHome.focusedKey())
        fromHome.move(0, 1)
        assertEquals(KeyboardKey.Character("g"), fromHome.focusedKey())

        val fromTop = engine(session(initialFocus = KeyPosition(1, 4)))
        fromTop.move(0, 1)
        assertEquals(KeyboardKey.Character("g"), fromTop.focusedKey())
        fromTop.move(0, -1)
        assertEquals(KeyboardKey.Character("t"), fromTop.focusedKey())

        val afterHorizontal = engine(session(initialFocus = KeyPosition(1, 4)))
        afterHorizontal.move(1, 0)
        afterHorizontal.move(0, 1)
        assertEquals(KeyboardKey.Character("h"), afterHorizontal.focusedKey())
        afterHorizontal.move(1, 0)
        afterHorizontal.move(0, -1)
        assertEquals(KeyboardKey.Character("u"), afterHorizontal.focusedKey())
    }

    @Test
    fun `down from p reaches backspace and up returns to p`() {
        val engine = engine(session(initialFocus = KeyPosition(1, 9)))
        engine.move(0, 1)
        assertEquals(KeyboardKey.Character("l"), engine.focusedKey())
        engine.move(0, 1)
        assertEquals(KeyboardKey.Function(FunctionKey.BACKSPACE, 1.5f), engine.focusedKey())
        engine.move(0, -1)
        engine.move(0, -1)
        assertEquals(KeyPosition(1, 9), engine.state.value.focus)
    }

    @Test
    fun `left from the first key wraps to the last key skipping gaps`() {
        val engine = engine(session(initialFocus = KeyPosition(2, 1)))
        assertEquals(KeyboardFeedback.NAVIGATE, engine.move(-1, 0))
        assertEquals(KeyboardKey.Character("l"), engine.focusedKey())
    }

    @Test
    fun `right from backspace lands on shift`() {
        val engine = engine(session(initialFocus = KeyPosition(3, 8)))
        engine.move(1, 0)
        assertEquals(KeyboardKey.Function(FunctionKey.SHIFT, 1.5f), engine.focusedKey())
    }

    @Test
    fun `up from the number row lands on the bottom row`() {
        val engine = engine(session(initialFocus = KeyPosition(0, 0)))
        engine.move(0, -1)
        assertEquals(4, engine.state.value.focus.row)
        assertEquals(KeyboardKey.Function(FunctionKey.SYMBOLS, 1.5f), engine.focusedKey())
    }

    @Test
    fun `no move from any key lands on a gap`() {
        val rows = engine().state.value.rows
        val positions = rows.indices.flatMap { row ->
            rows[row].keys.indices.filter { rows[row].keys[it] !is KeyboardKey.Gap }.map { KeyPosition(row, it) }
        }
        positions.forEach { start ->
            listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).forEach { (dx, dy) ->
                val engine = engine(session(initialFocus = start))
                repeat(rows.size + 1) {
                    engine.move(dx, dy)
                    assertTrue("from $start by ($dx,$dy)", engine.focusedKey() !is KeyboardKey.Gap)
                }
            }
        }
    }

    @Test
    fun `shift once uppercases one character then reverts`() {
        var latest = ""
        val engine = engine(onTextChange = { latest = it })
        assertEquals(KeyboardFeedback.TOGGLE, engine.shiftClick(0L))
        assertEquals(ShiftState.ONCE, engine.state.value.shift)
        engine.activate()
        assertEquals(ShiftState.OFF, engine.state.value.shift)
        engine.activate()
        assertEquals("Gg", engine.state.value.text)
        assertEquals("Gg", latest)
    }

    @Test
    fun `shift on the number row types the explicit shifted character`() {
        val engine = engine(session(initialFocus = KeyPosition(0, 0)))
        engine.shiftClick(0L)
        engine.activate()
        assertEquals("!", engine.state.value.text)
    }

    @Test
    fun `double shift inside the timeout locks and a third click clears`() {
        val engine = engine()
        engine.shiftClick(0L)
        assertEquals(KeyboardFeedback.TOGGLE_ON, engine.shiftClick(200L))
        assertEquals(ShiftState.LOCKED, engine.state.value.shift)
        engine.activate()
        engine.activate()
        assertEquals("GG", engine.state.value.text)
        assertEquals(KeyboardFeedback.TOGGLE_OFF, engine.shiftClick(2_000L))
        assertEquals(ShiftState.OFF, engine.state.value.shift)
    }

    @Test
    fun `double tap on the shift key locks caps like the stick click`() {
        var now = 0L
        val engine = KeyboardEngine(qwerty, session(), nowMs = { now })
        val shiftKey = engine.state.value.rows.indexOfFirst { row ->
            row.keys.any { it is KeyboardKey.Function && it.function == FunctionKey.SHIFT }
        }.let { row ->
            KeyPosition(row, engine.state.value.rows[row].keys.indexOfFirst {
                it is KeyboardKey.Function && it.function == FunctionKey.SHIFT
            })
        }
        engine.tapKey(shiftKey)
        now = 200L
        assertEquals(KeyboardFeedback.TOGGLE_ON, engine.tapKey(shiftKey))
        assertEquals(ShiftState.LOCKED, engine.state.value.shift)
        now = 5_000L
        engine.tapKey(shiftKey)
        now = 6_000L
        assertEquals(KeyboardFeedback.TOGGLE, engine.tapKey(shiftKey))
        assertEquals(ShiftState.ONCE, engine.state.value.shift)
    }

    @Test
    fun `second shift after the timeout turns shift off`() {
        val engine = engine()
        engine.shiftClick(0L)
        assertEquals(KeyboardFeedback.TOGGLE, engine.shiftClick(500L))
        assertEquals(ShiftState.OFF, engine.state.value.shift)
    }

    @Test
    fun `shift uppercases with the layout locale`() {
        val turkish = KeyboardLayout(
            id = "fixture-tr",
            script = "Latn",
            locales = listOf("tr"),
            composer = "none",
            pages = listOf(
                KeyboardPage(KeyboardLayout.LETTERS_PAGE, listOf(KeyboardRow(listOf(KeyboardKey.Character("i")))))
            ),
            accents = emptyMap()
        )
        val engine = engine(session(initialFocus = KeyPosition(0, 0)), layout = turkish)
        engine.shiftClick(0L)
        engine.activate()
        assertEquals("İ", engine.state.value.text)
    }

    @Test
    fun `typing at max length is a boundary and changes nothing`() {
        val engine = engine(session(initialText = "ab", maxLength = 2))
        assertEquals(KeyboardFeedback.BOUNDARY, engine.activate())
        assertEquals(KeyboardFeedback.BOUNDARY, engine.space())
        assertEquals("ab", engine.state.value.text)
    }

    @Test
    fun `backspace on empty text is a boundary`() {
        assertEquals(KeyboardFeedback.BOUNDARY, engine().backspace())
    }

    @Test
    fun `caret moves stop at the ends and typing inserts at the caret`() {
        val engine = engine(session(initialText = "abc"))
        assertEquals(3, engine.state.value.caret)
        assertEquals(KeyboardFeedback.BOUNDARY, engine.moveCaret(1))
        assertEquals(KeyboardFeedback.NAVIGATE, engine.moveCaret(-1))
        engine.activate()
        assertEquals("abgc", engine.state.value.text)
        assertEquals(3, engine.state.value.caret)
        assertEquals(KeyboardFeedback.BACK, engine.backspace())
        assertEquals("abc", engine.state.value.text)
        engine.setCaret(0)
        assertEquals(KeyboardFeedback.BOUNDARY, engine.moveCaret(-1))
        assertEquals(KeyboardFeedback.BOUNDARY, engine.backspace())
        assertEquals(KeyboardFeedback.NAVIGATE, engine.moveCaret(10))
        assertEquals(3, engine.state.value.caret)
    }

    @Test
    fun `long press on e opens the accent strip and picks e acute`() {
        val engine = engine(session(initialFocus = KeyPosition(1, 2)))
        engine.longActivate()
        val strip = engine.state.value.accentStrip
        assertNotNull(strip)
        assertEquals(0, strip!!.focused)
        assertEquals("è", strip.options.first())
        engine.move(1, 0)
        assertEquals(KeyboardFeedback.SELECT, engine.activate())
        assertEquals("é", engine.state.value.text)
        assertNull(engine.state.value.accentStrip)
    }

    @Test
    fun `accent strip starts on the layout locale default`() {
        val engine = engine(session(initialFocus = KeyPosition(1, 2)), layout = layout(KeyboardLayout.AZERTY_LAYOUT_ID))
        engine.longActivate()
        val strip = engine.state.value.accentStrip!!
        assertEquals("é", strip.options[strip.focused])
    }

    @Test
    fun `back closes the accent strip before closing the keyboard`() {
        val engine = engine(session(initialFocus = KeyPosition(1, 2)))
        engine.longActivate()
        assertEquals(KeyboardFeedback.BACK, engine.back())
        assertNull(engine.state.value.accentStrip)
        assertEquals("", engine.state.value.text)
        assertEquals(KeyboardFeedback.CLOSED, engine.back())
    }

    @Test
    fun `password field long press types the key once`() {
        val engine = engine(session(fieldType = KeyboardFieldType.PASSWORD, initialFocus = KeyPosition(1, 2)))
        assertEquals(KeyboardFeedback.SELECT, engine.longActivate())
        assertNull(engine.state.value.accentStrip)
        assertEquals("e", engine.state.value.text)
    }

    @Test
    fun `long press on a key without accents types it once`() {
        val engine = engine(session(initialFocus = KeyPosition(1, 0)))
        assertEquals(KeyboardFeedback.SELECT, engine.longActivate())
        assertEquals("q", engine.state.value.text)
    }

    private fun bottomRow(fieldType: KeyboardFieldType, pageId: String = KeyboardLayout.LETTERS_PAGE) =
        KeyboardEngine.bottomRow(qwerty.page(pageId)!!, fieldType).keys

    @Test
    fun `text bottom row carries the layout key`() {
        assertEquals(
            listOf(
                KeyboardKey.Function(FunctionKey.SYMBOLS, 1.5f),
                KeyboardKey.Function(FunctionKey.LAYOUT),
                KeyboardKey.Character(","),
                KeyboardKey.Function(FunctionKey.SPACE, 4f),
                KeyboardKey.Character("."),
                KeyboardKey.Function(FunctionKey.ENTER, 1.5f)
            ),
            bottomRow(KeyboardFieldType.TEXT)
        )
    }

    @Test
    fun `uri and email bottom rows carry their typical character`() {
        assertEquals(KeyboardKey.Character("/"), bottomRow(KeyboardFieldType.URI)[1])
        assertEquals(KeyboardKey.Character("@"), bottomRow(KeyboardFieldType.EMAIL)[1])
        assertEquals(KeyboardKey.Function(FunctionKey.SPACE, 4f), bottomRow(KeyboardFieldType.URI)[3])
    }

    @Test
    fun `password bottom row drops the layout key and widens space`() {
        val keys = bottomRow(KeyboardFieldType.PASSWORD)
        assertEquals(5, keys.size)
        assertEquals(KeyboardKey.Function(FunctionKey.SPACE, 5f), keys[2])
        assertTrue(keys.none { it == KeyboardKey.Function(FunctionKey.LAYOUT) })
    }

    @Test
    fun `symbols bottom row carries letters and the backtick`() {
        val keys = bottomRow(KeyboardFieldType.PASSWORD, KeyboardLayout.SYMBOLS_PAGE)
        assertEquals(KeyboardKey.Function(FunctionKey.LETTERS, 1.5f), keys[0])
        assertEquals(KeyboardKey.Character("`"), keys[1])
    }

    @Test
    fun `bottom row width matches the widest row`() {
        val cyrillic = layout(KeyboardLayout.JCUKEN_LAYOUT_ID)
        val keys = KeyboardEngine.bottomRow(cyrillic.page(KeyboardLayout.LETTERS_PAGE)!!, KeyboardFieldType.TEXT).keys
        assertEquals(12f, keys.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `numeric layout gets no bottom row`() {
        val numeric = layout(KeyboardLayout.NUMERIC_LAYOUT_ID)
        val engine = engine(session(fieldType = KeyboardFieldType.NUMBER), layout = numeric)
        assertEquals(4, engine.state.value.rows.size)
        assertEquals(KeyboardKey.Character("5"), engine.focusedKey())
    }

    @Test
    fun `symbols toggle keeps the focused row and column`() {
        val engine = engine(session(initialFocus = KeyPosition(1, 9)))
        assertEquals(KeyboardFeedback.TOGGLE, engine.toggleSymbols())
        assertEquals(KeyboardLayout.SYMBOLS_PAGE, engine.state.value.pageId)
        assertEquals(KeyPosition(1, 9), engine.state.value.focus)
        engine.toggleSymbols()
        assertEquals(KeyboardKey.Character("p"), engine.focusedKey())
    }

    @Test
    fun `layout key requests a layout cycle and ascii fields refuse a switch`() {
        val engine = engine(session(initialFocus = KeyPosition(4, 1)))
        assertEquals(KeyboardFeedback.LAYOUT_REQUESTED, engine.activate())
        val password = engine(session(fieldType = KeyboardFieldType.PASSWORD))
        assertEquals(KeyboardFeedback.NONE, password.switchLayout(layout(KeyboardLayout.JCUKEN_LAYOUT_ID), "Russian"))
        assertEquals(KeyboardLayout.QWERTY_LAYOUT_ID, password.state.value.layoutId)
    }

    @Test
    fun `submit follows canSubmit and a disabled keyboard swallows all but back`() {
        val engine = engine(session(canSubmit = false))
        assertEquals(KeyboardFeedback.ERROR, engine.submit())
        engine.updateSession(error = null, canSubmit = true, enabled = true)
        assertEquals(KeyboardFeedback.SUBMITTED, engine.submit())
        engine.updateSession(error = "Busy", canSubmit = true, enabled = false)
        assertEquals(KeyboardFeedback.NONE, engine.activate())
        assertEquals(KeyboardFeedback.NONE, engine.move(1, 0))
        assertEquals(KeyboardFeedback.CLOSED, engine.back())
    }

    @Test
    fun `tap focuses the key and types it`() {
        val engine = engine()
        assertEquals(KeyboardFeedback.SELECT, engine.tapKey(KeyPosition(1, 0)))
        assertEquals(KeyPosition(1, 0), engine.state.value.focus)
        assertEquals("q", engine.state.value.text)
        assertEquals(KeyboardFeedback.NONE, engine.tapKey(KeyPosition(2, 0)))
    }

    @Test
    fun `paste inserts at the caret and reports the text`() {
        var reported = ""
        val engine = engine(session(initialText = "ad"), onTextChange = { reported = it })
        engine.moveCaret(-1)

        assertEquals(KeyboardFeedback.SELECT, engine.paste("bc"))

        assertEquals("abcd", engine.state.value.text)
        assertEquals(3, engine.state.value.caret)
        assertEquals("abcd", reported)
    }

    @Test
    fun `paste cuts text past the length limit and a full field refuses it`() {
        val engine = engine(session(initialText = "ab", maxLength = 4))

        engine.paste("cdef")
        assertEquals("abcd", engine.state.value.text)
        assertEquals(KeyboardFeedback.BOUNDARY, engine.paste("e"))
        assertEquals("abcd", engine.state.value.text)
    }

    @Test
    fun `paste leaves shift as it was`() {
        val engine = engine()
        engine.shiftClick(0L)

        engine.paste("x")

        assertEquals(ShiftState.ONCE, engine.state.value.shift)
    }

    @Test
    fun `a disabled keyboard ignores paste`() {
        val engine = engine()
        engine.updateSession(error = null, canSubmit = true, enabled = false)

        assertEquals(KeyboardFeedback.NONE, engine.paste("x"))
        assertEquals("", engine.state.value.text)
    }

    @Test
    fun `paste text is cleaned for the field type`() {
        assertEquals("line one line two", pasteText("line one\r\nline two", KeyboardFieldType.TEXT))
        assertEquals("  spaced ", pasteText("  spaced\n", KeyboardFieldType.TEXT))
        assertEquals("https://romm.example", pasteText(" https://romm.example\n", KeyboardFieldType.URI))
        assertEquals("me@example.com", pasteText("me@example.com\t", KeyboardFieldType.EMAIL))
        assertEquals("p a ss", pasteText("\np a ss\n", KeyboardFieldType.PASSWORD))
        assertEquals("5551234", pasteText("555-1234\n", KeyboardFieldType.NUMBER))
    }

    private fun clipboardEngine(clip: String?) =
        KeyboardEngine(qwerty, session(initialFocus = KeyPosition(0, 4)), readClipboard = { clip })

    @Test
    fun `up from the top row reaches paste when the clipboard holds text, and ok pastes`() {
        val engine = clipboardEngine("hello")
        engine.setPasteAvailable(true)

        assertEquals(KeyboardFeedback.NAVIGATE, engine.move(0, -1))
        assertTrue(engine.state.value.pasteFocused)
        assertEquals(KeyboardFeedback.SELECT, engine.activate())
        assertEquals("hello", engine.state.value.text)
    }

    @Test
    fun `down from paste returns to the key it came from and up stops there`() {
        val engine = clipboardEngine("x")
        engine.setPasteAvailable(true)
        val from = engine.state.value.focus
        engine.move(0, -1)

        assertEquals(KeyboardFeedback.BOUNDARY, engine.move(0, -1))
        assertEquals(KeyboardFeedback.BOUNDARY, engine.move(1, 0))
        engine.move(0, 1)

        assertFalse(engine.state.value.pasteFocused)
        assertEquals(from, engine.state.value.focus)
    }

    @Test
    fun `without clipboard text up from the top row wraps as before`() {
        val engine = clipboardEngine(null)

        engine.move(0, -1)

        assertFalse(engine.state.value.pasteFocused)
        assertEquals(engine.state.value.rows.lastIndex, engine.state.value.focus.row)
    }

    @Test
    fun `paste focus leaves when the clipboard empties and a key tap takes focus back`() {
        val engine = clipboardEngine("x")
        engine.setPasteAvailable(true)
        engine.move(0, -1)

        engine.setPasteAvailable(false)
        assertFalse(engine.state.value.pasteFocused)

        engine.setPasteAvailable(true)
        engine.move(0, -1)
        engine.tapKey(KeyPosition(2, 5))
        assertFalse(engine.state.value.pasteFocused)
        assertEquals("g", engine.state.value.text)
    }

    @Test
    fun `pasting an empty clipboard is a boundary`() {
        assertEquals(KeyboardFeedback.BOUNDARY, clipboardEngine(null).pasteClipboard())
    }

    @Test
    fun `paste of nothing usable is a boundary`() {
        val engine = engine(session(fieldType = KeyboardFieldType.NUMBER))

        assertEquals(KeyboardFeedback.BOUNDARY, engine.paste("abc"))
    }
}
