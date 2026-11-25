package com.nendo.argosy.ui.audio

import com.nendo.argosy.data.preferences.MusicRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackEndActionTest {

    @Test
    fun `repeat one replays the track wherever it sits in the queue`() {
        assertEquals(TrackEndAction.REPLAY, trackEndAction(MusicRepeatMode.ONE, isAtLast = false))
        assertEquals(TrackEndAction.REPLAY, trackEndAction(MusicRepeatMode.ONE, isAtLast = true))
    }

    @Test
    fun `repeat all advances past the last track so the queue loops`() {
        assertEquals(TrackEndAction.ADVANCE, trackEndAction(MusicRepeatMode.ALL, isAtLast = false))
        assertEquals(TrackEndAction.ADVANCE, trackEndAction(MusicRepeatMode.ALL, isAtLast = true))
    }

    @Test
    fun `repeat off advances until the last track and then stops`() {
        assertEquals(TrackEndAction.ADVANCE, trackEndAction(MusicRepeatMode.OFF, isAtLast = false))
        assertEquals(TrackEndAction.STOP, trackEndAction(MusicRepeatMode.OFF, isAtLast = true))
    }

    @Test
    fun `the button cycles all, one, off and back`() {
        assertEquals(MusicRepeatMode.ONE, MusicRepeatMode.ALL.next)
        assertEquals(MusicRepeatMode.OFF, MusicRepeatMode.ONE.next)
        assertEquals(MusicRepeatMode.ALL, MusicRepeatMode.OFF.next)
    }

    @Test
    fun `a stored mode reads back and anything unknown loops the queue`() {
        MusicRepeatMode.entries.forEach { assertEquals(it, MusicRepeatMode.fromString(it.name)) }
        assertEquals(MusicRepeatMode.ALL, MusicRepeatMode.fromString(null))
        assertEquals(MusicRepeatMode.ALL, MusicRepeatMode.fromString("SOMETIMES"))
    }
}
