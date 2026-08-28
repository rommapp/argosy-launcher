package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what counts as game content. The download picker, the download itself, library sync and
 * the size calculation all read this, so a file admitted here is one every path agrees to fetch.
 */
class RomMRomFileContentTest {

    private fun file(path: String, name: String) = RomMRomFile(
        id = 1,
        romId = 1,
        fileName = name,
        filePath = path,
        fileSizeBytes = 1,
        fullPath = "$path/$name"
    )

    @Test
    fun `ordinary game files are content`() {
        assertTrue(file("Game Folder", "Game (Disc 1).chd").isGameContent)
        assertTrue(file("roms/psx/Game", "track01.bin").isGameContent)
    }

    @Test
    fun `synology writes eaDir beside the game and it is never content`() {
        assertFalse(file("Game Folder/@eaDir", "SYNOPHOTO_THUMB.jpg").isGameContent)
        assertFalse(file("Game Folder", "@eaDir").isGameContent)
    }

    @Test
    fun `eaDir is matched whatever its casing and however deep it sits`() {
        assertFalse(file("Game/@EADIR/thumbs", "cover.jpg").isGameContent)
        assertFalse(file("Game/@eadir", "index.db").isGameContent)
        assertFalse(file("a/b/c/@eaDir/d", "buried.bin").isGameContent)
    }

    @Test
    fun `a windows separator hides nothing`() {
        assertFalse(file("Game Folder\\@eaDir", "thumb.jpg").isGameContent)
    }

    @Test
    fun `dotfiles stay excluded, by name or by folder`() {
        assertFalse(file("Game Folder", ".DS_Store").isGameContent)
        assertFalse(file("Game Folder/.hidden", "inside.bin").isGameContent)
    }

    @Test
    fun `a name merely containing the marker is still content`() {
        assertTrue(file("Game Folder", "eaDirector's Cut.chd").isGameContent)
        assertTrue(file("My @eaDirty Game", "rom.bin").isGameContent)
    }
}
