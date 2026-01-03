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
import kotlinx.coroutines.flow.flatMapLatest
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
    val showOptionsModal: Boolean = false,
    val optionsModalFocusIndex: Int = 0,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRemoveGameDialog: Boolean = false,
    val gameToRemove: CollectionGameUi? = null
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

    private data class ModalState(
        val focusedIndex: Int = 0,
        val showOptionsModal: Boolean = false,
        val optionsModalFocusIndex: Int = 0,
        val showEditDialog: Boolean = false,
        val showDeleteDialog: Boolean = false,
        val showRemoveGameDialog: Boolean = false,
        val gameToRemove: CollectionGameUi? = null
    )

    private val _modalState = MutableStateFlow(ModalState())
    private val _refreshTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CollectionDetailUiState> = _refreshTrigger.flatMapLatest {
        combine(
            collectionDao.observeCollectionById(collectionId),
            collectionDao.observeGamesInCollection(collectionId),
            _modalState
        ) { collection, games, modalState ->
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
                focusedIndex = modalState.focusedIndex.coerceIn(0, (gamesUi.size - 1).coerceAtLeast(0)),
                showOptionsModal = modalState.showOptionsModal,
                optionsModalFocusIndex = modalState.optionsModalFocusIndex,
                showEditDialog = modalState.showEditDialog,
                showDeleteDialog = modalState.showDeleteDialog,
                showRemoveGameDialog = modalState.showRemoveGameDialog,
                gameToRemove = modalState.gameToRemove
            )
        }
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
        val currentIndex = _modalState.value.focusedIndex
        if (currentIndex > 0) {
            _modalState.value = _modalState.value.copy(focusedIndex = currentIndex - 1)
        }
    }

    fun moveDown() {
        val state = uiState.value
        val currentIndex = _modalState.value.focusedIndex
        if (currentIndex < state.games.size - 1) {
            _modalState.value = _modalState.value.copy(focusedIndex = currentIndex + 1)
        }
    }

    fun showOptionsModal() {
        _modalState.value = _modalState.value.copy(
            showOptionsModal = true,
            optionsModalFocusIndex = 0
        )
    }

    fun hideOptionsModal() {
        _modalState.value = _modalState.value.copy(showOptionsModal = false)
    }

    fun moveOptionsFocus(delta: Int) {
        val maxIndex = 1
        val currentIndex = _modalState.value.optionsModalFocusIndex
        val newIndex = (currentIndex + delta).coerceIn(0, maxIndex)
        _modalState.value = _modalState.value.copy(optionsModalFocusIndex = newIndex)
    }

    fun selectOptionAtIndex(index: Int) {
        _modalState.value = _modalState.value.copy(optionsModalFocusIndex = index)
        confirmOptionSelection()
    }

    fun confirmOptionSelection() {
        when (_modalState.value.optionsModalFocusIndex) {
            0 -> {
                hideOptionsModal()
                showEditDialog()
            }
            1 -> {
                hideOptionsModal()
                showDeleteDialog()
            }
        }
    }

    fun showEditDialog() {
        _modalState.value = _modalState.value.copy(showEditDialog = true)
    }

    fun hideEditDialog() {
        _modalState.value = _modalState.value.copy(showEditDialog = false)
    }

    fun showDeleteDialog() {
        _modalState.value = _modalState.value.copy(showDeleteDialog = true)
    }

    fun hideDeleteDialog() {
        _modalState.value = _modalState.value.copy(showDeleteDialog = false)
    }

    fun showRemoveGameDialog() {
        val game = uiState.value.focusedGame ?: return
        _modalState.value = _modalState.value.copy(
            gameToRemove = game,
            showRemoveGameDialog = true
        )
    }

    fun hideRemoveGameDialog() {
        _modalState.value = _modalState.value.copy(
            showRemoveGameDialog = false,
            gameToRemove = null
        )
    }

    fun confirmRemoveGame() {
        val game = _modalState.value.gameToRemove ?: return
        viewModelScope.launch {
            removeGameFromCollectionUseCase(game.id, collectionId)
            hideRemoveGameDialog()
            _refreshTrigger.value++
        }
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
            removeGameFromCollectionUseCase(gameId, collectionId)
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onGameClick: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = uiState.value
            when {
                state.showOptionsModal -> {
                    moveOptionsFocus(-1)
                    return InputResult.HANDLED
                }
                else -> {
                    moveUp()
                    return InputResult.HANDLED
                }
            }
        }

        override fun onDown(): InputResult {
            val state = uiState.value
            when {
                state.showOptionsModal -> {
                    moveOptionsFocus(1)
                    return InputResult.HANDLED
                }
                else -> {
                    moveDown()
                    return InputResult.HANDLED
                }
            }
        }

        override fun onConfirm(): InputResult {
            val state = uiState.value
            when {
                state.showOptionsModal -> {
                    confirmOptionSelection()
                    return InputResult.HANDLED
                }
                else -> {
                    state.focusedGame?.let { onGameClick(it.id) }
                    return InputResult.HANDLED
                }
            }
        }

        override fun onBack(): InputResult {
            val state = uiState.value
            when {
                state.showOptionsModal -> {
                    hideOptionsModal()
                    return InputResult.HANDLED
                }
                else -> {
                    onBack()
                    return InputResult.HANDLED
                }
            }
        }

        override fun onContextMenu(): InputResult {
            if (uiState.value.focusedGame != null) {
                showRemoveGameDialog()
            }
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            showOptionsModal()
            return InputResult.HANDLED
        }
    }
}
