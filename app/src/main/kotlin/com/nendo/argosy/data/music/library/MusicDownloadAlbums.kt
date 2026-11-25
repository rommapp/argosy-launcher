package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.domain.music.CreditSplitter
import com.nendo.argosy.domain.music.MusicKeys

private const val UNKNOWN_ALBUM_ID = "unknown"

/**
 * Rebuilds albums and tracks from downloaded rows so an unreachable source can still be browsed.
 * Album keys follow the `<source>:album:<albumId>` form the sources use online.
 */
object MusicDownloadAlbums {

    fun albumsFrom(kind: MusicSourceKind, rows: List<MusicDownloadEntity>): List<MusicAlbum> =
        rows.filter { it.source == kind.token }
            .groupBy { albumIdOf(it) }
            .map { (albumId, members) ->
                val first = members.first()
                MusicAlbum(
                    key = albumKey(kind, albumId),
                    source = kind,
                    id = albumId,
                    title = members.firstNotNullOfOrNull { it.album } ?: first.title,
                    artist = members.firstNotNullOfOrNull { it.artist },
                    artistId = null,
                    year = members.firstNotNullOfOrNull { it.year },
                    genre = members.firstNotNullOfOrNull { it.genre },
                    trackCount = members.size,
                    durationSec = members.sumOf { it.durationSec ?: 0 },
                    art = members.firstNotNullOfOrNull { it.artLocalPath }?.let { MusicArt.Local(it) },
                    playCount = 0,
                    downloaded = true,
                    credits = members.flatMap { CreditSplitter.split(it.artist) }.distinctBy(CreditSplitter::artistKey)
                )
            }

    fun trackFrom(row: MusicDownloadEntity): MusicTrack? {
        val kind = MusicSourceKind.fromToken(row.source) ?: return null
        return MusicTrack(
            key = row.trackKey,
            source = kind,
            id = row.trackId,
            title = row.title,
            artist = row.artist,
            album = row.album,
            albumId = row.albumId,
            trackNumber = row.trackNumber,
            durationSec = row.durationSec,
            year = row.year,
            genre = row.genre,
            art = row.artLocalPath?.let { MusicArt.Local(it) },
            downloadedPath = row.filePath
        )
    }

    fun albumIdOf(row: MusicDownloadEntity): String = row.albumId ?: UNKNOWN_ALBUM_ID

    fun albumKey(kind: MusicSourceKind, albumId: String): String = MusicKeys.albumKey(kind.token, albumId)
}
