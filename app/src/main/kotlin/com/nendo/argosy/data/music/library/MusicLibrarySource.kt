package com.nendo.argosy.data.music.library

import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArtist
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A server the Music screen can browse. Every call returns a failed [Result] when the server
 * is unreachable or refuses the credentials, and the library then falls back to downloads.
 * [downloadedPath] on returned tracks is always null; the library fills it from the download
 * table.
 */
interface MusicLibrarySource {
    val kind: MusicSourceKind

    /**
     * Emits when the source's catalog changed without a user action, so cached snapshots of it
     * are stale.
     */
    val catalogChanges: Flow<Unit> get() = emptyFlow()

    suspend fun isConfigured(): Boolean

    suspend fun albums(): Result<List<MusicAlbum>>

    suspend fun artists(): Result<List<MusicArtist>>

    suspend fun playlists(): Result<List<MusicPlaylist>>

    suspend fun albumTracks(albumId: String): Result<List<MusicTrack>>

    suspend fun playlistTracks(playlistId: String): Result<List<MusicTrack>>

    /**
     * Playlist writes. A track id here is the id the source's playlist API takes: the song id on
     * Navidrome, the rom file id on RomM. A write the server refuses for missing permission fails
     * with [MusicPermissionException]; a name the server already holds for this user fails with
     * [MusicPlaylistNameTakenException].
     */
    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit>

    suspend fun createPlaylist(name: String, trackIds: List<String>): Result<MusicPlaylist>

    suspend fun renamePlaylist(playlistId: String, name: String): Result<Unit>

    suspend fun deletePlaylist(playlistId: String): Result<Unit>

    /**
     * Removes one entry: Navidrome by [entryIndex] (0-based, songs may repeat), RomM by [trackId].
     */
    suspend fun removeFromPlaylist(playlistId: String, entryIndex: Int, trackId: String): Result<Unit>

    suspend fun streamSource(track: MusicTrack): MusicTrackSource.Remote?
}
