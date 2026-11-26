package com.nendo.argosy.data.music.library

import com.nendo.argosy.domain.model.MusicPlaylistEntry
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.domain.model.MusicPlaylistResult
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * Playlists merged across sources: the user's own playlists sharing a trimmed, case-insensitive
 * name form one group, and a track write lands on the half of its origin source. Order is the
 * Navidrome half then the RomM half until [move] stores a local order for the active account.
 */
interface MusicPlaylists {
    val changes: StateFlow<Int>

    suspend fun groups(filter: MusicSourceFilter): List<MusicPlaylistGroup>

    suspend fun group(key: String): MusicPlaylistGroup?

    suspend fun entries(group: MusicPlaylistGroup): List<MusicPlaylistEntry>

    suspend fun addTracks(group: MusicPlaylistGroup, tracks: List<MusicTrack>): MusicPlaylistResult

    suspend fun addTrack(group: MusicPlaylistGroup, track: MusicQueueTrack): MusicPlaylistResult

    /**
     * Creates [name] on the origin source of each track. Returns [MusicPlaylistResult.NameTaken]
     * without writing when an owned group already has that name.
     */
    suspend fun create(name: String, tracks: List<MusicTrack>): MusicPlaylistResult

    suspend fun create(name: String, track: MusicQueueTrack): MusicPlaylistResult

    /**
     * Renames every half. When a later half fails, the earlier ones are renamed back.
     */
    suspend fun rename(group: MusicPlaylistGroup, name: String): MusicPlaylistResult

    suspend fun delete(group: MusicPlaylistGroup): MusicPlaylistResult

    suspend fun remove(group: MusicPlaylistGroup, entry: MusicPlaylistEntry): MusicPlaylistResult

    /**
     * Moves the entry at [from] to [to], both indices into [entries]. Local only.
     */
    suspend fun move(group: MusicPlaylistGroup, from: Int, to: Int): MusicPlaylistResult

    suspend fun resetOrder(group: MusicPlaylistGroup)
}
