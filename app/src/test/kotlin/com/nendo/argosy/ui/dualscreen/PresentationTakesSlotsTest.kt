package com.nendo.argosy.ui.dualscreen

import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationTakesSlotsTest {

    @Test
    fun `an idle presentation screen takes the music slots`() {
        assertTrue(presentationTakesSlots(presentationShowsHints = true, shownSlot = PresentationSlot.Fallback))
    }

    @Test
    fun `a menu drawn over the presentation screen keeps the split`() {
        val menu = mockk<PresentationSlot.NavigationMenu>()
        assertTrue(presentationTakesSlots(presentationShowsHints = true, shownSlot = menu))
    }

    @Test
    fun `no presentation screen means single-screen layouts`() {
        assertFalse(presentationTakesSlots(presentationShowsHints = false, shownSlot = PresentationSlot.Fallback))
    }

    @Test
    fun `a live game or media playback keeps the presentation screen`() {
        assertFalse(presentationTakesSlots(true, mockk<PresentationSlot.InGame>()))
        assertFalse(presentationTakesSlots(true, PresentationSlot.MediaPlayback("item")))
    }
}
