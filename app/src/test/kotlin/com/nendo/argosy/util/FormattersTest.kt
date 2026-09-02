package com.nendo.argosy.util

import android.content.Context
import android.content.res.Resources
import com.nendo.argosy.R
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Pins the relative-time thresholds and the number formatting policy.
 *
 * The strings themselves live in resources, which a JVM unit test cannot resolve, so the fake
 * Context renders each call as its resource id and arguments. That is the invariant worth
 * defending: which branch an age selects and what it counts. The English wording is free to
 * change and a translation is free to differ; the boundaries are not.
 *
 * The absolute branch is supplied as a parameter rather than called for real, because the
 * month-and-day rendering goes through an Android formatter that is not present off-device.
 */
class FormattersTest {

    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")
    private val absolute: (Instant) -> String = { "ABSOLUTE" }

    private lateinit var context: Context

    private fun res(id: Int, vararg args: Any): String =
        if (args.isEmpty()) "res:$id" else "res:$id:" + args.joinToString(",")

    private fun plural(id: Int, count: Int, vararg args: Any): String =
        "plural:$id:$count:" + args.joinToString(",")

    @Before
    fun setup() {
        context = mockk()
        val resources: Resources = mockk()
        every { context.resources } returns resources
        every { context.getString(any<Int>()) } answers { "res:${firstArg<Int>()}" }
        every { context.getString(any<Int>(), *anyVararg()) } answers {
            val rest = call.invocation.args.drop(1).flatten()
            if (rest.isEmpty()) "res:${firstArg<Int>()}" else "res:${firstArg<Int>()}:" + rest.joinToString(",")
        }
        every { resources.getQuantityString(any<Int>(), any<Int>(), *anyVararg()) } answers {
            val rest = call.invocation.args.drop(2).flatten()
            "plural:${firstArg<Int>()}:${secondArg<Int>()}:" + rest.joinToString(",")
        }
    }

    private fun List<Any?>.flatten(): List<Any?> =
        flatMap { if (it is Array<*>) it.toList() else listOf(it) }

    private fun ago(days: Long = 0, hours: Long = 0, minutes: Long = 0): Instant =
        now.minusSeconds(days * 86_400 + hours * 3_600 + minutes * 60)

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `relative time stays relative inside a month`() {
        assertEquals(
            res(R.string.util_formatters_relative_today),
            formatRelativeTime(context, ago(hours = 3), now, absolute)
        )
        assertEquals(
            res(R.string.util_formatters_relative_yesterday),
            formatRelativeTime(context, ago(days = 1), now, absolute)
        )
        assertEquals(
            plural(R.plurals.util_formatters_relative_days_ago, 5, 5),
            formatRelativeTime(context, ago(days = 5), now, absolute)
        )
        assertEquals(
            plural(R.plurals.util_formatters_relative_weeks_ago, 2, 2),
            formatRelativeTime(context, ago(days = 20), now, absolute)
        )
    }

    @Test
    fun `relative time becomes a date past a month`() {
        assertEquals("ABSOLUTE", formatRelativeTime(context, ago(days = 30), now, absolute))
        assertEquals("ABSOLUTE", formatRelativeTime(context, ago(days = 400), now, absolute))
    }

    @Test
    fun `short relative time crosses at a month`() {
        assertEquals(
            res(R.string.util_formatters_relative_short_just_now),
            formatRelativeTimeShort(context, ago(), now, absolute)
        )
        assertEquals(
            plural(R.plurals.util_formatters_relative_short_minutes_ago, 45, 45),
            formatRelativeTimeShort(context, ago(minutes = 45), now, absolute)
        )
        assertEquals(
            plural(R.plurals.util_formatters_relative_short_hours_ago, 6, 6),
            formatRelativeTimeShort(context, ago(hours = 6), now, absolute)
        )
        assertEquals(
            plural(R.plurals.util_formatters_relative_short_days_ago, 29, 29),
            formatRelativeTimeShort(context, ago(days = 29), now, absolute)
        )
        assertEquals("ABSOLUTE", formatRelativeTimeShort(context, ago(days = 31), now, absolute))
    }

    @Test
    fun `verbose relative time crosses at a month`() {
        assertEquals(
            res(R.string.util_formatters_relative_verbose_just_now),
            formatRelativeTimeVerbose(context, ago(), now, absolute)
        )
        assertEquals(
            plural(R.plurals.util_formatters_relative_verbose_days_ago, 3, 3),
            formatRelativeTimeVerbose(context, ago(days = 3), now, absolute)
        )
        assertEquals(
            plural(R.plurals.util_formatters_relative_verbose_weeks_ago, 4, 4),
            formatRelativeTimeVerbose(context, ago(days = 29), now, absolute)
        )
        assertEquals("ABSOLUTE", formatRelativeTimeVerbose(context, ago(days = 45), now, absolute))
    }

    @Test
    fun `string overload parses and crosses at a month`() {
        assertEquals(
            res(R.string.util_formatters_relative_str_now),
            formatRelativeTime(context, "2026-06-15T12:00:00Z", now, absolute)
        )
        assertEquals(
            plural(R.plurals.util_formatters_relative_str_hours_ago, 2, 2),
            formatRelativeTime(context, "2026-06-15T10:00:00Z", now, absolute)
        )
        assertEquals(
            "ABSOLUTE",
            formatRelativeTime(context, "2026-01-01T10:00:00Z", now, absolute)
        )
        assertEquals("", formatRelativeTime(context, "not a timestamp", now, absolute))
    }

    @Test
    fun `displayed sizes use the readers decimal separator`() {
        assertEquals("1.5 KB", withLocale(Locale.US) { formatBytes(1536) })
        assertEquals("1,5 KB", withLocale(Locale.GERMANY) { formatBytes(1536) })
        assertEquals("2.0 MB", withLocale(Locale.US) { formatBytes(2L * 1024 * 1024) })
        assertEquals("0 B", withLocale(Locale.GERMANY) { formatBytes(0) })
    }

    @Test
    fun `raw bytes render whole, since a fractional byte means nothing`() {
        assertEquals("512 B", withLocale(Locale.US) { formatBytes(512) })
        assertEquals("512 B", withLocale(Locale.GERMANY) { formatBytes(512) })
    }

    @Test
    fun `logged sizes stay pinned regardless of locale`() {
        val us = withLocale(Locale.US) { formatBytesStable(1536) }
        val de = withLocale(Locale.GERMANY) { formatBytesStable(1536) }
        assertEquals(us, de)
        assertEquals("1.5 KB", us)
    }

    @Test
    fun `play time picks the right unit branch`() {
        assertEquals(
            res(R.string.util_formatters_playtime_minutes, 45),
            formatPlayTime(context, 45)
        )
        assertEquals(
            res(R.string.util_formatters_playtime_hours_minutes, 2, 30),
            formatPlayTime(context, 150)
        )
        assertEquals(
            res(R.string.util_formatters_playtime_hours, 25),
            formatPlayTime(context, 1500)
        )
    }

    @Test
    fun `missing time to beat estimates are hidden`() {
        assertNull(formatTimeToBeat(context, null))
        assertNull(formatTimeToBeat(context, 0))
        assertNull(formatTimeToBeat(context, -1))
    }

    @Test
    fun `short time to beat estimates retain minute precision`() {
        assertEquals(res(R.string.util_formatters_time_to_beat_minutes, 5), formatTimeToBeat(context, 300))
        assertEquals(res(R.string.util_formatters_time_to_beat_minutes, 30), formatTimeToBeat(context, 900))
        assertEquals(res(R.string.util_formatters_time_to_beat_minutes, 1), formatTimeToBeat(context, 30))
    }

    @Test
    fun `time to beat estimates round to half hours`() {
        assertEquals(res(R.string.util_formatters_time_to_beat_half_hours, 9), formatTimeToBeat(context, 33_739))
        assertEquals(res(R.string.util_formatters_time_to_beat_half_hours, 13), formatTimeToBeat(context, 49_369))
        assertEquals(res(R.string.util_formatters_time_to_beat_hours, 27), formatTimeToBeat(context, 97_200))
        assertEquals(res(R.string.util_formatters_time_to_beat_hours, 1), formatTimeToBeat(context, 3_600))
    }
}
