package com.nendo.argosy.data.music.romm

import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.resolved
import com.nendo.argosy.data.local.dao.resolvedFor
import com.nendo.argosy.data.local.entity.RommMusicTrackEntity
import com.nendo.argosy.data.music.library.MusicDownloadAlbums
import com.nendo.argosy.data.music.library.MusicLibrarySource
import com.nendo.argosy.data.music.library.MusicPermissionException
import com.nendo.argosy.data.music.library.MusicPlaylistNameTakenException
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMMusicLibraryService
import com.nendo.argosy.data.remote.romm.RomMMusicTrack
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicArtist
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.domain.model.MusicServerStatus
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.domain.model.SoundtrackGameEntry
import com.nendo.argosy.domain.music.MusicKeys
import com.nendo.argosy.domain.usecase.music.BGM_MIN_DURATION_SECONDS
import com.nendo.argosy.domain.usecase.music.GetLocalMusicTrackStateUseCase
import com.nendo.argosy.domain.usecase.music.GetMusicPlaylistsUseCase
import com.nendo.argosy.domain.usecase.music.MusicTrackLookup
import com.nendo.argosy.domain.usecase.music.ResolveMusicQueueUseCase
import com.nendo.argosy.domain.usecase.music.SearchSoundtrackGamesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

private const val ALBUM_PAGE_SIZE = 200
private const val MAX_ALBUMS = 5_000
private const val ALBUM_MEMO_MS = 30_000L
private const val LOCAL_TRACK_PREFIX = "path:"
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_FORBIDDEN = 403
private val MULTI_SPACE = Regex("\\s+")

/**
 * RomM as a Music source. Each rom with a soundtrack is one album, titled and tagged from its
 * tracks in [RommMusicIndex]; server playlists owned by the signed-in user are writable, others
 * read-only. A track id is the RomM stream path, or a `path:` id for a soundtrack file that exists
 * only on this device; playlist writes take rom file ids instead.
 */
@Singleton
class RommMusicSource @Inject constructor(
    private val musicLibrary: RomMMusicLibraryService,
    private val index: RommMusicIndex,
    private val searchSoundtrackGames: SearchSoundtrackGamesUseCase,
    private val getMusicPlaylists: GetMusicPlaylistsUseCase,
    private val resolveMusicQueue: ResolveMusicQueueUseCase,
    private val getLocalMusicTrackState: GetLocalMusicTrackStateUseCase,
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao,
    private val preferencesRepository: UserPreferencesRepository
) : MusicLibrarySource {

    override val kind: MusicSourceKind = MusicSourceKind.ROMM

    override val catalogChanges: Flow<Unit> = index.revision.drop(1).map { }

    override suspend fun isConfigured(): Boolean =
        !preferencesRepository.userPreferences.first().rommBaseUrl.isNullOrBlank()

    private val albumsLock = Mutex()
    private var albumsMemo: AlbumsMemo? = null

    override suspend fun albums(): Result<List<MusicAlbum>> = albumsLock.withLock {
        val now = System.currentTimeMillis()
        val owner = indexOwner()
        val revision = index.revision.value
        albumsMemo
            ?.takeIf { it.owner == owner && it.revision == revision && now - it.at < ALBUM_MEMO_MS }
            ?.let { return@withLock Result.success(it.albums) }
        fetchAlbums(owner).onSuccess { albumsMemo = AlbumsMemo(owner, revision, now, it) }
    }

    private suspend fun indexOwner(): Long? =
        if (musicLibrary.isConnected()) index.activeOwner() else null

    private suspend fun fetchAlbums(owner: Long?): Result<List<MusicAlbum>> = withContext(Dispatchers.IO) {
        if (owner != null) {
            index.refreshIfStale(owner)
            val rows = index.tracks(owner)
            if (rows.isNotEmpty()) return@withContext Result.success(indexedAlbums(rows))
        }
        searchedAlbums()
    }

    private suspend fun indexedAlbums(rows: List<RommMusicTrackEntity>): List<MusicAlbum> {
        val summaries = RommMusicAlbums.albums(rows)
        val gameIdByRom = gameDao.getRommIdMappings().associate { it.rommId to it.id }
        val art = gameArtDao.resolvedFor(summaries.mapNotNull { gameIdByRom[it.romId] })
        return summaries.map { summary ->
            val id = romAlbumId(summary.romId)
            val localCover = gameIdByRom[summary.romId]?.let { art[it]?.coverPath }
            MusicAlbum(
                key = albumKey(id),
                source = kind,
                id = id,
                title = summary.title,
                artist = summary.artist,
                credits = summary.credits,
                artistId = null,
                year = summary.year,
                genre = summary.genre,
                trackCount = summary.trackCount,
                durationSec = summary.durationSec,
                art = coverArt(localCover, id) ?: remoteCover(summary.coverUrl, id),
                playCount = 0,
                downloaded = false
            )
        }
    }

    private suspend fun searchedAlbums(): Result<List<MusicAlbum>> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<MusicAlbum>()
        var offset = 0
        while (offset < MAX_ALBUMS) {
            val page = searchSoundtrackGames(null, offset, ALBUM_PAGE_SIZE)
            if (!page.serverStatus.isUsable()) {
                return@withContext Result.failure(IOException("RomM music unavailable: ${page.serverStatus}"))
            }
            offset += page.entries.size
            collected += page.entries.mapNotNull { it.toAlbum() }
            if (!page.hasMore || page.entries.isEmpty()) break
        }
        Result.success(collected.toList())
    }

    override suspend fun artists(): Result<List<MusicArtist>> = withContext(Dispatchers.IO) {
        val owner = indexOwner() ?: return@withContext Result.success(emptyList())
        Result.success(
            RommMusicAlbums.artists(index.tracks(owner)).map { artist ->
                MusicArtist(
                    key = artistKey(artist.name),
                    source = kind,
                    id = artist.name,
                    name = artist.name,
                    albumCount = artist.albumCount,
                    photo = null
                )
            }
        )
    }

    override suspend fun playlists(): Result<List<MusicPlaylist>> = withContext(Dispatchers.IO) {
        val catalog = getMusicPlaylists()
        if (!catalog.serverStatus.isUsable()) {
            return@withContext Result.failure(IOException("RomM playlists unavailable: ${catalog.serverStatus}"))
        }
        val username = preferencesRepository.userPreferences.first().rommUsername?.takeIf { it.isNotBlank() }
        Result.success(
            catalog.entries.mapNotNull { entry ->
                val selection = entry.selection as? MusicSelection.ServerPlaylist ?: return@mapNotNull null
                MusicPlaylist(
                    key = playlistKey(selection.playlistId.toString()),
                    source = kind,
                    id = selection.playlistId.toString(),
                    name = selection.name,
                    trackCount = entry.trackCount ?: 0,
                    durationSec = 0,
                    art = null,
                    writable = username != null && entry.ownerName.equals(username, ignoreCase = true)
                )
            }
        )
    }

    override suspend fun albumTracks(albumId: String): Result<List<MusicTrack>> = withContext(Dispatchers.IO) {
        val romId = MusicKeys.rommRomIdOf(albumId)
        val gameId = MusicKeys.rommGameIdOf(albumId)
        if (romId == null && gameId == null) {
            return@withContext Result.failure(IllegalArgumentException("Unknown RomM album id"))
        }
        val owner = indexOwner()
        if (romId != null && owner != null) {
            val indexed = index.albumTracks(owner, romId)
            if (indexed.isNotEmpty()) {
                return@withContext Result.success(indexed.map { it.toRemote() }.ordered().toTracks())
            }
        }
        val canStream = musicLibrary.isConnected() && musicLibrary.capabilities().supportsMusicTrackRomFilter
        if (romId != null && canStream) {
            val result = musicLibrary.getGameTracks(romId, BGM_MIN_DURATION_SECONDS)
            if (result is RomMResult.Success && result.data.isNotEmpty()) {
                return@withContext Result.success(result.data.ordered().toTracks())
            }
        }
        val local = localSoundtrack(albumId, romId, gameId)
        if (local.isEmpty() && !musicLibrary.isConnected()) {
            Result.failure(IOException("RomM not connected"))
        } else {
            Result.success(local)
        }
    }

    override suspend fun playlistTracks(playlistId: String): Result<List<MusicTrack>> =
        withContext(Dispatchers.IO) {
            val id = playlistId.toLongOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Unknown RomM playlist id"))
            if (!musicLibrary.isConnected()) return@withContext Result.failure(IOException("RomM not connected"))
            when (val result = musicLibrary.getPlaylistTracks(id)) {
                is RomMResult.Success -> Result.success(result.data.toTracks())
                is RomMResult.Error -> Result.failure(IOException(result.message))
            }
        }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> =
        playlistWrite(playlistId, trackIds) { id, romFileIds ->
            if (romFileIds.isEmpty()) RomMResult.Success(Unit) else musicLibrary.addPlaylistTracks(id, romFileIds)
        }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<MusicPlaylist> =
        withContext(Dispatchers.IO) {
            val romFileIds = romFileIdsOf(trackIds)
                ?: return@withContext Result.failure(IllegalArgumentException("Unknown RomM track id"))
            if (!musicLibrary.isConnected()) return@withContext Result.failure(IOException("RomM not connected"))
            val created = when (val result = musicLibrary.createPlaylist(name)) {
                is RomMResult.Success -> result.data
                is RomMResult.Error -> return@withContext Result.failure(result.toWriteFailure(namesUnique = true))
            }
            if (romFileIds.isNotEmpty()) {
                val added = musicLibrary.addPlaylistTracks(created.id, romFileIds)
                if (added is RomMResult.Error) {
                    return@withContext Result.failure(added.toWriteFailure(namesUnique = false))
                }
            }
            Result.success(
                MusicPlaylist(
                    key = playlistKey(created.id.toString()),
                    source = kind,
                    id = created.id.toString(),
                    name = created.name,
                    trackCount = romFileIds.size,
                    durationSec = 0,
                    art = null,
                    writable = true
                )
            )
        }

    override suspend fun renamePlaylist(playlistId: String, name: String): Result<Unit> =
        playlistWrite(playlistId, emptyList(), namesUnique = true) { id, _ ->
            musicLibrary.renamePlaylist(id, name)
        }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        playlistWrite(playlistId, emptyList()) { id, _ -> musicLibrary.deletePlaylist(id) }

    override suspend fun removeFromPlaylist(playlistId: String, entryIndex: Int, trackId: String): Result<Unit> =
        playlistWrite(playlistId, listOf(trackId)) { id, romFileIds ->
            musicLibrary.removePlaylistTracks(id, romFileIds)
        }

    private suspend fun playlistWrite(
        playlistId: String,
        trackIds: List<String>,
        namesUnique: Boolean = false,
        call: suspend (playlistId: Long, romFileIds: List<Long>) -> RomMResult<Unit>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val id = playlistId.toLongOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown RomM playlist id"))
        val romFileIds = romFileIdsOf(trackIds)
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown RomM track id"))
        if (!musicLibrary.isConnected()) return@withContext Result.failure(IOException("RomM not connected"))
        when (val result = call(id, romFileIds)) {
            is RomMResult.Success -> Result.success(Unit)
            is RomMResult.Error -> Result.failure(result.toWriteFailure(namesUnique))
        }
    }

    private fun romFileIdsOf(trackIds: List<String>): List<Long>? {
        val parsed = trackIds.mapNotNull { it.toLongOrNull() }
        return parsed.takeIf { it.size == trackIds.size }
    }

    private fun RomMResult.Error.toWriteFailure(namesUnique: Boolean): Exception = when {
        code == HTTP_FORBIDDEN -> MusicPermissionException()
        namesUnique && code == HTTP_BAD_REQUEST -> MusicPlaylistNameTakenException()
        else -> IOException(message)
    }

    override suspend fun streamSource(track: MusicTrack): MusicTrackSource.Remote? {
        if (track.source != kind || track.id.startsWith(LOCAL_TRACK_PREFIX)) return null
        val url = musicLibrary.streamUrl(track.id) ?: return null
        val token = preferencesRepository.userPreferences.first().rommToken
        return MusicTrackSource.Remote(url, musicLibrary.authHeaders(url, token))
    }

    private fun MusicServerStatus.isUsable(): Boolean =
        this == MusicServerStatus.AVAILABLE || this == MusicServerStatus.UNSUPPORTED

    private fun SoundtrackGameEntry.toAlbum(): MusicAlbum? {
        val id = selection.romId?.let { romAlbumId(it) }
            ?: selection.gameId?.let { gameAlbumId(it) }
            ?: return null
        return MusicAlbum(
            key = albumKey(id),
            source = kind,
            id = id,
            title = selection.title,
            artist = null,
            artistId = null,
            year = null,
            genre = null,
            trackCount = trackCount,
            durationSec = 0,
            art = coverArt(coverPath, id),
            playCount = 0,
            downloaded = false
        )
    }

    private suspend fun localSoundtrack(albumId: String, romId: Long?, gameId: Long?): List<MusicTrack> {
        val title = gameId?.let { gameDao.getById(it)?.title }
            ?: romId?.let { gameDao.getByRommId(it)?.title }
            ?: return emptyList()
        return resolveMusicQueue(MusicSelection.GameSoundtrack(romId = romId, gameId = gameId, title = title))
            .mapIndexedNotNull { index, queued ->
                val path = queued.localPath ?: return@mapIndexedNotNull null
                MusicTrack(
                    key = trackKey(queued.id),
                    source = kind,
                    id = queued.id,
                    title = queued.title,
                    artist = null,
                    album = queued.gameTitle ?: title,
                    albumId = albumId,
                    trackNumber = index + 1,
                    durationSec = null,
                    year = null,
                    genre = null,
                    art = queued.coverPath?.let { coverArt(it, albumId) },
                    downloadedPath = path,
                    romId = queued.identity?.romId,
                    romFileId = queued.identity?.romFileId
                )
            }
    }

    private fun List<RomMMusicTrack>.ordered(): List<RomMMusicTrack> =
        sortedWith(
            compareBy(
                { it.disc ?: Int.MAX_VALUE },
                { it.track ?: Int.MAX_VALUE },
                { it.title.orEmpty().lowercase() }
            )
        )

    private fun RommMusicTrackEntity.toRemote() = RomMMusicTrack(
        romFileId = romFileId,
        romId = romId,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        year = year,
        track = track,
        disc = disc,
        durationSeconds = durationSeconds,
        gameName = gameName,
        platformId = platformId,
        platformSlug = platformSlug,
        platformName = platformName,
        streamUrl = streamUrl,
        coverUrl = coverUrl,
        gameCoverUrl = gameCoverUrl
    )

    private suspend fun List<RomMMusicTrack>.toTracks(): List<MusicTrack> {
        val locals = getLocalMusicTrackState(map { it.toLookup() })
        val covers = mutableMapOf<Long, MusicArt?>()
        return map { track ->
            val art = covers.getOrPut(track.romId) {
                localCover(track.romId)?.let { MusicArt.Local(it) }
                    ?: remoteCover(track.coverUrl ?: track.gameCoverUrl, romAlbumId(track.romId))
            }
            MusicTrack(
                key = trackKey(track.romFileId.toString()),
                source = kind,
                id = track.streamUrl,
                title = readableTrackTitle(track.title?.takeIf { it.isNotBlank() } ?: track.fileName()),
                artist = track.artist?.takeIf { it.isNotBlank() },
                album = track.gameName?.takeIf { it.isNotBlank() } ?: track.album,
                albumId = romAlbumId(track.romId),
                trackNumber = track.track,
                durationSec = track.durationSeconds?.toInt(),
                year = track.year,
                genre = track.genre?.takeIf { it.isNotBlank() },
                art = art,
                downloadedPath = locals[track.romFileId]?.localPath,
                romId = track.romId,
                romFileId = track.romFileId
            )
        }
    }

    private suspend fun localCover(romId: Long): String? =
        gameDao.getByRommId(romId)
            ?.let { gameArtDao.resolved(it.id).coverPath }
            ?.takeIf { it.isNotBlank() }

    private fun coverArt(coverPath: String?, albumId: String): MusicArt? {
        val path = coverPath?.takeIf { it.isNotBlank() } ?: return null
        return if (path.startsWith("/")) MusicArt.Local(path) else MusicArt.Remote(path, coverCacheKey(albumId))
    }

    private fun remoteCover(rawPath: String?, albumId: String): MusicArt? =
        musicLibrary.resourceUrl(rawPath)?.let { MusicArt.Remote(it, coverCacheKey(albumId)) }

    private fun readableTrackTitle(raw: String): String {
        val spaced = raw.replace('_', ' ').replace(MULTI_SPACE, " ").trim()
        return spaced.ifEmpty { raw }
    }

    private fun RomMMusicTrack.fileName(): String {
        val encoded = streamUrl.substringAfterLast('/')
        return runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrDefault(encoded)
    }

    private fun RomMMusicTrack.toLookup() = MusicTrackLookup(
        romFileId = romFileId,
        platformName = platformName,
        gameName = gameName.orEmpty(),
        trackNumber = track,
        trackTitle = title?.takeIf { it.isNotBlank() },
        fileName = fileName()
    )

    private class AlbumsMemo(val owner: Long?, val revision: Int, val at: Long, val albums: List<MusicAlbum>)

    companion object {
        fun romAlbumId(romId: Long): String = MusicKeys.rommRomAlbumId(romId)

        fun gameAlbumId(gameId: Long): String = MusicKeys.rommGameAlbumId(gameId)

        fun albumKey(albumId: String): String = MusicDownloadAlbums.albumKey(MusicSourceKind.ROMM, albumId)

        fun trackKey(trackId: String): String = MusicKeys.trackKey(MusicSourceKind.ROMM.token, trackId)

        fun playlistKey(playlistId: String): String = "romm:playlist:$playlistId"

        fun artistKey(name: String): String = "romm:artist:$name"

        private fun coverCacheKey(albumId: String): String = "romm:cover:$albumId"
    }
}
