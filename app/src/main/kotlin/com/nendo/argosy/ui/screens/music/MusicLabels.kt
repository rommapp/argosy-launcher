package com.nendo.argosy.ui.screens.music

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.music.library.MusicColumnBuilder
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind

@get:StringRes
val MusicGrouping.labelRes: Int
    get() = when (this) {
        MusicGrouping.PLAYLISTS -> R.string.music_library_grouping_playlists
        MusicGrouping.ARTISTS -> R.string.music_library_grouping_artists
        MusicGrouping.ALBUMS -> R.string.music_library_grouping_albums
        MusicGrouping.YEARS -> R.string.music_library_grouping_years
        MusicGrouping.GENRES -> R.string.music_library_grouping_genres
    }

@get:StringRes
val MusicSourceKind.labelRes: Int
    get() = when (this) {
        MusicSourceKind.NAVIDROME -> R.string.music_library_source_navidrome
        MusicSourceKind.ROMM -> R.string.music_library_source_romm
    }

@get:StringRes
val MusicSourceFilter.labelRes: Int
    get() = when (this) {
        MusicSourceFilter.ALL -> R.string.music_library_source_all
        MusicSourceFilter.NAVIDROME -> R.string.music_library_source_navidrome
        MusicSourceFilter.ROMM -> R.string.music_library_source_romm
    }

@get:StringRes
val MusicColumnKind.kickerRes: Int
    get() = when (this) {
        MusicColumnKind.RECENT -> R.string.music_library_kicker_recent
        MusicColumnKind.PLAYLIST -> R.string.music_library_kicker_playlist
        MusicColumnKind.ARTIST -> R.string.music_library_kicker_artist
        MusicColumnKind.ALBUM -> R.string.music_library_kicker_album
        MusicColumnKind.YEAR -> R.string.music_library_kicker_year
        MusicColumnKind.GENRE -> R.string.music_library_kicker_genre
    }

@Composable
fun musicColumnTitle(column: MusicColumn): String = when {
    column.kind == MusicColumnKind.RECENT -> stringResource(R.string.music_library_recent_title)
    column.key == MusicColumnBuilder.ARTIST_NONE_KEY -> stringResource(R.string.music_library_unknown_artist)
    column.key == MusicColumnBuilder.GENRE_NONE_KEY -> stringResource(R.string.music_library_unknown_genre)
    column.key == MusicColumnBuilder.YEAR_NONE_KEY -> stringResource(R.string.music_library_unknown_year)
    else -> column.title
}

@Composable
fun musicColumnBadge(column: MusicColumn, isPlaying: Boolean): String? = when {
    isPlaying -> stringResource(R.string.music_library_badge_playing)
    column.kind == MusicColumnKind.RECENT -> stringResource(column.kind.kickerRes)
    else -> null
}

@Composable
fun musicColumnSubtitle(column: MusicColumn): String =
    if (column.kind != MusicColumnKind.PLAYLIST && column.albumCount > 0) {
        pluralStringResource(R.plurals.music_library_count_albums, column.albumCount, column.albumCount)
    } else {
        pluralStringResource(R.plurals.music_library_count_tracks, column.trackCount, column.trackCount)
    }

@Composable
fun musicColumnFacts(column: MusicColumn): String {
    val parts = buildList {
        if (column.albumCount > 0) {
            add(pluralStringResource(R.plurals.music_library_count_albums, column.albumCount, column.albumCount))
        }
        add(pluralStringResource(R.plurals.music_library_count_tracks, column.trackCount, column.trackCount))
        if (column.durationSec > 0) add(formatMusicDuration(column.durationSec))
        MusicSourceKind.entries.filter { it in column.sources }.forEach { add(stringResource(it.labelRes)) }
    }
    return parts.joinToString(stringResource(R.string.music_library_separator))
}

fun formatMusicDuration(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, rest)
    } else {
        "%d:%02d".format(minutes, rest)
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
