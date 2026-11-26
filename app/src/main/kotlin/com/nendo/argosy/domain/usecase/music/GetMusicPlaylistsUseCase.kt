package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.music.BgmPlaylistRepository
import com.nendo.argosy.data.remote.romm.RomMMusicLibraryService
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.model.MusicPlaylistCatalog
import com.nendo.argosy.domain.model.MusicPlaylistCatalogEntry
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.domain.model.MusicServerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetMusicPlaylistsUseCase @Inject constructor(
    private val bgmPlaylistRepository: BgmPlaylistRepository,
    private val musicLibrary: RomMMusicLibraryService
) {
    suspend operator fun invoke(): MusicPlaylistCatalog = withContext(Dispatchers.IO) {
        val launcher = MusicPlaylistCatalogEntry(
            selection = MusicSelection.Launcher,
            trackCount = bgmPlaylistRepository.playableTracks().size
        )
        val (serverEntries, status) = serverPlaylists()
        MusicPlaylistCatalog(listOf(launcher) + serverEntries, status)
    }

    private suspend fun serverPlaylists(): Pair<List<MusicPlaylistCatalogEntry>, MusicServerStatus> {
        if (!musicLibrary.isConnected()) return emptyList<MusicPlaylistCatalogEntry>() to MusicServerStatus.OFFLINE
        if (!musicLibrary.capabilities().supportsMusicPlaylists) {
            return emptyList<MusicPlaylistCatalogEntry>() to MusicServerStatus.UNSUPPORTED
        }
        return when (val result = musicLibrary.getPlaylists()) {
            is RomMResult.Success -> result.data
                .sortedBy { it.name.lowercase() }
                .map { playlist ->
                    MusicPlaylistCatalogEntry(
                        selection = MusicSelection.ServerPlaylist(playlist.id, playlist.name),
                        trackCount = playlist.trackCount,
                        ownerName = playlist.ownerUsername
                    )
                } to MusicServerStatus.AVAILABLE
            is RomMResult.Error -> emptyList<MusicPlaylistCatalogEntry>() to musicServerStatusFor(result.code)
        }
    }
}

fun musicServerStatusFor(httpCode: Int?): MusicServerStatus = when (httpCode) {
    401, 403 -> MusicServerStatus.UNAUTHORIZED
    404 -> MusicServerStatus.UNSUPPORTED
    else -> MusicServerStatus.FAILED
}
