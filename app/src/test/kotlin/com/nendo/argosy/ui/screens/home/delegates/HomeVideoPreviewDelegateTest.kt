package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.components.GameVideoClip
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVideoPreviewDelegateTest {
    private val audio = mockk<AmbientAudioManager>(relaxed = true)
    private val preview = HomeVideoPreviewDelegate(audio)

    private fun load(videoId: String, gameId: Long = 1L) =
        preview.startVideoPreviewLoading(gameId, GameVideoClip.YouTube(videoId))

    @Test
    fun `a retained ready callback cannot activate behind an overlay or after it closes`() {
        var overlayOpen = false
        val retainedReady = { preview.activateVideoPreview("video-a", overlayOpen, isResumed = true) }
        load("video-a")

        overlayOpen = true
        retainedReady()
        assertFalse(preview.state.value.isVideoPreviewActive)
        verify(exactly = 0) { audio.fadeOut() }

        preview.deactivateVideoPreview()
        retainedReady()
        overlayOpen = false
        retainedReady()

        assertFalse(preview.state.value.isVideoPreviewActive)
        assertFalse(preview.state.value.isVideoPreviewLoading)
        assertEquals(null, preview.state.value.videoPreviewId)
        verify(exactly = 0) { audio.fadeOut() }
        verify(exactly = 1) { audio.fadeIn() }
    }

    @Test
    fun `an old player cannot activate the replacement loading video`() {
        preview.updateFromPreferences(muteVideoPreview = false, videoWallpaperEnabled = true, videoWallpaperDelaySeconds = 3)
        val oldReady = { preview.activateVideoPreview("video-a", blocked = false, isResumed = true) }
        load("video-a")
        preview.deactivateVideoPreview()
        load("video-b")

        oldReady()

        assertFalse(preview.state.value.isVideoPreviewActive)
        assertTrue(preview.state.value.isVideoPreviewLoading)
        assertEquals("video-b", preview.state.value.videoPreviewId)
        verify(exactly = 0) { audio.fadeOut() }

        preview.activateVideoPreview("video-b", blocked = false, isResumed = true)

        assertTrue(preview.state.value.isVideoPreviewActive)
        assertFalse(preview.state.value.isVideoPreviewLoading)
        verify(exactly = 1) { audio.fadeOut() }
    }

    @Test
    fun `a backgrounded surface cannot activate a loading preview`() {
        preview.updateFromPreferences(muteVideoPreview = false, videoWallpaperEnabled = true, videoWallpaperDelaySeconds = 3)
        load("video-a")

        preview.activateVideoPreview("video-a", blocked = false, isResumed = false)

        assertFalse(preview.state.value.isVideoPreviewActive)
        verify(exactly = 0) { audio.fadeOut() }

        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)

        assertTrue(preview.state.value.isVideoPreviewActive)
        verify(exactly = 1) { audio.fadeOut() }
    }

    @Test
    fun `a ready callback after a loading error leaves audio available`() {
        load("video-a")
        preview.cancelVideoPreviewLoading()

        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)

        assertFalse(preview.state.value.isVideoPreviewActive)
        assertFalse(preview.state.value.isVideoPreviewLoading)
        verify(exactly = 0) { audio.fadeOut() }
        verify(exactly = 1) { audio.fadeIn() }
    }

    @Test
    fun `a muted ready preview leaves ambient audio available`() {
        preview.updateFromPreferences(true, true, 3)
        load("video-a")

        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)
        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)

        assertTrue(preview.state.value.isVideoPreviewActive)
        verify(exactly = 0) { audio.fadeOut() }
    }

    @Test
    fun `the takeover runs from loading through playing and expanded to the info overlay`() {
        load("video-a", gameId = 7L)
        assertEquals(VideoPreviewPhase.LOADING, preview.state.value.phase)
        assertEquals(7L, preview.state.value.gameId)

        assertFalse(preview.beginTakeover("video-a"))
        assertEquals(VideoPreviewPhase.LOADING, preview.state.value.phase)

        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)
        assertEquals(VideoPreviewPhase.PLAYING, preview.state.value.phase)

        assertFalse(preview.showTakeoverInfo("video-a"))
        assertTrue(preview.beginTakeover("video-a"))
        assertEquals(VideoPreviewPhase.EXPANDED, preview.state.value.phase)

        assertTrue(preview.showTakeoverInfo("video-a"))
        assertEquals(VideoPreviewPhase.INFO, preview.state.value.phase)
        assertTrue(preview.state.value.isVideoPreviewActive)
    }

    @Test
    fun `a timer from a replaced clip cannot expand the current one`() {
        load("video-a")
        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)
        preview.deactivateVideoPreview()
        load("video-b")
        preview.activateVideoPreview("video-b", blocked = false, isResumed = true)

        assertFalse(preview.beginTakeover("video-a"))

        assertEquals(VideoPreviewPhase.PLAYING, preview.state.value.phase)
    }

    @Test
    fun `restoring an expanded or informed takeover shrinks it and then returns to idle`() {
        listOf(false, true).forEach { informed ->
            load("video-a")
            preview.activateVideoPreview("video-a", blocked = false, isResumed = true)
            preview.beginTakeover("video-a")
            if (informed) preview.showTakeoverInfo("video-a")

            assertTrue(preview.restoreTakeover())
            assertEquals(VideoPreviewPhase.RESTORING, preview.state.value.phase)
            assertFalse(preview.restoreTakeover())

            preview.finishTakeoverRestore()
            assertEquals(VideoPreviewPhase.IDLE, preview.state.value.phase)
            assertEquals(null, preview.state.value.videoPreviewId)
            assertEquals(null, preview.state.value.gameId)
        }
    }

    @Test
    fun `a takeover that has not expanded is not restored`() {
        assertFalse(preview.restoreTakeover())
        load("video-a")
        assertFalse(preview.restoreTakeover())
        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)
        assertFalse(preview.restoreTakeover())

        assertEquals(VideoPreviewPhase.PLAYING, preview.state.value.phase)
    }

    @Test
    fun `finishing a restore does nothing outside the restoring phase`() {
        load("video-a")
        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)

        preview.finishTakeoverRestore()

        assertEquals(VideoPreviewPhase.PLAYING, preview.state.value.phase)
    }

    @Test
    fun `with nothing loaded no transition leaves idle`() {
        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)
        preview.beginTakeover("video-a")
        preview.showTakeoverInfo("video-a")
        preview.restoreTakeover()
        preview.finishTakeoverRestore()

        assertEquals(VideoPreviewPhase.IDLE, preview.state.value.phase)
        verify(exactly = 0) { audio.fadeOut() }
    }
}
