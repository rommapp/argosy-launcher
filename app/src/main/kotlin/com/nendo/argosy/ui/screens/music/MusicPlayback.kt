package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.music.library.MusicLibrary
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicStatTrack
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.AmbientMusicSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "MusicPlayback"

/**
 * Starts a music queue from any screen: builds the queue, turns music on when it is off, and
 * hands the queue to the player. Every play call returns true once playback started; a false
 * return has already told the user why.
 */
class MusicPlayback @Inject constructor(
    private val library: MusicLibrary,
    private val audio: AmbientAudioManager,
    private val musicSwitch: AmbientMusicSwitch,
    private val notifications: NotificationManager
) {
    suspend fun play(sourceId: String, label: String?, tracks: List<MusicTrack>, startIndex: Int): Boolean {
        if (tracks.isEmpty()) {
            notifications.showError(NotificationText.Res(R.string.music_library_notice_nothing_to_play))
            return false
        }
        val queue = musicGuarded(TAG, "queue") { withContext(Dispatchers.IO) { library.queueFor(tracks) } }
        if (queue.isNullOrEmpty()) {
            notifications.showError(NotificationText.Res(R.string.music_library_notice_play_failed))
            return false
        }
        playQueue(sourceId, label, queue, startIndex.coerceIn(0, queue.lastIndex), 0L)
        return true
    }

    suspend fun playStats(
        sourceId: String,
        label: String?,
        stats: List<MusicStatTrack>,
        startIndex: Int,
        shuffle: Boolean
    ): Boolean {
        if (stats.isEmpty()) {
            notifications.showError(NotificationText.Res(R.string.music_library_notice_nothing_to_play))
            return false
        }
        val ordered = if (shuffle) stats.shuffled() else stats
        val tracks = musicGuarded(TAG, "stat tracks") { library.tracksForKeys(ordered) }
        if (tracks == null) {
            notifications.showError(NotificationText.Res(R.string.music_library_notice_play_failed))
            return false
        }
        val startKey = if (shuffle) null else stats.getOrNull(startIndex)?.trackKey
        val start = tracks.indexOfFirst { it.key == startKey }.coerceAtLeast(0)
        return play(sourceId, label, tracks, start)
    }

    suspend fun playQueue(
        sourceId: String,
        label: String?,
        queue: List<MusicQueueTrack>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        musicSwitch.ensureEnabled()
        audio.playTracks(sourceId, label, queue, startIndex, startPositionMs)
    }
}
