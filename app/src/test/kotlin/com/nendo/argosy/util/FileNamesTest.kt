package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the folding rules a downloaded name goes through. The cases that matter are the ones a
 * server can actually produce: a title with a colon, an accented title that must survive intact,
 * and an archive entry that tries to climb out of its folder.
 */
class FileNamesTest {

    @Test
    fun `folds characters android refuses`() {
        assertEquals("Pokemon Legends_ Z-A.zip", FileNames.sanitize("Pokemon Legends: Z-A.zip"))
        assertEquals("a_b_c_d_e_f_g_h", FileNames.sanitize("a<b>c:d\"e|f?g*h"))
    }

    @Test
    fun `leaves accented and spaced names alone`() {
        assertEquals("Pokémon Legends Z-A.zip", FileNames.sanitize("Pokémon Legends Z-A.zip"))
        assertEquals("Mr. Do!.nes", FileNames.sanitize("Mr. Do!.nes"))
    }

    @Test
    fun `folds separators so a name stays one name`() {
        assertEquals("a_b", FileNames.sanitize("a/b"))
        assertEquals("a_b", FileNames.sanitize("a\\b"))
    }

    @Test
    fun `drops trailing dots and spaces that android strips`() {
        assertEquals("game", FileNames.sanitize("game. "))
        assertEquals("_", FileNames.sanitize("   "))
    }

    @Test
    fun `keeps archive folder structure while folding each segment`() {
        assertEquals(
            "Disc 1/Pokémon_ Red.bin",
            FileNames.sanitizeRelativePath("Disc 1/Pokémon: Red.bin")
        )
    }

    @Test
    fun `refuses to climb out of the extraction folder`() {
        assertEquals("etc/passwd", FileNames.sanitizeRelativePath("../../etc/passwd"))
        assertEquals("rom.bin", FileNames.sanitizeRelativePath("./rom.bin"))
    }
}
