package com.nendo.argosy.ui.screens.musicstats

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.music.MusicStatsCalendar
import com.nendo.argosy.ui.components.recap.RecapPlayTime
import com.nendo.argosy.util.formatMonthYear

@get:StringRes
val MusicStatsTab.labelRes: Int
    get() = when (this) {
        MusicStatsTab.MONTH -> R.string.music_stats_tab_month
        MusicStatsTab.FLASHBACK -> R.string.music_stats_tab_flashback
        MusicStatsTab.GAMES -> R.string.music_stats_tab_games
    }

@get:StringRes
val MusicStatsPairingKind.kickerRes: Int
    get() = when (this) {
        MusicStatsPairingKind.GAME -> R.string.music_stats_kicker_game
        MusicStatsPairingKind.PLATFORM -> R.string.music_stats_kicker_platform
        MusicStatsPairingKind.GENRE -> R.string.music_stats_kicker_genre
        MusicStatsPairingKind.FRANCHISE -> R.string.music_stats_kicker_franchise
    }

@get:StringRes
val MusicStatsOptionAction.labelRes: Int
    get() = when (this) {
        MusicStatsOptionAction.PLAY -> R.string.music_stats_options_play
        MusicStatsOptionAction.SHUFFLE -> R.string.music_stats_options_shuffle
    }

@Composable
fun musicStatsShelfTitle(title: MusicStatsShelfTitle, yearMonth: Int): String = when (title) {
    MusicStatsShelfTitle.Month -> formatMonthYear(MusicStatsCalendar.toYearMonth(yearMonth))
    MusicStatsShelfTitle.TopTracks -> stringResource(R.string.music_stats_shelf_top_tracks)
    MusicStatsShelfTitle.TopAlbums -> stringResource(R.string.music_stats_shelf_top_albums)
    MusicStatsShelfTitle.TopArtists -> stringResource(R.string.music_stats_shelf_top_artists)
    is MusicStatsShelfTitle.OnThisDay -> stringResource(R.string.music_stats_shelf_on_this_day, title.year)
    is MusicStatsShelfTitle.MonthInYear -> stringResource(R.string.music_stats_shelf_month_in_year, title.year)
    is MusicStatsShelfTitle.Pairing -> title.label
}

@Composable
fun musicStatsListened(ms: Long): String = RecapPlayTime.of(LocalContext.current, ms)
