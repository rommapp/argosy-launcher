package com.nendo.argosy.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GB = 1024L * 1024L * 1024L

class ArchiveExpansionTest {

    @Test
    fun `switch zip reserves close to its own size, not a multiple of it`() {
        val archive = 60 * GB
        val reserved = ArchiveExpansion.estimate(archive, "Game.zip", "switch")

        assertTrue("a packed payload must not reserve a multiple of itself", reserved < archive * 2)
        assertEquals(archive * 110 / 100, reserved)
    }

    /**
     * The reported failure: a 60 GiB Switch download refused on a volume with 174 GiB free,
     * because the archive and its unpacked output are both charged and the unpack was estimated at
     * three times the archive. Both halves are still charged; only the estimate changed.
     */
    @Test
    fun `a 60 GiB switch title fits a volume with 174 GiB free`() {
        val archive = 60 * GB
        val free = 174 * GB
        val buffer = 50L * 1024 * 1024

        val required = archive + ArchiveExpansion.estimate(archive, "Game.zip", "switch") + buffer

        assertTrue("required $required must fit in $free", required < free)
    }

    @Test
    fun `an uncompressed disc image still reserves the general multiple`() {
        val archive = 10 * GB
        val reserved = ArchiveExpansion.estimate(archive, "Game.zip", "ps2")

        assertEquals(archive * ArchiveExpansion.ESTIMATE_MULTIPLIER, reserved)
    }

    @Test
    fun `compressed switch containers are packed whatever the platform says`() {
        assertTrue(ArchiveExpansion.isAlreadyPacked("Game.nsz", "unknown-platform"))
        assertTrue(ArchiveExpansion.isAlreadyPacked("Game.xcz", "unknown-platform"))
    }

    @Test
    fun `platform aliases resolve to the same answer as the canonical slug`() {
        assertTrue(ArchiveExpansion.isAlreadyPacked("Game.zip", "nintendoswitch"))
        assertTrue(ArchiveExpansion.isAlreadyPacked("Game.zip", "SWITCH"))
        assertFalse(ArchiveExpansion.isAlreadyPacked("Game.zip", "gamecube"))
    }
}
