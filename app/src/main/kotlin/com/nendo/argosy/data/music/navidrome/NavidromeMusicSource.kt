package com.nendo.argosy.data.music.navidrome

import com.nendo.argosy.data.music.library.MusicLibrarySource
import com.nendo.argosy.data.preferences.NavidromePreferences
import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.remote.navidrome.NavidromeAlbumDto
import com.nendo.argosy.data.remote.navidrome.NavidromeAlbumListType
import com.nendo.argosy.data.remote.navidrome.NavidromeApiClient
import com.nendo.argosy.data.remote.navidrome.NavidromeArtistDto
import com.nendo.argosy.data.remote.navidrome.NavidromeCredentials
import com.nendo.argosy.data.remote.navidrome.NavidromePlaylistDto
import com.nendo.argosy.data.remote.navidrome.NavidromeSongDto
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicArtist
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.domain.music.CreditSplitter
import com.nendo.argosy.domain.music.MusicKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val ALBUM_PAGE_SIZE = 500
private const val COVER_SIZE = 600
private const val KEY_PREFIX = "navidrome:"
private const val COVER_CACHE_PREFIX = "navidrome:cover:"
private const val ARTIST_CACHE_PREFIX = "navidrome:artist:"

class NavidromeNotSignedInException : IllegalStateException("Navidrome is not signed in")

@Singleton
class NavidromeMusicSource @Inject constructor(
    private val preferences: NavidromePreferencesRepository,
    private val api: NavidromeApiClient
) : MusicLibrarySource {

    override val kind: MusicSourceKind = MusicSourceKind.NAVIDROME

    override suspend fun isConfigured(): Boolean = preferences.preferences.first().isSignedIn

    override suspend fun albums(): Result<List<MusicAlbum>> = withCredentials { credentials ->
        val albums = LinkedHashMap<String, NavidromeAlbumDto>()
        var offset = 0
        while (true) {
            val page = api.albumList(
                credentials, NavidromeAlbumListType.ALPHABETICAL_BY_NAME, ALBUM_PAGE_SIZE, offset
            ).getOrThrow()
            val before = albums.size
            page.forEach { if (it.id !in albums) albums[it.id] = it }
            if (page.size < ALBUM_PAGE_SIZE || albums.size == before) break
            offset += page.size
        }
        albums.values.map { it.toMusicAlbum(coverArt(credentials, it.coverArt)) }
    }

    override suspend fun artists(): Result<List<MusicArtist>> = withCredentials { credentials ->
        api.artists(credentials).getOrThrow().map { it.toMusicArtist() }
    }

    override suspend fun playlists(): Result<List<MusicPlaylist>> = withCredentials { credentials ->
        api.playlists(credentials).getOrThrow().map {
            it.toMusicPlaylist(coverArt(credentials, it.coverArt), credentials.username)
        }
    }

    override suspend fun albumTracks(albumId: String): Result<List<MusicTrack>> = withCredentials { credentials ->
        api.album(credentials, albumId).getOrThrow().song.orEmpty().map {
            it.toMusicTrack(coverArt(credentials, it.coverArt))
        }
    }

    override suspend fun playlistTracks(playlistId: String): Result<List<MusicTrack>> =
        withCredentials { credentials ->
            api.playlist(credentials, playlistId).getOrThrow().entry.orEmpty().map {
                it.toMusicTrack(coverArt(credentials, it.coverArt))
            }
        }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> =
        withCredentials { credentials ->
            if (trackIds.isNotEmpty()) api.addToPlaylist(credentials, playlistId, trackIds).getOrThrow()
        }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<MusicPlaylist> =
        withCredentials { credentials ->
            val created = api.createPlaylist(credentials, name, trackIds).getOrThrow()
            created.toMusicPlaylist(coverArt(credentials, created.coverArt), credentials.username)
        }

    override suspend fun renamePlaylist(playlistId: String, name: String): Result<Unit> =
        withCredentials { credentials -> api.renamePlaylist(credentials, playlistId, name).getOrThrow() }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        withCredentials { credentials -> api.deletePlaylist(credentials, playlistId).getOrThrow() }

    override suspend fun removeFromPlaylist(playlistId: String, entryIndex: Int, trackId: String): Result<Unit> =
        withCredentials { credentials -> api.removeFromPlaylist(credentials, playlistId, entryIndex).getOrThrow() }

    override suspend fun streamSource(track: MusicTrack): MusicTrackSource.Remote? {
        if (track.source != MusicSourceKind.NAVIDROME) return null
        val credentials = credentials() ?: return null
        val url = api.streamUrl(credentials, track.id) ?: return null
        return MusicTrackSource.Remote(url, emptyMap())
    }

    /**
     * The artist photo the server holds for [artistId], from getArtistInfo2. Null when the
     * server has none, the call fails, or no account is signed in.
     */
    suspend fun artistPhoto(artistId: String): MusicArt? {
        val credentials = credentials() ?: return null
        val url = api.artistInfo(credentials, artistId).getOrNull()?.largeImageUrl
            ?.takeIf { it.isNotBlank() } ?: return null
        return MusicArt.Remote(url = url, cacheKey = "$ARTIST_CACHE_PREFIX$artistId")
    }

    private suspend fun credentials(): NavidromeCredentials? = preferences.preferences.first().credentials()

    private suspend fun <T> withCredentials(block: suspend (NavidromeCredentials) -> T): Result<T> {
        val credentials = credentials() ?: return Result.failure(NavidromeNotSignedInException())
        return try {
            Result.success(block(credentials))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun coverArt(credentials: NavidromeCredentials, coverArtId: String?): MusicArt? {
        val id = coverArtId?.takeIf { it.isNotBlank() } ?: return null
        val url = api.coverArtUrl(credentials, id, COVER_SIZE) ?: return null
        return MusicArt.Remote(url = url, cacheKey = "$COVER_CACHE_PREFIX$id")
    }
}

internal fun navidromeKey(kind: String, id: String): String = "$KEY_PREFIX$kind:$id"

internal fun NavidromePreferences.credentials(): NavidromeCredentials? {
    val serverUrl = serverUrl?.takeIf { it.isNotBlank() } ?: return null
    val username = username?.takeIf { it.isNotBlank() } ?: return null
    val password = password?.takeIf { it.isNotBlank() } ?: return null
    return NavidromeCredentials(serverUrl, username, password)
}

internal fun NavidromeAlbumDto.toMusicAlbum(art: MusicArt?): MusicAlbum = MusicAlbum(
    key = MusicKeys.albumKey(MusicSourceKind.NAVIDROME.token, id),
    source = MusicSourceKind.NAVIDROME,
    id = id,
    title = name.orEmpty(),
    artist = artist?.takeIf { it.isNotBlank() },
    artistId = artistId?.takeIf { it.isNotBlank() },
    year = year?.takeIf { it > 0 },
    genre = genre?.takeIf { it.isNotBlank() },
    trackCount = songCount ?: song?.size ?: 0,
    durationSec = duration ?: 0,
    art = art,
    playCount = playCount?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
    downloaded = false,
    credits = CreditSplitter.split(artist)
)

internal fun NavidromeSongDto.toMusicTrack(art: MusicArt?): MusicTrack = MusicTrack(
    key = MusicKeys.trackKey(MusicSourceKind.NAVIDROME.token, id),
    source = MusicSourceKind.NAVIDROME,
    id = id,
    title = title.orEmpty(),
    artist = artist?.takeIf { it.isNotBlank() },
    album = album?.takeIf { it.isNotBlank() },
    albumId = albumId?.takeIf { it.isNotBlank() },
    trackNumber = track?.takeIf { it > 0 },
    durationSec = duration,
    year = year?.takeIf { it > 0 },
    genre = genre?.takeIf { it.isNotBlank() },
    art = art,
    downloadedPath = null,
    isrc = isrc.orEmpty().firstOrNull { it.isNotBlank() },
    musicBrainzId = musicBrainzId?.takeIf { it.isNotBlank() }
)

internal fun NavidromeArtistDto.toMusicArtist(): MusicArtist = MusicArtist(
    key = navidromeKey("artist", id),
    source = MusicSourceKind.NAVIDROME,
    id = id,
    name = name.orEmpty(),
    albumCount = albumCount ?: 0,
    photo = artistImageUrl?.takeIf { it.isNotBlank() }?.let {
        MusicArt.Remote(url = it, cacheKey = "$ARTIST_CACHE_PREFIX$id")
    }
)

internal fun NavidromePlaylistDto.toMusicPlaylist(art: MusicArt?, username: String): MusicPlaylist = MusicPlaylist(
    key = navidromeKey("playlist", id),
    source = MusicSourceKind.NAVIDROME,
    id = id,
    name = name.orEmpty(),
    trackCount = songCount ?: entry?.size ?: 0,
    durationSec = duration ?: 0,
    art = art,
    writable = readonly != true && (owner == null || owner == username)
)
