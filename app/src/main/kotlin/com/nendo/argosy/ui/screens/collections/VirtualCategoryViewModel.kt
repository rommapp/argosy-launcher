package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.domain.usecase.collection.GetGamesByCategoryUseCase
import com.nendo.argosy.domain.usecase.collection.IsPinnedUseCase
import com.nendo.argosy.domain.usecase.collection.PinCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.RefreshAllCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.UnpinCollectionUseCase
import com.nendo.argosy.domain.usecase.download.DownloadGameUseCase
import com.nendo.argosy.ui.notification.NotificationDuration
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class DownloadAllProgress(
    val isActive: Boolean = false,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val isOnCooldown: Boolean = false
)

data class VirtualCategoryUiState(
    val type: String = "",
    val categoryName: String = "",
    val games: List<CollectionGameUi> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val focusedIndex: Int = 0,
    val isPinned: Boolean = false,
    val downloadAllProgress: DownloadAllProgress = DownloadAllProgress()
) {
    val focusedGame: CollectionGameUi?
        get() = games.getOrNull(focusedIndex)

    val downloadableGamesCount: Int
        get() = games.count { !it.isDownloaded && it.rommId != null }

    val canDownloadAll: Boolean
        get() = downloadableGamesCount > 0 &&
                !downloadAllProgress.isActive &&
                !downloadAllProgress.isOnCooldown
}

@HiltViewModel
class VirtualCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGamesByCategoryUseCase: GetGamesByCategoryUseCase,
    private val platformRepository: PlatformRepository,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase,
    private val downloadGameUseCase: DownloadGameUseCase,
    private val notificationManager: NotificationManager
) : ViewModel() {

    private val type: String = checkNotNull(savedStateHandle["type"])
    private val category: String = URLDecoder.decode(checkNotNull(savedStateHandle["category"]), "UTF-8")
    private val _focusedIndex = MutableStateFlow(0)
    private val _isPinned = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _downloadAllProgress = MutableStateFlow(DownloadAllProgress())
    private var lastRefreshTime = 0L

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 30_000L
        private const val DOWNLOAD_ALL_COOLDOWN_MS = 180_000L
    }

    private val categoryType = when (type) {
        "genres" -> CategoryType.GENRE
        "modes" -> CategoryType.GAME_MODE
        else -> CategoryType.GENRE
    }

    init {
        loadPinStatus()
    }

    private fun loadPinStatus() {
        viewModelScope.launch {
            val isPinned = isPinnedUseCase.isVirtualPinned(categoryType, category)
            _isPinned.value = isPinned
        }
    }

    val uiState: StateFlow<VirtualCategoryUiState> = combine(
        getGamesByCategoryUseCase(categoryType, category),
        platformRepository.observeAllPlatforms(),
        _focusedIndex,
        combine(_isPinned, _isRefreshing, _downloadAllProgress) { a, b, c -> Triple(a, b, c) }
    ) { games, platforms, focusedIndex, (isPinned, isRefreshing, downloadProgress) ->
        val platformMap = platforms.associate { it.id to it.getDisplayName() }
        val gamesUi = games.map { game ->
            game.toCollectionGameUi(platformMap[game.platformId] ?: "Unknown")
        }
        VirtualCategoryUiState(
            type = type,
            categoryName = category,
            games = gamesUi,
            isLoading = false,
            isRefreshing = isRefreshing,
            focusedIndex = focusedIndex.coerceIn(0, (gamesUi.size - 1).coerceAtLeast(0)),
            isPinned = isPinned,
            downloadAllProgress = downloadProgress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VirtualCategoryUiState(type = type, categoryName = category)
    )

    fun moveUp() {
        val currentIndex = _focusedIndex.value
        if (currentIndex > 0) {
            _focusedIndex.value = currentIndex - 1
        }
    }

    fun moveDown() {
        val state = uiState.value
        val currentIndex = _focusedIndex.value
        if (currentIndex < state.games.size - 1) {
            _focusedIndex.value = currentIndex + 1
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val isPinned = _isPinned.value
            if (isPinned) {
                unpinCollectionUseCase.unpinVirtual(categoryType, category)
            } else {
                pinCollectionUseCase.pinVirtual(categoryType, category)
            }
            _isPinned.value = !isPinned
        }
    }

    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_DEBOUNCE_MS) return
        if (_isRefreshing.value) return

        lastRefreshTime = now
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshAllCollectionsUseCase()
            _isRefreshing.value = false
        }
    }

    fun showDownloadAllModal() {
        val state = uiState.value
        if (!state.canDownloadAll) return

        val downloadable = state.games.filter { !it.isDownloaded && it.rommId != null }
        if (downloadable.isEmpty()) return

        _downloadAllProgress.value = DownloadAllProgress(
            isActive = true,
            currentIndex = 0,
            totalCount = downloadable.size,
            isOnCooldown = false
        )

        viewModelScope.launch {
            var queued = 0
            for ((index, game) in downloadable.withIndex()) {
                _downloadAllProgress.value = _downloadAllProgress.value.copy(
                    currentIndex = index + 1
                )
                downloadGameUseCase(game.id)
                queued++
                delay(50)
            }

            _downloadAllProgress.value = _downloadAllProgress.value.copy(
                isActive = false,
                isOnCooldown = true
            )

            notificationManager.show(
                title = "Downloads Queued",
                subtitle = "$queued game${if (queued > 1) "s" else ""} added to download queue",
                type = NotificationType.INFO,
                duration = NotificationDuration.MEDIUM
            )

            delay(DOWNLOAD_ALL_COOLDOWN_MS)
            _downloadAllProgress.value = DownloadAllProgress()
        }
    }

    fun dismissDownloadAllModal() {
        if (!_downloadAllProgress.value.isActive) {
            _downloadAllProgress.value = _downloadAllProgress.value.copy(isActive = false)
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onGameClick: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            moveUp()
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            moveDown()
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            uiState.value.focusedGame?.let { onGameClick(it.id) }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) {
                return InputResult.HANDLED
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            togglePin()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            refresh()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (uiState.value.canDownloadAll) {
                showDownloadAllModal()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
    }
}
