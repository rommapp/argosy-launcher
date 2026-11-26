package com.nendo.argosy.ui.audio

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide rules for the status pill's now-playing segment, shared by every display.
 *
 * [setSuppressed] is reference-counted per owner: each `true` needs a matching `false`, and two
 * live instances of one surface hide the segment until both have released it. [suppressed] holds
 * while any owner has an unreleased hold. [openRequests] carries taps from a surface that cannot
 * navigate itself to the one that can.
 */
@Singleton
class NowPlayingPillPolicy @Inject constructor() {
    private val holds = HashMap<String, Int>()
    private val _suppressed = MutableStateFlow(false)
    val suppressed: StateFlow<Boolean> = _suppressed.asStateFlow()

    private val _openRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val openRequests: SharedFlow<Unit> = _openRequests.asSharedFlow()

    fun setSuppressed(owner: String, suppressed: Boolean) {
        synchronized(holds) {
            val count = (holds[owner] ?: 0) + if (suppressed) 1 else -1
            if (count > 0) holds[owner] = count else holds.remove(owner)
            _suppressed.value = holds.isNotEmpty()
        }
    }

    fun requestOpen() {
        _openRequests.tryEmit(Unit)
    }
}
