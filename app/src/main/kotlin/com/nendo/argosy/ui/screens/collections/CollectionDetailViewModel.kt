package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.domain.usecase.collection.RemoveGameFromCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.UpdateCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.DeleteCollectionUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionGameUi(
    val id: Long,
    val title: String,
    val platformId: Long,
    val platformShortName: String,
    val coverPath: String?,
    val developer: String?,
    val releaseYear: Int?,
    val genre: String?,
    val userRating: Int,
    val userDifficulty: Int,
    val achievementCount: Int,
    val playTimeMinutes: Int,
    val isFavorite: Boolean
)

data class CollectionDetailUiState(
    val collection: CollectionEntity? = null,
    val games: List<CollectionGameUi> = emptyList(),
    val isLoading: Boolean = true,
    val focusedIndex: Int = 0,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false
) {
    val focusedGame: CollectionGameUi?
        get() = games.getOrNull(focusedIndex)
}

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionDao: CollectionDao,
    private val platformDao: PlatformDao,
    private val updateCollectionUseCase: UpdateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val removeGameFromCollectionUseCase: RemoveGameFromCollectionUseCase
) : ViewModel() {

    private val collectionId: Long = checkNotNull(savedStateHandle["collectionId"])

    private val _focusedIndex = MutableStateFlow(0)
    private val _showEditDialog = MutableStateFlow(false)
    private val _showDeleteDialog = MutableStateFlow(false)

    val uiState: StateFlow<CollectionDetailUiState> = combine(
        collectionDao.observeCollectionById(collectionId),
        collectionDao.observeGamesInCollection(collectionId),
        _focusedIndex,
        _showEditDialog,
        _showDeleteDialog
    ) { collection, games, focusedIndex, showEditDialog, showDeleteDialog ->
        val platformCache = mutableMapOf<Long, String>()
        val gamesUi = games.map { game ->
            val platformName = platformCache.getOrPut(game.platformId) {
                platformDao.getById(game.platformId)?.shortName ?: "Unknown"
            }
            game.toUi(platformName)
        }
        CollectionDetailUiState(
            collection = collection,
            games = gamesUi,
            isLoading = false,
            focusedIndex = focusedIndex.coerceIn(0, (gamesUi.size - 1).coerceAtLeast(0)),
            showEditDialog = showEditDialog,
            showDeleteDialog = showDeleteDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CollectionDetailUiState()
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

    fun showEditDialog() {
        _showEditDialog.value = true
    }

    fun hideEditDialog() {
        _showEditDialog.value = false
    }

    fun showDeleteDialog() {
        _showDeleteDialog.value = true
    }

    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
    }

    fun updateCollectionName(name: String) {
        viewModelScope.launch {
            updateCollectionUseCase(collectionId, name)
            hideEditDialog()
        }
    }

    fun deleteCollection(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteCollectionUseCase(collectionId)
            hideDeleteDialog()
            onDeleted()
        }
    }

    fun removeGameFromCollection(gameId: Long) {
        viewModelScope.launch {
            removeGameFromCollectionUseCase(collectionId, gameId)
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

        override fun onContextMenu(): InputResult {
            showEditDialog()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            showDeleteDialog()
            return InputResult.HANDLED
        }
    }
}
