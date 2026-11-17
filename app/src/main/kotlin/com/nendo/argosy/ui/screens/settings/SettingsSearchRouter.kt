package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.ui.common.DisplayText
import com.nendo.argosy.ui.common.resolve
import com.nendo.argosy.ui.screens.settings.search.SettingsSearchEntry
import com.nendo.argosy.ui.screens.settings.search.SettingsSearchResult
import com.nendo.argosy.ui.screens.settings.search.SettingsSearchSource
import com.nendo.argosy.ui.screens.settings.search.SettingsSearchTarget
import com.nendo.argosy.ui.screens.settings.search.buildSettingsSearchIndex
import com.nendo.argosy.ui.screens.settings.search.settingsSearchSource
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val SETTINGS_SEARCH_FIELD_INDEX = 0

internal fun matchSettingsSearch(
    entries: List<SettingsSearchEntry>,
    query: String,
    resolve: (DisplayText) -> String
): List<SettingsSearchResult> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return entries
        .distinctBy { it.target.id }
        .mapNotNull { entry ->
            val labelIndex = resolve(entry.label).indexOf(needle, ignoreCase = true)
            when {
                labelIndex >= 0 -> SettingsSearchResult(entry, labelIndex, needle.length)
                entry.keywords?.let { resolve(it).contains(needle, ignoreCase = true) } == true ->
                    SettingsSearchResult(entry, -1, 0)
                else -> null
            }
        }
        .sortedBy { result ->
            when {
                result.matchStart == 0 -> 0
                result.matchStart > 0 -> 1
                else -> 2
            }
        }
}

private fun searchResultsFor(vm: SettingsViewModel, state: SettingsUiState, query: String): List<SettingsSearchResult> =
    matchSettingsSearch(buildSettingsSearchIndex(state), query) { it.resolve(vm.context) }

internal fun routeOpenSettingsSearch(vm: SettingsViewModel) {
    vm._uiState.update {
        it.copy(
            focusedIndex = SETTINGS_SEARCH_FIELD_INDEX,
            settingsSearch = it.settingsSearch.copy(keyboardOpen = true)
        )
    }
    vm.viewModelScope.launch {
        val coreOptions = loadDownloadedCoreOptionsForSearch(vm)
        vm._uiState.update { state ->
            val withCores = state.copy(settingsSearch = state.settingsSearch.copy(coreOptions = coreOptions))
            withCores.copy(
                settingsSearch = withCores.settingsSearch.copy(
                    results = searchResultsFor(vm, withCores, withCores.settingsSearch.query)
                )
            )
        }
    }
}

internal fun routeUpdateSettingsSearchQuery(vm: SettingsViewModel, query: String) {
    vm._uiState.update { state ->
        state.copy(
            focusedIndex = SETTINGS_SEARCH_FIELD_INDEX,
            settingsSearch = state.settingsSearch.copy(
                query = query,
                results = searchResultsFor(vm, state, query)
            )
        )
    }
}

internal fun routeCloseSettingsSearchKeyboard(vm: SettingsViewModel) {
    vm._uiState.update { state ->
        val search = state.settingsSearch
        state.copy(
            focusedIndex = if (search.results.isNotEmpty()) SETTINGS_SEARCH_FIELD_INDEX + 1 else state.focusedIndex,
            settingsSearch = search.copy(keyboardOpen = false)
        )
    }
}

internal fun routeClearSettingsSearch(vm: SettingsViewModel) {
    vm._uiState.update {
        it.copy(
            focusedIndex = SETTINGS_SEARCH_FIELD_INDEX,
            settingsSearch = it.settingsSearch.copy(query = "", keyboardOpen = false, results = emptyList())
        )
    }
}

internal fun routeSettingsSearchConfirm(vm: SettingsViewModel, focusedIndex: Int) {
    if (focusedIndex == SETTINGS_SEARCH_FIELD_INDEX) {
        routeOpenSettingsSearch(vm)
    } else {
        routeOpenSettingsSearchResult(vm, focusedIndex - 1)
    }
}

internal fun routeOpenSettingsSearchResult(vm: SettingsViewModel, resultIndex: Int) {
    val result = vm._uiState.value.settingsSearch.results.getOrNull(resultIndex) ?: return
    vm.setFocusIndex(resultIndex + 1)
    when (val target = result.entry.target) {
        is SettingsSearchTarget.Row -> openRow(vm, target)
        is SettingsSearchTarget.Platform -> openPlatform(vm, target.platformId, target.rowKey)
        is SettingsSearchTarget.CoreOption -> routeOpenCoreOption(vm, target.coreId, target.optionKey)
    }
}

private fun openRow(vm: SettingsViewModel, target: SettingsSearchTarget.Row) {
    val source = settingsSearchSource(target.section) as? SettingsSearchSource.Rows<*, *> ?: return
    source.open(vm)
    val focus = source.focusIndexOf(target.key, vm._uiState.value)
    if (focus >= 0 && vm._uiState.value.currentSection == target.section) vm.setFocusIndex(focus)
}

private fun openPlatform(vm: SettingsViewModel, platformId: Long, rowKey: String?) {
    val index = vm._uiState.value.emulators.platforms.indexOfFirst { it.platform.id == platformId }
    if (index < 0) return
    vm.navigateToSection(SettingsSection.PLATFORMS)
    vm.setFocusIndex(vm.focusIndexForPlatform(platformId))
    vm.navigateToPlatformDetail(index, rowKey)
}
