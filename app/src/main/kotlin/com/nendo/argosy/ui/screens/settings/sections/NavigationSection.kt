package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.ControlsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.delegates.ControlsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class NavigationItem(
    val key: String,
    val section: String,
    val visibleWhen: (ControlsState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(
        key: String,
        section: String,
        val title: String,
        visibleWhen: (ControlsState) -> Boolean = { true }
    ) : NavigationItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (ControlsState) -> Boolean = { true })
        : NavigationItem(key, section, visibleWhen)

    data object ControllerLayout : NavigationItem("layout", "controller")
    data object SwapAB : NavigationItem("swapAB", "controller")
    data object SwapXY : NavigationItem("swapXY", "controller")
    data object SwapStartSelect : NavigationItem("swapStartSelect", "controller")

    data object HapticFeedback : NavigationItem("haptic", "feedback")
    data object VibrationStrength : NavigationItem(
        key = "vibration",
        section = "feedback",
        visibleWhen = { it.hapticEnabled && it.vibrationSupported }
    )

    data object MenuWrap : NavigationItem("menuWrap", "menus")
    data object SelectLCombo : NavigationItem("selectLCombo", "menus")
    data object SelectRCombo : NavigationItem("selectRCombo", "menus")

    companion object {
        private val ControllerHeader = Header("controllerHeader", "controller", "Controller")
        private val FeedbackSpacer = SectionSpacer("feedbackSpacer", "feedback")
        private val FeedbackHeader = Header("feedbackHeader", "feedback", "Feedback")
        private val MenusSpacer = SectionSpacer("menusSpacer", "menus")
        private val MenusHeader = Header("menusHeader", "menus", "Menus")

        val ALL: List<NavigationItem> = listOf(
            ControllerHeader,
            ControllerLayout, SwapAB, SwapXY, SwapStartSelect,
            FeedbackSpacer, FeedbackHeader,
            HapticFeedback, VibrationStrength,
            MenusSpacer, MenusHeader,
            MenuWrap, SelectLCombo, SelectRCombo
        )
    }
}

private val navigationLayout = SettingsLayout<NavigationItem, ControlsState>(
    allItems = NavigationItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "controller" -> "Controller"
            "feedback" -> "Feedback"
            "menus" -> "Menus"
            else -> null
        }
    }
)

internal fun navigationMaxFocusIndex(controls: ControlsState): Int = navigationLayout.maxFocusIndex(controls)

internal fun navigationItemAtFocusIndex(index: Int, controls: ControlsState): NavigationItem? =
    navigationLayout.itemAtFocusIndex(index, controls)

internal fun navigationSections(controls: ControlsState) = navigationLayout.buildSections(controls)

@Composable
fun NavigationSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val controls = uiState.controls

    val visibleItems = remember(controls.hapticEnabled, controls.vibrationSupported) {
        navigationLayout.visibleItems(controls)
    }
    val sections = remember(controls.hapticEnabled, controls.vibrationSupported) {
        navigationLayout.buildSections(controls)
    }

    fun isFocused(item: NavigationItem): Boolean =
        uiState.focusedIndex == navigationLayout.focusIndexOf(item, controls)

    fun pickerToken(item: NavigationItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { navigationLayout.focusToListIndex(it, controls) },
        itemKey = { it.key },
        isNavItem = { it is NavigationItem.SectionSpacer },
        isHeader = { it is NavigationItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is NavigationItem.Header -> NavigationSectionHeader(item.title)
            is NavigationItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            NavigationItem.ControllerLayout -> {
                val layoutDisplay = ControlsSettingsDelegate.layoutDisplayName(controls.controllerLayout)
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
                    onClick = { viewModel.cycleControllerLayout() },
                    onPrev = { viewModel.cycleControllerLayout(-1) },
                    options = remember { ControlsSettingsDelegate.LAYOUT_CYCLE.map { ControlsSettingsDelegate.layoutDisplayName(it) } },
                    onSelect = { viewModel.setControllerLayout(ControlsSettingsDelegate.LAYOUT_CYCLE[it]) },
                    pickerRequestToken = pickerToken(item)
                )
            }

            NavigationItem.SwapAB -> SwitchPreference(
                title = "Swap A/B",
                subtitle = "Swap confirm and back buttons",
                isEnabled = controls.swapAB,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapAB(it) }
            )

            NavigationItem.SwapXY -> SwitchPreference(
                title = "Swap X/Y",
                subtitle = "Swap context menu and secondary action",
                isEnabled = controls.swapXY,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapXY(it) }
            )

            NavigationItem.SwapStartSelect -> SwitchPreference(
                title = "Swap Start/Select",
                subtitle = "Flip the Start and Select button functions",
                isEnabled = controls.swapStartSelect,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapStartSelect(it) }
            )

            NavigationItem.HapticFeedback -> SwitchPreference(
                title = "Haptic Feedback",
                isEnabled = controls.hapticEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setHapticEnabled(it) }
            )

            NavigationItem.VibrationStrength -> SliderPreference(
                title = "Vibration Strength",
                value = (controls.vibrationStrength * 10).toInt() + 1,
                minValue = 1,
                maxValue = 11,
                isFocused = isFocused(item),
                onClick = { viewModel.cycleVibrationStrength() }
            )

            NavigationItem.MenuWrap -> CyclePreference(
                title = "Menu Wrapping",
                value = controls.menuWrapMode.displayName,
                subtitle = "Navigate past the last item to the first",
                isFocused = isFocused(item),
                onClick = { viewModel.cycleMenuWrapMode() },
                onPrev = { viewModel.cycleMenuWrapMode(-1) },
                options = remember { MenuWrapMode.entries.map { it.displayName } },
                onSelect = { viewModel.setMenuWrapMode(MenuWrapMode.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            NavigationItem.SelectLCombo -> CyclePreference(
                title = "Select + L",
                value = ControlsSettingsDelegate.comboDisplayName(controls.selectLCombo),
                subtitle = "Hold Select and press L1",
                isFocused = isFocused(item),
                onClick = { viewModel.cycleSelectLCombo() },
                onPrev = { viewModel.cycleSelectLCombo(-1) },
                options = remember { ControlsSettingsDelegate.COMBO_CYCLE.map { ControlsSettingsDelegate.comboDisplayName(it) } },
                onSelect = { viewModel.setSelectLCombo(ControlsSettingsDelegate.COMBO_CYCLE[it]) },
                pickerRequestToken = pickerToken(item)
            )

            NavigationItem.SelectRCombo -> CyclePreference(
                title = "Select + R",
                value = ControlsSettingsDelegate.comboDisplayName(controls.selectRCombo),
                subtitle = "Hold Select and press R1",
                isFocused = isFocused(item),
                onClick = { viewModel.cycleSelectRCombo() },
                onPrev = { viewModel.cycleSelectRCombo(-1) },
                options = remember { ControlsSettingsDelegate.COMBO_CYCLE.map { ControlsSettingsDelegate.comboDisplayName(it) } },
                onSelect = { viewModel.setSelectRCombo(ControlsSettingsDelegate.COMBO_CYCLE[it]) },
                pickerRequestToken = pickerToken(item)
            )
        }
    }
}

@Composable
private fun NavigationSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
