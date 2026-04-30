package com.nendo.argosy.util

import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

fun formatPlayTime(minutes: Int): String = when {
    minutes < MINUTES_PER_HOUR -> "${minutes}m"
    minutes < MINUTES_PER_DAY -> "${minutes / MINUTES_PER_HOUR}h ${minutes % MINUTES_PER_HOUR}m"
    else -> "${minutes / MINUTES_PER_HOUR}h"
}

fun formatRelativeTime(instant: Instant?): String {
    if (instant == null) return ""
    val days = Duration.between(instant, Instant.now()).toDays()
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        else -> "${days / 30}mo ago"
    }
}

fun formatRelativeTime(timestamp: String): String = try {
    val instant = parseRelativeTimestamp(timestamp)
    val duration = Duration.between(instant, Instant.now())
    when {
        duration.isNegative -> "now"
        duration.toMinutes() < 1 -> "now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "${duration.toHours()}h ago"
        duration.toDays() < 7 -> "${duration.toDays()}d ago"
        duration.toDays() < 30 -> "${duration.toDays() / 7}w ago"
        else -> "${duration.toDays() / 30}mo ago"
    }
} catch (_: Exception) {
    ""
}

fun formatRelativeTimeVerbose(instant: Instant): String {
    val duration = Duration.between(instant, Instant.now())
    return when {
        duration.toMinutes() < 1 -> "just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()} min ago"
        duration.toHours() < 24 -> "${duration.toHours()} hr ago"
        duration.toDays() < 7 -> "${duration.toDays()} days ago"
        else -> "${duration.toDays() / 7} weeks ago"
    }
}

fun formatRelativeTimeShort(instant: Instant): String {
    val duration = Duration.between(instant, Instant.now())
    return when {
        duration.isNegative -> "in the future"
        duration.toMinutes() < 1 -> "just now"
        duration.toHours() < 1 -> "${duration.toMinutes()}m ago"
        duration.toDays() < 1 -> "${duration.toHours()}h ago"
        duration.toDays() < 30 -> "${duration.toDays()}d ago"
        else -> "${duration.toDays() / 30}mo ago"
    }
}

fun formatAbsoluteTimestamp(epochMillis: Long): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    return DateTimeFormatter.ofPattern("MMM d, h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

fun formatAbsoluteTimestamp(instant: Instant): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)

fun formatSaveTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val diffMs = System.currentTimeMillis() - timestamp
    val diffDays = diffMs / (1000 * 60 * 60 * 24)
    return when {
        diffDays == 0L -> "Today ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)}"
        diffDays == 1L -> "Yesterday"
        diffDays < 7 -> "$diffDays days ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val safeIndex = digitGroups.coerceIn(0, units.lastIndex)
    return String.format(
        Locale.US,
        "%.1f %s",
        bytes / Math.pow(1024.0, safeIndex.toDouble()),
        units[safeIndex]
    )
}

fun formatSaveSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
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
