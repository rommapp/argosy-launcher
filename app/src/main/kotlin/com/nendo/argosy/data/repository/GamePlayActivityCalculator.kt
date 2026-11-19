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
        val end = now.atZone(zone)
        val bounds = (weeks downTo 0).map { end.minusWeeks(it.toLong()).toInstant() }
        val totals = LongArray(weeks)
        sessions.forEach { session ->
            val index = bounds.indexOfLast { !session.startTime.isBefore(it) }
            if (index in 0 until weeks) totals[index] += session.activePlayMs
        }
        return totals.map { if (it >= minWeekMs) it else 0L }
    }

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
