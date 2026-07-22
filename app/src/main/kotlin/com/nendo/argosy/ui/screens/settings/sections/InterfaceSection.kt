package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal data class InterfaceLayoutState(
    val display: DisplayState,
    val hasSecondaryDisplay: Boolean = false,
    val hasPhysicalSecondaryDisplay: Boolean = false,
    val dualScreenEnabled: Boolean = false
) {
    companion object {
        fun from(state: SettingsUiState) = InterfaceLayoutState(
            display = state.display,
            hasSecondaryDisplay = state.display.hasSecondaryDisplay,
            hasPhysicalSecondaryDisplay = state.display.hasPhysicalSecondaryDisplay,
            dualScreenEnabled = state.display.dualScreenEnabled
        )
    }
}

internal sealed class InterfaceItem(
    val key: String,
    val section: String,
    val visibleWhen: (InterfaceLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(
        key: String,
        section: String,
        val title: String,
        visibleWhen: (InterfaceLayoutState) -> Boolean = { true }
    ) : InterfaceItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (InterfaceLayoutState) -> Boolean = { true })
        : InterfaceItem(key, section, visibleWhen)

    data object GridDensity : InterfaceItem("gridDensity", "layout")
    data object UiScale : InterfaceItem("uiScale", "layout")
    data object HomeScreen : InterfaceItem("homeScreen", "layout")

    data object ScreenDimmer : InterfaceItem("screenDimmer", "screenSafety")
    data object DimAfter : InterfaceItem("dimAfter", "screenSafety")
    data object DimLevel : InterfaceItem("dimLevel", "screenSafety")

    data object DualScreenEnabled : InterfaceItem("dualScreenEnabled", "displays")
    data object DisplayRoles : InterfaceItem(
        key = "displayRoles",
        section = "displays",
        visibleWhen = { it.dualScreenEnabled }
    )
    data object AmbientLedSettings : InterfaceItem(
        key = "ambientLedSettings",
        section = "displays",
        visibleWhen = { it.display.ambientLedAvailable }
    )

    companion object {
        private val LayoutHeader = Header("layoutHeader", "layout", "Layout")
        private val ScreenSafetySpacer = SectionSpacer("screenSafetySpacer", "screenSafety")
        private val ScreenSafetyHeader = Header("screenSafetyHeader", "screenSafety", "Screen Safety")
        private val DisplaysSpacer = SectionSpacer("displaysSpacer", "displays")
        private val DisplaysHeader = Header("displaysHeader", "displays", "Displays")

        val ALL: List<InterfaceItem> = listOf(
            LayoutHeader,
            GridDensity, UiScale, HomeScreen,
            ScreenSafetySpacer, ScreenSafetyHeader,
            ScreenDimmer, DimAfter, DimLevel,
            DisplaysSpacer, DisplaysHeader,
            DualScreenEnabled, DisplayRoles, AmbientLedSettings
        )
    }
}

private val interfaceLayout = SettingsLayout<InterfaceItem, InterfaceLayoutState>(
    allItems = InterfaceItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "layout" -> "Layout"
            "screenSafety" -> "Screen Safety"
            "displays" -> "Displays"
            else -> null
        }
    }
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
    val storage = uiState.storage

    val layoutState = remember(
        display.ambientLedAvailable,
        display.hasSecondaryDisplay,
        display.hasPhysicalSecondaryDisplay,
        display.dualScreenEnabled
    ) {
        InterfaceLayoutState(display, display.hasSecondaryDisplay, display.hasPhysicalSecondaryDisplay, display.dualScreenEnabled)
    }

    val visibleItems = remember(layoutState) {
        interfaceLayout.visibleItems(layoutState)
    }
    val sections = remember(layoutState) {
        interfaceLayout.buildSections(layoutState)
    }

    fun isFocused(item: InterfaceItem): Boolean =
        uiState.focusedIndex == interfaceLayout.focusIndexOf(item, layoutState)

    fun pickerToken(item: InterfaceItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { interfaceLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is InterfaceItem.SectionSpacer },
        isHeader = { it is InterfaceItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
            when (item) {
                is InterfaceItem.Header -> InterfaceSectionHeader(item.title)
                is InterfaceItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                InterfaceItem.GridDensity -> CyclePreference(
                    title = "Grid Density",
                    value = display.gridDensity.name.lowercase().replaceFirstChar { it.uppercase() },
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleGridDensity(1) },
                    onPrev = { viewModel.cycleGridDensity(-1) },
                    options = remember { GridDensity.entries.map { d -> d.name.lowercase().replaceFirstChar { c -> c.uppercase() } } },
                    onSelect = { viewModel.setGridDensity(GridDensity.entries[it]) },
                    pickerRequestToken = pickerToken(item)
                )

                InterfaceItem.UiScale -> SliderPreference(
                    title = "UI Scale",
                    value = display.uiScale,
                    minValue = 50,
                    maxValue = 150,
                    isFocused = isFocused(item),
                    step = 5,
                    suffix = "%",
                    onClick = { viewModel.adjustUiScale(5) }
                )

                InterfaceItem.HomeScreen -> NavigationPreference(
                    icon = Icons.Outlined.Home,
                    title = "Home Screen",
                    subtitle = "Background and footer settings",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToHomeScreen() }
                )

                InterfaceItem.ScreenDimmer -> SwitchPreference(
                    title = "Screen Dimmer",
                    subtitle = "Dims screen after inactivity to prevent burn-in",
                    isEnabled = storage.screenDimmerEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleScreenDimmer() }
                )

                InterfaceItem.DimAfter -> CyclePreference(
                    title = "Dim After",
                    value = "${storage.screenDimmerTimeoutMinutes} min",
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleScreenDimmerTimeout() },
                    onPrev = { viewModel.adjustScreenDimmerTimeout(-1) },
                    options = remember { (1..5).map { "$it min" } },
                    onSelect = { viewModel.adjustScreenDimmerTimeout((it + 1) - storage.screenDimmerTimeoutMinutes) },
                    pickerRequestToken = pickerToken(item)
                )

                InterfaceItem.DimLevel -> SliderPreference(
                    title = "Dim Level",
                    value = storage.screenDimmerLevel,
                    minValue = 40,
                    maxValue = 70,
                    isFocused = isFocused(item),
                    step = 10,
                    onClick = { viewModel.cycleScreenDimmerLevel() }
                )

                InterfaceItem.DualScreenEnabled -> SwitchPreference(
                    title = "Enable Dual-screen Mode",
                    subtitle = "Use secondary display as companion screen",
                    isEnabled = display.dualScreenEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setDualScreenEnabled(it) }
                )

                InterfaceItem.DisplayRoles -> CyclePreference(
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

                InterfaceItem.AmbientLedSettings -> NavigationPreference(
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
private fun InterfaceSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
