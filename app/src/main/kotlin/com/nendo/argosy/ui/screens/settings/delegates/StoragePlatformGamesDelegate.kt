package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.download.FilePickerFlowUseCase
import com.nendo.argosy.domain.usecase.game.DeleteGameUseCase
import com.nendo.argosy.domain.usecase.storage.GameStorageBreakdownUseCase
import com.nendo.argosy.domain.usecase.storage.GameStorageBucket
import com.nendo.argosy.ui.screens.settings.GameStorageCategoryDeleteConfirm
import com.nendo.argosy.ui.screens.settings.GameStorageDeleteConfirm
import com.nendo.argosy.ui.screens.settings.StoragePlatformGamesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class StoragePlatformGamesDelegate @Inject constructor(
    private val breakdownUseCase: GameStorageBreakdownUseCase,
    private val deleteGameUseCase: DeleteGameUseCase,
    private val filePickerFlowUseCase: FilePickerFlowUseCase,
    private val gameRepository: GameRepository
) {
    private val _state = MutableStateFlow(StoragePlatformGamesState())
    val state: StateFlow<StoragePlatformGamesState> = _state.asStateFlow()

    fun open(platformId: Long, platformName: String, scope: CoroutineScope) {
        _state.update {
            StoragePlatformGamesState(
                selectedPlatformId = platformId,
                platformName = platformName,
                isLoading = true
            )
        }
        reload(scope)
    }

    fun reload(scope: CoroutineScope, onPlatformEmpty: (() -> Unit)? = null) {
        val platformId = _state.value.selectedPlatformId
        if (platformId < 0) return
        scope.launch {
            val games = breakdownUseCase.loadPlatform(platformId)
            val covers = gameRepository.getByIds(games.map { it.gameId }).associate { it.id to it.coverPath }
            _state.update {
                it.copy(
                    isLoading = false,
                    games = games,
                    coverPaths = covers
                )
            }
            if (games.isEmpty()) onPlatformEmpty?.invoke()
        }
    }

    fun setHighlightedCategory(index: Int) = _state.update { it.copy(highlightedCategoryIndex = index) }

    fun requestDeleteConfirm(gameId: Long) {
        val game = _state.value.games.find { it.gameId == gameId } ?: return
        _state.update {
            it.copy(
                deleteConfirm = GameStorageDeleteConfirm(
                    gameId = gameId,
                    title = game.title,
                    hasSoundtrack = game.hasSoundtrack,
                    unsyncedSaves = game.unsyncedSaves
                )
            )
        }
    }

    fun dismissDeleteConfirm() = _state.update { it.copy(deleteConfirm = null) }

    fun requestCategoryDeleteConfirm(gameId: Long, bucket: GameStorageBucket) {
        if (bucket == GameStorageBucket.BASE) return
        val game = _state.value.games.find { it.gameId == gameId } ?: return
        val row = game.buckets.firstOrNull { it.bucket == bucket } ?: return
        if (row.rommFileIds.isEmpty()) return
        _state.update {
            it.copy(
                categoryDeleteConfirm = GameStorageCategoryDeleteConfirm(
                    gameId = gameId,
                    bucket = bucket,
                    fileCount = row.fileCount,
                    totalBytes = row.totalBytes
                )
            )
        }
    }

    fun dismissCategoryDeleteConfirm() = _state.update { it.copy(categoryDeleteConfirm = null) }

    fun confirmDeleteGame(gameId: Long, withSoundtrack: Boolean, scope: CoroutineScope, onPlatformEmpty: () -> Unit) {
        _state.update { it.copy(deleteConfirm = null) }
        scope.launch {
            deleteGameUseCase(gameId)
            if (withSoundtrack) filePickerFlowUseCase.purgeSoundtrack(gameId)
            reload(scope, onPlatformEmpty)
        }
    }

    fun deleteCategory(gameId: Long, bucket: GameStorageBucket, scope: CoroutineScope, onPlatformEmpty: () -> Unit) {
        if (bucket == GameStorageBucket.BASE) return
        val current = _state.value
        val breakdown = current.games.find { it.gameId == gameId } ?: return
        val targetIds = breakdown.buckets.firstOrNull { it.bucket == bucket }?.rommFileIds?.toSet().orEmpty()
        _state.update { it.copy(categoryDeleteConfirm = null) }
        if (targetIds.isEmpty()) return
        scope.launch {
            val setup = filePickerFlowUseCase.buildManageRows(gameId)
            if (setup != null) {
                val keep = setup.preselectedFileIds - targetIds
                filePickerFlowUseCase.applyManagedSelection(gameId, setup.rows, keep)
            }
            reload(scope, onPlatformEmpty)
        }
    }
}
