package com.nendo.argosy.data.repository

import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.dropPrefixNormalized
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.equalsNormalized
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.isLatestSaveFileName
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.parseServerChannelNameForSync
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.resolveServerChannelName
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.startsWithNormalized
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.stripAccents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSyncNormalizationTest {

    // --- Group A: stripAccents ---

    @Test
    fun `stripAccents removes French accents`() {
        assertEquals("Pokemon Violet", stripAccents("Pok\u00e9mon Violet"))
    }

    @Test
    fun `stripAccents passes through plain ASCII`() {
        assertEquals("Hello World", stripAccents("Hello World"))
    }

    @Test
    fun `stripAccents normalizes precomposed e-acute`() {
        assertEquals("e", stripAccents("\u00e9"))
    }

    @Test
    fun `stripAccents normalizes decomposed e-acute`() {
        assertEquals("e", stripAccents("e\u0301"))
    }

    @Test
    fun `stripAccents handles multiple accent types`() {
        assertEquals("Pokemon Epee", stripAccents("Pok\u00e9mon \u00c9p\u00e9e"))
    }

    @Test
    fun `stripAccents preserves CJK characters`() {
        val cjk = "\u6e38\u620f"
        assertEquals(cjk, stripAccents(cjk))
    }

    @Test
    fun `stripAccents handles empty string`() {
        assertEquals("", stripAccents(""))
    }

    @Test
    fun `stripAccents removes German umlauts`() {
        assertEquals("Uber", stripAccents("\u00dcber"))
    }

    @Test
    fun `stripAccents removes tilde`() {
        assertEquals("Senor", stripAccents("Se\u00f1or"))
    }

    // --- Group B: equalsNormalized ---

    @Test
    fun `equalsNormalized matches accented vs ASCII`() {
        assertTrue(equalsNormalized("Pok\u00e9mon Violet", "Pokemon Violet"))
    }

    @Test
    fun `equalsNormalized is case insensitive`() {
        assertTrue(equalsNormalized("POKEMON VIOLET", "pokemon violet"))
    }

    @Test
    fun `equalsNormalized rejects different strings`() {
        assertFalse(equalsNormalized("Pokemon Violet", "Pokemon Scarlet"))
    }

    @Test
    fun `equalsNormalized matches both sides accented differently`() {
        assertTrue(equalsNormalized("Pok\u00e9mon", "Poke\u0301mon"))
    }

    @Test
    fun `equalsNormalized handles empty strings`() {
        assertTrue(equalsNormalized("", ""))
    }

    // --- Group C: startsWithNormalized / dropPrefixNormalized ---

    @Test
    fun `startsWithNormalized accented prefix matches ASCII base`() {
        assertTrue(startsWithNormalized("Pok\u00e9mon Violet Extra", "Pokemon Violet"))
    }

    @Test
    fun `startsWithNormalized rejects non-prefix`() {
        assertFalse(startsWithNormalized("Something Else", "Pokemon"))
    }

    @Test
    fun `dropPrefixNormalized drops accented prefix preserving suffix`() {
        val result = dropPrefixNormalized("Pok\u00e9mon Violet [2024]", "Pokemon Violet")
        assertEquals(" [2024]", result)
    }

    @Test
    fun `dropPrefixNormalized returns original when no match`() {
        val original = "Something Else"
        assertEquals(original, dropPrefixNormalized(original, "Pokemon"))
    }

    // --- Group D: isLatestSaveFileName ---

    @Test
    fun `isLatestSaveFileName matches default name for any romBaseName`() {
        assertTrue(isLatestSaveFileName("argosy-latest.srm", "anything"))
    }

    @Test
    fun `isLatestSaveFileName matches exact ASCII romBaseName`() {
        assertTrue(isLatestSaveFileName("Pokemon Violet.srm", "Pokemon Violet"))
    }

    @Test
    fun `isLatestSaveFileName matches accented filename vs ASCII romBaseName`() {
        assertTrue(isLatestSaveFileName("Pok\u00e9mon Violet.srm", "Pokemon Violet"))
    }

    @Test
    fun `isLatestSaveFileName matches ASCII filename vs accented romBaseName`() {
        assertTrue(isLatestSaveFileName("Pokemon Violet.srm", "Pok\u00e9mon Violet"))
    }

    @Test
    fun `isLatestSaveFileName matches accented with RomM timestamp tag`() {
        assertTrue(
            isLatestSaveFileName(
                "Pok\u00e9mon Violet [2024-01-15 12-00-00].srm",
                "Pokemon Violet"
            )
        )
    }

    @Test
    fun `isLatestSaveFileName rejects channel name`() {
        assertFalse(isLatestSaveFileName("checkpoint.srm", "Pokemon Violet"))
    }

    @Test
    fun `isLatestSaveFileName null romBaseName only matches default`() {
        assertTrue(isLatestSaveFileName("argosy-latest.srm", null))
        assertFalse(isLatestSaveFileName("something.srm", null))
    }

    // --- Group E: parseServerChannelNameForSync ---

    @Test
    fun `parseServerChannelNameForSync accented latest returns null`() {
        assertNull(
            parseServerChannelNameForSync("Pok\u00e9mon Violet.srm", "Pokemon Violet")
        )
    }

    @Test
    fun `parseServerChannelNameForSync non-matching returns channel name`() {
        assertEquals(
            "checkpoint",
            parseServerChannelNameForSync("checkpoint.srm", "Pokemon Violet")
        )
    }

    @Test
    fun `parseServerChannelNameForSync timestamp-only returns null`() {
        assertNull(
            parseServerChannelNameForSync("2024-01-15_14-30-00.srm", "Pokemon Violet")
        )
    }

    private fun serverSave(fileName: String, slot: String? = null) = RomMSave(
        id = 1L,
        romId = 2L,
        userId = 3L,
        emulator = "snes9x",
        fileName = fileName,
        updatedAt = "2026-09-10T00:00:00Z",
        slot = slot
    )

    @Test
    fun `a save named after the rom is the latest save, not a named slot`() {
        assertNull(
            resolveServerChannelName(serverSave("Super Metroid.srm"), "Super Metroid")
        )
    }

    @Test
    fun `an explicit autosave slot is the latest save even when the file is named otherwise`() {
        assertNull(
            resolveServerChannelName(serverSave("Super Metroid.srm", slot = "autosave"), "Super Metroid")
        )
    }

    @Test
    fun `an explicit slot wins over the file name`() {
        assertEquals(
            "checkpoint",
            resolveServerChannelName(serverSave("Super Metroid.srm", slot = "checkpoint"), "Super Metroid")
        )
    }

    @Test
    fun `a file name is read only when the server sent no slot`() {
        assertEquals(
            "checkpoint",
            resolveServerChannelName(serverSave("checkpoint.srm"), "Super Metroid")
        )
    }

    @Test
    fun `a rom-named save with no rom base name still resolves to a channel`() {
        assertEquals(
            "Super Metroid",
            resolveServerChannelName(serverSave("Super Metroid.srm"), null)
        )
    }

    @Test
    fun `a timestamped archive is not a named slot`() {
        assertNull(
            resolveServerChannelName(serverSave("2024-01-15_14-30-00.srm"), "Super Metroid")
        )
    }
}
