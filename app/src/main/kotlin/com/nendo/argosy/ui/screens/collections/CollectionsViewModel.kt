package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.domain.usecase.collection.CategoryWithCount
import com.nendo.argosy.domain.usecase.collection.CollectionWithCount
import com.nendo.argosy.domain.usecase.collection.CreateCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.DeleteCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.GetCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.GetVirtualCollectionCategoriesUseCase
import com.nendo.argosy.domain.usecase.collection.UpdateCollectionUseCase
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
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val editingCollection: CollectionWithCount? = null
) {
    val totalCollectionItems: Int
        get() = collections.size

    val browseByItems: List<String> = listOf("Genres", "Game Modes")

    val focusedCollection: CollectionWithCount?
        get() = if (focusedSection == CollectionSection.MY_COLLECTIONS && focusedIndex in collections.indices) {
            collections[focusedIndex]
        } else null
}

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val getVirtualCollectionCategoriesUseCase: GetVirtualCollectionCategoriesUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val updateCollectionUseCase: UpdateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val romMRepository: RomMRepository
) : ViewModel() {

    private val _focusedSection = MutableStateFlow(CollectionSection.MY_COLLECTIONS)
    private val _focusedIndex = MutableStateFlow(0)
    private val _showCreateDialog = MutableStateFlow(false)
    private val _showEditDialog = MutableStateFlow(false)
    private val _showDeleteDialog = MutableStateFlow(false)
    private val _editingCollection = MutableStateFlow<CollectionWithCount?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _localRefreshTrigger = MutableStateFlow(0)
    private var lastRefreshTime = 0L

    init {
        refreshCollections(force = true)
    }

    fun refreshCollections(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshTime < REFRESH_DEBOUNCE_MS) {
            return
        }
        if (_isRefreshing.value) return

        lastRefreshTime = now
        viewModelScope.launch {
            _isRefreshing.value = true
            romMRepository.syncCollections()
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
        combine(
            getCollectionsUseCase(),
            getVirtualCollectionCategoriesUseCase.getGenres(),
            getVirtualCollectionCategoriesUseCase.getGameModes(),
            _focusedSection,
            _focusedIndex,
            _showCreateDialog,
            _showEditDialog,
            _showDeleteDialog,
            _editingCollection,
            _isRefreshing
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            CollectionsUiState(
                collections = values[0] as List<CollectionWithCount>,
                genres = values[1] as List<CategoryWithCount>,
                gameModes = values[2] as List<CategoryWithCount>,
                isLoading = false,
                isRefreshing = values[9] as Boolean,
                focusedSection = values[3] as CollectionSection,
                focusedIndex = values[4] as Int,
                showCreateDialog = values[5] as Boolean,
                showEditDialog = values[6] as Boolean,
                showDeleteDialog = values[7] as Boolean,
                editingCollection = values[8] as CollectionWithCount?
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
                    _focusedIndex.value = (state.collections.size - 1).coerceAtLeast(0)
                }
            }
        }
    }

    fun moveDown() {
        val state = uiState.value
        when (state.focusedSection) {
            CollectionSection.MY_COLLECTIONS -> {
                if (_focusedIndex.value < state.collections.size - 1) {
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

    fun showEditDialog(collection: CollectionWithCount) {
        _editingCollection.value = collection
        _showEditDialog.value = true
    }

    fun hideEditDialog() {
        _showEditDialog.value = false
        _editingCollection.value = null
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
            createCollectionUseCase(name)
            hideCreateDialog()
        }
    }

    fun updateCollection(collectionId: Long, name: String) {
        viewModelScope.launch {
            updateCollectionUseCase(collectionId, name)
            hideEditDialog()
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            deleteCollectionUseCase(collectionId)
            hideDeleteDialog()
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onCollectionClick: (Long) -> Unit,
        onVirtualBrowseClick: (String) -> Unit
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
            val state = uiState.value
            when (state.focusedSection) {
                CollectionSection.MY_COLLECTIONS -> {
                    state.focusedCollection?.let { onCollectionClick(it.id) }
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
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            showCreateDialog()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val state = uiState.value
            if (state.focusedSection == CollectionSection.MY_COLLECTIONS) {
                state.focusedCollection?.let { showEditDialog(it) }
            }
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            val state = uiState.value
            if (state.focusedSection == CollectionSection.MY_COLLECTIONS) {
                state.focusedCollection?.let { showDeleteDialog(it) }
            }
            return InputResult.HANDLED
        }

        override fun onNextSection(): InputResult {
            refreshCollections()
            return InputResult.HANDLED
        }
    }
}
