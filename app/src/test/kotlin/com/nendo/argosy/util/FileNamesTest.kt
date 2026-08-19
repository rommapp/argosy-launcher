package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the folding rules a downloaded name goes through. The cases that matter are the ones a
 * server can actually produce: a title with a colon, an accented title that must survive intact,
 * an archive entry that tries to climb out of its folder, and the matcher still recognising a
 * file it renamed on the way in.
 */
class FileNamesTest {

    @Test
    fun `drops characters android refuses`() {
        assertEquals("Pokemon Legends Z-A.zip", FileNames.sanitize("Pokemon Legends: Z-A.zip"))
        assertEquals("abcdefgh", FileNames.sanitize("a<b>c:d\"e|f?g*h"))
    }

    @Test
    fun `leaves accented and punctuated names alone`() {
        assertEquals("Pokémon Legends Z-A.zip", FileNames.sanitize("Pokémon Legends Z-A.zip"))
        assertEquals("Mr. Do!.nes", FileNames.sanitize("Mr. Do!.nes"))
    }

    @Test
    fun `collapses the gap a dropped character leaves`() {
        assertEquals("Vol. 1 Beginnings.iso", FileNames.sanitize("Vol. 1 : Beginnings.iso"))
        assertEquals("ab", FileNames.sanitize("a/b"))
    }

    @Test
    fun `drops trailing dots and spaces that android strips`() {
        assertEquals("game", FileNames.sanitize("game. "))
        assertEquals("file", FileNames.sanitize("   "))
        assertEquals("file", FileNames.sanitize(":::"))
    }

    @Test
    fun `keeps archive folder structure while folding each segment`() {
        assertEquals(
            "Disc 1/Pokémon Red.bin",
            FileNames.sanitizeRelativePath("Disc 1/Pokémon: Red.bin")
        )
    }

    @Test
    fun `refuses to climb out of the extraction folder`() {
        assertEquals("etc/passwd", FileNames.sanitizeRelativePath("../../etc/passwd"))
        assertEquals("rom.bin", FileNames.sanitizeRelativePath("./rom.bin"))
    }

    @Test
    fun `matches a sanitized file against the name the server reports`() {
        val server = "Pokémon Legends: Z-A.zip"
        assertEquals(
            FileNames.normalizeForMatch(server),
            FileNames.normalizeForMatch(FileNames.sanitize(server))
        )
    }

    @Test
    fun `matches a file placed with the older underscore convention`() {
        assertEquals(
            FileNames.normalizeForMatch("Pokémon Legends: Z-A.zip"),
            FileNames.normalizeForMatch("Pokémon Legends_ Z-A.zip")
        )
    }
}
