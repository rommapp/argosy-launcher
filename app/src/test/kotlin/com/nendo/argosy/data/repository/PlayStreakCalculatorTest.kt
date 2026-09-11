package com.nendo.argosy.data.repository

import com.nendo.argosy.data.model.PlayStreaks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlayStreakCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 11)

    private fun days(vararg offsets: Long): List<LocalDate> = offsets.map { today.minusDays(it) }

    @Test
    fun `no play days gives no streaks`() {
        assertEquals(PlayStreaks(0, 0), PlayStreakCalculator.compute(emptyList(), today))
    }

    @Test
    fun `consecutive days ending today count as the current streak`() {
        assertEquals(PlayStreaks(3, 3), PlayStreakCalculator.compute(days(0, 1, 2), today))
    }

    @Test
    fun `a streak ending yesterday is still current`() {
        assertEquals(PlayStreaks(2, 2), PlayStreakCalculator.compute(days(1, 2), today))
    }

    @Test
    fun `a streak that ended two days ago is over`() {
        assertEquals(PlayStreaks(0, 4), PlayStreakCalculator.compute(days(2, 3, 4, 5), today))
    }

    @Test
    fun `the longest streak can be older than the current one`() {
        val result = PlayStreakCalculator.compute(days(0, 1, 10, 11, 12, 13, 14), today)
        assertEquals(PlayStreaks(current = 2, longest = 5), result)
    }

    @Test
    fun `the current streak is also the longest when it is the longest run`() {
        val result = PlayStreakCalculator.compute(days(0, 1, 2, 3, 20, 21), today)
        assertEquals(PlayStreaks(current = 4, longest = 4), result)
    }

    @Test
    fun `duplicate days do not inflate a streak`() {
        val result = PlayStreakCalculator.compute(days(0, 0, 1, 1, 1), today)
        assertEquals(PlayStreaks(2, 2), result)
    }

    @Test
    fun `a single day today is a streak of one`() {
        assertEquals(PlayStreaks(1, 1), PlayStreakCalculator.compute(days(0), today))
    }

    @Test
    fun `median of an odd count is the middle value`() {
        assertEquals(30L, PlayStreakCalculator.median(listOf(10L, 30L, 50L)))
    }

    @Test
    fun `median of an even count averages the two middle values`() {
        assertEquals(25L, PlayStreakCalculator.median(listOf(10L, 20L, 30L, 40L)))
    }

    @Test
    fun `median of nothing is zero`() {
        assertEquals(0L, PlayStreakCalculator.median(emptyList()))
    }

    @Test
    fun `trimmed mean drops the extremes at both ends`() {
        val values = listOf(1L, 40L, 50L, 60L, 50L, 40L, 60L, 50L, 40L, 9000L)
        assertEquals(48L, PlayStreakCalculator.trimmedMean(values, 0.1f))
    }

    @Test
    fun `trimmed mean without trimming matches the plain mean`() {
        assertEquals(30L, PlayStreakCalculator.trimmedMean(listOf(10L, 20L, 30L, 40L, 50L), 0f))
    }

    @Test
    fun `a short sample is averaged without trimming`() {
        assertEquals(505L, PlayStreakCalculator.trimmedMean(listOf(10L, 1000L), 0.1f))
    }

    @Test
    fun `trimmed mean of nothing is zero`() {
        assertEquals(0L, PlayStreakCalculator.trimmedMean(emptyList(), 0.1f))
    }
}
