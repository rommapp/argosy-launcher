package com.nendo.argosy.ui.screens.social

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.social.FeedEventDto
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SocialViewModel"

data class SocialUiState(
    val connectionState: SocialConnectionState = SocialConnectionState.Disconnected,
    val events: List<FeedEventDto> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val selectedFriendIndex: Int = -1,
    val focusedEventIndex: Int = 0,
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val showOptionsModal: Boolean = false,
    val optionsModalFocusIndex: Int = 0,
    val showReportReasonModal: Boolean = false,
    val reportReasonFocusIndex: Int = 0
) {
    val selectedFriend: Friend?
        get() = friends.getOrNull(selectedFriendIndex)

    val filterLabel: String
        get() = selectedFriend?.displayName ?: "All Friends"

    val isConnected: Boolean
        get() = connectionState is SocialConnectionState.Connected

    val focusedEvent: FeedEventDto?
        get() = events.getOrNull(focusedEventIndex)

    val optionsModalOptionCount: Int
        get() {
            val userName = focusedEvent?.user?.displayName
            val hasEvent = focusedEvent != null
            return getOptionCount(userName, hasEvent)
        }
}

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository
) : ViewModel() {

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
                SocialUiState(
                    connectionState = connection,
                    events = events,
                    friends = acceptedFriends,
                    selectedFriendIndex = currentState.selectedFriendIndex,
                    focusedEventIndex = newFocusIndex,
                    isLoading = isLoading,
                    hasMore = hasMore,
                    showOptionsModal = currentState.showOptionsModal,
                    optionsModalFocusIndex = currentState.optionsModalFocusIndex,
                    showReportReasonModal = currentState.showReportReasonModal,
                    reportReasonFocusIndex = currentState.reportReasonFocusIndex
                )
            }.collect { newState ->
                val prev = _uiState.value
                if (prev.events.size != newState.events.size || prev.isLoading != newState.isLoading) {
                    Log.d(TAG, "UI state changed: events ${prev.events.size}->${newState.events.size}, loading ${prev.isLoading}->${newState.isLoading}, hasMore=${newState.hasMore}")
                }
                _uiState.value = newState
            }
        }
    }

    fun loadFeed() {
        val selectedFriend = _uiState.value.selectedFriend
        Log.d(TAG, "loadFeed: selectedFriend=${selectedFriend?.displayName} (${selectedFriend?.id})")
        socialRepository.requestFeed(userId = selectedFriend?.id)
    }

    fun refresh() {
        Log.d(TAG, "refresh: resetting focusIndex and reloading")
        _uiState.value = _uiState.value.copy(focusedEventIndex = 0)
        loadFeed()
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

    private fun switchFriend(delta: Int): Boolean {
        val state = _uiState.value
        val friends = state.friends
        if (friends.isEmpty()) {
            Log.v(TAG, "switchFriend: no friends")
            return false
        }

        val newIndex = when {
            state.selectedFriendIndex == -1 && delta > 0 -> 0
            state.selectedFriendIndex == 0 && delta < 0 -> -1
            else -> (state.selectedFriendIndex + delta).coerceIn(-1, friends.size - 1)
        }

        if (newIndex != state.selectedFriendIndex) {
            val friendName = if (newIndex == -1) "All Friends" else friends.getOrNull(newIndex)?.displayName
            Log.d(TAG, "switchFriend: ${state.selectedFriendIndex} -> $newIndex ($friendName)")
            _uiState.value = state.copy(selectedFriendIndex = newIndex, focusedEventIndex = 0)
            loadFeed()
            return true
        }
        return false
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

    fun showReportReasonModal() {
        Log.d(TAG, "showReportReasonModal")
        _uiState.value = _uiState.value.copy(showReportReasonModal = true, reportReasonFocusIndex = 0)
    }

    fun hideReportReasonModal() {
        Log.d(TAG, "hideReportReasonModal")
        _uiState.value = _uiState.value.copy(showReportReasonModal = false)
    }

    private fun moveReportReasonFocus(delta: Int): Boolean {
        val state = _uiState.value
        val maxIndex = REPORT_REASON_COUNT - 1
        val newIndex = (state.reportReasonFocusIndex + delta).coerceIn(0, maxIndex)
        if (newIndex != state.reportReasonFocusIndex) {
            _uiState.value = state.copy(reportReasonFocusIndex = newIndex)
            return true
        }
        return false
    }

    fun showOptionsModal() {
        Log.d(TAG, "showOptionsModal")
        _uiState.value = _uiState.value.copy(showOptionsModal = true, optionsModalFocusIndex = 0)
    }

    fun hideOptionsModal() {
        Log.d(TAG, "hideOptionsModal")
        _uiState.value = _uiState.value.copy(showOptionsModal = false)
    }

    private fun moveOptionsModalFocus(delta: Int): Boolean {
        val state = _uiState.value
        val maxIndex = state.optionsModalOptionCount - 1
        val newIndex = (state.optionsModalFocusIndex + delta).coerceIn(0, maxIndex)
        if (newIndex != state.optionsModalFocusIndex) {
            _uiState.value = state.copy(optionsModalFocusIndex = newIndex)
            return true
        }
        return false
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onOpenEventDetail: (String) -> Unit,
        onCreateDoodle: () -> Unit,
        onViewProfile: (String) -> Unit,
        onShareScreenshot: () -> Unit,
        onNavigateToQuayPass: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            return when {
                _uiState.value.showReportReasonModal -> {
                    if (moveReportReasonFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
                _uiState.value.showOptionsModal -> {
                    if (moveOptionsModalFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
                else -> {
                    if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
            }
        }

        override fun onDown(): InputResult {
            return when {
                _uiState.value.showReportReasonModal -> {
                    if (moveReportReasonFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
                _uiState.value.showOptionsModal -> {
                    if (moveOptionsModalFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
                else -> {
                    if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
                }
            }
        }

        override fun onLeft(): InputResult = InputResult.UNHANDLED
        override fun onRight(): InputResult = InputResult.UNHANDLED

        override fun onConfirm(): InputResult {
            if (_uiState.value.showReportReasonModal) {
                val reason = ReportReason.entries[_uiState.value.reportReasonFocusIndex]
                Log.d(TAG, "onConfirm (report modal): reason=${reason.value}")
                hideReportReasonModal()
                reportCurrentEvent(reason)
                return InputResult.HANDLED
            }

            if (_uiState.value.showOptionsModal) {
                val state = _uiState.value
                val focusedEvent = state.focusedEvent
                val userName = focusedEvent?.user?.displayName
                val hasEvent = focusedEvent != null

                var currentIndex = 0
                val selectedOption = when (state.optionsModalFocusIndex) {
                    currentIndex++ -> FeedOption.CREATE_DOODLE
                    else -> {
                        if (userName != null && hasEvent && state.optionsModalFocusIndex == currentIndex++) {
                            FeedOption.VIEW_PROFILE
                        } else if (hasEvent) {
                            val baseIndex = if (userName != null) 2 else 1
                            when (state.optionsModalFocusIndex - baseIndex) {
                                0 -> FeedOption.SHARE_SCREENSHOT
                                1 -> FeedOption.REPORT_POST
                                2 -> FeedOption.HIDE_POST
                                else -> null
                            }
                        } else null
                    }
                }

                Log.d(TAG, "onConfirm (modal): selectedOption=$selectedOption")
                hideOptionsModal()

                when (selectedOption) {
                    FeedOption.CREATE_DOODLE -> onCreateDoodle()
                    FeedOption.QUAYPASS_PLAZA -> onNavigateToQuayPass()
                    FeedOption.VIEW_PROFILE -> focusedEvent?.user?.id?.let { onViewProfile(it) }
                    FeedOption.SHARE_SCREENSHOT -> onShareScreenshot()
                    FeedOption.REPORT_POST -> showReportReasonModal()
                    FeedOption.HIDE_POST -> hideCurrentEvent()
                    null -> {}
                }
                return InputResult.HANDLED
            }

            // A button opens event detail
            _uiState.value.focusedEvent?.let { event ->
                onOpenEventDetail(event.id)
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            if (_uiState.value.showReportReasonModal) {
                hideReportReasonModal()
                return InputResult.HANDLED
            }
            if (_uiState.value.showOptionsModal) {
                hideOptionsModal()
                return InputResult.HANDLED
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            if (_uiState.value.showOptionsModal) return InputResult.UNHANDLED
            refresh()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            // Y button now likes (matches Favorite convention)
            if (_uiState.value.showOptionsModal) return InputResult.UNHANDLED
            likeCurrentEvent()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (_uiState.value.showOptionsModal) return InputResult.UNHANDLED
            showOptionsModal()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult = InputResult.UNHANDLED
        override fun onPrevSection(): InputResult {
            if (_uiState.value.showOptionsModal) return InputResult.UNHANDLED
            return if (switchFriend(-1)) InputResult.HANDLED else InputResult.UNHANDLED
        }
        override fun onNextSection(): InputResult {
            if (_uiState.value.showOptionsModal) return InputResult.UNHANDLED
            return if (switchFriend(1)) InputResult.HANDLED else InputResult.UNHANDLED
        }
    }
}
