package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeColumnLabel
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.components.GameVideoClip
import com.nendo.argosy.ui.components.gameVideoClip
import com.nendo.argosy.ui.screens.home.delegates.HomeVideoPreviewDelegate
import com.nendo.argosy.ui.screens.home.delegates.VideoPreviewPhase
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeVideoPreviewSchedulingTest {
    @Test
    fun `closing an overlay does not schedule another load for an already started game`() = runTest {
        val preview = PreviewSession()
        launch { preview.schedule() }
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(preview.delegate.state.value.isVideoPreviewLoading)
        assertEquals(listOf("video-a"), preview.loads)

        preview.blocked = true
        preview.schedule()
        assertFalse(preview.delegate.state.value.isVideoPreviewLoading)
        preview.blocked = false
        preview.schedule()
        advanceTimeBy(2000)
        runCurrent()

        assertEquals(listOf("video-a"), preview.loads)
        assertFalse(preview.delegate.state.value.isVideoPreviewLoading)
        assertFalse(preview.delegate.state.value.isVideoPreviewActive)
        verify(exactly = 0) { preview.audio.fadeOut() }
    }

    @Test
    fun `an overlay cancels the pending delay and closing it permits the first load`() = runTest {
        val preview = PreviewSession()
        val pending = launch { preview.schedule() }
        runCurrent()
        advanceTimeBy(500)

        preview.blocked = true
        pending.cancel()
        preview.schedule()
        advanceTimeBy(2000)
        runCurrent()
        assertTrue(preview.loads.isEmpty())

        preview.blocked = false
        launch { preview.schedule() }
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()

        assertEquals(listOf("video-a"), preview.loads)
        assertTrue(preview.delegate.state.value.isVideoPreviewLoading)
    }

    @Test
    fun `a blocker arriving during the delay prevents a load before effect cancellation`() = runTest {
        val preview = PreviewSession()
        launch { preview.schedule() }
        runCurrent()
        advanceTimeBy(500)

        preview.blocked = true
        advanceTimeBy(1000)
        runCurrent()

        assertTrue(preview.loads.isEmpty())
        assertFalse(preview.delegate.state.value.isVideoPreviewLoading)
    }

    @Test
    fun `Columns schedules the focused column game after the delay`() = runTest {
        val preview = PreviewSession(state = columnsState(game(youtubeVideoId = "video-c")))
        launch { preview.schedule() }
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertTrue(preview.loads.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("video-c"), preview.loads)
        assertEquals(VideoPreviewPhase.LOADING, preview.delegate.state.value.phase)
        assertEquals(1L, preview.delegate.state.value.gameId)
    }

    @Test
    fun `Columns keeps the setting, blocked, suppressed and played gates`() = runTest {
        val game = game(youtubeVideoId = "video-c")
        listOf(
            PreviewSession(state = columnsState(game).copy(videoWallpaperEnabled = false)),
            PreviewSession(state = columnsState(game)).apply { blocked = true },
            PreviewSession(state = columnsState(game), suppressed = true),
            PreviewSession(state = columnsState(game), playedGameId = 1L)
        ).forEach { preview ->
            launch { preview.schedule() }
            runCurrent()
            advanceTimeBy(2000)
            runCurrent()

            assertTrue(preview.loads.isEmpty())
            assertEquals(VideoPreviewPhase.IDLE, preview.delegate.state.value.phase)
        }
    }

    @Test
    fun `the grid layouts still never schedule a preview`() = runTest {
        listOf(HomeLayoutKind.AUTO_GRID, HomeLayoutKind.CUSTOM_GRID).forEach { kind ->
            val preview = PreviewSession(state = carouselState(game(youtubeVideoId = "video-a")).copy(layoutKind = kind))
            launch { preview.schedule() }
            runCurrent()
            advanceTimeBy(2000)
            runCurrent()

            assertTrue(preview.loads.isEmpty())
        }
    }

    @Test
    fun `a game with no clip never leaves idle`() = runTest {
        val preview = PreviewSession(state = columnsState(game(youtubeVideoId = null)))
        launch { preview.schedule() }
        runCurrent()
        advanceTimeBy(2000)
        runCurrent()

        assertTrue(preview.loads.isEmpty())
        assertEquals(VideoPreviewPhase.IDLE, preview.delegate.state.value.phase)
    }

    @Test
    fun `the server clip is preferred over the trailer`() = runTest {
        val preview = PreviewSession(
            state = columnsState(game(youtubeVideoId = "video-c", videoNormalizedPath = "roms/1/video.mp4"))
        )
        launch { preview.schedule() }
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()

        assertEquals(listOf("https://romm/roms/1/video.mp4"), preview.loads)
        assertTrue(preview.delegate.state.value.clip is GameVideoClip.Server)
    }

    @Test
    fun `reduced motion never schedules a preview on either video layout`() = runTest {
        listOf(
            carouselState(game(youtubeVideoId = "video-a")),
            columnsState(game(youtubeVideoId = "video-c"))
        ).forEach { state ->
            val preview = PreviewSession(state = state, reducedMotion = true)
            launch { preview.schedule() }
            runCurrent()
            advanceTimeBy(2000)
            runCurrent()

            assertTrue(preview.loads.isEmpty())
            assertEquals(VideoPreviewPhase.IDLE, preview.delegate.state.value.phase)
        }
    }

    private class PreviewSession(
        private val state: HomeUiState = carouselState(game(youtubeVideoId = "video-a")),
        private val suppressed: Boolean = false,
        private val reducedMotion: Boolean = false,
        private var playedGameId: Long? = null
    ) {
        val audio = mockk<AmbientAudioManager>(relaxed = true)
        val delegate = HomeVideoPreviewDelegate(audio)
        val loads = mutableListOf<String>()
        var blocked = false

        suspend fun schedule() = scheduleHomeVideoPreview(
            state = state,
            blocked = blocked,
            suppressed = suppressed,
            reducedMotion = reducedMotion,
            alreadyPlayed = playedGameId == state.focusedGame?.id,
            clipFor = { gameVideoClip(it.videoNormalizedPath?.let { path -> "https://romm/$path" }, it.youtubeVideoId) },
            canStart = { !blocked },
            deactivate = delegate::deactivateVideoPreview,
            start = { gameId, clip ->
                playedGameId = gameId
                loads.add(clip.key)
                delegate.startVideoPreviewLoading(gameId, clip)
            }
        )
    }

    private companion object {
        fun game(youtubeVideoId: String?, videoNormalizedPath: String? = null) = HomeGameUi(
            id = 1, title = "Game", platformId = 1, platformSlug = "snes",
            platformDisplayName = "SNES", platformShortName = "SNES", coverPath = null, backgroundPath = null,
            developer = null, releaseYear = null, genre = null,
            isFavorite = false, isDownloaded = true, youtubeVideoId = youtubeVideoId,
            videoNormalizedPath = videoNormalizedPath
        )

        fun carouselState(game: HomeGameUi) = HomeUiState(
            recentGames = listOf(game),
            layoutKind = HomeLayoutKind.CAROUSEL,
            videoWallpaperEnabled = true,
            videoWallpaperDelayMs = 1000
        )

        fun columnsState(game: HomeGameUi) = HomeUiState(
            layoutKind = HomeLayoutKind.COLUMNS,
            columns = ColumnsState(
                columns = listOf(
                    HomeColumnUi(
                        id = "theme:CONTINUE",
                        label = HomeColumnLabel.Theme(HomeColumnTheme.CONTINUE),
                        items = listOf(HomeRowItem.Game(game))
                    )
                ),
                isLoaded = true
            ),
            videoWallpaperEnabled = true,
            videoWallpaperDelayMs = 1000
        )
    }
}
