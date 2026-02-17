package com.nendo.argosy.ui.screens.home.delegates

import androidx.lifecycle.SavedStateHandle
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.home.HomePlatformUi
import com.nendo.argosy.ui.screens.home.HomeRow
import com.nendo.argosy.ui.screens.home.HomeRowItem
import com.nendo.argosy.ui.screens.home.HomeUiState
import com.nendo.argosy.ui.util.ImagePrefetchManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_ROW_TYPE = "home_row_type"
private const val KEY_PLATFORM_INDEX = "home_platform_index"
private const val KEY_GAME_INDEX = "home_game_index"
private const val KEY_PINNED_COLLECTION_ID = "home_pinned_collection_id"
private const val KEY_PINNED_TYPE = "home_pinned_type"
private const val KEY_PINNED_NAME = "home_pinned_name"

private const val ROW_TYPE_FAVORITES = "favorites"
private const val ROW_TYPE_PLATFORM = "platform"
private const val ROW_TYPE_CONTINUE = "continue"
private const val ROW_TYPE_RECOMMENDATIONS = "recommendations"
private const val ROW_TYPE_STEAM = "steam"
private const val ROW_TYPE_ANDROID = "android"
private const val ROW_TYPE_PINNED_REGULAR = "pinned_regular"
private const val ROW_TYPE_PINNED_VIRTUAL = "pinned_virtual"

class HomeNavigationDelegate @Inject constructor(
    private val soundManager: SoundFeedbackManager,
    private val imagePrefetchManager: ImagePrefetchManager
) {
    private val rowGameIndexes = mutableMapOf<HomeRow, Int>()
    private var rowLoadJob: Job? = null
    private var backgroundPrefetchJob: Job? = null
    private val loadViewDebounceMs = 150L

    fun restoreInitialRow(savedStateHandle: SavedStateHandle): Pair<HomeRow, Int> {
        val rowType = savedStateHandle.get<String>(KEY_ROW_TYPE)
        val platformIndex = savedStateHandle.get<Int>(KEY_PLATFORM_INDEX) ?: 0
        val gameIndex = savedStateHandle.get<Int>(KEY_GAME_INDEX) ?: 0

        val currentRow = when (rowType) {
            ROW_TYPE_FAVORITES -> HomeRow.Favorites
            ROW_TYPE_PLATFORM -> HomeRow.Platform(platformIndex)
            ROW_TYPE_CONTINUE -> HomeRow.Continue
            ROW_TYPE_RECOMMENDATIONS -> HomeRow.Recommendations
            ROW_TYPE_ANDROID -> HomeRow.Android
            ROW_TYPE_STEAM -> HomeRow.Steam
            ROW_TYPE_PINNED_REGULAR, ROW_TYPE_PINNED_VIRTUAL -> HomeRow.Continue
            else -> HomeRow.Continue
        }

        return currentRow to gameIndex
    }

    fun saveCurrentState(savedStateHandle: SavedStateHandle, currentRow: HomeRow, focusedGameIndex: Int) {
        val (rowType, platformIndex) = when (val row = currentRow) {
            HomeRow.Favorites -> ROW_TYPE_FAVORITES to 0
            is HomeRow.Platform -> ROW_TYPE_PLATFORM to row.index
            HomeRow.Continue -> ROW_TYPE_CONTINUE to 0
            HomeRow.Recommendations -> ROW_TYPE_RECOMMENDATIONS to 0
            HomeRow.Android -> ROW_TYPE_ANDROID to 0
            HomeRow.Steam -> ROW_TYPE_STEAM to 0
            is HomeRow.PinnedRegular -> {
                savedStateHandle[KEY_PINNED_COLLECTION_ID] = row.collectionId
                ROW_TYPE_PINNED_REGULAR to 0
            }
            is HomeRow.PinnedVirtual -> {
                savedStateHandle[KEY_PINNED_TYPE] = row.type.name
                savedStateHandle[KEY_PINNED_NAME] = row.name
                ROW_TYPE_PINNED_VIRTUAL to 0
            }
        }
        savedStateHandle[KEY_ROW_TYPE] = rowType
        savedStateHandle[KEY_PLATFORM_INDEX] = platformIndex
        savedStateHandle[KEY_GAME_INDEX] = focusedGameIndex
    }

    fun nextRow(state: HomeUiState): Pair<HomeRow, Int>? {
        val rows = state.availableRows
        if (rows.isEmpty()) return null

        rowGameIndexes[state.currentRow] = state.focusedGameIndex

        val currentIdx = rows.indexOf(state.currentRow)
        val nextIdx = if (currentIdx >= rows.lastIndex) 0 else currentIdx + 1
        val nextRow = rows[nextIdx]
        val savedIndex = rowGameIndexes[nextRow] ?: 0
        return nextRow to savedIndex
    }

    fun previousRow(state: HomeUiState): Pair<HomeRow, Int>? {
        val rows = state.availableRows
        if (rows.isEmpty()) return null

        rowGameIndexes[state.currentRow] = state.focusedGameIndex

        val currentIdx = rows.indexOf(state.currentRow)
        val prevIdx = if (currentIdx <= 0) rows.lastIndex else currentIdx - 1
        val prevRow = rows[prevIdx]
        val savedIndex = rowGameIndexes[prevRow] ?: 0
        return prevRow to savedIndex
    }

    fun loadRowWithDebounce(scope: CoroutineScope, row: HomeRow, onLoadRow: suspend (HomeRow) -> Unit) {
        rowLoadJob?.cancel()
        rowLoadJob = scope.launch {
            delay(loadViewDebounceMs)
            onLoadRow(row)
        }
    }

    fun setFocusIndex(currentState: HomeUiState, index: Int): Boolean {
        if (index < 0 || index >= currentState.currentItems.size) return false
        if (index == currentState.focusedGameIndex) return false
        soundManager.play(SoundType.NAVIGATE)
        return true
    }

    fun prefetchAdjacentBackgrounds(scope: CoroutineScope, currentItems: List<HomeRowItem>, focusedIndex: Int) {
        backgroundPrefetchJob?.cancel()
        backgroundPrefetchJob = scope.launch(Dispatchers.IO) {
            delay(200)
            val games = currentItems.filterIsInstance<HomeRowItem.Game>()
            val paths = listOf(focusedIndex - 1, focusedIndex + 1, focusedIndex + 2)
                .filter { it in games.indices }
                .mapNotNull { games[it].game.backgroundPath }
            imagePrefetchManager.prefetchBackgrounds(paths)
        }
    }

    fun scrollToFirstItem(currentFocusedIndex: Int): Boolean {
        return currentFocusedIndex != 0
    }

    fun navigateToContinuePlaying(state: HomeUiState): Boolean {
        if (state.currentRow == HomeRow.Continue) return false
        if (state.recentGames.isEmpty()) return false
        rowGameIndexes[state.currentRow] = state.focusedGameIndex
        return true
    }

    fun clearRowIndexes() {
        rowGameIndexes.clear()
    }

    fun saveRowIndex(row: HomeRow, index: Int) {
        rowGameIndexes[row] = index
    }

    fun reconcilePlatformChange(
        state: HomeUiState,
        currentPlatforms: List<HomePlatformUi>,
        newPlatforms: List<HomePlatformUi>
    ): PlatformChangeResult {
        if (currentPlatforms.isEmpty() && newPlatforms.isNotEmpty()) {
            val newRow = when {
                state.recentGames.isNotEmpty() -> state.currentRow
                state.favoriteGames.isNotEmpty() -> state.currentRow
                else -> HomeRow.Platform(0)
            }
            return PlatformChangeResult.Initial(newRow, newPlatforms)
        }

        val currentIds = currentPlatforms.map { it.id }.toSet()
        val newIds = newPlatforms.map { it.id }.toSet()

        if (currentIds == newIds) {
            return PlatformChangeResult.DisplayOnly(newPlatforms)
        }

        clearRowIndexes()
        val row = state.currentRow
        val newRow = if (row is HomeRow.Platform) {
            val currentPlatformId = currentPlatforms.getOrNull(row.index)?.id
            val newIndex = currentPlatformId?.let { id ->
                newPlatforms.indexOfFirst { it.id == id }
            }?.takeIf { it >= 0 }
            when {
                newIndex != null -> HomeRow.Platform(newIndex)
                newPlatforms.isNotEmpty() -> HomeRow.Platform(0)
                else -> state.availableRows.firstOrNull() ?: HomeRow.Continue
            }
        } else {
            row
        }
        return PlatformChangeResult.StructuralChange(newRow, newPlatforms)
    }
}

sealed class PlatformChangeResult {
    data class Initial(val row: HomeRow, val platforms: List<HomePlatformUi>) : PlatformChangeResult()
    data class DisplayOnly(val platforms: List<HomePlatformUi>) : PlatformChangeResult()
    data class StructuralChange(val row: HomeRow, val platforms: List<HomePlatformUi>) : PlatformChangeResult()
}
