package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.domain.usecase.collection.CategoryWithCount
import com.nendo.argosy.domain.usecase.collection.CollectionWithCount
import com.nendo.argosy.domain.usecase.collection.GetCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.GetVirtualCollectionCategoriesUseCase
import com.nendo.argosy.domain.usecase.collection.IsPinnedUseCase
import com.nendo.argosy.domain.usecase.collection.PinCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.RefreshAllCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.UnpinCollectionUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOption
import javax.inject.Inject

enum class CollectionSection {
    MY_COLLECTIONS,
    BROWSE_BY
}

data class CollectionsUiState(
    val collections: List<CollectionWithCount> = emptyList(),
    val genres: List<CategoryWithCount> = emptyList(),
    val gameModes: List<CategoryWithCount> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val focusedSection: CollectionSection = CollectionSection.MY_COLLECTIONS,
    val focusedIndex: Int = 0,
    val showCreateDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val editingCollection: CollectionWithCount? = null,
    val pinnedCollectionIds: Set<Long> = emptySet(),
    val showOptionsModal: Boolean = false,
    val optionsFocusedIndex: Int = 0
) {
    val totalCollectionItems: Int
        get() = collections.size + 1

    val browseByItems: List<String> = listOf("Genres", "Game Modes")

    val isNewCollectionItemFocused: Boolean
        get() = focusedSection == CollectionSection.MY_COLLECTIONS && focusedIndex == collections.size

    val focusedCollection: CollectionWithCount?
        get() = if (focusedSection == CollectionSection.MY_COLLECTIONS && focusedIndex in collections.indices) {
            collections[focusedIndex]
        } else null

    val isFocusedCollectionPinned: Boolean
        get() = focusedCollection?.let { it.id in pinnedCollectionIds } ?: false

    val availableOptions: List<CollectionOption>
        get() = if (focusedCollection != null) listOf(CollectionOption.RENAME, CollectionOption.DELETE) else emptyList()

    val focusedOption: CollectionOption?
        get() = availableOptions.getOrNull(optionsFocusedIndex)
}

private data class DialogState(
    val showCreate: Boolean,
    val showDelete: Boolean,
    val editing: CollectionWithCount?,
    val showOptionsModal: Boolean,
    val optionsFocusedIndex: Int
)

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val getVirtualCollectionCategoriesUseCase: GetVirtualCollectionCategoriesUseCase,
    private val romMRepository: RomMRepository,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase
) : ViewModel() {

    private val _focusedSection = MutableStateFlow(CollectionSection.MY_COLLECTIONS)
    private val _focusedIndex = MutableStateFlow(0)
    private val _showCreateDialog = MutableStateFlow(false)
    private val _showDeleteDialog = MutableStateFlow(false)
    private val _editingCollection = MutableStateFlow<CollectionWithCount?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _localRefreshTrigger = MutableStateFlow(0)
    private val _pinnedCollectionIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _showOptionsModal = MutableStateFlow(false)
    private val _optionsFocusedIndex = MutableStateFlow(0)
    private var lastRefreshTime = 0L

    init {
        Log.d("CollectionsVM", "init: starting")
        refreshCollections(force = true)
        loadPinnedIds()
    }

    private fun loadPinnedIds() {
        viewModelScope.launch {
            val pinnedIds = withContext(Dispatchers.IO) {
                val collections = getCollectionsUseCase().stateIn(viewModelScope).value
                collections.filter { isPinnedUseCase.isRegularPinned(it.id) }.map { it.id }.toSet()
            }
            _pinnedCollectionIds.value = pinnedIds
        }
    }

    fun refreshCollections(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshTime < REFRESH_DEBOUNCE_MS) {
            Log.d("CollectionsVM", "refreshCollections: debounced")
            return
        }
        if (_isRefreshing.value) {
            Log.d("CollectionsVM", "refreshCollections: already refreshing")
            return
        }

        lastRefreshTime = now
        viewModelScope.launch {
            Log.d("CollectionsVM", "refreshCollections: starting sync")
            _isRefreshing.value = true
            refreshAllCollectionsUseCase()
            Log.d("CollectionsVM", "refreshCollections: sync complete")
            _isRefreshing.value = false
        }
    }

    fun refreshLocal() {
        _localRefreshTrigger.value++
    }

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 30_000L
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CollectionsUiState> = _localRefreshTrigger.flatMapLatest {
        Log.d("CollectionsVM", "uiState: flatMapLatest triggered")
        val dataFlow = combine(
            getCollectionsUseCase(),
            getVirtualCollectionCategoriesUseCase.getGenres(),
            getVirtualCollectionCategoriesUseCase.getGameModes()
        ) { collections, genres, gameModes ->
            Log.d("CollectionsVM", "dataFlow: collections=${collections.size}, genres=${genres.size}, modes=${gameModes.size}")
            Triple(collections, genres, gameModes)
        }
        val focusFlow = combine(_focusedSection, _focusedIndex) { section, index ->
            section to index
        }
        val dialogFlow = combine(
            _showCreateDialog, _showDeleteDialog, _editingCollection, _showOptionsModal, _optionsFocusedIndex
        ) { create, delete, editing, showOptions, optionsIndex ->
            DialogState(create, delete, editing, showOptions, optionsIndex)
        }
        val miscFlow = combine(_isRefreshing, _pinnedCollectionIds) { refreshing, pinnedIds ->
            refreshing to pinnedIds
        }
        combine(dataFlow, focusFlow, dialogFlow, miscFlow) { data, focus, dialogs, misc ->
            CollectionsUiState(
                collections = data.first,
                genres = data.second,
                gameModes = data.third,
                isLoading = false,
                isRefreshing = misc.first,
                focusedSection = focus.first,
                focusedIndex = focus.second,
                showCreateDialog = dialogs.showCreate,
                showDeleteDialog = dialogs.showDelete,
                editingCollection = dialogs.editing,
                pinnedCollectionIds = misc.second,
                showOptionsModal = dialogs.showOptionsModal,
                optionsFocusedIndex = dialogs.optionsFocusedIndex
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CollectionsUiState()
    )

    fun moveUp() {
        val state = uiState.value
        when (state.focusedSection) {
            CollectionSection.MY_COLLECTIONS -> {
                if (_focusedIndex.value > 0) {
                    _focusedIndex.value--
                }
            }
            CollectionSection.BROWSE_BY -> {
                if (_focusedIndex.value > 0) {
                    _focusedIndex.value--
                } else {
                    _focusedSection.value = CollectionSection.MY_COLLECTIONS
                    _focusedIndex.value = state.collections.size
                }
            }
        }
    }

    fun moveDown() {
        val state = uiState.value
        when (state.focusedSection) {
            CollectionSection.MY_COLLECTIONS -> {
                if (_focusedIndex.value < state.collections.size) {
                    _focusedIndex.value++
                } else {
                    _focusedSection.value = CollectionSection.BROWSE_BY
                    _focusedIndex.value = 0
                }
            }
            CollectionSection.BROWSE_BY -> {
                if (_focusedIndex.value < state.browseByItems.size - 1) {
                    _focusedIndex.value++
                }
            }
        }
    }

    fun showCreateDialog() {
        _showCreateDialog.value = true
    }

    fun hideCreateDialog() {
        _showCreateDialog.value = false
    }

    fun showOptionsModal() {
        if (uiState.value.focusedCollection != null) {
            _optionsFocusedIndex.value = 0
            _showOptionsModal.value = true
        }
    }

    fun hideOptionsModal() {
        _showOptionsModal.value = false
        _optionsFocusedIndex.value = 0
    }

    fun moveOptionsUp() {
        val currentIndex = _optionsFocusedIndex.value
        if (currentIndex > 0) {
            _optionsFocusedIndex.value = currentIndex - 1
        }
    }

    fun moveOptionsDown() {
        val state = uiState.value
        val currentIndex = _optionsFocusedIndex.value
        if (currentIndex < state.availableOptions.size - 1) {
            _optionsFocusedIndex.value = currentIndex + 1
        }
    }

    fun selectOption() {
        val state = uiState.value
        val collection = state.focusedCollection ?: return
        when (state.focusedOption) {
            CollectionOption.RENAME -> {
                _editingCollection.value = collection
                hideOptionsModal()
                _showCreateDialog.value = true
            }
            CollectionOption.DELETE -> {
                _editingCollection.value = collection
                hideOptionsModal()
                _showDeleteDialog.value = true
            }
            CollectionOption.DOWNLOAD_ALL, CollectionOption.REMOVE_GAME, null -> {}
        }
    }

    fun showDeleteDialog(collection: CollectionWithCount) {
        _editingCollection.value = collection
        _showDeleteDialog.value = true
    }

    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
        _editingCollection.value = null
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            romMRepository.createCollectionWithSync(name)
            hideCreateDialog()
        }
    }

    fun updateCollection(collectionId: Long, name: String) {
        viewModelScope.launch {
            romMRepository.updateCollectionWithSync(collectionId, name, null)
            hideCreateDialog()
            _editingCollection.value = null
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            romMRepository.deleteCollectionWithSync(collectionId)
            hideDeleteDialog()
        }
    }

    fun togglePinFocused() {
        val collection = uiState.value.focusedCollection ?: return
        viewModelScope.launch {
            val isPinned = collection.id in _pinnedCollectionIds.value
            if (isPinned) {
                unpinCollectionUseCase.unpinRegular(collection.id)
                _pinnedCollectionIds.value = _pinnedCollectionIds.value - collection.id
            } else {
                pinCollectionUseCase.pinRegular(collection.id)
                _pinnedCollectionIds.value = _pinnedCollectionIds.value + collection.id
            }
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onCollectionClick: (Long) -> Unit,
        onVirtualBrowseClick: (String) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = uiState.value
            if (state.showCreateDialog || state.showDeleteDialog) {
                return InputResult.UNHANDLED
            }
            if (state.showOptionsModal) {
                moveOptionsUp()
            } else {
                moveUp()
            }
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            val state = uiState.value
            if (state.showCreateDialog || state.showDeleteDialog) {
                return InputResult.UNHANDLED
            }
            if (state.showOptionsModal) {
                moveOptionsDown()
            } else {
                moveDown()
            }
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            val state = uiState.value
            if (state.showCreateDialog || state.showDeleteDialog) {
                return InputResult.UNHANDLED
            }
            if (state.showOptionsModal) {
                selectOption()
                return InputResult.HANDLED
            }
            when (state.focusedSection) {
                CollectionSection.MY_COLLECTIONS -> {
                    if (state.isNewCollectionItemFocused) {
                        showCreateDialog()
                    } else {
                        state.focusedCollection?.let { onCollectionClick(it.id) }
                    }
                }
                CollectionSection.BROWSE_BY -> {
                    when (state.focusedIndex) {
                        0 -> onVirtualBrowseClick("genres")
                        1 -> onVirtualBrowseClick("modes")
                    }
                }
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = uiState.value
            if (state.showCreateDialog) {
                hideCreateDialog()
                return InputResult.HANDLED
            }
            if (state.showDeleteDialog) {
                hideDeleteDialog()
                return InputResult.HANDLED
            }
            if (state.showOptionsModal) {
                hideOptionsModal()
                return InputResult.HANDLED
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = uiState.value
            if (state.showOptionsModal) return InputResult.HANDLED
            if (state.focusedSection == CollectionSection.MY_COLLECTIONS && state.focusedCollection != null) {
                togglePinFocused()
            }
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val state = uiState.value
            if (state.showOptionsModal) return InputResult.HANDLED
            refreshCollections()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            val state = uiState.value
            if (state.showOptionsModal) return InputResult.HANDLED
            if (state.focusedSection == CollectionSection.MY_COLLECTIONS && state.focusedCollection != null) {
                showOptionsModal()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
    }
}
