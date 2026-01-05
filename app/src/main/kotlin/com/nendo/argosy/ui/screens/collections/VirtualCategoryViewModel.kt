package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.domain.usecase.collection.GetGamesByCategoryUseCase
import com.nendo.argosy.domain.usecase.collection.IsPinnedUseCase
import com.nendo.argosy.domain.usecase.collection.PinCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.RefreshAllCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.UnpinCollectionUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class VirtualCategoryUiState(
    val type: String = "",
    val categoryName: String = "",
    val games: List<CollectionGameUi> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val focusedIndex: Int = 0,
    val isPinned: Boolean = false
) {
    val focusedGame: CollectionGameUi?
        get() = games.getOrNull(focusedIndex)
}

@HiltViewModel
class VirtualCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGamesByCategoryUseCase: GetGamesByCategoryUseCase,
    private val platformDao: PlatformDao,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase
) : ViewModel() {

    private val type: String = checkNotNull(savedStateHandle["type"])
    private val category: String = URLDecoder.decode(checkNotNull(savedStateHandle["category"]), "UTF-8")
    private val _focusedIndex = MutableStateFlow(0)
    private val _isPinned = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private var lastRefreshTime = 0L

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 30_000L
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
        platformDao.observeAllPlatforms(),
        _focusedIndex,
        _isPinned,
        _isRefreshing
    ) { games, platforms, focusedIndex, isPinned, isRefreshing ->
        val platformMap = platforms.associate { it.id to it.shortName }
        val gamesUi = games.map { game ->
            game.toUi(platformMap[game.platformId] ?: "Unknown")
        }
        VirtualCategoryUiState(
            type = type,
            categoryName = category,
            games = gamesUi,
            isLoading = false,
            isRefreshing = isRefreshing,
            focusedIndex = focusedIndex.coerceIn(0, (gamesUi.size - 1).coerceAtLeast(0)),
            isPinned = isPinned
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VirtualCategoryUiState(type = type, categoryName = category)
    )

    private fun GameEntity.toUi(platformShortName: String) = CollectionGameUi(
        id = id,
        title = title,
        platformId = platformId,
        platformShortName = platformShortName,
        coverPath = coverPath,
        developer = developer,
        releaseYear = releaseYear,
        genre = genre,
        userRating = userRating,
        userDifficulty = userDifficulty,
        achievementCount = achievementCount,
        playTimeMinutes = playTimeMinutes,
        isFavorite = isFavorite
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

    fun createInputHandler(
        onBack: () -> Unit,
        onGameClick: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            moveUp()
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            moveDown()
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            uiState.value.focusedGame?.let { onGameClick(it.id) }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            togglePin()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            refresh()
            return InputResult.HANDLED
        }
    }
}
