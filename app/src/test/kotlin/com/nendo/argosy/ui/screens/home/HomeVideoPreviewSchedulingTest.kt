package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.screens.home.delegates.HomeVideoPreviewDelegate
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

    private class PreviewSession {
        val audio = mockk<AmbientAudioManager>(relaxed = true)
        val delegate = HomeVideoPreviewDelegate(audio)
        val loads = mutableListOf<String>()
        var blocked = false
        private var playedGameId: Long? = null
        private val state = HomeUiState(
            recentGames = listOf(
                HomeGameUi(
                    id = 1, title = "Game", platformId = 1, platformSlug = "snes",
                    platformDisplayName = "SNES", coverPath = null, backgroundPath = null,
                    developer = null, releaseYear = null, genre = null,
                    isFavorite = false, isDownloaded = true, youtubeVideoId = "video-a"
                )
            ),
            layoutKind = HomeLayoutKind.CAROUSEL,
            videoWallpaperEnabled = true,
            videoWallpaperDelayMs = 1000
        )

        suspend fun schedule() = scheduleHomeVideoPreview(
            state = state,
            blocked = blocked,
            suppressed = false,
            alreadyPlayed = playedGameId == state.focusedGame?.id,
            canStart = { !blocked },
            deactivate = delegate::deactivateVideoPreview,
            start = { gameId, videoId ->
                playedGameId = gameId
                loads.add(videoId)
                delegate.startVideoPreviewLoading(videoId)
            }
        )
    }
}
