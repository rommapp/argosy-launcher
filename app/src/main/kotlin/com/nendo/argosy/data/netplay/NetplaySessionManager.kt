package com.nendo.argosy.data.netplay

import android.util.Log
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.ArgosSocialService.IncomingMessage
import com.nendo.argosy.data.social.NetplayJoinRequestPayload
import com.nendo.argosy.data.social.NetplayJoinResponsePayload
import com.nendo.argosy.data.social.NetplayOpenPayload
import com.nendo.argosy.data.social.NetplayReserveRequestPayload
import com.nendo.argosy.data.social.NetplaySessionState
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramSocket
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

class NetplaySessionManager(
    private val socialService: ArgosSocialService,
    private val handshake: NetplayHandshake,
    private val retroView: GLRetroView,
    private val sessionRules: NetplaySessionRules? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _sessionState = MutableStateFlow<NetplaySessionState>(NetplaySessionState.Idle)
    val sessionState: StateFlow<NetplaySessionState> = _sessionState.asStateFlow()

    private val inputShadow = NetplayInputShadow()

    @Volatile private var activeDriver: NetplayDriver? = null
    @Volatile private var role: NetplayHandshake.Role? = null
    @Volatile private var activeSessionId: String? = null
    @Volatile private var activePeerUserId: String? = null
    @Volatile private var pendingReadyPayload: PendingReady? = null
    private var silenceMonitorJob: Job? = null

    private val peerCandidatesChannel = Channel<IncomingMessage.NetplayPeerCandidates>(capacity = Channel.BUFFERED)
    private val punchStartChannel = Channel<IncomingMessage.NetplayPunchStart>(capacity = Channel.BUFFERED)
    private val readyChannel = Channel<IncomingMessage.NetplayReady>(capacity = Channel.BUFFERED)
    private val joinRequestedChannel = Channel<IncomingMessage.NetplayJoinRequested>(capacity = Channel.BUFFERED)

    private val inboundJob: Job = scope.launch {
        socialService.incomingMessages.collect { message ->
            dispatchIncoming(message)
        }
    }

    fun currentDriver(): NetplayDriver? = activeDriver

    suspend fun openServer(payload: NetplayOpenPayload): Result<String> {
        if (_sessionState.value !is NetplaySessionState.Idle) {
            return Result.failure(IllegalStateException("session not idle"))
        }
        _sessionState.value = NetplaySessionState.Opening(payload.gameTitle)
        if (!socialService.sendNetplayOpen(payload)) {
            _sessionState.value = NetplaySessionState.Error("send_failed")
            return Result.failure(IllegalStateException("send_failed"))
        }
        val ready = withTimeoutOrNull(READY_TIMEOUT) { readyChannel.receive() }
        if (ready == null) {
            _sessionState.value = NetplaySessionState.Error("ready_timeout")
            return Result.failure(IllegalStateException("ready_timeout"))
        }
        activeSessionId = ready.payload.sessionId
        pendingReadyPayload = PendingReady(
            sessionId = ready.payload.sessionId,
            sessionKey = decodeBase64(ready.payload.sessionKey)
        )
        role = NetplayHandshake.Role.Host
        _sessionState.value = NetplaySessionState.Waiting(
            sessionId = ready.payload.sessionId,
            gameTitle = payload.gameTitle
        )
        return Result.success(ready.payload.sessionId)
    }

    suspend fun acceptJoin(guestUserId: String) {
        val state = _sessionState.value
        if (state !is NetplaySessionState.JoinRequestReceived && state !is NetplaySessionState.Waiting) {
            Log.w(TAG, "acceptJoin ignored in state $state")
            return
        }
        val sessionId = activeSessionId ?: run {
            _sessionState.value = NetplaySessionState.Error("no_session")
            return
        }
        val ready = pendingReadyPayload ?: run {
            _sessionState.value = NetplaySessionState.Error("no_ready")
            return
        }
        socialService.sendNetplayJoinResponse(
            NetplayJoinResponsePayload(sessionId = sessionId, guestId = guestUserId, accept = true)
        )
        _sessionState.value = NetplaySessionState.Handshaking(sessionId = sessionId, peerUserId = guestUserId)
        runHandshakeAsHost(sessionId = sessionId, sessionKey = ready.sessionKey, guestUserId = guestUserId)
    }

    fun declineJoin(guestUserId: String, reason: String? = null) {
        val sessionId = activeSessionId ?: return
        socialService.sendNetplayJoinResponse(
            NetplayJoinResponsePayload(sessionId = sessionId, guestId = guestUserId, accept = false, reason = reason)
        )
        val gameTitle = (_sessionState.value as? NetplaySessionState.JoinRequestReceived)?.let { "" }
        _sessionState.value = NetplaySessionState.Waiting(sessionId, gameTitle ?: "")
    }

    suspend fun joinSession(sessionId: String, hostUserId: String) {
        if (_sessionState.value !is NetplaySessionState.Idle) {
            Log.w(TAG, "joinSession ignored in state ${_sessionState.value}")
            return
        }
        _sessionState.value = NetplaySessionState.Opening("")
        if (!socialService.sendNetplayJoinRequest(NetplayJoinRequestPayload(sessionId = sessionId))) {
            _sessionState.value = NetplaySessionState.Error("send_failed")
            return
        }
        val ready = withTimeoutOrNull(READY_TIMEOUT) { readyChannel.receive() }
        if (ready == null) {
            _sessionState.value = NetplaySessionState.Error("ready_timeout")
            return
        }
        activeSessionId = ready.payload.sessionId
        val sessionKey = decodeBase64(ready.payload.sessionKey)
        role = NetplayHandshake.Role.Guest
        _sessionState.value = NetplaySessionState.Handshaking(sessionId = ready.payload.sessionId, peerUserId = hostUserId)
        runHandshakeAsGuest(sessionId = ready.payload.sessionId, sessionKey = sessionKey, hostUserId = hostUserId)
    }

    suspend fun closeServer() {
        val sessionId = activeSessionId
        tearDownDriver("host_close")
        if (sessionId != null) {
            socialService.sendNetplayClose(sessionId)
        }
        _sessionState.value = NetplaySessionState.Idle
        activeSessionId = null
        pendingReadyPayload = null
        role = null
    }

    suspend fun leaveSession() {
        val sessionId = activeSessionId
        tearDownDriver("guest_leave")
        if (sessionId != null) {
            socialService.sendNetplayLeave(sessionId)
        }
        _sessionState.value = NetplaySessionState.Idle
        activeSessionId = null
        pendingReadyPayload = null
        role = null
    }

    suspend fun reserveSession(reservedForUserId: String?): Boolean {
        val sessionId = activeSessionId ?: return false
        val state = _sessionState.value
        if (state is NetplaySessionState.Idle || state is NetplaySessionState.Ending) {
            return false
        }
        return socialService.sendNetplayReserve(
            NetplayReserveRequestPayload(
                sessionId = sessionId,
                reservedForUserId = reservedForUserId
            )
        )
    }

    suspend fun onHostKeepSession() {
        val state = _sessionState.value
        if (state is NetplaySessionState.PeerDisconnected) {
            tearDownDriver("p2p_disconnect_keep_open")
            _sessionState.value = NetplaySessionState.Waiting(
                sessionId = state.sessionId,
                gameTitle = ""
            )
            runCatching { retroView.resumeEmulation() }
        }
    }

    suspend fun onHostCloseAfterDisconnect() {
        closeServer()
    }

    fun shutdown() {
        inboundJob.cancel()
        scope.launch { tearDownDriver("shutdown") }
    }

    private suspend fun runHandshakeAsHost(sessionId: String, sessionKey: ByteArray, guestUserId: String) {
        val localSocket = try {
            withContext(Dispatchers.IO) { DatagramSocket(0) }
        } catch (t: Throwable) {
            _sessionState.value = NetplaySessionState.Error("socket_bind_failed")
            return
        }
        val args = NetplayHandshake.HandshakeArgs(
            sessionId = sessionId,
            sessionKey = sessionKey,
            role = NetplayHandshake.Role.Host,
            peerUserId = guestUserId,
            localSocket = localSocket,
            peerCandidatesChannel = peerCandidatesChannel,
            punchStartChannel = punchStartChannel,
            scope = scope
        )
        val result = handshake.performHandshake(args)
        when (result) {
            is NetplayHandshake.HandshakeResult.Failure -> {
                runCatching { localSocket.close() }
                _sessionState.value = NetplaySessionState.Error(result.reason)
            }
            is NetplayHandshake.HandshakeResult.Success -> {
                installHostDriver(sessionId, guestUserId, result)
            }
        }
    }

    private suspend fun runHandshakeAsGuest(sessionId: String, sessionKey: ByteArray, hostUserId: String) {
        val localSocket = try {
            withContext(Dispatchers.IO) { DatagramSocket(0) }
        } catch (t: Throwable) {
            _sessionState.value = NetplaySessionState.Error("socket_bind_failed")
            return
        }
        val args = NetplayHandshake.HandshakeArgs(
            sessionId = sessionId,
            sessionKey = sessionKey,
            role = NetplayHandshake.Role.Guest,
            peerUserId = hostUserId,
            localSocket = localSocket,
            peerCandidatesChannel = peerCandidatesChannel,
            punchStartChannel = punchStartChannel,
            scope = scope
        )
        val result = handshake.performHandshake(args)
        when (result) {
            is NetplayHandshake.HandshakeResult.Failure -> {
                runCatching { localSocket.close() }
                _sessionState.value = NetplaySessionState.Error(result.reason)
            }
            is NetplayHandshake.HandshakeResult.Success -> {
                installGuestDriver(sessionId, hostUserId, result)
            }
        }
    }

    private fun startSilenceMonitor(role: NetplaySessionState.PeerRole) {
        silenceMonitorJob?.cancel()
        silenceMonitorJob = scope.launch {
            while (true) {
                delay(SILENCE_TICK_MS)
                val driver = activeDriver ?: return@launch
                val state = _sessionState.value
                if (state !is NetplaySessionState.Connected && state !is NetplaySessionState.Reconnecting) {
                    continue
                }
                val silenceNanos = System.nanoTime() - driver.lastIncomingNanos
                val silenceMs = silenceNanos / 1_000_000L
                when {
                    silenceMs >= TIER3_TIMEOUT_MS -> {
                        val sessionId = activeSessionId ?: return@launch
                        val peer = activePeerUserId ?: return@launch
                        runCatching { retroView.pauseEmulation() }
                        _sessionState.value = NetplaySessionState.PeerDisconnected(
                            sessionId = sessionId,
                            peerUserId = peer,
                            role = role
                        )
                        return@launch
                    }
                    silenceMs >= TIER2_TIMEOUT_MS -> {
                        if (state is NetplaySessionState.Connected) {
                            runCatching { retroView.pauseEmulation() }
                            _sessionState.value = NetplaySessionState.Reconnecting(
                                sessionId = state.sessionId,
                                peerUserId = state.peerUserId
                            )
                        }
                    }
                    else -> {
                        if (state is NetplaySessionState.Reconnecting) {
                            runCatching { retroView.resumeEmulation() }
                            _sessionState.value = NetplaySessionState.Connected(
                                sessionId = state.sessionId,
                                peerUserId = state.peerUserId
                            )
                        }
                    }
                }
            }
        }
    }

    private fun installHostDriver(sessionId: String, guestUserId: String, result: NetplayHandshake.HandshakeResult.Success) {
        val driver = NetplayHostDriver(
            retroView = retroView,
            transport = result.transport,
            peerAddress = result.peerAddress,
            peerUserId = guestUserId,
            localPort = LOCAL_HOST_PORT,
            guestPort = GUEST_PORT,
            inputShadow = inputShadow,
            scope = scope,
            onSessionEnd = { reason -> scope.launch { handleSessionEnd(reason) } }
        )
        activeDriver = driver
        retroView.netplayKeyIntercept = { port, action, keyCode ->
            inputShadow.onAndroidKeyEvent(port, action, keyCode)
        }
        retroView.netplayLocalPort = null
        retroView.netplayTick = { driver.tick() }
        activePeerUserId = guestUserId
        scope.launch {
            runCatching {
                sessionRules?.apply(NetplaySessionRules.ApplyContext(NetplaySessionRules.Role.Host))
                val state = retroView.serializeState()
                driver.sendInitialSnapshot(state)
                _sessionState.value = NetplaySessionState.Connected(sessionId = sessionId, peerUserId = guestUserId)
                startSilenceMonitor(NetplaySessionState.PeerRole.Host)
            }.onFailure {
                Log.w(TAG, "host install failed: ${it.message}")
                sessionRules?.release()
                _sessionState.value = NetplaySessionState.Error("host_install_failed")
            }
        }
    }

    private fun installGuestDriver(sessionId: String, hostUserId: String, result: NetplayHandshake.HandshakeResult.Success) {
        val driver = NetplayGuestDriver(
            retroView = retroView,
            transport = result.transport,
            peerAddress = result.peerAddress,
            peerUserId = hostUserId,
            localPort = GUEST_PORT,
            hostPort = LOCAL_HOST_PORT,
            inputShadow = inputShadow,
            scope = scope,
            onSessionEnd = { reason -> scope.launch { handleSessionEnd(reason) } }
        )
        activeDriver = driver
        retroView.netplayKeyIntercept = { port, action, keyCode ->
            inputShadow.onAndroidKeyEvent(port, action, keyCode)
        }
        retroView.netplayLocalPort = GUEST_PORT
        retroView.netplayTick = { driver.tick() }
        activePeerUserId = hostUserId
        scope.launch {
            runCatching {
                sessionRules?.apply(NetplaySessionRules.ApplyContext(NetplaySessionRules.Role.Guest))
                _sessionState.value = NetplaySessionState.Connected(sessionId = sessionId, peerUserId = hostUserId)
                startSilenceMonitor(NetplaySessionState.PeerRole.Guest)
            }.onFailure {
                Log.w(TAG, "guest install failed: ${it.message}")
                sessionRules?.release()
                _sessionState.value = NetplaySessionState.Error("guest_install_failed")
            }
        }
    }

    private suspend fun tearDownDriver(reason: String) {
        silenceMonitorJob?.cancel()
        silenceMonitorJob = null
        val driver = activeDriver
        activeDriver = null
        retroView.netplayTick = null
        retroView.netplayLocalPort = null
        retroView.netplayKeyIntercept = null
        driver?.stop()
        for (port in 0 until NetplayInputShadow.MAX_PORTS) {
            inputShadow.clear(port)
        }
        activePeerUserId = null
        sessionRules?.release()
        delay(0)
    }

    private suspend fun handleSessionEnd(reason: String) {
        tearDownDriver(reason)
        _sessionState.value = NetplaySessionState.Idle
        activeSessionId = null
        pendingReadyPayload = null
        role = null
    }

    private fun dispatchIncoming(message: IncomingMessage) {
        when (message) {
            is IncomingMessage.NetplayReady -> {
                pendingReadyPayload = PendingReady(
                    sessionId = message.payload.sessionId,
                    sessionKey = decodeBase64(message.payload.sessionKey)
                )
                activeSessionId = message.payload.sessionId
                readyChannel.trySend(message)
            }
            is IncomingMessage.NetplayJoinRequested -> {
                joinRequestedChannel.trySend(message)
                _sessionState.value = NetplaySessionState.JoinRequestReceived(
                    sessionId = message.payload.sessionId,
                    fromUserId = message.payload.fromUserId,
                    fromUsername = message.payload.fromUsername
                )
            }
            is IncomingMessage.NetplayJoinDeclined -> {
                _sessionState.value = NetplaySessionState.Error(message.payload.reason)
            }
            is IncomingMessage.NetplayPeerCandidates -> {
                peerCandidatesChannel.trySend(message)
            }
            is IncomingMessage.NetplayPunchStart -> {
                punchStartChannel.trySend(message)
            }
            is IncomingMessage.NetplayHandshakeFailed -> {
                _sessionState.value = NetplaySessionState.Error(message.payload.reason)
            }
            is IncomingMessage.NetplayKicked -> {
                scope.launch { handleSessionEnd("kicked") }
            }
            is IncomingMessage.NetplaySessionEnded -> {
                scope.launch { handleSessionEnd("session_ended") }
            }
            else -> {
            }
        }
    }

    private fun decodeBase64(encoded: String): ByteArray = try {
        Base64.getDecoder().decode(encoded)
    } catch (_: Throwable) {
        encoded.toByteArray(Charsets.UTF_8)
    }

    private data class PendingReady(val sessionId: String, val sessionKey: ByteArray)

    companion object {
        private const val TAG = "NetplaySessionManager"
        private val READY_TIMEOUT = 30.seconds
        private const val LOCAL_HOST_PORT = 0
        private const val GUEST_PORT = 1
        private const val SILENCE_TICK_MS = 100L
        private const val TIER2_TIMEOUT_MS = 500L
        private const val TIER3_TIMEOUT_MS = 2000L
    }
}
