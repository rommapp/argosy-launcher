package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.AppLanguage
import com.nendo.argosy.data.preferences.ScreenOrientationMode
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InfoPreference
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens

internal data class InterfaceLayoutState(
    val display: DisplayState
) {
    companion object {
        fun from(state: SettingsUiState) = InterfaceLayoutState(display = state.display)
    }
}

internal sealed class InterfaceItem(
    val key: String,
    val section: String,
    val visibleWhen: (InterfaceLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int) : InterfaceItem(key, section)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (InterfaceLayoutState) -> Boolean = { true }
    ) : InterfaceItem(key, section, visibleWhen), SearchableSetting

    data object Language : Row("language", SECTION_GENERAL, R.string.settings_main_language_title)
    data object UiScale : Row("uiScale", SECTION_GENERAL, R.string.settings_interface_ui_scale_title)
    data object ReducedMotion :
        Row("reducedMotion", SECTION_GENERAL, R.string.settings_interface_reduced_motion)
    data object ScreenOrientation :
        Row("screenOrientation", SECTION_GENERAL, R.string.settings_interface_screen_orientation_title)
    data object CompactFooter :
        Row("compactFooter", SECTION_GENERAL, R.string.settings_interface_compact_footer_title)
    data object HideSystemNavigationBar :
        Row("hideSystemNavigationBar", SECTION_GENERAL, R.string.settings_interface_hide_system_nav_bar_title)
    data object HideSystemStatusBar :
        Row("hideSystemStatusBar", SECTION_GENERAL, R.string.settings_interface_hide_system_status_bar_title)
    data object ControllerGrip :
        Row("controllerGrip", SECTION_GENERAL, R.string.settings_interface_controller_grip_title)
    data object HomeScreen : Row("homeScreen", SECTION_SCREENS, R.string.settings_interface_home_screen_title)
    data object LibraryView : Row("libraryView", SECTION_SCREENS, R.string.settings_interface_library_view_title)
    data object BoxArt : Row("boxArt", SECTION_SCREENS, R.string.settings_interface_box_art_title)
    data object Presentation : Row(
        key = "presentation",
        section = SECTION_SCREENS,
        labelRes = R.string.settings_interface_presentation_title,
        visibleWhen = {
            it.display.dualScreenEnabled && it.display.hasSecondaryDisplay &&
                !it.display.secondaryDisplayUnsupported
        }
    )
    data object LockScreenArt :
        Row("lockScreenArt", SECTION_SCREENS, R.string.settings_interface_lock_screen_art_title)
    data object HomeApp : Row(
        key = "homeApp",
        section = SECTION_SCREENS,
        labelRes = R.string.settings_interface_home_app_title,
        visibleWhen = { !it.display.isDefaultHome }
    )
    data object HomeAppLocked : InterfaceItem(
        key = "homeAppLocked",
        section = SECTION_SCREENS,
        visibleWhen = { it.display.isDefaultHome }
    )
    data object StatusClock :
        Row("statusClock", SECTION_STATUS_BAR, R.string.settings_interface_status_clock_title)
    data object StatusBattery :
        Row("statusBattery", SECTION_STATUS_BAR, R.string.settings_interface_status_battery_title)
    data object StatusNetwork :
        Row("statusNetwork", SECTION_STATUS_BAR, R.string.settings_interface_status_network_title)
    data object PreLaunchRecaps :
        Row("preLaunchRecaps", SECTION_RECAPS, R.string.settings_interface_pre_launch_recaps_title)
    data object MonthlyRecap :
        Row("monthlyRecap", SECTION_RECAPS, R.string.settings_interface_monthly_recap_title)
    data object Notifications :
        Row("notifications", SECTION_NOTIFICATIONS, R.string.settings_interface_notifications_title)
    data object NotifyDownloadsFinished : Row(
        key = "notifyDownloadsFinished",
        section = SECTION_NOTIFICATIONS,
        labelRes = R.string.settings_interface_notify_downloads_finished_title,
        visibleWhen = { it.display.notificationsEnabled }
    )
    data object NotifyBattery :
        Row("notifyBattery", SECTION_NOTIFICATIONS, R.string.settings_interface_notify_battery_title)
    data object NotifyCharging : Row(
        key = "notifyCharging",
        section = SECTION_NOTIFICATIONS,
        labelRes = R.string.settings_interface_notify_charging_title,
        visibleWhen = { it.display.notificationsEnabled }
    )
    data object NotifyControllers : Row(
        key = "notifyControllers",
        section = SECTION_NOTIFICATIONS,
        labelRes = R.string.settings_interface_notify_controllers_title,
        visibleWhen = { it.display.notificationsEnabled }
    )

    companion object {
        /**
         * A getter, not a stored list. As a `val` this is a static of the sealed class
         * itself, so it is built during that class's initialization, which is the same
         * initialization the `data object` entries above are waiting on: whichever
         * entries have not been constructed yet land in the list as nulls.
         */
        val ALL: List<InterfaceItem>
            get() = listOf(
                Header("generalHeader", SECTION_GENERAL, R.string.settings_interface_section_general),
                Language, ReducedMotion, UiScale, CompactFooter, HideSystemNavigationBar, HideSystemStatusBar,
                ControllerGrip, ScreenOrientation,
                Header("screensHeader", SECTION_SCREENS, R.string.settings_interface_section_screens),
                HomeScreen, LibraryView, BoxArt, Presentation, LockScreenArt,
                HomeApp, HomeAppLocked,
                Header("statusBarHeader", SECTION_STATUS_BAR, R.string.settings_interface_section_status_bar),
                StatusClock, StatusBattery, StatusNetwork,
                Header("recapsHeader", SECTION_RECAPS, R.string.settings_interface_section_recaps),
                PreLaunchRecaps, MonthlyRecap,
                Header("notificationsHeader", SECTION_NOTIFICATIONS, R.string.settings_interface_section_notifications),
                Notifications, NotifyDownloadsFinished, NotifyBattery, NotifyCharging, NotifyControllers
            )
    }
}

private const val SECTION_GENERAL = "general"
private const val SECTION_SCREENS = "screens"
private const val SECTION_STATUS_BAR = "statusBar"
private const val SECTION_RECAPS = "recaps"
private const val SECTION_NOTIFICATIONS = "notifications"

private fun interfaceSectionTitleRes(section: String): Int? = when (section) {
    SECTION_GENERAL -> R.string.settings_interface_section_general
    SECTION_SCREENS -> R.string.settings_interface_section_screens
    SECTION_STATUS_BAR -> R.string.settings_interface_section_status_bar
    SECTION_RECAPS -> R.string.settings_interface_section_recaps
    SECTION_NOTIFICATIONS -> R.string.settings_interface_section_notifications
    else -> null
}

internal fun languageLabelRes(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.settings_main_language_system_default
    AppLanguage.ENGLISH -> R.string.settings_main_language_name_en
    AppLanguage.FRENCH -> R.string.settings_main_language_name_fr
    AppLanguage.SPANISH -> R.string.settings_main_language_name_es
    AppLanguage.GERMAN -> R.string.settings_main_language_name_de
    AppLanguage.CHINESE_SIMPLIFIED -> R.string.settings_main_language_name_zh_hans
    AppLanguage.CHINESE_TRADITIONAL -> R.string.settings_main_language_name_zh_hant
    AppLanguage.RUSSIAN -> R.string.settings_main_language_name_ru
    AppLanguage.HINDI -> R.string.settings_main_language_name_hi
    AppLanguage.PORTUGUESE_BRAZIL -> R.string.settings_main_language_name_pt_br
}

internal fun screenOrientationLabelRes(mode: ScreenOrientationMode): Int = when (mode) {
    ScreenOrientationMode.AUTO -> R.string.settings_interface_screen_orientation_auto
    ScreenOrientationMode.LANDSCAPE -> R.string.settings_interface_screen_orientation_landscape
    ScreenOrientationMode.PORTRAIT -> R.string.settings_interface_screen_orientation_portrait
}

internal val interfaceLayout = SettingsLayout<InterfaceItem, InterfaceLayoutState>(
    allItems = InterfaceItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = ::interfaceSectionTitleRes
)

internal fun interfaceMaxFocusIndex(state: InterfaceLayoutState): Int = interfaceLayout.maxFocusIndex(state)

internal fun interfaceItemAtFocusIndex(index: Int, state: InterfaceLayoutState): InterfaceItem? =
    interfaceLayout.itemAtFocusIndex(index, state)

internal fun interfaceSections(state: InterfaceLayoutState) = interfaceLayout.buildSections(state)

internal fun interfaceFocusIndexOf(item: InterfaceItem, state: InterfaceLayoutState): Int =
    interfaceLayout.focusIndexOf(item, state)

@Composable
fun InterfaceSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshUsageStatsPermission()
            viewModel.refreshDefaultHome()
        }
    }

    val layoutState = remember(display) { InterfaceLayoutState(display) }

    val visibleItems = remember(layoutState) {
        interfaceLayout.visibleItems(layoutState)
    }
    val sections = remember(layoutState, context) {
        interfaceLayout.buildSections(layoutState, context)
    }

    fun isFocused(item: InterfaceItem): Boolean =
        uiState.focusedIndex == interfaceLayout.focusIndexOf(item, layoutState)

    fun openFrom(item: InterfaceItem, enter: () -> Unit) {
        viewModel.setFocusIndex(interfaceLayout.focusIndexOf(item, layoutState))
        enter()
    }

    fun pickerToken(item: InterfaceItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { interfaceLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is InterfaceItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
            when (item) {
                is InterfaceItem.Header -> InterfaceSectionHeader(stringResource(item.titleRes))

                is InterfaceItem.Language -> CyclePreference(
                    title = stringResource(item.labelRes),
                    value = stringResource(languageLabelRes(uiState.appLanguage)),
                    subtitle = stringResource(R.string.settings_main_language_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleAppLanguage() },
                    onPrev = { viewModel.cycleAppLanguage(-1) },
                    options = remember(context) {
                        AppLanguage.entries.map { context.getString(languageLabelRes(it)) }
                    },
                    onSelect = { index -> viewModel.setAppLanguage(AppLanguage.entries[index].tag) },
                    pickerRequestToken = pickerToken(item)
                )

                is InterfaceItem.UiScale -> SliderPreference(
                    title = stringResource(item.labelRes),
                    value = display.uiScale,
                    minValue = 50,
                    maxValue = 150,
                    isFocused = isFocused(item),
                    step = 5,
                    suffix = "%",
                    onAdjust = { viewModel.adjustUiScale(it) }
                )

                is InterfaceItem.ReducedMotion -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_reduced_motion_subtitle),
                    isEnabled = display.reduceAnimations,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setReduceAnimations(it) }
                )

                is InterfaceItem.ScreenOrientation -> CyclePreference(
                    title = stringResource(item.labelRes),
                    value = stringResource(screenOrientationLabelRes(display.screenOrientation)),
                    subtitle = stringResource(R.string.settings_interface_screen_orientation_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleScreenOrientation() },
                    onPrev = { viewModel.cycleScreenOrientation(-1) },
                    options = remember(context) {
                        ScreenOrientationMode.entries.map { context.getString(screenOrientationLabelRes(it)) }
                    },
                    onSelect = { index -> viewModel.setScreenOrientation(ScreenOrientationMode.entries[index]) },
                    pickerRequestToken = pickerToken(item)
                )

                is InterfaceItem.CompactFooter -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_compact_footer_subtitle),
                    isEnabled = display.compactFooter,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setCompactFooter(it) }
                )

                is InterfaceItem.HideSystemNavigationBar -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_hide_system_nav_bar_subtitle),
                    isEnabled = display.hideSystemNavigationBar,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setHideSystemNavigationBar(it) }
                )

                is InterfaceItem.HideSystemStatusBar -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_hide_system_status_bar_subtitle),
                    isEnabled = display.hideSystemStatusBar,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setHideSystemStatusBar(it) }
                )

                is InterfaceItem.LockScreenArt -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_lock_screen_art_subtitle),
                    isEnabled = display.lockScreenArt,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setLockScreenArt(it) }
                )

                is InterfaceItem.HomeApp -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_home_app_subtitle),
                    isEnabled = display.homeAppAvailable,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setHomeAppAvailable(it) }
                )

                InterfaceItem.HomeAppLocked -> InfoPreference(
                    title = stringResource(R.string.settings_interface_home_app_title),
                    value = stringResource(R.string.settings_interface_home_app_locked_value),
                    subtitle = stringResource(R.string.settings_interface_home_app_locked_subtitle),
                    isFocused = false
                )

                is InterfaceItem.StatusClock -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_status_clock_subtitle),
                    isEnabled = display.showStatusClock,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setShowStatusClock(it) }
                )

                is InterfaceItem.StatusBattery -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_status_battery_subtitle),
                    isEnabled = display.showStatusBattery,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setShowStatusBattery(it) }
                )

                is InterfaceItem.StatusNetwork -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_status_network_subtitle),
                    isEnabled = display.showStatusNetwork,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setShowStatusNetwork(it) }
                )

                is InterfaceItem.PreLaunchRecaps -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_pre_launch_recaps_subtitle),
                    isEnabled = display.preLaunchRecaps,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setPreLaunchRecaps(it) }
                )

                is InterfaceItem.MonthlyRecap -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_monthly_recap_subtitle),
                    isEnabled = display.monthlyRecap,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setMonthlyRecap(it) }
                )

                is InterfaceItem.Notifications -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_notifications_subtitle),
                    isEnabled = display.notificationsEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setNotificationsEnabled(it) }
                )

                is InterfaceItem.NotifyDownloadsFinished -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_notify_downloads_finished_subtitle),
                    isEnabled = display.notifyDownloadsFinished,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setNotifyDownloadsFinished(it) }
                )

                is InterfaceItem.NotifyBattery -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_notify_battery_subtitle),
                    isEnabled = display.notifyBattery,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setNotifyBattery(it) }
                )

                is InterfaceItem.NotifyCharging -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_notify_charging_subtitle),
                    isEnabled = display.notifyCharging,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setNotifyCharging(it) }
                )

                is InterfaceItem.NotifyControllers -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_notify_controllers_subtitle),
                    isEnabled = display.notifyControllers,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setNotifyControllers(it) }
                )

                is InterfaceItem.ControllerGrip -> NavigationPreference(
                    icon = Icons.Outlined.Gamepad,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_controller_grip_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToControllerGrip() } }
                )

                is InterfaceItem.HomeScreen -> NavigationPreference(
                    icon = Icons.Outlined.Home,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_home_screen_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToHomeScreen() } }
                )

                is InterfaceItem.LibraryView -> NavigationPreference(
                    icon = Icons.Outlined.GridView,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_library_view_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToLibraryView() } }
                )

                is InterfaceItem.BoxArt -> NavigationPreference(
                    icon = Icons.Outlined.Image,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_box_art_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToBoxArt() } }
                )

                is InterfaceItem.Presentation -> NavigationPreference(
                    icon = Icons.Outlined.Slideshow,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_interface_presentation_subtitle),
                    isFocused = isFocused(item),
                    onClick = { openFrom(item) { viewModel.navigateToPresentation() } }
                )

            }
    }
}

@Composable
private fun InterfaceSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
