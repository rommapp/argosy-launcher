package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion

@Composable
fun SyncFiltersModal(
    syncFilters: SyncFilterPreferences,
    focusIndex: Int,
    showRegionPicker: Boolean,
    regionPickerFocusIndex: Int,
    regionPickerRegions: List<String>,
    regionPickerHeldRegion: String?,
    onToggleRegion: (String) -> Unit,
    onLiftRegion: (String) -> Unit,
    onMoveRegionTo: (String, Int) -> Unit,
    onDropRegion: () -> Unit,
    onToggleRegionMode: () -> Unit,
    onToggleExcludeBeta: (Boolean) -> Unit,
    onToggleExcludePrototype: (Boolean) -> Unit,
    onToggleExcludeDemo: (Boolean) -> Unit,
    onToggleExcludeHack: (Boolean) -> Unit,
    onToggleExcludeUnofficial: (Boolean) -> Unit,
    onToggleDeleteOrphans: (Boolean) -> Unit,
    onShowRegionPicker: () -> Unit,
    onDismissRegionPicker: () -> Unit,
    onDismiss: () -> Unit,
    regionModePickerToken: Int = 0
) {
    val listState = rememberLazyListState()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val modalBlur by animateDpAsState(
        targetValue = if (showRegionPicker) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "regionPickerBlur"
    )

    FocusedScroll(
        listState = listState,
        focusedIndex = focusIndex
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthXl)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg)
                .blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.settings_sync_filters_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_sync_filters_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                item {
                    val enabledRegions = syncFilters.enabledRegions
                    val regionsText = if (enabledRegions.isEmpty()) {
                        stringResource(R.string.settings_sync_filters_regions_none)
                    } else {
                        enabledRegions.joinToString(", ")
                    }
                    ActionPreference(
                        title = stringResource(R.string.settings_sync_filters_regions_title),
                        subtitle = regionsText,
                        isFocused = focusIndex == 0,
                        onClick = onShowRegionPicker
                    )
                }
                item {
                    val modeText = regionModeLabel(syncFilters.regionMode)
                    CyclePreference(
                        title = stringResource(R.string.settings_sync_filters_region_mode_title),
                        value = modeText,
                        isFocused = focusIndex == 1,
                        onClick = onToggleRegionMode,
                        onPrev = onToggleRegionMode,
                        options = RegionFilterMode.entries.map { regionModeLabel(it) },
                        onSelect = { if (RegionFilterMode.entries[it] != syncFilters.regionMode) onToggleRegionMode() },
                        pickerRequestToken = regionModePickerToken
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.settings_sync_filters_exclude_beta_title),
                        isEnabled = syncFilters.excludeBeta,
                        isFocused = focusIndex == 2,
                        onToggle = onToggleExcludeBeta
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.settings_sync_filters_exclude_prototype_title),
                        isEnabled = syncFilters.excludePrototype,
                        isFocused = focusIndex == 3,
                        onToggle = onToggleExcludePrototype
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.settings_sync_filters_exclude_demo_title),
                        isEnabled = syncFilters.excludeDemo,
                        isFocused = focusIndex == 4,
                        onToggle = onToggleExcludeDemo
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.settings_sync_filters_exclude_hack_title),
                        isEnabled = syncFilters.excludeHack,
                        isFocused = focusIndex == 5,
                        onToggle = onToggleExcludeHack
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.settings_sync_filters_exclude_unofficial_title),
                        subtitle = stringResource(R.string.settings_sync_filters_exclude_unofficial_subtitle),
                        isEnabled = syncFilters.excludeUnofficial,
                        isFocused = focusIndex == 6,
                        onToggle = onToggleExcludeUnofficial
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.settings_sync_filters_remove_orphans_title),
                        subtitle = stringResource(R.string.settings_sync_filters_remove_orphans_subtitle),
                        isEnabled = syncFilters.deleteOrphans,
                        isFocused = focusIndex == 7,
                        onToggle = onToggleDeleteOrphans
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterHints(
                hints = listOf(
                    InputButton.DPAD to stringResource(R.string.settings_sync_filters_hint_navigate),
                    InputButton.A to stringResource(R.string.settings_sync_filters_hint_toggle),
                    InputButton.B to stringResource(R.string.settings_sync_filters_hint_close)
                ),
                onHintClick = { button ->
                    when (button) {
                        InputButton.B -> onDismiss()
                        else -> Unit
                    }
                }
            )
        }

        if (showRegionPicker) {
            RegionPickerPopup(
                regions = regionPickerRegions,
                enabledRegions = syncFilters.enabledRegions,
                focusIndex = regionPickerFocusIndex,
                heldRegion = regionPickerHeldRegion,
                orderingEnabled = syncFilters.regionMode == RegionFilterMode.INCLUDE,
                onToggle = onToggleRegion,
                onLift = onLiftRegion,
                onMoveTo = onMoveRegionTo,
                onDrop = onDropRegion,
                onDismiss = onDismissRegionPicker
            )
        }
    }
}

@Composable
private fun regionModeLabel(mode: RegionFilterMode): String = when (mode) {
    RegionFilterMode.INCLUDE -> stringResource(R.string.settings_sync_filters_region_mode_include)
    RegionFilterMode.EXCLUDE -> stringResource(R.string.settings_sync_filters_region_mode_exclude)
}
