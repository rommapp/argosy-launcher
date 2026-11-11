package com.nendo.argosy.ui.components.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HangulComposerTest {

    private val g = "ㄱ"
    private val gg = "ㄲ"
    private val n = "ㄴ"
    private val dd = "ㄸ"
    private val r = "ㄹ"
    private val s = "ㅅ"
    private val h = "ㅎ"
    private val a = "ㅏ"
    private val o = "ㅗ"
    private val wa = "ㅘ"
    private val yo = "ㅛ"
    private val eu = "ㅡ"

    private val ha = "하"
    private val han = "한"
    private val geu = "그"
    private val geul = "글"
    private val ra = "라"
    private val ga = "가"
    private val gak = "각"
    private val gaks = "갃"
    private val sa = "사"
    private val gwa = "과"
    private val kka = "까"

    private class Buffer(val composer: KeyboardComposer = HangulComposer()) {
        var text = ""
        var caret = 0

        fun type(vararg jamo: String): Buffer {
            jamo.forEach { key ->
                val edit = composer.input(text, caret, key)
                if (edit == null) {
                    text = StringBuilder(text).insert(caret, key).toString()
                    caret += key.length
                } else {
                    apply(edit)
                }
            }
            return this
        }

        fun backspace(): Buffer {
            val edit = composer.backspace(text, caret)
            if (edit == null) {
                text = text.removeRange(caret - 1, caret)
                caret -= 1
            } else {
                apply(edit)
            }
            return this
        }

        private fun apply(edit: ComposerEdit) {
            text = edit.text
            caret = edit.caret
        }
    }

    @Test
    fun `each jamo of hangeul folds into the syllables of the spec table`() {
        val buffer = Buffer()
        listOf(
            h to h,
            a to ha,
            n to han,
            g to han + g,
            eu to han + geu,
            r to han + geul,
            a to han + geu + ra
        ).forEach { (jamo, expected) ->
            buffer.type(jamo)
            assertEquals(expected, buffer.text)
            assertEquals(expected.length, buffer.caret)
        }
    }

    @Test
    fun `o then a combine into the compound vowel alone and under a lead`() {
        assertEquals(wa, Buffer().type(o, a).text)
        assertEquals(gwa, Buffer().type(g, o, a).text)
    }

    @Test
    fun `a compound final splits its second half onto the next syllable`() {
        val buffer = Buffer().type(g, a, g, s)
        assertEquals(gaks, buffer.text)
        buffer.type(a)
        assertEquals(gak + sa, buffer.text)
    }

    @Test
    fun `doubled tense consonants never become finals`() {
        assertEquals(ga + dd, Buffer().type(g, a, dd).text)
        assertEquals(ga + kka, Buffer().type(g, a, gg, a).text)
    }

    @Test
    fun `a lone vowel shows as the compatibility jamo and a consonant after it starts anew`() {
        assertEquals(yo + g, Buffer().type(yo, g).text)
    }

    @Test
    fun `backspace steps jamo by jamo through han then clears it`() {
        val buffer = Buffer().type(h, a, n)
        assertEquals(ha, buffer.backspace().text)
        assertEquals(h, buffer.backspace().text)
        assertEquals("", buffer.backspace().text)
        assertEquals(0, buffer.caret)
    }

    @Test
    fun `after a commit backspace leaves whole characters to the caller`() {
        val buffer = Buffer().type(h, a, n, g, eu, r)
        buffer.composer.commit()
        assertNull(buffer.composer.backspace(buffer.text, buffer.caret))
        assertEquals(han, buffer.backspace().text)
    }

    @Test
    fun `a vowel after a commit does not take the previous final`() {
        val buffer = Buffer().type(h, a, n)
        buffer.composer.commit()
        assertEquals(han + a, buffer.type(a).text)
    }

    @Test
    fun `non jamo input commits and is left to the caller`() {
        val buffer = Buffer().type(h, a, n, " ", a)
        assertEquals("$han $a", buffer.text)
    }

    private val assetDir = listOf(File("src/main/assets/keyboard"), File("app/src/main/assets/keyboard"))
        .first { it.isDirectory }

    private fun readFile(id: String): String? = File(assetDir, "$id.json").takeIf { it.isFile }?.readText()

    private fun hangulEngine(): KeyboardEngine = KeyboardEngine(
        parseLayout(readFile(KeyboardLayout.HANGUL_LAYOUT_ID)!!, ::readFile),
        KeyboardSession(label = "Name", submitLabel = "Done", languageLabel = "Korean")
    )

    private fun KeyboardEngine.typeKeys(vararg keys: String) {
        keys.forEach { key ->
            val rows = state.value.rows
            val position = rows.indices.firstNotNullOf { row ->
                rows[row].keys
                    .indexOfFirst { it is KeyboardKey.Character && it.text == key }
                    .takeIf { it >= 0 }
                    ?.let { KeyPosition(row, it) }
            }
            tapKey(position)
        }
    }

    @Test
    fun `the hangul layout file parses with the composer and maps from korean`() {
        val layout = parseLayout(readFile(KeyboardLayout.HANGUL_LAYOUT_ID)!!, ::readFile)
        assertEquals(HangulComposer.COMPOSER_ID, layout.composer)
        assertEquals(ShiftRule.None, layout.page(KeyboardLayout.LETTERS_PAGE)!!.shift)
        assertEquals(KeyboardLayout.HANGUL_LAYOUT_ID, layoutIdForLanguage("ko"))
    }

    @Test
    fun `typing keys through the engine yields hangeul`() {
        val engine = hangulEngine()
        engine.typeKeys(h, a, n, g, eu, r)
        assertEquals(han + geul, engine.state.value.text)
        assertEquals(2, engine.state.value.caret)
    }

    @Test
    fun `engine backspace steps jamo then deletes whole characters after a caret move`() {
        val engine = hangulEngine()
        engine.typeKeys(h, a, n, g, eu, r)
        engine.backspace()
        assertEquals(han + geu, engine.state.value.text)
        engine.moveCaret(-1)
        engine.moveCaret(1)
        engine.backspace()
        assertEquals(han, engine.state.value.text)
    }

    @Test
    fun `a caret move commits the syllable being composed`() {
        val engine = hangulEngine()
        engine.typeKeys(h, a)
        engine.moveCaret(-1)
        engine.moveCaret(1)
        engine.typeKeys(n)
        assertEquals(ha + n, engine.state.value.text)
    }

    @Test
    fun `shift types the doubled consonant and leaves keys without a shifted form as typed`() {
        val engine = hangulEngine()
        engine.shiftClick(0L)
        engine.typeKeys("ㄷ")
        engine.shiftClick(1_000L)
        engine.typeKeys(yo)
        assertEquals("뚀", engine.state.value.text)
    }
}
