package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.util.PlatformFilterLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun SyncSettingsDelegate.setPlatformFilterSortMode(scope: CoroutineScope, mode: PlatformFilterLogic.SortMode) {
    scope.launch {
        val currentState = state.value
        val filtered = PlatformFilterLogic.filterAndSort(
            items = currentState.platformFiltersAllPlatforms,
            searchQuery = currentState.platformFilterSearchQuery,
            hasGames = currentState.platformFilterHasGames,
            sortMode = mode,
            nameSelector = { it.name },
            countSelector = { it.romCount }
        )

        updateState(currentState.copy(
            platformFilterSortMode = mode,
            platformFiltersList = filtered,
            platformFiltersModalFocusIndex = 0
        ))
    }
}

fun SyncSettingsDelegate.setPlatformFilterHasGames(scope: CoroutineScope, enabled: Boolean) {
    scope.launch {
        val currentState = state.value
        val filtered = PlatformFilterLogic.filterAndSort(
            items = currentState.platformFiltersAllPlatforms,
            searchQuery = currentState.platformFilterSearchQuery,
            hasGames = enabled,
            sortMode = currentState.platformFilterSortMode,
            nameSelector = { it.name },
            countSelector = { it.romCount }
        )

        updateState(currentState.copy(
            platformFilterHasGames = enabled,
            platformFiltersList = filtered,
            platformFiltersModalFocusIndex = 0
        ))
    }
}

fun SyncSettingsDelegate.setPlatformFilterSearchQuery(scope: CoroutineScope, query: String) {
    scope.launch {
        val currentState = state.value
        val filtered = PlatformFilterLogic.filterAndSort(
            items = currentState.platformFiltersAllPlatforms,
            searchQuery = query,
            hasGames = currentState.platformFilterHasGames,
            sortMode = currentState.platformFilterSortMode,
            nameSelector = { it.name },
            countSelector = { it.romCount }
        )

        updateState(currentState.copy(
            platformFilterSearchQuery = query,
            platformFiltersList = filtered,
            platformFiltersModalFocusIndex = 0
        ))
    }
}
