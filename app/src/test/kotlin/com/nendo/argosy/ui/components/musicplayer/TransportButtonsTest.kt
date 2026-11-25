package com.nendo.argosy.ui.components.musicplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransportButtonsTest {

    @Test
    fun `play sits in the middle of the row with the playlist button`() {
        val buttons = transportButtons(withPlaylist = true)
        assertEquals(MusicTransportButton.PLAY_PAUSE, buttons[buttons.size / 2])
    }

    @Test
    fun `the row without a playlist button never lands on it`() {
        val buttons = transportButtons(withPlaylist = false)
        assertFalse(MusicTransportButton.ADD_TO_PLAYLIST in buttons)
        assertEquals(MusicTransportButton.BACKGROUND, buttons.stepFrom(MusicTransportButton.SHUFFLE, -1))
    }

    @Test
    fun `stepping wraps at both ends`() {
        val buttons = transportButtons(withPlaylist = true)
        assertEquals(MusicTransportButton.ADD_TO_PLAYLIST, buttons.stepFrom(MusicTransportButton.BACKGROUND, 1))
        assertEquals(MusicTransportButton.BACKGROUND, buttons.stepFrom(MusicTransportButton.ADD_TO_PLAYLIST, -1))
        assertEquals(MusicTransportButton.NEXT, buttons.stepFrom(MusicTransportButton.PLAY_PAUSE, 1))
    }
}
