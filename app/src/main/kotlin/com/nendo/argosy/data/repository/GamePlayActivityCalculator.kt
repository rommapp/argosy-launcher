package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import java.time.Instant
import java.time.ZoneId

object GamePlayActivityCalculator {
    const val WEEKS = 14

    fun windowStart(now: Instant, zone: ZoneId, weeks: Int = WEEKS): Instant =
        now.atZone(zone).minusWeeks(weeks.toLong()).toInstant()

    /**
     * Active time per week, oldest first, the last week ending at [now]. Week boundaries step back
     * from [now] in local wall-clock time, and a session belongs to the week it started in; a start
     * on a boundary opens the later week. Weeks totalling under [minWeekMs] read as zero.
     */
    fun weekActiveMs(
        sessions: List<PlaySessionEntity>,
        now: Instant,
        zone: ZoneId,
        weeks: Int = WEEKS,
        minWeekMs: Long = 0L
    ): List<Long> {
        val bounds = weekBounds(now, zone, weeks)
        val totals = LongArray(weeks)
        sessions.forEach { session ->
            val index = weekIndex(bounds, session.startTime)
            if (index in 0 until weeks) totals[index] += session.activePlayMs
        }
        return totals.map { if (it >= minWeekMs) it else 0L }
    }

    /**
     * The latest session that counts as play, by end time: one of [windowSessions] in a week that
     * [weekMs] keeps, else [beforeWindow]. A session in a week [weekActiveMs] reads as zero is left
     * out, so the last-played time never names a session the weekly bars do not show.
     */
    fun latestCountedSession(
        windowSessions: List<PlaySessionEntity>,
        weekMs: List<Long>,
        beforeWindow: PlaySessionEntity?,
        now: Instant,
        zone: ZoneId
    ): PlaySessionEntity? {
        val bounds = weekBounds(now, zone, weekMs.size)
        return windowSessions
            .filter { session -> (weekMs.getOrNull(weekIndex(bounds, session.startTime)) ?: 0L) > 0L }
            .maxByOrNull { it.endTime }
            ?: beforeWindow
    }

    private fun weekBounds(now: Instant, zone: ZoneId, weeks: Int): List<Instant> {
        val end = now.atZone(zone)
        return (weeks downTo 0).map { end.minusWeeks(it.toLong()).toInstant() }
    }

    private fun weekIndex(bounds: List<Instant>, start: Instant): Int =
        bounds.indexOfLast { !start.isBefore(it) }

    /**
     * Each week as a share of the busiest one; all zeros when nothing was played.
     */
    fun normalized(weekMs: List<Long>): List<Float> {
        val max = weekMs.maxOrNull()?.takeIf { it > 0L } ?: return weekMs.map { 0f }
        return weekMs.map { it.toFloat() / max }
    }

    /**
     * The device name a session was recorded on, or null when it carries none. A session pulled
     * from a server device with no name stores the device id as its model, which names nothing.
     */
    fun deviceName(session: PlaySessionEntity): String? =
        session.deviceModel.trim()
            .takeIf { it.isNotEmpty() && it != session.deviceId }
            ?.split(WHITESPACE)
            ?.fold(emptyList<String>()) { words, word ->
                if (words.lastOrNull().equals(word, ignoreCase = true)) words else words + word
            }
            ?.joinToString(" ")

    private val WHITESPACE = Regex("\\s+")
}
