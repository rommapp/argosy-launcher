package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.domain.usecase.collection.CategoryWithCount
import com.nendo.argosy.domain.usecase.collection.GetVirtualCollectionCategoriesUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class VirtualBrowserUiState(
    val type: String = "",
    val title: String = "",
    val categories: List<CategoryWithCount> = emptyList(),
    val isLoading: Boolean = true,
    val focusedIndex: Int = 0
) {
    val focusedCategory: CategoryWithCount?
        get() = categories.getOrNull(focusedIndex)
}

@HiltViewModel
class VirtualBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVirtualCollectionCategoriesUseCase: GetVirtualCollectionCategoriesUseCase
) : ViewModel() {

    private val type: String = checkNotNull(savedStateHandle["type"])
    private val _focusedIndex = MutableStateFlow(0)

    val uiState: StateFlow<VirtualBrowserUiState> = combine(
        when (type) {
            "genres" -> getVirtualCollectionCategoriesUseCase.getGenres()
            "modes" -> getVirtualCollectionCategoriesUseCase.getGameModes()
            else -> flowOf(emptyList())
        },
        _focusedIndex
    ) { categories, focusedIndex ->
        VirtualBrowserUiState(
            type = type,
            title = when (type) {
                "genres" -> "Genres"
                "modes" -> "Game Modes"
                else -> "Browse"
            },
            categories = categories,
            isLoading = false,
            focusedIndex = focusedIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
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
    }
}
