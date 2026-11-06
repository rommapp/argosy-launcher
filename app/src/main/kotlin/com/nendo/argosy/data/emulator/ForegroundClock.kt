package com.nendo.argosy.data.emulator

import android.os.SystemClock

/**
 * Time a built-in game has spent on screen during the current session. Resuming while already
 * running and pausing while already paused change nothing.
 */
class ForegroundClock(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private val lock = Any()
    private var accumulatedMs = 0L
    private var resumedAt: Long? = null

    fun resume() = synchronized(lock) {
        if (resumedAt == null) resumedAt = now()
    }

    fun pause() = synchronized(lock) {
        resumedAt?.let { accumulatedMs += now() - it }
        resumedAt = null
    }

    fun reset(running: Boolean) = synchronized(lock) {
        accumulatedMs = 0L
        resumedAt = if (running) now() else null
    }

    fun elapsedMs(): Long = synchronized(lock) {
        accumulatedMs + (resumedAt?.let { now() - it } ?: 0L)
    }
}

/**
 * Splits a session of [totalMs] into active and standby time. Active is the smaller of time in
 * the foreground and time with the screen on; a negative measurement counts as the whole session.
 */
fun splitActivePlayTime(totalMs: Long, foregroundMs: Long, screenOnMs: Long): Pair<Long, Long> {
    val foreground = if (foregroundMs >= 0) foregroundMs else totalMs
    val screenOn = if (screenOnMs >= 0) screenOnMs else totalMs
    val active = minOf(foreground, screenOn).coerceIn(0, totalMs.coerceAtLeast(0))
    return active to (totalMs - active).coerceAtLeast(0)
}
