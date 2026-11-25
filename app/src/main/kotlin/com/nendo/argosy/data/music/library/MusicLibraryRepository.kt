package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.MusicDownloadDao
import com.nendo.argosy.data.local.dao.MusicListenDao
import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.music.identity.MusicTrackIdentities
import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicColumnContents
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicColumnsPage
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicResumePoint
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicStatTrack
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.domain.music.MusicKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val SNAPSHOT_TTL_MS = 60_000L
private const val RECENT_LIMIT = 20
private const val RECENT_SCAN = 60

@Singleton
class MusicLibraryRepository @Inject constructor(
    private val sources: MusicSourceRegistry,
    private val database: ALauncherDatabase,
    private val downloadManager: MusicDownloadManager,
    private val columnBuilder: MusicColumnBuilder,
    private val preferencesRepository: UserPreferencesRepository,
    private val navidromePreferences: NavidromePreferencesRepository,
    private val accountReader: MusicAccountReader
) : MusicLibrary {

    private val dao: MusicDownloadDao get() = database.musicDownloadDao()
    private val listenDao: MusicListenDao get() = database.musicListenDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshots = ConcurrentHashMap<MusicSourceKind, CachedSnapshot>()
    private val snapshotLocks = MusicSourceKind.entries.associateWith { Mutex() }

    private val _changes = MutableStateFlow(0)
    override val changes: StateFlow<Int> = _changes.asStateFlow()

    init {
        scope.launch {
            downloadManager.revision.drop(1).collect { bumpChanges() }
        }
        scope.launch {
            sources.all.map { it.catalogChanges }.merge().collect { bumpChanges() }
        }
    }

    override fun configuredSources(): Flow<Set<MusicSourceKind>> =
        combine(
            preferencesRepository.userPreferences,
            navidromePreferences.preferences.distinctUntilChanged(),
            changes
        ) { _, _, _ -> Unit }
            .map {
                snapshots.clear()
                sources.all.filter { it.isConfigured() }.map { it.kind }.toSet()
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    override suspend fun columns(grouping: MusicGrouping, filter: MusicSourceFilter): MusicColumnsPage {
        val snapshots = snapshotsFor(filter)
        return columnBuilder.page(grouping, snapshots, recentAlbums(snapshots, filter))
    }

    override suspend fun contents(
        columnKey: String,
        grouping: MusicGrouping,
        filter: MusicSourceFilter
    ): MusicColumnContents? {
        val snapshots = snapshotsFor(filter)
        val group = columnBuilder.groups(grouping, snapshots, recentAlbums(snapshots, filter))
            .firstOrNull { it.column.key == columnKey }
            ?: return null
        val playlist = group.playlist
        if (group.column.kind != MusicColumnKind.PLAYLIST || playlist == null) {
            return MusicColumnContents(group.column, group.albums, emptyList())
        }
        val tracks = sources.of(playlist.source).playlistTracks(playlist.id).getOrDefault(emptyList())
        return MusicColumnContents(group.column, emptyList(), withDownloads(tracks))
    }

    override suspend fun albumTracks(album: MusicAlbum): List<MusicTrack> = withContext(Dispatchers.IO) {
        withDownloads(tracksOfAlbum(album.source, album.id))
    }

    override suspend fun tracksForKeys(refs: List<MusicStatTrack>): List<MusicTrack> = withContext(Dispatchers.IO) {
        if (refs.isEmpty()) return@withContext emptyList()
        val fromAlbums = coroutineScope {
            refs.mapNotNull(::albumRefOf)
                .distinct()
                .map { (kind, albumId) -> async { tracksOfAlbum(kind, albumId) } }
                .awaitAll()
        }.flatten().associateBy { it.key }
        val downloaded = doneRows(refs.map { it.trackKey })
        val resolved = refs.mapNotNull { ref ->
            fromAlbums[ref.trackKey] ?: downloaded[ref.trackKey]?.let(MusicDownloadAlbums::trackFrom)
        }
        withDownloads(resolved.distinctBy { it.key })
    }

    override suspend fun albumsForKeys(albumKeys: Collection<String>): Map<String, MusicAlbum> {
        if (albumKeys.isEmpty()) return emptyMap()
        val wanted = albumKeys.toSet()
        return snapshotsFor(MusicSourceFilter.ALL)
            .flatMap { it.albums }
            .filter { it.key in wanted }
            .associateBy { it.key }
    }

    private fun albumRefOf(ref: MusicStatTrack): Pair<MusicSourceKind, String>? {
        val kind = MusicSourceKind.fromToken(ref.source) ?: return null
        val albumId = ref.albumKey?.let { MusicKeys.albumIdOf(kind.token, it) }
            ?: ref.romId?.takeIf { kind == MusicSourceKind.ROMM }?.let(MusicKeys::rommRomAlbumId)
            ?: return null
        return kind to albumId
    }

    private suspend fun tracksOfAlbum(kind: MusicSourceKind, albumId: String): List<MusicTrack> =
        sources.of(kind).albumTracks(albumId).getOrNull()
            ?: dao.getBySourceAndStatus(kind.token, MusicDownloadStatus.DONE.name)
                .filter { MusicDownloadAlbums.albumIdOf(it) == albumId }
                .mapNotNull { MusicDownloadAlbums.trackFrom(it) }
                .sortedWith(compareBy({ it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() }))

    override suspend fun queueFor(tracks: List<MusicTrack>): List<MusicQueueTrack> = withContext(Dispatchers.IO) {
        val downloaded = doneRows(tracks.map { it.key })
        val accounts = accountReader.current()
        tracks.mapNotNull { track ->
            val localPath = downloaded[track.key]?.filePath?.takeIf { File(it).isFile }
                ?: track.downloadedPath?.takeIf { File(it).isFile }
            val source = localPath?.let { MusicTrackSource.Local(it) }
                ?: sources.of(track.source).streamSource(track)
                ?: return@mapNotNull null
            val art = downloaded[track.key]?.artLocalPath?.let { MusicArt.Local(it) } ?: track.art
            MusicQueueTrack(
                id = track.key,
                source = source,
                title = track.title,
                gameTitle = if (track.source == MusicSourceKind.ROMM) track.album else null,
                coverPath = when (art) {
                    is MusicArt.Local -> art.path
                    is MusicArt.Remote -> art.url
                    null -> null
                },
                artist = track.artist,
                album = track.album,
                art = art,
                durationSec = track.durationSec,
                identity = MusicTrackIdentities.forLibraryTrack(track, accounts)
            )
        }
    }

    override suspend fun resumePoint(): MusicResumePoint? = withContext(Dispatchers.IO) {
        val ownerKey = accountReader.current().ownerKey
        listenDao.latestWithAlbum(ownerKey)?.let(MusicRecentAlbums::resumePoint)
    }

    override suspend fun playlists(filter: MusicSourceFilter): List<MusicPlaylist> =
        snapshotsFor(filter).flatMap { it.playlists }

    override suspend fun addToPlaylist(playlist: MusicPlaylist, tracks: List<MusicTrack>): Result<Unit> {
        val ids = tracks.filter { it.source == playlist.source }.map { it.id }
        if (ids.isEmpty()) return Result.success(Unit)
        return addIdsToPlaylist(playlist, ids)
    }

    override suspend fun addToPlaylist(playlist: MusicPlaylist, track: MusicQueueTrack): Result<Unit> {
        val identity = track.identity
        if (identity == null || MusicSourceKind.fromToken(identity.source) != playlist.source) {
            return Result.failure(IllegalArgumentException("Track is not from the playlist's source"))
        }
        return addIdsToPlaylist(playlist, listOf(identity.sourceTrackId))
    }

    private suspend fun addIdsToPlaylist(playlist: MusicPlaylist, ids: List<String>): Result<Unit> {
        val result = sources.of(playlist.source).addToPlaylist(playlist.id, ids)
        if (result.isSuccess) bumpChanges()
        return result
    }

    override fun downloads(): Flow<Map<String, MusicDownloadStatus>> =
        dao.observeAll().map { rows ->
            rows.mapNotNull { row ->
                runCatching { MusicDownloadStatus.valueOf(row.status) }.getOrNull()?.let { row.trackKey to it }
            }.toMap()
        }

    override suspend fun download(tracks: List<MusicTrack>) = downloadManager.enqueue(tracks)

    override suspend fun removeDownloads(tracks: List<MusicTrack>) = downloadManager.remove(tracks)

    private fun bumpChanges() {
        snapshots.clear()
        _changes.update { it + 1 }
    }

    private suspend fun recentAlbums(snapshots: List<MusicSourceSnapshot>, filter: MusicSourceFilter): List<MusicAlbum> =
        withContext(Dispatchers.IO) {
            val ownerKey = accountReader.current().ownerKey
            val catalog = snapshots.flatMap { it.albums }.associateBy { it.key }
            MusicRecentAlbums.resolve(listenDao.latestPerAlbum(ownerKey, RECENT_SCAN), catalog, filter, RECENT_LIMIT)
        }

    private suspend fun snapshotsFor(filter: MusicSourceFilter): List<MusicSourceSnapshot> = coroutineScope {
        sources.all
            .filter { filter.admits(it.kind) }
            .map { source -> async(Dispatchers.IO) { snapshotOf(source) } }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun snapshotOf(source: MusicLibrarySource): MusicSourceSnapshot? =
        snapshotLocks.getValue(source.kind).withLock {
            val now = System.currentTimeMillis()
            snapshots[source.kind]?.takeIf { now - it.fetchedAt < SNAPSHOT_TTL_MS }?.let { return@withLock it.snapshot }
            if (!source.isConfigured()) return@withLock null
            val fetched = fetchSnapshot(source)
            snapshots[source.kind] = CachedSnapshot(now, fetched)
            fetched
        }

    private suspend fun fetchSnapshot(source: MusicLibrarySource): MusicSourceSnapshot = coroutineScope {
        val albums = async { source.albums() }
        val artists = async { source.artists() }
        val playlists = async { source.playlists() }
        val albumList = albums.await().getOrNull()
        if (albumList == null) {
            artists.cancel()
            playlists.cancel()
            return@coroutineScope offlineSnapshot(source.kind)
        }
        val downloadedCounts = dao.getBySourceAndStatus(source.kind.token, MusicDownloadStatus.DONE.name)
            .groupingBy { MusicDownloadAlbums.albumIdOf(it) }
            .eachCount()
        val marked = { album: MusicAlbum ->
            val count = downloadedCounts[album.id] ?: 0
            album.copy(downloaded = album.downloaded || (album.trackCount > 0 && count >= album.trackCount))
        }
        MusicSourceSnapshot(
            kind = source.kind,
            albums = albumList.map(marked),
            artists = artists.await().getOrDefault(emptyList()),
            playlists = playlists.await().getOrDefault(emptyList()),
            offline = false
        )
    }

    private suspend fun offlineSnapshot(kind: MusicSourceKind): MusicSourceSnapshot {
        val rows = dao.getBySourceAndStatus(kind.token, MusicDownloadStatus.DONE.name)
            .filter { row -> row.filePath?.let { File(it).isFile } == true }
        return MusicSourceSnapshot(
            kind = kind,
            albums = MusicDownloadAlbums.albumsFrom(kind, rows),
            artists = emptyList(),
            playlists = emptyList(),
            offline = true
        )
    }

    private suspend fun withDownloads(tracks: List<MusicTrack>): List<MusicTrack> {
        if (tracks.isEmpty()) return tracks
        val downloaded = doneRows(tracks.map { it.key })
        return tracks.map { track ->
            downloaded[track.key]?.filePath?.let { track.copy(downloadedPath = it) } ?: track
        }
    }

    private suspend fun doneRows(keys: List<String>): Map<String, MusicDownloadEntity> =
        keys.chunked(QUERY_CHUNK).flatMap { dao.getByKeys(it) }
            .filter { it.status == MusicDownloadStatus.DONE.name }
            .associateBy { it.trackKey }

    private class CachedSnapshot(val fetchedAt: Long, val snapshot: MusicSourceSnapshot)

    private companion object {
        const val QUERY_CHUNK = 500
    }
}
