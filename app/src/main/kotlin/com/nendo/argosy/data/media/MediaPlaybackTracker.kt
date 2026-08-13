package com.nendo.argosy.data.media

import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaPlaybackTracker"

/**
 * The media item the player currently has open. [isPlaying] distinguishes active playback from a
 * paused player; the item stays open across a pause, so consumers that care about "is a movie on
 * screen" read the presence of this object and consumers that care about audible output read
 * [isPlaying].
 */
data class ActiveMediaPlayback(
    val itemId: String,
    val title: String,
    val isPlaying: Boolean
)

/**
 * Single source of truth for what the media player has open right now. The player is the only
 * writer; everything else observes [activePlayback]. Nothing here is persisted -- an open player is
 * process state, and a killed process has no playback.
 */
@Singleton
class MediaPlaybackTracker @Inject constructor() {

    private val _activePlayback = MutableStateFlow<ActiveMediaPlayback?>(null)
    val activePlayback: StateFlow<ActiveMediaPlayback?> = _activePlayback.asStateFlow()

    fun onPlaybackStarted(itemId: String, title: String) {
        Logger.verbose(TAG) { "started $itemId" }
        _activePlayback.value = ActiveMediaPlayback(itemId, title, isPlaying = true)
    }

    /**
     * Reports transport state for the open item. A report for a different item than the one open is
     * a late callback from a player that has already moved on and is ignored.
     */
    fun onPlaybackStateChanged(itemId: String, isPlaying: Boolean) {
        val current = _activePlayback.value ?: return
        if (current.itemId != itemId) return
        if (current.isPlaying == isPlaying) return
        _activePlayback.value = current.copy(isPlaying = isPlaying)
    }

    fun onPlaybackEnded(itemId: String) {
        val current = _activePlayback.value ?: return
        if (current.itemId != itemId) return
        Logger.verbose(TAG) { "ended $itemId" }
        _activePlayback.value = null
    }

    fun clear() {
        if (_activePlayback.value == null) return
        Logger.verbose(TAG) { "cleared" }
        _activePlayback.value = null
    }
}
