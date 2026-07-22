package com.nendo.argosy.ui.audio

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import com.nendo.argosy.data.music.AudioLoudnessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TAG = "AmbientAudio"
private const val TARGET_LOUDNESS_DB = -14.0
private const val GAIN_MIN_DB = -12.0
private const val GAIN_MAX_DB = 10.0

sealed interface AmbientOverrideSource {
    val displayName: String

    data class Local(
        val path: String,
        override val displayName: String
    ) : AmbientOverrideSource

    data class Remote(
        val url: String,
        val headers: Map<String, String>,
        override val displayName: String
    ) : AmbientOverrideSource
}

@Singleton
class AmbientAudioManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loudnessRepository: AudioLoudnessRepository
) {
    companion object {
        const val AMBIENT_SOURCE_PLAYLIST = "playlist:"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaPlayer: MediaPlayer? = null
    private var enabled = false
    private var targetVolume = 0.5f
    private var fadeAnimator: ValueAnimator? = null
    private var fadeOutCancelled = false
    private var suspended = false

    private var sourceSet = false
    private var sourcePaths: List<String> = emptyList()
    private var playlistRefresh: (suspend () -> List<String>)? = null
    private var refreshJob: Job? = null
    private var generation = 0
    private var playlist: List<String> = emptyList()
    private var playlistIndex = 0
    private var shuffle = false

    private var overrideActive = false
    private var overrideToken = 0
    private var stashedPlaylistPlayer: MediaPlayer? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var trackGainDb = 0f
    private var logicalVolume = 0f

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrackName = MutableStateFlow<String?>(null)
    val currentTrackName: StateFlow<String?> = _currentTrackName.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Log.d(TAG, "setEnabled=$enabled")
        if (!enabled) {
            stopAndRelease()
        }
    }

    @Volatile
    private var silenceHolds = 0

    val currentVolume: Float get() = targetVolume

    fun setVolume(volume: Int) {
        this.targetVolume = (volume / 100f).coerceIn(0f, 1f)
        Log.d(TAG, "setVolume=$volume (${this.targetVolume})")
        mediaPlayer?.let { applyPlayerVolume(it, targetVolume) }
    }

    private fun applyPlayerVolume(player: MediaPlayer, volume: Float) {
        logicalVolume = volume
        val attenuation = if (trackGainDb < 0f) 10.0.pow(trackGainDb / 20.0).toFloat() else 1f
        val level = (volume * attenuation).coerceIn(0f, 1f)
        runCatching { player.setVolume(level, level) }
    }

    private fun attachLeveling(player: MediaPlayer, localPath: String?) {
        releaseEnhancer()
        trackGainDb = 0f
        loudnessEnhancer = try {
            LoudnessEnhancer(player.audioSessionId)
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer unavailable, attenuation-only leveling: ${e.message}")
            null
        }
        if (localPath == null) {
            applyLeveling(player)
            return
        }
        scope.launch {
            val meanDb = loudnessRepository.playbackMeanDb(localPath)
            if (mediaPlayer !== player) return@launch
            trackGainDb = computeGainDb(meanDb)
            applyLeveling(player)
        }
    }

    private fun computeGainDb(meanDb: Double?): Float =
        if (meanDb == null) 0f
        else (TARGET_LOUDNESS_DB - meanDb).coerceIn(GAIN_MIN_DB, GAIN_MAX_DB).toFloat()

    private fun applyLeveling(player: MediaPlayer) {
        val boostMb = (trackGainDb.coerceAtLeast(0f) * 100).roundToInt()
        loudnessEnhancer?.let { enhancer ->
            try {
                enhancer.setTargetGain(boostMb)
                enhancer.setEnabled(boostMb > 0)
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer apply failed, attenuation-only leveling: ${e.message}")
                releaseEnhancer()
            }
        }
        applyPlayerVolume(player, logicalVolume)
    }

    private fun releaseEnhancer() {
        loudnessEnhancer?.let { runCatching { it.release() } }
        loudnessEnhancer = null
    }

    fun setShuffle(shuffle: Boolean) {
        this.shuffle = shuffle
        Log.d(TAG, "setShuffle=$shuffle")
        if (playlist.isNotEmpty()) {
            reshufflePlaylist()
        }
    }

    /** Replaces the playback queue; refresh re-expands the playlist at each full loop. */
    fun setPlaylistSource(paths: List<String>, refresh: (suspend () -> List<String>)? = null) {
        playlistRefresh = refresh
        if (sourceSet && paths == sourcePaths) return
        Log.d(TAG, "setPlaylistSource: ${paths.size} tracks")

        generation++
        refreshJob?.cancel()

        if (!sourceSet) {
            if (!overrideActive) {
                stopAndRelease()
            }
            sourceSet = true
            sourcePaths = paths
            playlist = if (shuffle) paths.shuffled() else paths
            playlistIndex = 0
            updateCurrentTrackName()
            if (enabled && playlist.isNotEmpty() && !overrideActive) {
                preparePlayer(playlist[playlistIndex])
            }
            return
        }

        sourcePaths = paths
        applyPlaylistUpdate(paths)
    }

    private fun applyPlaylistUpdate(paths: List<String>) {
        val current = playlist.getOrNull(playlistIndex)
        val updated = if (shuffle) {
            val kept = playlist.filter { it in paths }
            kept + paths.filter { it !in kept }.shuffled()
        } else {
            paths
        }
        playlist = updated

        if (updated.isEmpty()) {
            Log.w(TAG, "Playlist emptied while active")
            if (overrideActive) {
                releaseStash()
            } else {
                stopAndRelease()
            }
            playlistIndex = 0
            updateCurrentTrackName()
            return
        }

        val currentIdx = current?.let { updated.indexOf(it) } ?: -1
        if (currentIdx >= 0) {
            playlistIndex = currentIdx
            updateCurrentTrackName()
        } else {
            playlistIndex = 0
            if (enabled) {
                restartAtCurrentIndex(resume = _isPlaying.value)
            } else {
                updateCurrentTrackName()
            }
        }
    }

    private fun reshufflePlaylist() {
        if (playlist.isEmpty()) return
        playlist = playlist.shuffled()
        playlistIndex = 0
        updateCurrentTrackName()
        Log.d(TAG, "Reshuffled playlist")
    }

    private fun updateCurrentTrackName() {
        if (overrideActive) return
        _currentTrackName.value = playlist.getOrNull(playlistIndex)?.substringAfterLast("/")
    }

    private fun preparePlayer(path: String) {
        if (!validatePath(path)) {
            Log.w(TAG, "Audio file not accessible: $path")
            playNextTrack()
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(path)
                setVolume(0f, 0f)
                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer prepared: ${path.substringAfterLast("/")}")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    playNextTrack()
                    true
                }
                setOnCompletionListener {
                    Log.d(TAG, "Track completed")
                    playNextTrack()
                }
                prepareAsync()
            }
            mediaPlayer?.let { attachLeveling(it, path) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaPlayer", e)
            mediaPlayer = null
            playNextTrack()
        }
    }

    private fun playNextTrack() {
        if (overrideActive) {
            releaseStash()
            return
        }
        if (playlist.isEmpty()) return

        val wasPlaying = _isPlaying.value
        releaseEnhancer()
        mediaPlayer?.release()
        mediaPlayer = null

        playlistIndex++
        if (playlistIndex >= playlist.size) {
            refreshAndRestart(resume = wasPlaying)
            return
        }

        Log.d(TAG, "Playing next track: ${playlist[playlistIndex].substringAfterLast("/")}")
        restartAtCurrentIndex(resume = wasPlaying)
    }

    private fun refreshAndRestart(resume: Boolean) {
        val gen = generation
        val refresh = playlistRefresh
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                refresh?.invoke() ?: sourcePaths.filter { File(it).canRead() }
            }
            if (gen != generation) return@launch
            sourcePaths = fresh
            playlist = if (shuffle) fresh.shuffled() else fresh
            playlistIndex = 0
            updateCurrentTrackName()
            if (playlist.isEmpty()) {
                Log.w(TAG, "No more tracks in playlist")
                _isPlaying.value = false
                return@launch
            }
            Log.d(TAG, "Playlist loop refreshed: ${playlist.size} tracks")
            restartAtCurrentIndex(resume = resume)
        }
    }

    private fun restartAtCurrentIndex(resume: Boolean) {
        if (overrideActive) {
            releaseStash()
            return
        }
        releaseEnhancer()
        mediaPlayer?.release()
        mediaPlayer = null
        updateCurrentTrackName()
        val track = playlist.getOrNull(playlistIndex) ?: return
        preparePlayer(track)
        if (resume && enabled && !suspended) {
            mediaPlayer?.setOnPreparedListener {
                try {
                    applyPlayerVolume(it, targetVolume)
                    it.start()
                    _isPlaying.value = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start next track", e)
                }
            }
        }
    }

    private fun validatePath(path: String): Boolean {
        return try {
            File(path).canRead()
        } catch (e: Exception) {
            Log.w(TAG, "Path validation failed: ${e.message}")
            false
        }
    }

    /**
     * Fades out current playback and loops the given track on top of the playlist,
     * leaving playlist position untouched until [clearOverride].
     */
    suspend fun playOverride(source: AmbientOverrideSource) = withContext(Dispatchers.Main.immediate) {
        if (!enabled) {
            Log.d(TAG, "playOverride skipped: disabled")
            return@withContext
        }
        if (source is AmbientOverrideSource.Local && !validatePath(source.path)) {
            Log.w(TAG, "playOverride skipped: unreadable path ${source.path}")
            return@withContext
        }
        overrideToken++
        val token = overrideToken
        overrideActive = true
        Log.d(TAG, "playOverride: ${source.displayName}")
        fadeOut {
            if (token != overrideToken) return@fadeOut
            detachCurrentPlayerForOverride()
            prepareOverride(source, token)
        }
    }

    /** Fades out an active override and resumes the underlying playlist where it left off. */
    fun clearOverride() {
        if (!overrideActive) return
        overrideToken++
        val token = overrideToken
        overrideActive = false
        Log.d(TAG, "clearOverride")
        fadeOut {
            if (token != overrideToken || overrideActive) return@fadeOut
            val outgoing = mediaPlayer
            mediaPlayer = null
            releaseEnhancer()
            runCatching { outgoing?.release() }
            resumePlaylistAfterOverride()
        }
    }

    private fun detachCurrentPlayerForOverride() {
        val outgoing = mediaPlayer ?: return
        mediaPlayer = null
        if (stashedPlaylistPlayer == null) {
            stashedPlaylistPlayer = outgoing
        } else {
            runCatching { outgoing.release() }
        }
    }

    private fun prepareOverride(source: AmbientOverrideSource, token: Int) {
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(audioAttributes)
            when (source) {
                is AmbientOverrideSource.Local -> player.setDataSource(source.path)
                is AmbientOverrideSource.Remote ->
                    player.setDataSource(context, Uri.parse(source.url), source.headers)
            }
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnPreparedListener {
                if (token != overrideToken || !overrideActive) {
                    runCatching { it.release() }
                    return@setOnPreparedListener
                }
                mediaPlayer = player
                attachLeveling(player, (source as? AmbientOverrideSource.Local)?.path)
                _currentTrackName.value = source.displayName
                if (enabled && !suspended) fadeIn()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Override error: what=$what extra=$extra")
                if (token == overrideToken && overrideActive) {
                    clearOverride()
                } else {
                    runCatching { player.release() }
                }
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare override", e)
            if (token == overrideToken && overrideActive) clearOverride()
        }
    }

    private fun resumePlaylistAfterOverride() {
        val stash = stashedPlaylistPlayer
        stashedPlaylistPlayer = null
        updateCurrentTrackName()
        if (!enabled) {
            runCatching { stash?.release() }
            return
        }
        if (stash != null) {
            mediaPlayer = stash
            attachLeveling(stash, playlist.getOrNull(playlistIndex))
            if (!suspended) fadeIn()
        } else {
            restartAtCurrentIndex(resume = true)
        }
    }

    private fun releaseStash() {
        val stash = stashedPlaylistPlayer ?: return
        stashedPlaylistPlayer = null
        runCatching { stash.release() }
    }

    fun suspend() {
        suspended = true
        fadeOut()
        Log.d(TAG, "suspended - awaiting user input to resume")
    }

    fun resumeFromSuspend() {
        if (suspended) {
            suspended = false
            Log.d(TAG, "resumed from suspend")
            fadeIn()
        }
    }

    fun holdSilence() {
        silenceHolds++
        Log.d(TAG, "silence hold acquired ($silenceHolds)")
        fadeOut()
    }

    fun releaseSilence() {
        silenceHolds = (silenceHolds - 1).coerceAtLeast(0)
        Log.d(TAG, "silence hold released ($silenceHolds)")
        if (silenceHolds == 0) fadeIn()
    }

    fun fadeIn(durationMs: Long = 500) {
        if (suspended) {
            Log.d(TAG, "fadeIn skipped: suspended (awaiting user input)")
            return
        }
        if (silenceHolds > 0) {
            Log.d(TAG, "fadeIn skipped: silence held")
            return
        }
        if (!enabled) {
            Log.d(TAG, "fadeIn skipped: disabled")
            return
        }

        if (mediaPlayer == null && !overrideActive && playlist.isNotEmpty()) {
            preparePlayer(playlist[playlistIndex])
        }

        val player = mediaPlayer ?: return

        fadeOutCancelled = true
        fadeAnimator?.cancel()

        try {
            applyPlayerVolume(player, 0f)
            if (!player.isPlaying) {
                player.start()
            }
            _isPlaying.value = true

            fadeAnimator = ValueAnimator.ofFloat(0f, targetVolume).apply {
                duration = durationMs
                addUpdateListener { animator ->
                    val vol = animator.animatedValue as Float
                    mediaPlayer?.let { applyPlayerVolume(it, vol) }
                }
                start()
            }
            Log.d(TAG, "fadeIn started")
        } catch (e: Exception) {
            Log.e(TAG, "fadeIn failed", e)
        }
    }

    fun fadeOut(durationMs: Long = 500, onComplete: () -> Unit = {}) {
        val player = mediaPlayer
        if (player == null || !player.isPlaying) {
            onComplete()
            return
        }

        fadeOutCancelled = false
        fadeAnimator?.cancel()

        fadeAnimator = ValueAnimator.ofFloat(targetVolume, 0f).apply {
            duration = durationMs
            addUpdateListener { animator ->
                val vol = animator.animatedValue as Float
                mediaPlayer?.let { applyPlayerVolume(it, vol) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!fadeOutCancelled) {
                        pauseInternal()
                    }
                    onComplete()
                }
            })
            start()
        }
        Log.d(TAG, "fadeOut started")
    }

    private fun pauseInternal() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            Log.d(TAG, "paused")
        } catch (e: Exception) {
            Log.e(TAG, "pause failed", e)
        }
    }

    private fun stopAndRelease() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        overrideToken++
        overrideActive = false
        releaseStash()
        releaseEnhancer()

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "stopAndRelease error", e)
        }
        mediaPlayer = null
        _isPlaying.value = false
        Log.d(TAG, "stopped and released")
    }

    fun release() {
        generation++
        refreshJob?.cancel()
        refreshJob = null
        stopAndRelease()
        enabled = false
        sourceSet = false
        sourcePaths = emptyList()
        playlistRefresh = null
        playlist = emptyList()
        playlistIndex = 0
        _currentTrackName.value = null
    }
}
