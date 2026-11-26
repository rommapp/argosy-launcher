package com.nendo.argosy.data.music.library

import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicColumnContents
import com.nendo.argosy.domain.model.MusicColumnsPage
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicResumePoint
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicStatTrack
import com.nendo.argosy.domain.model.MusicTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class MusicDownloadStatus { QUEUED, DOWNLOADING, DONE, FAILED }

/**
 * The Music screen's single entry point. Columns merge every configured source; when a source
 * is unreachable its downloaded tracks stand in and [MusicColumnsPage.offline] is set. The
 * RECENT column comes from the local listen history.
 * [changes] bumps whenever downloads or [MusicPlaylists] change, so callers know to reload.
 */
interface MusicLibrary {
    val changes: StateFlow<Int>

    fun configuredSources(): Flow<Set<MusicSourceKind>>

    suspend fun columns(grouping: MusicGrouping, filter: MusicSourceFilter): MusicColumnsPage

    suspend fun contents(
        columnKey: String,
        grouping: MusicGrouping,
        filter: MusicSourceFilter
    ): MusicColumnContents?

    suspend fun albumTracks(album: MusicAlbum): List<MusicTrack>

    suspend fun queueFor(tracks: List<MusicTrack>): List<MusicQueueTrack>

    /**
     * The library tracks behind listen-history [refs], in the order given and without repeats.
     * Each resolves through its album on the source, then through a finished download; a ref
     * neither can place is dropped.
     */
    suspend fun tracksForKeys(refs: List<MusicStatTrack>): List<MusicTrack>

    /**
     * The catalog albums for [albumKeys] across every configured source. Keys no source lists
     * are absent.
     */
    suspend fun albumsForKeys(albumKeys: Collection<String>): Map<String, MusicAlbum>

    /**
     * Where the active account's most recent listen on any album stopped, or null with no history.
     */
    suspend fun resumePoint(): MusicResumePoint?

    fun downloads(): Flow<Map<String, MusicDownloadStatus>>

    suspend fun download(tracks: List<MusicTrack>)

    suspend fun removeDownloads(tracks: List<MusicTrack>)
}
