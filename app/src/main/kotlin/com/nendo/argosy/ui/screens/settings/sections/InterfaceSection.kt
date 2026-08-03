package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
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

    data object UiScale : InterfaceItem("uiScale", "layout")
    data object CompactFooter : InterfaceItem("compactFooter", "layout")
    data object HomeScreen : InterfaceItem("homeScreen", "layout")
    data object LibraryView : InterfaceItem("libraryView", "layout")
    data object BoxArt : InterfaceItem("boxArt", "layout")

    companion object {
        /**
         * A getter, not a stored list. As a `val` this is a static of the sealed class
         * itself, so it is built during that class's initialization, which is the same
         * initialization the `data object` entries above are waiting on: whichever
         * entries have not been constructed yet land in the list as nulls.
         */
        val ALL: List<InterfaceItem>
            get() = listOf(UiScale, CompactFooter, HomeScreen, LibraryView, BoxArt)
    }
}

private val interfaceLayout = SettingsLayout<InterfaceItem, InterfaceLayoutState>(
    allItems = InterfaceItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = { null }
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
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshUsageStatsPermission()
        }
    }

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

                InterfaceItem.UiScale -> SliderPreference(
                    title = "UI Scale",
                    value = display.uiScale,
                    minValue = 50,
                    maxValue = 150,
                    isFocused = isFocused(item),
                    step = 5,
                    suffix = "%",
                    onAdjust = { viewModel.adjustUiScale(it) }
                )

                InterfaceItem.CompactFooter -> SwitchPreference(
                    title = "Compact Footer",
                    subtitle = "Use a thinner control guide bar",
                    isEnabled = display.compactFooter,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setCompactFooter(it) }
                )

                InterfaceItem.HomeScreen -> NavigationPreference(
                    icon = Icons.Outlined.Home,
                    title = "Home Screen",
                    subtitle = "Layout, content and background",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToHomeScreen() }
                )

                InterfaceItem.LibraryView -> NavigationPreference(
                    icon = Icons.Outlined.GridView,
                    title = "Library",
                    subtitle = "Grid density and default filters",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToLibraryView() }
                )

                InterfaceItem.BoxArt -> NavigationPreference(
                    icon = Icons.Outlined.Image,
                    title = "Box Art",
                    subtitle = "Cover shape, borders and effects",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToBoxArt() }
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
