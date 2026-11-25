package com.nendo.argosy.data.music.romm

import com.nendo.argosy.data.local.dao.RommMusicIndexDao
import com.nendo.argosy.data.local.entity.RommMusicTrackEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMMusicLibraryService
import com.nendo.argosy.data.remote.romm.RomMMusicTrack
import com.nendo.argosy.data.remote.romm.RomMMusicTrackPage
import com.nendo.argosy.data.remote.romm.RomMResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RommMusicIndexTest {

    private val owner = 7L
    private lateinit var musicLibrary: RomMMusicLibraryService
    private lateinit var dao: RommMusicIndexDao
    private lateinit var syncPreferences: SyncPreferencesRepository
    private lateinit var index: RommMusicIndex

    @Before
    fun setup() {
        musicLibrary = mockk()
        every { musicLibrary.isConnected() } returns true
        every { musicLibrary.capabilities() } returns RomMCapabilities.NONE.copy(supportsMusicApi = true)
        dao = mockk(relaxed = true)
        syncPreferences = mockk()
        coEvery { syncPreferences.getRommUserId() } returns owner
        index = RommMusicIndex(musicLibrary, dao, syncPreferences)
    }

    private fun tracks(from: Long, count: Int) = (from until from + count).map { id ->
        RomMMusicTrack(
            romFileId = id,
            romId = id / 10,
            platformId = 1L,
            platformSlug = "snes",
            platformName = "SNES",
            streamUrl = "/api/raw/$id"
        )
    }

    private fun page(items: List<RomMMusicTrack>, total: Int) =
        RomMResult.Success(RomMMusicTrackPage(items = items, total = total))

    @Test
    fun `a complete walk replaces the owner's rows with every page and advances the revision`() = runTest {
        coEvery { musicLibrary.getTrackPage(any(), 500, 0) } returns page(tracks(0, 500), 620)
        coEvery { musicLibrary.getTrackPage(any(), 500, 500) } returns page(tracks(500, 120), 620)
        val stored = slot<List<RommMusicTrackEntity>>()
        coEvery { dao.replaceForOwner(owner, capture(stored), any()) } returns Unit

        assertTrue(index.refresh(owner))

        assertEquals(620, stored.captured.size)
        assertTrue(stored.captured.all { it.ownerUserId == owner })
        assertEquals((0L until 620L).toList(), stored.captured.map { it.romFileId })
        assertEquals(1, index.revision.value)
    }

    @Test
    fun `a failed page keeps the old index`() = runTest {
        coEvery { musicLibrary.getTrackPage(any(), 500, 0) } returns page(tracks(0, 500), 620)
        coEvery { musicLibrary.getTrackPage(any(), 500, 500) } returns RomMResult.Error("Failed", 502)

        assertFalse(index.refresh(owner))

        coVerify(exactly = 0) { dao.replaceForOwner(any(), any(), any()) }
        coVerify(exactly = 0) { dao.deleteTracks(any()) }
        assertEquals(0, index.revision.value)
    }

    @Test
    fun `a server without the music api is never walked`() = runTest {
        every { musicLibrary.capabilities() } returns RomMCapabilities.NONE

        assertFalse(index.refresh(owner))

        coVerify(exactly = 0) { musicLibrary.getTrackPage(any(), any(), any()) }
        coVerify(exactly = 0) { dao.replaceForOwner(any(), any(), any()) }
    }

    @Test
    fun `a walk that ends after the account switched is not stored`() = runTest {
        coEvery { musicLibrary.getTrackPage(any(), 500, 0) } returns page(tracks(0, 3), 3)
        coEvery { syncPreferences.getRommUserId() } returns 8L

        assertFalse(index.refresh(owner))

        coVerify(exactly = 0) { dao.replaceForOwner(any(), any(), any()) }
    }

    @Test
    fun `an empty server stores an empty index for the owner`() = runTest {
        coEvery { musicLibrary.getTrackPage(any(), 500, 0) } returns page(emptyList(), 0)

        assertTrue(index.refresh(owner))

        coVerify(exactly = 1) { dao.replaceForOwner(owner, emptyList(), any()) }
    }
}
