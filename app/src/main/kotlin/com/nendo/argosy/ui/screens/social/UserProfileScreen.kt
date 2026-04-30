package com.nendo.argosy.ui.screens.social

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.social.UserProfileData
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.core.notification.NotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "UserProfileScreen"
private const val DISPLAY_SECTIONS = 3

data class UserProfileUiState(
    val profile: UserProfileData? = null,
    val isLoading: Boolean = true,
    val focusIndex: Int = 0,
    val error: String? = null,
    val localIgdbIds: Set<Int> = emptySet()
) {
    val focusCount: Int
        get() = DISPLAY_SECTIONS + (profile?.mostPlayed?.size ?: 0)

    val focusOnMostPlayed: Boolean
        get() = focusIndex >= DISPLAY_SECTIONS && profile?.mostPlayed?.isNotEmpty() == true

    val focusedMostPlayedIndex: Int
        get() = (focusIndex - DISPLAY_SECTIONS).coerceAtLeast(0)

    val focusedGameInLibrary: Boolean
        get() {
            if (!focusOnMostPlayed) return false
            val game = profile?.mostPlayed?.getOrNull(focusedMostPlayedIndex) ?: return false
            return game.igdbId in localIgdbIds
        }
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val gameRepository: GameRepository,
    val notificationManager: NotificationManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: String = savedStateHandle["userId"] ?: ""

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            socialRepository.userProfile.collect { profile ->
                if (profile != null && profile.user.id == userId) {
                    val igdbIds = profile.mostPlayed.map { it.igdbId }
                        .filter { gameRepository.getByIgdbId(it.toLong()) != null }
                        .toSet()
                    _uiState.value = _uiState.value.copy(
                        profile = profile,
                        isLoading = false,
                        localIgdbIds = igdbIds
                    )
                }
            }
        }
    }

    fun loadProfile() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        socialRepository.requestUserProfile(userId)
    }

    fun moveFocus(delta: Int): Boolean {
        val state = _uiState.value
        val maxIndex = (state.focusCount - 1).coerceAtLeast(0)
        val newIndex = (state.focusIndex + delta).coerceIn(0, maxIndex)
        if (newIndex != state.focusIndex) {
            _uiState.value = state.copy(focusIndex = newIndex)
            return true
        }
        return false
    }

    fun toggleFavorite() {
        val profile = _uiState.value.profile ?: return
        if (profile.relationship != "friend") return
        socialRepository.toggleFavoriteFriend(userId)
        _uiState.value = _uiState.value.copy(
            profile = profile.copy(isFavorite = !profile.isFavorite)
        )
    }

    fun sendFriendRequest() {
        val profile = _uiState.value.profile ?: return
        if (profile.relationship != "none") return
        socialRepository.sendFriendRequest(userId)
    }

    suspend fun getLocalGameId(igdbId: Int): Long? {
        return gameRepository.getByIgdbId(igdbId.toLong())?.id
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onNavigateToGameDetail: (Int) -> Unit
    ): InputHandler = object : InputHandler {

        override fun onUp(): InputResult =
            if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onDown(): InputResult =
            if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            if (state.focusOnMostPlayed) {
                val game = state.profile?.mostPlayed?.getOrNull(state.focusedMostPlayedIndex)
                if (game != null) {
                    viewModelScope.launch {
                        val localId = getLocalGameId(game.igdbId)
                        if (localId != null) {
                            onNavigateToGameDetail(localId.toInt())
                        } else {
                            notificationManager.show(title = "Game not in library")
                        }
                    }
                }
            }
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val profile = _uiState.value.profile ?: return InputResult.UNHANDLED
            when (profile.relationship) {
                "friend" -> toggleFavorite()
                "none" -> sendFriendRequest()
                else -> return InputResult.UNHANDLED
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
    }
}

@Composable
fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onNavigateToGameDetail: (Int) -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onNavigateToGameDetail) {
        viewModel.createInputHandler(
            onBack = onBack,
            onNavigateToGameDetail = onNavigateToGameDetail
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_USER_PROFILE)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_USER_PROFILE)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(userId) {
        viewModel.loadProfile()
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusIndex, uiState.profile?.mostPlayed?.size) {
        val mostPlayedCount = uiState.profile?.mostPlayed?.size ?: 0
        val itemIndex = profileFocusToItemIndex(uiState.focusIndex, mostPlayedCount)
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 60
        val centerOffset = (viewportHeight - itemHeight) / 2
        listState.animateScrollToItem(itemIndex, -centerOffset)
    }

    val profile = uiState.profile

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isLoading && profile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (profile != null) {
                val mostPlayed = profile.mostPlayed
                val mostPlayedFocusIndex = uiState.focusedMostPlayedIndex
                val highlightMostPlayed = uiState.focusOnMostPlayed

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    item {
                        AccountInfoCard(user = profile.user, profile = profile)
                    }

                    item {
                        ProfileStatsGrid(
                            profile = profile,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item {
                        PlaytimeLineChart(
                            dailyPlaytime = profile.dailyPlaytime,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (mostPlayed.isNotEmpty()) {
                        item {
                            Text(
                                text = "MOST PLAYED",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }

                        mostPlayed.forEachIndexed { index, game ->
                            item(key = "most_played_${game.igdbId}") {
                                MostPlayedGameItem(
                                    game = game,
                                    isFocused = highlightMostPlayed && index == mostPlayedFocusIndex,
                                    isFirst = index == 0,
                                    isLast = index == mostPlayed.size - 1,
                                    onGameClick = { igdbId ->
                                        viewModel.viewModelScope.launch {
                                            val localId = viewModel.getLocalGameId(igdbId)
                                            if (localId != null) {
                                                onNavigateToGameDetail(localId.toInt())
                                            } else {
                                                viewModel.notificationManager.show(title = "Game not in library")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        FooterBarWithState(
            hints = buildList {
                add(FooterHintItem(InputButton.A, "View Game", enabled = uiState.focusedGameInLibrary))
                if (profile != null) {
                    when (profile.relationship) {
                        "friend" -> add(FooterHintItem(
                            InputButton.Y,
                            if (profile.isFavorite) "Unfavorite" else "Favorite"
                        ))
                        "none" -> add(FooterHintItem(InputButton.Y, "Add Friend"))
                    }
                }
                add(FooterHintItem(InputButton.B, "Back"))
            }
        )
    }
}
