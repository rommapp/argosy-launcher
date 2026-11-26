package com.nendo.argosy.data.music.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenProgressTest {

    private val trackMs = 200_000L

    @Test
    fun `audible time accumulates and the position follows it`() {
        val progress = ListenProgress(startPositionMs = 0L, durationMs = trackMs)

        progress.startAudible(clockMs = 1_000L)
        progress.accumulate(clockMs = 11_000L)

        assertEquals(10_000L, progress.listenedMs)
        assertEquals(10_000L, progress.positionMs)
    }

    @Test
    fun `seeking forward moves the position without adding heard time`() {
        val progress = ListenProgress(startPositionMs = 0L, durationMs = trackMs)
        progress.startAudible(clockMs = 0L)

        progress.seek(clockMs = 10_000L, positionMs = 150_000L, durationMs = trackMs)
        progress.accumulate(clockMs = 15_000L)

        assertEquals(15_000L, progress.listenedMs)
        assertEquals(155_000L, progress.positionMs)
        assertFalse(MusicListenRules.isCounted(progress.listenedMs, progress.durationMs))
    }

    @Test
    fun `seeking back moves the position without removing heard time`() {
        val progress = ListenProgress(startPositionMs = 0L, durationMs = trackMs)
        progress.startAudible(clockMs = 0L)

        progress.seek(clockMs = 90_000L, positionMs = 0L, durationMs = trackMs)
        progress.accumulate(clockMs = 100_000L)

        assertEquals(100_000L, progress.listenedMs)
        assertEquals(10_000L, progress.positionMs)
        assertTrue(MusicListenRules.isCounted(progress.listenedMs, progress.durationMs))
    }

    @Test
    fun `a seek while paused adds no time and resumes from the new position`() {
        val progress = ListenProgress(startPositionMs = 0L, durationMs = trackMs)
        progress.startAudible(clockMs = 0L)
        progress.stopAudible(clockMs = 20_000L)

        progress.seek(clockMs = 60_000L, positionMs = 120_000L, durationMs = trackMs)
        progress.startAudible(clockMs = 70_000L)
        progress.accumulate(clockMs = 75_000L)

        assertEquals(25_000L, progress.listenedMs)
        assertEquals(125_000L, progress.positionMs)
    }

    @Test
    fun `a listen that starts mid-track begins at its start position`() {
        val progress = ListenProgress(startPositionMs = 30_000L, durationMs = trackMs)

        progress.startAudible(clockMs = 0L)
        progress.accumulate(clockMs = 4_000L)

        assertEquals(34_000L, progress.positionMs)
    }

    @Test
    fun `a longer reported duration replaces the stored one, a shorter one does not`() {
        val progress = ListenProgress(startPositionMs = 0L, durationMs = trackMs)

        progress.observe(positionMs = null, durationMs = 210_000L)
        progress.observe(positionMs = null, durationMs = 100_000L)

        assertEquals(210_000L, progress.durationMs)
    }
}
