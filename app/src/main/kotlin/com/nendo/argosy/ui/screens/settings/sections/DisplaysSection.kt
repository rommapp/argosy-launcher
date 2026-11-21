package com.nendo.argosy.ui.screens.settings.sections

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.hardware.DisplayBadgeSize
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.LaunchTargetScreens
import com.nendo.argosy.ui.screens.settings.NavigationMenuChoices
import com.nendo.argosy.domain.model.NavigationMenuPin
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.screenRole
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens

internal data class DisplaysLayoutState(
    val display: DisplayState,
    val hasPhysicalSecondaryDisplay: Boolean = false,
    val dualScreenEnabled: Boolean = false
) {
    companion object {
        fun from(state: SettingsUiState) = DisplaysLayoutState(
            display = state.display,
            hasPhysicalSecondaryDisplay = state.display.hasPhysicalSecondaryDisplay,
            dualScreenEnabled = state.display.dualScreenEnabled
        )
    }
}

internal sealed class DisplaysItem(
    val key: String,
    val section: String,
    val visibleWhen: (DisplaysLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(
        key: String,
        section: String,
        val titleRes: Int,
        visibleWhen: (DisplaysLayoutState) -> Boolean = { true }
    ) : DisplaysItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (DisplaysLayoutState) -> Boolean = { true })
        : DisplaysItem(key, section, visibleWhen)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (DisplaysLayoutState) -> Boolean = { true }
    ) : DisplaysItem(key, section, visibleWhen), SearchableSetting

    data object ScreenDimmer :
        Row("screenDimmer", "screenSafety", R.string.settings_displays_screen_dimmer_title)
    data object DimAfter : Row("dimAfter", "screenSafety", R.string.settings_displays_dim_after_title)
    data object DimLevel : Row("dimLevel", "screenSafety", R.string.settings_displays_dim_level_title)
    data object LidSleep : Row(
        key = "lidSleep",
        section = "screenSafety",
        labelRes = R.string.settings_displays_lid_sleep_title,
        visibleWhen = { it.display.lidSleepSupported }
    )

    data object DualScreenEnabled :
        Row("dualScreenEnabled", "displays", R.string.settings_displays_dual_screen_title)
    data object DisplayRoles : Row(
        key = "displayRoles",
        section = "displays",
        labelRes = R.string.settings_displays_display_roles_title,
        visibleWhen = { it.dualScreenEnabled && !it.display.secondaryDisplayUnsupported }
    )
    data object NavigationMenu : Row(
        key = "navigationMenu",
        section = "displays",
        labelRes = R.string.settings_displays_navigation_menu_title,
        visibleWhen = { it.dualScreenEnabled && !it.display.secondaryDisplayUnsupported }
    )
    data object DualScreenMatchSize : Row(
        key = "dualScreenMatchSize",
        section = "displays",
        labelRes = R.string.settings_displays_match_screen_size_title,
        visibleWhen = { it.dualScreenEnabled && it.display.dualScreenMatchSizeAvailable }
    )
    data object ScreenLayout : Row(
        key = "screenLayout",
        section = "displays",
        labelRes = R.string.settings_displays_screens_title,
        visibleWhen = { it.hasPhysicalSecondaryDisplay }
    )
    data object AmbientLedSettings : Row(
        key = "ambientLedSettings",
        section = "displays",
        labelRes = R.string.settings_displays_ambient_led_title,
        visibleWhen = { it.display.ambientLedAvailable }
    )

    data object GamesTarget : Row(
        key = "gamesTarget",
        section = DISPLAY_TARGETS_SECTION,
        labelRes = R.string.settings_displays_games_target_title,
        visibleWhen = { it.hasPhysicalSecondaryDisplay }
    )
    data object AppsTarget : Row(
        key = "appsTarget",
        section = DISPLAY_TARGETS_SECTION,
        labelRes = R.string.settings_displays_apps_target_title,
        visibleWhen = { it.hasPhysicalSecondaryDisplay }
    )
    data object ByPlatformAndApp : Row(
        key = "byPlatformAndApp",
        section = DISPLAY_TARGETS_SECTION,
        labelRes = R.string.settings_displays_by_platform_and_app_title,
        visibleWhen = { it.hasPhysicalSecondaryDisplay }
    )

    companion object {
        private val ScreenSafetyHeader =
            Header("screenSafetyHeader", "screenSafety", R.string.settings_displays_section_screen_safety)
        private val DisplaysSpacer = SectionSpacer("displaysSpacer", "displays")
        private val DisplaysHeader =
            Header("displaysHeader", "displays", R.string.settings_displays_section_displays)
        private val DisplayTargetsSpacer = SectionSpacer(
            "displayTargetsSpacer",
            DISPLAY_TARGETS_SECTION,
            visibleWhen = { it.hasPhysicalSecondaryDisplay }
        )
        private val DisplayTargetsHeader = Header(
            "displayTargetsHeader",
            DISPLAY_TARGETS_SECTION,
            R.string.settings_displays_section_display_targets,
            visibleWhen = { it.hasPhysicalSecondaryDisplay }
        )

        val ALL: List<DisplaysItem>
            get() = listOf(
                ScreenSafetyHeader,
                ScreenDimmer, DimAfter, DimLevel, LidSleep,
                DisplaysSpacer, DisplaysHeader,
                DualScreenEnabled, DisplayRoles, NavigationMenu, DualScreenMatchSize, ScreenLayout,
                AmbientLedSettings,
                DisplayTargetsSpacer, DisplayTargetsHeader,
                GamesTarget, AppsTarget, ByPlatformAndApp
            )
    }
}

private const val DISPLAY_TARGETS_SECTION = "displayTargets"

internal val displaysLayout = SettingsLayout<DisplaysItem, DisplaysLayoutState>(
    allItems = DisplaysItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "screenSafety" -> R.string.settings_displays_section_screen_safety
            "displays" -> R.string.settings_displays_section_displays
            DISPLAY_TARGETS_SECTION -> R.string.settings_displays_section_display_targets
            else -> null
        }
    }
)

private fun displayRoleLabelRes(mode: DisplayRoleOverride): Int = when (mode) {
    DisplayRoleOverride.AUTO -> R.string.settings_displays_display_roles_auto
    DisplayRoleOverride.STANDARD -> R.string.settings_displays_display_roles_standard
    DisplayRoleOverride.SWAPPED -> R.string.settings_displays_display_roles_swapped
}

private fun launchTargetLabel(context: Context, target: EmulatorDisplayTarget, screens: LaunchTargetScreens): String {
    val role = context.getString(screenRoleTitleRes(target.screenRole))
    val number = screens.numberFor(target) ?: return role
    return context.getString(R.string.settings_displays_launch_target_value, role, number)
}

private fun navigationMenuLabel(context: Context, pin: NavigationMenuPin, choices: NavigationMenuChoices): String =
    when (pin) {
        NavigationMenuPin.Presentation -> context.getString(R.string.settings_displays_navigation_menu_presentation)
        is NavigationMenuPin.Panel -> choices.numberOf(pin)
            ?.let { context.getString(R.string.settings_displays_navigation_menu_screen, it) }
            ?: context.getString(R.string.settings_displays_navigation_menu_control)
        NavigationMenuPin.Control -> context.getString(R.string.settings_displays_navigation_menu_control)
    }

@Composable
private fun NavigationMenuPreference(
    title: String,
    choices: NavigationMenuChoices,
    isFocused: Boolean,
    onCycle: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    pickerRequestToken: Int
) {
    val context = LocalContext.current
    val value = remember(choices, context) { navigationMenuLabel(context, choices.pin, choices) }
    val options = remember(choices, context) { choices.options.map { navigationMenuLabel(context, it, choices) } }
    CyclePreference(
        title = title,
        value = value,
        subtitle = stringResource(R.string.settings_displays_navigation_menu_subtitle),
        isFocused = isFocused,
        onClick = { onCycle(1) },
        onPrev = { onCycle(-1) },
        options = options,
        onSelect = onSelect,
        pickerRequestToken = pickerRequestToken,
        onPickerVisibilityChange = { visible ->
            val manager = DualScreenManagerHolder.instance
            if (visible) manager?.showScreenNumbers(DisplayBadgeSize.SMALL) else manager?.hideScreenNumbers()
        }
    )
}

@Composable
private fun LaunchTargetPreference(
    title: String,
    stored: EmulatorDisplayTarget,
    screens: LaunchTargetScreens,
    isFocused: Boolean,
    onCycle: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    pickerRequestToken: Int
) {
    val context = LocalContext.current
    val shown = screens.shown(stored)
    val value = remember(shown, screens, context) { launchTargetLabel(context, shown, screens) }
    val noAppScreenSubtitle = remember(shown, screens, context) {
        if (screens.lacksAppScreen(shown)) {
            context.getString(
                R.string.settings_displays_launch_target_no_app_screen,
                launchTargetLabel(context, EmulatorDisplayTarget.PRESENTATION, screens)
            )
        } else {
            null
        }
    }
    val options = remember(screens, context) { screens.options.map { launchTargetLabel(context, it, screens) } }
    CyclePreference(
        title = title,
        value = value,
        subtitle = noAppScreenSubtitle,
        isFocused = isFocused,
        onClick = { onCycle(1) },
        onPrev = { onCycle(-1) },
        options = options,
        onSelect = onSelect,
        pickerRequestToken = pickerRequestToken,
        onPickerVisibilityChange = { visible ->
            val manager = DualScreenManagerHolder.instance
            if (visible) manager?.showScreenNumbers(DisplayBadgeSize.SMALL) else manager?.hideScreenNumbers()
        }
    )
}

internal fun displaysMaxFocusIndex(state: DisplaysLayoutState): Int = displaysLayout.maxFocusIndex(state)

internal fun displaysItemAtFocusIndex(index: Int, state: DisplaysLayoutState): DisplaysItem? =
    displaysLayout.itemAtFocusIndex(index, state)

internal fun displaysSections(state: DisplaysLayoutState) = displaysLayout.buildSections(state)

internal fun displaysFocusIndexOf(item: DisplaysItem, state: DisplaysLayoutState): Int =
    displaysLayout.focusIndexOf(item, state)

@Composable
fun DisplaysSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val storage = uiState.storage
    val context = LocalContext.current

    val layoutState = remember(
        display.ambientLedAvailable,
        display.hasPhysicalSecondaryDisplay,
        display.dualScreenEnabled,
        display.secondaryDisplayUnsupported,
        display.lidSleepSupported,
        display.lidSleepEnabled
    ) {
        DisplaysLayoutState(
            display,
            display.hasPhysicalSecondaryDisplay,
            display.dualScreenEnabled
        )
    }

    val visibleItems = remember(layoutState) {
        displaysLayout.visibleItems(layoutState)
    }
    val sections = remember(layoutState, context) {
        displaysLayout.buildSections(layoutState, context)
    }

    fun isFocused(item: DisplaysItem): Boolean =
        uiState.focusedIndex == displaysLayout.focusIndexOf(item, layoutState)

    fun openFrom(item: DisplaysItem, enter: () -> Unit) {
        viewModel.setFocusIndex(displaysLayout.focusIndexOf(item, layoutState))
        enter()
    }

    fun pickerToken(item: DisplaysItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { displaysLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is DisplaysItem.SectionSpacer },
        isHeader = { it is DisplaysItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is DisplaysItem.Header -> DisplaysSectionHeader(stringResource(item.titleRes))
            is DisplaysItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            is DisplaysItem.ScreenDimmer -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_displays_screen_dimmer_subtitle),
                isEnabled = storage.screenDimmerEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleScreenDimmer() }
            )

            is DisplaysItem.DimAfter -> CyclePreference(
                title = stringResource(item.labelRes),
                value = pluralStringResource(
                    R.plurals.settings_displays_dim_after_value,
                    storage.screenDimmerTimeoutMinutes,
                    storage.screenDimmerTimeoutMinutes
                ),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleScreenDimmerTimeout() },
                onPrev = { viewModel.adjustScreenDimmerTimeout(-1) },
                options = remember(context) {
                    (1..5).map {
                        context.resources.getQuantityString(
                            R.plurals.settings_displays_dim_after_value,
                            it,
                            it
                        )
                    }
                },
                onSelect = { viewModel.adjustScreenDimmerTimeout((it + 1) - storage.screenDimmerTimeoutMinutes) },
                pickerRequestToken = pickerToken(item)
            )

            is DisplaysItem.DimLevel -> SliderPreference(
                title = stringResource(item.labelRes),
                value = storage.screenDimmerLevel,
                minValue = 40,
                maxValue = 70,
                isFocused = isFocused(item),
                step = 10,
                onAdjust = { viewModel.adjustScreenDimmerLevel(if (it < 0) -1 else 1) }
            )

            is DisplaysItem.LidSleep -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_displays_lid_sleep_subtitle),
                isEnabled = display.lidSleepEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setLidSleepEnabled(it) }
            )

            is DisplaysItem.DualScreenEnabled -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = if (display.secondaryDisplayUnsupported) {
                    stringResource(R.string.settings_displays_dual_screen_subtitle_unsupported)
                } else {
                    stringResource(R.string.settings_displays_dual_screen_subtitle)
                },
                isEnabled = display.dualScreenEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setDualScreenEnabled(it) }
            )


            is DisplaysItem.DisplayRoles -> CyclePreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_displays_display_roles_subtitle),
                value = stringResource(displayRoleLabelRes(display.displayRoleOverride)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleDisplayRoleOverride() },
                onPrev = { viewModel.cycleDisplayRoleOverride(-1) },
                options = remember(context) {
                    DisplayRoleOverride.entries.map { context.getString(displayRoleLabelRes(it)) }
                },
                onSelect = { viewModel.setDisplayRoleOverride(DisplayRoleOverride.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            is DisplaysItem.NavigationMenu -> NavigationMenuPreference(
                title = stringResource(item.labelRes),
                choices = display.navigationMenu,
                isFocused = isFocused(item),
                onCycle = { viewModel.cycleNavigationMenu(it) },
                onSelect = { viewModel.selectNavigationMenu(it) },
                pickerRequestToken = pickerToken(item)
            )

            is DisplaysItem.DualScreenMatchSize -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_displays_match_screen_size_subtitle),
                isEnabled = display.dualScreenMatchSize,
                isFocused = isFocused(item),
                onToggle = { viewModel.setDualScreenMatchSize(it) }
            )

            is DisplaysItem.ScreenLayout -> NavigationPreference(
                icon = Icons.Outlined.Monitor,
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_displays_screens_subtitle),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToScreens() } }
            )

            is DisplaysItem.AmbientLedSettings -> NavigationPreference(
                icon = Icons.Outlined.WbTwilight,
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_displays_ambient_led_subtitle),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToAmbientLed() } }
            )

            is DisplaysItem.GamesTarget -> LaunchTargetPreference(
                title = stringResource(item.labelRes),
                stored = display.gamesLaunchTarget,
                screens = display.launchTargetScreens,
                isFocused = isFocused(item),
                onCycle = { viewModel.cycleGamesLaunchTarget(it) },
                onSelect = { viewModel.selectGamesLaunchTarget(it) },
                pickerRequestToken = pickerToken(item)
            )

            is DisplaysItem.AppsTarget -> LaunchTargetPreference(
                title = stringResource(item.labelRes),
                stored = display.appsLaunchTarget,
                screens = display.launchTargetScreens,
                isFocused = isFocused(item),
                onCycle = { viewModel.cycleAppsLaunchTarget(it) },
                onSelect = { viewModel.selectAppsLaunchTarget(it) },
                pickerRequestToken = pickerToken(item)
            )

            is DisplaysItem.ByPlatformAndApp -> NavigationPreference(
                icon = Icons.Outlined.Tv,
                title = stringResource(item.labelRes),
                subtitle = if (display.launchTargetSetCount == 0) {
                    stringResource(R.string.settings_displays_by_platform_and_app_none)
                } else {
                    pluralStringResource(
                        R.plurals.settings_displays_by_platform_and_app_count,
                        display.launchTargetSetCount,
                        display.launchTargetSetCount
                    )
                },
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToLaunchTargets() } }
            )
        }
    }
}

@Composable
private fun DisplaysSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
