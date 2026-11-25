package com.nendo.argosy.domain.model

enum class MusicSourceKind(val token: String) {
    NAVIDROME("navidrome"),
    ROMM("romm");

    companion object {
        fun fromToken(token: String?): MusicSourceKind? = entries.firstOrNull { it.token == token }
    }
}

enum class MusicSourceFilter(val token: String) {
    ALL("all"),
    NAVIDROME("navidrome"),
    ROMM("romm");

    fun admits(kind: MusicSourceKind): Boolean = when (this) {
        ALL -> true
        NAVIDROME -> kind == MusicSourceKind.NAVIDROME
        ROMM -> kind == MusicSourceKind.ROMM
    }

    companion object {
        fun fromToken(token: String?): MusicSourceFilter = entries.firstOrNull { it.token == token } ?: ALL
    }
}

enum class MusicGrouping(val token: String) {
    PLAYLISTS("playlists"),
    ARTISTS("artists"),
    ALBUMS("albums"),
    YEARS("years"),
    GENRES("genres");

    companion object {
        fun fromToken(token: String?): MusicGrouping = entries.firstOrNull { it.token == token } ?: ARTISTS
    }
}

/**
 * Artwork reference. [cacheKey] identifies the image without any credentials, so it is the
 * key image caches must use; [url] may carry per-request auth and must never be logged.
 */
sealed interface MusicArt {
    data class Remote(val url: String, val cacheKey: String) : MusicArt
    data class Local(val path: String) : MusicArt
}

data class MusicTrack(
    val key: String,
    val source: MusicSourceKind,
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val trackNumber: Int?,
    val durationSec: Int?,
    val year: Int?,
    val genre: String?,
    val art: MusicArt?,
    val downloadedPath: String?,
    val isrc: String? = null,
    val musicBrainzId: String? = null,
    val romId: Long? = null,
    val romFileId: Long? = null
)

data class MusicAlbum(
    val key: String,
    val source: MusicSourceKind,
    val id: String,
    val title: String,
    val artist: String?,
    val artistId: String?,
    val year: Int?,
    val genre: String?,
    val trackCount: Int,
    val durationSec: Int,
    val art: MusicArt?,
    val playCount: Int,
    val downloaded: Boolean,
    val credits: List<String> = emptyList()
)

data class MusicArtist(
    val key: String,
    val source: MusicSourceKind,
    val id: String,
    val name: String,
    val albumCount: Int,
    val photo: MusicArt?
)

data class MusicPlaylist(
    val key: String,
    val source: MusicSourceKind,
    val id: String,
    val name: String,
    val trackCount: Int,
    val durationSec: Int,
    val art: MusicArt?,
    val writable: Boolean
)

enum class MusicColumnKind { RECENT, PLAYLIST, ARTIST, ALBUM, YEAR, GENRE }

/**
 * One column of the Music screen. [heroArt] fills the column background, [sleeves] are the
 * up-to-three covers stacked in front (front sleeve first), and [hiddenAlbumCount] is the
 * "+N" beyond them. [photo] replaces the hero for artists that have one.
 */
data class MusicColumn(
    val key: String,
    val kind: MusicColumnKind,
    val title: String,
    val sources: Set<MusicSourceKind>,
    val albumCount: Int,
    val trackCount: Int,
    val durationSec: Int,
    val heroArt: MusicArt?,
    val photo: MusicArt?,
    val sleeves: List<MusicArt>,
    val hiddenAlbumCount: Int,
    val sleeveTitles: List<String>
)

data class MusicColumnsPage(
    val columns: List<MusicColumn>,
    val offline: Boolean
)

data class MusicColumnContents(
    val column: MusicColumn,
    val albums: List<MusicAlbum>,
    val tracks: List<MusicTrack>
)
