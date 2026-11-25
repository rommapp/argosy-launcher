package com.nendo.argosy.ui.screens.musicstats

import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicMonthTotals
import com.nendo.argosy.domain.model.MusicStatAlbum
import com.nendo.argosy.domain.model.MusicStatArtist
import com.nendo.argosy.domain.model.MusicStatTrack

enum class MusicStatsTab { MONTH, FLASHBACK, GAMES }

enum class MusicStatsShelfKind { MONTH, TRACKS, ALBUMS, ARTISTS }

enum class MusicStatsPairingKind { GAME, PLATFORM, GENRE, FRANCHISE }

enum class MusicStatsOptionAction { PLAY, SHUFFLE }

sealed interface MusicStatsShelfTitle {
    data object Month : MusicStatsShelfTitle
    data object TopTracks : MusicStatsShelfTitle
    data object TopAlbums : MusicStatsShelfTitle
    data object TopArtists : MusicStatsShelfTitle
    data class OnThisDay(val year: Int) : MusicStatsShelfTitle
    data class MonthInYear(val year: Int, val month: Int) : MusicStatsShelfTitle
    data class Pairing(val kind: MusicStatsPairingKind, val label: String) : MusicStatsShelfTitle
}

data class MusicStatsTrackUi(
    val stat: MusicStatTrack,
    val art: MusicArt? = null
)

data class MusicStatsAlbumUi(
    val stat: MusicStatAlbum,
    val album: MusicAlbum? = null
)

/**
 * One row of the Listening screen. Only the list matching [kind] is filled; a MONTH shelf is the
 * month stepper and holds a single focus stop.
 */
data class MusicStatsShelf(
    val key: String,
    val kind: MusicStatsShelfKind,
    val title: MusicStatsShelfTitle,
    val tracks: List<MusicStatsTrackUi> = emptyList(),
    val albums: List<MusicStatsAlbumUi> = emptyList(),
    val artists: List<MusicStatArtist> = emptyList(),
    val listenedMs: Long = 0,
    val coverPath: String? = null
) {
    val itemCount: Int
        get() = when (kind) {
            MusicStatsShelfKind.MONTH -> 1
            MusicStatsShelfKind.TRACKS -> tracks.size
            MusicStatsShelfKind.ALBUMS -> albums.size
            MusicStatsShelfKind.ARTISTS -> artists.size
        }
}

data class MusicStatsPage(
    val shelves: List<MusicStatsShelf> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false
) {
    val hasHistory: Boolean get() = shelves.any { it.kind != MusicStatsShelfKind.MONTH && it.itemCount > 0 }
}

data class MusicStatsOptionsState(
    val shelfKey: String,
    val itemIndex: Int,
    val focusIndex: Int = 0
) {
    val actions: List<MusicStatsOptionAction> get() = MusicStatsOptionAction.entries
}

/**
 * The Listening screen. [yearMonth] is the month the MONTH tab shows and [currentYearMonth] the
 * month it cannot step past, both yyyymm. Focus is [shelfIndex] within the active tab's page and
 * [itemIndex] within that shelf.
 */
data class MusicStatsUiState(
    val tab: MusicStatsTab = MusicStatsTab.MONTH,
    val yearMonth: Int = 0,
    val currentYearMonth: Int = 0,
    val totals: MusicMonthTotals? = null,
    val month: MusicStatsPage = MusicStatsPage(),
    val flashback: MusicStatsPage = MusicStatsPage(),
    val games: MusicStatsPage = MusicStatsPage(),
    val shelfIndex: Int = 0,
    val itemIndex: Int = 0,
    val options: MusicStatsOptionsState? = null
) {
    val page: MusicStatsPage
        get() = when (tab) {
            MusicStatsTab.MONTH -> month
            MusicStatsTab.FLASHBACK -> flashback
            MusicStatsTab.GAMES -> games
        }
    val focusedShelf: MusicStatsShelf? get() = page.shelves.getOrNull(shelfIndex)
    val canStepForward: Boolean get() = yearMonth < currentYearMonth
    val canPlayFocused: Boolean
        get() {
            val shelf = focusedShelf ?: return false
            return if (shelf.kind == MusicStatsShelfKind.MONTH) {
                month.shelves.any { it.kind == MusicStatsShelfKind.TRACKS }
            } else {
                shelf.itemCount > 0
            }
        }
    val optionsShelf: MusicStatsShelf? get() = options?.let { o -> page.shelves.firstOrNull { it.key == o.shelfKey } }
}
