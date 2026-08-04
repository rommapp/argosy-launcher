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
        val title: String,
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
        private val ScreenSafetyHeader = Header("screenSafetyHeader", "screenSafety", "Screen Safety")
        private val DisplaysSpacer = SectionSpacer("displaysSpacer", "displays")
        private val DisplaysHeader = Header("displaysHeader", "displays", "Displays")

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
    sectionTitle = {
        when (it) {
            "screenSafety" -> "Screen Safety"
            "displays" -> "Displays"
            else -> null
        }
    }
)

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
    val sections = remember(layoutState) {
        displaysLayout.buildSections(layoutState)
    }

    fun isFocused(item: DisplaysItem): Boolean =
        uiState.focusedIndex == displaysLayout.focusIndexOf(item, layoutState)

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
            is DisplaysItem.Header -> DisplaysSectionHeader(item.title)
            is DisplaysItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            DisplaysItem.ScreenDimmer -> SwitchPreference(
                title = "Screen Dimmer",
                subtitle = "Dims screen after inactivity to prevent burn-in",
                isEnabled = storage.screenDimmerEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleScreenDimmer() }
            )

            DisplaysItem.DimAfter -> CyclePreference(
                title = "Dim After",
                value = "${storage.screenDimmerTimeoutMinutes} min",
                isFocused = isFocused(item),
                onClick = { viewModel.cycleScreenDimmerTimeout() },
                onPrev = { viewModel.adjustScreenDimmerTimeout(-1) },
                options = remember { (1..5).map { "$it min" } },
                onSelect = { viewModel.adjustScreenDimmerTimeout((it + 1) - storage.screenDimmerTimeoutMinutes) },
                pickerRequestToken = pickerToken(item)
            )

            DisplaysItem.DimLevel -> SliderPreference(
                title = "Dim Level",
                value = storage.screenDimmerLevel,
                minValue = 40,
                maxValue = 70,
                isFocused = isFocused(item),
                step = 10,
                onAdjust = { viewModel.adjustScreenDimmerLevel(if (it < 0) -1 else 1) }
            )

            DisplaysItem.DualScreenEnabled -> SwitchPreference(
                title = "Enable Dual-screen Mode",
                subtitle = if (display.secondaryDisplayUnsupported) {
                    "This system does not allow a companion app on the secondary display; toggle off and on to retry"
                } else {
                    "Use secondary display as companion screen"
                },
                isEnabled = display.dualScreenEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setDualScreenEnabled(it) }
            )

            DisplaysItem.DisplayRoles -> CyclePreference(
                title = "Display Roles",
                subtitle = "Which physical display is the main vs companion screen; Swapped flips top and bottom",
                value = display.displayRoleOverride.displayName,
                isFocused = isFocused(item),
                onClick = { viewModel.cycleDisplayRoleOverride() },
                onPrev = { viewModel.cycleDisplayRoleOverride(-1) },
                options = remember { DisplayRoleOverride.entries.map { it.displayName } },
                onSelect = { viewModel.setDisplayRoleOverride(DisplayRoleOverride.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            DisplaysItem.AmbientLedSettings -> NavigationPreference(
                icon = Icons.Outlined.WbTwilight,
                title = "LED Control",
                subtitle = "Thumbstick LED colors and effects",
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToAmbientLed() }
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
