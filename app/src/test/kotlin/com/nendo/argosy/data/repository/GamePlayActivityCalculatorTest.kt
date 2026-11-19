package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class GamePlayActivityCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = at("2026-11-19T12:00")

    private fun at(dateTime: String, inZone: ZoneId = zone): Instant =
        LocalDateTime.parse(dateTime).atZone(inZone).toInstant()

    private fun session(
        start: Instant,
        activeMinutes: Long = 30,
        deviceId: String = "device",
        deviceModel: String = "Odin3"
    ) = PlaySessionEntity(
        userId = null,
        gameId = 1L,
        igdbId = null,
        gameTitle = "Game",
        platformSlug = "dc",
        startTime = start,
        endTime = start.plus(Duration.ofMinutes(activeMinutes)),
        deviceId = deviceId,
        deviceManufacturer = "Ayn",
        deviceModel = deviceModel,
        activePlayMs = Duration.ofMinutes(activeMinutes).toMillis()
    )

    private fun minutes(n: Long): Long = Duration.ofMinutes(n).toMillis()

    private fun weeksBefore(n: Long): Instant = now.atZone(zone).minusWeeks(n).toInstant()

    @Test
    fun `no sessions give fourteen empty weeks`() {
        val weeks = GamePlayActivityCalculator.weekActiveMs(emptyList(), now, zone)
        assertEquals(GamePlayActivityCalculator.WEEKS, weeks.size)
        assertEquals(14, weeks.size)
        assertEquals(0L, weeks.sum())
    }

    @Test
    fun `this week is the last bucket and the oldest week is the first`() {
        val weeks = GamePlayActivityCalculator.weekActiveMs(
            listOf(
                session(now.minus(Duration.ofDays(1)), activeMinutes = 40),
                session(weeksBefore(14).plus(Duration.ofDays(1)), activeMinutes = 20)
            ),
            now,
            zone
        )
        assertEquals(minutes(40), weeks[13])
        assertEquals(minutes(20), weeks[0])
        assertEquals(minutes(60), weeks.sum())
    }

    @Test
    fun `a session starting on a week boundary opens the later week`() {
        val weeks = GamePlayActivityCalculator.weekActiveMs(
            listOf(
                session(weeksBefore(1), activeMinutes = 10),
                session(weeksBefore(1).minusMillis(1), activeMinutes = 25)
            ),
            now,
            zone
        )
        assertEquals(minutes(10), weeks[13])
        assertEquals(minutes(25), weeks[12])
    }

    @Test
    fun `sessions before the window or from now on are ignored`() {
        val windowStart = GamePlayActivityCalculator.windowStart(now, zone)
        val weeks = GamePlayActivityCalculator.weekActiveMs(
            listOf(
                session(windowStart.minusMillis(1), activeMinutes = 90),
                session(windowStart, activeMinutes = 15),
                session(now, activeMinutes = 45)
            ),
            now,
            zone
        )
        assertEquals(minutes(15), weeks[0])
        assertEquals(minutes(15), weeks.sum())
    }

    @Test
    fun `week boundaries follow local wall-clock time across a clock change`() {
        val newYork = ZoneId.of("America/New_York")
        val localNow = at("2026-11-03T12:00", newYork)
        val afterLocalBoundary = at("2026-10-27T12:30", newYork)
        val weeks = GamePlayActivityCalculator.weekActiveMs(
            listOf(session(afterLocalBoundary, activeMinutes = 30)),
            localNow,
            newYork
        )
        assertEquals(minutes(30), weeks[13])
        assertEquals(0L, weeks[12])
    }

    @Test
    fun `a week under the minimum reads as zero`() {
        val weeks = GamePlayActivityCalculator.weekActiveMs(
            listOf(
                session(now.minus(Duration.ofDays(1)), activeMinutes = 0).copy(activePlayMs = 30_000L),
                session(weeksBefore(2).plus(Duration.ofDays(1)), activeMinutes = 5)
            ),
            now,
            zone,
            minWeekMs = minutes(1)
        )
        assertEquals(0L, weeks[13])
        assertEquals(minutes(5), weeks[12])
    }

    @Test
    fun `weeks are scaled against the busiest week`() {
        val shares = GamePlayActivityCalculator.normalized(listOf(0L, minutes(30), minutes(60), minutes(15)))
        assertEquals(listOf(0f, 0.5f, 1f, 0.25f), shares)
    }

    @Test
    fun `no play scales to all zeros`() {
        val shares = GamePlayActivityCalculator.normalized(List(14) { 0L })
        assertEquals(List(14) { 0f }, shares)
    }

    @Test
    fun `the device name is the session's model`() {
        assertEquals("Odin3", GamePlayActivityCalculator.deviceName(session(now, deviceModel = " Odin3 ")))
    }

    @Test
    fun `a blank model has no device name`() {
        assertNull(GamePlayActivityCalculator.deviceName(session(now, deviceModel = "  ")))
    }

    @Test
    fun `a maker name repeated at the front of the model is shown once`() {
        assertEquals("AYN Thor", GamePlayActivityCalculator.deviceName(session(now, deviceModel = "AYN AYN Thor")))
        assertEquals("ayn Odin3", GamePlayActivityCalculator.deviceName(session(now, deviceModel = "ayn Odin3")))
    }

    @Test
    fun `a model that is only the device id has no device name`() {
        assertNull(
            GamePlayActivityCalculator.deviceName(session(now, deviceId = "a1b2c3", deviceModel = "a1b2c3"))
        )
    }
}
