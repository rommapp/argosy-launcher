package com.nendo.argosy.ui.screens.social

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.FeedEventDto
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SocialViewModel"

enum class SocialTab { FEED, FRIENDS, PROFILE }

private const val PROFILE_TOGGLE_COUNT = 5

data class SocialUiState(
    val connectionState: SocialConnectionState = SocialConnectionState.Disconnected,
    val selectedTab: SocialTab = SocialTab.FEED,
    val events: List<FeedEventDto> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val focusedEventIndex: Int = 0,
    val focusedFriendIndex: Int = 0,
    val profileFocusIndex: Int = 0,
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val socialOnlineStatus: Boolean = true,
    val socialShowNowPlaying: Boolean = true,
    val socialNotifyFriendOnline: Boolean = true,
    val socialNotifyFriendPlaying: Boolean = true,
    val socialSuppressNotificationsInGame: Boolean = false
) {
    val isConnected: Boolean
        get() = connectionState is SocialConnectionState.Connected

    val connectedUser: SocialUser?
        get() = (connectionState as? SocialConnectionState.Connected)?.user

    val focusedEvent: FeedEventDto?
        get() = events.getOrNull(focusedEventIndex)
}

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val preferencesRepository: UserPreferencesRepository,
    val notificationManager: NotificationManager
) : ViewModel() {

    val feedOptionsDelegate = FeedOptionsDelegate()

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
    }

    fun loadFeed() {
        Log.d(TAG, "loadFeed: global feed")
        socialRepository.requestFeed()
    }

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
        val events = state.events
        if (events.isEmpty()) {
            Log.v(TAG, "moveFocus: no events")
            return false
        }

        val currentIndex = state.focusedEventIndex
        val newIndex = (currentIndex + delta).coerceIn(0, events.size - 1)

        if (newIndex != currentIndex) {
            Log.v(TAG, "moveFocus: $currentIndex -> $newIndex (of ${events.size})")
            _uiState.value = state.copy(focusedEventIndex = newIndex)

            if (newIndex >= events.size - 3 && state.hasMore && !state.isLoading) {
                Log.d(TAG, "moveFocus: near end (index $newIndex of ${events.size}), triggering loadMore")
                socialRepository.loadMoreFeed()
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

    private fun moveProfileFocus(delta: Int): Boolean {
        val state = _uiState.value
        val currentIndex = state.profileFocusIndex
        val newIndex = (currentIndex + delta).coerceIn(0, PROFILE_TOGGLE_COUNT - 1)
        if (newIndex != currentIndex) {
            Log.v(TAG, "moveProfileFocus: $currentIndex -> $newIndex")
            _uiState.value = state.copy(profileFocusIndex = newIndex)
            return true
        }
        return false
    }

    private fun toggleProfilePreference(index: Int) {
        val state = _uiState.value
        when (index) {
            0 -> setSocialOnlineStatus(!state.socialOnlineStatus)
            1 -> setSocialShowNowPlaying(!state.socialShowNowPlaying)
            2 -> setSocialNotifyFriendOnline(!state.socialNotifyFriendOnline)
            3 -> setSocialNotifyFriendPlaying(!state.socialNotifyFriendPlaying)
            4 -> setSocialSuppressNotificationsInGame(!state.socialSuppressNotificationsInGame)
        }
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
        onCreateDoodle: () -> Unit,
        onViewProfile: (String) -> Unit,
        onShareScreenshot: () -> Unit,
        onDrawerToggle: () -> Unit
    ): InputHandler = object : InputHandler {

        private fun focusedUserName(): String? = _uiState.value.focusedEvent?.user?.displayName
        private fun hasEvent(): Boolean = _uiState.value.focusedEvent != null
        private fun anyModalShowing(): Boolean = with(feedOptionsDelegate.state.value) {
            showOptionsModal || showReportReasonModal
        }

        override fun onUp(): InputResult {
            val delegateState = feedOptionsDelegate.state.value
            return when {
                delegateState.showReportReasonModal ->
                    if (feedOptionsDelegate.moveReportReasonFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                delegateState.showOptionsModal ->
                    if (feedOptionsDelegate.moveOptionsFocus(-1, focusedUserName(), hasEvent())) InputResult.HANDLED else InputResult.UNHANDLED
                else -> when (_uiState.value.selectedTab) {
                    SocialTab.FEED -> if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.FRIENDS -> if (moveFriendFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.PROFILE -> if (moveProfileFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
            }
        }

        override fun onDown(): InputResult {
            val delegateState = feedOptionsDelegate.state.value
            return when {
                delegateState.showReportReasonModal ->
                    if (feedOptionsDelegate.moveReportReasonFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                delegateState.showOptionsModal ->
                    if (feedOptionsDelegate.moveOptionsFocus(1, focusedUserName(), hasEvent())) InputResult.HANDLED else InputResult.UNHANDLED
                else -> when (_uiState.value.selectedTab) {
                    SocialTab.FEED -> if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                    SocialTab.FRIENDS -> if (moveFriendFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
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
                    focusedEvent != null
                )
                Log.d(TAG, "onConfirm (modal): selectedOption=$selectedOption")
                feedOptionsDelegate.hideOptionsModal()

                when (selectedOption) {
                    FeedOption.CREATE_DOODLE -> onCreateDoodle()
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
                    friend?.let { onViewProfile(it.id) }
                    InputResult.HANDLED
                }
                SocialTab.PROFILE -> {
                    toggleProfilePreference(_uiState.value.profileFocusIndex)
                    InputResult.HANDLED
                }
            }
        }

        override fun onBack(): InputResult {
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
            if (_uiState.value.selectedTab != SocialTab.FEED) return InputResult.UNHANDLED
            likeCurrentEvent()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (anyModalShowing()) return InputResult.UNHANDLED
            if (_uiState.value.selectedTab != SocialTab.FEED) return InputResult.UNHANDLED
            feedOptionsDelegate.showOptionsModal()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult = InputResult.UNHANDLED

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
