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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val pinnedCategories: Set<String> = emptySet(),
    val sectionLabels: List<String> = emptyList(),
    val currentSectionLabel: String = "",
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val overlayLetter: String? = null
) {
    val focusedCategory: CategoryWithCount?
        get() = categories.getOrNull(focusedIndex)

    val isFocusedCategoryPinned: Boolean
        get() = focusedCategory?.let { it.name in pinnedCategories } ?: false

    val showSectionSidebar: Boolean
        get() = !isSearchActive && sectionLabels.size > 1
}

private data class SearchState(val active: Boolean = false, val query: String = "")

@HiltViewModel
class VirtualBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVirtualCollectionCategoriesUseCase: GetVirtualCollectionCategoriesUseCase,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase,
    private val positions: VirtualBrowsePositions
) : ViewModel() {

    private val type: String = checkNotNull(savedStateHandle["type"])
    private val _focusedIndex = MutableStateFlow(positions.get(type))
    private val _pinnedCategories = MutableStateFlow<Set<String>>(emptySet())
    private val _isRefreshing = MutableStateFlow(false)
    private val _search = MutableStateFlow(SearchState())
    private val _overlayLetter = MutableStateFlow<String?>(null)
    private var preSearchIndex = 0
    private var lastRefreshTime = 0L
    private var overlayJob: Job? = null

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 30_000L
    }

    private val categoryType = when (type) {
        "genres" -> CategoryType.GENRE
        "modes" -> CategoryType.GAME_MODE
        "series" -> CategoryType.SERIES
        else -> CategoryType.GENRE
    }

    init {
        loadPinnedCategories()
    }

    private fun loadPinnedCategories() {
        viewModelScope.launch {
            val pinned = withContext(Dispatchers.IO) {
                val categories = categoriesFlow().stateIn(viewModelScope).value
                categories.filter { isPinnedUseCase.isVirtualPinned(categoryType, it.name) }.map { it.name }.toSet()
            }
            _pinnedCategories.value = pinned
        }
    }

    private fun categoriesFlow() = when (type) {
        "genres" -> getVirtualCollectionCategoriesUseCase.getGenres()
        "modes" -> getVirtualCollectionCategoriesUseCase.getGameModes()
        "series" -> getVirtualCollectionCategoriesUseCase.getSeries()
        else -> flowOf(emptyList())
    }

    private val core = combine(
        categoriesFlow(),
        _focusedIndex,
        _pinnedCategories,
        _isRefreshing,
        _search
    ) { categories, focusedIndex, pinnedCategories, isRefreshing, search ->
        val sorted = categories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val visible = if (search.query.isBlank()) {
            sorted
        } else {
            val q = com.nendo.argosy.util.SearchNormalizer.normalize(search.query)
            sorted.filter { com.nendo.argosy.util.SearchNormalizer.normalize(it.name).contains(q) }
        }
        val clamped = focusedIndex.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
        VirtualBrowserUiState(
            type = type,
            title = titleFor(type),
            categories = visible,
            isLoading = false,
            isRefreshing = isRefreshing,
            focusedIndex = clamped,
            pinnedCategories = pinnedCategories,
            sectionLabels = if (search.active) emptyList() else visible.map { sectionLabelFor(it.name) }.distinct(),
            currentSectionLabel = visible.getOrNull(clamped)?.let { sectionLabelFor(it.name) } ?: "",
            isSearchActive = search.active,
            searchQuery = search.query
        )
    }

    val uiState: StateFlow<VirtualBrowserUiState> = combine(core, _overlayLetter) { state, overlay ->
        state.copy(overlayLetter = overlay)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VirtualBrowserUiState(type = type, title = titleFor(type))
    )

    private fun titleFor(type: String): String = when (type) {
        "genres" -> "Genres"
        "modes" -> "Game Modes"
        "series" -> "Series"
        else -> "Browse"
    }

    private fun sectionLabelFor(name: String): String {
        val first = name.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (first in 'A'..'Z') first.toString() else "#"
    }

    private fun setFocus(index: Int) {
        _focusedIndex.value = index
        if (!_search.value.active) positions.set(type, index)
    }

    fun moveUp() {
        val current = uiState.value.focusedIndex
        if (current > 0) setFocus(current - 1)
    }

    fun moveDown() {
        val current = uiState.value.focusedIndex
        if (current < uiState.value.categories.size - 1) setFocus(current + 1)
    }

    fun jumpToSection(label: String) {
        val state = uiState.value
        val target = state.categories.indexOfFirst { sectionLabelFor(it.name) == label }
        if (target < 0) return
        setFocus(target)
        showLetterOverlay(label)
    }

    fun jumpToNextSection() {
        val labels = uiState.value.sectionLabels
        if (labels.isEmpty()) return
        val current = labels.indexOf(uiState.value.currentSectionLabel)
        val next = if (current < 0 || current >= labels.lastIndex) 0 else current + 1
        jumpToSection(labels[next])
    }

    fun jumpToPreviousSection() {
        val labels = uiState.value.sectionLabels
        if (labels.isEmpty()) return
        val current = labels.indexOf(uiState.value.currentSectionLabel)
        val prev = if (current <= 0) labels.lastIndex else current - 1
        jumpToSection(labels[prev])
    }

    private fun showLetterOverlay(label: String) {
        overlayJob?.cancel()
        _overlayLetter.value = label
        overlayJob = viewModelScope.launch {
            delay(600)
            _overlayLetter.value = null
        }
    }

    fun openSearch() {
        if (_search.value.active) return
        preSearchIndex = uiState.value.focusedIndex
        _focusedIndex.value = 0
        _search.value = SearchState(active = true, query = "")
    }

    fun closeSearch() {
        if (!_search.value.active) return
        _search.value = SearchState(active = false, query = "")
        _focusedIndex.value = preSearchIndex
    }

    fun setSearchQuery(query: String) {
        _search.value = _search.value.copy(query = query)
        _focusedIndex.value = 0
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
            if (uiState.value.isSearchActive) {
                closeSearch()
                return InputResult.HANDLED
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            togglePinFocused()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            openSearch()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            refresh()
            return InputResult.HANDLED
        }

        override fun onPrevTrigger(): InputResult {
            if (uiState.value.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToPreviousSection()
            return InputResult.HANDLED
        }

        override fun onNextTrigger(): InputResult {
            if (uiState.value.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToNextSection()
            return InputResult.HANDLED
        }
    }
}
