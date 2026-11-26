package com.nendo.argosy.ui.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSeekTest {

    private val midTrack = PlaybackPosition(positionMs = 60_000L, durationMs = 180_000L)

    @Test
    fun `seeking by a delta moves from the current position`() {
        assertEquals(70_000L, resolveSeek(midTrack, overrideActive = false, request = SeekRequest.By(10_000L)))
        assertEquals(50_000L, resolveSeek(midTrack, overrideActive = false, request = SeekRequest.By(-10_000L)))
    }

    @Test
    fun `seeking past either end stops at the end`() {
        assertEquals(0L, resolveSeek(midTrack, overrideActive = false, request = SeekRequest.By(-90_000L)))
        assertEquals(180_000L, resolveSeek(midTrack, overrideActive = false, request = SeekRequest.By(500_000L)))
        assertEquals(0L, resolveSeek(midTrack, overrideActive = false, request = SeekRequest.To(-1L)))
        assertEquals(180_000L, resolveSeek(midTrack, overrideActive = false, request = SeekRequest.To(181_000L)))
    }

    @Test
    fun `seeking to a position inside the track lands on it`() {
        assertEquals(42_000L, resolveSeek(midTrack, overrideActive = false, request = SeekRequest.To(42_000L)))
    }

    @Test
    fun `nothing seeks without a prepared track`() {
        assertNull(resolveSeek(null, overrideActive = false, request = SeekRequest.By(10_000L)))
    }

    @Test
    fun `nothing seeks while an override plays over the queue`() {
        assertNull(resolveSeek(midTrack, overrideActive = true, request = SeekRequest.By(10_000L)))
        assertNull(resolveSeek(midTrack, overrideActive = true, request = SeekRequest.To(0L)))
    }

    @Test
    fun `nothing seeks in a track of unknown length`() {
        val unknown = PlaybackPosition(positionMs = 5_000L, durationMs = 0L)

        assertNull(resolveSeek(unknown, overrideActive = false, request = SeekRequest.By(10_000L)))
    }
}
