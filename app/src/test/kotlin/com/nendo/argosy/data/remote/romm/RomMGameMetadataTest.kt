package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.util.SearchNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A library sync and a per-game refresh both rebuild a row from the same RomM model. They
 * used to carry separate field lists, so a field added to sync never reached refresh and
 * refreshing a game quietly dropped its box art. Both go through this mapper now; these
 * pin what it owns so the two cannot drift apart again.
 */
class RomMGameMetadataTest {

    private fun rom(
        name: String = "Ocarina of Time 3D",
        hasSoundtrack: Boolean = false,
        achievements: Int? = null,
        titleId: String? = null,
        saveTarget: String? = null,
        saveTargetLayout: String? = null
    ) = RomMRom(
        id = 42L,
        platformId = 1L,
        platformSlug = "3ds",
        name = name,
        slug = "oot",
        fileName = "oot.3ds",
        filePath = "/roms/3ds/oot.3ds",
        igdbId = 1234L,
        mobyId = 55L,
        summary = "A summary",
        coverSmall = null,
        coverLarge = null,
        regions = listOf("USA"),
        languages = listOf("en"),
        revision = null,
        hasSoundtrack = hasSoundtrack,
        hasManual = true,
        manualPath = "/manuals/oot.pdf",
        crcHash = "crc",
        md5Hash = "md5",
        sha1Hash = "sha1",
        youtubeVideoId = "abc123",
        alternativeNames = listOf("Zelda OoT 3D"),
        titleId = titleId,
        saveTarget = saveTarget,
        saveTargetLayout = saveTargetLayout,
        raMetadata = achievements?.let { count ->
            RomMRAMetadata(
                achievements = List(count) {
                    RomMAchievement(
                        raId = it.toLong(),
                        badgeId = null,
                        title = "Achievement $it",
                        description = null,
                        points = 5,
                        type = null,
                        badgeUrl = null,
                        badgeUrlLock = null
                    )
                }
            )
        }
    )

    private fun existing() = GameEntity(
        platformId = 1L,
        platformSlug = "3ds",
        title = "stale title",
        sortTitle = "stale title",
        localPath = null,
        rommId = 42L,
        igdbId = null,
        source = GameSource.ROMM_REMOTE,
        playCount = 7,
        isFavorite = true,
        achievementCount = 12
    )

    @Test
    fun `fields that come from the rom are taken from it`() {
        val result = existing().withRomMetadata(rom())

        assertEquals("Ocarina of Time 3D", result.title)
        assertEquals("A summary", result.description)
        assertEquals("USA", result.regions)
        assertEquals("en", result.languages)
        assertEquals(55L, result.mobyId)
        assertEquals("md5", result.md5Hash)
        assertEquals("abc123", result.youtubeVideoId)
        assertEquals("Zelda OoT 3D", result.alternativeNames)
        assertTrue(result.hasManual)
    }

    @Test
    fun `a renamed game gets a fresh search index`() {
        val result = existing().withRomMetadata(rom(name = "Pokemon Sun"))

        assertEquals("Pokemon Sun", result.title)
        assertEquals(SearchNormalizer.normalize("Pokemon Sun"), result.searchTitle)
    }

    @Test
    fun `soundtrack availability is carried over`() {
        assertTrue(existing().withRomMetadata(rom(hasSoundtrack = true)).remoteHasSoundtrack)
        assertFalse(existing().withRomMetadata(rom(hasSoundtrack = false)).remoteHasSoundtrack)
    }

    @Test
    fun `local state the rom knows nothing about survives`() {
        val result = existing().withRomMetadata(rom())

        assertEquals(7, result.playCount)
        assertTrue(result.isFavorite)
        assertEquals(GameSource.ROMM_REMOTE, result.source)
    }

    @Test
    fun `an achievement count is taken from the rom when it has one`() {
        assertEquals(3, existing().withRomMetadata(rom(achievements = 3)).achievementCount)
    }

    @Test
    fun `an absent achievement count keeps what the row already had`() {
        assertEquals(12, existing().withRomMetadata(rom(achievements = null)).achievementCount)
    }

    @Test
    fun `a server title id fills a row that has none`() {
        val result = existing().withRomMetadata(rom(titleId = "0004000000033500"))

        assertEquals("0004000000033500", result.titleId)
    }

    @Test
    fun `a locally extracted title id outranks the server's`() {
        val local = existing().copy(titleId = "LOCAL0001")

        val result = local.withRomMetadata(rom(titleId = "0004000000033500"))

        assertEquals("LOCAL0001", result.titleId)
    }

    @Test
    fun `a locked row takes no title id from the server`() {
        val locked = existing().copy(titleId = null, titleIdLocked = true)

        assertNull(locked.withRomMetadata(rom(titleId = "0004000000033500")).titleId)
    }

    @Test
    fun `switch keeps its locally extracted identity`() {
        val switchRow = existing().copy(platformSlug = "switch")

        val result = switchRow.withRomMetadata(
            rom(titleId = "0100000000010000", saveTarget = "0100000000010000")
        )

        assertNull("the server does not decrypt switch headers", result.titleId)
        assertNull(result.saveTarget)
    }

    @Test
    fun `a save target rides along with the title id`() {
        val result = existing().withRomMetadata(
            rom(titleId = "0004000000033500", saveTarget = "00040000", saveTargetLayout = "folder-prefix")
        )

        assertEquals("00040000", result.saveTarget)
        assertEquals("folder-prefix", result.saveTargetLayout)
    }

    @Test
    fun `the sibling group key and hack flag come from the rom`() {
        val result = existing().withRomMetadata(rom())

        assertEquals("igdb-1-1234", result.siblingGroupKey)
        assertFalse(result.isHackVariant)
        assertTrue(existing().withRomMetadata(rom().copy(tags = listOf("Hack"))).isHackVariant)
    }

    @Test
    fun `a save target already on the row is kept`() {
        val local = existing().copy(saveTarget = "LOCALTARGET", saveTargetLayout = "file-exact")

        val result = local.withRomMetadata(rom(saveTarget = "00040000", saveTargetLayout = "folder-prefix"))

        assertEquals("LOCALTARGET", result.saveTarget)
        assertEquals("file-exact", result.saveTargetLayout)
    }
}
