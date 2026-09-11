package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object PlayWeekHourMatrix {
    const val DAYS = 7
    const val HOURS = 24

    /**
     * Active time per weekday (Monday first) and hour of day. A session's active time is spread
     * across the wall-clock hours it covered in proportion to their length, so a sitting from
     * 23:30 to 00:30 lands half in each hour instead of wholly on the hour it started. The sum of
     * every cell equals the sum of every session's active time.
     */
    fun build(sessions: List<PlaySessionEntity>, zone: ZoneId): List<List<Long>> {
        val cells = Array(DAYS) { LongArray(HOURS) }
        sessions.forEach { spread(it.startTime, it.endTime, it.activePlayMs, zone, cells) }
        return cells.map { it.toList() }
    }

    fun empty(): List<List<Long>> = List(DAYS) { List(HOURS) { 0L } }

    private fun spread(
        start: Instant,
        end: Instant,
        activeMs: Long,
        zone: ZoneId,
        cells: Array<LongArray>
    ) {
        if (activeMs <= 0L) return
        val startAt = start.atZone(zone)
        var remainingSpan = end.toEpochMilli() - start.toEpochMilli()
        if (remainingSpan <= 0L) {
            cells[weekdayIndex(startAt)][startAt.hour] += activeMs
            return
        }
        var cursor = startAt
        var remaining = activeMs
        while (remainingSpan > 0L && remaining > 0L) {
            val nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val slice = minOf(remainingSpan, Duration.between(cursor, nextHour).toMillis().coerceAtLeast(1L))
            val share = if (slice >= remainingSpan) remaining else remaining * slice / remainingSpan
            cells[weekdayIndex(cursor)][cursor.hour] += share
            remaining -= share
            remainingSpan -= slice
            cursor = nextHour
        }
    }

    private fun weekdayIndex(at: ZonedDateTime): Int = at.dayOfWeek.value - 1
}
