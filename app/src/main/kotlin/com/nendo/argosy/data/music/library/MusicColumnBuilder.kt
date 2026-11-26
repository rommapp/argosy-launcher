package com.nendo.argosy.data.music.library

import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicArtist
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicColumnsPage
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.domain.model.MusicSourceKind
import java.util.Locale
import javax.inject.Inject

private const val MAX_SLEEVES = 3

data class MusicSourceSnapshot(
    val kind: MusicSourceKind,
    val albums: List<MusicAlbum>,
    val artists: List<MusicArtist>,
    val playlists: List<MusicPlaylist>,
    val offline: Boolean
)

data class MusicColumnGroup(
    val column: MusicColumn,
    val albums: List<MusicAlbum>,
    val playlistGroup: MusicPlaylistGroup?
)

/**
 * Turns per-source snapshots into Music screen columns. RECENT leads when [recent] (most recent
 * first) is non-empty. Grouped columns sort by title, years ascending, with the unlabelled group
 * last.
 */
class MusicColumnBuilder @Inject constructor() {

    fun page(
        grouping: MusicGrouping,
        snapshots: List<MusicSourceSnapshot>,
        recent: List<MusicAlbum> = emptyList()
    ): MusicColumnsPage =
        MusicColumnsPage(
            columns = groups(grouping, snapshots, recent).map { it.column },
            offline = snapshots.any { it.offline }
        )

    fun groups(
        grouping: MusicGrouping,
        snapshots: List<MusicSourceSnapshot>,
        recent: List<MusicAlbum> = emptyList()
    ): List<MusicColumnGroup> {
        val recentColumn = recentGroup(recent)
        val albums = snapshots.flatMap { it.albums }
        val body = when (grouping) {
            MusicGrouping.PLAYLISTS -> playlistGroups(snapshots.flatMap { it.playlists })
            MusicGrouping.ALBUMS -> albumGroups(albums)
            MusicGrouping.ARTISTS -> artistGroups(albums, snapshots.flatMap { it.artists })
            MusicGrouping.YEARS -> yearGroups(albums)
            MusicGrouping.GENRES -> genreGroups(albums)
        }
        return listOfNotNull(recentColumn) + body
    }

    private fun recentGroup(albums: List<MusicAlbum>): MusicColumnGroup? {
        val recent = albums.distinctBy { it.key }
        if (recent.isEmpty()) return null
        return MusicColumnGroup(
            column = column(RECENT_KEY, MusicColumnKind.RECENT, "", recent, recent, photo = null),
            albums = recent,
            playlistGroup = null
        )
    }

    private fun playlistGroups(playlists: List<MusicPlaylist>): List<MusicColumnGroup> =
        mergePlaylistGroups(playlists).map { group ->
            val art = group.art
            MusicColumnGroup(
                column = MusicColumn(
                    key = "$PLAYLIST_PREFIX${group.key}",
                    kind = MusicColumnKind.PLAYLIST,
                    title = group.name,
                    sources = group.sources,
                    albumCount = 0,
                    trackCount = group.trackCount,
                    durationSec = group.durationSec,
                    heroArt = art,
                    photo = null,
                    sleeves = listOfNotNull(art),
                    hiddenAlbumCount = 0,
                    sleeveTitles = if (art != null) listOf(group.name) else emptyList()
                ),
                albums = emptyList(),
                playlistGroup = group
            )
        }

    private fun albumGroups(albums: List<MusicAlbum>): List<MusicColumnGroup> =
        albums
            .sortedWith(compareBy({ it.title.lowercase(Locale.ROOT) }, { it.key }))
            .map { album ->
                val single = listOf(album)
                MusicColumnGroup(
                    column = column("$ALBUM_PREFIX${album.key}", MusicColumnKind.ALBUM, album.title, single, rank(single), null),
                    albums = single,
                    playlistGroup = null
                )
            }

    private fun artistGroups(albums: List<MusicAlbum>, artists: List<MusicArtist>): List<MusicColumnGroup> {
        val photos = artists
            .filter { it.photo != null }
            .groupBy { fold(it.name) }
            .mapValues { (_, matches) -> matches.first().photo }
        val names = artists.associate { fold(it.name) to it.name.trim() }
        return groupedColumns(
            albums = albums,
            kind = MusicColumnKind.ARTIST,
            prefix = ARTIST_PREFIX,
            labels = { album ->
                album.credits.ifEmpty { listOfNotNull(album.artist) }
                    .filterNot { it.isBlank() || fold(it) == UNKNOWN_ARTIST_FOLDED }
            },
            title = { folded, first -> names[folded] ?: first },
            photo = { photos[it] },
            order = compareBy<String> { it.lowercase(Locale.ROOT) }
        )
    }

    private fun genreGroups(albums: List<MusicAlbum>): List<MusicColumnGroup> =
        groupedColumns(
            albums = albums,
            kind = MusicColumnKind.GENRE,
            prefix = GENRE_PREFIX,
            labels = { listOfNotNull(it.genre?.takeIf(String::isNotBlank)) },
            title = { _, first -> first },
            photo = { null },
            order = compareBy<String> { it.lowercase(Locale.ROOT) }
        )

    private fun yearGroups(albums: List<MusicAlbum>): List<MusicColumnGroup> =
        groupedColumns(
            albums = albums,
            kind = MusicColumnKind.YEAR,
            prefix = YEAR_PREFIX,
            labels = { listOfNotNull(it.year?.toString()) },
            title = { _, first -> first },
            photo = { null },
            order = compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }
        )

    private fun groupedColumns(
        albums: List<MusicAlbum>,
        kind: MusicColumnKind,
        prefix: String,
        labels: (MusicAlbum) -> List<String>,
        title: (folded: String, first: String) -> String,
        photo: (folded: String) -> MusicArt?,
        order: Comparator<String>
    ): List<MusicColumnGroup> {
        val (labelled, unlabelled) = albums.partition { labels(it).isNotEmpty() }
        val firstSeen = mutableMapOf<String, String>()
        val named = labelled
            .flatMap { album ->
                labels(album).map { raw ->
                    val folded = fold(raw)
                    firstSeen.getOrPut(folded) { raw.trim() }
                    folded to album
                }
            }
            .distinct()
            .groupBy({ it.first }, { it.second })
            .map { (folded, members) ->
                val shown = title(folded, firstSeen.getValue(folded))
                shown to MusicColumnGroup(
                    column = column("$prefix$folded", kind, shown, members, rank(members), photo(folded)),
                    albums = orderedContents(kind, members),
                    playlistGroup = null
                )
            }
            .sortedWith(compareBy(order) { it.first })
            .map { it.second }
        val none = unlabelled.takeIf { it.isNotEmpty() }?.let { members ->
            MusicColumnGroup(
                column = column("$prefix$NONE_SUFFIX", kind, "", members, rank(members), null),
                albums = orderedContents(kind, members),
                playlistGroup = null
            )
        }
        return named + listOfNotNull(none)
    }

    private fun orderedContents(kind: MusicColumnKind, albums: List<MusicAlbum>): List<MusicAlbum> =
        if (kind == MusicColumnKind.ARTIST) {
            albums.sortedWith(compareBy<MusicAlbum>({ it.year ?: Int.MAX_VALUE }, { it.title.lowercase(Locale.ROOT) }))
        } else {
            albums.sortedBy { it.title.lowercase(Locale.ROOT) }
        }

    private fun column(
        key: String,
        kind: MusicColumnKind,
        title: String,
        albums: List<MusicAlbum>,
        ranked: List<MusicAlbum>,
        photo: MusicArt?
    ): MusicColumn {
        val sleeved = ranked.filter { it.art != null }.take(MAX_SLEEVES)
        return MusicColumn(
            key = key,
            kind = kind,
            title = title,
            sources = albums.mapTo(LinkedHashSet()) { it.source },
            albumCount = albums.size,
            trackCount = albums.sumOf { it.trackCount },
            durationSec = albums.sumOf { it.durationSec },
            heroArt = sleeved.firstOrNull()?.art,
            photo = photo,
            sleeves = sleeved.mapNotNull { it.art },
            hiddenAlbumCount = (albums.size - sleeved.size).coerceAtLeast(0),
            sleeveTitles = sleeved.map { it.title }
        )
    }

    private fun rank(albums: List<MusicAlbum>): List<MusicAlbum> =
        albums.sortedWith(
            compareByDescending<MusicAlbum> { it.playCount }
                .thenByDescending { it.year ?: Int.MIN_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )

    private fun fold(name: String): String = name.trim().lowercase(Locale.ROOT)

    companion object {
        const val RECENT_KEY = "recent"
        const val PLAYLIST_PREFIX = "playlist:"
        const val ALBUM_PREFIX = "album:"
        const val ARTIST_PREFIX = "artist:"
        const val YEAR_PREFIX = "year:"
        const val GENRE_PREFIX = "genre:"
        private const val NONE_SUFFIX = "none"
        const val ARTIST_NONE_KEY = "$ARTIST_PREFIX$NONE_SUFFIX"
        const val YEAR_NONE_KEY = "$YEAR_PREFIX$NONE_SUFFIX"
        const val GENRE_NONE_KEY = "$GENRE_PREFIX$NONE_SUFFIX"
        const val NAVIDROME_UNKNOWN_ARTIST = "[Unknown Artist]"
        private val UNKNOWN_ARTIST_FOLDED = NAVIDROME_UNKNOWN_ARTIST.lowercase(Locale.ROOT)
    }
}
