package com.nendo.argosy.ui.screens.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderChoicesTest {

    private val internal = "/storage/emulated/0/ROMs"
    private val sdCard = "/storage/1A2B-3C4D/ROMs"

    @Test
    fun `a fresh page pre-selects internal storage and private covers`() {
        val choices = folderChoicesFrom(internal, sdCard, savedRomPath = null, savedCoverPath = null)
        assertEquals(RomFolderKind.INTERNAL, choices.selectedRom)
        assertEquals(internal, choices.selectedRomPath)
        assertEquals(CoverStorageKind.PRIVATE, choices.selectedCover)
        assertNull(choices.coverPath)
        assertEquals(listOf(RomFolderKind.INTERNAL, RomFolderKind.SD_CARD, RomFolderKind.CUSTOM), choices.romKinds)
    }

    @Test
    fun `no card means no card row`() {
        val choices = folderChoicesFrom(internal, sdCardPath = null, savedRomPath = null, savedCoverPath = null)
        assertEquals(listOf(RomFolderKind.INTERNAL, RomFolderKind.CUSTOM), choices.romKinds)
    }

    @Test
    fun `saved folders come back as the selection`() {
        assertEquals(
            RomFolderKind.SD_CARD,
            folderChoicesFrom(internal, sdCard, savedRomPath = sdCard, savedCoverPath = null).selectedRom
        )
        val custom = folderChoicesFrom(internal, sdCard, savedRomPath = "/storage/emulated/0/Games", savedCoverPath = "/storage/emulated/0/Pictures")
        assertEquals(RomFolderKind.CUSTOM, custom.selectedRom)
        assertEquals("/storage/emulated/0/Games", custom.selectedRomPath)
        assertEquals(CoverStorageKind.PUBLIC, custom.selectedCover)
        assertEquals("/storage/emulated/0/Pictures", custom.coverPath)
    }

    @Test
    fun `a blank saved folder counts as none`() {
        val blank = folderChoicesFrom(internal, sdCard, savedRomPath = " ", savedCoverPath = "")
        assertEquals(RomFolderKind.INTERNAL, blank.selectedRom)
        assertEquals(CoverStorageKind.PRIVATE, blank.selectedCover)
    }

    @Test
    fun `removing the selected card locks its row and falls back to internal storage`() {
        val choices = folderChoicesFrom(internal, sdCard, savedRomPath = sdCard, savedCoverPath = null)
        val removed = choices.withSdCard(null)
        assertTrue(removed.sdCardRemoved)
        assertFalse(removed.isAvailable(RomFolderKind.SD_CARD))
        assertEquals(RomFolderKind.INTERNAL, removed.selectedRom)
        assertEquals(internal, removed.selectedRomPath)

        val returned = removed.withSdCard(sdCard)
        assertFalse(returned.sdCardRemoved)
        assertTrue(returned.isAvailable(RomFolderKind.SD_CARD))
    }

    @Test
    fun `a card that was never there stays absent`() {
        val choices = folderChoicesFrom(internal, sdCardPath = null, savedRomPath = null, savedCoverPath = null)
        assertEquals(choices, choices.withSdCard(null))
    }

    @Test
    fun `choosing another folder before picking one has no path to confirm`() {
        val choices = folderChoicesFrom(internal, sdCard, null, null).copy(selectedRom = RomFolderKind.CUSTOM)
        assertNull(choices.selectedRomPath)
    }

    @Test
    fun `public storage without a picked folder writes no cover path`() {
        val choices = folderChoicesFrom(internal, sdCard, null, null).copy(selectedCover = CoverStorageKind.PUBLIC)
        assertNull(choices.coverPath)
    }
}
