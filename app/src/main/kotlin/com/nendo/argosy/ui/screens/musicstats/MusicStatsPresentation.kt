package com.nendo.argosy.ui.screens.musicstats

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.music.MusicStatsCalendar
import com.nendo.argosy.ui.dualscreen.BreakdownRow
import com.nendo.argosy.ui.dualscreen.PresentOnCompanionLive
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.screens.music.MusicShowcaseState
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.formatMonthYear

internal enum class MusicStatsShowcaseKind { BREAKDOWN, ITEM }

internal fun MusicStatsUiState.showcaseKind(): MusicStatsShowcaseKind? {
    if (page.loadFailed) return null
    val shelf = focusedShelf ?: return null
    return when {
        shelf.kind == MusicStatsShelfKind.MONTH -> MusicStatsShowcaseKind.BREAKDOWN
        itemIndex in 0 until shelf.itemCount -> MusicStatsShowcaseKind.ITEM
        else -> null
    }
}

@Composable
internal fun MusicStatsOnPresentation(uiState: MusicStatsUiState, owner: SlotOwner) {
    val slot = when (uiState.showcaseKind()) {
        MusicStatsShowcaseKind.BREAKDOWN -> monthBreakdown(uiState)
        MusicStatsShowcaseKind.ITEM -> uiState.focusedShelf?.let { itemShowcase(it, uiState) }
        null -> null
    } ?: return
    PresentOnCompanionLive(owner = owner, slot = slot)
}

@Composable
private fun monthBreakdown(uiState: MusicStatsUiState): PresentationSlot.Breakdown {
    val totals = uiState.totals
    val separator = stringResource(R.string.music_stats_showcase_separator)
    val subtitle = totals?.takeIf { it.playCount + it.trackCount > 0 }?.let {
        listOf(
            musicStatsListened(it.listenedMs),
            pluralStringResource(R.plurals.music_stats_count_plays, it.playCount, it.playCount)
        ).joinToString(separator)
    }
    val artists = uiState.month.shelves.firstOrNull { it.kind == MusicStatsShelfKind.ARTISTS }?.artists.orEmpty()
    val top = artists.maxOfOrNull { it.listenedMs }?.takeIf { it > 0 } ?: 1L
    val accent: Color = LocalArgosyTheme.current.focusAccent
    val rows = artists.map { artist ->
        BreakdownRow(
            label = artist.name,
            value = musicStatsListened(artist.listenedMs),
            fraction = artist.listenedMs.toFloat() / top,
            color = accent
        )
    }
    return PresentationSlot.Breakdown(
        title = formatMonthYear(MusicStatsCalendar.toYearMonth(uiState.yearMonth)),
        subtitle = subtitle,
        rows = rows
    )
}

@Composable
private fun itemShowcase(shelf: MusicStatsShelf, uiState: MusicStatsUiState): PresentationSlot.MusicShowcase? {
    val separator = stringResource(R.string.music_stats_showcase_separator)
    val kicker = musicStatsShelfTitle(shelf.title, uiState.yearMonth)
    val index = uiState.itemIndex
    val state = when (shelf.kind) {
        MusicStatsShelfKind.TRACKS -> shelf.tracks.getOrNull(index)?.let { track ->
            MusicShowcaseState.Item(
                kicker = kicker,
                title = track.stat.title,
                subtitle = track.stat.artistCredit ?: track.stat.trackGameTitle,
                facts = listeningFacts(track.stat.playCount, track.stat.listenedMs, separator),
                art = track.art,
                backdropPath = shelf.coverPath
            )
        }
        MusicStatsShelfKind.ALBUMS -> shelf.albums.getOrNull(index)?.let { album ->
            MusicShowcaseState.Item(
                kicker = kicker,
                title = album.album?.title ?: album.stat.title ?: album.stat.trackGameTitle.orEmpty(),
                subtitle = album.album?.artist ?: album.stat.artistCredit,
                facts = listeningFacts(album.stat.playCount, album.stat.listenedMs, separator),
                art = album.album?.art,
                backdropPath = shelf.coverPath
            )
        }
        MusicStatsShelfKind.ARTISTS -> shelf.artists.getOrNull(index)?.let { artist ->
            MusicShowcaseState.Item(
                kicker = kicker,
                title = artist.name,
                subtitle = null,
                facts = listeningFacts(artist.playCount, artist.listenedMs, separator),
                art = null,
                backdropPath = shelf.coverPath
            )
        }
        MusicStatsShelfKind.MONTH -> null
    } ?: return null
    return PresentationSlot.MusicShowcase(state)
}

@Composable
private fun listeningFacts(playCount: Int, listenedMs: Long, separator: String): String =
    listOf(
        pluralStringResource(R.plurals.music_stats_count_plays, playCount, playCount),
        musicStatsListened(listenedMs)
    ).joinToString(separator)
