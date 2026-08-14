package com.nendo.argosy.ui.screens.player

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.MediaPlaybackTracker
import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.TICKS_PER_MILLISECOND
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.screens.player.delegates.PlayerChromeDelegate
import com.nendo.argosy.ui.screens.player.delegates.PlayerTrackDelegate
import com.nendo.argosy.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PlayerViewModel"
private const val POSITION_TICK_MS = 250L
private const val SCRUB_COMMIT_DELAY_MS = 600L
private const val SCRUB_STEP_MS = 10_000L
private const val NEAR_END_MS = 15_000L
private const val CHAPTER_BACK_GRACE_MS = 3_000L

/**
 * How the player was asked to open. [startPositionMs] below zero means the caller made no decision
 * and the player resolves resume itself, which is what a relaunch on another display sends.
 */
data class PlayerArgs(
    val itemId: String,
    val title: String = "",
    val subtitle: String = "",
    val startPositionMs: Long = -1
)

sealed interface PlayerEvent {
    data object Finish : PlayerEvent

    /**
     * A game has taken the screen this window is on and the viewing should carry on elsewhere.
     *
     * [positionMs] rides along rather than being left for the next window to look up: the position
     * is written to the database on a scope that outlives this view model precisely so it survives
     * teardown, and a window opening in the same instant would race that write and resume from the
     * previous value. Whether there is anywhere to move to is not decided here - a window knows
     * which display it is on, a view model does not.
     */
    data class Relocate(val itemId: String, val positionMs: Long) : PlayerEvent
}

/**
 * Drives one video from negotiation to teardown.
 *
 * Three obligations run through here and all three funnel through [endMediaSession] so they cannot
 * drift apart: the server has to be told the stream stopped or it keeps an encoder alive, the
 * social layer has to be told the item closed or presence stays on "watching" until the process
 * dies, and the launcher's own music has to be given the audio output back.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val engine: PlayerEngine,
    private val apiClient: JellyfinApiClient,
    private val negotiator: PlaybackNegotiator,
    private val itemLoader: PlayerItemLoader,
    private val reporter: PlayerSessionReporter,
    private val mediaRepository: MediaRepository,
    private val playbackTracker: MediaPlaybackTracker,
    private val ambientAudioManager: AmbientAudioManager,
    private val playSessionTracker: PlaySessionTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val eventChannel = Channel<PlayerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val chrome = PlayerChromeDelegate(_uiState, viewModelScope)

    val tracks = PlayerTrackDelegate(
        state = _uiState,
        engine = engine,
        playerOf = { _player.value },
        playbackOf = { currentPlayback },
        reload = { audio, subtitle -> reload(audio, subtitle) },
        closeOverlay = { chrome.closeOverlay() }
    )

    private var initialized = false
    private var skipSegments: List<PlayerSkipSegment> = emptyList()
    private var currentPlayback: NegotiatedPlayback? = null
    private var transcodeOffsetMs: Long = 0
    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaSessionOpen = false
    private var interruptedAtMs: Long? = null
    private var wasPlayingBeforeInterrupt = true
    private var authorizationHeader: String? = null

    private var startJob: Job? = null
    private var positionJob: Job? = null
    private var scrubCommitJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = onTransportChanged(isPlaying)

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _uiState.update { it.copy(isBuffering = true) }
                Player.STATE_READY -> _uiState.update {
                    it.copy(isBuffering = false, isLoading = false)
                }
                Player.STATE_ENDED -> onPlaybackCompleted()
                else -> Unit
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            this@PlayerViewModel.tracks.applySelections()
        }

        override fun onPlayerError(error: PlaybackException) {
            Logger.error(TAG, "playback failed", error)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isBuffering = false,
                    errorMessage = "This title could not be played"
                )
            }
            endMediaSession(currentItemPositionMs())
        }
    }

    /**
     * Opens an item, including into a player that is already showing a different one.
     *
     * A second title arriving is a second playback in the same window, so the first one is closed
     * down its normal path - which is what tells the server its stream ended - and everything the
     * first title left behind is cleared before the second is described. Re-arriving with the same
     * item id is a no-op, so a relaunch of what is already playing does not restart it.
     */
    fun initialize(args: PlayerArgs) {
        if (initialized) {
            if (args.itemId == _uiState.value.itemId) return
            closeCurrentItem()
        } else {
            observeGameSessions()
        }
        initialized = true
        _uiState.value = PlayerUiState(
            itemId = args.itemId,
            title = args.title,
            subtitle = args.subtitle
        )
        startJob = viewModelScope.launch {
            authorizationHeader = engine.authorizationHeader()
            val burnIn = negotiator.readPreferences().burnInImageSubtitles
            val detail = itemLoader.load(args.itemId)
            val resumeMs = resolveResumePosition(args.itemId, detail.serverResumeMs, detail.runtimeMs)
            skipSegments = detail.skipSegments
            _uiState.update {
                it.copy(
                    title = detail.title.ifBlank { it.title },
                    subtitle = detail.subtitle.ifBlank { it.subtitle },
                    durationMs = detail.runtimeMs,
                    chapters = detail.chapters,
                    trickplay = detail.trickplay,
                    trickplayAuthHeader = authorizationHeader,
                    burnInImageSubtitles = burnIn
                )
            }
            if (args.startPositionMs >= 0) startPlayback(args.startPositionMs) else startPlayback(resumeMs)
        }
    }

    private fun closeCurrentItem() {
        startJob?.cancel()
        endMediaSession(currentItemPositionMs())
        chrome.cancelTimer()
        skipSegments = emptyList()
        currentPlayback = null
        transcodeOffsetMs = 0
        interruptedAtMs = null
        wasPlayingBeforeInterrupt = true
        _player.value?.let {
            it.playWhenReady = false
            it.clearMediaItems()
        }
    }

    /**
     * The position the item resumes from. The local row is consulted alongside the server's because
     * a position written while offline is the only copy that exists, and the later of the two is the
     * one the viewer actually reached. The local value is in the server's own tick unit, matching
     * how it is stored.
     *
     * A position within the closing seconds is treated as no position at all: resuming there shows
     * the credits and nothing else, which is never what was wanted.
     */
    private suspend fun resolveResumePosition(
        itemId: String,
        serverResumeMs: Long,
        runtimeMs: Long
    ): Long {
        val localTicks = runCatching { mediaRepository.resumePositionFor(itemId) }.getOrNull() ?: 0L
        val resume = maxOf(serverResumeMs, localTicks / TICKS_PER_MILLISECOND).coerceAtLeast(0)
        if (runtimeMs > 0 && resume >= runtimeMs - NEAR_END_MS) return 0
        return resume
    }

    /**
     * Opens one playback and reports it.
     *
     * Every entry into playback comes through here, including a seek on a transcoded stream and a
     * track change the server has to re-encode for, because each of those is a new session as far as
     * the server is concerned. The previous session is stopped first, so a reload never leaves an
     * encoder behind.
     */
    private suspend fun startPlayback(
        startPositionMs: Long,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null
    ) {
        val state = _uiState.value
        if (state.itemId.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        ambientAudioManager.holdVideoSilence()

        val negotiation = negotiator.negotiate(
            itemId = state.itemId,
            startPositionMs = startPositionMs,
            burnInImageSubtitles = state.burnInImageSubtitles,
            audioStreamIndex = audioStreamIndex ?: state.selectedAudioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex ?: state.selectedSubtitleStreamIndex,
            mediaSourceId = currentPlayback?.mediaSourceId
        )

        when (negotiation) {
            is PlaybackNegotiation.Failed -> {
                _uiState.update { it.copy(isLoading = false, errorMessage = negotiation.message) }
                endMediaSession(startPositionMs)
            }
            is PlaybackNegotiation.Ready -> open(negotiation.playback, startPositionMs)
        }
    }

    /**
     * What to say when a title with a downloaded copy is streaming anyway. Silence would hide a
     * choice the viewer would make differently: one of the two reasons is fixed by reconnecting the
     * storage, the other by downloading the title again.
     */
    private fun streamingFallbackNotice(playback: NegotiatedPlayback): String? = when {
        playback.isLocalFile -> null
        playback.localCopy == MediaAvailability.UNAVAILABLE ->
            "Streaming - your download is on storage that is not connected"
        playback.localCopy == MediaAvailability.ABSENT ->
            "Streaming - your download is no longer on this device"
        else -> null
    }

    private fun reload(
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        startPositionMs: Long = -1
    ) {
        val from = if (startPositionMs >= 0) startPositionMs else currentItemPositionMs()
        startJob?.cancel()
        startJob = viewModelScope.launch {
            startPlayback(from, audioStreamIndex, subtitleStreamIndex)
        }
    }

    /**
     * A transcoded stream begins at the position it was negotiated for, so its own timeline starts
     * at zero and the item position is that offset plus whatever the player reports. Seeking inside
     * it would land at twice the intended point.
     *
     * A file off the disk is opened without a credential and reported to nobody. There is no play
     * session behind it to keep alive, no encoder to free, and a viewer with no server in reach is
     * the case downloading exists for - a report attempted here would fail and a report deferred
     * would describe a session the server never opened.
     */
    private fun open(playback: NegotiatedPlayback, startPositionMs: Long) {
        val header = authorizationHeader
        if (header == null && !playback.isLocalFile) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Not signed in to Jellyfin") }
            endMediaSession(startPositionMs)
            return
        }
        currentPlayback = playback
        transcodeOffsetMs = if (playback.isTranscode) startPositionMs else 0
        tracks.resetForPlayback()

        val activePlayer = _player.value ?: engine.createPlayer(header, playerListener).also {
            _player.value = it
        }

        activePlayer.setMediaItem(
            engine.buildMediaItem(playback),
            if (playback.isTranscode) 0L else startPositionMs
        )
        activePlayer.prepare()
        activePlayer.playWhenReady = true

        _uiState.update {
            it.copy(
                audioTracks = playback.audioTracks,
                subtitleTracks = playback.subtitleTracks,
                selectedAudioStreamIndex = playback.audioStreamIndex,
                selectedSubtitleStreamIndex = playback.subtitleStreamIndex,
                durationMs = if (it.durationMs > 0) it.durationMs else playback.runtimeMs,
                positionMs = startPositionMs,
                scrubTargetMs = null,
                isLocalPlayback = playback.isLocalFile,
                playbackNotice = streamingFallbackNotice(playback),
                errorMessage = null
            )
        }

        if (playback.isLocalFile) {
            reporter.stop(startPositionMs)
        } else {
            reporter.start(
                itemId = playback.itemId,
                mediaSourceId = playback.mediaSourceId,
                playSessionId = playback.playSessionId,
                playMethod = playback.playMethod,
                positionMs = startPositionMs,
                audioStreamIndex = playback.audioStreamIndex,
                subtitleStreamIndex = playback.subtitleStreamIndex
            )
        }
        playbackTracker.onPlaybackStarted(playback.itemId, _uiState.value.title)
        mediaSessionOpen = true
        startPositionTicker()
        chrome.restartTimer()
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                delay(POSITION_TICK_MS)
                val activePlayer = _player.value ?: continue
                val position = transcodeOffsetMs + activePlayer.currentPosition
                reporter.setPosition(position, !activePlayer.isPlaying)
                _uiState.update { it.copy(positionMs = position, activeSkip = skipAt(position)) }
            }
        }
    }

    private fun skipAt(positionMs: Long): PlayerSkipSegment? =
        skipSegments.firstOrNull { positionMs in it.startMs until it.endMs }

    private fun onTransportChanged(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
        playbackTracker.onPlaybackStateChanged(_uiState.value.itemId, isPlaying)
        reporter.reportTransport(currentItemPositionMs(), !isPlaying)
        if (isPlaying) chrome.restartTimer() else chrome.show()
    }

    private fun onPlaybackCompleted() {
        val duration = _uiState.value.durationMs
        endMediaSession(if (duration > 0) duration else currentItemPositionMs())
        eventChannel.trySend(PlayerEvent.Finish)
    }

    /**
     * The single place a playback stops being a playback.
     *
     * Idempotent, because it is reached from paths that do not know about each other: the user
     * leaving, a game claiming the screen, the item ending, a fatal player error and the activity
     * being torn down. Each of them individually must leave no encoder running server-side and no
     * stale "watching" presence behind.
     */
    private fun endMediaSession(finalPositionMs: Long) {
        ambientAudioManager.releaseVideoSilence()
        if (!mediaSessionOpen) return
        mediaSessionOpen = false
        positionJob?.cancel()
        positionJob = null
        reporter.stop(finalPositionMs)
        recordLocalPosition(finalPositionMs)
        playbackTracker.onPlaybackEnded(_uiState.value.itemId)
    }

    /**
     * The local write is what survives an offline session, so it cannot ride on [viewModelScope] --
     * teardown is exactly when it would be cancelled.
     */
    private fun recordLocalPosition(finalPositionMs: Long) {
        val state = _uiState.value
        val itemId = state.itemId.ifEmpty { return }
        val durationMs = state.durationMs
        val played = durationMs > 0 && finalPositionMs >= durationMs - NEAR_END_MS
        val percentage = if (durationMs > 0) {
            finalPositionMs.toDouble() / durationMs.toDouble() * 100.0
        } else {
            null
        }
        detachedScope.launch {
            runCatching {
                mediaRepository.recordPosition(
                    itemId = itemId,
                    positionTicks = if (played) 0L else finalPositionMs * TICKS_PER_MILLISECOND,
                    playedPercentage = percentage,
                    played = played
                )
            }.onFailure { Logger.warn(TAG, "Local position write failed for $itemId", it) }
        }
    }

    /**
     * A game claiming the screen ends the viewing, it does not merely hide it. The video pauses, the
     * position is kept, the server is told to free its encoder, and the item becomes a Continue
     * Watching entry rather than a session that is still notionally open.
     *
     * On a device with a second screen the viewing then carries on there, which is why the position
     * is announced alongside the suspension rather than only written away: the window decides
     * whether a move is possible and needs the position to open with.
     */
    private fun observeGameSessions() {
        viewModelScope.launch {
            playSessionTracker.hasActiveSession.collect { active ->
                if (!active || !mediaSessionOpen) return@collect
                val itemId = _uiState.value.itemId
                val position = currentItemPositionMs()
                suspendForInterruption()
                eventChannel.trySend(PlayerEvent.Relocate(itemId, position))
            }
        }
    }

    fun onEnteredBackground() {
        if (!mediaSessionOpen) return
        suspendForInterruption()
    }

    private fun suspendForInterruption() {
        val position = currentItemPositionMs()
        wasPlayingBeforeInterrupt = _uiState.value.isPlaying
        _player.value?.playWhenReady = false
        interruptedAtMs = position
        endMediaSession(position)
        Logger.info(TAG, "playback suspended at ${position}ms")
    }

    fun onEnteredForeground() {
        val resumeFrom = interruptedAtMs ?: return
        interruptedAtMs = null
        startJob?.cancel()
        startJob = viewModelScope.launch {
            startPlayback(resumeFrom)
            if (!wasPlayingBeforeInterrupt) _player.value?.playWhenReady = false
        }
    }

    /**
     * The scrub preview for a position, or nothing when the server has no thumbnails for this item.
     */
    fun trickplayTile(positionMs: Long): TrickplayTile? {
        val state = _uiState.value
        val trickplay = state.trickplay ?: return null
        if (state.itemId.isBlank()) return null
        return trickplayTileFor(
            trickplay = trickplay,
            url = { sheet ->
                apiClient.buildTrickplayTileUrl(
                    itemId = state.itemId,
                    width = trickplay.thumbnailWidth,
                    index = sheet,
                    mediaSourceId = trickplay.mediaSourceId
                )
            },
            positionMs = positionMs
        )
    }

    fun currentItemPositionMs(): Long {
        val activePlayer = _player.value ?: return _uiState.value.positionMs
        return transcodeOffsetMs + activePlayer.currentPosition
    }

    fun togglePlayPause() {
        val activePlayer = _player.value ?: return
        activePlayer.playWhenReady = !activePlayer.playWhenReady
        chrome.show()
    }

    /**
     * A seek on a transcoded stream is a new negotiation, because the encoder can only start where
     * it was told to start. The stall that follows is the server restarting it and is not something
     * the client can hide.
     */
    fun seekTo(targetMs: Long) {
        val duration = _uiState.value.durationMs
        val clamped = targetMs.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        _uiState.update { it.copy(scrubTargetMs = null, positionMs = clamped) }
        if (currentPlayback?.isTranscode == true) {
            reload(startPositionMs = clamped)
        } else {
            _player.value?.seekTo(clamped)
        }
        chrome.restartTimer()
    }

    fun nudgeScrub(direction: Int) {
        val state = _uiState.value
        val duration = state.durationMs
        val target = ((state.scrubTargetMs ?: state.positionMs) + direction * SCRUB_STEP_MS)
            .coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        _uiState.update { it.copy(scrubTargetMs = target) }
        chrome.show()
        scrubCommitJob?.cancel()
        scrubCommitJob = viewModelScope.launch {
            delay(SCRUB_COMMIT_DELAY_MS)
            seekTo(target)
        }
    }

    fun scrubToFraction(fraction: Float) {
        val duration = _uiState.value.durationMs
        if (duration <= 0) return
        seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun skipActiveSegment() {
        val segment = _uiState.value.activeSkip ?: return
        seekTo(segment.endMs)
    }

    fun jumpChapter(direction: Int) {
        val state = _uiState.value
        if (state.chapters.isEmpty()) return
        val target = if (direction > 0) {
            state.chapters.firstOrNull { it.startMs > state.positionMs + CHAPTER_BACK_GRACE_MS }
        } else {
            state.chapters.lastOrNull { it.startMs < state.positionMs - CHAPTER_BACK_GRACE_MS }
        } ?: return
        seekTo(target.startMs)
    }

    fun playChapter(index: Int) {
        val chapter = _uiState.value.chapters.getOrNull(index) ?: return
        chrome.closeOverlay()
        seekTo(chapter.startMs)
    }

    /**
     * Commits whatever the open overlay has selected. Kept in one place so the gamepad path and the
     * touch path cannot drift into meaning different things on the same row.
     */
    fun confirmOverlaySelection() {
        val state = _uiState.value
        when (state.overlay) {
            PlayerOverlay.AUDIO_TRACKS -> tracks.selectAudioTrack(state.overlayIndex)
            PlayerOverlay.SUBTITLE_TRACKS -> if (state.overlayIndex == state.burnInRowIndex) {
                tracks.toggleBurnIn()
            } else {
                tracks.selectSubtitleTrack(state.overlayIndex - 1)
            }
            PlayerOverlay.CHAPTERS -> playChapter(state.overlayIndex)
            PlayerOverlay.NONE -> Unit
        }
    }

    fun requestExit() {
        endMediaSession(currentItemPositionMs())
        eventChannel.trySend(PlayerEvent.Finish)
    }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null) }
        reload(startPositionMs = _uiState.value.positionMs)
    }

    override fun onCleared() {
        endMediaSession(currentItemPositionMs())
        reporter.release()
        chrome.cancelTimer()
        _player.value?.let {
            it.removeListener(playerListener)
            it.release()
        }
        _player.value = null
        super.onCleared()
    }
}
