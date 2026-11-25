package com.nendo.argosy.ui.audio

import com.nendo.argosy.domain.model.MusicQueueTrack
import kotlin.random.Random

const val SKIP_PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

enum class PreviousAction {
    RESTART,
    STEP_BACK
}

/**
 * Play order over one source list. Shuffle reorders around the current track and turning it off
 * returns to source order at that same track.
 */
class PlaybackQueue(private val random: Random = Random.Default) {
    var sourceTracks: List<MusicQueueTrack> = emptyList()
        private set
    var order: List<MusicQueueTrack> = emptyList()
        private set
    var index: Int = 0
        private set
    var shuffle: Boolean = false
        private set

    val current: MusicQueueTrack? get() = order.getOrNull(index)
    val size: Int get() = order.size
    val isEmpty: Boolean get() = order.isEmpty()

    fun load(tracks: List<MusicQueueTrack>) {
        sourceTracks = tracks
        order = if (shuffle) tracks.shuffled(random) else tracks
        index = 0
    }

    /**
     * Loads [tracks] with play starting at source position [startIndex]. Under shuffle the
     * chosen track plays first and the rest follow in shuffled order.
     */
    fun loadAt(tracks: List<MusicQueueTrack>, startIndex: Int) {
        val start = tracks.getOrNull(startIndex)
        if (start == null) {
            load(tracks)
            return
        }
        sourceTracks = tracks
        if (shuffle) {
            order = listOf(start) + tracks.filterIndexed { i, _ -> i != startIndex }.shuffled(random)
            index = 0
        } else {
            order = tracks
            index = startIndex
        }
    }

    fun replaceKeepingCurrent(tracks: List<MusicQueueTrack>): Boolean {
        val playingId = current?.id
        val incoming = tracks.associateBy { it.id }
        sourceTracks = tracks
        order = if (shuffle) {
            val kept = order.mapNotNull { incoming[it.id] }
            val keptIds = kept.mapTo(HashSet()) { it.id }
            kept + tracks.filter { it.id !in keptIds }.shuffled(random)
        } else {
            tracks
        }
        val at = playingId?.let { id -> order.indexOfFirst { it.id == id } } ?: -1
        index = at.coerceAtLeast(0)
        return at >= 0
    }

    fun setShuffle(enabled: Boolean): Boolean {
        if (enabled == shuffle) return false
        shuffle = enabled
        val playing = current
        order = if (enabled) {
            val rest = sourceTracks.filter { it.id != playing?.id }.shuffled(random)
            listOfNotNull(playing) + rest
        } else {
            sourceTracks
        }
        index = playing?.let { track -> order.indexOfFirst { it.id == track.id } }
            ?.coerceAtLeast(0) ?: 0
        return true
    }

    fun advance(): Boolean {
        if (index + 1 >= order.size) return false
        index++
        return true
    }

    fun jumpTo(position: Int): Boolean {
        if (position !in order.indices) return false
        index = position
        return true
    }

    fun stepBack() {
        if (order.isEmpty()) return
        index = (index - 1).mod(order.size)
    }

    val isAtLast: Boolean get() = index >= order.lastIndex

    fun previousAction(positionMs: Long): PreviousAction =
        if (index == 0 || positionMs > SKIP_PREVIOUS_RESTART_THRESHOLD_MS) {
            PreviousAction.RESTART
        } else {
            PreviousAction.STEP_BACK
        }

    fun clear() {
        sourceTracks = emptyList()
        order = emptyList()
        index = 0
    }
}

enum class FadeInBlock {
    SUSPENDED,
    SILENCE_HELD,
    DISABLED,
    USER_PAUSED
}

class AmbientPlaybackGate {
    var enabled: Boolean = false
    var suspended: Boolean = false
    var userPaused: Boolean = false

    @Volatile
    var silenceHolds: Int = 0

    fun fadeInBlock(): FadeInBlock? = when {
        suspended -> FadeInBlock.SUSPENDED
        silenceHolds > 0 -> FadeInBlock.SILENCE_HELD
        !enabled -> FadeInBlock.DISABLED
        userPaused -> FadeInBlock.USER_PAUSED
        else -> null
    }
}
