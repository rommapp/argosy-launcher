package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.ui.screens.settings.StorageAttributionState
import com.nendo.argosy.ui.screens.settings.StorageGamesSortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class StorageAttributionDelegate @Inject constructor(
    private val attributionRepository: StorageAttributionRepository
) {
    private val _state = MutableStateFlow(StorageAttributionState())
    val state: StateFlow<StorageAttributionState> = _state.asStateFlow()

    fun initFlowCollection(scope: CoroutineScope) {
        attributionRepository.snapshot.onEach { snapshot ->
            _state.update { it.copy(snapshot = snapshot) }
        }.launchIn(scope)

        attributionRepository.volumes.onEach { volumes ->
            _state.update { it.copy(volumes = volumes) }
        }.launchIn(scope)

        attributionRepository.walkProgress.onEach { progress ->
            _state.update { it.copy(walkProgress = progress) }
        }.launchIn(scope)

        attributionRepository.isRefreshing.onEach { refreshing ->
            _state.update { it.copy(isRefreshing = refreshing) }
        }.launchIn(scope)
    }

    fun refresh(force: Boolean = false, deep: Boolean = false) {
        attributionRepository.refresh(force, deep)
    }

    fun refreshOnOpen() {
        attributionRepository.refreshOnOpen()
    }

    fun markDirty(category: StorageCategory) = attributionRepository.markDirty(category)

    fun setGamesSortMode(mode: StorageGamesSortMode) {
        _state.update { it.copy(gamesSortMode = mode) }
    }

    fun setMusicEnteredFromStorage(entered: Boolean) {
        _state.update { it.copy(musicEnteredFromStorage = entered) }
    }

    fun setCachesEntryFocus(focus: Int) {
        _state.update { it.copy(cachesEntryFocus = focus) }
    }

    /** Latched at STORAGE entry so the Steam tile cannot insert mid-visit and shift focus indices. */
    fun latchSteamTileVisible(visible: Boolean) {
        _state.update { it.copy(steamTileLatched = visible) }
    }
}
