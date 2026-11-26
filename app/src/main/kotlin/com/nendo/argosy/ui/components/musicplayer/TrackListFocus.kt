package com.nendo.argosy.ui.components.musicplayer

/**
 * Where the cursor sat when focus left a track list, and which queue entry was playing then.
 * Re-entering the list restores [position] only while the same entry is still playing.
 */
data class TrackListCursor(
    val position: Int,
    val playingIndex: Int,
    val sourceId: String
)

fun trackListEntryFocus(cursor: TrackListCursor?, playingIndex: Int, sourceId: String, trackCount: Int): Int {
    val last = (trackCount - 1).coerceAtLeast(0)
    val remembered = cursor?.takeIf { it.playingIndex == playingIndex && it.sourceId == sourceId }
    return (remembered?.position ?: playingIndex).coerceIn(0, last)
}

/**
 * The cursor of a list after the playing entry moves from [previousPlaying] to [playing]: it follows
 * only when the list holds focus and the cursor sat on the entry that was playing.
 */
fun followPlayingFocus(focus: Int, previousPlaying: Int?, playing: Int?, listFocused: Boolean): Int = when {
    !listFocused || previousPlaying == null || playing == null -> focus
    focus != previousPlaying -> focus
    else -> playing
}
