package com.nendo.argosy.libretro

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.netplay.CandidateGatherer
import com.nendo.argosy.data.netplay.CoreHashCache
import com.nendo.argosy.data.netplay.NetplayHandshake
import com.nendo.argosy.data.netplay.NetplaySessionManager
import com.nendo.argosy.data.netplay.NetplaySessionRules
import com.nendo.argosy.data.netplay.RomHashComputer
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.FriendshipStatus
import com.nendo.argosy.data.social.NetplayOpenPayload
import com.nendo.argosy.data.social.NetplaySessionMode
import com.nendo.argosy.data.social.NetplaySessionState
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.libretro.ui.NetplayFriendPickerEntry
import com.nendo.argosy.libretro.ui.NetplayMenuRole
import com.nendo.argosy.libretro.ui.NetplayProgressStage
import com.nendo.argosy.libretro.ui.NetplayProgressState
import com.nendo.argosy.libretro.ui.NetplayQualityInfo
import com.nendo.argosy.libretro.ui.NetplayQualityLabel
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Owns the netplay session lifecycle for a libretro session: room
 * create/join, peer state, signaling, and host vs. guest distinction.
 *
 * State exposed via Compose-observable properties is read directly by
 * [LibretroActivity]'s in-game overlay; mutating actions are exposed as
 * methods routed from menu callbacks.
 */
class LibretroNetplayCoordinator(
    private val activity: Activity,
    private val gameDao: GameDao,
    private val coreHashCache: CoreHashCache,
    private val socialRepository: SocialRepository,
    private val argosSocialService: ArgosSocialService,
    private val preferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val showToast: (String) -> Unit,
    private val onFastForwardRelease: () -> Unit,
    private val getRetroView: () -> GLRetroView,
    private val getResolvedCoreId: () -> String?,
    private val getCorePath: () -> String,
    private val getRomPath: () -> String,
    private val getGameId: () -> Long,
    private val getGameName: () -> String,
    private val getRaSessionManager: () -> RetroAchievementsSessionManager?
) {
    private data class PendingNetplayJoin(val sessionId: String, val hostUserId: String)

    private var sessionManager: NetplaySessionManager? = null
    private var sessionRules: NetplaySessionRules? = null
    private var pendingJoin: PendingNetplayJoin? = null
    private var savedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var guestSessionEverStarted: Boolean = false
    private var lastJoinedToastPeerId: String? = null
    private var lastAnnouncedTier: NetplayQualityLabel? = null
    private var tierChangeTimestamp: Long = 0L
    private val rttRingBuffer = IntArray(RTT_RING_BUFFER_SIZE)
    private var rttRingIndex = 0
    private var rttRingCount = 0
    private var scratchDir: File? = null

    var isGuestJoinedSession: Boolean = false
        private set

    var inSession by mutableStateOf(false)
        private set
    var role: NetplayMenuRole? by mutableStateOf(null)
        private set
    var sessionIsReserved by mutableStateOf(false)
        private set
    var progressState by mutableStateOf<NetplayProgressState?>(null)
    var reconnecting by mutableStateOf(false)
        private set
    var disconnectPromptVisible by mutableStateOf(false)
    var disconnectPromptPeer by mutableStateOf("Friend")
        private set
    var disconnectPromptFocus by mutableStateOf(0)
    var modePickerVisible by mutableStateOf(false)
    var modePickerFocus by mutableStateOf(0)
    var friendPickerVisible by mutableStateOf(false)
    var friendPickerFocus by mutableStateOf(0)
    var friendPickerEntries by mutableStateOf<List<NetplayFriendPickerEntry>>(emptyList())
        private set
    var joinRequestVisible by mutableStateOf(false)
        private set
    var joinRequestFocus by mutableStateOf(0)
    var joinRequestUsername by mutableStateOf("")
        private set
    private var joinRequestUserId by mutableStateOf("")
    var peerDisplayName by mutableStateOf("Friend")
        private set
    var lastRttMs by mutableStateOf<Int?>(null)
        private set
    var qualityWarningVisible by mutableStateOf(false)
    var qualityWarningRttMs by mutableStateOf(0)
        private set
    var qualityWarningJitterMs by mutableStateOf(0)
        private set
    var qualityWarningLabel by mutableStateOf("")
        private set
    var qualityWarningFocus by mutableStateOf(0)
    var hudSessionMode by mutableStateOf(NetplaySessionMode.OPEN)
        private set
    var hudAveragePingMs by mutableStateOf<Int?>(null)
        private set
    var hudHostUsername by mutableStateOf("")
        private set
    var hudHostAvatarColor by mutableStateOf<String?>(null)
        private set
    var hudGuestAvatarColor by mutableStateOf<String?>(null)
        private set
    var peerConnected by mutableStateOf(false)
        private set

    val isAnyDialogVisible: Boolean
        get() = progressState != null || disconnectPromptVisible || friendPickerVisible ||
            qualityWarningVisible || modePickerVisible || joinRequestVisible

    fun parseJoinIntent(joinSessionId: String?, joinHostUserId: String?): Boolean {
        if (joinSessionId.isNullOrEmpty() || joinHostUserId.isNullOrEmpty()) return false
        Log.d(TAG, "parseJoinIntent: guest join detected | sessionId=$joinSessionId, hostUserId=$joinHostUserId")
        pendingJoin = PendingNetplayJoin(joinSessionId, joinHostUserId)
        isGuestJoinedSession = true
        return true
    }

    fun createScratchDir(cacheDir: File): Triple<File, File, File> {
        val scratch = File(cacheDir, "netplay_guest/${System.currentTimeMillis()}")
        scratchDir = scratch
        val saves = File(scratch, "saves").apply { mkdirs() }
        val states = File(scratch, "states").apply { mkdirs() }
        return Triple(scratch, saves, states)
    }

    fun attachCheatSessionManager(cheatManager: CheatSessionManager) {
        sessionRules?.cheatSessionManager = cheatManager
    }

    fun start() {
        if (sessionManager != null) return
        val handshake = NetplayHandshake(
            candidateGatherer = CandidateGatherer(),
            socialService = argosSocialService
        )
        val rv = getRetroView()
        val rules = NetplaySessionRules(
            retroView = rv,
            raSessionManager = { getRaSessionManager() },
            onFastForwardRelease = onFastForwardRelease
        )
        sessionRules = rules
        val manager = NetplaySessionManager(
            socialService = argosSocialService,
            handshake = handshake,
            retroView = rv,
            sessionRules = rules
        )
        sessionManager = manager
        runBlocking {
            preferencesRepository.userPreferences.first()
        }.let { prefs ->
            hudHostUsername = prefs.socialDisplayName ?: prefs.socialUsername ?: ""
            hudHostAvatarColor = prefs.socialAvatarColor
        }
        observeSessionState(manager)
        observeRttQuality(manager)
        observeGuestLeftEvents(manager)
        observeJoinRequestQueue(manager)
        observeQualityWarnings(manager)
        observeProgressHints(manager)
    }

    private fun observeSessionState(manager: NetplaySessionManager) {
        scope.launch {
            manager.sessionState.collect { state ->
                when (state) {
                    is NetplaySessionState.Connected -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        val wasReconnecting = reconnecting
                        if (!inSession) {
                            savedOrientation = activity.requestedOrientation
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        }
                        inSession = true
                        reconnecting = false
                        disconnectPromptVisible = false
                        val peerName = resolveFriendDisplayName(state.peerUserId)
                        peerDisplayName = peerName
                        peerConnected = true
                        hudGuestAvatarColor = resolveFriendAvatarColor(state.peerUserId)
                        val initRtt = manager.initialRttMs
                        val rttSuffix = if (initRtt != null) " -- ${initRtt}ms" else ""
                        if (role == NetplayMenuRole.Host &&
                            !wasReconnecting &&
                            lastJoinedToastPeerId != state.peerUserId
                        ) {
                            lastJoinedToastPeerId = state.peerUserId
                            showToast("$peerName joined your session$rttSuffix")
                        }
                        if (progressState?.stage != NetplayProgressStage.Failed) {
                            val label = NetplayQualityInfo.labelForRttMs(initRtt)
                            val readyMsg = if (initRtt != null) "Ready -- ${initRtt}ms [${label.name}]" else null
                            progressState = NetplayProgressState(NetplayProgressStage.Ready, readyMsg)
                            lastAnnouncedTier = if (initRtt != null) label else null
                            tierChangeTimestamp = System.currentTimeMillis()
                            delay(800)
                            if (progressState?.stage == NetplayProgressStage.Ready) {
                                progressState = null
                            }
                        }
                    }
                    is NetplaySessionState.Idle -> {
                        if (inSession) {
                            activity.requestedOrientation = savedOrientation
                        }
                        inSession = false
                        role = null
                        peerConnected = false
                        sessionIsReserved = false
                        lastJoinedToastPeerId = null
                        lastAnnouncedTier = null
                        reconnecting = false
                        disconnectPromptVisible = false
                        if (isGuestJoinedSession && guestSessionEverStarted) {
                            Log.d(TAG, "guest session ended; closing emulator activity")
                            activity.finish()
                        }
                    }
                    is NetplaySessionState.Opening -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        if (!inSession) {
                            savedOrientation = activity.requestedOrientation
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        }
                        inSession = true
                        getRetroView().suppressAutoResume = false
                        getRetroView().resumeEmulation()
                        if (role == NetplayMenuRole.Guest) {
                            progressState = NetplayProgressState(NetplayProgressStage.RequestingJoin)
                        }
                    }
                    is NetplaySessionState.Waiting -> {
                        if (!inSession) {
                            savedOrientation = activity.requestedOrientation
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        }
                        inSession = true
                        reconnecting = false
                        disconnectPromptVisible = false
                        progressState = null
                    }
                    is NetplaySessionState.Handshaking -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        if (!inSession) {
                            savedOrientation = activity.requestedOrientation
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        }
                        inSession = true
                        getRetroView().suppressAutoResume = false
                        getRetroView().resumeEmulation()
                        progressState = NetplayProgressState(NetplayProgressStage.Connecting)
                    }
                    is NetplaySessionState.Reconnecting -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        inSession = true
                        reconnecting = true
                    }
                    is NetplaySessionState.PeerDisconnected -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        inSession = true
                        reconnecting = false
                        disconnectPromptPeer = resolveFriendDisplayName(state.peerUserId)
                        disconnectPromptFocus = 0
                        if (isGuestJoinedSession) {
                            disconnectPromptVisible = false
                            progressState = NetplayProgressState(
                                NetplayProgressStage.Failed,
                                "Disconnected from the session"
                            )
                            launch {
                                delay(1500)
                                activity.finish()
                            }
                        } else {
                            disconnectPromptVisible = true
                        }
                    }
                    is NetplaySessionState.Ending -> {
                        if (inSession) {
                            activity.requestedOrientation = savedOrientation
                        }
                        inSession = false
                    }
                    is NetplaySessionState.Error -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        if (inSession) {
                            activity.requestedOrientation = savedOrientation
                        }
                        inSession = false
                        progressState = NetplayProgressState(
                            NetplayProgressStage.Failed,
                            netplayErrorMessage(state.reason)
                        )
                        if (isGuestJoinedSession) {
                            launch {
                                delay(2500)
                                activity.finish()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeRttQuality(manager: NetplaySessionManager) {
        scope.launch {
            var candidateTier: NetplayQualityLabel? = null
            var candidateStartMs = 0L
            while (true) {
                delay(500)
                val driver = manager.currentDriver()
                val rtt = driver?.lastRttNanos ?: 0L
                val rttMs = if (rtt > 0L) (rtt / 1_000_000L).toInt() else null
                lastRttMs = rttMs
                if (!inSession || rttMs == null) {
                    candidateTier = null
                    rttRingCount = 0
                    rttRingIndex = 0
                    hudAveragePingMs = null
                    continue
                }
                rttRingBuffer[rttRingIndex] = rttMs
                rttRingIndex = (rttRingIndex + 1).mod(RTT_RING_BUFFER_SIZE)
                if (rttRingCount < RTT_RING_BUFFER_SIZE) rttRingCount++
                hudAveragePingMs = rttRingBuffer.take(rttRingCount).average().toInt()
                val currentTier = NetplayQualityInfo.labelForRttMs(rttMs)
                if (currentTier != lastAnnouncedTier) {
                    if (currentTier != candidateTier) {
                        candidateTier = currentTier
                        candidateStartMs = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - candidateStartMs >= TIER_CHANGE_DEBOUNCE_MS) {
                        lastAnnouncedTier = currentTier
                        tierChangeTimestamp = System.currentTimeMillis()
                        showToast("Connection: ${currentTier.name} (${rttMs}ms)")
                    }
                } else {
                    candidateTier = null
                }
            }
        }
    }

    private fun observeGuestLeftEvents(manager: NetplaySessionManager) {
        scope.launch {
            manager.guestLeftEvents.collect { event ->
                val name = resolveFriendDisplayName(event.guestUserId)
                showToast("$name left your session")
                peerDisplayName = "Friend"
                peerConnected = false
                hudGuestAvatarColor = null
                lastRttMs = null
            }
        }
    }

    private fun observeJoinRequestQueue(manager: NetplaySessionManager) {
        scope.launch {
            manager.joinRequestQueue.collect { updateJoinRequestModal(manager) }
        }
    }

    private fun observeQualityWarnings(manager: NetplaySessionManager) {
        scope.launch {
            manager.qualityWarningPending.collect { warning ->
                if (warning != null) {
                    qualityWarningRttMs = warning.measuredRttMs
                    qualityWarningJitterMs = warning.measuredJitterMs
                    qualityWarningLabel = warning.ratingLabel
                    qualityWarningFocus = 0
                    qualityWarningVisible = true
                } else {
                    qualityWarningVisible = false
                }
            }
        }
    }

    private fun observeProgressHints(manager: NetplaySessionManager) {
        scope.launch {
            manager.progressHint.collect { hint ->
                val stage = when (hint) {
                    NetplaySessionManager.ProgressHint.WaitingForHost -> NetplayProgressStage.WaitingForHost
                    NetplaySessionManager.ProgressHint.Measuring -> NetplayProgressStage.Measuring
                    NetplaySessionManager.ProgressHint.LoadingState -> NetplayProgressStage.LoadingState
                    null -> null
                }
                if (stage != null && progressState?.stage != NetplayProgressStage.Failed) {
                    progressState = NetplayProgressState(stage)
                }
            }
        }
    }

    fun triggerPendingNetplayJoin() {
        val pending = pendingJoin ?: return
        val manager = sessionManager ?: return
        pendingJoin = null
        role = NetplayMenuRole.Guest
        Log.d(TAG, "auto-join: triggered after first frame, hostUserId=${pending.hostUserId}")
        scope.launch {
            argosSocialService.connectionState
                .first { it is ArgosSocialService.ConnectionState.Connected }
            val currentSessionId = socialRepository.friends.value
                .firstOrNull { it.id == pending.hostUserId }
                ?.currentGame?.netplaySession?.sessionId
                ?: pending.sessionId
            Log.d(TAG, "auto-join: calling joinSession (intent=${pending.sessionId}, current=$currentSessionId)")
            runCatching {
                manager.joinSession(currentSessionId, pending.hostUserId)
            }.onFailure { err ->
                Log.w(TAG, "auto-join from intent failed: ${err.message}")
            }
            Log.d(TAG, "auto-join: joinSession returned")
        }
    }

    private fun resolveFriendDisplayName(userId: String): String {
        return socialRepository.friends.value.firstOrNull { it.id == userId }?.displayName ?: "Friend"
    }

    private fun resolveFriendAvatarColor(userId: String): String? {
        return socialRepository.friends.value.firstOrNull { it.id == userId }?.avatarColor
    }

    fun isCoreSupported(): Boolean {
        val coreId = getResolvedCoreId() ?: return false
        val core = LibretroCoreRegistry.getCoreById(coreId) ?: return false
        return core.netplaySupport == NetplaySupportLevel.SUPPORTED
    }

    private fun buildOpenPayload(): NetplayOpenPayload? {
        val coreId = getResolvedCoreId() ?: return null
        val romFile = File(getRomPath())
        val romHash = RomHashComputer.computeRomHashPrefix(romFile) ?: return null
        val coreHash = coreHashCache.getHashForCore(getCorePath()) ?: return null
        val gameId = getGameId()
        val igdbId = if (gameId != -1L) {
            runBlocking { gameDao.getById(gameId) }?.igdbId?.toInt()
        } else null
        return NetplayOpenPayload(
            gameIgdbId = igdbId,
            gameTitle = getGameName(),
            coreId = coreId,
            romHashPrefix = romHash,
            coreHash = coreHash
        )
    }

    fun handleOpenWithMode(mode: NetplaySessionMode) {
        val manager = sessionManager ?: run {
            showToast("Netplay manager unavailable")
            return
        }
        val payload = buildOpenPayload() ?: run {
            showToast("Netplay: failed to compute hashes")
            return
        }
        manager.sessionMode = mode
        hudSessionMode = mode
        role = NetplayMenuRole.Host
        getRetroView().suppressAutoResume = false
        getRetroView().resumeEmulation()
        scope.launch {
            val result = manager.openServer(payload)
            showToast(result.fold(
                onSuccess = { _ ->
                    val label = when (mode) {
                        NetplaySessionMode.OPEN -> "open"
                        NetplaySessionMode.PRIVATE -> "private"
                        NetplaySessionMode.INVITE_ONLY -> "invite-only"
                    }
                    "Netplay session opened ($label)"
                },
                onFailure = { e ->
                    role = null
                    "Netplay open failed: ${e.message}"
                }
            ))
        }
    }

    fun handleInviteFriend() {
        if (sessionManager == null) {
            showToast("Netplay manager unavailable")
            return
        }
        val onlineFriends = socialRepository.friends.value
            .asSequence()
            .filter { it.friendshipStatus == FriendshipStatus.ACCEPTED }
            .filter { it.presence != null && it.presence != PresenceStatus.OFFLINE }
            .map { friend ->
                NetplayFriendPickerEntry(
                    userId = friend.id,
                    displayName = friend.displayName,
                    avatarColorHex = friend.avatarColor,
                    isOnline = true
                )
            }
            .toList()
        friendPickerEntries = onlineFriends
        friendPickerFocus = 0
        friendPickerVisible = true
    }

    fun onFriendPicked(friend: NetplayFriendPickerEntry) {
        friendPickerVisible = false
        val manager = sessionManager ?: return
        val state = manager.sessionState.value
        scope.launch {
            if (state is NetplaySessionState.Waiting || state is NetplaySessionState.Connected) {
                manager.sessionMode = NetplaySessionMode.INVITE_ONLY
                val ok = manager.reserveSession(friend.userId)
                if (ok) sessionIsReserved = true
                showToast(if (ok) "Invited ${friend.displayName}" else "Invite failed")
            } else {
                val payload = buildOpenPayload() ?: run {
                    showToast("Netplay: failed to compute hashes")
                    return@launch
                }
                manager.sessionMode = NetplaySessionMode.INVITE_ONLY
                hudSessionMode = NetplaySessionMode.INVITE_ONLY
                role = NetplayMenuRole.Host
                val result = manager.openServer(payload)
                result.fold(
                    onSuccess = {
                        val reserved = manager.reserveSession(friend.userId)
                        if (reserved) sessionIsReserved = true
                        showToast(if (reserved) "Invited ${friend.displayName}" else "Session opened; invite failed")
                    },
                    onFailure = { e ->
                        role = null
                        showToast("Netplay open failed: ${e.message}")
                    }
                )
            }
        }
    }

    fun handleClearReservation() {
        val manager = sessionManager ?: return
        scope.launch {
            val ok = manager.reserveSession(null)
            if (ok) {
                sessionIsReserved = false
                manager.sessionMode = NetplaySessionMode.OPEN
                hudSessionMode = NetplaySessionMode.OPEN
                val head = manager.joinRequestQueue.value.firstOrNull()
                if (head != null) {
                    manager.acceptJoin(head.fromUserId)
                }
                showToast("Session open to all friends")
            } else {
                showToast("Failed to clear reservation")
            }
        }
    }

    fun handleJoinRequestAccept() {
        val manager = sessionManager ?: return
        val userId = joinRequestUserId
        joinRequestVisible = false
        scope.launch { manager.acceptJoin(userId) }
    }

    fun handleJoinRequestDecline() {
        val manager = sessionManager ?: return
        val userId = joinRequestUserId
        manager.declineJoin(userId, "host_declined")
        updateJoinRequestModal(manager)
    }

    private fun updateJoinRequestModal(manager: NetplaySessionManager) {
        val queue = manager.joinRequestQueue.value
        val head = queue.firstOrNull()
        if (head != null && manager.sessionMode == NetplaySessionMode.PRIVATE &&
            manager.sessionState.value is NetplaySessionState.Waiting
        ) {
            joinRequestUserId = head.fromUserId
            joinRequestUsername = head.fromUsername
            joinRequestFocus = 0
            joinRequestVisible = true
        } else {
            joinRequestVisible = false
        }
    }

    fun handleQualityAccept() {
        qualityWarningVisible = false
        sessionManager?.acceptQualityWarning()
    }

    fun handleQualityDecline() {
        qualityWarningVisible = false
        sessionManager?.declineQualityWarning()
    }

    fun handleKeepSession() {
        disconnectPromptVisible = false
        val manager = sessionManager ?: return
        scope.launch { manager.onHostKeepSession() }
    }

    fun handleCloseAfterDisconnect() {
        disconnectPromptVisible = false
        val manager = sessionManager ?: return
        scope.launch {
            manager.onHostCloseAfterDisconnect()
            activity.finish()
        }
    }

    fun handleCloseSession() {
        val manager = sessionManager ?: return
        val currentRole = role
        scope.launch {
            val state = manager.sessionState.value
            if (state is NetplaySessionState.Waiting ||
                state is NetplaySessionState.Connected ||
                state is NetplaySessionState.Handshaking
            ) {
                if (currentRole == NetplayMenuRole.Guest) manager.leaveSession() else manager.closeServer()
            }
        }
    }

    fun gracefullyEndIfActive() {
        val manager = sessionManager ?: return
        val state = manager.sessionState.value
        if (state is NetplaySessionState.Idle) return
        runBlocking {
            withTimeoutOrNull(NETPLAY_CLOSE_TIMEOUT_MS) {
                if (role == NetplayMenuRole.Guest) manager.leaveSession() else manager.closeServer()
            }
        }
    }

    fun shutdown() {
        sessionManager?.shutdown()
        sessionManager = null
        scratchDir?.takeIf { it.exists() }?.deleteRecursively()
        scratchDir = null
    }

    private fun netplayErrorMessage(reason: String): String = when (reason) {
        "protocol_version_mismatch" -> "Update Argosy to join this netplay session."
        "rate_limited" -> "You've started too many netplay sessions recently. Please wait a few minutes."
        "send_failed" -> "Couldn't reach Argosy. Check your connection."
        "ready_timeout", "handshake_timeout" -> "Connection timed out. Try again."
        "candidate_pair_failed" -> "Couldn't establish a direct connection with your friend."
        "quality_rejected" -> "Connection quality too poor for netplay."
        "no_candidates" -> "No network paths available for netplay."
        "not_found" -> "That session is no longer available."
        "not_friend" -> "You can only join sessions from friends."
        "session_full" -> "That session is full."
        "already_open" -> "You already have an active netplay session."
        "already_filled", "already_joined" -> "Someone else already joined that session."
        "disabled" -> "Netplay is currently disabled on the server."
        "core_unsupported" -> "This core doesn't support netplay."
        "self_join" -> "You can't join your own session."
        "host_install_failed", "guest_install_failed" -> "Couldn't start the netplay session."
        "socket_bind_failed" -> "Couldn't open a network socket for netplay."
        "invalid_payload", "invalid_state" -> "Netplay request was rejected by the server."
        "db_error", "internal_error" -> "A server error occurred. Try again shortly."
        else -> "Couldn't connect: $reason"
    }

    companion object {
        private const val TAG = "NetplayCoordinator"
        private const val NETPLAY_CLOSE_TIMEOUT_MS = 500L
        private const val TIER_CHANGE_DEBOUNCE_MS = 2000L
        private const val RTT_RING_BUFFER_SIZE = 6
    }
}
