package com.nendo.argosy.domain.music

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MusicStatsCalendarTest {

    @Test
    fun `year month of a date is yyyymm`() {
        assertEquals(202611, MusicStatsCalendar.yearMonthOf(LocalDate.of(2026, 11, 25)))
        assertEquals(202601, MusicStatsCalendar.yearMonthOf(YearMonth.of(2026, 1)))
    }

    @Test
    fun `stepping back from january lands on december of the year before`() {
        assertEquals(202512, MusicStatsCalendar.step(202601, -1))
    }

    @Test
    fun `stepping forward from december lands on january of the next year`() {
        assertEquals(202701, MusicStatsCalendar.step(202612, 1))
    }

    @Test
    fun `stepping by zero and by several months`() {
        assertEquals(202611, MusicStatsCalendar.step(202611, 0))
        assertEquals(202511, MusicStatsCalendar.step(202611, -12))
        assertEquals(202702, MusicStatsCalendar.step(202611, 3))
    }

    @Test
    fun `day range covers the whole month inclusive`() {
        assertEquals(20261101..20261130, MusicStatsCalendar.dayRange(202611))
        assertEquals(20261201..20261231, MusicStatsCalendar.dayRange(202612))
    }

    @Test
    fun `day range follows february length in leap and common years`() {
        assertEquals(20240201..20240229, MusicStatsCalendar.dayRange(202402))
        assertEquals(20250201..20250228, MusicStatsCalendar.dayRange(202502))
    }

    @Test
    fun `month day of a date is mmdd`() {
        assertEquals(1125, MusicStatsCalendar.monthDayOf(LocalDate.of(2026, 11, 25)))
        assertEquals(101, MusicStatsCalendar.monthDayOf(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `year month round trips through YearMonth`() {
        assertEquals(YearMonth.of(2026, 3), MusicStatsCalendar.toYearMonth(202603))
        assertEquals(2026, MusicStatsCalendar.yearOf(202603))
        assertEquals(3, MusicStatsCalendar.monthOf(202603))
    }
}
