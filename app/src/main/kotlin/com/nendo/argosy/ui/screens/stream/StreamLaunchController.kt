package com.nendo.argosy.ui.screens.stream

import android.content.Context
import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMStreamJoinableSession
import com.nendo.argosy.data.remote.romm.RomMStreamRomAssets
import com.nendo.argosy.data.remote.romm.RomMStreamSave
import com.nendo.argosy.data.remote.romm.RomMStreamState
import com.nendo.argosy.data.remote.romm.RomMStreamStatePicks
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.streaming.StreamClaimOutcome
import com.nendo.argosy.data.streaming.StreamFirstRule
import com.nendo.argosy.data.streaming.StreamPicker
import com.nendo.argosy.data.streaming.StreamSessionManager
import com.nendo.argosy.data.streaming.StreamTarget
import com.nendo.argosy.data.streaming.StreamTriggerGate
import com.nendo.argosy.data.streaming.StreamingConfigStore
import com.nendo.argosy.data.sync.snapshot.SnapshotChannelService
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.screens.common.EmulatorLaunchTargetResolver
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One screen's "Stream on {container}" flow: the save and state picks, the claim, and the
 * occupied, memory card and refusal answers, ending in the stream player.
 */
class StreamLaunchController private constructor(
    private val deps: Factory,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<StreamLaunchUiState?>(null)
    val state: StateFlow<StreamLaunchUiState?> = _state.asStateFlow()

    val streamFirst: StateFlow<StreamFirstUi> = combine(
        deps.preferences.userPreferences.map { it.notOnDeviceAction }.distinctUntilChanged(),
        deps.configStore.availability
    ) { action, _ -> StreamFirstUi(action, deps.configStore.streamablePlatforms()) }
        .stateIn(scope, SharingStarted.Eagerly, StreamFirstUi())

    val offers: Flow<Unit> =
        combine(deps.configStore.availability, deps.sessions.session, streamFirst) { _, _, _ -> }

    private var loadJob: Job? = null
    private var target: StreamTarget? = null
    private var container: RomMStreamingContainer? = null
    private var saves: List<RomMStreamSave> = emptyList()
    private var states: List<RomMStreamState> = emptyList()
    private var joinable: RomMStreamJoinableSession? = null

    /**
     * The stream row for a game whose rom is not downloaded, or null when its platform has no
     * container and no stream of it is running. [isOnDevice] marks a partial copy, such as a paused
     * download, which keeps Download as the primary action.
     */
    fun offerFor(gameId: Long, platformSlug: String?, isOnDevice: Boolean = false): StreamOffer? {
        val leads = StreamFirstRule.leadsWithStream(streamFirst.value.action, isOnDevice, hasContainer = true)
        val live = deps.sessions.session.value?.takeIf { deps.sessions.isLive(gameId) }
        if (live != null) return StreamOffer(live.target.containerLabel, isLive = true, leads = leads)
        val found = deps.configStore.containerFor(platformSlug) ?: return null
        val label = StreamTriggerGate.labelOf(found) ?: return null
        return StreamOffer(label, isLive = false, leads = leads)
    }

    fun leadsWithStream(platformSlug: String?, isRommGame: Boolean, isOnDevice: Boolean): Boolean =
        streamFirst.value.leads(platformSlug, isRommGame, isOnDevice)

    fun open(gameId: Long) {
        if (deps.sessions.isLive(gameId)) {
            scope.launch { startPlayer(gameId) }
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val game = deps.gameRepository.getById(gameId) ?: return@launch
            val rommId = game.rommId?.takeIf { it > 0 } ?: return@launch
            val found = deps.configStore.containerFor(game.platformSlug) ?: return@launch
            container = found
            _state.value = StreamLaunchUiState(
                gameId = gameId,
                title = game.title,
                containerLabel = StreamTriggerGate.labelOf(found).orEmpty()
            )
            deps.soundManager.play(SoundType.OPEN_MODAL)
            loadChoices(gameId, rommId, game.title, found)
        }
    }

    private suspend fun loadChoices(gameId: Long, rommId: Long, title: String, found: RomMStreamingContainer) {
        val loaded = withContext(Dispatchers.IO) { fetch(gameId, rommId, found) }
        if (loaded == null) {
            showNotice(R.string.stream_launch_notice_unreachable, null)
            return
        }
        saves = loaded.assets.userSaves
        states = loaded.assets.allUserStates
        target = StreamSessionManager.targetOf(
            gameId = gameId,
            rommId = rommId,
            title = title,
            container = found,
            discs = StreamSessionManager.discsOf(loaded.assets.files)
        )
        val stateOptions = StreamPicker.pickableStates(states, loaded.picks)
        val defaultState = StreamPicker.defaultState(states, loaded.picks)
        val saveOptions = StreamPicker.pickableSaves(saves, found)
        val defaultSave = StreamPicker.defaultSave(saves, found, loaded.headSaveId)
        val stateUi = listOf(FRESH_START) + stateOptions.map { it.toOption() }
        _state.update { current ->
            current?.copy(
                step = StreamLaunchStep.Pick,
                states = stateUi,
                stateIndex = stateUi.indexOfFirst { it.id == defaultState?.id }.coerceAtLeast(0),
                saves = saveOptions.map { it.toOption() },
                saveIndex = saveOptions.indexOfFirst { it.id == defaultSave?.id }.coerceAtLeast(0),
                showsStatePicker = StreamPicker.supportsStates(found) && stateOptions.isNotEmpty(),
                showsSavePicker = StreamPicker.showsSavePicker(saves, found)
            )?.let { picked -> picked.copy(focusIndex = picked.rows.indexOf(StreamLaunchRow.START).coerceAtLeast(0)) }
        }
    }

    private class Loaded(val assets: RomMStreamRomAssets, val picks: RomMStreamStatePicks?, val headSaveId: Long?)

    private suspend fun fetch(gameId: Long, rommId: Long, found: RomMStreamingContainer): Loaded? {
        val api = deps.connectionManager.getApi() ?: return null
        return try {
            val assets = api.getStreamRomAssets(rommId).takeIf { it.isSuccessful }?.body() ?: return null
            val picks = if (StreamPicker.supportsStates(found)) {
                runCatching { api.getStreamStatePicks(rommId).takeIf { it.isSuccessful }?.body() }.getOrNull()
            } else {
                null
            }
            Loaded(assets, picks, activeChannelHead(gameId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "stream choices could not be read for rom $rommId: ${e.message}")
            null
        }
    }

    private suspend fun activeChannelHead(gameId: Long): Long? {
        if (!deps.connectionManager.getCapabilities().supportsSnapshots) return null
        val library = deps.snapshotChannels.load(gameId) ?: return null
        val channelId = library.deviceChannelId ?: return null
        return library.mine.firstOrNull { it.channel.id == channelId }?.channel?.current?.save?.id
    }

    fun moveFocus(delta: Int) {
        _state.update { current ->
            val rows = current?.rows ?: return@update current
            if (rows.isEmpty()) current else current.copy(focusIndex = (current.focusIndex + delta).mod(rows.size))
        }
    }

    fun focusRow(row: StreamLaunchRow) {
        _state.update { current ->
            val index = current?.rows?.indexOf(row) ?: -1
            if (current == null || index < 0) current else current.copy(focusIndex = index)
        }
    }

    fun cycle(row: StreamLaunchRow, delta: Int) {
        _state.update { current ->
            when {
                current == null -> null
                row == StreamLaunchRow.STATE && current.states.isNotEmpty() ->
                    current.copy(stateIndex = (current.stateIndex + delta).mod(current.states.size))
                row == StreamLaunchRow.SAVE && current.saves.isNotEmpty() ->
                    current.copy(saveIndex = (current.saveIndex + delta).mod(current.saves.size))
                else -> current
            }
        }
    }

    fun activate(row: StreamLaunchRow) {
        when (row) {
            StreamLaunchRow.STATE, StreamLaunchRow.SAVE -> Unit
            StreamLaunchRow.START -> claim(cardImport = null)
            StreamLaunchRow.IMPORT_CARD -> claim(cardImport = CARD_ADOPT)
            StreamLaunchRow.FRESH_CARD -> claim(cardImport = CARD_DISCARD)
            StreamLaunchRow.JOIN -> join()
            StreamLaunchRow.CANCEL, StreamLaunchRow.BACK, StreamLaunchRow.DISMISS -> dismiss()
        }
    }

    fun dismiss() {
        if (_state.value == null) return
        loadJob?.cancel()
        loadJob = null
        _state.value = null
        deps.soundManager.play(SoundType.CLOSE_MODAL)
    }

    private fun claim(cardImport: String?) {
        val current = _state.value ?: return
        val streamTarget = target ?: return
        val found = container ?: return
        val stateId = current.states.getOrNull(current.stateIndex)?.id?.takeIf { current.showsStatePicker }
        val chosenSave = current.saves.getOrNull(current.saveIndex)?.id
            ?.let { id -> saves.firstOrNull { it.id == id } }
        val saveId = StreamPicker.claimSaveId(saves, found, chosenSave)
        _state.update { it?.copy(step = StreamLaunchStep.Starting, focusIndex = 0) }
        loadJob = scope.launch {
            when (val outcome = deps.sessions.claim(streamTarget, stateId, saveId, cardImport)) {
                is StreamClaimOutcome.Launching -> {
                    _state.value = null
                    startPlayer(streamTarget.gameId)
                }
                is StreamClaimOutcome.Busy -> {
                    joinable = if (outcome.draining) null else deps.sessions.joinableFor(streamTarget.rommId)
                    _state.update {
                        it?.copy(
                            step = StreamLaunchStep.Busy(
                                romName = outcome.romName,
                                sinceMillis = outcome.claimedAt
                                    ?.let { SaveSyncApiClient.parseTimestampOrNull(it) }
                                    ?.toEpochMilli(),
                                draining = outcome.draining,
                                canJoin = joinable != null
                            ),
                            focusIndex = 0
                        )
                    }
                }
                is StreamClaimOutcome.CardDecision -> _state.update {
                    it?.copy(
                        step = StreamLaunchStep.CardDecision(
                            unreadable = outcome.unreadable,
                            fileCount = outcome.fileCount,
                            gameCodes = outcome.gameCodes,
                            reason = outcome.reason
                        ),
                        focusIndex = 0
                    )
                }
                StreamClaimOutcome.StartRefused -> showNotice(R.string.stream_launch_notice_start_refused, null)
                is StreamClaimOutcome.Refused -> showNotice(R.string.stream_launch_notice_refused, outcome.detail)
                StreamClaimOutcome.NotFound -> showNotice(R.string.stream_launch_notice_not_found, null)
                StreamClaimOutcome.Unreachable -> showNotice(R.string.stream_launch_notice_unreachable, null)
                StreamClaimOutcome.GameRunning -> showNotice(R.string.stream_launch_notice_game_running, null)
                is StreamClaimOutcome.Failed -> showNotice(R.string.stream_launch_notice_failed, null)
            }
        }
    }

    private fun join() {
        val streamTarget = target ?: return
        val session = joinable ?: return
        _state.update { it?.copy(step = StreamLaunchStep.Starting, focusIndex = 0) }
        loadJob = scope.launch {
            if (deps.sessions.join(streamTarget, session)) {
                _state.value = null
                startPlayer(streamTarget.gameId)
            } else {
                showNotice(R.string.stream_launch_notice_join_ended, null)
            }
        }
    }

    private fun showNotice(@StringRes message: Int, detail: String?) {
        _state.update { it?.copy(step = StreamLaunchStep.Notice(message, detail), focusIndex = 0) }
    }

    private suspend fun startPlayer(gameId: Long) {
        val options = deps.launchTargets.launchOptionsFor(gameId)
        StreamPlayerActivity.start(deps.context, options)
    }

    private fun RomMStreamState.toOption() = StreamOptionUi(
        id = id,
        title = fileName,
        timestampMillis = SaveSyncApiClient.parseTimestampOrNull(createdAt ?: updatedAt)?.toEpochMilli(),
        owner = username
    )

    private fun RomMStreamSave.toOption() = StreamOptionUi(
        id = id,
        title = fileName,
        timestampMillis = SaveSyncApiClient.parseTimestampOrNull(updatedAt)?.toEpochMilli(),
        owner = null
    )

    fun inputHandler(): InputHandler = object : InputHandler {
        private fun focused(): StreamLaunchRow? = _state.value?.focusedRow

        override fun onUp(): InputResult {
            moveFocus(-1)
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            moveFocus(1)
            return InputResult.HANDLED
        }

        override fun onLeft(): InputResult {
            focused()?.let { cycle(it, -1) }
            return InputResult.HANDLED
        }

        override fun onRight(): InputResult {
            focused()?.let { cycle(it, 1) }
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            val row = focused() ?: return InputResult.HANDLED
            if (row == StreamLaunchRow.STATE || row == StreamLaunchRow.SAVE) return InputResult.handled(SoundType.SILENT)
            activate(row)
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            if (_state.value?.step != StreamLaunchStep.Starting) dismiss()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult = InputResult.HANDLED
        override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        override fun onContextMenu(): InputResult = InputResult.HANDLED
        override fun onPrevSection(): InputResult = InputResult.HANDLED
        override fun onNextSection(): InputResult = InputResult.HANDLED
        override fun onPrevTrigger(): InputResult = InputResult.HANDLED
        override fun onNextTrigger(): InputResult = InputResult.HANDLED
        override fun onSelect(): InputResult = InputResult.HANDLED
        override fun onLongConfirm(): InputResult = InputResult.HANDLED
        override fun onLongSelect(): InputResult = InputResult.HANDLED
    }

    class Factory @Inject constructor(
        @ApplicationContext internal val context: Context,
        internal val gameRepository: GameRepository,
        internal val configStore: StreamingConfigStore,
        internal val sessions: StreamSessionManager,
        internal val connectionManager: RomMConnectionManager,
        internal val snapshotChannels: SnapshotChannelService,
        internal val launchTargets: EmulatorLaunchTargetResolver,
        internal val soundManager: SoundFeedbackManager,
        internal val preferences: UserPreferencesRepository
    ) {
        fun create(scope: CoroutineScope): StreamLaunchController = StreamLaunchController(this, scope)
    }

    private companion object {
        const val TAG = "StreamLaunchController"
        const val CARD_ADOPT = "adopt"
        const val CARD_DISCARD = "discard"
        val FRESH_START = StreamOptionUi(id = null, title = "", timestampMillis = null, owner = null)
    }
}
