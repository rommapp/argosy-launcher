package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.domain.model.PinnedCollection
import com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.ReorderPinnedCollectionsUseCase
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
import javax.inject.Inject

data class ManagePinsUiState(
    val pins: List<PinnedCollection> = emptyList(),
    val focusedIndex: Int = 0,
    val isReorderMode: Boolean = false,
    val reorderingIndex: Int? = null,
    val isLoading: Boolean = true
) {
    val focusedPin: PinnedCollection?
        get() = pins.getOrNull(focusedIndex)
}

@HiltViewModel
class ManagePinsViewModel @Inject constructor(
    private val getPinnedCollectionsUseCase: GetPinnedCollectionsUseCase,
    private val reorderPinnedCollectionsUseCase: ReorderPinnedCollectionsUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase
) : ViewModel() {

    private val _localState = MutableStateFlow(LocalState())

    private data class LocalState(
        val focusedIndex: Int = 0,
        val isReorderMode: Boolean = false,
        val reorderingIndex: Int? = null,
        val localPins: List<PinnedCollection>? = null
    )

    val uiState: StateFlow<ManagePinsUiState> = combine(
        getPinnedCollectionsUseCase(),
        _localState
    ) { dbPins, localState ->
        val pins = localState.localPins ?: dbPins
        ManagePinsUiState(
            pins = pins,
            focusedIndex = localState.focusedIndex.coerceIn(0, (pins.size - 1).coerceAtLeast(0)),
            isReorderMode = localState.isReorderMode,
            reorderingIndex = localState.reorderingIndex,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManagePinsUiState()
    )

    fun moveFocus(delta: Int) {
        val current = _localState.value
        val pins = uiState.value.pins
        if (pins.isEmpty()) return
        val newIndex = (current.focusedIndex + delta).coerceIn(0, pins.size - 1)
        _localState.value = current.copy(focusedIndex = newIndex)
    }

    fun setFocusIndex(index: Int) {
        val pins = uiState.value.pins
        if (index < 0 || index >= pins.size) return
        _localState.value = _localState.value.copy(focusedIndex = index)
    }

    fun toggleReorderMode() {
        val current = _localState.value
        if (current.isReorderMode) {
            confirmReorder()
        } else {
            val pins = uiState.value.pins
            _localState.value = current.copy(
                isReorderMode = true,
                reorderingIndex = current.focusedIndex,
                localPins = pins
            )
        }
    }

    fun moveItem(delta: Int) {
        val current = _localState.value
        if (!current.isReorderMode) return
        val pins = current.localPins ?: return
        val fromIndex = current.reorderingIndex ?: return
        val toIndex = (fromIndex + delta).coerceIn(0, pins.size - 1)
        if (fromIndex == toIndex) return

        val mutablePins = pins.toMutableList()
        val item = mutablePins.removeAt(fromIndex)
        mutablePins.add(toIndex, item)

        _localState.value = current.copy(
            localPins = mutablePins,
            reorderingIndex = toIndex,
            focusedIndex = toIndex
        )
    }

    fun confirmReorder() {
        val current = _localState.value
        val pins = current.localPins ?: return
        viewModelScope.launch {
            reorderPinnedCollectionsUseCase(pins)
            _localState.value = current.copy(
                isReorderMode = false,
                reorderingIndex = null,
                localPins = null
            )
        }
    }

    fun cancelReorder() {
        _localState.value = _localState.value.copy(
            isReorderMode = false,
            reorderingIndex = null,
            localPins = null
        )
    }

    fun unpinFocused() {
        val pin = uiState.value.focusedPin ?: return
        viewModelScope.launch {
            when (pin) {
                is PinnedCollection.Regular -> unpinCollectionUseCase.unpinById(pin.id)
                is PinnedCollection.Virtual -> unpinCollectionUseCase.unpinById(pin.id)
            }
        }
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = uiState.value
            if (state.isReorderMode) {
                moveItem(-1)
            } else {
                moveFocus(-1)
            }
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            val state = uiState.value
            if (state.isReorderMode) {
                moveItem(1)
            } else {
                moveFocus(1)
            }
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            toggleReorderMode()
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = uiState.value
            if (state.isReorderMode) {
                cancelReorder()
            } else {
                onBack()
            }
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = uiState.value
            if (!state.isReorderMode) {
                unpinFocused()
            }
            return InputResult.HANDLED
        }
    }
}
