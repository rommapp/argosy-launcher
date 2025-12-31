package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FooterBar
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
    onToggleRegion: (String) -> Unit,
    onToggleRegionMode: () -> Unit,
    onToggleExcludeBeta: (Boolean) -> Unit,
    onToggleExcludePrototype: (Boolean) -> Unit,
    onToggleExcludeDemo: (Boolean) -> Unit,
    onToggleExcludeHack: (Boolean) -> Unit,
    onToggleDeleteOrphans: (Boolean) -> Unit,
    onShowRegionPicker: () -> Unit,
    onDismissRegionPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val modalBlur by animateDpAsState(
        targetValue = if (showRegionPicker) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "regionPickerBlur"
    )

    LaunchedEffect(focusIndex) {
        val safeIndex = focusIndex.coerceAtLeast(0)
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0

        if (itemHeight == 0 || viewportHeight == 0) {
            listState.animateScrollToItem(safeIndex)
            return@LaunchedEffect
        }

        val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
        val centerOffset = (viewportHeight - itemHeight) / 2
        listState.animateScrollToItem(safeIndex, -centerOffset + paddingBuffer)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false, onClick = {})
                .padding(Dimens.spacingLg)
                .blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = "SYNC FILTERS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Configure which games to include during library sync",
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
                        "None selected"
                    } else {
                        enabledRegions.sorted().joinToString(", ")
                    }
                    ActionPreference(
                        title = "Regions",
                        subtitle = regionsText,
                        isFocused = focusIndex == 0,
                        onClick = onShowRegionPicker
                    )
                }
                item {
                    val modeText = when (syncFilters.regionMode) {
                        RegionFilterMode.INCLUDE -> "Include selected"
                        RegionFilterMode.EXCLUDE -> "Exclude selected"
                    }
                    CyclePreference(
                        title = "Region Mode",
                        value = modeText,
                        isFocused = focusIndex == 1,
                        onClick = onToggleRegionMode
                    )
                }
                item {
                    SwitchPreference(
                        title = "Exclude Beta",
                        isEnabled = syncFilters.excludeBeta,
                        isFocused = focusIndex == 2,
                        onToggle = onToggleExcludeBeta
                    )
                }
                item {
                    SwitchPreference(
                        title = "Exclude Prototype",
                        isEnabled = syncFilters.excludePrototype,
                        isFocused = focusIndex == 3,
                        onToggle = onToggleExcludePrototype
                    )
                }
                item {
                    SwitchPreference(
                        title = "Exclude Demo",
                        isEnabled = syncFilters.excludeDemo,
                        isFocused = focusIndex == 4,
                        onToggle = onToggleExcludeDemo
                    )
                }
                item {
                    SwitchPreference(
                        title = "Exclude ROM Hacks",
                        isEnabled = syncFilters.excludeHack,
                        isFocused = focusIndex == 5,
                        onToggle = onToggleExcludeHack
                    )
                }
                item {
                    SwitchPreference(
                        title = "Remove Orphaned Entries",
                        subtitle = "Delete games no longer on server",
                        isEnabled = syncFilters.deleteOrphans,
                        isFocused = focusIndex == 6,
                        onToggle = onToggleDeleteOrphans
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterBar(
                hints = listOf(
                    InputButton.DPAD to "Navigate",
                    InputButton.SOUTH to "Toggle",
                    InputButton.EAST to "Close"
                )
            )
        }

        if (showRegionPicker) {
            RegionPickerPopup(
                enabledRegions = syncFilters.enabledRegions,
                focusIndex = regionPickerFocusIndex,
                onToggle = onToggleRegion,
                onDismiss = onDismissRegionPicker
            )
        }
    }
}
