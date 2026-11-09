package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedFolderGrantTest {

    private fun folderOf(path: String) = sharedFolderSegments(path.trim('/').split('/'))

    @Test
    fun `a game folder on internal storage is granted`() {
        assertEquals(
            listOf("storage", "emulated", "0", "Roms", "psx", "CTR"),
            folderOf("storage/emulated/0/Roms/psx/CTR/CTR.cue")
        )
    }

    @Test
    fun `a game folder on an SD card is granted`() {
        assertEquals(
            listOf("storage", "546B-6466", "Games", "psx"),
            folderOf("storage/546B-6466/Games/psx/Game.cue")
        )
    }

    @Test
    fun `a file at the root of a volume grants no folder`() {
        assertNull(folderOf("storage/emulated/0/Game.cue"))
        assertNull(folderOf("storage/self/primary/Game.cue"))
        assertNull(folderOf("storage/546B-6466/Game.cue"))
        assertNull(folderOf("mnt/media_rw/546B-6466/Game.cue"))
    }

    @Test
    fun `a file directly under the storage root grants no folder`() {
        assertNull(folderOf("storage/Game.cue"))
    }

    @Test
    fun `anything under a volume's Android directory grants no folder`() {
        assertNull(folderOf("storage/emulated/0/Android/data/com.nendo.argosy/files/x/Game.cue"))
        assertNull(folderOf("storage/546B-6466/Android/obb/Game.cue"))
        assertNull(folderOf("storage/emulated/0/android/Game.cue"))
    }

    @Test
    fun `Argosy's private FileProvider roots grant no folder`() {
        assertNull(folderOf("internal/roms/Game.cue"))
        assertNull(folderOf("cache/extract/Game/Game.cue"))
        assertNull(folderOf("external_files/roms/Game/Game.cue"))
    }
}
