package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.data.music.library.MusicLibrary
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnContents
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MusicTrackResolver(private val library: MusicLibrary) {
    private val mutex = Mutex()
    private val contentsCache = mutableMapOf<String, MusicColumnContents>()
    private val albumCache = mutableMapOf<String, List<MusicTrack>>()

    suspend fun clear() = mutex.withLock {
        contentsCache.clear()
        albumCache.clear()
    }

    suspend fun contents(
        column: MusicColumn,
        grouping: MusicGrouping,
        filter: MusicSourceFilter
    ): MusicColumnContents? {
        val cacheKey = "${grouping.token}|${filter.token}|${column.key}"
        mutex.withLock { contentsCache[cacheKey] }?.let { return it }
        val loaded = withContext(Dispatchers.IO) { library.contents(column.key, grouping, filter) } ?: return null
        mutex.withLock { contentsCache[cacheKey] = loaded }
        return loaded
    }

    suspend fun albumTracks(album: MusicAlbum, columnTracks: List<MusicTrack>): List<MusicTrack> {
        mutex.withLock { albumCache[album.key] }?.let { return it }
        val fromColumn = columnTracks.filter { it.source == album.source && it.albumId == album.id }
        val tracks = fromColumn.ifEmpty { withContext(Dispatchers.IO) { library.albumTracks(album) } }
        mutex.withLock { albumCache[album.key] = tracks }
        return tracks
    }

    suspend fun columnTracks(
        column: MusicColumn,
        grouping: MusicGrouping,
        filter: MusicSourceFilter
    ): List<MusicTrack> {
        val contents = contents(column, grouping, filter) ?: return emptyList()
        if (column.kind == MusicColumnKind.PLAYLIST || contents.albums.isEmpty()) return contents.tracks
        return contents.albums.flatMap { albumTracks(it, contents.tracks) }
    }
}
