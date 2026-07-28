package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
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
    val isFocusable: Boolean get() = when (this) {
        is Header -> false
        else -> true
    }

    class Header(
        key: String,
        section: String,
        val title: String,
        visibleWhen: (InterfaceLayoutState) -> Boolean = { true }
    ) : InterfaceItem(key, section, visibleWhen)

    data object GridDensity : InterfaceItem("gridDensity", "layout")
    data object UiScale : InterfaceItem("uiScale", "layout")
    data object HomeScreen : InterfaceItem("homeScreen", "layout")

    companion object {
        private val LayoutHeader = Header("layoutHeader", "layout", "Layout")

        val ALL: List<InterfaceItem> = listOf(
            LayoutHeader,
            GridDensity, UiScale, HomeScreen
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

    val layoutState = remember(display) { InterfaceLayoutState(display) }

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
        isNavItem = { false },
        isHeader = { it is InterfaceItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
            when (item) {
                is InterfaceItem.Header -> InterfaceSectionHeader(item.title)

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
