package com.nendo.argosy.ui.screens.musicstats

import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicFlashbackYear
import com.nendo.argosy.domain.model.MusicPairingGroup
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicStatAlbum
import com.nendo.argosy.domain.model.MusicStatArtist
import com.nendo.argosy.domain.model.MusicStatTrack
import com.nendo.argosy.domain.music.CreditSplitter
import com.nendo.argosy.domain.music.MusicKeys

/**
 * Builds the Listening screen's shelves from stats query results. Shelf keys are stable per
 * content, so focus and options can address a shelf across reloads.
 */
object MusicStatsShelves {
    const val MONTH_KEY = "month"
    private const val TOP_TRACKS_KEY = "top_tracks"
    private const val TOP_ALBUMS_KEY = "top_albums"
    private const val TOP_ARTISTS_KEY = "top_artists"
    private const val ON_THIS_DAY_PREFIX = "day:"
    private const val IN_MONTH_PREFIX = "month:"

    fun monthShelves(
        tracks: List<MusicStatTrack>,
        albums: List<MusicStatAlbum>,
        artists: List<MusicStatArtist>
    ): List<MusicStatsShelf> = buildList {
        add(MusicStatsShelf(MONTH_KEY, MusicStatsShelfKind.MONTH, MusicStatsShelfTitle.Month))
        if (tracks.isNotEmpty()) {
            add(
                MusicStatsShelf(
                    key = TOP_TRACKS_KEY,
                    kind = MusicStatsShelfKind.TRACKS,
                    title = MusicStatsShelfTitle.TopTracks,
                    tracks = tracks.map { MusicStatsTrackUi(it) },
                    listenedMs = tracks.sumOf { it.listenedMs }
                )
            )
        }
        if (albums.isNotEmpty()) {
            add(
                MusicStatsShelf(
                    key = TOP_ALBUMS_KEY,
                    kind = MusicStatsShelfKind.ALBUMS,
                    title = MusicStatsShelfTitle.TopAlbums,
                    albums = albums.map { MusicStatsAlbumUi(it) },
                    listenedMs = albums.sumOf { it.listenedMs }
                )
            )
        }
        if (artists.isNotEmpty()) {
            add(
                MusicStatsShelf(
                    key = TOP_ARTISTS_KEY,
                    kind = MusicStatsShelfKind.ARTISTS,
                    title = MusicStatsShelfTitle.TopArtists,
                    artists = artists,
                    listenedMs = artists.sumOf { it.listenedMs }
                )
            )
        }
    }

    /**
     * "On this day" years first, then "this month in" years, each newest first. [currentYear] is
     * left out of both, and years without tracks are dropped.
     */
    fun flashbackShelves(
        onThisDay: List<MusicFlashbackYear>,
        inMonth: List<MusicFlashbackYear>,
        month: Int,
        currentYear: Int
    ): List<MusicStatsShelf> {
        val past = { years: List<MusicFlashbackYear> ->
            years.filter { it.year != currentYear && it.tracks.isNotEmpty() }.sortedByDescending { it.year }
        }
        val days = past(onThisDay).map { year ->
            MusicStatsShelf(
                key = "$ON_THIS_DAY_PREFIX${year.year}",
                kind = MusicStatsShelfKind.TRACKS,
                title = MusicStatsShelfTitle.OnThisDay(year.year),
                tracks = year.tracks.map { MusicStatsTrackUi(it) },
                listenedMs = year.listenedMs
            )
        }
        val months = past(inMonth).map { year ->
            MusicStatsShelf(
                key = "$IN_MONTH_PREFIX${year.year}",
                kind = MusicStatsShelfKind.TRACKS,
                title = MusicStatsShelfTitle.MonthInYear(year.year, month),
                tracks = year.tracks.map { MusicStatsTrackUi(it) },
                listenedMs = year.listenedMs
            )
        }
        return days + months
    }

    /**
     * One TRACKS shelf per group with tracks. [labels] replaces a group's stored label when it
     * holds the group key; [covers] gives a game cover per group key.
     */
    fun pairingShelves(
        kind: MusicStatsPairingKind,
        groups: List<MusicPairingGroup>,
        labels: Map<String, String> = emptyMap(),
        covers: Map<String, String> = emptyMap()
    ): List<MusicStatsShelf> =
        groups.filter { it.tracks.isNotEmpty() }.map { group ->
            MusicStatsShelf(
                key = "${kind.name}:${group.groupKey}",
                kind = MusicStatsShelfKind.TRACKS,
                title = MusicStatsShelfTitle.Pairing(kind, labels[group.groupKey] ?: group.label),
                tracks = group.tracks.map { MusicStatsTrackUi(it) },
                listenedMs = group.listenedMs,
                coverPath = covers[group.groupKey]
            )
        }

    fun albumKeyOf(track: MusicStatTrack): String? =
        track.albumKey
            ?: track.romId?.takeIf { track.source == MusicSourceKind.ROMM.token }?.let(MusicKeys::rommRomAlbumKey)

    fun albumKeysOf(shelves: List<MusicStatsShelf>): Set<String> = buildSet {
        shelves.forEach { shelf ->
            shelf.tracks.forEach { track -> albumKeyOf(track.stat)?.let(::add) }
            shelf.albums.forEach { add(it.stat.albumKey) }
        }
    }

    fun withCatalog(shelves: List<MusicStatsShelf>, catalog: Map<String, MusicAlbum>): List<MusicStatsShelf> =
        shelves.map { shelf ->
            shelf.copy(
                tracks = shelf.tracks.map { track ->
                    track.copy(art = albumKeyOf(track.stat)?.let { catalog[it]?.art } ?: track.art)
                },
                albums = shelf.albums.map { album -> album.copy(album = catalog[album.stat.albumKey] ?: album.album) }
            )
        }

    fun artistTracks(pool: List<MusicStatTrack>, artistKey: String): List<MusicStatTrack> =
        pool.filter { track ->
            CreditSplitter.split(track.artistCredit).any { CreditSplitter.artistKey(it) == artistKey }
        }

    fun albumTracks(pool: List<MusicStatTrack>, albumKey: String): List<MusicStatTrack> =
        pool.filter { albumKeyOf(it) == albumKey }
}
