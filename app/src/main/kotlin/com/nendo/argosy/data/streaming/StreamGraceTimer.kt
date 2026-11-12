package com.nendo.argosy.data.streaming

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The wait between a stream leaving the screen and Argosy ending it. [arm] restarts the wait,
 * [disarm] cancels it, and [onExpired] runs once when the full [durationMs] passes armed.
 */
class StreamGraceTimer(
    private val scope: CoroutineScope,
    private val durationMs: Long = DEFAULT_GRACE_MS,
    private val onExpired: suspend () -> Unit
) {
    private var job: Job? = null

    val isArmed: Boolean get() = job?.isActive == true

    fun arm() {
        job?.cancel()
        job = scope.launch {
            delay(durationMs)
            onExpired()
        }
    }

    fun disarm() {
        job?.cancel()
        job = null
    }

    companion object {
        const val DEFAULT_GRACE_MS = 5 * 60 * 1000L
    }
}
