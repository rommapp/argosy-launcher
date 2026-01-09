package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun ControlsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val maxIndex = if (uiState.controls.hapticEnabled) 5 else 4

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..maxIndex) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset + paddingBuffer)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
        contentPadding = PaddingValues(top = Dimens.spacingMd, bottom = Dimens.spacingXxl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            SwitchPreference(
                title = "Haptic Feedback",
                isEnabled = uiState.controls.hapticEnabled,
                isFocused = uiState.focusedIndex == 0,
                onToggle = { viewModel.setHapticEnabled(it) }
            )
        }
        if (uiState.controls.hapticEnabled) {
            item {
                SliderPreference(
                    title = "Haptic Intensity",
                    value = uiState.controls.hapticIntensity.ordinal + 1,
                    minValue = 1,
                    maxValue = 3,
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.cycleHapticIntensity() }
                )
            }
        }
        item {
            val focusIndex = if (uiState.controls.hapticEnabled) 2 else 1
            val layoutDisplay = when (uiState.controls.controllerLayout) {
                "nintendo" -> "Nintendo"
                "xbox" -> "Xbox"
                else -> "Auto"
            }
            val detected = uiState.controls.detectedLayout
            val device = uiState.controls.detectedDeviceName
            val subtitle = when {
                detected != null && device != null -> "Detected: $detected ($device)"
                detected != null -> "Detected: $detected"
                else -> "No controller detected"
            }
            CyclePreference(
                title = "Controller Layout",
                value = layoutDisplay,
                subtitle = subtitle,
                isFocused = uiState.focusedIndex == focusIndex,
                onClick = { viewModel.cycleControllerLayout() }
            )
        }
        item {
            val focusIndex = if (uiState.controls.hapticEnabled) 3 else 2
            SwitchPreference(
                title = "Swap A/B",
                subtitle = "Swap confirm and back buttons",
                isEnabled = uiState.controls.swapAB,
                isFocused = uiState.focusedIndex == focusIndex,
                onToggle = { viewModel.setSwapAB(it) }
            )
        }
        item {
            val focusIndex = if (uiState.controls.hapticEnabled) 4 else 3
            SwitchPreference(
                title = "Swap X/Y",
                subtitle = "Swap context menu and secondary action",
                isEnabled = uiState.controls.swapXY,
                isFocused = uiState.focusedIndex == focusIndex,
                onToggle = { viewModel.setSwapXY(it) }
            )
        }
        item {
            val focusIndex = if (uiState.controls.hapticEnabled) 5 else 4
            SwitchPreference(
                title = "Swap Start/Select",
                subtitle = "Flip the Start and Select button functions",
                isEnabled = uiState.controls.swapStartSelect,
                isFocused = uiState.focusedIndex == focusIndex,
                onToggle = { viewModel.setSwapStartSelect(it) }
            )
        }
    }
}
