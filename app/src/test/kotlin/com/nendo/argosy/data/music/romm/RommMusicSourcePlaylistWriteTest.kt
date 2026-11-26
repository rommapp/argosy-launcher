package com.nendo.argosy.data.music.romm

import com.nendo.argosy.data.music.library.MusicPermissionException
import com.nendo.argosy.data.music.library.MusicPlaylistNameTakenException
import com.nendo.argosy.data.remote.romm.RomMMusicLibraryService
import com.nendo.argosy.data.remote.romm.RomMResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RommMusicSourcePlaylistWriteTest {

    private val service = mockk<RomMMusicLibraryService>(relaxed = true)

    private fun source(): RommMusicSource {
        val index = mockk<RommMusicIndex>(relaxed = true)
        every { index.revision } returns MutableStateFlow(0)
        every { service.isConnected() } returns true
        return RommMusicSource(
            musicLibrary = service,
            index = index,
            searchSoundtrackGames = mockk(relaxed = true),
            getMusicPlaylists = mockk(relaxed = true),
            resolveMusicQueue = mockk(relaxed = true),
            getLocalMusicTrackState = mockk(relaxed = true),
            gameDao = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            preferencesRepository = mockk(relaxed = true)
        )
    }

    @Test
    fun `a 403 on any write fails with the permission exception`() = runTest {
        val source = source()
        coEvery { service.addPlaylistTracks(4L, listOf(12L)) } returns RomMResult.Error("Forbidden", 403)
        coEvery { service.deletePlaylist(4L) } returns RomMResult.Error("Forbidden", 403)

        assertTrue(source.addToPlaylist("4", listOf("12")).exceptionOrNull() is MusicPermissionException)
        assertTrue(source.deletePlaylist("4").exceptionOrNull() is MusicPermissionException)
    }

    @Test
    fun `a 400 on rename is a taken name, a 400 on add is a plain failure`() = runTest {
        val source = source()
        coEvery { service.renamePlaylist(4L, "Mix") } returns RomMResult.Error("Duplicate", 400)
        coEvery { service.addPlaylistTracks(4L, listOf(12L)) } returns RomMResult.Error("Bad", 400)

        assertTrue(source.renamePlaylist("4", "Mix").exceptionOrNull() is MusicPlaylistNameTakenException)
        assertTrue(source.addToPlaylist("4", listOf("12")).exceptionOrNull() is IOException)
    }

    @Test
    fun `removal goes by rom file id`() = runTest {
        val source = source()
        coEvery { service.removePlaylistTracks(4L, listOf(12L)) } returns RomMResult.Success(Unit)

        assertTrue(source.removeFromPlaylist("4", entryIndex = 3, trackId = "12").isSuccess)
        coVerify { service.removePlaylistTracks(4L, listOf(12L)) }
    }
}
