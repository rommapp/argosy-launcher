package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.dao.MusicPlaylistOrderDao
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.music.identity.MusicAccounts
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicPlaylistResult
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class MusicPlaylistsRepositoryTest {

    private val navidrome = mockk<MusicLibrarySource>(relaxed = true)
    private val romm = mockk<MusicLibrarySource>(relaxed = true)
    private val orderDao = mockk<MusicPlaylistOrderDao>(relaxed = true)
    private val accountReader = mockk<MusicAccountReader>()

    private val ndHalf = playlist("n1", MusicSourceKind.NAVIDROME, "Mix")
    private val rommHalf = playlist("4", MusicSourceKind.ROMM, "Mix")

    private fun playlist(id: String, source: MusicSourceKind, name: String) = MusicPlaylist(
        key = "${source.token}:playlist:$id",
        source = source,
        id = id,
        name = name,
        trackCount = 1,
        durationSec = 0,
        art = null,
        writable = true
    )

    private fun rommTrack(romFileId: Long) = MusicTrack(
        key = "romm:track:$romFileId",
        source = MusicSourceKind.ROMM,
        id = "/stream/$romFileId",
        title = "Track $romFileId",
        artist = null,
        album = null,
        albumId = null,
        trackNumber = null,
        durationSec = null,
        year = null,
        genre = null,
        art = null,
        downloadedPath = null,
        romFileId = romFileId
    )

    private fun repository(): MusicPlaylistsRepository {
        val registry = mockk<MusicSourceRegistry>()
        every { registry.all } returns listOf(navidrome, romm)
        every { registry.of(MusicSourceKind.NAVIDROME) } returns navidrome
        every { registry.of(MusicSourceKind.ROMM) } returns romm
        every { navidrome.kind } returns MusicSourceKind.NAVIDROME
        every { romm.kind } returns MusicSourceKind.ROMM
        coEvery { navidrome.isConfigured() } returns true
        coEvery { romm.isConfigured() } returns true
        coEvery { navidrome.playlists() } returns Result.success(listOf(ndHalf))
        coEvery { romm.playlists() } returns Result.success(listOf(rommHalf))
        coEvery { orderDao.names(any()) } returns emptyList()
        coEvery { orderDao.get(any(), any()) } returns null
        coEvery { accountReader.current() } returns MusicAccounts(7L, "", "", "")
        return MusicPlaylistsRepository(registry, orderDao, accountReader)
    }

    @Test
    fun `rename rolls the first half back when the second half fails`() = runTest {
        val repository = repository()
        coEvery { navidrome.renamePlaylist(any(), any()) } returns Result.success(Unit)
        coEvery { romm.renamePlaylist("4", "Party") } returns Result.failure(IOException("down"))
        val group = repository.groups(MusicSourceFilter.ALL).single()

        val result = repository.rename(group, "Party")

        assertEquals(MusicPlaylistResult.Failed, result)
        coVerify(ordering = Ordering.ORDERED) {
            navidrome.renamePlaylist("n1", "Party")
            romm.renamePlaylist("4", "Party")
            navidrome.renamePlaylist("n1", "Mix")
        }
        coVerify(exactly = 0) { orderDao.rename(any(), any(), any()) }
        assertEquals(1, repository.changes.value)
    }

    @Test
    fun `a rename that succeeds on every half re-keys the order row`() = runTest {
        val repository = repository()
        coEvery { navidrome.renamePlaylist(any(), any()) } returns Result.success(Unit)
        coEvery { romm.renamePlaylist(any(), any()) } returns Result.success(Unit)
        val group = repository.groups(MusicSourceFilter.ALL).single()

        repository.rename(group, "Party")

        coVerify { orderDao.rename(7L, "mix", "party") }
    }

    @Test
    fun `a romm 403 on add maps to needs romm permission`() = runTest {
        val repository = repository()
        coEvery { romm.addToPlaylist("4", listOf("12")) } returns Result.failure(MusicPermissionException())
        val group = repository.groups(MusicSourceFilter.ALL).single()

        val result = repository.addTracks(group, listOf(rommTrack(12)))

        assertEquals(MusicPlaylistResult.NeedsRommPermission, result)
        assertEquals(0, repository.changes.value)
    }

    @Test
    fun `a missing romm half is created under the group name`() = runTest {
        val repository = repository()
        coEvery { romm.playlists() } returns Result.success(emptyList())
        coEvery { romm.createPlaylist("Mix", listOf("12")) } returns Result.failure(MusicPermissionException())
        val group = repository.groups(MusicSourceFilter.ALL).single()

        val result = repository.addTracks(group, listOf(rommTrack(12)))

        assertEquals(MusicPlaylistResult.NeedsRommPermission, result)
        coVerify { romm.createPlaylist("Mix", listOf("12")) }
    }

    @Test
    fun `create onto an owned name returns the existing group without writing`() = runTest {
        val repository = repository()
        val existing = repository.groups(MusicSourceFilter.ALL).single()

        val result = repository.create(" mix ", listOf(rommTrack(12)))

        assertEquals(MusicPlaylistResult.NameTaken(existing), result)
        coVerify(exactly = 0) { romm.createPlaylist(any(), any()) }
    }
}
