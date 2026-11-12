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
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.hardware.SystemHotkeyAction
import com.nendo.argosy.libretro.HotkeyManager
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.input.UiShortcut
import com.nendo.argosy.ui.input.UiShortcutKeys
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.screens.settings.ControlsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.NavRingPopup
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.components.ShortcutCaptureModal
import com.nendo.argosy.ui.screens.settings.components.SystemComboCaptureModal
import com.nendo.argosy.ui.screens.settings.delegates.ControlsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import kotlin.math.roundToInt

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
    data object IgnoreControllerTouchpad : NavigationItem("ignoreControllerTouchpad", "controller")

    data object HapticFeedback : NavigationItem("haptic", "feedback")
    data object VibrationStrength : NavigationItem(
        key = "vibration",
        section = "feedback",
        visibleWhen = { it.hapticEnabled }
    )

    data object MenuWrap : NavigationItem("menuWrap", "menus")
    data object SelectLCombo : NavigationItem("selectLCombo", "menus")
    data object SelectRCombo : NavigationItem("selectRCombo", "menus")
    data object SelectSwap : NavigationItem(
        key = "selectSwap",
        section = "menus",
        visibleWhen = { it.hasSecondaryDisplay }
    )

    data object QuickNavigation : NavigationItem("quickNavigation", "navbar")
    data object NavBarAutoHide : NavigationItem(
        key = "navBarAutoHide",
        section = "navbar",
        visibleWhen = { it.quickNavigation }
    )
    data object NavBarPages : NavigationItem(
        key = "navBarPages",
        section = "navbar",
        visibleWhen = { it.quickNavigation }
    )

    data object OpenNavigationShortcut : NavigationItem("openNavigationKey", "shortcuts")
    data object OpenQuickPanelShortcut : NavigationItem("openQuickPanelKey", "shortcuts")

    data object SystemHotkeys : NavigationItem("systemHotkeys", "system")
    data object SystemHotkeysAccess : NavigationItem(
        key = "systemHotkeysAccess",
        section = "system",
        visibleWhen = { it.systemHotkeysEnabled }
    )
    data object SystemHomeCombo : NavigationItem(
        key = "systemHomeCombo",
        section = "system",
        visibleWhen = { it.systemHotkeysEnabled }
    )
    data object SystemBackCombo : NavigationItem(
        key = "systemBackCombo",
        section = "system",
        visibleWhen = { it.systemHotkeysEnabled }
    )

    val shortcut: UiShortcut? get() = when (this) {
        OpenNavigationShortcut -> UiShortcut.OPEN_NAVIGATION
        OpenQuickPanelShortcut -> UiShortcut.OPEN_QUICK_PANEL
        else -> null
    }

    val systemHotkeyAction: SystemHotkeyAction? get() = when (this) {
        SystemHomeCombo -> SystemHotkeyAction.HOME
        SystemBackCombo -> SystemHotkeyAction.BACK
        else -> null
    }

    companion object {
        private val ControllerHeader =
            Header("controllerHeader", "controller", R.string.settings_navigation_section_controller)
        private val FeedbackSpacer = SectionSpacer("feedbackSpacer", "feedback")
        private val FeedbackHeader =
            Header("feedbackHeader", "feedback", R.string.settings_navigation_section_feedback)
        private val MenusSpacer = SectionSpacer("menusSpacer", "menus")
        private val MenusHeader = Header("menusHeader", "menus", R.string.settings_navigation_section_menus)
        private val NavBarSpacer = SectionSpacer("navBarSpacer", "navbar")
        private val NavBarHeader =
            Header("navBarHeader", "navbar", R.string.settings_navigation_section_nav_bar)
        private val ShortcutsSpacer = SectionSpacer("shortcutsSpacer", "shortcuts")
        private val ShortcutsHeader =
            Header("shortcutsHeader", "shortcuts", R.string.settings_navigation_section_shortcuts)
        private val SystemSpacer = SectionSpacer("systemSpacer", "system")
        private val SystemHeader =
            Header("systemHeader", "system", R.string.settings_navigation_section_system_buttons)
        val ALL: List<NavigationItem>
            get() = listOf(
                ControllerHeader,
                ControllerLayout, SwapAB, SwapXY, SwapStartSelect, IgnoreControllerTouchpad,
                FeedbackSpacer, FeedbackHeader,
                HapticFeedback, VibrationStrength,
                MenusSpacer, MenusHeader,
                MenuWrap, SelectLCombo, SelectRCombo, SelectSwap,
                NavBarSpacer, NavBarHeader,
                QuickNavigation, NavBarAutoHide, NavBarPages,
                ShortcutsSpacer, ShortcutsHeader,
                OpenNavigationShortcut, OpenQuickPanelShortcut,
                SystemSpacer, SystemHeader,
                SystemHotkeys, SystemHotkeysAccess, SystemHomeCombo, SystemBackCombo
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
            "navbar" -> R.string.settings_navigation_section_nav_bar
            "shortcuts" -> R.string.settings_navigation_section_shortcuts
            "system" -> R.string.settings_navigation_section_system_buttons
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

    val visibleItems = remember(
        controls.hapticEnabled, controls.hasSecondaryDisplay, controls.quickNavigation, controls.systemHotkeysEnabled
    ) {
        navigationLayout.visibleItems(controls)
    }
    val sections = remember(
        controls.hapticEnabled, controls.hasSecondaryDisplay, controls.quickNavigation, controls.systemHotkeysEnabled,
        context
    ) {
        navigationLayout.buildSections(controls, context)
    }

    fun isFocused(item: NavigationItem): Boolean =
        uiState.focusedIndex == navigationLayout.focusIndexOf(item, controls)

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
                val detected = controls.detectedLayout
                val device = controls.detectedDeviceName
                val subtitle = when {
                    detected != null && device != null ->
                        stringResource(R.string.settings_navigation_controller_layout_detected_device, detected, device)
                    detected != null ->
                        stringResource(R.string.settings_navigation_controller_layout_detected, detected)
                    else -> stringResource(R.string.settings_navigation_controller_layout_undetected)
                }
                SegmentedPreference(
                    title = stringResource(R.string.settings_navigation_controller_layout_title),
                    subtitle = subtitle,
                    options = ControlsSettingsDelegate.LAYOUT_OPTIONS.map {
                        stringResource(ControlsSettingsDelegate.layoutDisplayNameRes(it))
                    },
                    selectedIndex = ControlsSettingsDelegate.LAYOUT_OPTIONS.indexOf(controls.controllerLayout),
                    isFocused = isFocused(item),
                    onSelect = { viewModel.setControllerLayout(ControlsSettingsDelegate.LAYOUT_OPTIONS[it]) }
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

            NavigationItem.IgnoreControllerTouchpad -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_ignore_controller_touchpad_title),
                subtitle = stringResource(R.string.settings_navigation_ignore_controller_touchpad_subtitle),
                isEnabled = controls.ignoreControllerTouchpad,
                isFocused = isFocused(item),
                onToggle = { viewModel.setIgnoreControllerTouchpad(it) }
            )

            NavigationItem.QuickNavigation -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_quick_navigation_title),
                subtitle = stringResource(R.string.settings_navigation_quick_navigation_subtitle),
                isEnabled = controls.quickNavigation,
                isFocused = isFocused(item),
                onToggle = { viewModel.setQuickNavigation(it) }
            )

            NavigationItem.NavBarAutoHide -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_nav_bar_auto_hide_title),
                subtitle = stringResource(R.string.settings_navigation_nav_bar_auto_hide_subtitle),
                isEnabled = controls.navBarAutoHide,
                isFocused = isFocused(item),
                onToggle = { viewModel.setNavBarAutoHide(it) }
            )

            NavigationItem.HapticFeedback -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_haptic_title),
                isEnabled = controls.hapticEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setHapticEnabled(it) }
            )

            NavigationItem.VibrationStrength -> SliderPreference(
                title = stringResource(R.string.settings_navigation_vibration_title),
                value = (controls.vibrationStrength * ControlsPreferences.HAPTIC_STRENGTH_STEPS).roundToInt() + 1,
                minValue = 1,
                maxValue = ControlsPreferences.HAPTIC_STRENGTH_STEPS + 1,
                isFocused = isFocused(item),
                onAdjust = {
                    viewModel.adjustVibrationStrength(
                        if (it < 0) -ControlsPreferences.HAPTIC_STRENGTH_STEP else ControlsPreferences.HAPTIC_STRENGTH_STEP
                    )
                }
            )

            NavigationItem.MenuWrap -> SegmentedPreference(
                title = stringResource(R.string.settings_navigation_menu_wrap_title),
                subtitle = stringResource(R.string.settings_navigation_menu_wrap_subtitle),
                options = MenuWrapMode.entries.map { stringResource(menuWrapLabelRes(it)) },
                selectedIndex = MenuWrapMode.entries.indexOf(controls.menuWrapMode),
                isFocused = isFocused(item),
                onSelect = { viewModel.setMenuWrapMode(MenuWrapMode.entries[it]) }
            )

            NavigationItem.SelectLCombo -> SegmentedPreference(
                title = stringResource(R.string.settings_navigation_select_l_combo_title),
                subtitle = stringResource(R.string.settings_navigation_select_l_combo_subtitle),
                options = ControlsSettingsDelegate.COMBO_OPTIONS.map {
                    stringResource(ControlsSettingsDelegate.comboDisplayNameRes(it))
                },
                selectedIndex = ControlsSettingsDelegate.COMBO_OPTIONS.indexOf(controls.selectLCombo),
                isFocused = isFocused(item),
                onSelect = { viewModel.setSelectLCombo(ControlsSettingsDelegate.COMBO_OPTIONS[it]) }
            )

            NavigationItem.SelectRCombo -> SegmentedPreference(
                title = stringResource(R.string.settings_navigation_select_r_combo_title),
                subtitle = stringResource(R.string.settings_navigation_select_r_combo_subtitle),
                options = ControlsSettingsDelegate.COMBO_OPTIONS.map {
                    stringResource(ControlsSettingsDelegate.comboDisplayNameRes(it))
                },
                selectedIndex = ControlsSettingsDelegate.COMBO_OPTIONS.indexOf(controls.selectRCombo),
                isFocused = isFocused(item),
                onSelect = { viewModel.setSelectRCombo(ControlsSettingsDelegate.COMBO_OPTIONS[it]) }
            )

            NavigationItem.SelectSwap -> SegmentedPreference(
                title = stringResource(R.string.settings_navigation_select_swap_title),
                subtitle = stringResource(R.string.settings_navigation_select_swap_subtitle),
                options = SelectSwapMode.entries.map { stringResource(it.labelRes) },
                selectedIndex = SelectSwapMode.entries.indexOf(controls.selectSwapMode),
                isFocused = isFocused(item),
                onSelect = { viewModel.setSelectSwapMode(SelectSwapMode.entries[it]) }
            )

            NavigationItem.NavBarPages -> ActionPreference(
                title = stringResource(R.string.settings_navigation_nav_bar_pages_title),
                subtitle = stringResource(R.string.settings_navigation_nav_bar_pages_subtitle),
                isFocused = isFocused(item),
                trailingText = stringResource(
                    R.string.settings_navigation_nav_bar_pages_count,
                    controls.navRingRoutes.size,
                    NavRing.PAGES.size
                ),
                onClick = { viewModel.showNavRingModal() }
            )

            NavigationItem.OpenNavigationShortcut -> ShortcutKeyPreference(
                title = stringResource(R.string.settings_navigation_open_navigation_title),
                subtitle = stringResource(R.string.settings_navigation_open_navigation_subtitle),
                keyCode = controls.openNavigationKey,
                isFocused = isFocused(item),
                onClick = { viewModel.startShortcutCapture(UiShortcut.OPEN_NAVIGATION) }
            )

            NavigationItem.OpenQuickPanelShortcut -> ShortcutKeyPreference(
                title = stringResource(R.string.settings_navigation_open_quick_panel_title),
                subtitle = stringResource(R.string.settings_navigation_open_quick_panel_subtitle),
                keyCode = controls.openQuickPanelKey,
                isFocused = isFocused(item),
                onClick = { viewModel.startShortcutCapture(UiShortcut.OPEN_QUICK_PANEL) }
            )

            NavigationItem.SystemHotkeys -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_system_hotkeys_title),
                subtitle = stringResource(R.string.settings_navigation_system_hotkeys_subtitle),
                isEnabled = controls.systemHotkeysEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSystemHotkeysEnabled(it) }
            )

            NavigationItem.SystemHotkeysAccess -> ActionPreference(
                title = stringResource(R.string.settings_navigation_system_hotkeys_access_title),
                subtitle = stringResource(R.string.settings_navigation_system_hotkeys_access_subtitle),
                isFocused = isFocused(item),
                trailingText = if (uiState.permissions.snapshot.isGranted(ArgosyPermission.ACCESSIBILITY)) {
                    stringResource(R.string.settings_navigation_system_hotkeys_access_on)
                } else {
                    stringResource(R.string.settings_navigation_system_hotkeys_access_needed)
                },
                onClick = { viewModel.openAccessibilitySettings() }
            )

            NavigationItem.SystemHomeCombo -> SystemComboPreference(
                title = stringResource(R.string.settings_navigation_system_home_title),
                subtitle = stringResource(R.string.settings_navigation_system_home_subtitle),
                action = SystemHotkeyAction.HOME,
                controls = controls,
                isFocused = isFocused(item),
                viewModel = viewModel
            )

            NavigationItem.SystemBackCombo -> SystemComboPreference(
                title = stringResource(R.string.settings_navigation_system_back_title),
                subtitle = stringResource(R.string.settings_navigation_system_back_subtitle),
                action = SystemHotkeyAction.BACK,
                controls = controls,
                isFocused = isFocused(item),
                viewModel = viewModel
            )
        }
    }

    if (controls.showNavRingModal) {
        NavRingPopup(
            enabled = controls.navRingRoutes,
            focusIndex = controls.navRingFocusIndex,
            heldToken = controls.navRingHeld,
            onFocus = { viewModel.focusNavRing(it) },
            onToggle = { viewModel.toggleNavRing(it) },
            onLift = { viewModel.liftNavRing() },
            onLiftAt = { viewModel.liftNavRingAt(it) },
            onMoveTo = { token, index -> viewModel.moveNavRingTo(token, index) },
            onDrop = { viewModel.dropNavRing() },
            onBack = { viewModel.backNavRing() }
        )
    }

    controls.shortcutCaptureTarget?.let { target ->
        ShortcutCaptureModal(
            actionTitle = stringResource(shortcutTitleRes(target)),
            onAssign = { keyCode -> viewModel.assignShortcutKey(target, keyCode) },
            onDismiss = { viewModel.cancelShortcutCapture() }
        )
    }

    controls.systemComboCaptureTarget?.let { action ->
        SystemComboCaptureModal(
            actionTitle = stringResource(systemComboTitleRes(action)),
            onAssign = { keyCodes -> viewModel.assignSystemCombo(action, keyCodes) },
            onDismiss = { viewModel.cancelSystemComboCapture() }
        )
    }
}

private fun shortcutTitleRes(shortcut: UiShortcut): Int = when (shortcut) {
    UiShortcut.OPEN_NAVIGATION -> R.string.settings_navigation_open_navigation_title
    UiShortcut.OPEN_QUICK_PANEL -> R.string.settings_navigation_open_quick_panel_title
}

private fun systemComboTitleRes(action: SystemHotkeyAction): Int = when (action) {
    SystemHotkeyAction.HOME -> R.string.settings_navigation_system_home_title
    SystemHotkeyAction.BACK -> R.string.settings_navigation_system_back_title
}

@Composable
private fun SystemComboPreference(
    title: String,
    subtitle: String,
    action: SystemHotkeyAction,
    controls: ControlsState,
    isFocused: Boolean,
    viewModel: SettingsViewModel
) {
    val combo = controls.systemCombo(action)
    ActionPreference(
        title = title,
        subtitle = subtitle,
        isFocused = isFocused,
        trailingText = when (action) {
            SystemHotkeyAction.HOME -> HotkeyManager.formatCombo(combo)
            SystemHotkeyAction.BACK -> combo.joinToString(" + ") { HotkeyManager.getKeyName(it) }
        },
        showResetButton = controls.isSystemComboCustom(action),
        onReset = { viewModel.resetSystemCombo(action) },
        onClick = { viewModel.startSystemComboCapture(action) }
    )
}

@Composable
private fun ShortcutKeyPreference(
    title: String,
    subtitle: String,
    keyCode: Int,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    ActionPreference(
        title = title,
        subtitle = subtitle,
        isFocused = isFocused,
        trailingText = if (UiShortcutKeys.isBindable(keyCode)) {
            HotkeyManager.getKeyName(keyCode)
        } else {
            stringResource(R.string.settings_navigation_shortcut_unassigned)
        },
        onClick = onClick
    )
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
