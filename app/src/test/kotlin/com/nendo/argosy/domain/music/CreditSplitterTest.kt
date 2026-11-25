package com.nendo.argosy.domain.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CreditSplitterTest {

    @Test
    fun `splits on commas, semicolons, slashes and pipes and trims each part`() {
        assertEquals(
            listOf("Go Ichinose", "Hitomi Sato", "Minako Adachi"),
            CreditSplitter.split(" Go Ichinose, Hitomi Sato ;Minako Adachi ")
        )
        assertEquals(listOf("Tatsuhiko Yamamoto", "Cross Beat"), CreditSplitter.split("Tatsuhiko Yamamoto/Cross Beat"))
        assertEquals(listOf("Night Tempo", "Yuki Saito"), CreditSplitter.split("Night Tempo|Yuki Saito"))
    }

    private val ideographicComma = Char(0x3001)
    private val fullWidthOpen = Char(0xFF08)
    private val fullWidthClose = Char(0xFF09)

    @Test
    fun `splits on the ideographic comma`() {
        assertEquals(
            listOf("Yoko Shimomura", "Yasunori Mitsuda"),
            CreditSplitter.split("Yoko Shimomura${ideographicComma}Yasunori Mitsuda")
        )
    }

    @Test
    fun `never splits inside round, square or full-width brackets`() {
        val fullWidth = "Unit${fullWidthOpen}A${ideographicComma}B${fullWidthClose}"
        assertEquals(listOf("ACE (TOMOri Kudo / CHiCO)"), CreditSplitter.split("ACE (TOMOri Kudo / CHiCO)"))
        assertEquals(listOf("Unit [A, B]", "C"), CreditSplitter.split("Unit [A, B], C"))
        assertEquals(listOf(fullWidth, "C"), CreditSplitter.split("$fullWidth / C"))
        assertEquals(listOf("X (feat. Y)"), CreditSplitter.split("X (feat. Y)"))
    }

    @Test
    fun `an unbalanced closing bracket does not stop later splits`() {
        assertEquals(listOf("A)", "B"), CreditSplitter.split("A), B"))
    }

    @Test
    fun `feat and ft split in any case but a bare word does not`() {
        assertEquals(listOf("Artist", "Guest"), CreditSplitter.split("Artist feat. Guest"))
        assertEquals(listOf("Artist", "Guest"), CreditSplitter.split("Artist FT. Guest"))
        assertEquals(listOf("Artist", "Guest", "Other"), CreditSplitter.split("Artist Feat. Guest, Other"))
        assertEquals(listOf("Featherweight"), CreditSplitter.split("Featherweight"))
    }

    @Test
    fun `ampersands keep bands whole`() {
        assertEquals(listOf("Simon & Garfunkel"), CreditSplitter.split("Simon & Garfunkel"))
    }

    @Test
    fun `repeats collapse onto the first spelling by artist key`() {
        assertEquals(listOf("A", "B"), CreditSplitter.split("A / B / A"))
        assertEquals(listOf("Yuki Saito"), CreditSplitter.split("Yuki Saito, yuki  saito"))
    }

    @Test
    fun `blank and null credits give no parts`() {
        assertEquals(emptyList<String>(), CreditSplitter.split("  "))
        assertEquals(emptyList<String>(), CreditSplitter.split(null))
        assertEquals(listOf("A"), CreditSplitter.split("A, ,"))
    }

    @Test
    fun `artist key folds width, case and spacing but keeps punctuation`() {
        assertEquals("yuki saito", CreditSplitter.artistKey("  Yuki   SAITO "))
        val fullWidthAbc = String(charArrayOf(Char(0xFF21), Char(0xFF22), Char(0xFF23)))
        assertEquals("abc", CreditSplitter.artistKey(fullWidthAbc))
        assertNotEquals(CreditSplitter.artistKey("A.B"), CreditSplitter.artistKey("AB"))
    }
}
