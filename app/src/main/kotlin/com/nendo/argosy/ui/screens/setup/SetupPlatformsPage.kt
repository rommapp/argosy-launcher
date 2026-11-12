package com.nendo.argosy.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.PlatformFilterHeader
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.primitives.RowButton
import com.nendo.argosy.ui.theme.Dimens

@Composable
internal fun PlatformsPage(state: SetupUiState, viewModel: SetupViewModel) {
    val list = state.platforms
    val focus = focusedTarget(state)
    SetupPageBody(state = state, viewModel = viewModel) {
        PlatformFilterHeader(
            platformCount = list.shown.size,
            filterMode = list.filterMode,
            searchQuery = list.searchQuery,
            headerFocused = focus == SetupTarget.PlatformHeader,
            headerIndex = list.headerIndex,
            searchActive = list.searchActive,
            sortMenuOpen = list.sortMenuOpen,
            sortMenuIndex = list.sortMenuIndex,
            onSearchQueryChange = viewModel::setPlatformSearch,
            onSortModeChange = viewModel::setPlatformSort,
            onFilterModeChange = viewModel::cyclePlatformFilter,
            onOpenSearch = viewModel::openPlatformSearch,
            onCloseSearch = viewModel::closePlatformSearch,
            onOpenSortMenu = viewModel::openPlatformSort,
            onCloseSortMenu = viewModel::closePlatformSort,
            modifier = Modifier.padding(bottom = Dimens.spacingSm),
        )
        val listState = rememberLazyListState()
        val focusedIndex = when (focus) {
            SetupTarget.SelectAllPlatforms -> 0
            is SetupTarget.Platform -> list.shown.indexOfFirst { it.id == focus.id }.let { if (it < 0) -1 else it + 1 }
            SetupTarget.CoreDownloads -> list.shown.size + 1
            else -> -1
        }
        FocusedScroll(listState = listState, focusedIndex = focusedIndex)
        val allEnabled = list.all.isNotEmpty() && list.all.all { it.syncEnabled }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.listGap),
        ) {
            item(key = "select-all") {
                RowButton(
                    label = stringResource(
                        if (allEnabled) {
                            R.string.firstrun_platform_select_button_deselect_all
                        } else {
                            R.string.firstrun_platform_select_button_select_all
                        }
                    ),
                    focused = focus == SetupTarget.SelectAllPlatforms,
                    onClick = { viewModel.tap(SetupTarget.SelectAllPlatforms) },
                )
            }
            items(list.shown, key = { it.id }) { platform ->
                SwitchPreference(
                    title = platform.name,
                    subtitle = pluralStringResource(
                        R.plurals.firstrun_platform_select_game_count,
                        platform.gameCount,
                        platform.gameCount
                    ),
                    isEnabled = platform.syncEnabled,
                    isFocused = focus == SetupTarget.Platform(platform.id),
                    onToggle = { viewModel.tap(SetupTarget.Platform(platform.id)) }
                )
            }
            if (list.missingCoreIds.isNotEmpty()) {
                item(key = "core-downloads") {
                    SwitchPreference(
                        title = stringResource(R.string.setup_platforms_cores_title),
                        subtitle = pluralStringResource(
                            R.plurals.setup_platforms_cores_subtitle,
                            list.missingCoreIds.size,
                            list.missingCoreIds.size
                        ),
                        isEnabled = list.downloadCores,
                        isFocused = focus == SetupTarget.CoreDownloads,
                        onToggle = { viewModel.tap(SetupTarget.CoreDownloads) }
                    )
                }
            }
        }
    }
}
