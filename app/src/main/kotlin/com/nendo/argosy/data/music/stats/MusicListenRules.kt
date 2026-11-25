package com.nendo.argosy.data.music.stats

import java.time.Instant
import java.time.ZoneId

enum class MusicListenEndReason(val token: String) {
    COMPLETED("completed"),
    SKIPPED("skipped"),
    STOPPED("stopped")
}

object MusicListenRules {
    const val MIN_COUNTED_DURATION_MS = 30_000L
    const val MAX_COUNT_THRESHOLD_MS = 240_000L

    fun isCounted(listenedMs: Long, durationMs: Long): Boolean =
        durationMs >= MIN_COUNTED_DURATION_MS &&
            listenedMs >= minOf(durationMs / 2, MAX_COUNT_THRESHOLD_MS)

    fun localDay(epochMs: Long, zone: ZoneId): Int {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        return date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth
    }
}
