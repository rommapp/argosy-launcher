package com.nendo.argosy.util

import android.content.Context
import com.nendo.argosy.R
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val DAYS_PER_MONTH = 30L

/**
 * Month and day in the reader's field order, so a locale that leads with the day gets one.
 * Injected as a parameter by the relative-time formatters so their thresholds stay testable
 * off-device.
 */
fun formatMonthDay(instant: Instant): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd")
    return DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

fun formatPlayTime(context: Context, minutes: Int): String = when {
    minutes < MINUTES_PER_HOUR -> context.getString(R.string.util_formatters_playtime_minutes, minutes)
    minutes < MINUTES_PER_DAY -> context.getString(
        R.string.util_formatters_playtime_hours_minutes,
        minutes / MINUTES_PER_HOUR,
        minutes % MINUTES_PER_HOUR
    )
    else -> context.getString(R.string.util_formatters_playtime_hours, minutes / MINUTES_PER_HOUR)
}

fun formatRelativeTime(
    context: Context,
    instant: Instant?,
    now: Instant = Instant.now(),
    absolute: (Instant) -> String = ::formatMonthDay
): String {
    if (instant == null) return ""
    val days = Duration.between(instant, now).toDays()
    val resources = context.resources
    return when {
        days == 0L -> context.getString(R.string.util_formatters_relative_today)
        days == 1L -> context.getString(R.string.util_formatters_relative_yesterday)
        days < 7 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_days_ago,
            days.toInt(),
            days.toInt()
        )
        days < DAYS_PER_MONTH -> resources.getQuantityString(
            R.plurals.util_formatters_relative_weeks_ago,
            (days / 7).toInt(),
            (days / 7).toInt()
        )
        else -> absolute(instant)
    }
}

fun formatRelativeTime(
    context: Context,
    timestamp: String,
    now: Instant = Instant.now(),
    absolute: (Instant) -> String = ::formatMonthDay
): String = try {
    val instant = parseRelativeTimestamp(timestamp)
    val duration = Duration.between(instant, now)
    val resources = context.resources
    when {
        duration.isNegative -> context.getString(R.string.util_formatters_relative_str_now)
        duration.toMinutes() < 1 -> context.getString(R.string.util_formatters_relative_str_now)
        duration.toMinutes() < 60 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_str_minutes_ago,
            duration.toMinutes().toInt(),
            duration.toMinutes().toInt()
        )
        duration.toHours() < 24 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_str_hours_ago,
            duration.toHours().toInt(),
            duration.toHours().toInt()
        )
        duration.toDays() < 7 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_str_days_ago,
            duration.toDays().toInt(),
            duration.toDays().toInt()
        )
        duration.toDays() < DAYS_PER_MONTH -> resources.getQuantityString(
            R.plurals.util_formatters_relative_str_weeks_ago,
            (duration.toDays() / 7).toInt(),
            (duration.toDays() / 7).toInt()
        )
        else -> absolute(instant)
    }
} catch (_: Exception) {
    ""
}

fun formatRelativeTimeVerbose(
    context: Context,
    instant: Instant,
    now: Instant = Instant.now(),
    absolute: (Instant) -> String = ::formatMonthDay
): String {
    val duration = Duration.between(instant, now)
    val resources = context.resources
    return when {
        duration.toMinutes() < 1 -> context.getString(R.string.util_formatters_relative_verbose_just_now)
        duration.toMinutes() < 60 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_verbose_minutes_ago,
            duration.toMinutes().toInt(),
            duration.toMinutes().toInt()
        )
        duration.toHours() < 24 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_verbose_hours_ago,
            duration.toHours().toInt(),
            duration.toHours().toInt()
        )
        duration.toDays() < 7 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_verbose_days_ago,
            duration.toDays().toInt(),
            duration.toDays().toInt()
        )
        duration.toDays() < DAYS_PER_MONTH -> resources.getQuantityString(
            R.plurals.util_formatters_relative_verbose_weeks_ago,
            (duration.toDays() / 7).toInt(),
            (duration.toDays() / 7).toInt()
        )
        else -> absolute(instant)
    }
}

fun formatRelativeTimeShort(
    context: Context,
    instant: Instant,
    now: Instant = Instant.now(),
    absolute: (Instant) -> String = ::formatMonthDay
): String {
    val duration = Duration.between(instant, now)
    val resources = context.resources
    return when {
        duration.isNegative -> context.getString(R.string.util_formatters_relative_short_future)
        duration.toMinutes() < 1 -> context.getString(R.string.util_formatters_relative_short_just_now)
        duration.toHours() < 1 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_short_minutes_ago,
            duration.toMinutes().toInt(),
            duration.toMinutes().toInt()
        )
        duration.toDays() < 1 -> resources.getQuantityString(
            R.plurals.util_formatters_relative_short_hours_ago,
            duration.toHours().toInt(),
            duration.toHours().toInt()
        )
        duration.toDays() < DAYS_PER_MONTH -> resources.getQuantityString(
            R.plurals.util_formatters_relative_short_days_ago,
            duration.toDays().toInt(),
            duration.toDays().toInt()
        )
        else -> absolute(instant)
    }
}

/**
 * Date and time for callers with no Context, so the clock form is the one the locale
 * conventionally uses rather than the one the device is set to. English falls out as 12-hour.
 * Prefer the Context overload wherever one is in reach.
 */
fun formatAbsoluteTimestamp(instant: Instant): String =
    DateTimeFormatter
        .ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)

/**
 * Date and time, with the clock in whichever form the device is set to rather than forced to
 * 24-hour, and the date fields in the reader's order.
 */
fun formatAbsoluteTimestamp(context: Context, instant: Instant): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMdyyyy")
    val date = DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)
    return "$date ${formatClockTime(context, instant.toEpochMilli())}"
}

fun formatSaveTimestamp(context: Context, timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val diffDays = diffMs / (1000 * 60 * 60 * 24)
    return when {
        diffDays == 0L -> context.getString(
            R.string.util_formatters_save_timestamp_today,
            formatClockTime(context, timestamp)
        )
        diffDays == 1L -> context.getString(R.string.util_formatters_save_timestamp_yesterday)
        diffDays < 7 -> context.resources.getQuantityString(
            R.plurals.util_formatters_save_timestamp_days_ago,
            diffDays.toInt(),
            diffDays.toInt()
        )
        else -> formatMonthDay(Instant.ofEpochMilli(timestamp))
    }
}

private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

private fun bytesParts(bytes: Long): Pair<Double, String> {
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val safeIndex = digitGroups.coerceIn(0, BYTE_UNITS.lastIndex)
    return bytes / Math.pow(1024.0, safeIndex.toDouble()) to BYTE_UNITS[safeIndex]
}

/**
 * Size for display. The number follows the reader's locale so the decimal separator is
 * theirs; the unit stays as written, because the letters are not what varies.
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val (value, unit) = bytesParts(bytes)
    if (unit == BYTE_UNITS.first()) return String.format(Locale.getDefault(), "%d %s", bytes, unit)
    return String.format(Locale.getDefault(), "%.1f %s", value, unit)
}

/**
 * Size for a log file rather than a screen. `util/SaveDebugLogger.kt` writes this into a
 * diffable log, so the text is pinned to [Locale.ROOT] and never routed through string
 * resources: a diff between two logs must stay about the sizes, never about the device
 * language they were captured on. Do not localize this or merge it with [formatBytes].
 */
fun formatBytesStable(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val (value, unit) = bytesParts(bytes)
    return String.format(Locale.ROOT, "%.1f %s", value, unit)
}

fun formatSaveSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun parseRelativeTimestamp(timestamp: String): Instant = try {
    Instant.parse(timestamp)
} catch (_: Exception) {
    val epochValue = timestamp.toLongOrNull()
    if (epochValue != null) {
        when {
            epochValue > 1_000_000_000_000_000L -> Instant.ofEpochSecond(epochValue / 1_000_000_000)
            epochValue > 1_000_000_000_000L -> Instant.ofEpochMilli(epochValue)
            else -> Instant.ofEpochSecond(epochValue)
        }
    } else {
        try {
            java.time.OffsetDateTime.parse(timestamp).toInstant()
        } catch (_: Exception) {
            throw IllegalArgumentException("Unknown timestamp format: $timestamp")
        }
    }
}
