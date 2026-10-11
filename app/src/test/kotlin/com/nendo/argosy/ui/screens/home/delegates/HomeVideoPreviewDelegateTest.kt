package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.audio.AmbientAudioManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVideoPreviewDelegateTest {
    private val audio = mockk<AmbientAudioManager>(relaxed = true)
    private val preview = HomeVideoPreviewDelegate(audio)

    @Test
    fun `a retained ready callback cannot activate behind an overlay or after it closes`() {
        var overlayOpen = false
        val retainedReady = { preview.activateVideoPreview("video-a", overlayOpen, isResumed = true) }
        preview.startVideoPreviewLoading("video-a")

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
        val oldReady = { preview.activateVideoPreview("video-a", blocked = false, isResumed = true) }
        preview.startVideoPreviewLoading("video-a")
        preview.deactivateVideoPreview()
        preview.startVideoPreviewLoading("video-b")

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
        preview.startVideoPreviewLoading("video-a")

        preview.activateVideoPreview("video-a", blocked = false, isResumed = false)

        assertFalse(preview.state.value.isVideoPreviewActive)
        verify(exactly = 0) { audio.fadeOut() }

        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)

        assertTrue(preview.state.value.isVideoPreviewActive)
        verify(exactly = 1) { audio.fadeOut() }
    }

    @Test
    fun `a ready callback after a loading error leaves audio available`() {
        preview.startVideoPreviewLoading("video-a")
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
        preview.startVideoPreviewLoading("video-a")

        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)
        preview.activateVideoPreview("video-a", blocked = false, isResumed = true)

        assertTrue(preview.state.value.isVideoPreviewActive)
        verify(exactly = 0) { audio.fadeOut() }
    }
}
