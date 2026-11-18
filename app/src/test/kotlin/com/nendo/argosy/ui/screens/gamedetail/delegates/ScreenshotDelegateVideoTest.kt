package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.ui.screens.gamedetail.ScreenshotPair
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotDelegateVideoTest {

    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val delegate = ScreenshotDelegate(
        context = mockk<Context>(relaxed = true),
        imageCacheManager = imageCacheManager,
        notificationManager = mockk<NotificationManager>(relaxed = true)
    )

    private val entries = listOf(
        ScreenshotPair(remoteUrl = "/assets/romm/resources/roms/1/video.mp4", cachedPath = null, isVideo = true),
        ScreenshotPair(remoteUrl = "https://cdn/a.jpg", cachedPath = "/cache/a.jpg")
    )

    @Test
    fun `set background refuses the video entry`() = runTest {
        delegate.openScreenshotViewer(entries, 0)

        delegate.setCurrentScreenshotAsBackground(this, 7L, entries) {}
        advanceUntilIdle()

        coVerify(exactly = 0) { imageCacheManager.setScreenshotAsBackground(any(), any()) }
    }

    @Test
    fun `set background still applies a screenshot entry`() = runTest {
        coEvery { imageCacheManager.setScreenshotAsBackground(7L, "/cache/a.jpg") } returns true
        delegate.openScreenshotViewer(entries, 1)

        delegate.setCurrentScreenshotAsBackground(this, 7L, entries) {}
        advanceUntilIdle()

        coVerify(exactly = 1) { imageCacheManager.setScreenshotAsBackground(7L, "/cache/a.jpg") }
    }

    @Test
    fun `playback toggles only on the video page and resets when the page changes`() {
        delegate.openScreenshotViewer(entries, 0)

        delegate.toggleViewerVideoPlayback(entries)
        assertTrue(delegate.state.value.viewerVideoPaused)

        delegate.moveViewerIndex(1, entries)
        assertFalse(delegate.state.value.viewerVideoPaused)
        assertEquals(1, delegate.state.value.viewerScreenshotIndex)

        delegate.toggleViewerVideoPlayback(entries)
        assertFalse(delegate.state.value.viewerVideoPaused)
    }

    @Test
    fun `closing the viewer clears a paused video`() {
        delegate.openScreenshotViewer(entries, 0)
        delegate.toggleViewerVideoPlayback(entries)

        delegate.closeScreenshotViewer()

        assertFalse(delegate.state.value.viewerVideoPaused)
    }
}
