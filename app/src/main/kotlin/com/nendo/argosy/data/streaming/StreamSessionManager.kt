package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMDetailResponse
import com.nendo.argosy.data.remote.romm.RomMRomFile
import com.nendo.argosy.data.remote.romm.RomMStreamBusyEnvelope
import com.nendo.argosy.data.remote.romm.RomMStreamCardImportEnvelope
import com.nendo.argosy.data.remote.romm.RomMStreamClaimRequest
import com.nendo.argosy.data.remote.romm.RomMStreamJoinableSession
import com.nendo.argosy.data.remote.romm.RomMStreamSlotCapabilities
import com.nendo.argosy.data.remote.romm.RomMStreamSlotRequest
import com.nendo.argosy.data.remote.romm.RomMStreamSwapDiscRequest
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class StreamPhase { LAUNCHING, PLAYING, ENDED }

enum class StreamEndReason { SAVED_AND_EXITED, GRACE_EXPIRED, ENDED_BY_SERVER, LAUNCH_FAILED, LEFT }

data class StreamDisc(val fileId: Long, val label: String)

data class StreamTarget(
    val gameId: Long,
    val rommId: Long,
    val title: String,
    val platform: String,
    val containerLabel: String,
    val capabilities: RomMStreamSlotCapabilities,
    val supportsLiveStates: Boolean,
    val discs: List<StreamDisc>
)

/**
 * The stream this device holds. [isHost] is false for a session joined from another player, which
 * has no controls and sends no heartbeat.
 */
data class StreamSession(
    val target: StreamTarget,
    val containerKey: String?,
    val claimedAt: String?,
    val isHost: Boolean,
    val phase: StreamPhase,
    val roomUrl: String?,
    val extractionPhase: String?,
    val startedAtMillis: Long,
    val taskId: Int?,
    val currentDiscId: Long?,
    val lastStateSavedAtMillis: Long?,
    val endReason: StreamEndReason?
) {
    val stateSlot: Int
        get() = if (target.capabilities.hasAutosave) target.capabilities.autosaveSlot else FIRST_SLOT

    val supportsStates: Boolean
        get() = isHost && target.supportsLiveStates &&
            (target.capabilities.maxSlots > 0 || target.capabilities.hasAutosave)

    val canSwapDisc: Boolean
        get() = isHost && target.capabilities.supportsDiscSwap && target.discs.size > 1

    val claim: StreamClaim?
        get() = if (isHost && containerKey != null && claimedAt != null) {
            StreamClaim(target.platform, containerKey, claimedAt)
        } else {
            null
        }

    private companion object {
        const val FIRST_SLOT = 1
    }
}

/**
 * The one RomM stream this device holds: its claim, room, heartbeat and teardown. A held claim is
 * a stream session in [PlaySessionTracker], which records no play time; RomM records the streamed
 * session itself at teardown.
 */
@Singleton
class StreamSessionManager internal constructor(
    private val connectionManager: RomMConnectionManager,
    private val configStore: StreamingConfigStore,
    private val tracker: PlaySessionTracker,
    private val releaser: StreamClaimReleaser,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long
) {
    @Inject
    constructor(
        connectionManager: RomMConnectionManager,
        configStore: StreamingConfigStore,
        tracker: PlaySessionTracker,
        releaser: StreamClaimReleaser
    ) : this(
        connectionManager,
        configStore,
        tracker,
        releaser,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        Dispatchers.IO,
        System::currentTimeMillis
    )

    private val moshi = Moshi.Builder().build()
    private val busyAdapter = moshi.adapter(RomMStreamBusyEnvelope::class.java)
    private val cardAdapter = moshi.adapter(RomMStreamCardImportEnvelope::class.java)
    private val detailAdapter = moshi.adapter(RomMDetailResponse::class.java)

    private val _session = MutableStateFlow<StreamSession?>(null)
    val session: StateFlow<StreamSession?> = _session.asStateFlow()

    private var heartbeatJob: Job? = null
    private var roomJob: Job? = null
    private val grace = StreamGraceTimer(scope) { endWithSave(StreamEndReason.GRACE_EXPIRED) }

    val isGraceArmed: Boolean get() = grace.isArmed

    init {
        scope.launch {
            var held: StreamClaim? = null
            tracker.activeSession.collect { active ->
                val released = held
                held = active?.streamClaim
                if (released != null && released != held) onTrackerSessionEnded(released)
            }
        }
    }

    private fun onTrackerSessionEnded(claim: StreamClaim) {
        val current = _session.value ?: return
        if (current.phase == StreamPhase.ENDED || current.claim != claim) return
        Logger.info(TAG, "the stream session on ${claim.platform} was ended outside the stream, ending the stream")
        endWithSave(StreamEndReason.LEFT)
    }

    fun isLive(gameId: Long): Boolean =
        _session.value?.let { it.target.gameId == gameId && it.phase != StreamPhase.ENDED } == true

    suspend fun claim(
        target: StreamTarget,
        stateId: Long?,
        saveId: Long?,
        cardImport: String?
    ): StreamClaimOutcome = withContext(io) {
        if (holdsOtherSession()) return@withContext StreamClaimOutcome.GameRunning
        val api = connectionManager.getApi() ?: return@withContext StreamClaimOutcome.Unreachable
        val response = try {
            api.claimStreamSession(
                RomMStreamClaimRequest(
                    romId = target.rommId,
                    stateId = stateId,
                    saveId = saveId,
                    cardImport = cardImport
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "claim failed for rom ${target.rommId}: ${e.message}")
            return@withContext StreamClaimOutcome.Unreachable
        }
        val launch = response.body()
        if (response.isSuccessful && launch != null) {
            val claim = StreamClaim(target.platform, launch.container, launch.claimedAt)
            if (!begin(target, claim)) {
                val released = releaser.release(claim)
                Logger.warn(TAG, "a game session opened during the claim, stream on ${claim.platform} released with outcome $released")
                return@withContext StreamClaimOutcome.GameRunning
            }
            return@withContext StreamClaimOutcome.Launching(launch)
        }
        val body = runCatching { response.errorBody()?.string() }.getOrNull()
        val outcome = StreamClaimErrors.classify(
            status = response.code(),
            busy = parse { busyAdapter.fromJson(body.orEmpty())?.detail },
            card = parse { cardAdapter.fromJson(body.orEmpty())?.detail },
            detailText = parse { detailAdapter.fromJson(body.orEmpty())?.detail }
        )
        if (outcome is StreamClaimOutcome.StartRefused) configStore.markStartRefused()
        Logger.info(TAG, "claim for rom ${target.rommId} answered ${response.code()} -> $outcome")
        outcome
    }

    suspend fun joinableFor(rommId: Long): RomMStreamJoinableSession? = withContext(io) {
        val api = connectionManager.getApi() ?: return@withContext null
        val sessions = try {
            api.getJoinableStreamSessions(rommId).takeIf { it.isSuccessful }?.body()?.sessions.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "joinable sessions could not be read: ${e.message}")
            emptyList()
        }
        StreamBusyChoice.joinTarget(sessions, rommId)
    }

    suspend fun join(target: StreamTarget, joinable: RomMStreamJoinableSession): Boolean = withContext(io) {
        val api = connectionManager.getApi() ?: return@withContext false
        val platform = joinable.platform ?: return@withContext false
        val joined = try {
            api.joinStreamSession(platform, joinable.container).takeIf { it.isSuccessful }?.body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "join failed for ${joinable.container}: ${e.message}")
            null
        } ?: return@withContext false
        val room = StreamRoom.resolve(connectionManager.getBaseUrl(), joined.host) ?: return@withContext false
        stopJobs()
        _session.value = StreamSession(
            target = target.copy(platform = platform),
            containerKey = joinable.container,
            claimedAt = null,
            isHost = false,
            phase = StreamPhase.PLAYING,
            roomUrl = room,
            extractionPhase = null,
            startedAtMillis = clock(),
            taskId = null,
            currentDiscId = null,
            lastStateSavedAtMillis = null,
            endReason = null
        )
        true
    }

    fun attachTask(taskId: Int) {
        _session.update { it?.copy(taskId = taskId) }
    }

    fun onPlayerShown() {
        grace.disarm()
    }

    fun onPlayerHidden() {
        val current = _session.value ?: return
        if (current.phase == StreamPhase.ENDED) return
        if (!current.isHost) {
            end(StreamEndReason.LEFT)
            return
        }
        grace.arm()
    }

    fun saveAndExit() = endWithSave(StreamEndReason.SAVED_AND_EXITED)

    fun leave() {
        val current = _session.value ?: return
        if (current.isHost) endWithSave(StreamEndReason.LEFT) else end(StreamEndReason.LEFT)
    }

    fun clearEnded() {
        _session.update { if (it?.phase == StreamPhase.ENDED) null else it }
    }

    suspend fun saveState(): Boolean = control { api, s ->
        api.saveStreamState(s.target.platform, RomMStreamSlotRequest(s.stateSlot), s.containerKey, s.claimedAt)
            .isSuccessful
            .also { saved -> if (saved) _session.update { it?.copy(lastStateSavedAtMillis = clock()) } }
    }

    suspend fun loadState(): Boolean = control { api, s ->
        api.loadStreamState(s.target.platform, RomMStreamSlotRequest(s.stateSlot), s.containerKey, s.claimedAt)
            .isSuccessful
    }

    suspend fun swapDisc(fileId: Long): Boolean = control { api, s ->
        api.swapStreamDisc(s.target.platform, RomMStreamSwapDiscRequest(fileId), s.containerKey, s.claimedAt)
            .isSuccessful
            .also { swapped -> if (swapped) _session.update { it?.copy(currentDiscId = fileId) } }
    }

    suspend fun nextDisc(): Boolean {
        val current = _session.value ?: return false
        val discs = current.target.discs
        if (discs.size < 2) return false
        val index = discs.indexOfFirst { it.fileId == current.currentDiscId }
        return swapDisc(discs[(index + 1).mod(discs.size)].fileId)
    }

    private suspend fun control(block: suspend (RomMApi, StreamSession) -> Boolean): Boolean = withContext(io) {
        val current = _session.value?.takeIf { it.isHost && it.phase == StreamPhase.PLAYING } ?: return@withContext false
        val api = connectionManager.getApi() ?: return@withContext false
        try {
            block(api, current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "stream control failed: ${e.message}")
            false
        }
    }

    private fun holdsOtherSession(): Boolean {
        if (_session.value?.let { it.isHost && it.phase != StreamPhase.ENDED } == true) return true
        val active = tracker.activeSession.value ?: return false
        return active.streamClaim == null
    }

    private fun begin(target: StreamTarget, claim: StreamClaim): Boolean {
        stopJobs()
        val previous = _session.value?.claim
        val containerKey = claim.containerKey
        val claimedAt = claim.claimedAt
        _session.value = StreamSession(
            target = target,
            containerKey = containerKey,
            claimedAt = claimedAt,
            isHost = true,
            phase = StreamPhase.LAUNCHING,
            roomUrl = null,
            extractionPhase = null,
            startedAtMillis = clock(),
            taskId = null,
            currentDiscId = null,
            lastStateSavedAtMillis = null,
            endReason = null
        )
        previous?.let(tracker::endStreamSession)
        if (!tracker.startStreamSession(target.gameId, claim)) {
            markEnded(StreamEndReason.LAUNCH_FAILED)
            return false
        }
        roomJob = scope.launch(io) { awaitRoom(target.platform, claimedAt) }
        heartbeatJob = scope.launch(io) { beat(target.platform, containerKey, claimedAt) }
        return true
    }

    private suspend fun awaitRoom(platform: String, claimedAt: String) {
        val deadline = clock() + ROOM_WAIT_MS
        while (scope.isActive && clock() < deadline) {
            val status = try {
                connectionManager.getApi()?.getStreamSessionStatus(platform, claimedAt)
                    ?.takeIf { it.isSuccessful }?.body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.debug(TAG, "status poll failed: ${e.message}")
                null
            }
            if (status != null && !status.isActive) {
                end(StreamEndReason.LAUNCH_FAILED)
                return
            }
            val room = StreamRoom.resolve(connectionManager.getBaseUrl(), status?.host)
            if (room != null) {
                _session.update { it?.copy(phase = StreamPhase.PLAYING, roomUrl = room, extractionPhase = null) }
                return
            }
            if (status?.extractionPhase != null) {
                _session.update { it?.copy(extractionPhase = status.extractionPhase) }
            }
            delay(ROOM_POLL_MS)
        }
        endWithSave(StreamEndReason.LAUNCH_FAILED)
    }

    private suspend fun beat(platform: String, containerKey: String, claimedAt: String) {
        while (scope.isActive) {
            delay(HEARTBEAT_MS)
            val status = try {
                connectionManager.getApi()?.heartbeatStreamSession(platform, containerKey, claimedAt)
                    ?.takeIf { it.isSuccessful }?.body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.debug(TAG, "heartbeat failed: ${e.message}")
                null
            }
            if (status != null && !status.isActive) {
                Logger.info(TAG, "RomM reports the stream on $platform ended")
                end(StreamEndReason.ENDED_BY_SERVER)
                return
            }
        }
    }

    private fun endWithSave(reason: StreamEndReason) {
        val current = _session.value ?: return
        if (current.phase == StreamPhase.ENDED) return
        val claim = current.claim
        if (claim == null) {
            end(reason)
            return
        }
        markEnded(reason)
        scope.launch(io) {
            val outcome = releaser.release(claim)
            Logger.info(TAG, "stream on ${claim.platform} released with outcome $outcome")
            tracker.endStreamSession(claim)
        }
    }

    private fun end(reason: StreamEndReason) {
        val claim = _session.value?.takeIf { it.phase != StreamPhase.ENDED }?.claim
        markEnded(reason)
        claim?.let(tracker::endStreamSession)
    }

    private fun markEnded(reason: StreamEndReason) {
        stopJobs()
        _session.update { it?.copy(phase = StreamPhase.ENDED, endReason = reason) }
    }

    private fun stopJobs() {
        grace.disarm()
        heartbeatJob?.cancel()
        heartbeatJob = null
        roomJob?.cancel()
        roomJob = null
    }

    private inline fun <T> parse(block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        null
    }

    companion object {
        const val STREAM_PACKAGE = "argosy.romm.stream"
        internal const val HEARTBEAT_MS = 30_000L
        internal const val ROOM_POLL_MS = 2_000L
        internal const val ROOM_WAIT_MS = 10 * 60 * 1000L
        private const val TAG = "StreamSessionManager"

        fun discsOf(files: List<RomMRomFile>): List<StreamDisc> =
            StreamRoom.swappableDiscs(files).map { StreamDisc(it.id, it.fileName) }

        fun targetOf(
            gameId: Long,
            rommId: Long,
            title: String,
            container: RomMStreamingContainer,
            discs: List<StreamDisc>
        ): StreamTarget = StreamTarget(
            gameId = gameId,
            rommId = rommId,
            title = title,
            platform = container.platform,
            containerLabel = StreamTriggerGate.labelOf(container).orEmpty(),
            capabilities = container.capabilities,
            supportsLiveStates = container.supportsLiveStates,
            discs = discs
        )
    }
}
