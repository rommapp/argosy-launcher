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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
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
        val titleRes: Int,
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
        private val ControllerHeader =
            Header("controllerHeader", "controller", R.string.settings_navigation_section_controller)
        private val FeedbackSpacer = SectionSpacer("feedbackSpacer", "feedback")
        private val FeedbackHeader =
            Header("feedbackHeader", "feedback", R.string.settings_navigation_section_feedback)
        private val MenusSpacer = SectionSpacer("menusSpacer", "menus")
        private val MenusHeader = Header("menusHeader", "menus", R.string.settings_navigation_section_menus)

        val ALL: List<NavigationItem>
            get() = listOf(
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
    sectionTitleRes = {
        when (it) {
            "controller" -> R.string.settings_navigation_section_controller
            "feedback" -> R.string.settings_navigation_section_feedback
            "menus" -> R.string.settings_navigation_section_menus
            else -> null
        }
    }
)

internal fun navigationMaxFocusIndex(controls: ControlsState): Int = navigationLayout.maxFocusIndex(controls)

internal fun navigationItemAtFocusIndex(index: Int, controls: ControlsState): NavigationItem? =
    navigationLayout.itemAtFocusIndex(index, controls)

internal fun navigationSections(controls: ControlsState) = navigationLayout.buildSections(controls)

private fun menuWrapLabelRes(mode: MenuWrapMode): Int = when (mode) {
    MenuWrapMode.OFF -> R.string.settings_navigation_menu_wrap_off
    MenuWrapMode.HARD_STOP -> R.string.settings_navigation_menu_wrap_hard_stop
    MenuWrapMode.AUTO -> R.string.settings_navigation_menu_wrap_auto
}

@Composable
fun NavigationSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val controls = uiState.controls
    val context = LocalContext.current

    val visibleItems = remember(controls.hapticEnabled, controls.vibrationSupported) {
        navigationLayout.visibleItems(controls)
    }
    val sections = remember(controls.hapticEnabled, controls.vibrationSupported, context) {
        navigationLayout.buildSections(controls, context)
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
            is NavigationItem.Header -> NavigationSectionHeader(stringResource(item.titleRes))
            is NavigationItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            NavigationItem.ControllerLayout -> {
                val layoutDisplay = stringResource(
                    ControlsSettingsDelegate.layoutDisplayNameRes(controls.controllerLayout)
                )
                val detected = controls.detectedLayout
                val device = controls.detectedDeviceName
                val subtitle = when {
                    detected != null && device != null ->
                        stringResource(R.string.settings_navigation_controller_layout_detected_device, detected, device)
                    detected != null ->
                        stringResource(R.string.settings_navigation_controller_layout_detected, detected)
                    else -> stringResource(R.string.settings_navigation_controller_layout_undetected)
                }
                CyclePreference(
                    title = stringResource(R.string.settings_navigation_controller_layout_title),
                    value = layoutDisplay,
                    subtitle = subtitle,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleControllerLayout() },
                    onPrev = { viewModel.cycleControllerLayout(-1) },
                    options = remember(context) {
                        ControlsSettingsDelegate.LAYOUT_CYCLE.map {
                            context.getString(ControlsSettingsDelegate.layoutDisplayNameRes(it))
                        }
                    },
                    onSelect = { viewModel.setControllerLayout(ControlsSettingsDelegate.LAYOUT_CYCLE[it]) },
                    pickerRequestToken = pickerToken(item)
                )
            }

            NavigationItem.SwapAB -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_swap_ab_title),
                subtitle = stringResource(R.string.settings_navigation_swap_ab_subtitle),
                isEnabled = controls.swapAB,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapAB(it) }
            )

            NavigationItem.SwapXY -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_swap_xy_title),
                subtitle = stringResource(R.string.settings_navigation_swap_xy_subtitle),
                isEnabled = controls.swapXY,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapXY(it) }
            )

            NavigationItem.SwapStartSelect -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_swap_start_select_title),
                subtitle = stringResource(R.string.settings_navigation_swap_start_select_subtitle),
                isEnabled = controls.swapStartSelect,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapStartSelect(it) }
            )

            NavigationItem.HapticFeedback -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_haptic_title),
                isEnabled = controls.hapticEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setHapticEnabled(it) }
            )

            NavigationItem.VibrationStrength -> SliderPreference(
                title = stringResource(R.string.settings_navigation_vibration_title),
                value = (controls.vibrationStrength * 10).toInt() + 1,
                minValue = 1,
                maxValue = 11,
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustVibrationStrength(if (it < 0) -0.1f else 0.1f) }
            )

            NavigationItem.MenuWrap -> CyclePreference(
                title = stringResource(R.string.settings_navigation_menu_wrap_title),
                value = stringResource(menuWrapLabelRes(controls.menuWrapMode)),
                subtitle = stringResource(R.string.settings_navigation_menu_wrap_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleMenuWrapMode() },
                onPrev = { viewModel.cycleMenuWrapMode(-1) },
                options = remember(context) {
                    MenuWrapMode.entries.map { context.getString(menuWrapLabelRes(it)) }
                },
                onSelect = { viewModel.setMenuWrapMode(MenuWrapMode.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            NavigationItem.SelectLCombo -> CyclePreference(
                title = stringResource(R.string.settings_navigation_select_l_combo_title),
                value = stringResource(
                    ControlsSettingsDelegate.comboDisplayNameRes(controls.selectLCombo)
                ),
                subtitle = stringResource(R.string.settings_navigation_select_l_combo_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleSelectLCombo() },
                onPrev = { viewModel.cycleSelectLCombo(-1) },
                options = remember(context) {
                    ControlsSettingsDelegate.COMBO_CYCLE.map {
                        context.getString(ControlsSettingsDelegate.comboDisplayNameRes(it))
                    }
                },
                onSelect = { viewModel.setSelectLCombo(ControlsSettingsDelegate.COMBO_CYCLE[it]) },
                pickerRequestToken = pickerToken(item)
            )

            NavigationItem.SelectRCombo -> CyclePreference(
                title = stringResource(R.string.settings_navigation_select_r_combo_title),
                value = stringResource(
                    ControlsSettingsDelegate.comboDisplayNameRes(controls.selectRCombo)
                ),
                subtitle = stringResource(R.string.settings_navigation_select_r_combo_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleSelectRCombo() },
                onPrev = { viewModel.cycleSelectRCombo(-1) },
                options = remember(context) {
                    ControlsSettingsDelegate.COMBO_CYCLE.map {
                        context.getString(ControlsSettingsDelegate.comboDisplayNameRes(it))
                    }
                },
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
