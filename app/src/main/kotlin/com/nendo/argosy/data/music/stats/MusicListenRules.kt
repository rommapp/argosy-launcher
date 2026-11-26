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

/**
 * Time heard and track position for one listen, driven by a monotonic clock. [listenedMs] grows
 * only while audible. A [seek] moves [positionMs] and leaves [listenedMs] unchanged.
 */
class ListenProgress(startPositionMs: Long, durationMs: Long) {
    var durationMs: Long = durationMs
        private set
    var listenedMs: Long = 0L
        private set
    private var audibleSince: Long? = null
    private var anchorPositionMs: Long = startPositionMs
    private var anchorListenedMs: Long = 0L

    val isAudible: Boolean get() = audibleSince != null
    val positionMs: Long get() = anchorPositionMs + (listenedMs - anchorListenedMs)

    fun accumulate(clockMs: Long) {
        val since = audibleSince ?: return
        listenedMs += (clockMs - since).coerceAtLeast(0L)
        audibleSince = clockMs
    }

    fun startAudible(clockMs: Long) {
        accumulate(clockMs)
        audibleSince = clockMs
    }

    fun stopAudible(clockMs: Long) {
        accumulate(clockMs)
        audibleSince = null
    }

    fun observe(positionMs: Long?, durationMs: Long?) {
        positionMs?.takeIf { it >= 0 }?.let {
            anchorPositionMs = it
            anchorListenedMs = listenedMs
        }
        durationMs?.takeIf { it > this.durationMs }?.let { this.durationMs = it }
    }

    fun seek(clockMs: Long, positionMs: Long, durationMs: Long?) {
        accumulate(clockMs)
        observe(positionMs, durationMs)
    }
}
