package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class PlayWeekHourMatrixTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(dateTime: String): Instant = LocalDateTime.parse(dateTime).atZone(zone).toInstant()

    private fun session(start: String, spanMinutes: Long, activeMinutes: Long = spanMinutes) = PlaySessionEntity(
        userId = null,
        gameId = 1L,
        igdbId = null,
        gameTitle = "Game",
        platformSlug = "snes",
        startTime = at(start),
        endTime = at(start).plus(Duration.ofMinutes(spanMinutes)),
        deviceId = "device",
        deviceManufacturer = "Acme",
        deviceModel = "One",
        activePlayMs = Duration.ofMinutes(activeMinutes).toMillis()
    )

    private fun minutes(n: Long): Long = Duration.ofMinutes(n).toMillis()

    @Test
    fun `an empty range is a zero matrix of seven rows by twenty four hours`() {
        val matrix = PlayWeekHourMatrix.build(emptyList(), zone)
        assertEquals(PlayWeekHourMatrix.DAYS, matrix.size)
        matrix.forEach { row ->
            assertEquals(PlayWeekHourMatrix.HOURS, row.size)
            assertEquals(0L, row.sum())
        }
    }

    @Test
    fun `a session inside one hour lands whole in that cell`() {
        val matrix = PlayWeekHourMatrix.build(listOf(session("2026-09-07T10:15", spanMinutes = 30)), zone)
        assertEquals(minutes(30), matrix[0][10])
        assertEquals(minutes(30), matrix.sumOf { it.sum() })
    }

    @Test
    fun `a session over an hour boundary is split in proportion`() {
        val matrix = PlayWeekHourMatrix.build(listOf(session("2026-09-07T23:30", spanMinutes = 60)), zone)
        assertEquals(minutes(30), matrix[0][23])
        assertEquals(minutes(30), matrix[1][0])
    }

    @Test
    fun `active time is spread by the same proportion as the span`() {
        val matrix = PlayWeekHourMatrix.build(
            listOf(session("2026-09-12T21:00", spanMinutes = 90, activeMinutes = 45)),
            zone
        )
        assertEquals(minutes(30), matrix[5][21])
        assertEquals(minutes(15), matrix[5][22])
    }

    @Test
    fun `every millisecond of active time is preserved`() {
        val sessions = listOf(
            session("2026-09-07T09:07", spanMinutes = 187, activeMinutes = 113),
            session("2026-09-08T22:41", spanMinutes = 95, activeMinutes = 61),
            session("2026-09-13T00:00", spanMinutes = 1, activeMinutes = 1)
        )
        val matrix = PlayWeekHourMatrix.build(sessions, zone)
        assertEquals(sessions.sumOf { it.activePlayMs }, matrix.sumOf { it.sum() })
    }

    @Test
    fun `a session with no span still counts at its starting hour`() {
        val matrix = PlayWeekHourMatrix.build(listOf(session("2026-09-09T14:00", spanMinutes = 0, activeMinutes = 5)), zone)
        assertEquals(minutes(5), matrix[2][14])
    }
}
