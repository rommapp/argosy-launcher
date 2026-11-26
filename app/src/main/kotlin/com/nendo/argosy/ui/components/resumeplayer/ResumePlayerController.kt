package com.nendo.argosy.ui.components.resumeplayer

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.audio.NowPlayingPillPolicy
import com.nendo.argosy.ui.components.musicplayer.TrackListCursor
import com.nendo.argosy.ui.components.musicplayer.followPlayingFocus
import com.nendo.argosy.ui.components.musicplayer.trackListEntryFocus
import com.nendo.argosy.ui.components.nowplaying.resolvedDurationMs
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Resume page's player card and running-order panel: card focus, the panel and its cursor,
 * the panel's idle collapse, and the status pill suppression while the card shows. Hosts call
 * [onVisible] and [onHidden] with their own visibility and [setHasGames] after each refresh.
 */
class ResumePlayerController(
    private val scope: CoroutineScope,
    private val transport: ResumePlayerTransport,
    private val pillPolicy: NowPlayingPillPolicy
) {
    private val _uiState = MutableStateFlow(ResumePlayerUiState(playback = transport.playback.value))
    val uiState: StateFlow<ResumePlayerUiState> = _uiState.asStateFlow()

    private var visible = false
    private var suppressed = false
    private var pollJob: Job? = null
    private var idleJob: Job? = null
    private var nowPlayingAction: (() -> Unit)? = null

    init {
        scope.launch { transport.playback.collect(::onPlayback) }
        scope.launch { transport.analysis.collect { analysis -> _uiState.update { it.copy(analysis = analysis) } } }
        scope.launch { transport.stage.collect { stage -> _uiState.update { it.copy(stage = stage) } } }
    }

    private fun onPlayback(playback: AmbientPlaybackState) {
        _uiState.update { state ->
            val last = (playback.tracks.size - 1).coerceAtLeast(0)
            val sourceChanged = playback.sourceId != state.playback.sourceId
            val followed = followPlayingFocus(
                focus = state.listFocus,
                previousPlaying = state.playback.index,
                playing = playback.index,
                listFocused = state.listFocused
            )
            state.copy(
                playback = playback,
                listFocus = (if (sourceChanged) playback.index else followed).coerceIn(0, last),
                cursor = if (sourceChanged) null else state.cursor
            ).reconciled()
        }
        syncIdleCollapse()
        syncPillSuppression()
    }

    fun onVisible() {
        visible = true
        _uiState.update { it.copy(focus = ResumePlayerFocus.PAGE, button = ResumePlayerButton.PLAY_PAUSE).reconciled() }
        if (pollJob?.isActive != true) {
            pollJob = scope.launch {
                while (isActive) {
                    samplePosition()
                    delay(ComponentDefaults.MusicPlayer.positionPollMs.toLong())
                }
            }
        }
        syncPillSuppression()
    }

    fun onHidden() {
        visible = false
        pollJob?.cancel()
        pollJob = null
        _uiState.update { state -> if (state.panelOpen) state.collapsed() else state }
        syncIdleCollapse()
        syncPillSuppression()
    }

    fun setHasGames(hasGames: Boolean) {
        _uiState.update { state ->
            if (state.hasGames == hasGames) return@update state
            val next = state.copy(hasGames = hasGames)
            if (hasGames && next.cardFocused) next.copy(focus = ResumePlayerFocus.PAGE) else next.reconciled()
        }
    }

    fun setOpenNowPlaying(action: (() -> Unit)?) {
        nowPlayingAction = action
        _uiState.update { it.copy(canOpenNowPlaying = action != null) }
    }

    fun enterCard(): Boolean {
        val state = _uiState.value
        if (!state.showsCard || state.focus != ResumePlayerFocus.PAGE) return false
        _uiState.update { it.copy(focus = ResumePlayerFocus.CARD, button = ResumePlayerButton.PLAY_PAUSE) }
        return true
    }

    fun leaveCard(): Boolean {
        val state = _uiState.value
        if (!state.cardFocused) return false
        if (state.panelOpen) {
            _uiState.update { it.copy(focus = ResumePlayerFocus.PANEL) }
            syncIdleCollapse()
            return true
        }
        if (!state.hasGames) return false
        _uiState.update { it.copy(focus = ResumePlayerFocus.PAGE) }
        return true
    }

    fun focusPage() {
        _uiState.update { state ->
            if (!state.hasGames || state.focus == ResumePlayerFocus.PAGE) return@update state
            (if (state.panelOpen) state.collapsed() else state).copy(focus = ResumePlayerFocus.PAGE)
        }
        syncIdleCollapse()
    }

    fun moveButton(delta: Int): Boolean {
        val state = _uiState.value
        if (!state.cardFocused) return false
        syncIdleCollapse(restart = true)
        val buttons = state.buttons
        val target = buttons.indexOf(state.button) + delta
        if (target !in buttons.indices) return false
        _uiState.update { it.copy(button = buttons[target]) }
        return true
    }

    fun confirmButton(): Boolean {
        val state = _uiState.value
        if (!state.cardFocused) return false
        activate(state.button)
        syncIdleCollapse(restart = true)
        return true
    }

    fun tapButton(button: ResumePlayerButton) {
        _uiState.update { it.copy(focus = ResumePlayerFocus.CARD, button = button) }
        activate(button)
        syncIdleCollapse(restart = true)
    }

    fun openNowPlaying() {
        nowPlayingAction?.invoke()
    }

    private fun activate(button: ResumePlayerButton) {
        when (button) {
            ResumePlayerButton.PREVIOUS -> scope.launch { transport.skipPrevious() }
            ResumePlayerButton.PLAY_PAUSE -> scope.launch { transport.togglePlayPause() }
            ResumePlayerButton.NEXT -> scope.launch { transport.skipNext() }
            ResumePlayerButton.QUEUE -> openPanel()
        }
    }

    fun openPanel(): Boolean {
        val state = _uiState.value
        if (!state.cardFocused || state.playback.tracks.isEmpty()) return false
        _uiState.update {
            it.copy(
                panelOpen = true,
                focus = ResumePlayerFocus.PANEL,
                listFocus = if (it.panelOpen) it.listFocus else it.entryFocus()
            )
        }
        syncIdleCollapse()
        return true
    }

    fun togglePanelFromTouch() {
        val state = _uiState.value
        if (state.panelOpen) {
            dismissPanel()
            return
        }
        if (!state.showsCard || state.playback.tracks.isEmpty()) return
        _uiState.update {
            it.copy(
                panelOpen = true,
                focus = ResumePlayerFocus.PANEL,
                button = if (it.cardFocused) it.button else ResumePlayerButton.PLAY_PAUSE,
                listFocus = it.entryFocus()
            )
        }
        syncIdleCollapse()
    }

    fun dismissPanel(): Boolean {
        if (!_uiState.value.panelOpen) return false
        _uiState.update { state -> if (state.panelOpen) state.collapsed() else state }
        syncIdleCollapse()
        return true
    }

    /**
     * Moves the list cursor. Up from the first row hands focus to the card's controls with the
     * panel still open, which arms the idle collapse.
     */
    fun moveList(delta: Int): Boolean {
        val state = _uiState.value
        if (!state.listFocused) return false
        val target = state.listFocus + delta
        if (target < 0) {
            _uiState.update { it.copy(focus = ResumePlayerFocus.CARD) }
            syncIdleCollapse(restart = true)
            return true
        }
        if (target !in state.playback.tracks.indices) return false
        _uiState.update { it.copy(listFocus = target) }
        return true
    }

    fun playFocused(): Boolean {
        val state = _uiState.value
        if (!state.listFocused) return false
        playRow(state.listFocus)
        return true
    }

    fun playRow(position: Int) {
        val state = _uiState.value
        if (!state.panelOpen || position !in state.playback.tracks.indices) return
        _uiState.update { it.copy(focus = ResumePlayerFocus.PANEL, listFocus = position) }
        syncIdleCollapse()
        scope.launch { transport.playAt(position) }
    }

    fun skip(delta: Int): Boolean {
        val state = _uiState.value
        if (!state.showsCard) return false
        syncIdleCollapse(restart = true)
        scope.launch { if (delta > 0) transport.skipNext() else transport.skipPrevious() }
        return true
    }

    fun onPanelTouched() {
        syncIdleCollapse(restart = true)
    }

    fun createPanelInputHandler(): InputHandler = object : InputHandler {
        private fun list(delta: Int): InputResult =
            if (moveList(delta)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        private fun held(): InputResult {
            onPanelTouched()
            return InputResult.handled(SoundType.SILENT)
        }

        private fun close(): InputResult {
            dismissPanel()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }

        override fun onUp(): InputResult = list(-1)
        override fun onDown(): InputResult = list(1)
        override fun onLeft(): InputResult = held()
        override fun onRight(): InputResult = held()

        override fun onConfirm(): InputResult {
            playFocused()
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult = close()
        override fun onContextMenu(): InputResult = close()

        override fun onPrevTrigger(): InputResult {
            skip(-1)
            return InputResult.HANDLED
        }

        override fun onNextTrigger(): InputResult {
            skip(1)
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult = held()
        override fun onSecondaryAction(): InputResult = held()
        override fun onPrevSection(): InputResult = held()
        override fun onNextSection(): InputResult = held()
        override fun onSelect(): InputResult = held()
        override fun onLeftStickClick(): InputResult = held()
        override fun onRightStickClick(): InputResult = held()
        override fun onRightStickUp(): InputResult = held()
        override fun onRightStickDown(): InputResult = held()
        override fun onRightStickLeft(): InputResult = held()
        override fun onRightStickRight(): InputResult = held()
        override fun onLongConfirm(): InputResult = held()
        override fun onLongSelect(): InputResult = held()
    }

    private fun syncIdleCollapse(restart: Boolean = false) {
        if (!_uiState.value.collapseArmed) {
            idleJob?.cancel()
            idleJob = null
            return
        }
        if (!restart && idleJob?.isActive == true) return
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(ComponentDefaults.ResumePlayer.panelIdleCollapseMs.toLong())
            dismissPanel()
        }
    }

    private fun ResumePlayerUiState.collapsed(): ResumePlayerUiState = copy(
        panelOpen = false,
        focus = if (focus == ResumePlayerFocus.PANEL) ResumePlayerFocus.CARD else focus,
        cursor = leavingCursor()
    )

    private fun samplePosition() {
        val position = transport.positionSnapshot()
        _uiState.update {
            it.copy(
                positionMs = position?.positionMs ?: 0L,
                durationMs = it.playback.resolvedDurationMs(position)
            )
        }
    }

    private fun syncPillSuppression() {
        val next = visible && _uiState.value.showsCard
        if (next == suppressed) return
        suppressed = next
        pillPolicy.setSuppressed(PILL_OWNER, next)
    }

    private fun ResumePlayerUiState.entryFocus(): Int = trackListEntryFocus(
        cursor = cursor,
        playingIndex = playback.index,
        sourceId = playback.sourceId,
        trackCount = playback.tracks.size
    )

    private fun ResumePlayerUiState.leavingCursor(): TrackListCursor =
        TrackListCursor(position = listFocus, playingIndex = playback.index, sourceId = playback.sourceId)

    private fun ResumePlayerUiState.reconciled(): ResumePlayerUiState = when {
        !showsCard -> if (focus == ResumePlayerFocus.PAGE && !panelOpen) {
            this
        } else {
            (if (panelOpen) collapsed() else this).copy(focus = ResumePlayerFocus.PAGE)
        }
        panelOpen && playback.tracks.isEmpty() -> collapsed()
        focus == ResumePlayerFocus.PAGE && !hasGames -> copy(
            focus = ResumePlayerFocus.CARD,
            button = ResumePlayerButton.PLAY_PAUSE
        )
        else -> this
    }

    class Factory @Inject constructor(
        private val transport: AmbientResumePlayerTransport,
        private val pillPolicy: NowPlayingPillPolicy
    ) {
        fun create(scope: CoroutineScope): ResumePlayerController = ResumePlayerController(scope, transport, pillPolicy)
    }

    private companion object {
        const val PILL_OWNER = "resume_card"
    }
}
