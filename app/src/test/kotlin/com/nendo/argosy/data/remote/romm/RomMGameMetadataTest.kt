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
        achievements: Int? = null
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
    fun `image paths are left to the caller`() {
        val result = existing().withRomMetadata(rom())

        assertNull("cover is resolved to a cached file or a url by the caller", result.coverPath)
        assertNull("box art is resolved by the caller", result.boxBackPath)
        assertNull(result.boxSpinePath)
    }

    @Test
    fun `an achievement count is taken from the rom when it has one`() {
        assertEquals(3, existing().withRomMetadata(rom(achievements = 3)).achievementCount)
    }

    @Test
    fun `an absent achievement count keeps what the row already had`() {
        assertEquals(12, existing().withRomMetadata(rom(achievements = null)).achievementCount)
    }
}
