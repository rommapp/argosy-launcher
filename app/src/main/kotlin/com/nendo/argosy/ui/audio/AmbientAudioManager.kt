package com.nendo.argosy.ui.audio

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.nendo.argosy.data.music.AudioLoudnessRepository
import com.nendo.argosy.data.music.analysis.TrackAnalysisStore
import com.nendo.argosy.data.music.stats.MusicListenEndReason
import com.nendo.argosy.data.preferences.MusicRepeatMode
import com.nendo.argosy.data.music.stats.MusicListenRecorder
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
private const val LAUNCHER_FOCUS_HANDOFF_MS = 300L
private const val TARGET_LOUDNESS_DB = -14.0
private const val GAIN_MIN_DB = -12.0
private const val GAIN_MAX_DB = 10.0
private const val ANALYSIS_PREFETCH_AHEAD = 2

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

private class PlayerSlot(
    val player: MediaPlayer,
    val autoStart: Boolean,
    var prepared: Boolean = false,
    var startWhenPrepared: Boolean = false,
    val startPositionMs: Long = 0L
)

@Singleton
class AmbientAudioManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loudnessRepository: AudioLoudnessRepository,
    private val analysisStore: TrackAnalysisStore,
    private val listenRecorder: MusicListenRecorder
) {
    companion object {
        const val AMBIENT_SOURCE_PLAYLIST = "playlist:"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val teardown = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private fun disposePlayer(player: MediaPlayer?) {
        player ?: return
        player.setOnPreparedListener(null)
        player.setOnErrorListener(null)
        player.setOnCompletionListener(null)
        teardown.launch { runCatching { player.release() } }
    }

    private val gate = AmbientPlaybackGate()
    private val queue = PlaybackQueue()

    private var slot: PlayerSlot? = null
    private var playing = false
    private var targetVolume = 0.5f
    private var fadeAnimator: ValueAnimator? = null
    private var fadeOutCancelled = false
    private var fadingOut = false
    private var consecutiveFailures = 0

    private var launcherSet = false
    private var launcherTracks: List<MusicQueueTrack> = emptyList()
    private var launcherRefresh: (suspend () -> List<MusicQueueTrack>)? = null
    private var activeSourceId: String = MusicSelection.LAUNCHER_ID
    private var activeSourceLabel: String? = null
    private var refreshJob: Job? = null
    private var generation = 0

    private var overrideActive = false
    private var overrideToken = 0
    private var overrideTitle: String? = null
    private var stashedSlot: PlayerSlot? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var trackGainDb = 0f
    private var logicalVolume = 0f
    private var analysisPrefetchedFor: String? = null

    private var gameSession: GameSessionAudio? = null
    private var heldForLaunchAtMs: Long? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val _currentTrackName = MutableStateFlow<String?>(null)
    val currentTrackName: StateFlow<String?> = _currentTrackName.asStateFlow()

    private val _playback = MutableStateFlow(AmbientPlaybackState())
    val playback: StateFlow<AmbientPlaybackState> = _playback.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        val wasEnabled = gate.enabled
        gate.enabled = enabled
        Logger.verbose(TAG) { "setEnabled=$enabled" }
        if (!enabled) {
            stopAndRelease()
        } else if (!wasEnabled) {
            gate.userPaused = false
            publish()
        }
    }

    val currentVolume: Float get() = targetVolume

    fun setVolume(volume: Int) {
        this.targetVolume = (volume / 100f).coerceIn(0f, 1f)
        Logger.verbose(TAG) { "setVolume=$volume ($targetVolume)" }
        slot?.let { applyPlayerVolume(it.player, playbackVolume()) }
    }

    private fun keepsPlayingThroughGame(): Boolean {
        val session = gameSession ?: return false
        return GamePlaybackPolicy.keepPlayingDuringGame(
            sourceIsLauncher = activeSourceId == MusicSelection.LAUNCHER_ID,
            themePreviewActive = overrideActive,
            session = session
        )
    }

    private fun playbackVolume(): Float {
        val session = gameSession
        if (session == null || !keepsPlayingThroughGame()) return targetVolume
        return GamePlaybackPolicy.duckedVolume(targetVolume, session.musicVolumePercent)
    }

    /**
     * Applies the running game session, or null once it has ended. Music the policy keeps playing
     * ducks to the session's level and returns to the full music volume when the session ends; music
     * the launcher faded on its way into this game comes back.
     */
    fun setGameSession(session: GameSessionAudio?) {
        if (session == gameSession) return
        gameSession = session
        Log.d(TAG, "game session audio: $session")
        if (session == null) {
            heldForLaunchAtMs = null
            rampToPlaybackVolume()
            return
        }
        val resumes = GamePlaybackPolicy.resumesAtSessionStart(
            keepsPlaying = keepsPlayingThroughGame(),
            heldAtMs = heldForLaunchAtMs,
            nowMs = SystemClock.elapsedRealtime()
        )
        heldForLaunchAtMs = null
        if (resumes) {
            gate.suspended = false
            Log.d(TAG, "resuming music held for the game launch")
            fadeIn()
            return
        }
        rampToPlaybackVolume()
    }

    private fun rampToPlaybackVolume(durationMs: Long = 500) {
        val current = slot ?: return
        if (!current.prepared || fadingOut || !isPlayerPlaying(current.player)) return
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(logicalVolume, playbackVolume()).apply {
            duration = durationMs
            addUpdateListener { animator ->
                val vol = animator.animatedValue as Float
                slot?.let { applyPlayerVolume(it.player, vol) }
            }
            start()
        }
    }

    private fun markHeldForLaunch() {
        if (playing && !gate.userPaused) heldForLaunchAtMs = SystemClock.elapsedRealtime()
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
            if (slot?.player !== player) return@launch
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
        if (!queue.setShuffle(shuffle)) return
        Logger.verbose(TAG) { "setShuffle=$shuffle" }
        publish()
    }

    private var repeat = MusicRepeatMode.ALL

    fun setRepeat(mode: MusicRepeatMode) {
        if (repeat == mode) return
        repeat = mode
        Logger.verbose(TAG) { "setRepeat=$mode" }
        publish()
    }

    private var playInBackground = false

    fun setPlayInBackground(enabled: Boolean) {
        playInBackground = enabled
    }

    fun keepsPlayingAsleep(): Boolean =
        playInBackground && context.getSystemService(PowerManager::class.java)?.isInteractive == false

    private val focusedLauncherWindows = mutableSetOf<String>()
    private var focusLossFade: Job? = null

    fun onLauncherWindowFocused(window: String) {
        focusedLauncherWindows += window
        focusLossFade?.cancel()
        focusLossFade = null
    }

    /**
     * Fades the music once no launcher window holds focus, after a short wait so focus moving
     * between the launcher's own screens never silences it. Music the game session policy keeps
     * playing is left alone.
     */
    fun onLauncherWindowUnfocused(window: String) {
        focusedLauncherWindows -= window
        if (focusedLauncherWindows.isNotEmpty()) return
        focusLossFade?.cancel()
        focusLossFade = scope.launch {
            delay(LAUNCHER_FOCUS_HANDOFF_MS)
            if (focusedLauncherWindows.isEmpty() && !keepsPlayingAsleep() && !keepsPlayingThroughGame()) {
                markHeldForLaunch()
                fadeOut()
            }
        }
    }

    /**
     * Supplies the launcher playlist and the refresh that re-expands it at each full loop. The
     * queue only follows it while the launcher playlist is the active selection.
     */
    fun setLauncherSource(
        tracks: List<MusicQueueTrack>,
        refresh: (suspend () -> List<MusicQueueTrack>)? = null
    ) {
        launcherRefresh = refresh
        if (launcherSet && tracks == launcherTracks) return
        val firstLoad = !launcherSet
        launcherSet = true
        launcherTracks = tracks
        if (activeSourceId != MusicSelection.LAUNCHER_ID) return
        Log.d(TAG, "setLauncherSource: ${tracks.size} tracks")

        generation++
        refreshJob?.cancel()

        if (firstLoad) {
            if (!overrideActive) stopAndRelease()
            queue.load(tracks)
            if (gate.enabled && !queue.isEmpty && !overrideActive) {
                queue.current?.let { preparePlayer(it, autoStart = false) }
            }
            publish()
            return
        }
        applyQueueUpdate(tracks)
    }

    fun playSelection(sourceId: String, sourceLabel: String?, tracks: List<MusicQueueTrack>) {
        startQueue(sourceId, sourceLabel) { queue.load(tracks) }
        Log.d(TAG, "playSelection: $sourceId, ${tracks.size} tracks")
        queue.current?.let { preparePlayer(it, autoStart = true) }
        publish()
    }

    fun playTracks(
        sourceId: String,
        label: String?,
        tracks: List<MusicQueueTrack>,
        startIndex: Int,
        startPositionMs: Long = 0L
    ) {
        startQueue(sourceId, label) { queue.loadAt(tracks, startIndex) }
        Log.d(TAG, "playTracks: $sourceId, ${tracks.size} tracks from $startIndex")
        queue.current?.let { preparePlayer(it, autoStart = true, startPositionMs = startPositionMs) }
        publish()
    }

    private inline fun startQueue(sourceId: String, sourceLabel: String?, load: () -> Unit) {
        generation++
        refreshJob?.cancel()
        activeSourceId = sourceId
        activeSourceLabel = sourceLabel
        gate.userPaused = false
        gate.suspended = false
        heldForLaunchAtMs = null
        consecutiveFailures = 0
        dropOverride()
        releasePlayer()
        load()
    }

    fun activateLauncher() {
        if (activeSourceId == MusicSelection.LAUNCHER_ID && !queue.isEmpty) {
            play()
            return
        }
        playSelection(MusicSelection.LAUNCHER_ID, null, launcherTracks)
    }

    fun play() {
        gate.userPaused = false
        gate.suspended = false
        heldForLaunchAtMs = null
        Log.d(TAG, "user play")
        fadeIn()
        publish()
    }

    fun pause() {
        gate.userPaused = true
        heldForLaunchAtMs = null
        Log.d(TAG, "user pause")
        fadeOut()
        publish()
    }

    fun skipNext(): Boolean {
        if (queue.isEmpty || (!overrideActive && queue.isAtLast)) return false
        gate.suspended = false
        consecutiveFailures = 0
        endListen(MusicListenEndReason.SKIPPED)
        dropOverride()
        advanceQueue(resume = true)
        return true
    }

    fun skipPrevious() {
        if (queue.isEmpty) return
        gate.suspended = false
        consecutiveFailures = 0
        endListen(MusicListenEndReason.SKIPPED)
        if (overrideActive) {
            dropOverride()
            restartAtCurrentIndex(resume = true)
            return
        }
        when (queue.previousAction(positionSnapshot()?.positionMs ?: 0L)) {
            PreviousAction.RESTART -> {
                val current = slot
                if (current != null && current.prepared) {
                    runCatching { current.player.seekTo(0) }
                    queue.current?.let { listenRecorder.onTrackStarted(it, activeSourceId) }
                } else {
                    restartAtCurrentIndex(resume = true)
                }
            }
            PreviousAction.STEP_BACK -> {
                queue.stepBack()
                restartAtCurrentIndex(resume = true)
            }
        }
    }

    fun playAt(position: Int) {
        if (!queue.jumpTo(position)) return
        endListen(MusicListenEndReason.SKIPPED)
        consecutiveFailures = 0
        gate.userPaused = false
        gate.suspended = false
        dropOverride()
        restartAtCurrentIndex(resume = true)
    }

    fun positionSnapshot(): PlaybackPosition? {
        val current = slot ?: return null
        if (!current.prepared) return null
        return runCatching {
            PlaybackPosition(
                positionMs = current.player.currentPosition.toLong().coerceAtLeast(0L),
                durationMs = current.player.duration.toLong().coerceAtLeast(0L)
            )
        }.getOrNull()
    }

    private fun applyQueueUpdate(tracks: List<MusicQueueTrack>) {
        val survived = queue.replaceKeepingCurrent(tracks)

        if (queue.isEmpty) {
            Log.w(TAG, "Playlist emptied while active")
            if (overrideActive) {
                releaseStash()
            } else {
                stopAndRelease()
            }
            publish()
            return
        }

        if (!survived && gate.enabled) {
            restartAtCurrentIndex(resume = playing)
        } else {
            publish()
        }
    }

    private fun preparePlayer(track: MusicQueueTrack, autoStart: Boolean, startPositionMs: Long = 0L) {
        val localPath = track.localPath
        if (localPath != null && !validatePath(localPath)) {
            Log.w(TAG, "Audio file not accessible: $localPath")
            onTrackFailed(resume = autoStart)
            return
        }

        val player = MediaPlayer()
        val newSlot = PlayerSlot(player, autoStart, startPositionMs = startPositionMs)
        try {
            player.setAudioAttributes(audioAttributes)
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            when (val source = track.source) {
                is MusicTrackSource.Local -> player.setDataSource(source.path)
                is MusicTrackSource.Remote ->
                    if (source.headers.isEmpty()) {
                        player.setDataSource(source.url)
                    } else {
                        player.setDataSource(context, Uri.parse(source.url), source.headers)
                    }
            }
            player.setVolume(0f, 0f)
            player.setOnPreparedListener {
                newSlot.prepared = true
                if (slot !== newSlot) return@setOnPreparedListener
                Log.d(TAG, "MediaPlayer prepared: ${track.title}")
                if (newSlot.startPositionMs > 0) {
                    runCatching { player.seekTo(newSlot.startPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
                }
                if (newSlot.autoStart || newSlot.startWhenPrepared) fadeIn()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                if (slot === newSlot) {
                    onTrackFailed(resume = playing || newSlot.autoStart || newSlot.startWhenPrepared)
                }
                true
            }
            player.setOnCompletionListener {
                if (slot !== newSlot) return@setOnCompletionListener
                Log.d(TAG, "Track completed")
                endListen(MusicListenEndReason.COMPLETED)
                consecutiveFailures = 0
                if (overrideActive) {
                    advanceQueue(resume = true)
                    return@setOnCompletionListener
                }
                when (trackEndAction(repeat, queue.isAtLast)) {
                    TrackEndAction.REPLAY -> restartAtCurrentIndex(resume = true)
                    TrackEndAction.ADVANCE -> advanceQueue(resume = true)
                    TrackEndAction.STOP -> stopAtQueueEnd()
                }
            }
            slot = newSlot
            listenRecorder.onTrackStarted(track, activeSourceId, startPositionMs)
            player.prepareAsync()
            attachLeveling(player, localPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaPlayer", e)
            disposePlayer(player)
            if (slot === newSlot) slot = null
            onTrackFailed(resume = autoStart)
        }
    }

    private fun onTrackFailed(resume: Boolean) {
        consecutiveFailures++
        if (consecutiveFailures >= queue.size.coerceAtLeast(1)) {
            Log.w(TAG, "Every track in the queue failed; stopping")
            consecutiveFailures = 0
            releasePlayer()
            publish()
            return
        }
        advanceQueue(resume)
    }

    private fun advanceQueue(resume: Boolean) {
        if (overrideActive) {
            releaseStash()
            return
        }
        if (queue.isEmpty) return
        releasePlayer()
        if (!queue.advance()) {
            refreshAndRestart(resume)
            return
        }
        Log.d(TAG, "Playing next track: ${queue.current?.title}")
        restartAtCurrentIndex(resume)
    }

    private fun stopAtQueueEnd() {
        Log.d(TAG, "Queue finished with repeat off")
        gate.userPaused = true
        queue.jumpTo(0)
        restartAtCurrentIndex(resume = false)
    }

    private fun refreshAndRestart(resume: Boolean) {
        val gen = generation
        val launcherActive = activeSourceId == MusicSelection.LAUNCHER_ID
        val refresh = if (launcherActive) launcherRefresh else null
        val snapshot = queue.sourceTracks
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                refresh?.invoke() ?: snapshot.filter { track ->
                    track.localPath?.let { File(it).canRead() } ?: true
                }
            }
            if (gen != generation) return@launch
            if (launcherActive) launcherTracks = fresh
            queue.load(fresh)
            if (queue.isEmpty) {
                Log.w(TAG, "No more tracks in playlist")
                setPlaying(false)
                return@launch
            }
            Log.d(TAG, "Playlist loop refreshed: ${queue.size} tracks")
            restartAtCurrentIndex(resume)
        }
    }

    private fun restartAtCurrentIndex(resume: Boolean) {
        if (overrideActive) {
            releaseStash()
            publish()
            return
        }
        releasePlayer()
        val track = queue.current
        if (track == null) {
            publish()
            return
        }
        preparePlayer(track, autoStart = resume)
        publish()
    }

    private fun releasePlayer() {
        val outgoing = slot ?: return
        endListen(MusicListenEndReason.STOPPED)
        slot = null
        fadeAnimator?.cancel()
        fadeAnimator = null
        fadingOut = false
        releaseEnhancer()
        disposePlayer(outgoing.player)
        setPlaying(false)
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
        if (!gate.enabled) {
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
        overrideTitle = null
        Log.d(TAG, "clearOverride")
        fadeOut {
            if (token != overrideToken || overrideActive) return@fadeOut
            val outgoing = slot
            slot = null
            releaseEnhancer()
            disposePlayer(outgoing?.player)
            resumePlaylistAfterOverride()
        }
    }

    private fun dropOverride() {
        if (!overrideActive) return
        endListen(MusicListenEndReason.STOPPED)
        overrideToken++
        overrideActive = false
        overrideTitle = null
        releasePlayer()
        releaseStash()
        Log.d(TAG, "override dropped for user playback")
    }

    private fun detachCurrentPlayerForOverride() {
        val outgoing = slot ?: return
        slot = null
        if (stashedSlot == null) {
            stashedSlot = outgoing
        } else {
            disposePlayer(outgoing.player)
        }
    }

    private fun prepareOverride(source: AmbientOverrideSource, token: Int) {
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(audioAttributes)
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            when (source) {
                is AmbientOverrideSource.Local -> player.setDataSource(source.path)
                is AmbientOverrideSource.Remote ->
                    player.setDataSource(context, Uri.parse(source.url), source.headers)
            }
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnPreparedListener {
                if (token != overrideToken || !overrideActive) {
                    disposePlayer(it)
                    return@setOnPreparedListener
                }
                slot = PlayerSlot(player, autoStart = true, prepared = true)
                attachLeveling(player, (source as? AmbientOverrideSource.Local)?.path)
                overrideTitle = source.displayName
                _currentTrackName.value = source.displayName
                publish()
                fadeIn()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Override error: what=$what extra=$extra")
                if (token == overrideToken && overrideActive) {
                    clearOverride()
                } else {
                    disposePlayer(player)
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
        val stash = stashedSlot
        stashedSlot = null
        publish()
        if (!gate.enabled) {
            disposePlayer(stash?.player)
            return
        }
        if (stash != null) {
            slot = stash
            attachLeveling(stash.player, queue.current?.localPath)
            fadeIn()
        } else {
            restartAtCurrentIndex(resume = true)
        }
    }

    private fun releaseStash() {
        val stash = stashedSlot ?: return
        endListen(MusicListenEndReason.STOPPED)
        stashedSlot = null
        disposePlayer(stash.player)
    }

    fun suspend() {
        if (keepsPlayingThroughGame()) {
            Log.d(TAG, "suspend skipped: music keeps playing through the game")
            return
        }
        markHeldForLaunch()
        gate.suspended = true
        fadeOut()
        Log.d(TAG, "suspended - awaiting user input to resume")
    }

    fun resumeFromSuspend() {
        if (gate.suspended) {
            gate.suspended = false
            heldForLaunchAtMs = null
            Logger.verbose(TAG) { "resumed from suspend" }
            fadeIn()
        }
    }

    /**
     * Resumes from suspend on launcher input, except while a game session holds music the policy
     * stops. A launcher surface can stay interactive beside a running game, and input there must
     * not bring that music back.
     */
    fun resumeFromLauncherInput() {
        if (!GamePlaybackPolicy.companionInputMayResume(gameSession, keepsPlayingThroughGame())) return
        resumeFromSuspend()
    }

    fun holdSilence() {
        gate.silenceHolds++
        Log.d(TAG, "silence hold acquired (${gate.silenceHolds})")
        fadeOut()
    }

    fun releaseSilence() {
        gate.silenceHolds = (gate.silenceHolds - 1).coerceAtLeast(0)
        Log.d(TAG, "silence hold released (${gate.silenceHolds})")
        if (gate.silenceHolds == 0) fadeIn()
    }

    private var videoSilenceHeld = false

    /**
     * Yields the audio output to the video player until [releaseVideoSilence]. This holds silence
     * rather than stopping: [stopAndRelease] destroys the MediaPlayer along with any active
     * override and its stashed playlist player, so music could only come back from the top of the
     * current track and a game override would be lost, whereas a silence hold fades out a prepared
     * player that keeps its position and fades back in where it left off.
     *
     * Idempotent in both directions, so the player can call it from every path that starts or ends
     * playback without unbalancing the underlying hold count.
     */
    fun holdVideoSilence() {
        scope.launch(Dispatchers.Main.immediate) {
            if (videoSilenceHeld) return@launch
            videoSilenceHeld = true
            holdSilence()
        }
    }

    fun releaseVideoSilence() {
        scope.launch(Dispatchers.Main.immediate) {
            if (!videoSilenceHeld) return@launch
            videoSilenceHeld = false
            releaseSilence()
        }
    }

    fun fadeIn(durationMs: Long = 500) {
        gate.fadeInBlock()?.let { block ->
            Logger.verbose(TAG) { "fadeIn skipped: $block" }
            return
        }

        if (slot == null && !overrideActive) {
            queue.current?.let { preparePlayer(it, autoStart = true) }
            return
        }

        val current = slot ?: return
        if (!current.prepared) {
            current.startWhenPrepared = true
            return
        }
        val player = current.player
        if (isPlayerPlaying(player) && !fadingOut) return

        fadeOutCancelled = true
        fadingOut = false
        fadeAnimator?.cancel()

        try {
            applyPlayerVolume(player, 0f)
            if (!player.isPlaying) {
                player.start()
            }
            setPlaying(true)

            fadeAnimator = ValueAnimator.ofFloat(0f, playbackVolume()).apply {
                duration = durationMs
                addUpdateListener { animator ->
                    val vol = animator.animatedValue as Float
                    slot?.let { applyPlayerVolume(it.player, vol) }
                }
                start()
            }
            Log.d(TAG, "fadeIn started")
        } catch (e: Exception) {
            Log.e(TAG, "fadeIn failed", e)
        }
    }

    fun fadeOut(durationMs: Long = 500, onComplete: () -> Unit = {}) {
        val current = slot
        if (current == null || !current.prepared || !isPlayerPlaying(current.player)) {
            onComplete()
            return
        }

        fadeOutCancelled = false
        fadingOut = true
        fadeAnimator?.cancel()

        fadeAnimator = ValueAnimator.ofFloat(logicalVolume, 0f).apply {
            duration = durationMs
            addUpdateListener { animator ->
                val vol = animator.animatedValue as Float
                slot?.let { applyPlayerVolume(it.player, vol) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!fadeOutCancelled) {
                        fadingOut = false
                        pauseInternal()
                    }
                    onComplete()
                }
            })
            start()
        }
        Log.d(TAG, "fadeOut started")
    }

    private fun isPlayerPlaying(player: MediaPlayer): Boolean =
        runCatching { player.isPlaying }.getOrDefault(false)

    private fun pauseInternal() {
        val current = slot
        try {
            if (current != null && current.prepared) current.player.pause()
            setPlaying(false)
            Log.d(TAG, "paused")
        } catch (e: Exception) {
            Log.e(TAG, "pause failed", e)
        }
    }

    private fun stopAndRelease() {
        endListen(MusicListenEndReason.STOPPED)
        fadeAnimator?.cancel()
        fadeAnimator = null
        fadingOut = false
        overrideToken++
        overrideActive = false
        overrideTitle = null
        releaseStash()
        releaseEnhancer()

        val outgoing = slot
        slot = null
        try {
            if (outgoing?.prepared == true) outgoing.player.pause()
            disposePlayer(outgoing?.player)
        } catch (e: Exception) {
            Log.e(TAG, "stopAndRelease error", e)
        }
        setPlaying(false)
        Logger.verbose(TAG) { "stopped and released" }
    }

    private fun setPlaying(value: Boolean) {
        playing = value
        val position = listenPosition()
        listenRecorder.onAudibleChanged(value && !overrideActive, position?.positionMs, position?.durationMs)
        publish()
    }

    private fun endListen(reason: MusicListenEndReason) {
        val position = listenPosition()
        listenRecorder.onTrackEnded(reason, position?.positionMs, position?.durationMs)
    }

    private fun listenPosition(): PlaybackPosition? =
        if (overrideActive) null else positionSnapshot()

    private fun publish() {
        val track = queue.current
        _playback.value = AmbientPlaybackState(
            trackTitle = track?.title,
            gameTitle = track?.gameTitle,
            coverPath = track?.coverPath,
            artist = track?.artist,
            album = track?.album,
            art = track?.art,
            durationMs = track?.durationSec?.let { it * 1000L },
            overrideTitle = if (overrideActive) overrideTitle else null,
            index = queue.index,
            count = queue.size,
            tracks = queue.order,
            isPlaying = playing,
            userPaused = gate.userPaused,
            shuffle = queue.shuffle,
            repeat = repeat,
            sourceId = activeSourceId,
            sourceLabel = activeSourceLabel
        )
        if (!overrideActive) _currentTrackName.value = track?.title
        prefetchAnalysisAfter(track)
    }

    private fun prefetchAnalysisAfter(current: MusicQueueTrack?) {
        if (current?.id == analysisPrefetchedFor) return
        analysisPrefetchedFor = current?.id
        val order = queue.order
        val index = queue.index
        analysisStore.prefetch((1..ANALYSIS_PREFETCH_AHEAD).mapNotNull { order.getOrNull(index + it) })
    }

    fun release() {
        generation++
        refreshJob?.cancel()
        refreshJob = null
        stopAndRelease()
        gate.enabled = false
        launcherSet = false
        launcherTracks = emptyList()
        launcherRefresh = null
        activeSourceId = MusicSelection.LAUNCHER_ID
        activeSourceLabel = null
        queue.clear()
        _currentTrackName.value = null
        publish()
    }
}
