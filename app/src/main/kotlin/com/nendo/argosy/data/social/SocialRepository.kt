package com.nendo.argosy.data.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSocialSyncDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.PendingSocialSyncEntity
import com.nendo.argosy.data.local.entity.SocialSyncType
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.SocialSyncCoordinator
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.ui.notification.NotificationDuration
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: SocialAuthManager,
    private val socialService: ArgosSocialService,
    private val preferencesRepository: UserPreferencesRepository,
    private val playSessionDao: PlaySessionDao,
    private val pendingSocialSyncDao: PendingSocialSyncDao,
    private val gameDao: GameDao,
    private val achievementDao: AchievementDao,
    private val notificationManager: NotificationManager,
    private val discordTokenHolder: DiscordTokenHolder,
    private val playSessionTracker: PlaySessionTracker,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val socialSyncCoordinator: Lazy<SocialSyncCoordinator>,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var hasCompletedInitialSync = false
    private var achievementSyncSuppressed = false

    private val _connectionState = MutableStateFlow<SocialConnectionState>(SocialConnectionState.Disconnected)
    val connectionState: StateFlow<SocialConnectionState> = _connectionState.asStateFlow()

    data class FriendCode(val code: String, val url: String)

    private val _friendCode = MutableStateFlow<FriendCode?>(null)
    val friendCode: StateFlow<FriendCode?> = _friendCode.asStateFlow()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _sharedCollections = MutableStateFlow<List<CollectionSummary>>(emptyList())
    val sharedCollections: StateFlow<List<CollectionSummary>> = _sharedCollections.asStateFlow()

    private val _savedCollections = MutableStateFlow<List<CollectionSummary>>(emptyList())
    val savedCollections: StateFlow<List<CollectionSummary>> = _savedCollections.asStateFlow()

    private val _feedEvents = MutableStateFlow<List<FeedEventDto>>(emptyList())
    val feedEvents: StateFlow<List<FeedEventDto>> = _feedEvents.asStateFlow()

    private val _discordLinked = MutableStateFlow(false)
    val discordLinked: StateFlow<Boolean> = _discordLinked.asStateFlow()

    private val _discordUsername = MutableStateFlow<String?>(null)
    val discordUsername: StateFlow<String?> = _discordUsername.asStateFlow()

    private val _feedHasMore = MutableStateFlow(false)
    val feedHasMore: StateFlow<Boolean> = _feedHasMore.asStateFlow()

    private val _isLoadingFeed = MutableStateFlow(false)
    val isLoadingFeed: StateFlow<Boolean> = _isLoadingFeed.asStateFlow()

    private var _isLoadingMoreFeed = false
    private var _currentFeedUserId: String? = null

    private val _eventComments = MutableStateFlow<Map<String, List<FeedComment>>>(emptyMap())
    val eventComments: StateFlow<Map<String, List<FeedComment>>> = _eventComments.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _notifications = MutableStateFlow<List<SocialNotification>>(emptyList())
    val notifications: StateFlow<List<SocialNotification>> = _notifications.asStateFlow()

    private val _notificationsHasMore = MutableStateFlow(false)
    val notificationsHasMore: StateFlow<Boolean> = _notificationsHasMore.asStateFlow()

    private val _isLoadingNotifications = MutableStateFlow(false)
    val isLoadingNotifications: StateFlow<Boolean> = _isLoadingNotifications.asStateFlow()

    private var _isLoadingMoreNotifications = false
    private var _notificationsCursor: String? = null

    private val _communityFollows = MutableStateFlow<List<CommunityFollow>>(emptyList())
    val communityFollows: StateFlow<List<CommunityFollow>> = _communityFollows.asStateFlow()

    private val _communityFeed = MutableStateFlow<List<FeedEventDto>>(emptyList())
    val communityFeed: StateFlow<List<FeedEventDto>> = _communityFeed.asStateFlow()

    private val _communityFeedHasMore = MutableStateFlow(false)
    val communityFeedHasMore: StateFlow<Boolean> = _communityFeedHasMore.asStateFlow()

    private val _isLoadingCommunityFeed = MutableStateFlow(false)
    val isLoadingCommunityFeed: StateFlow<Boolean> = _isLoadingCommunityFeed.asStateFlow()

    private var _isLoadingMoreCommunityFeed = false

    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _hiddenGameIds = MutableStateFlow<Set<Int>>(emptySet())
    val hiddenGameIds: StateFlow<Set<Int>> = _hiddenGameIds.asStateFlow()
    private var hiddenGameToggleJob: Job? = null
    private var pendingToggleGameId: Int? = null

    private val _usersCache = mutableMapOf<String, SocialUser>()

    val authState: StateFlow<SocialAuthManager.AuthState> = authManager.authState
    val serviceConnectionState: StateFlow<ArgosSocialService.ConnectionState> = socialService.connectionState

    init {
        observeAuthState()
        observeServiceState()
        observeIncomingMessages()
        observeSyncAchievementResults()
        observeAchievementUpdates()
        setupSessionRevokedCallback()
        seedHiddenGameIds()
        attemptAutoConnect()
    }

    private fun seedHiddenGameIds() {
        scope.launch {
            syncPreferencesRepository.hiddenGameIds().first().let {
                _hiddenGameIds.value = it
            }
        }
    }

    private fun setupSessionRevokedCallback() {
        socialService.onSessionRevoked = {
            scope.launch {
                Log.w(TAG, "Session revoked callback triggered")
                handleSessionRevoked()
            }
        }
    }

    private fun observeAuthState() {
        scope.launch {
            authManager.authState.collect { state ->
                when (state) {
                    is SocialAuthManager.AuthState.AwaitingLogin -> {
                        _connectionState.value = SocialConnectionState.AwaitingAuth(
                            qrUrl = state.qrUrl,
                            loginCode = state.loginCode
                        )
                    }
                    is SocialAuthManager.AuthState.Success -> {
                        Log.d(TAG, "Auth success observed, connecting service")
                        connectService()
                    }
                    is SocialAuthManager.AuthState.Error -> {
                        _connectionState.value = SocialConnectionState.Failed(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeServiceState() {
        scope.launch {
            socialService.connectionState.collect { state ->
                when (state) {
                    is ArgosSocialService.ConnectionState.Connected -> {
                        achievementSyncSuppressed = false
                        val prefs = preferencesRepository.userPreferences.first()
                        if (prefs.socialUsername != null) {
                            _connectionState.value = SocialConnectionState.Connected(
                                SocialUser(
                                    id = prefs.socialUserId ?: "",
                                    username = prefs.socialUsername,
                                    displayName = prefs.socialDisplayName ?: prefs.socialUsername,
                                    avatarColor = prefs.socialAvatarColor ?: "#6366f1"
                                )
                            )
                            syncPlaySessions()
                            syncUnsharedAchievements()
                            socialService.sendGetHiddenGames()
                        }
                    }
                    is ArgosSocialService.ConnectionState.Failed -> {
                        _connectionState.value = SocialConnectionState.Failed(state.reason)
                    }
                    is ArgosSocialService.ConnectionState.Disconnected -> {
                        val prefs = preferencesRepository.userPreferences.first()
                        if (!prefs.isSocialLinked) {
                            _connectionState.value = SocialConnectionState.Disconnected
                        }
                    }
                    is ArgosSocialService.ConnectionState.Connecting,
                    is ArgosSocialService.ConnectionState.Reconnecting -> {
                        _connectionState.value = SocialConnectionState.Connecting
                    }
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        scope.launch {
            socialService.incomingMessages.collect { message ->
                when (message) {
                    is ArgosSocialService.IncomingMessage.SessionRevoked -> {
                        Log.w(TAG, "Session revoked by server: ${message.reason}")
                        handleSessionRevoked()
                    }
                    is ArgosSocialService.IncomingMessage.FriendCodeData -> {
                        Log.d(TAG, "Received FriendCodeData: ${message.code}, url: ${message.url}")
                        _friendCode.value = FriendCode(message.code, message.url)
                    }
                    is ArgosSocialService.IncomingMessage.FriendAccepted -> {
                        val newFriend = Friend(
                            id = message.userId,
                            username = message.username,
                            displayName = message.displayName,
                            avatarColor = message.avatarColor,
                            status = "accepted"
                        )
                        _friends.value = _friends.value + newFriend
                    }
                    is ArgosSocialService.IncomingMessage.FriendAdded -> {
                        Log.d(TAG, "Friend added: ${message.username}")
                        val newFriend = Friend(
                            id = message.userId,
                            username = message.username,
                            displayName = message.displayName,
                            avatarColor = message.avatarColor,
                            status = "accepted"
                        )
                        if (_friends.value.none { it.id == message.userId }) {
                            _friends.value = _friends.value + newFriend
                        }
                    }
                    is ArgosSocialService.IncomingMessage.FriendRemoved -> {
                        Log.d(TAG, "Friend removed: ${message.userId}")
                        _friends.value = _friends.value.filter { it.id != message.userId }
                    }
                    is ArgosSocialService.IncomingMessage.PresenceUpdate -> {
                        val update = message.update
                        val newPresence = PresenceStatus.fromValue(update.status)
                        Log.d(TAG, "Presence update for ${update.userId}: ${update.status}, game=${update.game?.title}")

                        _friends.value = _friends.value.map { friend ->
                            if (friend.id == update.userId) {
                                val oldPresence = friend.presence
                                val oldGame = friend.currentGame

                                showPresenceNotificationIfNeeded(
                                    friend = friend,
                                    oldPresence = oldPresence,
                                    newPresence = newPresence,
                                    oldGame = oldGame,
                                    newGame = update.game
                                )

                                friend.copy(
                                    presence = newPresence,
                                    currentGame = update.game,
                                    deviceName = update.deviceName
                                )
                            } else {
                                friend
                            }
                        }
                    }
                    is ArgosSocialService.IncomingMessage.FavoriteFriendUpdated -> {
                        Log.d(TAG, "Favorite updated: ${message.friendId} -> ${message.isFavorite}")
                        _friends.value = _friends.value.map { friend ->
                            if (friend.id == message.friendId) friend.copy(isFavorite = message.isFavorite)
                            else friend
                        }.sortedWith(friendComparator)
                    }
                    is ArgosSocialService.IncomingMessage.FriendsData -> {
                        Log.d(TAG, "Received initial friends: ${message.friends.size}")
                        _friends.value = message.friends.sortedWith(friendComparator)
                        hasCompletedInitialSync = true
                    }
                    is ArgosSocialService.IncomingMessage.SharedCollections -> {
                        Log.d(TAG, "Received shared collections: ${message.collections.size}")
                        _sharedCollections.value = message.collections
                    }
                    is ArgosSocialService.IncomingMessage.SavedCollections -> {
                        Log.d(TAG, "Received saved collections: ${message.collections.size}")
                        _savedCollections.value = message.collections
                    }
                    is ArgosSocialService.IncomingMessage.FeedData -> {
                        val prevCount = _feedEvents.value.size
                        Log.d(TAG, "FeedData received: ${message.events.size} new events, loadingMore=$_isLoadingMoreFeed, prevCount=$prevCount")
                        _isLoadingFeed.value = false
                        if (_isLoadingMoreFeed) {
                            val combined = _feedEvents.value + message.events
                            Log.d(TAG, "FeedData appending: $prevCount + ${message.events.size} = ${combined.size} total")
                            _feedEvents.value = combined
                            _isLoadingMoreFeed = false
                        } else {
                            Log.d(TAG, "FeedData replacing: was $prevCount, now ${message.events.size}")
                            _feedEvents.value = message.events
                        }
                        _feedHasMore.value = message.hasMore
                        Log.d(TAG, "FeedData complete: total=${_feedEvents.value.size}, hasMore=${message.hasMore}")
                    }
                    is ArgosSocialService.IncomingMessage.FeedEvent -> {
                        val prevCount = _feedEvents.value.size
                        Log.d(TAG, "FeedEvent received: id=${message.event.id}, type=${message.event.type}, prepending to $prevCount events")
                        _feedEvents.value = listOf(message.event) + _feedEvents.value
                        Log.d(TAG, "FeedEvent added: total now ${_feedEvents.value.size}")
                    }
                    is ArgosSocialService.IncomingMessage.FeedEventUpdated -> {
                        Log.d(TAG, "FeedEventUpdated: id=${message.eventId}, likes=${message.likeCount}, comments=${message.commentCount}")
                        val found = _feedEvents.value.any { it.id == message.eventId }
                        Log.v(TAG, "FeedEventUpdated: event found in local list: $found")
                        _feedEvents.value = _feedEvents.value.map { event ->
                            if (event.id == message.eventId) {
                                Log.v(TAG, "FeedEventUpdated: updating event ${event.id} likes ${event.likeCount}->${message.likeCount}")
                                event.copy(
                                    likeCount = message.likeCount,
                                    commentCount = message.commentCount
                                )
                            } else event
                        }
                    }
                    is ArgosSocialService.IncomingMessage.EventCommentsData -> {
                        Log.d(TAG, "EventCommentsData: eventId=${message.eventId}, count=${message.comments.size}")
                        _eventComments.value = _eventComments.value + (message.eventId to message.comments)
                    }
                    is ArgosSocialService.IncomingMessage.FeedCommentReceived -> {
                        Log.d(TAG, "FeedCommentReceived: eventId=${message.eventId}, commentId=${message.comment.id}")
                        val current = _eventComments.value[message.eventId] ?: emptyList()
                        _eventComments.value = _eventComments.value + (message.eventId to (current + message.comment))
                    }
                    is ArgosSocialService.IncomingMessage.RequestGameData -> {
                        Log.d(TAG, "Server requesting game data: igdbId=${message.igdbId}, title=${message.gameTitle}")
                        handleGameDataRequest(message.igdbId, message.gameTitle, message.fields)
                    }
                    is ArgosSocialService.IncomingMessage.DiscordTokens -> {
                        Log.d(TAG, "Received Discord tokens (expires in ${message.expiresIn}s)")
                        discordTokenHolder.update(message.accessToken, message.tokenType, message.expiresIn)
                        _discordLinked.value = true
                    }
                    is ArgosSocialService.IncomingMessage.DiscordNotLinked -> {
                        Log.d(TAG, "Discord not linked")
                        discordTokenHolder.clear()
                        _discordLinked.value = false
                        _discordUsername.value = null
                    }
                    is ArgosSocialService.IncomingMessage.UnreadCount -> {
                        _unreadCount.value = message.count
                    }
                    is ArgosSocialService.IncomingMessage.NotificationReceived -> {
                        _usersCache.putAll(message.users)
                        val resolved = resolveNotificationActors(message.notification)
                        _notifications.value = trimNotifications(listOf(resolved) + _notifications.value)
                    }
                    is ArgosSocialService.IncomingMessage.NotificationsData -> {
                        _usersCache.putAll(message.users)
                        _isLoadingNotifications.value = false
                        if (message.notifications.isNotEmpty()) {
                            _notificationsCursor = message.notifications.last().updatedAt
                        }
                        val resolved = message.notifications.map { resolveNotificationActors(it) }
                        if (_isLoadingMoreNotifications) {
                            _notifications.value = trimNotifications(_notifications.value + resolved)
                            _isLoadingMoreNotifications = false
                        } else {
                            _notifications.value = trimNotifications(resolved)
                        }
                        _notificationsHasMore.value = message.hasMore
                    }
                    is ArgosSocialService.IncomingMessage.CommunityFeedData -> {
                        if (_isLoadingMoreCommunityFeed) {
                            _communityFeed.value = _communityFeed.value + message.events
                        } else {
                            _communityFeed.value = message.events
                        }
                        _communityFeedHasMore.value = message.hasMore
                        _isLoadingCommunityFeed.value = false
                        _isLoadingMoreCommunityFeed = false
                        Log.d(TAG, "CommunityFeedData: total=${_communityFeed.value.size}, hasMore=${message.hasMore}")
                    }
                    is ArgosSocialService.IncomingMessage.CommunityFollowsData -> {
                        _communityFollows.value = message.follows
                        Log.d(TAG, "CommunityFollows: ${message.follows.size}")
                    }
                    is ArgosSocialService.IncomingMessage.CommunityFollowUpdated -> {
                        val existing = _communityFollows.value.toMutableList()
                        val idx = existing.indexOfFirst { it.igdbGameId == message.follow.igdbGameId }
                        if (idx >= 0) {
                            existing[idx] = message.follow
                        } else {
                            existing.add(message.follow)
                        }
                        _communityFollows.value = existing
                        Log.d(TAG, "CommunityFollowUpdated: igdbGameId=${message.follow.igdbGameId}")
                    }
                    is ArgosSocialService.IncomingMessage.UserSettingsData -> {
                        _userSettings.value = message.settings
                        Log.d(TAG, "UserSettings: autoShare=${message.settings.communityAutoShare}")
                    }
                    is ArgosSocialService.IncomingMessage.HiddenGames -> {
                        val pending = pendingToggleGameId
                        val merged = if (pending != null && hiddenGameToggleJob?.isActive == true) {
                            val localHas = pending in _hiddenGameIds.value
                            val serverIds = message.igdbGameIds - pending
                            if (localHas) serverIds + pending else serverIds
                        } else {
                            message.igdbGameIds
                        }
                        _hiddenGameIds.value = merged
                        syncPreferencesRepository.setHiddenGameIds(merged)
                        Log.d(TAG, "HiddenGames: ${message.igdbGameIds.size} from server, pending=$pending")
                    }
                    is ArgosSocialService.IncomingMessage.Error -> {
                        if (message.code == "unknown_type") {
                            Log.w(TAG, "Server returned unknown_type -- suppressing achievement sync for this session")
                            achievementSyncSuppressed = true
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun handleSessionRevoked() {
        hasCompletedInitialSync = false
        preferencesRepository.clearSocialCredentials()
        authManager.reset()
        discordTokenHolder.clear()
        _discordLinked.value = false
        _discordUsername.value = null
        _connectionState.value = SocialConnectionState.Disconnected
        clearSocialData()
    }

    private fun attemptAutoConnect() {
        scope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.isSocialLinked && prefs.socialSessionToken != null) {
                Log.d(TAG, "Auto-connecting with stored session token")
                connectService()
            }
        }
    }

    private suspend fun connectService() {
        val prefs = preferencesRepository.userPreferences.first()
        val token = prefs.socialSessionToken
        Log.d(TAG, "connectService: hasToken=${token != null}")
        if (token == null) {
            Log.w(TAG, "connectService: No token available")
            return
        }
        socialService.disconnect()
        socialService.connect(token)
    }

    suspend fun startAuth(): SocialAuthManager.AuthResult {
        _connectionState.value = SocialConnectionState.Connecting
        return authManager.startAuth()
    }

    fun cancelAuth() {
        authManager.cancelAuth()
        _connectionState.value = SocialConnectionState.Disconnected
    }

    suspend fun logout() {
        hasCompletedInitialSync = false
        socialService.disconnect()
        authManager.logout()
        discordTokenHolder.clear()
        _discordLinked.value = false
        _discordUsername.value = null
        _connectionState.value = SocialConnectionState.Disconnected
        clearSocialData()
    }

    private fun clearSocialData() {
        _friends.value = emptyList()
        _friendCode.value = null
        _sharedCollections.value = emptyList()
        _savedCollections.value = emptyList()
        _feedEvents.value = emptyList()
        _eventComments.value = emptyMap()
        _feedHasMore.value = false
        _notifications.value = emptyList()
        _unreadCount.value = 0
        _notificationsHasMore.value = false
        _isLoadingNotifications.value = false
        _notificationsCursor = null
        _usersCache.clear()
    }

    fun sendPresence(status: PresenceStatus, gameIgdbId: Int? = null, gameTitle: String? = null): Boolean {
        if (!socialService.isConnected()) return false
        return socialService.sendPresence(status, gameIgdbId, gameTitle, android.os.Build.MODEL)
    }

    fun isConnected(): Boolean = socialService.isConnected()

    fun reconnectIfNeeded() {
        socialService.reconnectIfNeeded()
    }

    fun requestDiscordTokens() {
        if (socialService.isConnected()) {
            socialService.requestDiscordTokens()
        }
    }

    fun toggleFavoriteFriend(friendId: String) {
        if (socialService.isConnected()) {
            socialService.toggleFavoriteFriend(friendId)
        }
    }

    fun toggleGameVisibility(igdbGameId: Int) {
        if (!socialService.isConnected()) return
        val isHidden = igdbGameId in _hiddenGameIds.value
        if (isHidden) {
            _hiddenGameIds.value = _hiddenGameIds.value - igdbGameId
            socialService.sendUnhideGame(igdbGameId)
        } else {
            _hiddenGameIds.value = _hiddenGameIds.value + igdbGameId
            socialService.sendHideGame(igdbGameId)
        }
        pendingToggleGameId = igdbGameId
        hiddenGameToggleJob?.cancel()
        hiddenGameToggleJob = scope.launch {
            delay(500)
            syncPreferencesRepository.setHiddenGameIds(_hiddenGameIds.value)
            pendingToggleGameId = null
        }
    }

    fun requestFriendCode() {
        val connected = socialService.isConnected()
        Log.d(TAG, "requestFriendCode: isConnected=$connected, serviceState=${socialService.connectionState.value}, repoState=${_connectionState.value}")
        if (connected) {
            socialService.getFriendCode()
            Log.d(TAG, "requestFriendCode: sent getFriendCode request")
        } else {
            Log.w(TAG, "requestFriendCode: not connected, request not sent")
        }
    }

    fun regenerateFriendCode() {
        if (socialService.isConnected()) {
            socialService.regenerateFriendCode()
        }
    }

    fun addFriendByCode(code: String) {
        if (socialService.isConnected()) {
            socialService.addFriendByCode(code)
        }
    }

    suspend fun syncPlaySessions(): SyncResult {
        val prefs = preferencesRepository.userPreferences.first()
        val lastSync = prefs.lastPlaySessionSync

        val sessions = if (lastSync != null) {
            playSessionDao.getUnsyncedSessions(lastSync)
        } else {
            playSessionDao.getAllUnsyncedSessions()
        }

        if (sessions.isEmpty()) {
            return SyncResult.Success(0)
        }

        var queued = 0
        for (session in sessions) {
            val userId = session.userId ?: continue
            val payload = ArgosSocialService.PlaySessionPayload(
                userId = userId,
                deviceId = session.deviceId,
                deviceManufacturer = session.deviceManufacturer,
                deviceModel = session.deviceModel,
                igdbId = session.igdbId,
                gameTitle = session.gameTitle,
                platformSlug = session.platformSlug,
                startTime = session.startTime.toString(),
                endTime = session.endTime.toString(),
                continued = session.continued,
                standbyMs = session.standbyMs,
                activePlayMs = session.activePlayMs
            )

            val payloadJson = JSONObject(payload.toMap().mapValues { (_, v) -> v ?: JSONObject.NULL }).toString()
            pendingSocialSyncDao.insert(
                PendingSocialSyncEntity(
                    syncType = SocialSyncType.PLAY_SESSION,
                    payloadJson = payloadJson,
                    occurredAt = session.startTime
                )
            )
            queued++
        }

        if (queued > 0) {
            val maxEndTime = sessions.maxOf { it.endTime }
            preferencesRepository.setLastPlaySessionSyncTime(maxEndTime)
            Log.d(TAG, "Queued $queued play sessions for sync, lastSync updated to $maxEndTime")

            if (socialService.isConnected()) {
                scope.launch { socialSyncCoordinator.get().processQueue() }
            }
        }

        return SyncResult.Success(queued)
    }

    private fun observeAchievementUpdates() {
        scope.launch {
            achievementUpdateBus.updates.collect {
                syncUnsharedAchievements()
            }
        }
    }

    private fun observeSyncAchievementResults() {
        scope.launch {
            socialService.syncAchievementResult.collect { acceptedIds ->
                if (acceptedIds.isNotEmpty()) {
                    achievementDao.markSocialSharedBatch(acceptedIds, System.currentTimeMillis())
                    Log.d(TAG, "Marked ${acceptedIds.size} achievements as socially shared")
                }
            }
        }
    }

    suspend fun syncUnsharedAchievements() {
        if (!socialService.isConnected()) return
        if (achievementSyncSuppressed) return

        val unshared = achievementDao.getUnsharedUnlocked(50)
        if (unshared.isEmpty()) return

        val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        val recent = unshared.filter {
            (it.unlockedHardcoreAt ?: it.unlockedAt ?: 0L) >= cutoff
        }
        val stale = unshared.filter {
            (it.unlockedHardcoreAt ?: it.unlockedAt ?: 0L) < cutoff
        }
        if (stale.isNotEmpty()) {
            achievementDao.markSocialSharedBatch(stale.map { it.raId }, System.currentTimeMillis())
            Log.d(TAG, "Pre-marked ${stale.size} stale achievements as shared")
        }

        if (recent.isEmpty()) return

        recent.groupBy { it.gameId }.forEach { (_, achievements) ->
            val first = achievements.first()
            socialService.syncAchievementUnlocks(
                igdbId = first.gameIgdbId,
                raGameId = first.gameRaId,
                gameTitle = first.gameTitle,
                unlocks = achievements.map { ach ->
                    mapOf(
                        "ra_id" to ach.raId,
                        "name" to ach.title,
                        "description" to ach.description,
                        "points" to ach.points,
                        "badge_name" to ach.badgeUrl?.substringAfterLast("/")?.removeSuffix(".png"),
                        "is_hardcore" to (ach.unlockedHardcoreAt != null),
                        "unlocked_at" to Instant.ofEpochMilli(
                            ach.unlockedHardcoreAt ?: ach.unlockedAt!!
                        ).toString()
                    )
                }
            )
        }
        Log.d(TAG, "Sent ${recent.size} unshared achievements for sync")
    }

    sealed class SyncResult {
        data object NotConnected : SyncResult()
        data class Success(val count: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    fun requestFeed(limit: Int = FEED_PAGE_SIZE, beforeId: String? = null, userId: String? = null) {
        Log.d(TAG, "requestFeed: limit=$limit, beforeId=$beforeId, userId=$userId, connected=${socialService.isConnected()}")
        if (socialService.isConnected()) {
            _isLoadingFeed.value = true
            _currentFeedUserId = userId
            Log.d(TAG, "requestFeed: sending request, currentFeedUserId=$_currentFeedUserId")
            socialService.getFeed(limit, beforeId, userId)
        } else {
            Log.w(TAG, "requestFeed: not connected, request not sent")
        }
    }

    fun loadMoreFeed() {
        val lastEvent = _feedEvents.value.lastOrNull()
        Log.d(TAG, "loadMoreFeed: lastEvent=${lastEvent?.id}, hasMore=$_feedHasMore, isLoading=${_isLoadingFeed.value}")
        if (lastEvent == null) {
            Log.w(TAG, "loadMoreFeed: no events to paginate from")
            return
        }
        if (_feedHasMore.value && !_isLoadingFeed.value) {
            Log.d(TAG, "loadMoreFeed: requesting more with beforeId=${lastEvent.id}, userId=$_currentFeedUserId")
            _isLoadingFeed.value = true
            _isLoadingMoreFeed = true
            socialService.getFeed(FEED_PAGE_SIZE, lastEvent.id, _currentFeedUserId)
        } else {
            Log.d(TAG, "loadMoreFeed: skipped - hasMore=${_feedHasMore.value}, isLoading=${_isLoadingFeed.value}")
        }
    }

    fun likeEvent(eventId: String) {
        Log.d(TAG, "likeEvent: eventId=$eventId, connected=${socialService.isConnected()}")
        if (socialService.isConnected()) {
            val events = _feedEvents.value.toMutableList()
            val index = events.indexOfFirst { it.id == eventId }
            if (index >= 0) {
                val event = events[index]
                val newLiked = !event.isLikedByMe
                val newCount = if (newLiked) event.likeCount + 1 else event.likeCount - 1
                Log.d(TAG, "likeEvent: optimistic update - liked=$newLiked, count=${event.likeCount}->$newCount")
                events[index] = event.copy(isLikedByMe = newLiked, likeCount = newCount.coerceAtLeast(0))
                _feedEvents.value = events
            } else {
                Log.w(TAG, "likeEvent: event $eventId not found in local list")
            }
            socialService.likeEvent(eventId)
        }
    }

    fun hideEvent(eventId: String) {
        Log.d(TAG, "hideEvent: eventId=$eventId, connected=${socialService.isConnected()}")
        if (socialService.isConnected()) {
            val prevCount = _feedEvents.value.size
            _feedEvents.value = _feedEvents.value.filter { it.id != eventId }
            Log.d(TAG, "hideEvent: removed locally, count $prevCount -> ${_feedEvents.value.size}")
            socialService.hideEvent(eventId)
        }
    }

    fun reportEvent(eventId: String, reason: String? = null) {
        Log.d(TAG, "reportEvent: eventId=$eventId, reason=$reason, connected=${socialService.isConnected()}")
        if (socialService.isConnected()) {
            socialService.reportEvent(eventId, reason)
        }
    }

    fun requestNotifications(limit: Int = NOTIFICATIONS_PAGE_SIZE) {
        if (socialService.isConnected()) {
            _isLoadingNotifications.value = true
            _notificationsCursor = null
            socialService.getNotifications(limit)
        }
    }

    fun loadMoreNotifications() {
        val cursor = _notificationsCursor ?: return
        if (_notificationsHasMore.value && !_isLoadingNotifications.value) {
            _isLoadingNotifications.value = true
            _isLoadingMoreNotifications = true
            socialService.getNotifications(NOTIFICATIONS_PAGE_SIZE, cursor)
        }
    }

    fun markNotificationRead(notificationId: String) {
        if (socialService.isConnected()) {
            socialService.markNotificationRead(notificationId = notificationId)
            _notifications.value = _notifications.value.map {
                if (it.id == notificationId && it.isUnread) {
                    it.copy(viewedAt = java.time.Instant.now().toString())
                } else it
            }
        }
    }

    fun markAllNotificationsRead() {
        if (socialService.isConnected()) {
            socialService.markAllNotificationsRead()
            val now = java.time.Instant.now().toString()
            _notifications.value = _notifications.value.map {
                it.copy(viewedAt = now)
            }
            _unreadCount.value = 0
        }
    }

    fun getEvent(eventId: String) {
        if (socialService.isConnected()) {
            socialService.getEvent(eventId)
        }
    }

    private fun resolveNotificationActors(notification: SocialNotification): SocialNotification {
        val resolved = notification.actors.mapNotNull { _usersCache[it] }
        return notification.copy(resolvedActors = resolved)
    }

    private fun trimNotifications(list: List<SocialNotification>): List<SocialNotification> {
        return if (list.size > NOTIFICATIONS_MAX_CACHE) list.take(NOTIFICATIONS_MAX_CACHE) else list
    }

    fun requestEventComments(eventId: String) {
        Log.d(TAG, "requestEventComments: eventId=$eventId, connected=${socialService.isConnected()}")
        if (socialService.isConnected()) {
            socialService.getEventComments(eventId)
        }
    }

    fun commentEvent(eventId: String, content: String) {
        Log.d(TAG, "commentEvent: eventId=$eventId, connected=${socialService.isConnected()}")
        if (socialService.isConnected()) {
            socialService.commentEvent(eventId, content)
        }
    }

    fun createPost(
        body: String,
        canvasSize: Int? = null,
        doodleData: String? = null,
        igdbId: Int? = null,
        gameTitle: String? = null,
        public: Boolean = false
    ) {
        if (socialService.isConnected()) {
            socialService.createPost(body, canvasSize, doodleData, igdbId, gameTitle, public)
        }
    }

    fun requestCommunityFeed(igdbGameId: Int? = null, limit: Int = FEED_PAGE_SIZE) {
        if (socialService.isConnected()) {
            _isLoadingCommunityFeed.value = true
            _isLoadingMoreCommunityFeed = false
            socialService.getCommunityFeed(igdbGameId, limit)
        }
    }

    fun loadMoreCommunityFeed(igdbGameId: Int? = null) {
        val last = _communityFeed.value.lastOrNull() ?: return
        if (_communityFeedHasMore.value && !_isLoadingCommunityFeed.value) {
            _isLoadingCommunityFeed.value = true
            _isLoadingMoreCommunityFeed = true
            socialService.getCommunityFeed(igdbGameId, FEED_PAGE_SIZE, last.id)
        }
    }

    fun requestCommunityFollows() {
        if (socialService.isConnected()) {
            socialService.getCommunityFollows()
        }
    }

    fun followCommunity(igdbGameId: Int, isPrivate: Boolean = false, anonymous: Boolean = false) {
        if (socialService.isConnected()) {
            socialService.followCommunity(igdbGameId, isPrivate, anonymous)
        }
    }

    fun unfollowCommunity(igdbGameId: Int) {
        if (socialService.isConnected()) {
            socialService.unfollowCommunity(igdbGameId)
            _communityFollows.value = _communityFollows.value.filter { it.igdbGameId != igdbGameId }
        }
    }

    fun updateCommunityFollow(igdbGameId: Int, isPrivate: Boolean, anonymous: Boolean) {
        if (socialService.isConnected()) {
            socialService.updateCommunityFollow(igdbGameId, isPrivate, anonymous)
        }
    }

    fun requestUserSettings() {
        if (socialService.isConnected()) {
            socialService.getUserSettings()
        }
    }

    fun updateUserSettings(communityAutoShare: Boolean? = null) {
        if (socialService.isConnected()) {
            socialService.updateUserSettings(communityAutoShare)
            if (communityAutoShare != null) {
                _userSettings.value = _userSettings.value.copy(communityAutoShare = communityAutoShare)
            }
        }
    }

    fun updateEventVisibility(eventId: String, public: Boolean) {
        if (socialService.isConnected()) {
            socialService.updateEventVisibility(eventId, public)
        }
    }

    fun emitAchievementUnlocked(
        igdbId: Long?,
        raGameId: Long?,
        gameTitle: String,
        achievementRaId: Long,
        achievementName: String,
        achievementDescription: String?,
        points: Int,
        badgeName: String?,
        isHardcore: Boolean,
        earnedCount: Int,
        totalCount: Int,
        unlockedAt: Long
    ): Boolean {
        if (!socialService.isConnected()) return false
        return socialService.createFeedEvent(
            eventType = FeedEventType.ACHIEVEMENT_UNLOCKED.value,
            igdbId = igdbId,
            gameTitle = gameTitle,
            data = mapOf(
                "achievement_ra_id" to achievementRaId,
                "achievement_name" to achievementName,
                "achievement_description" to achievementDescription,
                "ra_game_id" to raGameId,
                "points" to points,
                "badge_name" to badgeName,
                "is_hardcore" to isHardcore,
                "earned_count" to earnedCount,
                "total_count" to totalCount,
                "unlocked_at" to Instant.ofEpochMilli(unlockedAt).toString()
            )
        )
    }

    fun emitPerfectGame(
        igdbId: Long?,
        gameTitle: String,
        isHardcore: Boolean,
        earnedCount: Int,
        totalCount: Int
    ): Boolean {
        if (!socialService.isConnected()) return false
        return socialService.createFeedEvent(
            eventType = FeedEventType.PERFECT_GAME.value,
            igdbId = igdbId,
            gameTitle = gameTitle,
            data = mapOf(
                "is_hardcore" to isHardcore,
                "earned_count" to earnedCount,
                "total_count" to totalCount
            )
        )
    }

    suspend fun emitStartedPlaying(igdbId: Long?, gameTitle: String, platformSlug: String) {
        queueFeedEvent(
            eventType = FeedEventType.STARTED_PLAYING.value,
            igdbId = igdbId,
            gameTitle = gameTitle,
            data = mapOf("platform_slug" to platformSlug)
        )
    }

    suspend fun emitMarathonSession(igdbId: Long?, gameTitle: String, durationMins: Int, platformSlug: String) {
        queueFeedEvent(
            eventType = FeedEventType.MARATHON_SESSION.value,
            igdbId = igdbId,
            gameTitle = gameTitle,
            data = mapOf(
                "duration_mins" to durationMins,
                "platform_slug" to platformSlug
            )
        )
    }

    private suspend fun queueFeedEvent(
        eventType: String,
        igdbId: Long?,
        gameTitle: String,
        data: Map<String, Any?>
    ) {
        val payloadJson = JSONObject(buildMap<String, Any?> {
            put("event_type", eventType)
            put("igdb_id", igdbId)
            put("game_title", gameTitle)
            put("data", JSONObject(data))
        }).toString()

        pendingSocialSyncDao.insert(
            PendingSocialSyncEntity(
                syncType = SocialSyncType.FEED_EVENT,
                payloadJson = payloadJson,
                occurredAt = Instant.now()
            )
        )

        Log.d(TAG, "Queued feed event: $eventType for $gameTitle")

        if (socialService.isConnected()) {
            scope.launch { socialSyncCoordinator.get().processQueue() }
        }
    }

    fun createDoodle(
        canvasSize: Int,
        data: String,
        caption: String?,
        igdbId: Int?,
        gameTitle: String?
    ) {
        if (socialService.isConnected()) {
            socialService.createDoodle(canvasSize, data, caption, igdbId, gameTitle)
        }
    }

    private suspend fun handleGameDataRequest(igdbId: Long, gameTitle: String, fields: List<String>) {
        val game = gameDao.getByIgdbId(igdbId)
        if (game == null) {
            Log.w(TAG, "Game not found in local DB for igdbId=$igdbId, sending minimal response")
            socialService.sendGameData(
                igdbId = igdbId,
                gameTitle = gameTitle,
                developer = null,
                releaseYear = null,
                genre = null,
                description = null,
                coverThumbBase64 = null,
                gradientColors = null
            )
            return
        }

        Log.d(TAG, "Found game: ${game.title}, coverPath=${game.coverPath}, generating response for fields=$fields")

        val coverThumbBase64 = if ("cover_thumb" in fields && game.coverPath != null) {
            generateCoverThumbnail(game.coverPath)
        } else null

        socialService.sendGameData(
            igdbId = igdbId,
            gameTitle = game.title,
            developer = if ("developer" in fields) game.developer else null,
            releaseYear = if ("release_year" in fields) game.releaseYear else null,
            genre = if ("genre" in fields) game.genre else null,
            description = if ("description" in fields) game.description else null,
            coverThumbBase64 = coverThumbBase64,
            gradientColors = if ("gradient_colors" in fields) game.gradientColors else null
        )
    }

    private fun generateCoverThumbnail(coverPath: String): String? {
        return try {
            val file = File(coverPath)
            if (!file.exists()) {
                Log.w(TAG, "Cover file doesn't exist: $coverPath")
                return null
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(coverPath, options)

            val targetWidth = THUMB_WIDTH
            val targetHeight = THUMB_HEIGHT
            val sampleSize = calculateInSampleSize(options, targetWidth, targetHeight)

            options.apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
            }

            val bitmap = BitmapFactory.decodeFile(coverPath, options) ?: return null
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, outputStream)

            if (bitmap != scaledBitmap) bitmap.recycle()
            scaledBitmap.recycle()

            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            Log.d(TAG, "Generated thumbnail: ${base64.length} chars (~${outputStream.size()} bytes)")
            base64
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate cover thumbnail", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun showPresenceNotificationIfNeeded(
        friend: Friend,
        oldPresence: PresenceStatus?,
        newPresence: PresenceStatus,
        oldGame: PresenceGameInfo?,
        newGame: PresenceGameInfo?
    ) {
        if (!hasCompletedInitialSync) return

        scope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            if (!prefs.socialOnlineStatusEnabled) return@launch
            if (prefs.socialSuppressNotificationsInGame && playSessionTracker.activeSession.value != null) return@launch

            val wasOfflineOrAway = oldPresence == null ||
                oldPresence == PresenceStatus.OFFLINE ||
                oldPresence == PresenceStatus.AWAY
            val isNowOnline = newPresence == PresenceStatus.ONLINE ||
                newPresence == PresenceStatus.IN_GAME

            val startedPlayingNewGame = newPresence == PresenceStatus.IN_GAME &&
                newGame != null &&
                (oldPresence != PresenceStatus.IN_GAME || oldGame?.title != newGame.title)

            val avatarColorInt = parseAvatarColorInt(friend.avatarColor)

            val title: String?
            val subtitle: String?

            when {
                startedPlayingNewGame && prefs.socialNotifyFriendPlaying -> {
                    title = friend.displayName
                    subtitle = "Started playing ${newGame?.title}"
                    val coverPath = newGame?.coverThumb?.let { saveTempCover(it, friend.id) }
                    notificationManager.show(
                        title = title,
                        subtitle = subtitle,
                        imagePath = coverPath,
                        type = NotificationType.INFO,
                        duration = NotificationDuration.SHORT,
                        key = "presence_${friend.id}",
                        accentColor = avatarColorInt
                    )
                }
                wasOfflineOrAway && isNowOnline && !startedPlayingNewGame && prefs.socialNotifyFriendOnline -> {
                    title = friend.displayName
                    subtitle = "Is now online"
                    notificationManager.show(
                        title = title,
                        subtitle = subtitle,
                        type = NotificationType.INFO,
                        duration = NotificationDuration.SHORT,
                        key = "presence_${friend.id}",
                        accentColor = avatarColorInt
                    )
                }
                else -> {
                    title = null
                    subtitle = null
                }
            }

            if (title != null && !isAppInForeground()) {
                SocialOverlayService.show(context, title, subtitle)
            }
        }
    }

    private fun saveTempCover(base64: String, friendId: String): String? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val cacheDir = File(context.cacheDir, "presence_covers")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "cover_$friendId.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save temp cover", e)
            null
        }
    }

    private fun parseAvatarColorInt(hexColor: String): Int? {
        return try {
            android.graphics.Color.parseColor(hexColor)
        } catch (_: Exception) {
            null
        }
    }

    private fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private val friendComparator = compareByDescending<Friend> { it.isFavorite }
        .thenBy { it.displayName.lowercase() }

    companion object {
        private const val THUMB_WIDTH = 144
        private const val THUMB_HEIGHT = 192
        private const val THUMB_QUALITY = 80
        private const val TAG = "SocialRepository"
        private const val FEED_PAGE_SIZE = 10
        private const val NOTIFICATIONS_PAGE_SIZE = 20
        private const val NOTIFICATIONS_MAX_CACHE = 100
    }
}
