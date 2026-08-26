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
 * What the companion shows while a film or an episode is open on the other screen.
 *
 * [nowPlaying] is the item the player has open, which is what the hero block describes. An episode
 * brings the show mode with it: [seasons] and [episodes] carry the selected season's run, and
 * tapping a season swaps the listed episodes without leaving the panel. A film has neither, so it
 * gets [overview], [cast] and the related titles in [rows] instead.
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
    val episodes: List<MediaItemUi> = emptyList(),
    /**
     * The episode the browse cursor is on within [episodes]. Only meaningful while the panel is
     * controller-driven with nothing playing; the touch-only playing readout never draws it.
     */
    val focusedEpisodeIndex: Int = -1,
    val nowPlayingEpisodeId: String? = null,
    /**
     * True while the panel is browsing a requested series with nothing playing: the season and
     * episode surfaces render without a now-playing marker to anchor them.
     */
    val isEpisodeBrowse: Boolean = false,
    /**
     * True while a season's episodes are being fetched from the server. Episodes are stored a
     * season at a time and only on request, so an empty list mid-fetch is "loading", not "this
     * season has nothing".
     */
    val isFetchingEpisodes: Boolean = false,
    val episodeFetchError: String? = null
) {
    val focusedItem: MediaItemUi?
        get() = (rows.getOrNull(focusedRowIndex) as? DualMediaRow.Item)?.item

    val focusedEpisode: MediaItemUi?
        get() = episodes.getOrNull(focusedEpisodeIndex)

    val hasRows: Boolean get() = rows.any { it is DualMediaRow.Item }

    val isShowMode: Boolean get() = nowPlayingEpisodeId != null || isEpisodeBrowse

    val isEmpty: Boolean get() = !isLoading && nowPlaying == null && !hasRows
}
