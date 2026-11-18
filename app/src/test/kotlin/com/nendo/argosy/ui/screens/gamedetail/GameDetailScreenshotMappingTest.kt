package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameScreenshotEntity
import com.nendo.argosy.data.local.entity.ScreenshotSource
import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDetailScreenshotMappingTest {

    private val game = GameEntity(
        id = 7,
        platformId = 1,
        platformSlug = "snes",
        title = "Game",
        sortTitle = "game",
        localPath = null,
        rommId = 42,
        igdbId = null,
        source = GameSource.ROMM_REMOTE
    )

    private val official = GameScreenshotEntity(7L, 0, "https://cdn/a.jpg", "/cache/ss_42_0_a.jpg", "https://cdn/a.jpg")

    private fun gallery(source: String, position: Int, id: Long, cached: Boolean = true) = GameScreenshotEntity(
        gameId = 7L,
        position = position,
        sourceUrl = "https://romm/api/screenshots/$id/content",
        cachedPath = if (cached) "/cache/uss_42_${id}_abc.png" else null,
        source = source,
        rommScreenshotId = id
    )

    private val videoPath = "roms/42/video/normalized.mp4"

    private fun detail(rows: List<GameScreenshotEntity>, entity: GameEntity = game) = entity.toGameDetailUi(
        art = null,
        screenshotRows = rows,
        platformName = "SNES",
        emulatorName = null,
        canPlay = false
    )

    @Test
    fun `a gameplay video leads the strip ahead of every screenshot`() {
        val shots = detail(listOf(official), game.copy(videoNormalizedPath = videoPath)).screenshots

        assertEquals(listOf(videoPath, "https://cdn/a.jpg"), shots.map { it.remoteUrl })
        assertEquals(listOf(true, false), shots.map { it.isVideo })
        assertNull(shots.first().cachedPath)
    }

    @Test
    fun `a game with only a gameplay video still has one strip entry`() {
        val shots = detail(emptyList(), game.copy(videoNormalizedPath = videoPath)).screenshots

        assertTrue(shots.single().isVideo)
    }

    @Test
    fun `a game without a gameplay video has no video entry`() {
        val shots = detail(listOf(official), game.copy(youtubeVideoId = "abc123")).screenshots

        assertFalse(shots.any { it.isVideo })
        assertEquals(listOf("https://cdn/a.jpg"), shots.map { it.remoteUrl })
    }

    @Test
    fun `a blank video path adds no entry`() {
        assertTrue(detail(emptyList(), game.copy(videoNormalizedPath = " ")).screenshots.isEmpty())
    }

    @Test
    fun `refreshing the gallery keeps the gameplay video first`() {
        val loaded = detail(listOf(official), game.copy(videoNormalizedPath = videoPath))

        val refreshed = screenshotEntries(loaded.videoPath, listOf(official, gallery(ScreenshotSource.OWN, 0, 9L)))

        assertEquals(
            listOf(videoPath, "https://romm/api/screenshots/9/content", "https://cdn/a.jpg"),
            refreshed.map { it.remoteUrl }
        )
        assertTrue(refreshed.first().isVideo)
    }

    @Test
    fun `own shots lead, other users' follow, official screenshots close the list`() {
        val rows = listOf(
            official,
            gallery(ScreenshotSource.COMMUNITY, 0, 20L),
            gallery(ScreenshotSource.OWN, 1, 8L),
            gallery(ScreenshotSource.OWN, 0, 9L)
        )

        assertEquals(
            listOf(
                "https://romm/api/screenshots/9/content",
                "https://romm/api/screenshots/8/content",
                "https://romm/api/screenshots/20/content",
                "https://cdn/a.jpg"
            ),
            detail(rows).screenshots.map { it.remoteUrl }
        )
    }

    @Test
    fun `a gallery shot carries its content url and cached file separately`() {
        val shot = detail(listOf(gallery(ScreenshotSource.OWN, 0, 9L))).screenshots.single()

        assertEquals("https://romm/api/screenshots/9/content", shot.remoteUrl)
        assertEquals("/cache/uss_42_9_abc.png", shot.cachedPath)
    }

    @Test
    fun `a gallery shot without a cached file streams from its content url like an official one`() {
        val rows = listOf(official, gallery(ScreenshotSource.COMMUNITY, 0, 20L, cached = false))

        val shots = detail(rows).screenshots

        assertEquals(listOf("https://romm/api/screenshots/20/content", "https://cdn/a.jpg"), shots.map { it.remoteUrl })
        assertEquals(null, shots.first().cachedPath)
    }

    @Test
    fun `the background fallback is the first official screenshot, never a gallery shot`() {
        val rows = listOf(gallery(ScreenshotSource.OWN, 0, 9L), official)

        assertEquals("https://cdn/a.jpg", detail(rows).backgroundPath)
    }
}
