package com.nendo.argosy.ui.dualscreen.media

import com.nendo.argosy.ui.screens.media.MediaCastUi
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.MediaSeasonUi

/**
 * One line of the companion's media panel while nothing episodic drives it: the browse rails when
 * the player is empty, and the related titles under a film.
 *
 * The panel is a single vertical list rather than a set of rails: it is drawn on a companion screen
 * a few centimetres tall, where a horizontal rail shows two tiles and hides the rest. Headers keep
 * the groups apart and are never focusable, so the cursor only ever lands on something playable.
 */
sealed interface DualMediaRow {
    data class Header(val label: String) : DualMediaRow

    data class Item(
        val item: MediaItemUi,
        val isNowPlaying: Boolean = false
    ) : DualMediaRow
}

/**
 * How the panel lays the selected season's episodes out: the vertical list that shows the brief on
 * every row, or the horizontal rail that shows more episodes at once.
 */
enum class DualMediaEpisodeLayout { LIST, RAIL }

/**
 * What the companion shows while a film or an episode is open on the other screen.
 *
 * [nowPlaying] is the item the player has open, which is what the hero block describes. An episode
 * brings the show mode with it: [seasons] and [episodes] carry the selected season's run, and the
 * toolbar swaps seasons without leaving the panel. A film has neither, so it gets [overview],
 * [cast] and the related titles in [rows] instead.
 *
 * While [isPlaybackLive] the panel is touch-driven only - the controller belongs to the player on
 * the other screen, so no cursor is drawn and no gamepad hint is offered. [focusedRowIndex] still
 * exists for the one state without a player: browsing the rails with nothing open.
 */
data class DualMediaUiState(
    val nowPlaying: MediaItemUi? = null,
    val nowPlayingTitle: String = "",
    val isPlaying: Boolean = false,
    val isPlaybackLive: Boolean = false,
    val rows: List<DualMediaRow> = emptyList(),
    val focusedRowIndex: Int = -1,
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = true,
    val overview: String? = null,
    val cast: List<MediaCastUi> = emptyList(),
    val seasons: List<MediaSeasonUi> = emptyList(),
    val selectedSeasonIndex: Int = -1,
    val isSeasonPickerOpen: Boolean = false,
    val episodes: List<MediaItemUi> = emptyList(),
    val nowPlayingEpisodeId: String? = null,
    val episodeLayout: DualMediaEpisodeLayout = DualMediaEpisodeLayout.LIST,
    /**
     * Bumped by the jump affordance; the list scrolls to the playing episode when it changes. A
     * counter rather than a flag so pressing jump twice scrolls twice.
     */
    val jumpNonce: Int = 0
) {
    val focusedItem: MediaItemUi?
        get() = (rows.getOrNull(focusedRowIndex) as? DualMediaRow.Item)?.item

    val hasRows: Boolean get() = rows.any { it is DualMediaRow.Item }

    val isShowMode: Boolean get() = nowPlayingEpisodeId != null

    val selectedSeason: MediaSeasonUi? get() = seasons.getOrNull(selectedSeasonIndex)

    val isEmpty: Boolean get() = !isLoading && nowPlaying == null && !hasRows
}
