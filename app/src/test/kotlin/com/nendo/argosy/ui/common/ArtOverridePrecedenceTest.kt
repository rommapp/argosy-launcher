package com.nendo.argosy.ui.common

import com.nendo.argosy.data.local.entity.GameArtEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.toResolvedArt
import com.nendo.argosy.data.local.entity.toResolvedArtByGame
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.resolveArtPath
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.ui.screens.gamedetail.toGameDetailUi
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtOverridePrecedenceTest {

    private val source = "https://romm/covers/42.png"
    private val cached = "/cache/snes/covers/cover_42_abc.jpg"
    private val override = "/cache/snes/covers/cover_override_9_a.jpg"

    private val downloadStatus = mockk<DownloadFileStatusRepository>(relaxed = true)

    private fun game() = GameEntity(
        id = 9L,
        platformId = 1L,
        platformSlug = "snes",
        title = "Chrono Trigger",
        sortTitle = "chrono trigger",
        localPath = null,
        rommId = 42L,
        igdbId = null,
        source = GameSource.ROMM_REMOTE
    )

    private fun row(
        slot: ArtSlot,
        sourceUrl: String? = null,
        cachedPath: String? = null,
        overridePath: String? = null,
        gameId: Long = 9L
    ) = GameArtEntity(
        gameId = gameId,
        slot = slot.name,
        sourceUrl = sourceUrl,
        cachedPath = cachedPath,
        overridePath = overridePath
    )

    @Test
    fun `override wins over cached and source`() {
        assertEquals(override, resolveArtPath(override, cached, source))
    }

    @Test
    fun `override wins over cached alone`() {
        assertEquals(override, resolveArtPath(override, cached, null))
    }

    @Test
    fun `override wins over source alone`() {
        assertEquals(override, resolveArtPath(override, null, source))
    }

    @Test
    fun `override alone is used`() {
        assertEquals(override, resolveArtPath(override, null, null))
    }

    @Test
    fun `cached wins over source without an override`() {
        assertEquals(cached, resolveArtPath(null, cached, source))
    }

    @Test
    fun `cached alone is used`() {
        assertEquals(cached, resolveArtPath(null, cached, null))
    }

    @Test
    fun `source is used when nothing local exists`() {
        assertEquals(source, resolveArtPath(null, null, source))
    }

    @Test
    fun `nothing resolves to null`() {
        assertNull(resolveArtPath(null, null, null))
    }

    @Test
    fun `a row resolves through the same rule`() {
        assertEquals(override, row(ArtSlot.COVER, source, cached, override).resolvedPath)
        assertEquals(cached, row(ArtSlot.COVER, source, cached).resolvedPath)
        assertEquals(source, row(ArtSlot.COVER, source).resolvedPath)
    }

    @Test
    fun `rows resolve per slot and only the overridden slots are flagged`() {
        val art = listOf(
            row(ArtSlot.COVER, source, cached, override),
            row(ArtSlot.BACKGROUND, "https://romm/bg/42.jpg", "/cache/snes/backgrounds/bg_42_def.jpg"),
            row(ArtSlot.LOGO, "https://romm/logo/42.png")
        ).toResolvedArt()

        assertEquals(override, art.coverPath)
        assertEquals("/cache/snes/backgrounds/bg_42_def.jpg", art.backgroundPath)
        assertEquals("https://romm/logo/42.png", art.logoPath)
        assertEquals(setOf(ArtSlot.COVER), art.overriddenSlots)
    }

    @Test
    fun `box spine and back resolve from their own rows`() {
        val art = listOf(
            row(ArtSlot.BOX_SPINE, "https://romm/side/42.png", "/cache/snes/covers/box_spine_42_abc.png"),
            row(ArtSlot.BOX_BACK, "https://romm/back/42.png")
        ).toResolvedArt()

        assertEquals("/cache/snes/covers/box_spine_42_abc.png", art.boxSpinePath)
        assertEquals("https://romm/back/42.png", art.boxBackPath)
        assertNull(art.coverPath)
    }

    @Test
    fun `gradient and aspect come from the cover row only`() {
        val art = listOf(
            row(ArtSlot.COVER, source).copy(gradientColors = "{}", coverAspectRatio = 0.7f),
            row(ArtSlot.BACKGROUND, source).copy(gradientColors = "{\"bg\":1}", coverAspectRatio = 1.7f)
        ).toResolvedArt()

        assertEquals("{}", art.gradientColors)
        assertEquals(0.7f, art.coverAspectRatio)
    }

    @Test
    fun `rows group by game`() {
        val byGame = listOf(
            row(ArtSlot.COVER, source, gameId = 1L),
            row(ArtSlot.COVER, cachedPath = cached, gameId = 2L)
        ).toResolvedArtByGame()

        assertEquals(source, byGame[1L]?.coverPath)
        assertEquals(cached, byGame[2L]?.coverPath)
    }

    @Test
    fun `home tiles read the resolved art`() = runTest {
        val art = listOf(
            row(ArtSlot.COVER, source, cached, override),
            row(ArtSlot.BACKGROUND, cachedPath = "/cache/bg.jpg"),
            row(ArtSlot.LOGO, overridePath = "/cache/logo.png")
        ).toResolvedArt()

        val ui = game().toHomeGameUi(downloadStatus, art, firstScreenshotUrl = null)

        assertEquals(override, ui.coverPath)
        assertEquals("/cache/bg.jpg", ui.backgroundPath)
        assertEquals("/cache/logo.png", ui.logoPath)
    }

    @Test
    fun `library tiles read the resolved cover`() = runTest {
        val art = listOf(row(ArtSlot.COVER, source, cached)).toResolvedArt()

        assertEquals(cached, game().toLibraryGameUi(downloadStatus, art).coverPath)
    }

    @Test
    fun `game detail reads the resolved art and flags each overridden slot`() {
        val art = listOf(
            row(ArtSlot.COVER, source, cached, override),
            row(ArtSlot.BACKGROUND, overridePath = "/cache/bg_override.jpg")
        ).toResolvedArt()

        val detail = game().toGameDetailUi(
            art = art,
            screenshotRows = emptyList(),
            platformName = "SNES",
            emulatorName = null,
            canPlay = false
        )

        assertEquals(override, detail.coverPath)
        assertEquals("/cache/bg_override.jpg", detail.backgroundPath)
        assertEquals(setOf(ArtSlot.COVER, ArtSlot.BACKGROUND), detail.overriddenArtSlots)
    }

    @Test
    fun `game detail without art shows nothing overridden`() {
        val detail = game().toGameDetailUi(
            art = null,
            screenshotRows = emptyList(),
            platformName = "SNES",
            emulatorName = null,
            canPlay = false
        )

        assertNull(detail.coverPath)
        assertTrue(detail.overriddenArtSlots.isEmpty())
    }
}
