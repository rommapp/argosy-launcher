package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.preferences.SyncFilterPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Variant exclusions read a rom's release tags from the tag list, the revision and the file
 * name, because a rom matched against metadata carries a clean title that holds none of them.
 * Debug builds ride with prototypes, both being unreleased internal builds, while pirate,
 * unlicensed and aftermarket carts are originals rather than modified dumps and get their own
 * switch.
 */
class RomMSyncFilterVariantTest {

    private fun rom(
        fsName: String,
        name: String = "Clean IGDB Title",
        revision: String? = null,
        tags: List<String>? = null
    ) = RomMRom(
        id = 1L,
        name = name,
        fileName = "$fsName.zip",
        filePath = "/roms/gba/$fsName.zip",
        platformId = 1L,
        platformSlug = "gba",
        slug = "rom",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = null,
        languages = null,
        revision = revision,
        tags = tags
    )

    private val prototypeOnly = SyncFilterPreferences(
        excludeBeta = false,
        excludePrototype = true,
        excludeDemo = false
    )

    private val unofficialOnly = SyncFilterPreferences(
        excludeBeta = false,
        excludePrototype = false,
        excludeDemo = false,
        excludeUnofficial = true
    )

    private val nothingExcluded = SyncFilterPreferences(
        excludeBeta = false,
        excludePrototype = false,
        excludeDemo = false
    )

    @Test
    fun `debug builds are excluded by the prototype filter`() {
        val debug = rom(
            "Pokemon - Rubin-Edition (Germany) (Debug Version)",
            name = "Pokemon Ruby Version",
            tags = listOf("Debug Version")
        )

        assertFalse(RomMSyncFilter.shouldSyncRom(debug, prototypeOnly))
        assertTrue(RomMSyncFilter.shouldSyncRom(debug, nothingExcluded))
    }

    /**
     * A rom RomM matched against metadata carries the clean IGDB title, so every release tag
     * lives in the tag list and the file name. Reading the title alone matched nothing.
     */
    @Test
    fun `variant tags are read when the title is the clean igdb one`() {
        val cases = mapOf(
            "'98 Koushien (Japan) (Demo)" to listOf("Demo"),
            "Findet Nemo (Germany) (Beta)" to listOf("Beta"),
            "GP-1 Racing (USA) (Proto)" to listOf("Proto"),
            "ASO - Armored Scrum Object (Japan) (En) (Sample)" to listOf("Sample")
        )

        cases.forEach { (fsName, tags) ->
            val excluded = rom(fsName, name = "Clean IGDB Title", tags = tags)
            assertFalse(fsName, RomMSyncFilter.shouldSyncRom(excluded, SyncFilterPreferences()))
        }
    }

    @Test
    fun `numbered variant tags still match`() {
        val beta2 = rom("Some Game (USA) (Beta 2)", tags = listOf("Beta 2"))
        val proto1 = rom("Some Game (USA) (Proto 1)", tags = listOf("Proto 1"))

        assertFalse(RomMSyncFilter.shouldSyncRom(beta2, SyncFilterPreferences()))
        assertFalse(RomMSyncFilter.shouldSyncRom(proto1, SyncFilterPreferences()))
    }

    @Test
    fun `prototypes still match after debug was folded in`() {
        assertFalse(
            RomMSyncFilter.shouldSyncRom(rom("Some Game (USA) (Proto)", tags = listOf("Proto")), prototypeOnly)
        )
        assertFalse(RomMSyncFilter.shouldSyncRom(rom("Some Game", revision = "proto"), prototypeOnly))
    }

    @Test
    fun `pirate unlicensed and aftermarket are excluded together`() {
        val names = listOf(
            "Pokemon - Leaf Green (USA) (Pirate)",
            "Some Game (Taiwan) (Unl)",
            "Some Game (USA) (Unlicensed)",
            "Some Game (USA) (Aftermarket)"
        )

        names.forEach { name ->
            assertFalse(name, RomMSyncFilter.shouldSyncRom(rom(name), unofficialOnly))
            assertTrue(name, RomMSyncFilter.shouldSyncRom(rom(name), nothingExcluded))
        }
        val tagged = rom(
            "Pokemon - Leaf Green (USA) (Pirate)",
            name = "Pokemon LeafGreen Version",
            tags = listOf("Pirate")
        )
        assertFalse(RomMSyncFilter.shouldSyncRom(tagged, unofficialOnly))
    }

    @Test
    fun `unofficial is read from revision and tags as well as the name`() {
        assertFalse(RomMSyncFilter.shouldSyncRom(rom("Some Game", revision = "pirate"), unofficialOnly))
        assertFalse(
            RomMSyncFilter.shouldSyncRom(rom("Some Game", tags = listOf("aftermarket")), unofficialOnly)
        )
    }

    @Test
    fun `an ordinary release is untouched by either filter`() {
        val ordinary = rom("Mario Party 2 (Europe) (En,Fr,De,Es,It)", name = "Mario Party 2")

        assertTrue(RomMSyncFilter.shouldSyncRom(ordinary, prototypeOnly))
        assertTrue(RomMSyncFilter.shouldSyncRom(ordinary, unofficialOnly))
        assertTrue(RomMSyncFilter.shouldSyncRom(ordinary, SyncFilterPreferences()))
    }

    @Test
    fun `a german release with a hardware tag is untouched`() {
        val pokemon = rom(
            "Pokemon - Rote Edition (Germany) (SGB Enhanced)",
            name = "Pokemon Red Version",
            tags = listOf("SGB Enhanced")
        )

        assertTrue(RomMSyncFilter.shouldSyncRom(pokemon, prototypeOnly))
        assertTrue(RomMSyncFilter.shouldSyncRom(pokemon, unofficialOnly))
        assertTrue(RomMSyncFilter.shouldSyncRom(pokemon, SyncFilterPreferences()))
    }

    @Test
    fun `unofficial stays off by default so aftermarket carts keep syncing`() {
        assertFalse(SyncFilterPreferences().excludeUnofficial)
        assertTrue(
            RomMSyncFilter.shouldSyncRom(
                rom("Some Game (USA) (Aftermarket)", tags = listOf("Aftermarket")),
                SyncFilterPreferences()
            )
        )
    }
}
