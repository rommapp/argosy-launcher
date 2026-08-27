package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal data class DisplaysLayoutState(
    val display: DisplayState,
    val hasSecondaryDisplay: Boolean = false,
    val hasPhysicalSecondaryDisplay: Boolean = false,
    val dualScreenEnabled: Boolean = false
) {
    companion object {
        fun from(state: SettingsUiState) = DisplaysLayoutState(
            display = state.display,
            hasSecondaryDisplay = state.display.hasSecondaryDisplay,
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
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(
        key: String,
        section: String,
        val titleRes: Int,
        visibleWhen: (DisplaysLayoutState) -> Boolean = { true }
    ) : DisplaysItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (DisplaysLayoutState) -> Boolean = { true })
        : DisplaysItem(key, section, visibleWhen)

    data object ScreenDimmer : DisplaysItem("screenDimmer", "screenSafety")
    data object DimAfter : DisplaysItem("dimAfter", "screenSafety")
    data object DimLevel : DisplaysItem("dimLevel", "screenSafety")

    data object DualScreenEnabled : DisplaysItem("dualScreenEnabled", "displays")
    data object DisplayRoles : DisplaysItem(
        key = "displayRoles",
        section = "displays",
        visibleWhen = { it.dualScreenEnabled && !it.display.secondaryDisplayUnsupported }
    )
    data object AmbientLedSettings : DisplaysItem(
        key = "ambientLedSettings",
        section = "displays",
        visibleWhen = { it.display.ambientLedAvailable }
    )

    companion object {
        private val ScreenSafetyHeader =
            Header("screenSafetyHeader", "screenSafety", R.string.settings_displays_section_screen_safety)
        private val DisplaysSpacer = SectionSpacer("displaysSpacer", "displays")
        private val DisplaysHeader =
            Header("displaysHeader", "displays", R.string.settings_displays_section_displays)

        val ALL: List<DisplaysItem>
            get() = listOf(
                ScreenSafetyHeader,
                ScreenDimmer, DimAfter, DimLevel,
                DisplaysSpacer, DisplaysHeader,
                DualScreenEnabled, DisplayRoles, AmbientLedSettings
            )
    }
}

private val displaysLayout = SettingsLayout<DisplaysItem, DisplaysLayoutState>(
    allItems = DisplaysItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "screenSafety" -> R.string.settings_displays_section_screen_safety
            "displays" -> R.string.settings_displays_section_displays
            else -> null
        }
    }
)

private fun displayRoleLabelRes(mode: DisplayRoleOverride): Int = when (mode) {
    DisplayRoleOverride.AUTO -> R.string.settings_displays_display_roles_auto
    DisplayRoleOverride.STANDARD -> R.string.settings_displays_display_roles_standard
    DisplayRoleOverride.SWAPPED -> R.string.settings_displays_display_roles_swapped
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
        display.hasSecondaryDisplay,
        display.hasPhysicalSecondaryDisplay,
        display.dualScreenEnabled,
        display.secondaryDisplayUnsupported
    ) {
        DisplaysLayoutState(
            display,
            display.hasSecondaryDisplay,
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

            DisplaysItem.ScreenDimmer -> SwitchPreference(
                title = stringResource(R.string.settings_displays_screen_dimmer_title),
                subtitle = stringResource(R.string.settings_displays_screen_dimmer_subtitle),
                isEnabled = storage.screenDimmerEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleScreenDimmer() }
            )

            DisplaysItem.DimAfter -> CyclePreference(
                title = stringResource(R.string.settings_displays_dim_after_title),
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

            DisplaysItem.DimLevel -> SliderPreference(
                title = stringResource(R.string.settings_displays_dim_level_title),
                value = storage.screenDimmerLevel,
                minValue = 40,
                maxValue = 70,
                isFocused = isFocused(item),
                step = 10,
                onAdjust = { viewModel.adjustScreenDimmerLevel(if (it < 0) -1 else 1) }
            )

            DisplaysItem.DualScreenEnabled -> SwitchPreference(
                title = stringResource(R.string.settings_displays_dual_screen_title),
                subtitle = if (display.secondaryDisplayUnsupported) {
                    stringResource(R.string.settings_displays_dual_screen_subtitle_unsupported)
                } else {
                    stringResource(R.string.settings_displays_dual_screen_subtitle)
                },
                isEnabled = display.dualScreenEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setDualScreenEnabled(it) }
            )

            DisplaysItem.DisplayRoles -> CyclePreference(
                title = stringResource(R.string.settings_displays_display_roles_title),
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

            DisplaysItem.AmbientLedSettings -> NavigationPreference(
                icon = Icons.Outlined.WbTwilight,
                title = stringResource(R.string.settings_displays_ambient_led_title),
                subtitle = stringResource(R.string.settings_displays_ambient_led_subtitle),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToAmbientLed() } }
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
