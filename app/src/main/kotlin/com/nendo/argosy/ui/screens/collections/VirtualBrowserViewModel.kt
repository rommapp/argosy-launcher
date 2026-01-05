package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.domain.usecase.collection.CategoryWithCount
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class VirtualBrowserUiState(
    val type: String = "",
    val title: String = "",
    val categories: List<CategoryWithCount> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val focusedIndex: Int = 0,
    val pinnedCategories: Set<String> = emptySet()
) {
    val focusedCategory: CategoryWithCount?
        get() = categories.getOrNull(focusedIndex)

    val isFocusedCategoryPinned: Boolean
        get() = focusedCategory?.let { it.name in pinnedCategories } ?: false
}

@HiltViewModel
class VirtualBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVirtualCollectionCategoriesUseCase: GetVirtualCollectionCategoriesUseCase,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase
) : ViewModel() {

    private val type: String = checkNotNull(savedStateHandle["type"])
    private val _focusedIndex = MutableStateFlow(0)
    private val _pinnedCategories = MutableStateFlow<Set<String>>(emptySet())
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
        loadPinnedCategories()
    }

    private fun loadPinnedCategories() {
        viewModelScope.launch {
            val pinned = withContext(Dispatchers.IO) {
                val categories = when (type) {
                    "genres" -> getVirtualCollectionCategoriesUseCase.getGenres()
                    "modes" -> getVirtualCollectionCategoriesUseCase.getGameModes()
                    else -> flowOf(emptyList())
                }.stateIn(viewModelScope).value
                categories.filter { isPinnedUseCase.isVirtualPinned(categoryType, it.name) }.map { it.name }.toSet()
            }
            _pinnedCategories.value = pinned
        }
    }

    val uiState: StateFlow<VirtualBrowserUiState> = combine(
        when (type) {
            "genres" -> getVirtualCollectionCategoriesUseCase.getGenres()
            "modes" -> getVirtualCollectionCategoriesUseCase.getGameModes()
            else -> flowOf(emptyList())
        },
        _focusedIndex,
        _pinnedCategories,
        _isRefreshing
    ) { categories, focusedIndex, pinnedCategories, isRefreshing ->
        VirtualBrowserUiState(
            type = type,
            title = when (type) {
                "genres" -> "Genres"
                "modes" -> "Game Modes"
                else -> "Browse"
            },
            categories = categories,
            isLoading = false,
            isRefreshing = isRefreshing,
            focusedIndex = focusedIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0)),
            pinnedCategories = pinnedCategories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VirtualBrowserUiState(type = type, title = if (type == "genres") "Genres" else "Game Modes")
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
        if (currentIndex < state.categories.size - 1) {
            _focusedIndex.value = currentIndex + 1
        }
    }

    fun togglePinFocused() {
        val category = uiState.value.focusedCategory ?: return
        viewModelScope.launch {
            val isPinned = category.name in _pinnedCategories.value
            if (isPinned) {
                unpinCollectionUseCase.unpinVirtual(categoryType, category.name)
                _pinnedCategories.value = _pinnedCategories.value - category.name
            } else {
                pinCollectionUseCase.pinVirtual(categoryType, category.name)
                _pinnedCategories.value = _pinnedCategories.value + category.name
            }
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
        onCategoryClick: (String) -> Unit
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
            uiState.value.focusedCategory?.let { onCategoryClick(it.name) }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            togglePinFocused()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            refresh()
            return InputResult.HANDLED
        }
    }
}
