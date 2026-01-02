package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.domain.usecase.collection.GetGamesByCategoryUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.net.URLDecoder
import javax.inject.Inject

data class VirtualCategoryUiState(
    val type: String = "",
    val categoryName: String = "",
    val games: List<CollectionGameUi> = emptyList(),
    val isLoading: Boolean = true,
    val focusedIndex: Int = 0
) {
    val focusedGame: CollectionGameUi?
        get() = games.getOrNull(focusedIndex)
}

@HiltViewModel
class VirtualCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGamesByCategoryUseCase: GetGamesByCategoryUseCase,
    private val platformDao: PlatformDao
) : ViewModel() {

    private val type: String = checkNotNull(savedStateHandle["type"])
    private val category: String = URLDecoder.decode(checkNotNull(savedStateHandle["category"]), "UTF-8")
    private val _focusedIndex = MutableStateFlow(0)

    private val categoryType = when (type) {
        "genres" -> CategoryType.GENRE
        "modes" -> CategoryType.GAME_MODE
        else -> CategoryType.GENRE
    }

    val uiState: StateFlow<VirtualCategoryUiState> = combine(
        getGamesByCategoryUseCase(categoryType, category),
        _focusedIndex
    ) { games, focusedIndex ->
        val platformCache = mutableMapOf<Long, String>()
        val gamesUi = games.map { game ->
            val platformName = platformCache.getOrPut(game.platformId) {
                platformDao.getById(game.platformId)?.shortName ?: "Unknown"
            }
            game.toUi(platformName)
        }
        VirtualCategoryUiState(
            type = type,
            categoryName = category,
            games = gamesUi,
            isLoading = false,
            focusedIndex = focusedIndex.coerceIn(0, (gamesUi.size - 1).coerceAtLeast(0))
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
    }
}
