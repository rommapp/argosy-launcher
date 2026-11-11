package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.ui.screens.gamedetail.ArtCandidate
import com.nendo.argosy.ui.screens.gamedetail.stepped
import com.nendo.argosy.ui.screens.gamedetail.withRevertTile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDelegateTest {

    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val delegate = ArtworkDelegate(
        imageCacheManager = imageCacheManager,
        notificationManager = notificationManager
    )

    private val found = listOf(ArtCandidate(source = "https://example.test/a.png"))

    @Test
    fun `slot tabs cycle in order and wrap at both ends`() {
        assertEquals(ArtSlot.BACKGROUND, ArtSlot.COVER.stepped(1))
        assertEquals(ArtSlot.BOX_3D, ArtSlot.COVER.stepped(-1))
        assertEquals(ArtSlot.BOX_3D, ArtSlot.LOGO.stepped(1))
        assertEquals(ArtSlot.COVER, ArtSlot.BOX_3D.stepped(1))
    }

    @Test
    fun `slot tabs never land on the box spine or back scans`() {
        val visited = generateSequence(ArtSlot.COVER) { it.stepped(1) }.take(ArtSlot.entries.size * 2).toSet()

        assertEquals(setOf(ArtSlot.COVER, ArtSlot.BACKGROUND, ArtSlot.LOGO, ArtSlot.BOX_3D), visited)
    }

    @Test
    fun `an overridden slot leads with a revert tile`() {
        val listed = withRevertTile(ArtSlot.LOGO, setOf(ArtSlot.LOGO), found)

        assertTrue(listed.first().isRevert)
        assertEquals(found, listed.drop(1))
    }

    @Test
    fun `a slot showing server art has no revert tile`() {
        assertEquals(found, withRevertTile(ArtSlot.COVER, setOf(ArtSlot.LOGO), found))
    }

    @Test
    fun `a failed download raises the artwork error notice`() = runTest {
        coEvery { imageCacheManager.applyArtOverride(7L, ArtSlot.LOGO, any()) } returns false
        var finished = false

        delegate.applyOverride(this, 7L, ArtSlot.LOGO, "https://example.test/logo.png") { finished = true }
        advanceUntilIdle()

        assertTrue(finished)
        verify {
            notificationManager.show(
                any(),
                NotificationText.Res(R.string.gamedetail_notice_artwork_failed),
                NotificationType.ERROR,
                any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `a local path is read as a file and a success raises no notice`() = runTest {
        coEvery { imageCacheManager.applyArtOverrideFromFile(7L, ArtSlot.BACKGROUND, any()) } returns true

        delegate.applyOverride(this, 7L, ArtSlot.BACKGROUND, "/storage/emulated/0/Pictures/bg.png") {}
        advanceUntilIdle()

        coVerify { imageCacheManager.applyArtOverrideFromFile(7L, ArtSlot.BACKGROUND, "/storage/emulated/0/Pictures/bg.png") }
        coVerify(exactly = 0) { imageCacheManager.applyArtOverride(any(), any(), any()) }
        verify(exactly = 0) { notificationManager.show(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `revert clears only the chosen slot`() = runTest {
        delegate.revertOverride(this, 7L, ArtSlot.COVER) {}
        advanceUntilIdle()

        coVerify(exactly = 1) { imageCacheManager.clearArtOverride(7L, ArtSlot.COVER) }
        coVerify(exactly = 0) { imageCacheManager.clearArtOverride(7L, ArtSlot.BACKGROUND) }
        coVerify(exactly = 0) { imageCacheManager.clearArtOverride(7L, ArtSlot.LOGO) }
    }
}
