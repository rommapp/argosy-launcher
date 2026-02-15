package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.ControlsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class ControlsItem(
    val key: String,
    val visibleWhen: (ControlsState) -> Boolean = { true }
) {
    data object HapticFeedback : ControlsItem("haptic")
    data object VibrationStrength : ControlsItem(
        key = "vibration",
        visibleWhen = { it.hapticEnabled && it.vibrationSupported }
    )
    data object ControllerLayout : ControlsItem("layout")
    data object SwapAB : ControlsItem("swapAB")
    data object SwapXY : ControlsItem("swapXY")
    data object SwapStartSelect : ControlsItem("swapStartSelect")
    data object InputFocus : ControlsItem(
        key = "inputFocus",
        visibleWhen = { it.hasSecondaryDisplay }
    )

    companion object {
        val ALL: List<ControlsItem> = listOf(
            InputFocus, HapticFeedback, VibrationStrength, ControllerLayout,
            SwapAB, SwapXY, SwapStartSelect
        )
    }
}

private val controlsLayout = SettingsLayout<ControlsItem, ControlsState>(
    allItems = ControlsItem.ALL,
    isFocusable = { true },
    visibleWhen = { item, state -> item.visibleWhen(state) }
)

internal fun controlsMaxFocusIndex(controls: ControlsState): Int = controlsLayout.maxFocusIndex(controls)

internal fun controlsItemAtFocusIndex(index: Int, controls: ControlsState): ControlsItem? =
    controlsLayout.itemAtFocusIndex(index, controls)

@Composable
fun ControlsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val controls = uiState.controls

    val visibleItems = remember(controls.hapticEnabled, controls.vibrationSupported, controls.hasSecondaryDisplay) {
        controlsLayout.visibleItems(controls)
    }

    fun isFocused(item: ControlsItem): Boolean =
        uiState.focusedIndex == controlsLayout.focusIndexOf(item, controls)

    FocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
        contentPadding = PaddingValues(top = Dimens.spacingMd, bottom = Dimens.spacingXxl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                ControlsItem.HapticFeedback -> SwitchPreference(
                    title = "Haptic Feedback",
                    isEnabled = controls.hapticEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setHapticEnabled(it) }
                )

                ControlsItem.VibrationStrength -> SliderPreference(
                    title = "Vibration Strength",
                    value = (controls.vibrationStrength * 10).toInt() + 1,
                    minValue = 1,
                    maxValue = 11,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleVibrationStrength() }
                )

                ControlsItem.ControllerLayout -> {
                    val layoutDisplay = when (controls.controllerLayout) {
                        "nintendo" -> "Nintendo"
                        "xbox" -> "Xbox"
                        else -> "Auto"
                    }
                    val detected = controls.detectedLayout
                    val device = controls.detectedDeviceName
                    val subtitle = when {
                        detected != null && device != null -> "Detected: $detected ($device)"
                        detected != null -> "Detected: $detected"
                        else -> "No controller detected"
                    }
                    CyclePreference(
                        title = "Controller Layout",
                        value = layoutDisplay,
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleControllerLayout() }
                    )
                }

                ControlsItem.SwapAB -> SwitchPreference(
                    title = "Swap A/B",
                    subtitle = "Swap confirm and back buttons",
                    isEnabled = controls.swapAB,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSwapAB(it) }
                )

                ControlsItem.SwapXY -> SwitchPreference(
                    title = "Swap X/Y",
                    subtitle = "Swap context menu and secondary action",
                    isEnabled = controls.swapXY,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSwapXY(it) }
                )

                ControlsItem.SwapStartSelect -> SwitchPreference(
                    title = "Swap Start/Select",
                    subtitle = "Flip the Start and Select button functions",
                    isEnabled = controls.swapStartSelect,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSwapStartSelect(it) }
                )

                ControlsItem.InputFocus -> CyclePreference(
                    title = "Input Focus",
                    value = controls.dualScreenInputFocus.displayName,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleDualScreenInputFocus() }
                )
            }
        }
    }
}
