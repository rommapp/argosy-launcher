package com.nendo.argosy.data.music.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MusicListenRulesTest {

    @Test
    fun `a listen counts at half the track`() {
        assertFalse(MusicListenRules.isCounted(listenedMs = 99_999, durationMs = 200_000))
        assertTrue(MusicListenRules.isCounted(listenedMs = 100_000, durationMs = 200_000))
    }

    @Test
    fun `long tracks count at four minutes, never later`() {
        assertFalse(MusicListenRules.isCounted(listenedMs = 239_999, durationMs = 1_200_000))
        assertTrue(MusicListenRules.isCounted(listenedMs = 240_000, durationMs = 1_200_000))
    }

    @Test
    fun `tracks under thirty seconds or of unknown length never count`() {
        assertFalse(MusicListenRules.isCounted(listenedMs = 29_999, durationMs = 29_999))
        assertTrue(MusicListenRules.isCounted(listenedMs = 15_000, durationMs = 30_000))
        assertFalse(MusicListenRules.isCounted(listenedMs = 500_000, durationMs = 0))
    }

    @Test
    fun `local day is the calendar day in the given zone`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val lateUtc = ZonedDateTime.of(2026, 11, 24, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

        assertEquals(20261125, MusicListenRules.localDay(lateUtc, tokyo))
        assertEquals(20261124, MusicListenRules.localDay(lateUtc, ZoneId.of("UTC")))
    }
}
