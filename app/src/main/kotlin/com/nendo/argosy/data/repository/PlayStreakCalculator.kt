package com.nendo.argosy.data.repository

import com.nendo.argosy.data.model.PlayStreaks
import java.time.LocalDate

object PlayStreakCalculator {
    /**
     * Streaks over the calendar days in [playDays]. The current streak is the run of consecutive
     * days ending today, or ending yesterday when today has no play yet, so a streak is not
     * reported broken before the day is over. The longest streak is the longest run anywhere.
     */
    fun compute(playDays: Collection<LocalDate>, today: LocalDate): PlayStreaks {
        if (playDays.isEmpty()) return PlayStreaks(current = 0, longest = 0)
        val days = playDays.toSortedSet()
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            if (run > longest) longest = run
            previous = day
        }
        val anchor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return PlayStreaks(current = 0, longest = longest)
        }
        var current = 0
        var cursor = anchor
        while (cursor in days) {
            current++
            cursor = cursor.minusDays(1)
        }
        return PlayStreaks(current = current, longest = longest)
    }

    fun median(sortedValues: List<Long>): Long {
        if (sortedValues.isEmpty()) return 0L
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 1) {
            sortedValues[middle]
        } else {
            (sortedValues[middle - 1] + sortedValues[middle]) / 2
        }
    }

    /**
     * Mean of [values] with the longest and shortest [trimRatio] of them dropped, so one marathon
     * or one mis-tracked stub does not set the figure. Falls back to the plain mean below four
     * values, where trimming would discard most of the sample.
     */
    fun trimmedMean(values: List<Long>, trimRatio: Float): Long {
        if (values.isEmpty()) return 0L
        if (values.size < MIN_TRIM_SAMPLE) return values.sum() / values.size
        val sorted = values.sorted()
        val drop = (sorted.size * trimRatio).toInt().coerceAtMost((sorted.size - 1) / 2)
        val kept = sorted.subList(drop, sorted.size - drop)
        if (kept.isEmpty()) return 0L
        return kept.sum() / kept.size
    }

    private const val MIN_TRIM_SAMPLE = 4
}
