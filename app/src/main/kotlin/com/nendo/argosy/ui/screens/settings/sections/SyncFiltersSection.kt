package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.RegionPickerPopup
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun SyncFiltersSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()

    val modalBlur by animateDpAsState(
        targetValue = if (uiState.syncSettings.showRegionPicker) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "regionPickerBlur"
    )

    val maxIndex = 6

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..maxIndex) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset + paddingBuffer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            item {
                val enabledRegions = uiState.syncSettings.syncFilters.enabledRegions
                val regionsText = if (enabledRegions.isEmpty()) {
                    "None selected"
                } else {
                    enabledRegions.sorted().joinToString(", ")
                }
                ActionPreference(
                    title = "Regions",
                    subtitle = regionsText,
                    isFocused = uiState.focusedIndex == 0,
                    onClick = { viewModel.showRegionPicker() }
                )
            }
            item {
                val modeText = when (uiState.syncSettings.syncFilters.regionMode) {
                    RegionFilterMode.INCLUDE -> "Include selected"
                    RegionFilterMode.EXCLUDE -> "Exclude selected"
                }
                CyclePreference(
                    title = "Region Mode",
                    value = modeText,
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.toggleRegionMode() }
                )
            }
            item {
                SwitchPreference(
                    title = "Exclude Beta",
                    isEnabled = uiState.syncSettings.syncFilters.excludeBeta,
                    isFocused = uiState.focusedIndex == 2,
                    onToggle = { viewModel.setExcludeBeta(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Exclude Prototype",
                    isEnabled = uiState.syncSettings.syncFilters.excludePrototype,
                    isFocused = uiState.focusedIndex == 3,
                    onToggle = { viewModel.setExcludePrototype(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Exclude Demo",
                    isEnabled = uiState.syncSettings.syncFilters.excludeDemo,
                    isFocused = uiState.focusedIndex == 4,
                    onToggle = { viewModel.setExcludeDemo(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Exclude ROM Hacks",
                    isEnabled = uiState.syncSettings.syncFilters.excludeHack,
                    isFocused = uiState.focusedIndex == 5,
                    onToggle = { viewModel.setExcludeHack(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Remove Orphaned Entries",
                    subtitle = "Delete games no longer on server",
                    isEnabled = uiState.syncSettings.syncFilters.deleteOrphans,
                    isFocused = uiState.focusedIndex == 6,
                    onToggle = { viewModel.setDeleteOrphans(it) }
                )
            }
        }

        if (uiState.syncSettings.showRegionPicker) {
            RegionPickerPopup(
                enabledRegions = uiState.syncSettings.syncFilters.enabledRegions,
                focusIndex = uiState.syncSettings.regionPickerFocusIndex,
                onToggle = { viewModel.toggleRegion(it) },
                onDismiss = { viewModel.dismissRegionPicker() }
            )
        }
    }
}
