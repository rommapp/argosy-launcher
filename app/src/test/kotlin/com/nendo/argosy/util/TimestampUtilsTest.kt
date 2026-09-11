package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TimestampUtilsTest {

    private val expected = Instant.parse("2026-09-08T23:57:54.531Z").toEpochMilli()

    @Test
    fun `parses an instant with a trailing zulu marker`() {
        assertEquals(expected, parseTimestamp("2026-09-08T23:57:54.531Z"))
    }

    @Test
    fun `parses an offset timestamp`() {
        assertEquals(expected, parseTimestamp("2026-09-09T08:57:54.531+09:00"))
    }

    @Test
    fun `reads a naive iso timestamp as utc`() {
        assertEquals(expected, parseTimestamp("2026-09-08T23:57:54.531000"))
    }

    @Test
    fun `reads a naive iso timestamp without fractions as utc`() {
        assertEquals(
            Instant.parse("2026-09-08T23:57:54Z").toEpochMilli(),
            parseTimestamp("2026-09-08T23:57:54")
        )
    }

    @Test
    fun `reads a space separated timestamp as utc`() {
        assertEquals(
            Instant.parse("2026-09-08T23:57:54Z").toEpochMilli(),
            parseTimestamp("2026-09-08 23:57:54")
        )
    }

    @Test
    fun `refuses text that is not a timestamp`() {
        assertNull(parseTimestamp("not a date"))
        assertNull(parseTimestamp(""))
    }
}
