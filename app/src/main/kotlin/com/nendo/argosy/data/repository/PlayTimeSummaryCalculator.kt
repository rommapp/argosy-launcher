package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.model.PlayTimeSummary
import java.time.DayOfWeek

object PlayTimeSummaryCalculator {

    /**
     * The one-sentence reading of a range: how much was played, on how many platforms, and which
     * platform, weekday, hour and device took the most of it. Weekday and hour are the largest
     * row and column of [weekHourMs], the same matrix the waveform draws, so the sentence and the
     * figure never disagree. Null when the range holds no active time.
     */
    fun compute(sessions: List<PlaySessionEntity>, weekHourMs: List<List<Long>>): PlayTimeSummary? {
        val active = sessions.filter { it.activePlayMs > 0L }
        val totalActiveMs = active.sumOf { it.activePlayMs }
        if (totalActiveMs <= 0L || weekHourMs.size != PlayWeekHourMatrix.DAYS) return null
        val platformTotals = totalsBy(active) { it.platformSlug }
        val deviceTotals = totalsBy(active) { it.deviceId }
        val weekdayTotals = weekHourMs.map { row -> row.sum() }
        val hourTotals = (0 until PlayWeekHourMatrix.HOURS).map { hour -> weekHourMs.sumOf { row -> row.getOrElse(hour) { 0L } } }
        return PlayTimeSummary(
            totalActiveMs = totalActiveMs,
            platformCount = platformTotals.size,
            topPlatformSlug = dominant(platformTotals),
            weekday = DayOfWeek.of(indexOfMax(weekdayTotals) + 1),
            hour = indexOfMax(hourTotals),
            topDeviceId = dominant(deviceTotals)
        )
    }

    private fun totalsBy(sessions: List<PlaySessionEntity>, key: (PlaySessionEntity) -> String): Map<String, Long> =
        sessions.groupBy(key).mapValues { (_, group) -> group.sumOf { it.activePlayMs } }

    private fun dominant(totals: Map<String, Long>): String =
        totals.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key }).first().key

    private fun indexOfMax(values: List<Long>): Int {
        var best = 0
        values.forEachIndexed { index, value -> if (value > values[best]) best = index }
        return best
    }
}
