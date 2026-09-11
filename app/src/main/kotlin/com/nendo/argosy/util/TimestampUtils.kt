package com.nendo.argosy.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Epoch millis for a server timestamp. A value carrying no zone is read as UTC, which is what
 * every Argosy backend stores; MySQL and MariaDB drop the offset on a TIMESTAMP column, so a
 * naive reading is the common case rather than the exception.
 */
fun parseTimestamp(timestamp: String): Long? {
    val text = timestamp.trim().ifEmpty { return null }
    ZONED_PARSERS.forEach { parse ->
        runCatching { return parse(text) }
    }
    return null
}

private val LOCAL_SPACE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private val ZONED_PARSERS: List<(String) -> Long> = listOf(
    { ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli() },
    { Instant.parse(it).toEpochMilli() },
    { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toEpochMilli(ZoneOffset.UTC) },
    { LocalDateTime.parse(it, LOCAL_SPACE_FORMAT).toEpochMilli(ZoneOffset.UTC) }
)

private fun LocalDateTime.toEpochMilli(offset: ZoneOffset): Long =
    atZone(offset).toInstant().toEpochMilli()
