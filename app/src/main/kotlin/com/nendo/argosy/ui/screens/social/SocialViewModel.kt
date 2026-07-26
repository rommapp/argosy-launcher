package com.nendo.argosy.ui.screens.social

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.netplay.NetplayPreflightChecker
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.netplay.NetplayPreflightResult
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.CommunityFollow
import com.nendo.argosy.data.social.FeedEventDto
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.NetplaySession
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialNotification
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.data.social.UserProfileData
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.doodle.GamePickerItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SocialViewModel"

enum class SocialTab { FEED, FRIENDS, NOTIFICATIONS, PROFILE }
enum class FeedMode { FRIENDS, COMMUNITY }

enum class AvatarModalOption(val label: String) {
    EDIT_DOODLE("Edit doodle"),
    USE_DOODLE("Use doodle"),
    USE_INITIALS("Use initials")
}

private const val PROFILE_DISPLAY_SECTIONS = 3

data class SocialUiState(
    val connectionState: SocialConnectionState = SocialConnectionState.Disconnected,
    val selectedTab: SocialTab = SocialTab.FEED,
    val feedMode: FeedMode = FeedMode.FRIENDS,
    val events: List<FeedEventDto> = emptyList(),
    val communityEvents: List<FeedEventDto> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val receivedRequests: List<Friend> = emptyList(),
    val sentRequests: List<Friend> = emptyList(),
    val notifications: List<SocialNotification> = emptyList(),
    val communityFollows: List<CommunityFollow> = emptyList(),
    val focusedEventIndex: Int = 0,
    val focusedFriendIndex: Int = 0,
    val focusedNotificationIndex: Int = 0,
    val profileFocusIndex: Int = 0,
    val userProfile: UserProfileData? = null,
    val isLoadingProfile: Boolean = false,
    val localIgdbIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val isCommunityLoading: Boolean = false,
    val communityHasMore: Boolean = false,
    val unreadCount: Int = 0,
    val isLoadingNotifications: Boolean = false,
    val notificationsHasMore: Boolean = false,
    val socialOnlineStatus: Boolean = true,
    val socialShowNowPlaying: Boolean = true,
    val socialNotifyFriendOnline: Boolean = true,
    val socialNotifyFriendPlaying: Boolean = true,
    val socialSuppressNotificationsInGame: Boolean = false,
    val showCommunitySearch: Boolean = false,
    val communitySearchQuery: String = "",
    val communitySearchResults: List<GamePickerItem> = emptyList(),
    val communitySearchFocusIndex: Int = 0,
    val communitySearchFieldFocused: Boolean = true,
    val joinableFriendIds: Set<String> = emptySet(),
    val avatarDoodle: String? = null,
    val avatarUseDoodle: Boolean = false,
    val showAvatarModal: Boolean = false,
    val avatarModalFocusIndex: Int = 0
) {
    val activeAvatarDoodle: String?
        get() = avatarDoodle.takeIf { avatarUseDoodle }

    val avatarModalOptions: List<AvatarModalOption>
        get() = buildList {
            add(AvatarModalOption.EDIT_DOODLE)
            if (avatarDoodle != null && !avatarUseDoodle) add(AvatarModalOption.USE_DOODLE)
            if (avatarUseDoodle) add(AvatarModalOption.USE_INITIALS)
        }

    val profileFocusCount: Int
        get() = PROFILE_DISPLAY_SECTIONS + (userProfile?.mostPlayed?.size ?: 0)

    val profileFocusOnMostPlayed: Boolean
        get() = profileFocusIndex >= PROFILE_DISPLAY_SECTIONS &&
                userProfile?.mostPlayed?.isNotEmpty() == true

    val focusedMostPlayedIndex: Int
        get() = (profileFocusIndex - PROFILE_DISPLAY_SECTIONS).coerceAtLeast(0)

    val focusedGameInLibrary: Boolean
        get() {
            if (!profileFocusOnMostPlayed) return false
            val game = userProfile?.mostPlayed?.getOrNull(focusedMostPlayedIndex) ?: return false
            return game.igdbId in localIgdbIds
        }

    val isConnected: Boolean
        get() = connectionState is SocialConnectionState.Connected

    val connectedUser: SocialUser?
        get() = (connectionState as? SocialConnectionState.Connected)?.user

    val activeFeedEvents: List<FeedEventDto>
        get() = if (feedMode == FeedMode.COMMUNITY) communityEvents else events

    val activeFeedLoading: Boolean
        get() = if (feedMode == FeedMode.COMMUNITY) isCommunityLoading else isLoading

    val activeFeedHasMore: Boolean
        get() = if (feedMode == FeedMode.COMMUNITY) communityHasMore else hasMore

    val focusedEvent: FeedEventDto?
        get() = activeFeedEvents.getOrNull(focusedEventIndex)

    val focusedNotification: SocialNotification?
        get() = notifications.getOrNull(focusedNotificationIndex)

    val friendsTabCount: Int
        get() = receivedRequests.size + sentRequests.size + friends.size

    val focusedFriendIsReceived: Boolean
        get() = focusedFriendIndex < receivedRequests.size

    val focusedFriendIsSent: Boolean
        get() = focusedFriendIndex >= receivedRequests.size &&
                focusedFriendIndex < receivedRequests.size + sentRequests.size

    val focusedFriendRow: Friend?
        get() = when {
            focusedFriendIndex < receivedRequests.size ->
                receivedRequests.getOrNull(focusedFriendIndex)
            focusedFriendIndex < receivedRequests.size + sentRequests.size ->
                sentRequests.getOrNull(focusedFriendIndex - receivedRequests.size)
            else ->
                friends.getOrNull(focusedFriendIndex - receivedRequests.size - sentRequests.size)
        }
}

sealed class SocialLaunchEvent {
    data class LaunchIntent(val intent: Intent) : SocialLaunchEvent()
    data class LaunchError(val message: String) : SocialLaunchEvent()
}

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val gameRepository: GameRepository,
    private val netplayPreflightChecker: NetplayPreflightChecker,
    private val netplayJoinService: com.nendo.argosy.data.netplay.NetplayJoinService,
    private val launchGameUseCase: LaunchGameUseCase,
    val notificationManager: NotificationManager
) : ViewModel() {

    private val _launchEvents = MutableSharedFlow<SocialLaunchEvent>(extraBufferCapacity = 4)
    val launchEvents: SharedFlow<SocialLaunchEvent> = _launchEvents.asSharedFlow()

    suspend fun runNetplayPreflight(
        session: NetplaySession
    ): NetplayPreflightResult = netplayPreflightChecker.check(session)

    fun reportFriendNetplayJoinable(friendId: String, joinable: Boolean) {
        _uiState.update { state ->
            val current = state.joinableFriendIds
            val next = when {
                joinable && friendId !in current -> current + friendId
                !joinable && friendId in current -> current - friendId
                else -> current
            }
            if (next === current) state else state.copy(joinableFriendIds = next)
        }
    }

    fun launchNetplayJoin(friend: Friend, session: NetplaySession) {
        netplayJoinService.start(session, friend)
    }

    @Suppress("unused")
    private fun legacyLaunchNetplayJoin(friend: Friend, session: NetplaySession) {
        viewModelScope.launch {
            notificationManager.show(
                title = "Joining ${session.gameTitle}",
                subtitle = "Checking compatibility...",
                duration = com.nendo.argosy.core.notification.NotificationDuration.LONG
            )
            val preflight = netplayPreflightChecker.check(session)
            if (preflight !is NetplayPreflightResult.Joinable) {
                _launchEvents.emit(SocialLaunchEvent.LaunchError("This session is not joinable"))
                return@launch
            }
            val gameId = preflight.gameId ?: run {
                val igdbId = session.gameIgdbId?.toLong()
                    ?: return@launch _launchEvents.emit(SocialLaunchEvent.LaunchError("Missing game id for session"))
                gameRepository.getByIgdbId(igdbId)?.id
                    ?: return@launch _launchEvents.emit(SocialLaunchEvent.LaunchError("Local game not found"))
            }
            when (val result = launchGameUseCase(gameId = gameId, allowVariantPrompt = false)) {
                is LaunchResult.Success -> {
                    val decorated = Intent(result.intent).apply {
                        putExtra(LibretroActivity.EXTRA_NETPLAY_JOIN_SESSION_ID, session.sessionId)
                        putExtra(LibretroActivity.EXTRA_NETPLAY_JOIN_HOST_USER_ID, friend.id)
                        if (preflight.resolvedCorePath != null) {
                            putExtra(LibretroActivity.EXTRA_CORE_PATH, preflight.resolvedCorePath)
                        }
                    }
                    _launchEvents.emit(SocialLaunchEvent.LaunchIntent(decorated))
                }
                is LaunchResult.Error -> {
                    _launchEvents.emit(SocialLaunchEvent.LaunchError(result.message))
                }
                else -> {
                    _launchEvents.emit(SocialLaunchEvent.LaunchError("Couldn't launch ${friend.currentGame?.title ?: "game"}"))
                }
            }
        }
    }

    val feedOptionsDelegate = FeedOptionsDelegate()
    private var communitySearchJob: Job? = null

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "init: starting state collection")
        viewModelScope.launch {
            combine(
                socialRepository.connectionState,
                socialRepository.feedEvents,
                socialRepository.friends,
                socialRepository.isLoadingFeed,
                socialRepository.feedHasMore
            ) { connection, events, friends, isLoading, hasMore ->
                data class FriendsFeedState(
                    val connection: SocialConnectionState,
                    val events: List<FeedEventDto>,
                    val friends: List<Friend>,
                    val isLoading: Boolean,
                    val hasMore: Boolean
                )
                FriendsFeedState(connection, events, friends, isLoading, hasMore)
            }.collect { fs ->
                val acceptedFriends = fs.friends.filter { it.isAccepted }
                val received = fs.friends.filter { it.requestReceived }
                val sent = fs.friends.filter { it.requestSent }
                val friendsTabCount = acceptedFriends.size + received.size + sent.size
                _uiState.update { current ->
                    current.copy(
                        connectionState = fs.connection,
                        events = fs.events,
                        friends = acceptedFriends,
                        receivedRequests = received,
                        sentRequests = sent,
                        focusedEventIndex = current.focusedEventIndex.coerceIn(0, fs.events.size.coerceAtLeast(1) - 1),
                        focusedFriendIndex = current.focusedFriendIndex.coerceIn(0, friendsTabCount.coerceAtLeast(1) - 1),
                        isLoading = fs.isLoading,
                        hasMore = fs.hasMore
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                socialRepository.notifications,
                socialRepository.unreadCount,
                socialRepository.isLoadingNotifications,
                socialRepository.notificationsHasMore
            ) { notifications, unread, loading, hasMore ->
                data class NotifState(
                    val notifications: List<SocialNotification>,
                    val unread: Int,
                    val loading: Boolean,
                    val hasMore: Boolean
                )
                NotifState(notifications, unread, loading, hasMore)
            }.collect { ns ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    notifications = ns.notifications,
                    unreadCount = ns.unread,
                    isLoadingNotifications = ns.loading,
                    notificationsHasMore = ns.hasMore,
                    focusedNotificationIndex = current.focusedNotificationIndex.coerceIn(
                        0, ns.notifications.size.coerceAtLeast(1) - 1
                    )
                )
            }
        }

        viewModelScope.launch {
            combine(
                socialRepository.communityFeed,
                socialRepository.communityFollows,
                socialRepository.isLoadingCommunityFeed,
                socialRepository.communityFeedHasMore
            ) { communityEvents, follows, loading, hasMore ->
                data class CommunityState(
                    val events: List<FeedEventDto>,
                    val follows: List<CommunityFollow>,
                    val loading: Boolean,
                    val hasMore: Boolean
                )
                CommunityState(communityEvents, follows, loading, hasMore)
            }.collect { cs ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    communityEvents = cs.events,
                    communityFollows = cs.follows,
                    isCommunityLoading = cs.loading,
                    communityHasMore = cs.hasMore,
                    focusedEventIndex = if (current.feedMode == FeedMode.COMMUNITY) {
                        current.focusedEventIndex.coerceIn(0, cs.events.size.coerceAtLeast(1) - 1)
                    } else current.focusedEventIndex
                )
            }
        }

        viewModelScope.launch {
            syncPreferencesRepository.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        avatarDoodle = prefs.socialAvatarDoodle,
                        avatarUseDoodle = prefs.socialAvatarUseDoodle
                    )
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    socialOnlineStatus = prefs.socialOnlineStatusEnabled,
                    socialShowNowPlaying = prefs.socialShowNowPlaying,
                    socialNotifyFriendOnline = prefs.socialNotifyFriendOnline,
                    socialNotifyFriendPlaying = prefs.socialNotifyFriendPlaying,
                    socialSuppressNotificationsInGame = prefs.socialSuppressNotificationsInGame
                )
            }
        }

        viewModelScope.launch {
            combine(
                socialRepository.userProfile,
                socialRepository.isLoadingProfile
            ) { profile, loading ->
                profile to loading
            }.collect { (profile, loading) ->
                val currentUserId = _uiState.value.connectedUser?.id
                val isOwnProfile = profile == null || profile.user.id == currentUserId
                if (!isOwnProfile) return@collect
                val igdbIds = if (profile != null && profile != _uiState.value.userProfile) {
                    resolveLocalIgdbIds(profile.mostPlayed.map { it.igdbId })
                } else {
                    _uiState.value.localIgdbIds
                }
                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    isLoadingProfile = loading,
                    localIgdbIds = igdbIds
                )
            }
        }
    }

    fun loadFeed() {
        val state = _uiState.value
        if (state.feedMode == FeedMode.COMMUNITY) {
            Log.d(TAG, "loadFeed: community feed")
            socialRepository.requestCommunityFeed()
        } else {
            Log.d(TAG, "loadFeed: friends feed")
            socialRepository.requestFeed()
        }
    }

    fun toggleFeedMode() {
        val current = _uiState.value
        val newMode = if (current.feedMode == FeedMode.FRIENDS) FeedMode.COMMUNITY else FeedMode.FRIENDS
        Log.d(TAG, "toggleFeedMode: ${current.feedMode} -> $newMode")
        _uiState.value = current.copy(feedMode = newMode, focusedEventIndex = 0)
        if (newMode == FeedMode.COMMUNITY && current.communityEvents.isEmpty()) {
            socialRepository.requestCommunityFeed()
            socialRepository.requestCommunityFollows()
        }
    }

    fun showCommunitySearch() {
        _uiState.value = _uiState.value.copy(
            showCommunitySearch = true,
            communitySearchQuery = "",
            communitySearchResults = emptyList(),
            communitySearchFocusIndex = 0,
            communitySearchFieldFocused = true
        )
        viewModelScope.launch {
            val recent = gameRepository.getRecentlyPlayed(10)
            _uiState.value = _uiState.value.copy(
                communitySearchResults = recent.map { it.toPickerItem() }
            )
        }
    }

    fun hideCommunitySearch() {
        _uiState.value = _uiState.value.copy(showCommunitySearch = false)
        communitySearchJob?.cancel()
    }

    fun updateCommunitySearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            communitySearchQuery = query,
            communitySearchFocusIndex = 0
        )
        communitySearchJob?.cancel()
        if (query.isBlank()) {
            viewModelScope.launch {
                val recent = gameRepository.getRecentlyPlayed(10)
                _uiState.value = _uiState.value.copy(
                    communitySearchResults = recent.map { it.toPickerItem() }
                )
            }
            return
        }
        communitySearchJob = viewModelScope.launch {
            delay(300)
            val results = gameRepository.searchForQuickMenu(query, 15).first()
            _uiState.value = _uiState.value.copy(
                communitySearchResults = results.map { it.toPickerItem() }
            )
        }
    }

    fun moveCommunitySearchFocus(delta: Int) {
        val state = _uiState.value
        val maxIndex = state.communitySearchResults.size - 1
        if (maxIndex < 0) return
        val newIndex = (state.communitySearchFocusIndex + delta).coerceIn(0, maxIndex)
        _uiState.value = state.copy(communitySearchFocusIndex = newIndex)
    }

    fun focusCommunitySearchField() {
        _uiState.value = _uiState.value.copy(communitySearchFieldFocused = true)
    }

    fun focusCommunitySearchList() {
        _uiState.value = _uiState.value.copy(
            communitySearchFieldFocused = false,
            communitySearchFocusIndex = 0
        )
    }

    fun toggleCommunityFollow(igdbId: Int) {
        val isFollowed = _uiState.value.communityFollows.any { it.igdbGameId == igdbId }
        if (isFollowed) {
            socialRepository.unfollowCommunity(igdbId)
        } else {
            socialRepository.followCommunity(igdbId)
        }
    }

    private fun com.nendo.argosy.data.local.entity.GameEntity.toPickerItem() = GamePickerItem(
        id = id,
        igdbId = igdbId?.toInt(),
        title = title,
        platform = platformSlug,
        coverPath = coverPath
    )

    fun refresh() {
        Log.d(TAG, "refresh: resetting focusIndex and reloading")
        _uiState.value = _uiState.value.copy(focusedEventIndex = 0)
        loadFeed()
    }

    fun switchTab(delta: Int): Boolean {
        val tabs = SocialTab.entries
        val currentOrdinal = _uiState.value.selectedTab.ordinal
        val newOrdinal = (currentOrdinal + delta).coerceIn(0, tabs.size - 1)
        if (newOrdinal != currentOrdinal) {
            Log.d(TAG, "switchTab: ${tabs[currentOrdinal]} -> ${tabs[newOrdinal]}")
            _uiState.value = _uiState.value.copy(selectedTab = tabs[newOrdinal])
            return true
        }
        return false
    }

    private fun moveFocus(delta: Int): Boolean {
        val state = _uiState.value
        val events = state.activeFeedEvents
        if (events.isEmpty()) {
            Log.v(TAG, "moveFocus: no events")
            return false
        }

        val currentIndex = state.focusedEventIndex
        val newIndex = (currentIndex + delta).coerceIn(0, events.size - 1)

        if (newIndex != currentIndex) {
            Log.v(TAG, "moveFocus: $currentIndex -> $newIndex (of ${events.size})")
            _uiState.value = state.copy(focusedEventIndex = newIndex)

            if (newIndex >= events.size - 3 && state.activeFeedHasMore && !state.activeFeedLoading) {
                Log.d(TAG, "moveFocus: near end (index $newIndex of ${events.size}), triggering loadMore")
                if (state.feedMode == FeedMode.COMMUNITY) {
                    socialRepository.loadMoreCommunityFeed()
                } else {
                    socialRepository.loadMoreFeed()
                }
            }
            return true
        }
        return false
    }

    private fun moveFriendFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.friendsTabCount == 0) return false
        val currentIndex = state.focusedFriendIndex
        val newIndex = (currentIndex + delta).coerceIn(0, state.friendsTabCount - 1)
        if (newIndex != currentIndex) {
            Log.v(TAG, "moveFriendFocus: $currentIndex -> $newIndex")
            _uiState.value = state.copy(focusedFriendIndex = newIndex)
            return true
        }
        return false
    }

    private fun moveNotificationFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.notifications.isEmpty()) return false
        val currentIndex = state.focusedNotificationIndex
        val newIndex = (currentIndex + delta).coerceIn(0, state.notifications.size - 1)
        if (newIndex != currentIndex) {
            _uiState.value = state.copy(focusedNotificationIndex = newIndex)
            if (newIndex >= state.notifications.size - 3 && state.notificationsHasMore && !state.isLoadingNotifications) {
                socialRepository.loadMoreNotifications()
            }
            return true
        }
        return false
    }

    private fun moveProfileFocus(delta: Int): Boolean {
        val state = _uiState.value
        val currentIndex = state.profileFocusIndex
        val maxIndex = (state.profileFocusCount - 1).coerceAtLeast(0)
        val newIndex = (currentIndex + delta).coerceIn(0, maxIndex)
        if (newIndex != currentIndex) {
            Log.v(TAG, "moveProfileFocus: $currentIndex -> $newIndex")
            _uiState.value = state.copy(profileFocusIndex = newIndex)
            return true
        }
        return false
    }

    fun loadProfile(userId: String? = null) {
        val effectiveUserId = userId ?: _uiState.value.connectedUser?.id
        socialRepository.requestUserProfile(effectiveUserId)
    }

    private suspend fun resolveLocalIgdbIds(igdbIds: List<Int>): Set<Int> {
        return igdbIds.filter { igdbId ->
            gameRepository.getByIgdbId(igdbId.toLong()) != null
        }.toSet()
    }

    fun setSocialOnlineStatus(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialOnlineStatusEnabled(enabled)
        }
    }

    fun setSocialShowNowPlaying(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialShowNowPlaying(enabled)
        }
    }

    fun setSocialNotifyFriendOnline(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialNotifyFriendOnline(enabled)
        }
    }

    fun setSocialNotifyFriendPlaying(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialNotifyFriendPlaying(enabled)
        }
    }

    fun setSocialSuppressNotificationsInGame(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialSuppressNotificationsInGame(enabled)
        }
    }

    fun loadNotifications() {
        socialRepository.requestNotifications()
    }

    fun markNotificationRead(id: String) {
        socialRepository.markNotificationRead(id)
    }

    fun markAllNotificationsRead() {
        socialRepository.markAllNotificationsRead()
    }

    fun toggleFavoriteFriend(friendId: String) {
        socialRepository.toggleFavoriteFriend(friendId)
    }

    fun acceptRequest(userId: String) {
        socialRepository.acceptFriend(userId)
    }

    fun declineRequest(userId: String) {
        socialRepository.removeFriend(userId)
    }

    fun cancelRequest(userId: String) {
        socialRepository.removeFriend(userId)
    }

    fun likeCurrentEvent() {
        val event = _uiState.value.focusedEvent
        Log.d(TAG, "likeCurrentEvent: event=${event?.id}, currentlyLiked=${event?.isLikedByMe}")
        event?.let { socialRepository.likeEvent(it.id) }
    }

    fun hideCurrentEvent() {
        val event = _uiState.value.focusedEvent
        Log.d(TAG, "hideCurrentEvent: event=${event?.id}")
        event?.let { socialRepository.hideEvent(it.id) }
    }

    fun reportCurrentEvent(reason: ReportReason) {
        val event = _uiState.value.focusedEvent
        Log.d(TAG, "reportCurrentEvent: event=${event?.id}, reason=${reason.value}")
        event?.let {
            socialRepository.reportEvent(it.id, reason.value)
            socialRepository.hideEvent(it.id)
        }
    }

    fun showAvatarModal() {
        _uiState.value = _uiState.value.copy(showAvatarModal = true, avatarModalFocusIndex = 0)
    }

    fun hideAvatarModal() {
        _uiState.value = _uiState.value.copy(showAvatarModal = false)
    }

    fun moveAvatarModalFocus(delta: Int) {
        _uiState.value = _uiState.value.let { state ->
            val count = state.avatarModalOptions.size
            state.copy(avatarModalFocusIndex = (state.avatarModalFocusIndex + delta).mod(count))
        }
    }

    fun confirmAvatarModal(onNavigateToAvatarEditor: () -> Unit) {
        val state = _uiState.value
        when (val option = state.avatarModalOptions.getOrNull(state.avatarModalFocusIndex)) {
            AvatarModalOption.EDIT_DOODLE -> {
                hideAvatarModal()
                onNavigateToAvatarEditor()
            }
            null -> hideAvatarModal()
            else -> confirmAvatarModalOption(option)
        }
    }

    fun confirmAvatarModalOption(option: AvatarModalOption) {
        when (option) {
            AvatarModalOption.EDIT_DOODLE -> hideAvatarModal()
            AvatarModalOption.USE_DOODLE -> {
                viewModelScope.launch { syncPreferencesRepository.setSocialAvatarUseDoodle(true) }
                hideAvatarModal()
            }
            AvatarModalOption.USE_INITIALS -> {
                viewModelScope.launch { syncPreferencesRepository.setSocialAvatarUseDoodle(false) }
                hideAvatarModal()
            }
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onOpenEventDetail: (String) -> Unit,
        onCreatePost: () -> Unit,
        onViewProfile: (String) -> Unit,
        onShareScreenshot: () -> Unit,
        onDrawerToggle: () -> Unit,
        onNavigateToGameDetail: (Int) -> Unit = {},
        onNavigateToSocialSettings: () -> Unit = {},
        onNavigateToAvatarEditor: () -> Unit = {}
    ): InputHandler = object : InputHandler {

        private fun focusedUserName(): String? = _uiState.value.focusedEvent?.user?.displayName
        private fun hasEvent(): Boolean = _uiState.value.focusedEvent != null
        private fun isCommunityMode(): Boolean = _uiState.value.feedMode == FeedMode.COMMUNITY
        private fun anyModalShowing(): Boolean = with(feedOptionsDelegate.state.value) {
            showOptionsModal || showReportReasonModal
        } || _uiState.value.showCommunitySearch || _uiState.value.showAvatarModal

        override fun onUp(): InputResult {
            val delegateState = feedOptionsDelegate.state.value
            val state = _uiState.value
            return when {
                state.showAvatarModal -> {
                    moveAvatarModalFocus(-1)
                    InputResult.HANDLED
                }
                state.showCommunitySearch && !state.communitySearchFieldFocused -> {
                    if (state.communitySearchFocusIndex == 0) {
                        focusCommunitySearchField()
                    } else {
                        moveCommunitySearchFocus(-1)
                    }
                    InputResult.HANDLED
                }
                state.showCommunitySearch -> InputResult.HANDLED
                delegateState.showReportReasonModal ->
                    if (feedOptionsDelegate.moveReportReasonFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                delegateState.showOptionsModal ->
                    if (feedOptionsDelegate.moveOptionsFocus(-1, focusedUserName(), hasEvent(), isCommunityMode())) InputResult.HANDLED else InputResult.UNHANDLED
                else -> when (_uiState.value.selectedTab) {
                    SocialTab.FEED -> if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.FRIENDS -> if (moveFriendFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.NOTIFICATIONS -> if (moveNotificationFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.PROFILE -> if (moveProfileFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
            }
        }

        override fun onDown(): InputResult {
            val delegateState = feedOptionsDelegate.state.value
            val state = _uiState.value
            return when {
                state.showAvatarModal -> {
                    moveAvatarModalFocus(1)
                    InputResult.HANDLED
                }
                state.showCommunitySearch && state.communitySearchFieldFocused -> {
                    focusCommunitySearchList()
                    InputResult.HANDLED
                }
                state.showCommunitySearch -> {
                    moveCommunitySearchFocus(1)
                    InputResult.HANDLED
                }
                delegateState.showReportReasonModal ->
                    if (feedOptionsDelegate.moveReportReasonFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                delegateState.showOptionsModal ->
                    if (feedOptionsDelegate.moveOptionsFocus(1, focusedUserName(), hasEvent(), isCommunityMode())) InputResult.HANDLED else InputResult.UNHANDLED
                else -> when (_uiState.value.selectedTab) {
                    SocialTab.FEED -> if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.FRIENDS -> if (moveFriendFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.NOTIFICATIONS -> if (moveNotificationFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.PROFILE -> if (moveProfileFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
            }
        }

        override fun onLeft(): InputResult {
            if (anyModalShowing()) return InputResult.HANDLED
            return if (switchTab(-1)) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onRight(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            return if (switchTab(1)) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            if (state.showAvatarModal) {
                confirmAvatarModal(onNavigateToAvatarEditor)
                return InputResult.HANDLED
            }
            if (state.showCommunitySearch) {
                if (!state.communitySearchFieldFocused) {
                    val item = state.communitySearchResults.getOrNull(state.communitySearchFocusIndex)
                    item?.igdbId?.let {
                        toggleCommunityFollow(it)
                        hideCommunitySearch()
                    }
                }
                return InputResult.HANDLED
            }

            val delegateState = feedOptionsDelegate.state.value

            if (delegateState.showReportReasonModal) {
                val reason = feedOptionsDelegate.resolveReportReason()
                Log.d(TAG, "onConfirm (report modal): reason=${reason.value}")
                feedOptionsDelegate.hideReportReasonModal()
                reportCurrentEvent(reason)
                return InputResult.HANDLED
            }

            if (delegateState.showOptionsModal) {
                val focusedEvent = _uiState.value.focusedEvent
                val selectedOption = feedOptionsDelegate.resolveOptionAction(
                    focusedEvent?.user?.displayName,
                    focusedEvent != null,
                    isCommunityMode()
                )
                Log.d(TAG, "onConfirm (modal): selectedOption=$selectedOption")
                feedOptionsDelegate.hideOptionsModal()

                when (selectedOption) {
                    FeedOption.CREATE_POST -> onCreatePost()
                    FeedOption.FIND_COMMUNITIES -> showCommunitySearch()
                    FeedOption.VIEW_PROFILE -> focusedEvent?.user?.id?.let { onViewProfile(it) }
                    FeedOption.SHARE_SCREENSHOT -> onShareScreenshot()
                    FeedOption.REPORT_POST -> feedOptionsDelegate.showReportReasonModal()
                    FeedOption.HIDE_POST -> hideCurrentEvent()
                    null -> {}
                }
                return InputResult.HANDLED
            }

            return when (_uiState.value.selectedTab) {
                SocialTab.FEED -> {
                    _uiState.value.focusedEvent?.let { event ->
                        onOpenEventDetail(event.id)
                    }
                    InputResult.HANDLED
                }
                SocialTab.FRIENDS -> {
                    val state = _uiState.value
                    val row = state.focusedFriendRow
                    when {
                        row == null -> {}
                        state.focusedFriendIsReceived -> acceptRequest(row.id)
                        state.focusedFriendIsSent -> cancelRequest(row.id)
                        else -> {
                            val session = row.currentGame?.netplaySession
                            if (session != null && row.id in state.joinableFriendIds) {
                                launchNetplayJoin(row, session)
                            } else {
                                onViewProfile(row.id)
                            }
                        }
                    }
                    InputResult.HANDLED
                }
                SocialTab.NOTIFICATIONS -> {
                    val notif = _uiState.value.focusedNotification
                    if (notif != null) {
                        markNotificationRead(notif.id)
                        when (notif.type) {
                            "comment", "like_milestone" -> notif.eventId?.let { onOpenEventDetail(it) }
                            "friend_request", "friend_accepted", "friend_added" -> {
                                val delta = SocialTab.FRIENDS.ordinal - _uiState.value.selectedTab.ordinal
                                switchTab(delta)
                            }
                        }
                    }
                    InputResult.HANDLED
                }
                SocialTab.PROFILE -> {
                    val profileState = _uiState.value
                    if (!profileState.profileFocusOnMostPlayed && profileState.profileFocusIndex == 0) {
                        showAvatarModal()
                        return InputResult.HANDLED
                    }
                    if (profileState.profileFocusOnMostPlayed) {
                        val game = profileState.userProfile?.mostPlayed?.getOrNull(profileState.focusedMostPlayedIndex)
                        if (game != null) {
                            viewModelScope.launch {
                                val localGame = gameRepository.getByIgdbId(game.igdbId.toLong())
                                if (localGame != null) {
                                    onNavigateToGameDetail(localGame.id.toInt())
                                } else {
                                    notificationManager.show(title = "Game not in library")
                                }
                            }
                        }
                    }
                    InputResult.HANDLED
                }
            }
        }

        override fun onBack(): InputResult {
            if (_uiState.value.showAvatarModal) {
                hideAvatarModal()
                return InputResult.HANDLED
            }
            if (_uiState.value.showCommunitySearch) {
                hideCommunitySearch()
                return InputResult.HANDLED
            }
            val delegateState = feedOptionsDelegate.state.value
            if (delegateState.showReportReasonModal) {
                feedOptionsDelegate.hideReportReasonModal()
                return InputResult.HANDLED
            }
            if (delegateState.showOptionsModal) {
                feedOptionsDelegate.hideOptionsModal()
                return InputResult.HANDLED
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            onDrawerToggle()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            return when (_uiState.value.selectedTab) {
                SocialTab.FEED -> {
                    likeCurrentEvent()
                    InputResult.HANDLED
                }
                SocialTab.FRIENDS -> {
                    val state = _uiState.value
                    val row = state.focusedFriendRow
                    when {
                        row == null -> {}
                        state.focusedFriendIsReceived -> declineRequest(row.id)
                        state.focusedFriendIsSent -> cancelRequest(row.id)
                        else -> toggleFavoriteFriend(row.id)
                    }
                    InputResult.HANDLED
                }
                SocialTab.NOTIFICATIONS -> {
                    markAllNotificationsRead()
                    InputResult.HANDLED
                }
                SocialTab.PROFILE -> InputResult.UNHANDLED
            }
        }

        override fun onSelect(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            return when (_uiState.value.selectedTab) {
                SocialTab.FEED -> {
                    feedOptionsDelegate.showOptionsModal()
                    InputResult.HANDLED
                }
                SocialTab.PROFILE -> {
                    onNavigateToSocialSettings()
                    InputResult.HANDLED
                }
                else -> InputResult.UNHANDLED
            }
        }

        override fun onContextMenu(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            if (_uiState.value.selectedTab == SocialTab.FEED) {
                toggleFeedMode()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }

        override fun onPrevSection(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            return if (switchTab(-1)) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onNextSection(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            return if (switchTab(1)) InputResult.HANDLED else InputResult.UNHANDLED
        }
    }
}
