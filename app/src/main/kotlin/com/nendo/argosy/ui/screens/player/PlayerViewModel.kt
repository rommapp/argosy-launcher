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
import com.nendo.argosy.ui.screens.player.delegates.PlayerQualityDelegate
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
private const val NEAR_END_MS = 15_000L
private const val NEAR_END_PERCENT = 95.0
private const val MIN_COMPLETED_PROGRESS_MS = 10_000L
private const val AUTOPLAY_COUNTDOWN_SECONDS = 10
private const val FULLY_PLAYED_PERCENT = 100.0
private const val HALF_VOLUME_FACTOR = 0.5f

/**
 * How close two Back presses must land to count as the deliberate double-press that releases a
 * locked player from the pad, for the windows where a double-tap is not available because the
 * device has no touch.
 */
private const val LOCK_RELEASE_DOUBLE_PRESS_MS = 400L

/**
 * The exit confirmation's two buttons, in the confirm modal's own order: cancel at index zero,
 * the committing action last.
 */
private const val EXIT_CONFIRM_LEAVE_INDEX = 1

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

    val quality = PlayerQualityDelegate(
        state = _uiState,
        openOverlay = { chrome.openOverlay(it) },
        closeOverlay = { chrome.closeOverlay() },
        reload = { reload() }
    )

    private var initialized = false
    private var hostDisplayId: Int? = null
    private var skipSegments: List<PlayerSkipSegment> = emptyList()
    private var currentPlayback: NegotiatedPlayback? = null
    private var transcodeOffsetMs: Long = 0
    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaSessionOpen = false
    private var playbackStartPositionMs = 0L
    private var completionHandled = false
    private var interruptedAtMs: Long? = null
    private var wasPlayingBeforeInterrupt = true
    private var authorizationHeader: String? = null

    /**
     * A watch state the viewer set by hand, which outranks the one the closing position implies.
     * Without it, marking a film watched half way through would be undone by the write that records
     * where it was left.
     */
    private var watchedOverride: Boolean? = null

    /**
     * The player's own volume when playback first opened, which is what "full" restores. Captured
     * once so cycling to mute and back lands the viewer where they started rather than at the
     * stream's maximum.
     */
    private var openingVolume: Float? = null

    private var lastLockedBackPressMs = 0L

    private var startJob: Job? = null
    private var positionJob: Job? = null
    private var autoplayJob: Job? = null
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
            unlockControls()
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
        val carried = _uiState.value
        _uiState.value = PlayerUiState(
            itemId = args.itemId,
            title = args.title,
            subtitle = args.subtitle,
            videoScale = carried.videoScale,
            volumeStep = carried.volumeStep
        )
        startJob = viewModelScope.launch {
            authorizationHeader = engine.authorizationHeader()
            val prefs = negotiator.readPreferences()
            val detail = itemLoader.load(args.itemId)
            val resumeMs = resolveResumePosition(args.itemId, detail)
            skipSegments = detail.skipSegments
            _uiState.update {
                it.copy(
                    title = detail.title.ifBlank { it.title },
                    subtitle = detail.subtitle.ifBlank { it.subtitle },
                    durationMs = detail.runtimeMs,
                    chapters = detail.chapters,
                    trickplay = detail.trickplay,
                    trickplayAuthHeader = authorizationHeader,
                    burnInImageSubtitles = prefs.burnInImageSubtitles,
                    confirmPlayerExit = prefs.confirmPlayerExit,
                    isWatched = detail.isWatched,
                    isEpisode = detail.isEpisode,
                    nextEpisode = detail.nextEpisode,
                    previousEpisode = detail.previousEpisode,
                    defaultQuality = PlayerQualityCeilings(
                        maxHeight = prefs.streamingQuality.maxHeight,
                        maxBitrateKbps = prefs.streamingQuality.maxBitrateKbps
                    )
                )
            }
            if (args.startPositionMs >= 0) startPlayback(args.startPositionMs) else startPlayback(resumeMs)
        }
    }

    private fun closeCurrentItem() {
        startJob?.cancel()
        clearAutoplayCountdown()
        unlockControls()
        endMediaSession(currentItemPositionMs())
        chrome.cancelTimer()
        skipSegments = emptyList()
        currentPlayback = null
        transcodeOffsetMs = 0
        interruptedAtMs = null
        wasPlayingBeforeInterrupt = true
        watchedOverride = null
        _player.value?.let {
            it.playWhenReady = false
            it.clearMediaItems()
        }
    }

    /**
     * The position the item resumes from. A title the viewer has already finished starts from the
     * beginning outright: whatever position such an item still carries is end-of-file residue, and
     * honouring it plays the last instant and completes at once.
     *
     * For a part-watched title the local row is consulted alongside the server's because a position
     * written while offline is the only copy that exists, and the later of the two is the one the
     * viewer actually reached. The local value is in the server's own tick unit, matching how it is
     * stored.
     *
     * A position within the closing seconds is treated as no position at all: resuming there shows
     * the credits and nothing else, which is never what was wanted. When the runtime is not known
     * the closing seconds cannot be measured, so the stored played percentage stands in for them;
     * with neither signal the position is honoured, because wiping a real mid-file position costs
     * more than the rare stale one.
     */
    private suspend fun resolveResumePosition(itemId: String, detail: PlayerItemDetail): Long {
        if (detail.isWatched) return 0
        val localTicks = runCatching { mediaRepository.resumePositionFor(itemId) }.getOrNull() ?: 0L
        val resume = maxOf(detail.serverResumeMs, localTicks / TICKS_PER_MILLISECOND).coerceAtLeast(0)
        if (resume <= 0) return 0
        if (detail.runtimeMs > 0) {
            return if (resume >= detail.runtimeMs - NEAR_END_MS) 0 else resume
        }
        val percent = detail.playedPercent
        if (percent != null && percent >= NEAR_END_PERCENT) return 0
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
            mediaSourceId = currentPlayback?.mediaSourceId,
            qualityOverride = state.sessionQuality
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
     * A progressive transcode begins at the position it was negotiated for, so its own timeline
     * starts at zero and the item position is that offset plus whatever the player reports. Opening
     * it at the resume position instead would land at twice the intended point.
     *
     * Every other shape - an HLS transcode, a direct play, a file off the disk - is addressed in item
     * time, so it carries no offset and is opened at the position itself.
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
        transcodeOffsetMs = if (playback.startsAtNegotiatedOffset) startPositionMs else 0
        tracks.resetForPlayback()

        val activePlayer = _player.value
            ?: engine.createPlayer(header, playerListener, hostDisplayId).also {
                _player.value = it
            }
        if (openingVolume == null) openingVolume = activePlayer.volume
        applyVolumeStep(_uiState.value.volumeStep)

        activePlayer.setMediaItem(
            engine.buildMediaItem(playback),
            if (playback.startsAtNegotiatedOffset) 0L else startPositionMs
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
                sourceVideo = playback.sourceVideo,
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
        playbackStartPositionMs = startPositionMs
        completionHandled = false
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

    /**
     * Reaching the end marks the item played outright rather than leaving it to the near-end
     * heuristic, which cannot fire when the duration was never learned. Everything that resolves
     * "what plays next" reads that local flag, so a finished episode that stayed unplayed would be
     * offered again as if it had never been watched.
     *
     * Handled once per opened playback: an ended state can arrive again for the same item, and the
     * player also reports ended when a playlist is cleared to make way for the next one, which the
     * session guard filters out.
     *
     * An end with almost nothing played is not a finished viewing - it is a stale end-of-file
     * position that slipped past resume resolution, or a stream that died on arrival. It marks
     * nothing played and offers no next episode. When the playback began mid-file with no known
     * runtime it is retried once from the beginning, which is where such a playback should have
     * started; a playback that already began at zero has nothing left to retry and closes.
     */
    private fun onPlaybackCompleted() {
        if (completionHandled || !mediaSessionOpen) return
        completionHandled = true
        unlockControls()
        val startedFrom = playbackStartPositionMs
        val progressedMs = currentItemPositionMs() - startedFrom
        if (progressedMs < MIN_COMPLETED_PROGRESS_MS) {
            val runtimeUnknown = _uiState.value.durationMs <= 0
            endMediaSession(currentItemPositionMs())
            if (runtimeUnknown && startedFrom > 0) {
                reload(startPositionMs = 0)
            } else {
                eventChannel.trySend(PlayerEvent.Finish)
            }
            return
        }
        watchedOverride = true
        val duration = _uiState.value.durationMs
        endMediaSession(if (duration > 0) duration else currentItemPositionMs())
        if (_uiState.value.nextEpisode == null) {
            eventChannel.trySend(PlayerEvent.Finish)
            return
        }
        startAutoplayCountdown()
    }

    /**
     * Offers the next episode rather than starting it, and closes if nobody answers.
     *
     * A countdown is the only part of this that is a choice: rolling straight on is how a season
     * plays itself out to an empty room, and stopping dead every time is how a viewer ends up
     * reaching for the pad between every episode. The window is short enough to ignore and long
     * enough to refuse.
     */
    private fun startAutoplayCountdown() {
        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch {
            for (remaining in AUTOPLAY_COUNTDOWN_SECONDS downTo 1) {
                _uiState.update { it.copy(autoplayCountdownSeconds = remaining) }
                kotlinx.coroutines.delay(1000)
            }
            clearAutoplayCountdown()
            playNextEpisode()
        }
    }

    private fun clearAutoplayCountdown() {
        autoplayJob?.cancel()
        autoplayJob = null
        _uiState.update { it.copy(autoplayCountdownSeconds = null) }
    }

    /**
     * Declines the next episode. The item is over, so declining closes the window.
     */
    fun cancelAutoplay() {
        clearAutoplayCountdown()
        eventChannel.trySend(PlayerEvent.Finish)
    }

    /**
     * Takes the next episode now rather than waiting the countdown out.
     */
    fun confirmAutoplay() {
        clearAutoplayCountdown()
        playNextEpisode()
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
        val played = watchedOverride ?: (durationMs > 0 && finalPositionMs >= durationMs - NEAR_END_MS)
        val percentage = when {
            played -> FULLY_PLAYED_PERCENT
            durationMs > 0 -> finalPositionMs.toDouble() / durationMs.toDouble() * 100.0
            else -> null
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
        clearAutoplayCountdown()
        unlockControls()
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
     * Which physical display the hosting window is on, reported by the window itself. The player
     * is built from a context tied to this display, because that is what firmwares with a volume
     * per display bind the audio against - and the binding is fixed when the audio track is
     * created, so a window that moved displays cannot keep its player. A move with a playback open
     * therefore rebuilds the player and reopens the item where it was, exactly like a return from
     * the background; the first report of a fresh window rebuilds nothing.
     */
    fun onHostDisplayChanged(displayId: Int) {
        if (hostDisplayId == displayId) return
        val playerNeedsRebuild = hostDisplayId != null && _player.value != null
        hostDisplayId = displayId
        if (playerNeedsRebuild) rebuildPlayerForNewDisplay()
    }

    private fun rebuildPlayerForNewDisplay() {
        val activePlayer = _player.value ?: return
        val position = currentItemPositionMs()
        val wasPlaying = activePlayer.playWhenReady
        activePlayer.removeListener(playerListener)
        activePlayer.release()
        _player.value = null
        if (!mediaSessionOpen) return
        startJob?.cancel()
        startJob = viewModelScope.launch {
            startPlayback(position)
            if (!wasPlaying) _player.value?.playWhenReady = false
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
     *
     * A seek also withdraws a pending next-episode offer and re-arms completion: moving the
     * position away from the end means the viewer is watching this item again, and reaching the
     * end after that is a fresh completion.
     */
    fun seekTo(targetMs: Long) {
        scrubCommitJob?.cancel()
        scrubCommitJob = null
        clearAutoplayCountdown()
        completionHandled = false
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
        val target = ((state.scrubTargetMs ?: state.positionMs) + direction * PLAYER_SEEK_STEP_MS)
            .coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        _uiState.update { it.copy(scrubTargetMs = target) }
        chrome.show()
        scrubCommitJob?.cancel()
        scrubCommitJob = viewModelScope.launch {
            delay(SCRUB_COMMIT_DELAY_MS)
            scrubCommitJob = null
            seekTo(target)
        }
    }

    /**
     * Moves by a fixed step and commits at once, which is what separates the two seek mechanisms: the
     * scrubber walks a preview and waits to see whether the viewer is still moving, while a press on
     * the transport is already the decision.
     */
    fun skipBy(direction: Int) {
        seekTo(currentItemPositionMs() + direction * PLAYER_SEEK_STEP_MS)
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

    /**
     * The trigger seek, a coarser step than the shoulder skip. The triggers reach the player as one
     * event per pull rather than a held stream - the axis emitter fires once when the pull crosses
     * its threshold - so covering ground comes from the step size, not from repeat.
     */
    fun shuttleBy(direction: Int) {
        seekTo(currentItemPositionMs() + direction * PLAYER_SHUTTLE_STEP_MS)
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
            PlayerOverlay.QUALITY -> quality.applyDraft()
            PlayerOverlay.NONE -> Unit
        }
    }

    /**
     * Marks the item watched, or takes that back. The write lands locally and is queued for the
     * server, so it holds with nothing in reach; the chrome reads the state it just set rather than
     * waiting on the round trip.
     *
     * It runs off the view model's own scope because the press is very often the last thing that
     * happens before the viewer leaves, and teardown would cancel it.
     */
    fun toggleWatched() {
        val itemId = _uiState.value.itemId.ifEmpty { return }
        val watched = !_uiState.value.isWatched
        watchedOverride = watched
        _uiState.update { it.copy(isWatched = watched) }
        chrome.show()
        detachedScope.launch {
            runCatching { mediaRepository.setPlayed(itemId, watched) }
                .onFailure { Logger.warn(TAG, "Watch state write failed for $itemId", it) }
        }
    }

    /**
     * Flips the picture between fitting inside the screen and filling it. Session-only: it lives in
     * the ui state, carries across episodes in the same window, and resets with the window, because
     * whether a crop is acceptable is a property of the title rather than of the device.
     */
    fun toggleVideoScale() {
        _uiState.update {
            it.copy(
                videoScale = if (it.videoScale == PlayerVideoScale.FIT) PlayerVideoScale.FILL
                else PlayerVideoScale.FIT
            )
        }
        chrome.show()
    }

    /**
     * Walks the volume one stop around full, half and mute. It drives the player's own gain rather
     * than the device stream, so the launcher's sounds and every other app keep their level, and
     * "full" is the level playback opened with.
     */
    fun cycleVolume() {
        val next = when (_uiState.value.volumeStep) {
            PlayerVolumeStep.FULL -> PlayerVolumeStep.HALF
            PlayerVolumeStep.HALF -> PlayerVolumeStep.MUTE
            PlayerVolumeStep.MUTE -> PlayerVolumeStep.FULL
        }
        _uiState.update { it.copy(volumeStep = next) }
        applyVolumeStep(next)
        chrome.show()
    }

    private fun applyVolumeStep(step: PlayerVolumeStep) {
        val activePlayer = _player.value ?: return
        val full = openingVolume ?: activePlayer.volume
        activePlayer.volume = when (step) {
            PlayerVolumeStep.FULL -> full
            PlayerVolumeStep.HALF -> full * HALF_VOLUME_FACTOR
            PlayerVolumeStep.MUTE -> 0f
        }
    }

    /**
     * Hides the chrome and keeps it hidden: while the lock holds, the chrome delegate refuses to
     * show and the input handler swallows the pad, so neither a wake press nor a transport event
     * can bring the controls back over the film.
     */
    fun lockControls() {
        _uiState.update { it.copy(controlsLocked = true) }
        chrome.hide()
    }

    /**
     * Releases the lock and brings the chrome back so the release is visible. Also called by every
     * path that ends or interrupts the viewing, so a locked player can never outlive the playback
     * it was locking.
     */
    fun unlockControls() {
        if (!_uiState.value.controlsLocked) return
        lastLockedBackPressMs = 0L
        _uiState.update { it.copy(controlsLocked = false) }
        chrome.show()
    }

    /**
     * The pad's release gesture while locked: two Back presses inside the double-press window. A
     * single press is swallowed like every other key, which is what keeps an accidental press from
     * waking the chrome, while still leaving a touchless device a way out.
     */
    fun registerLockedBackPress() {
        val now = System.currentTimeMillis()
        if (now - lastLockedBackPressMs <= LOCK_RELEASE_DOUBLE_PRESS_MS) {
            unlockControls()
        } else {
            lastLockedBackPressMs = now
        }
    }

    /**
     * Moves the window on to the following episode. It is the same path a second title arriving from
     * anywhere else takes, so the current one is closed down properly and the server is told its
     * stream ended before the next is negotiated.
     */
    fun playNextEpisode() {
        val state = _uiState.value
        val next = state.nextEpisode ?: return
        initialize(PlayerArgs(itemId = next.itemId, title = state.title, subtitle = next.label))
    }

    /**
     * Moves the window back to the episode before this one, down the same path the next episode
     * takes. With no previous episode resolved the press does nothing, which is what the disabled
     * button already promised.
     */
    fun playPreviousEpisode() {
        val state = _uiState.value
        val previous = state.previousEpisode ?: return
        initialize(PlayerArgs(itemId = previous.itemId, title = state.title, subtitle = previous.label))
    }

    /**
     * What one transport button does. Both ways of pressing it land here so a touch and a gamepad
     * confirm cannot come to mean different things on the same button.
     */
    fun activateControl(control: PlayerControl?) {
        when (control) {
            PlayerControl.PREVIOUS_EPISODE -> playPreviousEpisode()
            PlayerControl.SKIP_BACK -> skipBy(-1)
            PlayerControl.PLAY_PAUSE -> togglePlayPause()
            PlayerControl.SKIP_FORWARD -> skipBy(1)
            PlayerControl.AUDIO -> chrome.openOverlay(PlayerOverlay.AUDIO_TRACKS)
            PlayerControl.SUBTITLES -> chrome.openOverlay(PlayerOverlay.SUBTITLE_TRACKS)
            PlayerControl.CHAPTERS -> chrome.openOverlay(PlayerOverlay.CHAPTERS)
            PlayerControl.QUALITY -> quality.openWheels()
            PlayerControl.FIT_FILL -> toggleVideoScale()
            PlayerControl.VOLUME -> cycleVolume()
            PlayerControl.LOCK -> lockControls()
            PlayerControl.NEXT_EPISODE -> playNextEpisode()
            PlayerControl.MARK_WATCHED -> toggleWatched()
            PlayerControl.CLOSE -> requestExit()
            PlayerControl.SKIP -> skipActiveSegment()
            null -> Unit
        }
    }

    /**
     * A deliberate ask to leave - the B ladder's last rung, the Close button, and the error
     * panel's Close. Only these are ever gated behind the exit confirmation: the closures that
     * happen with nobody at the pad (a declined or absent next episode, a dead-on-arrival stream)
     * send [PlayerEvent.Finish] directly and never see the prompt. An ask that arrives during the
     * autoplay countdown also skips the prompt, because the item is already over and the countdown
     * is itself the standing question.
     */
    fun requestExit() {
        val current = _uiState.value
        if (current.confirmPlayerExit && current.autoplayCountdownSeconds == null) {
            chrome.cancelTimer()
            _uiState.update { it.copy(showExitConfirm = true, exitConfirmIndex = 0) }
            return
        }
        performExit()
    }

    fun moveExitConfirmFocus(delta: Int) {
        _uiState.update {
            it.copy(
                exitConfirmIndex = (it.exitConfirmIndex + delta)
                    .coerceIn(0, EXIT_CONFIRM_LEAVE_INDEX)
            )
        }
    }

    fun confirmExitSelection() {
        if (_uiState.value.exitConfirmIndex == EXIT_CONFIRM_LEAVE_INDEX) {
            confirmExit()
        } else {
            dismissExitConfirm()
        }
    }

    fun confirmExit() {
        _uiState.update { it.copy(showExitConfirm = false, exitConfirmIndex = 0) }
        performExit()
    }

    fun dismissExitConfirm() {
        _uiState.update { it.copy(showExitConfirm = false, exitConfirmIndex = 0) }
        chrome.restartTimer()
    }

    private fun performExit() {
        clearAutoplayCountdown()
        unlockControls()
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
