package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped default has to sync a library whole. The previous default whitelisted a fixed
 * thirteen regions, so a rom tagged with anything else vanished with no filter visibly set.
 */
class RomMSyncFilterRegionTest {

    private fun rom(vararg regions: String) = RomMRom(
        id = 1L,
        name = "rom",
        fileName = "rom.nes",
        filePath = "/roms/nes/rom.nes",
        platformId = 1L,
        platformSlug = "nes",
        slug = "rom",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = regions.toList(),
        languages = null,
        revision = null
    )

    @Test
    fun `default filters select nothing and blacklist`() {
        val defaults = SyncFilterPreferences()
        assertTrue(defaults.enabledRegions.isEmpty())
        assertTrue(defaults.regionMode == RegionFilterMode.EXCLUDE)
    }

    @Test
    fun `default keeps a rom tagged with an unlisted region`() {
        assertTrue(RomMSyncFilter.shouldSyncRom(rom("Netherlands"), SyncFilterPreferences()))
    }

    @Test
    fun `default keeps a rom tagged with a known region`() {
        assertTrue(RomMSyncFilter.shouldSyncRom(rom("Japan"), SyncFilterPreferences()))
    }

    @Test
    fun `default keeps an untagged rom`() {
        assertTrue(RomMSyncFilter.shouldSyncRom(rom(), SyncFilterPreferences()))
    }

    @Test
    fun `a configured blacklist still drops its selection`() {
        val filters = SyncFilterPreferences(
            enabledRegions = listOf("Japan"),
            regionMode = RegionFilterMode.EXCLUDE
        )
        assertFalse(RomMSyncFilter.shouldSyncRom(rom("Japan"), filters))
        assertTrue(RomMSyncFilter.shouldSyncRom(rom("USA"), filters))
    }

    @Test
    fun `a curated list carried over from the include default is not read as a blacklist`() {
        val carriedOver = SyncFilterPreferences(
            enabledRegions = listOf("USA", "Europe"),
            regionMode = RegionFilterMode.INCLUDE
        )
        assertTrue(RomMSyncFilter.shouldSyncRom(rom("USA"), carriedOver))
        assertFalse(RomMSyncFilter.shouldSyncRom(rom("Japan"), carriedOver))
    }

    @Test
    fun `a configured whitelist still keeps only its selection`() {
        val filters = SyncFilterPreferences(
            enabledRegions = listOf("USA"),
            regionMode = RegionFilterMode.INCLUDE
        )
        assertTrue(RomMSyncFilter.shouldSyncRom(rom("USA"), filters))
        assertFalse(RomMSyncFilter.shouldSyncRom(rom("Japan"), filters))
    }
}
