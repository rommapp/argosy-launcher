package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class PlayTimeSummaryCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(dateTime: String): Instant = LocalDateTime.parse(dateTime).atZone(zone).toInstant()

    private fun session(
        start: String,
        minutes: Long,
        platform: String = "snes",
        device: String = "device-a",
        gameId: Long = 1L
    ) = PlaySessionEntity(
        userId = null,
        gameId = gameId,
        igdbId = null,
        gameTitle = "Game $gameId",
        platformSlug = platform,
        startTime = at(start),
        endTime = at(start).plus(Duration.ofMinutes(minutes)),
        deviceId = device,
        deviceManufacturer = "Acme",
        deviceModel = device,
        activePlayMs = Duration.ofMinutes(minutes).toMillis()
    )

    private fun compute(sessions: List<PlaySessionEntity>) =
        PlayTimeSummaryCalculator.compute(sessions, PlayWeekHourMatrix.build(sessions, zone))

    @Test
    fun `an empty range has no summary`() {
        assertNull(compute(emptyList()))
    }

    @Test
    fun `a range with only idle sessions has no summary`() {
        assertNull(compute(listOf(session("2026-09-07T10:00", minutes = 0))))
    }

    @Test
    fun `dominants follow active time not session count`() {
        val sessions = listOf(
            session("2026-09-07T10:00", minutes = 10, platform = "gba", device = "device-b"),
            session("2026-09-07T11:00", minutes = 10, platform = "gba", device = "device-b"),
            session("2026-09-07T12:00", minutes = 10, platform = "gba", device = "device-b"),
            session("2026-09-12T21:00", minutes = 90, platform = "n64", device = "device-a")
        )
        val summary = compute(sessions)!!
        assertEquals(Duration.ofMinutes(120).toMillis(), summary.totalActiveMs)
        assertEquals(2, summary.platformCount)
        assertEquals("n64", summary.topPlatformSlug)
        assertEquals("device-a", summary.topDeviceId)
        assertEquals(DayOfWeek.SATURDAY, summary.weekday)
        assertEquals(21, summary.hour)
    }

    @Test
    fun `busiest hour is read across the whole week not the busiest day`() {
        val sessions = listOf(
            session("2026-09-07T09:00", minutes = 50),
            session("2026-09-08T20:00", minutes = 30),
            session("2026-09-09T20:00", minutes = 30)
        )
        val summary = compute(sessions)!!
        assertEquals(DayOfWeek.MONDAY, summary.weekday)
        assertEquals(20, summary.hour)
    }

    @Test
    fun `idle sessions do not count toward the platform total`() {
        val sessions = listOf(
            session("2026-09-07T10:00", minutes = 30, platform = "snes"),
            session("2026-09-07T12:00", minutes = 0, platform = "gba")
        )
        assertEquals(1, compute(sessions)!!.platformCount)
    }

    @Test
    fun `ties break on the smaller key so the reading is stable`() {
        val sessions = listOf(
            session("2026-09-07T10:00", minutes = 30, platform = "snes", device = "device-b"),
            session("2026-09-07T12:00", minutes = 30, platform = "gba", device = "device-a")
        )
        val summary = compute(sessions)!!
        assertEquals("gba", summary.topPlatformSlug)
        assertEquals("device-a", summary.topDeviceId)
    }
}
