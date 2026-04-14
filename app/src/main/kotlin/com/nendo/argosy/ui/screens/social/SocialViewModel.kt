package com.nendo.argosy.ui.screens.social

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.netplay.NetplayPreflightChecker
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
import com.nendo.argosy.ui.notification.NotificationManager
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

private const val PROFILE_DISPLAY_SECTIONS = 3

data class SocialUiState(
    val connectionState: SocialConnectionState = SocialConnectionState.Disconnected,
    val selectedTab: SocialTab = SocialTab.FEED,
    val feedMode: FeedMode = FeedMode.FRIENDS,
    val events: List<FeedEventDto> = emptyList(),
    val communityEvents: List<FeedEventDto> = emptyList(),
    val friends: List<Friend> = emptyList(),
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
    val joinableFriendIds: Set<String> = emptySet()
) {
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
}

sealed class SocialLaunchEvent {
    data class LaunchIntent(val intent: Intent) : SocialLaunchEvent()
    data class LaunchError(val message: String) : SocialLaunchEvent()
}

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameDao: GameDao,
    private val netplayPreflightChecker: NetplayPreflightChecker,
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
        viewModelScope.launch {
            val preflight = netplayPreflightChecker.check(session)
            if (preflight !is NetplayPreflightResult.Joinable) {
                _launchEvents.emit(SocialLaunchEvent.LaunchError("This session is not joinable"))
                return@launch
            }
            val igdbId = session.gameIgdbId?.toLong()
            if (igdbId == null) {
                _launchEvents.emit(SocialLaunchEvent.LaunchError("Missing game id for session"))
                return@launch
            }
            val game = gameDao.getByIgdbId(igdbId)
            if (game == null) {
                _launchEvents.emit(SocialLaunchEvent.LaunchError("Local game not found"))
                return@launch
            }
            when (val result = launchGameUseCase(gameId = game.id)) {
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
                val acceptedFriends = friends.filter { it.friendshipStatus.value == "accepted" }
                val currentState = _uiState.value
                val newFocusIndex = currentState.focusedEventIndex.coerceIn(0, events.size.coerceAtLeast(1) - 1)
                Log.v(TAG, "state update: connection=$connection, events=${events.size}, friends=${acceptedFriends.size}, loading=$isLoading, hasMore=$hasMore, focusIndex=$newFocusIndex")
                currentState.copy(
                    connectionState = connection,
                    events = events,
                    friends = acceptedFriends,
                    focusedEventIndex = newFocusIndex,
                    isLoading = isLoading,
                    hasMore = hasMore
                )
            }.collect { newState ->
                val prev = _uiState.value
                if (prev.events.size != newState.events.size || prev.isLoading != newState.isLoading) {
                    Log.d(TAG, "UI state changed: events ${prev.events.size}->${newState.events.size}, loading ${prev.isLoading}->${newState.isLoading}, hasMore=${newState.hasMore}")
                }
                _uiState.value = newState
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
            val recent = gameDao.getRecentlyPlayed(10)
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
                val recent = gameDao.getRecentlyPlayed(10)
                _uiState.value = _uiState.value.copy(
                    communitySearchResults = recent.map { it.toPickerItem() }
                )
            }
            return
        }
        communitySearchJob = viewModelScope.launch {
            delay(300)
            val results = gameDao.searchForQuickMenu(query, 15).first()
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
        if (state.friends.isEmpty()) return false
        val currentIndex = state.focusedFriendIndex
        val newIndex = (currentIndex + delta).coerceIn(0, state.friends.size - 1)
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
            gameDao.getByIgdbId(igdbId.toLong()) != null
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

    fun createInputHandler(
        onBack: () -> Unit,
        onOpenEventDetail: (String) -> Unit,
        onCreatePost: () -> Unit,
        onViewProfile: (String) -> Unit,
        onShareScreenshot: () -> Unit,
        onDrawerToggle: () -> Unit,
        onNavigateToGameDetail: (Int) -> Unit = {},
        onNavigateToSocialSettings: () -> Unit = {}
    ): InputHandler = object : InputHandler {

        private fun focusedUserName(): String? = _uiState.value.focusedEvent?.user?.displayName
        private fun hasEvent(): Boolean = _uiState.value.focusedEvent != null
        private fun isCommunityMode(): Boolean = _uiState.value.feedMode == FeedMode.COMMUNITY
        private fun anyModalShowing(): Boolean = with(feedOptionsDelegate.state.value) {
            showOptionsModal || showReportReasonModal
        } || _uiState.value.showCommunitySearch

        override fun onUp(): InputResult {
            val delegateState = feedOptionsDelegate.state.value
            val state = _uiState.value
            return when {
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
            if (anyModalShowing()) return InputResult.UNHANDLED
            return if (switchTab(-1)) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onRight(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            return if (switchTab(1)) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
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
                    val friend = state.friends.getOrNull(state.focusedFriendIndex)
                    val session = friend?.currentGame?.netplaySession
                    if (friend != null && session != null && friend.id in state.joinableFriendIds) {
                        launchNetplayJoin(friend, session)
                    } else {
                        friend?.let { onViewProfile(it.id) }
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
                    if (profileState.profileFocusOnMostPlayed) {
                        val game = profileState.userProfile?.mostPlayed?.getOrNull(profileState.focusedMostPlayedIndex)
                        if (game != null) {
                            viewModelScope.launch {
                                val localGame = gameDao.getByIgdbId(game.igdbId.toLong())
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
                    val friend = _uiState.value.friends.getOrNull(_uiState.value.focusedFriendIndex)
                    friend?.let { toggleFavoriteFriend(it.id) }
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
