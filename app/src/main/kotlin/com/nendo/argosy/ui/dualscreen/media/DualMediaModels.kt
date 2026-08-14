package com.nendo.argosy.ui.dualscreen.media

import com.nendo.argosy.ui.screens.media.MediaItemUi

/**
 * One line of the companion's media panel.
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
 * [nowPlaying] is the item the player has open, which is what the metadata block describes. The rows
 * below it are the episodes of the same season when an episode is playing, and the two media rails
 * otherwise - a movie has no siblings to list, and neither does an empty player.
 *
 * [focusedRowIndex] indexes [rows] and always points at a [DualMediaRow.Item]; movement skips the
 * headers rather than letting the cursor rest on one.
 */
data class DualMediaUiState(
    val nowPlaying: MediaItemUi? = null,
    val nowPlayingTitle: String = "",
    val nowPlayingSubtitle: String? = null,
    val isPlaying: Boolean = false,
    val rows: List<DualMediaRow> = emptyList(),
    val focusedRowIndex: Int = -1,
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = true
) {
    val focusedItem: MediaItemUi?
        get() = (rows.getOrNull(focusedRowIndex) as? DualMediaRow.Item)?.item

    val hasRows: Boolean get() = rows.any { it is DualMediaRow.Item }

    val isEmpty: Boolean get() = !isLoading && nowPlaying == null && !hasRows
}
