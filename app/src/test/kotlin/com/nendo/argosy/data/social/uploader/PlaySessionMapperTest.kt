package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PlaySessionMapperTest {

    private fun session(
        start: Instant = Instant.parse("2026-05-15T12:00:00Z"),
        end: Instant = Instant.parse("2026-05-15T12:30:00Z"),
        activePlayMs: Long = 1_800_000L
    ) = PlaySessionEntity(
        id = 0,
        userId = "user-1",
        gameId = 1L,
        igdbId = 42L,
        gameTitle = "Test Game",
        platformSlug = "snes",
        startTime = start,
        endTime = end,
        continued = false,
        deviceId = "device-x",
        deviceManufacturer = "Acme",
        deviceModel = "ZX-90",
        activePlayMs = activePlayMs,
        standbyMs = 0
    )

    @Test
    fun `maps active session against the rom id it was selected with`() {
        val entry = PlaySessionMapper.toRomMEntry(session(), rommId = 99L)

        assertEquals(99L, entry?.romId)
        assertEquals("2026-05-15T12:00:00Z", entry?.startTime)
        assertEquals("2026-05-15T12:30:00Z", entry?.endTime)
        assertEquals(1_800_000L, entry?.durationMs)
        assertNull(entry?.saveSlot)
    }

    @Test
    fun `drops session where end equals start`() {
        val t = Instant.parse("2026-05-15T12:00:00Z")

        assertNull(PlaySessionMapper.toRomMEntry(session(start = t, end = t), rommId = 99L))
    }

    @Test
    fun `drops session where end is before start`() {
        val degenerate = session(
            start = Instant.parse("2026-05-15T12:30:00Z"),
            end = Instant.parse("2026-05-15T12:00:00Z")
        )

        assertNull(PlaySessionMapper.toRomMEntry(degenerate, rommId = 99L))
    }

    @Test
    fun `drops session that lives inside one second`() {
        val start = Instant.parse("2026-05-15T12:00:00.100Z")
        val end = Instant.parse("2026-05-15T12:00:00.900Z")

        assertNull(PlaySessionMapper.toRomMEntry(session(start = start, end = end), rommId = 99L))
    }

    @Test
    fun `keeps session that crosses a second boundary`() {
        val start = Instant.parse("2026-05-15T12:00:00.900Z")
        val end = Instant.parse("2026-05-15T12:00:01.100Z")

        assertNotNull(PlaySessionMapper.toRomMEntry(session(start = start, end = end), rommId = 99L))
    }
}
