package com.nendo.argosy.ui.components.nowplaying

import com.nendo.argosy.ui.audio.AmbientPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingPillTest {

    private val playing = AmbientPlaybackState(
        trackTitle = "Gusty Garden",
        artist = "Mahito Yokota",
        count = 12,
        isPlaying = true
    )

    @Test
    fun `a playing queue shows title and artist, animated`() {
        assertEquals(
            NowPlayingPillUi(title = "Gusty Garden", artist = "Mahito Yokota", playing = true),
            nowPlayingPill(playing, suppressed = false)
        )
    }

    @Test
    fun `a paused queue still shows, frozen`() {
        val pill = nowPlayingPill(playing.copy(isPlaying = false, userPaused = true), suppressed = false)

        assertFalse(pill!!.playing)
    }

    @Test
    fun `a held-for-launch queue reads as not playing`() {
        val pill = nowPlayingPill(playing.copy(isPlaying = true, userPaused = true), suppressed = false)

        assertFalse(pill!!.playing)
    }

    @Test
    fun `no queue means no segment`() {
        assertNull(nowPlayingPill(AmbientPlaybackState(), suppressed = false))
        assertNull(nowPlayingPill(playing.copy(count = 0), suppressed = false))
    }

    @Test
    fun `a suppression hides the segment`() {
        assertNull(nowPlayingPill(playing, suppressed = true))
    }

    @Test
    fun `a soundtrack without an artist falls back to the game title`() {
        val pill = nowPlayingPill(playing.copy(artist = null, gameTitle = "Super Mario Galaxy"), suppressed = false)

        assertEquals("Super Mario Galaxy", pill!!.artist)
    }

    @Test
    fun `a blank artist shows the title alone`() {
        val pill = nowPlayingPill(playing.copy(artist = " "), suppressed = false)

        assertNull(pill!!.artist)
    }

    @Test
    fun `an override shows its own title with no artist`() {
        val pill = nowPlayingPill(playing.copy(overrideTitle = "Title Theme"), suppressed = false)

        assertEquals("Title Theme", pill!!.title)
        assertNull(pill.artist)
    }

    @Test
    fun `equaliser bars stay between the floor and full height`() {
        for (step in 0..20) {
            val phase = step / 20f
            for (index in 0 until 3) {
                val fraction = equaliserBarFraction(index, 3, phase, 0.3f)
                assertTrue(fraction in 0.3f..1f)
            }
        }
    }

    @Test
    fun `equaliser bars are out of step with each other`() {
        val heights = (0 until 3).map { equaliserBarFraction(it, 3, 0f, 0.3f) }

        assertEquals(3, heights.distinct().size)
    }

    @Test
    fun `the marquee runs at the given speed`() {
        assertEquals(2_000, marqueeDurationMs(distancePx = 96f, pxPerSecond = 48f))
        assertEquals(0, marqueeDurationMs(distancePx = 0f, pxPerSecond = 48f))
    }
}
