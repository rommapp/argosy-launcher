package com.nendo.argosy.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostedSurfaceTest {
    @Test
    fun `status pill disappears only when every status item is hidden`() {
        val hidden = StatusBarItems(clock = false, battery = false, network = false)
        assertFalse(hasStatusBarContent(hidden, scrapingArtwork = false))
        assertTrue(hasStatusBarContent(hidden.copy(clock = true), scrapingArtwork = false))
        assertTrue(hasStatusBarContent(hidden.copy(battery = true), scrapingArtwork = false))
        assertTrue(hasStatusBarContent(hidden.copy(network = true), scrapingArtwork = false))
        assertTrue(hasStatusBarContent(hidden, scrapingArtwork = true))
    }

    @Test
    fun `accent footer chooses readable foreground at both contrast extremes`() {
        assertEquals(Color.White, contrastingFrostedContent(Color.Black))
        assertEquals(Color.Black, contrastingFrostedContent(Color.White))
        assertEquals(Color.Black, contrastingFrostedContent(Color.Yellow))
        assertEquals(Color.White, contrastingFrostedContent(Color.Blue))
    }

    @Test
    fun `narrow footer keeps non-obvious actions and drops obvious guides`() {
        assertEquals(
            listOf(1, 2),
            fittingFooterHintIndices(listOf(50, 70, 80), listOf(0, 4, 3), 166, 16)
        )
    }

    @Test
    fun `one long footer label does not force two overflowing hints`() {
        assertEquals(
            listOf(0),
            fittingFooterHintIndices(listOf(180, 80), listOf(4, 3), 100, 16)
        )
        assertEquals(emptyList<Int>(), fittingFooterHintIndices(emptyList(), emptyList(), 0, 16))
    }
}
