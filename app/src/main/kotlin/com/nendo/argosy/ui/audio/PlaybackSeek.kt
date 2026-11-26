package com.nendo.argosy.ui.audio

sealed interface SeekRequest {
    data class By(val deltaMs: Long) : SeekRequest
    data class To(val positionMs: Long) : SeekRequest
}

/**
 * The position a seek lands on, clamped to the track, or null when nothing may seek: no prepared
 * track, a track of unknown length, or an override playing over the queue.
 */
fun resolveSeek(position: PlaybackPosition?, overrideActive: Boolean, request: SeekRequest): Long? {
    if (overrideActive || position == null || position.durationMs <= 0L) return null
    val requested = when (request) {
        is SeekRequest.By -> position.positionMs + request.deltaMs
        is SeekRequest.To -> request.positionMs
    }
    return requested.coerceIn(0L, position.durationMs)
}
