package com.nendo.argosy.domain.music

import java.time.LocalDate
import java.time.YearMonth

/**
 * Conversions between calendar values and the integer forms the listen history queries take:
 * yyyymm for a month, yyyymmdd for a day, mmdd for a day of any year.
 */
object MusicStatsCalendar {
    private const val MONTH_FACTOR = 100

    fun yearMonthOf(month: YearMonth): Int = month.year * MONTH_FACTOR + month.monthValue

    fun yearMonthOf(date: LocalDate): Int = yearMonthOf(YearMonth.from(date))

    fun toYearMonth(yearMonth: Int): YearMonth =
        YearMonth.of(yearMonth / MONTH_FACTOR, yearMonth % MONTH_FACTOR)

    fun yearOf(yearMonth: Int): Int = yearMonth / MONTH_FACTOR

    fun monthOf(yearMonth: Int): Int = yearMonth % MONTH_FACTOR

    fun step(yearMonth: Int, delta: Int): Int =
        yearMonthOf(toYearMonth(yearMonth).plusMonths(delta.toLong()))

    /**
     * The first and last yyyymmdd day of [yearMonth], both inclusive.
     */
    fun dayRange(yearMonth: Int): IntRange {
        val base = yearMonth * MONTH_FACTOR
        return (base + 1)..(base + toYearMonth(yearMonth).lengthOfMonth())
    }

    fun monthDayOf(date: LocalDate): Int = date.monthValue * MONTH_FACTOR + date.dayOfMonth
}
