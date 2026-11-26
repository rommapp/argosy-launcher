package com.nendo.argosy.ui.components.musicplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackListFocusTest {

    @Test
    fun `focus on the previously playing entry follows the new one`() {
        assertEquals(4, followPlayingFocus(focus = 3, previousPlaying = 3, playing = 4, listFocused = true))
    }

    @Test
    fun `focus on another entry stays put`() {
        assertEquals(1, followPlayingFocus(focus = 1, previousPlaying = 3, playing = 4, listFocused = true))
    }

    @Test
    fun `an unfocused list never follows`() {
        assertEquals(3, followPlayingFocus(focus = 3, previousPlaying = 3, playing = 4, listFocused = false))
    }

    @Test
    fun `first entry lands on the playing entry`() {
        assertEquals(2, trackListEntryFocus(cursor = null, playingIndex = 2, sourceId = "a", trackCount = 5))
    }

    @Test
    fun `re-entry restores the cursor while the same entry plays`() {
        val cursor = TrackListCursor(position = 4, playingIndex = 2, sourceId = "a")
        assertEquals(4, trackListEntryFocus(cursor, playingIndex = 2, sourceId = "a", trackCount = 5))
    }

    @Test
    fun `re-entry after a skip lands on the playing entry`() {
        val cursor = TrackListCursor(position = 4, playingIndex = 2, sourceId = "a")
        assertEquals(3, trackListEntryFocus(cursor, playingIndex = 3, sourceId = "a", trackCount = 5))
    }

    @Test
    fun `re-entry after a source change lands on the playing entry`() {
        val cursor = TrackListCursor(position = 4, playingIndex = 2, sourceId = "a")
        assertEquals(2, trackListEntryFocus(cursor, playingIndex = 2, sourceId = "b", trackCount = 5))
    }

    @Test
    fun `restored cursor is clamped to a shorter list`() {
        val cursor = TrackListCursor(position = 9, playingIndex = 1, sourceId = "a")
        assertEquals(2, trackListEntryFocus(cursor, playingIndex = 1, sourceId = "a", trackCount = 3))
    }
}
