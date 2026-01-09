package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.domain.usecase.collection.IsPinnedUseCase
import com.nendo.argosy.domain.usecase.collection.PinCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.RefreshAllCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.RemoveGameFromCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.UnpinCollectionUseCase
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
    val platformDisplayName: String,
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
    val isRefreshing: Boolean = false,
    val focusedIndex: Int = 0,
    val showOptionsModal: Boolean = false,
    val optionsModalFocusIndex: Int = 0,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRemoveGameDialog: Boolean = false,
    val gameToRemove: CollectionGameUi? = null,
    val isPinned: Boolean = false
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
    private val removeGameFromCollectionUseCase: RemoveGameFromCollectionUseCase,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase
) : ViewModel() {

    private val collectionId: Long = checkNotNull(savedStateHandle["collectionId"])

    private data class ModalState(
        val focusedIndex: Int = 0,
        val showOptionsModal: Boolean = false,
        val optionsModalFocusIndex: Int = 0,
        val showEditDialog: Boolean = false,
        val showDeleteDialog: Boolean = false,
        val showRemoveGameDialog: Boolean = false,
        val gameToRemove: CollectionGameUi? = null,
        val isPinned: Boolean = false,
        val isRefreshing: Boolean = false
    )

    private val _modalState = MutableStateFlow(ModalState())
    private val _refreshTrigger = MutableStateFlow(0)
    private var lastRefreshTime = 0L

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 30_000L
    }

    init {
        loadPinStatus()
    }

    private fun loadPinStatus() {
        viewModelScope.launch {
            val isPinned = isPinnedUseCase.isRegularPinned(collectionId)
            _modalState.value = _modalState.value.copy(isPinned = isPinned)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CollectionDetailUiState> = _refreshTrigger.flatMapLatest {
        combine(
            collectionDao.observeCollectionById(collectionId),
            collectionDao.observeGamesInCollection(collectionId),
            platformDao.observeAllPlatforms(),
            _modalState
        ) { collection, games, platforms, modalState ->
            val platformMap = platforms.associate { it.id to it.getDisplayName() }
            val gamesUi = games.map { game ->
                game.toUi(platformMap[game.platformId] ?: "Unknown")
            }
            CollectionDetailUiState(
                collection = collection,
                games = gamesUi,
                isLoading = false,
                isRefreshing = modalState.isRefreshing,
                focusedIndex = modalState.focusedIndex.coerceIn(0, (gamesUi.size - 1).coerceAtLeast(0)),
                showOptionsModal = modalState.showOptionsModal,
                optionsModalFocusIndex = modalState.optionsModalFocusIndex,
                showEditDialog = modalState.showEditDialog,
                showDeleteDialog = modalState.showDeleteDialog,
                showRemoveGameDialog = modalState.showRemoveGameDialog,
                gameToRemove = modalState.gameToRemove,
                isPinned = modalState.isPinned
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CollectionDetailUiState()
    )

    private fun GameEntity.toUi(platformDisplayName: String) = CollectionGameUi(
        id = id,
        title = title,
        platformId = platformId,
        platformDisplayName = platformDisplayName,
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
        val hasGame = uiState.value.focusedGame != null
        val maxIndex = if (hasGame) 2 else 1
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
            2 -> {
                hideOptionsModal()
                showRemoveGameDialog()
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

    fun togglePin() {
        viewModelScope.launch {
            val isPinned = _modalState.value.isPinned
            if (isPinned) {
                unpinCollectionUseCase.unpinRegular(collectionId)
            } else {
                pinCollectionUseCase.pinRegular(collectionId)
            }
            _modalState.value = _modalState.value.copy(isPinned = !isPinned)
        }
    }

    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_DEBOUNCE_MS) return
        if (_modalState.value.isRefreshing) return

        lastRefreshTime = now
        viewModelScope.launch {
            _modalState.value = _modalState.value.copy(isRefreshing = true)
            refreshAllCollectionsUseCase()
            _modalState.value = _modalState.value.copy(isRefreshing = false)
            _refreshTrigger.value++
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onGameClick: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        private fun hasDialogOpen(state: CollectionDetailUiState): Boolean =
            state.showEditDialog || state.showDeleteDialog || state.showRemoveGameDialog

        override fun onUp(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
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
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
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
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
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
                state.showEditDialog -> {
                    hideEditDialog()
                    return InputResult.HANDLED
                }
                state.showDeleteDialog -> {
                    hideDeleteDialog()
                    return InputResult.HANDLED
                }
                state.showRemoveGameDialog -> {
                    hideRemoveGameDialog()
                    return InputResult.HANDLED
                }
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
            val state = uiState.value
            if (state.showOptionsModal) return InputResult.HANDLED
            refresh()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            showOptionsModal()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            togglePin()
            return InputResult.HANDLED
        }
    }
}
