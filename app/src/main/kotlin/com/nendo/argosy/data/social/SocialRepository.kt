package com.nendo.argosy.data.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.netplay.external.ExternalNetplay
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSocialSyncDao
import com.nendo.argosy.data.local.dao.PlayRecordDeletionDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.cutoffsFor
import com.nendo.argosy.data.local.dao.resolved
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.PendingSocialSyncEntity
import com.nendo.argosy.data.local.entity.SocialSyncType
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.uploader.PlaySessionBackfill
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.sync.SocialSyncCoordinator
import com.nendo.argosy.core.event.AchievementUpdateBus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val OFFLINE_GRACE_MS = 20_000L
private const val ONLINE_NOTICE_MIN_OFFLINE_MS = 5 * 60_000L

@Singleton
class SocialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: SocialAuthManager,
    private val socialService: ArgosSocialService,
    private val preferencesRepository: UserPreferencesRepository,
    private val playSessionDao: PlaySessionDao,
    private val playRecordDeletionDao: PlayRecordDeletionDao,
    private val pendingSocialSyncDao: PendingSocialSyncDao,
    private val gameDao: GameDao,
    private val achievementDao: AchievementDao,
    private val notificationManager: NotificationManager,
    private val discordTokenHolder: DiscordTokenHolder,
    private val playSessionTracker: PlaySessionTracker,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val socialSyncCoordinator: Lazy<SocialSyncCoordinator>,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val imageCacheManager: ImageCacheManager,
    private val playSessionBackfill: PlaySessionBackfill,
    private val attributionRepository: StorageAttributionRepository,
    private val quayPassService: Lazy<com.nendo.argosy.data.quaypass.QuayPassService>,
    private val gameArtDao: com.nendo.argosy.data.local.dao.GameArtDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var hasCompletedInitialSync = false
    private val pendingOffline = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val offlineSince = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var achievementSyncSuppressed = false

    private val _connectionState = MutableStateFlow<SocialConnectionState>(SocialConnectionState.Disconnected)
    val connectionState: StateFlow<SocialConnectionState> = _connectionState.asStateFlow()

    data class FriendCode(val code: String, val url: String)

    private val _friendCode = MutableStateFlow<FriendCode?>(null)
    val friendCode: StateFlow<FriendCode?> = _friendCode.asStateFlow()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _recentPlaysByFriend = MutableStateFlow<Map<String, List<ActiveGameRow>>>(emptyMap())
    private var recentPlaysFetchedAtMillis = 0L

    /**
     * Friends tied to each game, keyed by the game's IGDB id: playing it now, or played it inside
     * the recent-play window. The one source every surface that names friends beside a game reads.
     */
    val friendsActivity: StateFlow<Map<Int, List<FriendActivity>>> =
        combine(_friends, _recentPlaysByFriend) { friends, plays ->
            mergeFriendActivity(
                live = liveFriendActivity(friends),
                recent = recentFriendActivity(friends, plays, System.currentTimeMillis())
            )
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private fun refreshRecentFriendPlays() {
        scope.launch {
            val now = System.currentTimeMillis()
            if (now - recentPlaysFetchedAtMillis < RECENT_PLAYS_REFRESH_MILLIS) return@launch
            recentPlaysFetchedAtMillis = now
            val fetched = _friends.value
                .filter { it.isAccepted }
                .mapNotNull { friend ->
                    socialService.requestActiveGames(friend.id)?.let { friend.id to it }
                }
            Log.d(TAG, "Recent friend plays: ${fetched.sumOf { it.second.size }} rows from ${fetched.size} friends")
            _recentPlaysByFriend.update { it + fetched }
        }
    }

    private val _quayPassCheckins = MutableStateFlow<List<QuayPassCheckin>>(emptyList())
    val quayPassCheckins: StateFlow<List<QuayPassCheckin>> = _quayPassCheckins.asStateFlow()

    private val _sharedCollections = MutableStateFlow<List<CollectionSummary>>(emptyList())
    val sharedCollections: StateFlow<List<CollectionSummary>> = _sharedCollections.asStateFlow()

    private val _savedCollections = MutableStateFlow<List<CollectionSummary>>(emptyList())
    val savedCollections: StateFlow<List<CollectionSummary>> = _savedCollections.asStateFlow()

    private val _feedEvents = MutableStateFlow<List<FeedEventDto>>(emptyList())
    val feedEvents: StateFlow<List<FeedEventDto>> = _feedEvents.asStateFlow()

    /**
     * Keyed by igdb id so both the primary and the companion read the same reviews for a game
     * instead of each fetching its own copy.
     */
    private val _reviewSummaries = MutableStateFlow<Map<Int, ReviewSummary>>(emptyMap())
    val reviewSummaries: StateFlow<Map<Int, ReviewSummary>> = _reviewSummaries.asStateFlow()

    private val _gameReviews = MutableStateFlow<Map<Int, GameReviewsPage>>(emptyMap())
    val gameReviews: StateFlow<Map<Int, GameReviewsPage>> = _gameReviews.asStateFlow()

    private val _reviewWriteEvents = MutableSharedFlow<ReviewWriteEvent>(extraBufferCapacity = 8)
    val reviewWriteEvents: SharedFlow<ReviewWriteEvent> = _reviewWriteEvents.asSharedFlow()

    private var pendingReviewWriteIgdbId: Int? = null

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

    private val _userProfile = MutableStateFlow<UserProfileData?>(null)
    val userProfile: StateFlow<UserProfileData?> = _userProfile.asStateFlow()

    private val _isLoadingProfile = MutableStateFlow(false)
    val isLoadingProfile: StateFlow<Boolean> = _isLoadingProfile.asStateFlow()

    private val _usersCache = mutableMapOf<String, SocialUser>()

    private val _netplayInvites = MutableSharedFlow<NetplayInvitePayload>(
        replay = 0,
        extraBufferCapacity = 4
    )
    val netplayInvites: SharedFlow<NetplayInvitePayload> = _netplayInvites.asSharedFlow()

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
                        abandonInFlightRequests()
                        _connectionState.update { SocialConnectionState.Failed(state.reason) }
                    }
                    is ArgosSocialService.ConnectionState.Disconnected -> {
                        abandonInFlightRequests()
                        _connectionState.update { current ->
                            if (current is SocialConnectionState.AwaitingAuth) current
                            else SocialConnectionState.Disconnected
                        }
                    }
                    is ArgosSocialService.ConnectionState.Connecting,
                    is ArgosSocialService.ConnectionState.Reconnecting -> {
                        abandonInFlightRequests()
                        _connectionState.update { SocialConnectionState.Connecting }
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
                        Log.d(TAG, "Friend accepted: ${message.username}; list updates via the friends push")
                    }
                    is ArgosSocialService.IncomingMessage.FriendAdded -> {
                        Log.d(TAG, "Friend added: ${message.username}; list updates via the friends push")
                    }
                    is ArgosSocialService.IncomingMessage.FriendRemoved -> {
                        Log.d(TAG, "Friend removed: ${message.userId}; list updates via the friends push")
                    }
                    is ArgosSocialService.IncomingMessage.PresenceUpdate -> {
                        val update = message.update
                        val newPresence = PresenceStatus.fromValue(update.status)
                        Log.d(TAG, "Presence update for ${update.userId}: ${update.status}, game=${update.game?.title}")
                        onPresenceUpdate(update, newPresence)
                    }
                    is ArgosSocialService.IncomingMessage.FavoriteFriendUpdated -> {
                        Log.d(TAG, "Favorite updated: ${message.friendId} -> ${message.isFavorite}")
                        _friends.update { friends ->
                            friends.map { friend ->
                                if (friend.id == message.friendId) friend.copy(isFavorite = message.isFavorite)
                                else friend
                            }.sortedWith(friendComparator)
                        }
                    }
                    is ArgosSocialService.IncomingMessage.QuayPassAvatarUpdated -> {
                        message.ownerDoodle?.let { preferencesRepository.setSocialAvatarFromServer(it) }
                        message.raster?.let { raster ->
                            _friends.update { friends ->
                                friends.map {
                                    if (it.id == message.userId) it.copy(quayPassAvatar = raster) else it
                                }
                            }
                        }
                    }
                    is ArgosSocialService.IncomingMessage.QuayPassMessageUpdated -> {
                        preferencesRepository.setQuayPassGreetingFromServer(message.message)
                    }
                    is ArgosSocialService.IncomingMessage.QuayPassCheckins -> {
                        _quayPassCheckins.value = message.checkins
                    }
                    is ArgosSocialService.IncomingMessage.FriendRequest -> {
                        requestQuayPassCheckins()
                    }
                    is ArgosSocialService.IncomingMessage.FriendsData -> {
                        Log.d(TAG, "Received initial friends: ${message.friends.size}")
                        _friends.value = message.friends.sortedWith(friendComparator)
                        hasCompletedInitialSync = true
                        refreshRecentFriendPlays()
                    }
                    is ArgosSocialService.IncomingMessage.SharedCollections -> {
                        Log.d(TAG, "Received shared collections: ${message.collections.size}")
                        _sharedCollections.value = message.collections
                    }
                    is ArgosSocialService.IncomingMessage.SavedCollections -> {
                        Log.d(TAG, "Received saved collections: ${message.collections.size}")
                        _savedCollections.value = message.collections
                    }
                    is ArgosSocialService.IncomingMessage.ReviewSummaryData -> {
                        _reviewSummaries.update { it + (message.summary.igdbId to message.summary) }
                    }
                    is ArgosSocialService.IncomingMessage.GameReviewsData -> {
                        val page = withOwnAuthor(message.page)
                        _gameReviews.update { current ->
                            current + (page.igdbId to mergeReviewPage(current[page.igdbId], page))
                        }
                    }
                    is ArgosSocialService.IncomingMessage.ReviewSaved -> {
                        pendingReviewWriteIgdbId = null
                        applyMyReview(message.review.igdbId, message.review)
                        notificationManager.showSuccess(NotificationText.Res(R.string.notif_review_saved))
                        _reviewWriteEvents.tryEmit(ReviewWriteEvent.Saved(message.review))
                        requestReviewSummary(message.review.igdbId)
                    }
                    is ArgosSocialService.IncomingMessage.ReviewDeleted -> {
                        pendingReviewWriteIgdbId = null
                        applyMyReview(message.igdbId, null)
                        notificationManager.showSuccess(NotificationText.Res(R.string.notif_review_deleted))
                        _reviewWriteEvents.tryEmit(ReviewWriteEvent.Deleted(message.igdbId))
                        requestReviewSummary(message.igdbId)
                    }
                    is ArgosSocialService.IncomingMessage.FeedData -> {
                        val prevCount = _feedEvents.value.size
                        Log.d(TAG, "FeedData received: ${message.events.size} new events, loadingMore=$_isLoadingMoreFeed, prevCount=$prevCount")
                        _isLoadingFeed.value = false
                        if (_isLoadingMoreFeed) {
                            val combined = _feedEvents.updateAndGet { it + message.events }
                            Log.d(TAG, "FeedData appending: $prevCount + ${message.events.size} = ${combined.size} total")
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
                        _feedEvents.update { listOf(message.event) + it }
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
                        _eventComments.update { it + (message.eventId to message.comments) }
                    }
                    is ArgosSocialService.IncomingMessage.FeedCommentReceived -> {
                        Log.d(TAG, "FeedCommentReceived: eventId=${message.eventId}, commentId=${message.comment.id}")
                        _eventComments.update {
                            it + (message.eventId to ((it[message.eventId] ?: emptyList()) + message.comment))
                        }
                    }
                    is ArgosSocialService.IncomingMessage.RequestGameData -> {
                        Log.d(TAG, "Server requesting game data: igdbId=${message.igdbId}, steamAppId=${message.steamAppId}, title=${message.gameTitle}")
                        handleGameDataRequest(message.igdbId, message.gameTitle, message.fields, message.steamAppId)
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
                        _notifications.update { trimNotifications(listOf(resolved) + it) }
                    }
                    is ArgosSocialService.IncomingMessage.NotificationsData -> {
                        _usersCache.putAll(message.users)
                        _isLoadingNotifications.value = false
                        if (message.notifications.isNotEmpty()) {
                            _notificationsCursor = message.notifications.last().updatedAt
                        }
                        val resolved = message.notifications.map { resolveNotificationActors(it) }
                        if (_isLoadingMoreNotifications) {
                            _notifications.update { trimNotifications(it + resolved) }
                            _isLoadingMoreNotifications = false
                        } else {
                            _notifications.value = trimNotifications(resolved)
                        }
                        _notificationsHasMore.value = message.hasMore
                    }
                    is ArgosSocialService.IncomingMessage.CommunityFeedData -> {
                        if (_isLoadingMoreCommunityFeed) {
                            _communityFeed.update { it + message.events }
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
                        _communityFollows.update { follows ->
                            val existing = follows.toMutableList()
                            val idx = existing.indexOfFirst { it.igdbGameId == message.follow.igdbGameId }
                            if (idx >= 0) {
                                existing[idx] = message.follow
                            } else {
                                existing.add(message.follow)
                            }
                            existing
                        }
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
                    is ArgosSocialService.IncomingMessage.UserProfileReceived -> {
                        _userProfile.value = message.profile
                        _isLoadingProfile.value = false
                        Log.d(TAG, "UserProfile: ${message.profile.user.username}, hours=${message.profile.totalPlayHours}")
                    }
                    is ArgosSocialService.IncomingMessage.SteamGameResolved -> {
                        handleSteamGameResolved(message)
                    }
                    is ArgosSocialService.IncomingMessage.Error -> {
                        if (message.code == "unknown_type") {
                            Log.w(TAG, "Server returned unknown_type -- suppressing achievement sync for this session")
                            achievementSyncSuppressed = true
                        }
                        val pendingReview = pendingReviewWriteIgdbId
                        if (pendingReview != null && message.code in REVIEW_WRITE_ERROR_CODES) {
                            failReviewWrite(pendingReview, reviewErrorText(message.code))
                        }
                    }
                    is ArgosSocialService.IncomingMessage.NetplayReady -> {
                        Log.d(TAG, "NetplayReady: sessionId=${message.payload.sessionId}, protoVersion=${message.payload.protocolVersion}")
                    }
                    is ArgosSocialService.IncomingMessage.NetplayJoinRequested -> {
                        Log.d(TAG, "NetplayJoinRequested: sessionId=${message.payload.sessionId}, from=${message.payload.fromUsername}")
                    }
                    is ArgosSocialService.IncomingMessage.NetplayJoinDeclined -> {
                        Log.d(TAG, "NetplayJoinDeclined: sessionId=${message.payload.sessionId}, reason=${message.payload.reason}")
                    }
                    is ArgosSocialService.IncomingMessage.NetplayPeerCandidates -> {
                        Log.d(TAG, "NetplayPeerCandidates: sessionId=${message.payload.sessionId}, count=${message.payload.candidates.size}")
                    }
                    is ArgosSocialService.IncomingMessage.NetplayPunchStart -> {
                        Log.d(TAG, "NetplayPunchStart: sessionId=${message.payload.sessionId}, startAt=${message.payload.startAtUnixMs}")
                    }
                    is ArgosSocialService.IncomingMessage.NetplayHandshakeFailed -> {
                        Log.w(TAG, "NetplayHandshakeFailed: sessionId=${message.payload.sessionId}, reason=${message.payload.reason}")
                    }
                    is ArgosSocialService.IncomingMessage.NetplayKicked -> {
                        Log.w(TAG, "NetplayKicked: sessionId=${message.payload.sessionId}, reason=${message.payload.reason}")
                    }
                    is ArgosSocialService.IncomingMessage.NetplaySessionEnded -> {
                        Log.d(TAG, "NetplaySessionEnded: sessionId=${message.payload.sessionId}")
                    }
                    is ArgosSocialService.IncomingMessage.NetplayInvite -> {
                        Log.d(TAG, "NetplayInvite: sessionId=${message.payload.sessionId}, host=${message.payload.hostUsername}")
                        _netplayInvites.tryEmit(message.payload)
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

    private suspend fun handleSteamGameResolved(message: ArgosSocialService.IncomingMessage.SteamGameResolved) {
        val game = gameDao.getBySteamAppId(message.steamAppId)
        if (game == null) {
            Log.w(TAG, "IGDB resolve: game not found for steamAppId=${message.steamAppId}")
            return
        }

        val coverSource = gameArtDao.get(game.id, ArtSlot.COVER.name)?.sourceUrl
        Log.d(TAG, "IGDB resolve: ${game.title} (steamAppId=${message.steamAppId}) -> igdbId=${message.igdbId}, coverImageId=${message.coverImageId}, currentIgdb=${game.igdbId}, currentCover=${coverSource?.take(60)}")

        if (game.igdbId == null) {
            gameDao.updateIgdbId(game.id, message.igdbId)
            Log.d(TAG, "IGDB resolve: set igdbId=${message.igdbId} for gameId=${game.id}")
        }

        if (message.coverImageId != null) {
            val needsCover = game.igdbId == null
                || coverSource == null
                || coverSource.startsWith("https://steamcdn-a.akamaihd.net/")
            Log.d(TAG, "IGDB resolve: needsCover=$needsCover for ${game.title}")
            if (needsCover) {
                val igdbUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/${message.coverImageId}.jpg"
                com.nendo.argosy.data.cache.recordArtSource(
                    gameArtDao, imageCacheManager, game.id, ArtSlot.COVER, listOf(igdbUrl), game.title
                )
            }
        } else {
            Log.d(TAG, "IGDB resolve: no coverImageId from server for ${game.title}")
        }
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

    /**
     * Takes social off the air for the duration of a RomM account switch.
     *
     * Presence, the websocket and QuayPass advertising all speak for whoever is signed in, and
     * the switch moves that out from under them. Going quiet is the only way they cannot
     * announce the wrong person mid-swap.
     */
    suspend fun suspendForAccountSwitch() {
        hasCompletedInitialSync = false
        quayPassService.get().holdForAccountSwitch()
        socialService.disconnect()
        _connectionState.value = SocialConnectionState.Disconnected
        clearSocialData()
    }

    /**
     * Brings social back up on the account that is now active, if it has a social link.
     */
    suspend fun resumeAfterAccountSwitch() {
        val prefs = preferencesRepository.userPreferences.first()
        if (prefs.isSocialLinked && prefs.socialSessionToken != null) {
            connectService()
        }
        quayPassService.get().releaseAccountSwitchHold()
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
        _recentPlaysByFriend.value = emptyMap()
        recentPlaysFetchedAtMillis = 0L
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
        _userProfile.value = null
        _isLoadingProfile.value = false
        _usersCache.clear()
    }

    private fun abandonInFlightRequests() {
        _isLoadingFeed.update { false }
        _isLoadingMoreFeed = false
        _isLoadingNotifications.update { false }
        _isLoadingMoreNotifications = false
        _isLoadingCommunityFeed.update { false }
        _isLoadingMoreCommunityFeed = false
        _isLoadingProfile.update { false }
        pendingReviewWriteIgdbId?.let {
            failReviewWrite(it, NotificationText.Res(R.string.notif_review_error_offline))
        }
    }

    private fun sendWrite(send: () -> Boolean): Boolean {
        val sent = socialService.isConnected() && send()
        if (!sent) {
            notificationManager.showError(NotificationText.Res(R.string.notif_social_error_offline))
        }
        return sent
    }

    fun sendPresence(
        status: PresenceStatus,
        gameIgdbId: Int? = null,
        gameTitle: String? = null,
        media: MediaPresence? = null,
        externalNetplay: ExternalNetplay? = null
    ): Boolean {
        if (!socialService.isConnected()) return false
        return socialService.sendPresence(status, gameIgdbId, gameTitle, android.os.Build.MODEL, media, externalNetplay)
    }

    fun isConnected(): Boolean = socialService.isConnected()

    fun reconnectIfNeeded() {
        socialService.reconnectIfNeeded()
    }

    fun requestUserProfile(userId: String? = null): Boolean {
        if (!socialService.isConnected()) return false
        _isLoadingProfile.update { true }
        val sent = socialService.requestUserProfile(userId)
        if (!sent) _isLoadingProfile.update { false }
        return sent
    }

    fun sendFriendRequest(userId: String): Boolean =
        sendWrite { socialService.sendFriendRequest(userId) }

    fun acceptFriend(userId: String): Boolean =
        sendWrite { socialService.acceptFriend(userId) }

    /**
     * Declines a received request, cancels a sent one, or unfriends. The server
     * routes by the relationship, so the caller does not distinguish them.
     */
    fun removeFriend(userId: String): Boolean =
        sendWrite { socialService.removeFriend(userId) }

    fun requestQuayPassCheckins() {
        if (socialService.isConnected()) {
            socialService.requestQuayPassCheckins(limit = 100)
        }
    }

    fun requestDiscordTokens() {
        if (socialService.isConnected()) {
            socialService.requestDiscordTokens()
        }
    }

    fun toggleFavoriteFriend(friendId: String): Boolean =
        sendWrite { socialService.toggleFavoriteFriend(friendId) }

    fun toggleGameVisibility(igdbGameId: Int?, steamAppId: Int? = null, isCurrentlyHidden: Boolean = false): Boolean {
        if (igdbGameId != null && igdbGameId != 0) {
            val isHidden = igdbGameId in _hiddenGameIds.value
            val sent = sendWrite {
                if (isHidden) socialService.sendUnhideGame(igdbGameId, steamAppId)
                else socialService.sendHideGame(igdbGameId, steamAppId)
            }
            if (!sent) return false
            _hiddenGameIds.update { if (isHidden) it - igdbGameId else it + igdbGameId }
            pendingToggleGameId = igdbGameId
            hiddenGameToggleJob?.cancel()
            hiddenGameToggleJob = scope.launch {
                delay(500)
                syncPreferencesRepository.setHiddenGameIds(_hiddenGameIds.value)
                pendingToggleGameId = null
            }
            return true
        }
        if (steamAppId == null) return false
        return sendWrite {
            if (isCurrentlyHidden) socialService.sendUnhideGame(null, steamAppId)
            else socialService.sendHideGame(null, steamAppId)
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

    fun regenerateFriendCode(): Boolean =
        sendWrite { socialService.regenerateFriendCode() }

    fun addFriendByCode(code: String): Boolean =
        sendWrite { socialService.addFriendByCode(code) }

    /**
     * Hands new play sessions to the social queue and to the RomM ingest. Without a social
     * account nothing is queued and the social watermark still advances, so linking one later
     * starts from that moment rather than replaying old sessions.
     */
    suspend fun syncPlaySessions(): SyncResult {
        val prefs = preferencesRepository.userPreferences.first()
        val ownerUserId = prefs.rommUserId
        val sessions = playSessionDao.getUnsyncedForSocial(prefs.lastPlaySessionSync, ownerUserId)

        scope.launch { playSessionBackfill.uploadThenPull() }

        if (sessions.isEmpty()) {
            return SyncResult.Success(0)
        }

        if (prefs.socialUsername == null) {
            preferencesRepository.setLastPlaySessionSyncTime(sessions.maxOf { it.endTime })
            Log.d(TAG, "Skipped ${sessions.size} play sessions with no social account")
            return SyncResult.Success(0)
        }

        val cutoffs = playRecordDeletionDao.cutoffsFor(ownerUserId)
        var queued = 0
        for (session in sessions) {
            if (cutoffs.blocks(session.gameId, session.startTime)) continue
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
                    occurredAt = session.startTime,
                    ownerUserId = ownerUserId
                )
            )
            queued++
        }

        val maxEndTime = sessions.maxOf { it.endTime }
        preferencesRepository.setLastPlaySessionSyncTime(maxEndTime)
        Log.d(TAG, "Queued $queued of ${sessions.size} play sessions for sync, lastSync updated to $maxEndTime")

        if (queued > 0 && socialService.isConnected()) {
            scope.launch { socialSyncCoordinator.get().processQueue() }
        }

        return SyncResult.Success(queued)
    }

    suspend fun backfillRomMPlaySessions(): PlaySessionBackfill.Summary = playSessionBackfill.run()

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
                    achievementDao.markSocialSharedBatch(
                        acceptedIds,
                        syncPreferencesRepository.getRommUserId() ?: AchievementEntity.NO_OWNER,
                        System.currentTimeMillis()
                    )
                    Log.d(TAG, "Marked ${acceptedIds.size} achievements as socially shared")
                }
            }
        }
    }

    suspend fun syncUnsharedAchievements() {
        if (!socialService.isConnected()) return
        if (achievementSyncSuppressed) return

        val ownerUserId = syncPreferencesRepository.getRommUserId() ?: AchievementEntity.NO_OWNER
        val unshared = achievementDao.getUnsharedUnlocked(ownerUserId = ownerUserId, limit = 50)
        if (unshared.isEmpty()) return

        val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        val recent = unshared.filter {
            (it.unlockedHardcoreAt ?: it.unlockedAt ?: 0L) >= cutoff
        }
        val stale = unshared.filter {
            (it.unlockedHardcoreAt ?: it.unlockedAt ?: 0L) < cutoff
        }
        if (stale.isNotEmpty()) {
            achievementDao.markSocialSharedBatch(
                stale.map { it.raId },
                ownerUserId,
                System.currentTimeMillis()
            )
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

    fun requestReviewSummary(igdbId: Int) {
        if (igdbId <= 0) return
        if (socialService.isConnected()) socialService.getReviewSummary(igdbId)
    }

    fun requestGameReviews(igdbId: Int) {
        if (igdbId <= 0) return
        if (socialService.isConnected()) socialService.getGameReviews(igdbId)
    }

    /**
     * Asks for the next page of the public list. The friends block arrives with the first page
     * only, so a later page carrying an empty one is not a sign the friends went away.
     */
    fun loadMoreGameReviews(igdbId: Int) {
        val page = _gameReviews.value[igdbId] ?: return
        val cursor = page.nextCursor
        if (!page.hasMore || cursor == null) return
        if (socialService.isConnected()) socialService.getGameReviews(igdbId, cursor = cursor)
    }

    /**
     * One review per user per game, so a save replaces whatever was held. The body is trimmed here
     * and sent absent when empty, matching what the server would store anyway, so the cached copy
     * the reply updates never disagrees with the draft that produced it.
     */
    fun upsertReview(igdbId: Int, recommended: Boolean, body: String?, visibility: String): Boolean {
        if (igdbId <= 0) return false
        if (!socialService.isConnected()) {
            failReviewWrite(igdbId, NotificationText.Res(R.string.notif_review_error_offline))
            return false
        }
        pendingReviewWriteIgdbId = igdbId
        val trimmed = body?.trim()?.takeIf { it.isNotEmpty() }
        val sent = socialService.upsertReview(igdbId, recommended, trimmed, visibility)
        if (!sent) failReviewWrite(igdbId, NotificationText.Res(R.string.notif_review_error_offline))
        return sent
    }

    fun deleteReview(igdbId: Int): Boolean {
        if (igdbId <= 0) return false
        if (!socialService.isConnected()) {
            failReviewWrite(igdbId, NotificationText.Res(R.string.notif_review_error_offline))
            return false
        }
        pendingReviewWriteIgdbId = igdbId
        val sent = socialService.deleteReview(igdbId)
        if (!sent) failReviewWrite(igdbId, NotificationText.Res(R.string.notif_review_error_offline))
        return sent
    }

    private fun failReviewWrite(igdbId: Int, message: NotificationText) {
        pendingReviewWriteIgdbId = null
        notificationManager.showError(message)
        _reviewWriteEvents.tryEmit(ReviewWriteEvent.Failed(igdbId))
    }

    private fun reviewErrorText(code: String): NotificationText = NotificationText.Res(
        when (code) {
            "body_too_long" -> R.string.notif_review_error_too_long
            "invalid_payload" -> R.string.notif_review_error_invalid
            else -> R.string.notif_review_error_server
        }
    )

    private fun applyMyReview(igdbId: Int, review: GameReview?) {
        _reviewSummaries.update { current ->
            val summary = current[igdbId] ?: return@update current
            current + (igdbId to summary.copy(myReview = review))
        }
        _gameReviews.update { current ->
            val page = current[igdbId] ?: return@update current
            current + (igdbId to page.copy(myReview = review))
        }
    }

    private fun withOwnAuthor(page: GameReviewsPage): GameReviewsPage {
        val myReview = page.myReview ?: return page
        if (page.users.containsKey(myReview.userId)) return page
        val me = (_connectionState.value as? SocialConnectionState.Connected)
            ?.user
            ?.takeIf { it.id == myReview.userId }
            ?: _usersCache[myReview.userId]
            ?: return page
        return page.copy(users = page.users + (myReview.userId to me))
    }

    private fun mergeReviewPage(existing: GameReviewsPage?, incoming: GameReviewsPage): GameReviewsPage {
        if (existing == null) return incoming
        val isContinuation = incoming.friends.isEmpty() && existing.public.isNotEmpty()
        if (!isContinuation) return incoming

        val seen = existing.public.mapTo(mutableSetOf()) { it.userId }
        return incoming.copy(
            friends = existing.friends,
            public = existing.public + incoming.public.filterNot { it.userId in seen },
            users = existing.users + incoming.users
        )
    }

    fun requestFeed(limit: Int = FEED_PAGE_SIZE, beforeId: String? = null, userId: String? = null) {
        Log.d(TAG, "requestFeed: limit=$limit, beforeId=$beforeId, userId=$userId, connected=${socialService.isConnected()}")
        if (socialService.isConnected()) {
            _isLoadingFeed.update { true }
            _currentFeedUserId = userId
            Log.d(TAG, "requestFeed: sending request, currentFeedUserId=$_currentFeedUserId")
            if (!socialService.getFeed(limit, beforeId, userId)) {
                _isLoadingFeed.update { false }
            }
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
        if (_feedHasMore.value && !_isLoadingFeed.value && socialService.isConnected()) {
            Log.d(TAG, "loadMoreFeed: requesting more with beforeId=${lastEvent.id}, userId=$_currentFeedUserId")
            _isLoadingFeed.update { true }
            _isLoadingMoreFeed = true
            if (!socialService.getFeed(FEED_PAGE_SIZE, lastEvent.id, _currentFeedUserId)) {
                _isLoadingMoreFeed = false
                _isLoadingFeed.update { false }
            }
        } else {
            Log.d(TAG, "loadMoreFeed: skipped - hasMore=${_feedHasMore.value}, isLoading=${_isLoadingFeed.value}")
        }
    }

    fun likeEvent(eventId: String): Boolean {
        Log.d(TAG, "likeEvent: eventId=$eventId, connected=${socialService.isConnected()}")
        if (!sendWrite { socialService.likeEvent(eventId) }) return false
        _feedEvents.update { events ->
            events.map { event ->
                if (event.id != eventId) return@map event
                val newLiked = !event.isLikedByMe
                val newCount = if (newLiked) event.likeCount + 1 else event.likeCount - 1
                event.copy(isLikedByMe = newLiked, likeCount = newCount.coerceAtLeast(0))
            }
        }
        return true
    }

    fun hideEvent(eventId: String): Boolean {
        Log.d(TAG, "hideEvent: eventId=$eventId, connected=${socialService.isConnected()}")
        if (!sendWrite { socialService.hideEvent(eventId) }) return false
        _feedEvents.update { events -> events.filter { it.id != eventId } }
        return true
    }

    fun reportEvent(eventId: String, reason: String? = null): Boolean {
        Log.d(TAG, "reportEvent: eventId=$eventId, reason=$reason, connected=${socialService.isConnected()}")
        return sendWrite { socialService.reportEvent(eventId, reason) }
    }

    fun requestNotifications(limit: Int = NOTIFICATIONS_PAGE_SIZE) {
        if (socialService.isConnected()) {
            _isLoadingNotifications.update { true }
            _notificationsCursor = null
            if (!socialService.getNotifications(limit)) {
                _isLoadingNotifications.update { false }
            }
        }
    }

    fun loadMoreNotifications() {
        val cursor = _notificationsCursor ?: return
        if (_notificationsHasMore.value && !_isLoadingNotifications.value && socialService.isConnected()) {
            _isLoadingNotifications.update { true }
            _isLoadingMoreNotifications = true
            if (!socialService.getNotifications(NOTIFICATIONS_PAGE_SIZE, cursor)) {
                _isLoadingMoreNotifications = false
                _isLoadingNotifications.update { false }
            }
        }
    }

    fun markNotificationRead(notificationId: String): Boolean {
        if (!sendWrite { socialService.markNotificationRead(notificationId = notificationId) }) return false
        _notifications.update { notifications ->
            notifications.map {
                if (it.id == notificationId && it.isUnread) {
                    it.copy(viewedAt = java.time.Instant.now().toString())
                } else it
            }
        }
        return true
    }

    fun markAllNotificationsRead(): Boolean {
        if (!sendWrite { socialService.markAllNotificationsRead() }) return false
        val now = java.time.Instant.now().toString()
        _notifications.update { notifications ->
            notifications.map {
                it.copy(viewedAt = now)
            }
        }
        _unreadCount.update { 0 }
        return true
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

    fun commentEvent(eventId: String, content: String): Boolean {
        Log.d(TAG, "commentEvent: eventId=$eventId, connected=${socialService.isConnected()}")
        return sendWrite { socialService.commentEvent(eventId, content) }
    }

    fun createPost(
        body: String,
        canvasSize: Int? = null,
        doodleData: String? = null,
        igdbId: Int? = null,
        gameTitle: String? = null,
        public: Boolean = false
    ): Boolean = sendWrite {
        socialService.createPost(body, canvasSize, doodleData, igdbId, gameTitle, public)
    }

    fun requestCommunityFeed(igdbGameId: Int? = null, limit: Int = FEED_PAGE_SIZE) {
        if (socialService.isConnected()) {
            _isLoadingCommunityFeed.update { true }
            _isLoadingMoreCommunityFeed = false
            if (!socialService.getCommunityFeed(igdbGameId, limit)) {
                _isLoadingCommunityFeed.update { false }
            }
        }
    }

    fun loadMoreCommunityFeed(igdbGameId: Int? = null) {
        val last = _communityFeed.value.lastOrNull() ?: return
        if (_communityFeedHasMore.value && !_isLoadingCommunityFeed.value && socialService.isConnected()) {
            _isLoadingCommunityFeed.update { true }
            _isLoadingMoreCommunityFeed = true
            if (!socialService.getCommunityFeed(igdbGameId, FEED_PAGE_SIZE, last.id)) {
                _isLoadingMoreCommunityFeed = false
                _isLoadingCommunityFeed.update { false }
            }
        }
    }

    fun requestCommunityFollows() {
        if (socialService.isConnected()) {
            socialService.getCommunityFollows()
        }
    }

    fun followCommunity(igdbGameId: Int, isPrivate: Boolean = false, anonymous: Boolean = false): Boolean =
        sendWrite { socialService.followCommunity(igdbGameId, isPrivate, anonymous) }

    fun unfollowCommunity(igdbGameId: Int): Boolean {
        if (!sendWrite { socialService.unfollowCommunity(igdbGameId) }) return false
        _communityFollows.update { follows -> follows.filter { it.igdbGameId != igdbGameId } }
        return true
    }

    fun updateCommunityFollow(igdbGameId: Int, isPrivate: Boolean, anonymous: Boolean): Boolean =
        sendWrite { socialService.updateCommunityFollow(igdbGameId, isPrivate, anonymous) }

    fun requestUserSettings() {
        if (socialService.isConnected()) {
            socialService.getUserSettings()
        }
    }

    fun updateUserSettings(communityAutoShare: Boolean? = null): Boolean {
        if (!sendWrite { socialService.updateUserSettings(communityAutoShare) }) return false
        if (communityAutoShare != null) {
            _userSettings.update { it.copy(communityAutoShare = communityAutoShare) }
        }
        return true
    }

    fun updateEventVisibility(eventId: String, public: Boolean): Boolean =
        sendWrite { socialService.updateEventVisibility(eventId, public) }

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

    /**
     * Feed events are only sendable as a social account. Without one there is nothing to send them
     * as and nothing that will ever drain them, so they are dropped rather than queued: a backlog
     * that cannot empty is what used to hold an account signed in, since removal counts unsent work.
     */
    private suspend fun queueFeedEvent(
        eventType: String,
        igdbId: Long?,
        gameTitle: String,
        data: Map<String, Any?>
    ) {
        if (preferencesRepository.userPreferences.first().socialUsername == null) {
            Log.d(TAG, "Skipped feed event with no social account: $eventType for $gameTitle")
            return
        }
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
                occurredAt = Instant.now(),
                ownerUserId = syncPreferencesRepository.getRommUserId()
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
    ): Boolean = sendWrite {
        socialService.createDoodle(canvasSize, data, caption, igdbId, gameTitle)
    }

    private suspend fun handleGameDataRequest(igdbId: Long, gameTitle: String, fields: List<String>, steamAppId: Long? = null) {
        val game = gameDao.getByIgdbId(igdbId)
            ?: steamAppId?.let { gameDao.getBySteamAppId(it) }
        if (game == null) {
            Log.w(TAG, "Game not found in local DB for igdbId=$igdbId, steamAppId=$steamAppId, sending minimal response")
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

        val art = gameArtDao.resolved(game.id)
        val coverPath = art.coverPath
        Log.d(TAG, "Found game: ${game.title}, coverPath=$coverPath, generating response for fields=$fields")

        val coverThumbBase64 = if ("cover_thumb" in fields && coverPath != null) {
            generateCoverThumbnail(coverPath)
        } else null

        socialService.sendGameData(
            igdbId = igdbId,
            gameTitle = game.title,
            developer = if ("developer" in fields) game.developer else null,
            releaseYear = if ("release_year" in fields) game.releaseYear else null,
            genre = if ("genre" in fields) game.genre else null,
            description = if ("description" in fields) game.description else null,
            coverThumbBase64 = coverThumbBase64,
            gradientColors = if ("gradient_colors" in fields) art.gradientColors else null
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

    private fun onPresenceUpdate(update: PresenceUpdate, newPresence: PresenceStatus) {
        pendingOffline.remove(update.userId)?.cancel()
        val shownOnline = _friends.value.firstOrNull { it.id == update.userId }
            ?.presence
            ?.let { it != PresenceStatus.OFFLINE } == true
        if (newPresence == PresenceStatus.OFFLINE && shownOnline) {
            pendingOffline[update.userId] = scope.launch {
                delay(OFFLINE_GRACE_MS)
                pendingOffline.remove(update.userId)
                applyPresenceUpdate(update, newPresence)
            }
            return
        }
        applyPresenceUpdate(update, newPresence)
    }

    private fun applyPresenceUpdate(update: PresenceUpdate, newPresence: PresenceStatus) {
        val friend = _friends.value.firstOrNull { it.id == update.userId } ?: return
        val statusImpliesTitle = newPresence == PresenceStatus.IN_GAME ||
            newPresence == PresenceStatus.WATCHING
        val mergedGame = if (statusImpliesTitle && update.game == null) friend.currentGame else update.game
        val now = System.currentTimeMillis()
        val offlineAt = offlineSince[update.userId]
        val briefDrop = friend.presence == PresenceStatus.OFFLINE && offlineAt != null &&
            now - offlineAt < ONLINE_NOTICE_MIN_OFFLINE_MS
        if (newPresence == PresenceStatus.OFFLINE) {
            if (friend.presence != PresenceStatus.OFFLINE) offlineSince[update.userId] = now
        } else {
            offlineSince.remove(update.userId)
        }

        showPresenceNotificationIfNeeded(
            friend = friend,
            oldPresence = friend.presence,
            newPresence = newPresence,
            oldGame = friend.currentGame,
            newGame = mergedGame,
            suppressOnlineNotice = briefDrop
        )

        _friends.update { friends ->
            friends.map {
                if (it.id == update.userId) {
                    it.copy(presence = newPresence, currentGame = mergedGame, deviceName = update.deviceName)
                } else {
                    it
                }
            }
        }
    }

    private fun showPresenceNotificationIfNeeded(
        friend: Friend,
        oldPresence: PresenceStatus?,
        newPresence: PresenceStatus,
        oldGame: PresenceGameInfo?,
        newGame: PresenceGameInfo?,
        suppressOnlineNotice: Boolean = false
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
                newPresence == PresenceStatus.IN_GAME ||
                newPresence == PresenceStatus.WATCHING

            val startedPlayingNewGame = newPresence == PresenceStatus.IN_GAME &&
                newGame != null &&
                (oldPresence != PresenceStatus.IN_GAME || oldGame?.title != newGame.title)

            val avatarColorInt = parseAvatarColorInt(friend.avatarColor)

            val title: String?
            val subtitle: String?

            when {
                startedPlayingNewGame && prefs.socialNotifyFriendPlaying -> {
                    title = friend.displayName
                    subtitle = context.getString(R.string.notif_social_friend_started_playing, newGame?.title)
                    val coverPath = newGame?.coverThumb?.let { saveTempCover(it, friend.id) }
                    notificationManager.show(
                        title = NotificationText.Raw(title),
                        subtitle = NotificationText.Raw(subtitle),
                        imagePath = coverPath,
                        type = NotificationType.INFO,
                        duration = NotificationDuration.SHORT,
                        key = "presence_${friend.id}",
                        accentColor = avatarColorInt
                    )
                }
                wasOfflineOrAway && isNowOnline && !startedPlayingNewGame && !suppressOnlineNotice &&
                    prefs.socialNotifyFriendOnline -> {
                    title = friend.displayName
                    subtitle = context.getString(R.string.notif_social_friend_online)
                    notificationManager.show(
                        title = NotificationText.Raw(title),
                        subtitle = NotificationText.Raw(subtitle),
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
                try {
                    SocialOverlayService.show(context, title, subtitle)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to show overlay from background", e)
                }
            }
        }
    }

    /** Deletes cached friend-presence cover thumbnails; they re-download with the next presence event. */
    suspend fun clearPresenceCovers() = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "presence_covers")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        attributionRepository.markDirty(StorageCategory.MISC_DOWNLOADS)
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
        private val RECENT_PLAYS_REFRESH_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(10)
        private const val FEED_PAGE_SIZE = 10
        private const val NOTIFICATIONS_PAGE_SIZE = 20
        private const val NOTIFICATIONS_MAX_CACHE = 100
        private val REVIEW_WRITE_ERROR_CODES = setOf("invalid_payload", "body_too_long", "db_error")
    }
}
