package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal const val LIBRARY_SOURCE_ALL = "ALL"

internal data class LibraryLayoutState(
    val platformNames: List<String>
) {
    companion object {
        fun from(state: SettingsUiState) = LibraryLayoutState(
            platformNames = state.emulators.platforms
                .filter { it.platform.syncEnabled }
                .map { it.platform.getDisplayName() }
                .sorted()
        )
    }
}

internal sealed class LibraryItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val title: String) : LibraryItem(key, section)

    data object GridDensityItem : LibraryItem("libraryGridDensity", "layout")
    data object DefaultSort : LibraryItem("libraryDefaultSort", "defaults")
    data object InstalledFirst : LibraryItem("sortInstalledFirst", "defaults")
    data object FavoritesFirst : LibraryItem("sortFavoritesFirst", "defaults")
    data object DefaultPlatform : LibraryItem("libraryDefaultPlatform", "defaults")
    data object DefaultSource : LibraryItem("libraryDefaultSource", "defaults")

    companion object {
        val ALL: List<LibraryItem>
            get() = listOf(
                Header("libraryLayoutHeader", "layout", "Layout"),
                GridDensityItem,
                Header("libraryDefaultsHeader", "defaults", "Defaults"),
                DefaultSort, InstalledFirst, FavoritesFirst, DefaultPlatform, DefaultSource
            )
    }
}

private val libraryLayout = SettingsLayout<LibraryItem, LibraryLayoutState>(
    allItems = LibraryItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "layout" -> "Layout"
            "defaults" -> "Defaults"
            else -> null
        }
    }
)

internal fun libraryMaxFocusIndex(state: LibraryLayoutState): Int = libraryLayout.maxFocusIndex(state)

internal fun libraryItemAtFocusIndex(index: Int, state: LibraryLayoutState): LibraryItem? =
    libraryLayout.itemAtFocusIndex(index, state)

internal fun librarySections(state: LibraryLayoutState) = libraryLayout.buildSections(state)

/**
 * Sort labels carry the direction so one row expresses both without a second control.
 */
internal fun librarySortLabel(option: SortOption, descending: Boolean): String =
    "${option.label} ${if (descending) "v" else "^"}"

internal fun librarySortOptions(): List<String> =
    SortOption.entries.flatMap { option ->
        listOf(librarySortLabel(option, false), librarySortLabel(option, true))
    }

internal fun libraryPlatformOptions(state: LibraryLayoutState): List<String> =
    listOf("All Platforms") + state.platformNames

internal fun librarySourceOptions(): List<String> = listOf("All Games", "Playable", "Favorites")

internal fun librarySourceKeys(): List<String> = listOf(LIBRARY_SOURCE_ALL, "PLAYABLE", "FAVORITES")

@Composable
fun LibrarySection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val layoutState = remember(uiState.emulators.platforms) { LibraryLayoutState.from(uiState) }

    val visibleItems = remember(layoutState) { libraryLayout.visibleItems(layoutState) }
    val sections = remember(layoutState) { libraryLayout.buildSections(layoutState) }

    fun isFocused(item: LibraryItem): Boolean =
        uiState.focusedIndex == libraryLayout.focusIndexOf(item, layoutState)

    fun pickerToken(item: LibraryItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    val sortOption = SortOption.entries.firstOrNull { it.name == display.libraryDefaultSort }
        ?: SortOption.TITLE
    val sortDescending = display.libraryDefaultSortDescending ?: sortOption.defaultDescending
    val platformOptions = libraryPlatformOptions(layoutState)
    val platformIndex = (platformOptions.indexOf(display.libraryDefaultPlatform)).coerceAtLeast(0)
    val sourceIndex = librarySourceKeys().indexOf(display.libraryDefaultSource).coerceAtLeast(0)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { libraryLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is LibraryItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is LibraryItem.Header ->
                com.nendo.argosy.ui.screens.settings.components.SectionHeader(item.title)

            LibraryItem.GridDensityItem -> CyclePreference(
                title = "Grid Density",
                value = display.gridDensity.name.lowercase().replaceFirstChar { it.uppercase() },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleGridDensity(1) },
                onPrev = { viewModel.cycleGridDensity(-1) },
                options = remember {
                    GridDensity.entries.map { d -> d.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                },
                onSelect = { viewModel.setGridDensity(GridDensity.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.DefaultSort -> CyclePreference(
                title = "Default Sort",
                value = librarySortLabel(sortOption, sortDescending),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultSort(1) },
                onPrev = { viewModel.cycleLibraryDefaultSort(-1) },
                options = remember { librarySortOptions() },
                onSelect = { viewModel.setLibraryDefaultSortIndex(it) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.InstalledFirst -> SwitchPreference(
                title = "Installed First",
                subtitle = "Group games you have downloaded ahead of the rest",
                isEnabled = display.sortInstalledFirst,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSortInstalledFirst(!display.sortInstalledFirst) }
            )

            LibraryItem.FavoritesFirst -> SwitchPreference(
                title = "Favorites First",
                subtitle = "Group your favorites ahead of the rest",
                isEnabled = display.sortFavoritesFirst,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSortFavoritesFirst(!display.sortFavoritesFirst) }
            )

            LibraryItem.DefaultPlatform -> CyclePreference(
                title = "Default Platform",
                value = platformOptions.getOrElse(platformIndex) { "All Platforms" },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultPlatform(1, platformOptions) },
                onPrev = { viewModel.cycleLibraryDefaultPlatform(-1, platformOptions) },
                options = platformOptions,
                onSelect = { viewModel.setLibraryDefaultPlatform(if (it == 0) "" else platformOptions[it]) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.DefaultSource -> CyclePreference(
                title = "Default Filter",
                value = librarySourceOptions().getOrElse(sourceIndex) { "All Games" },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultSource(1) },
                onPrev = { viewModel.cycleLibraryDefaultSource(-1) },
                options = remember { librarySourceOptions() },
                onSelect = { viewModel.setLibraryDefaultSource(librarySourceKeys()[it]) },
                pickerRequestToken = pickerToken(item)
            )
        }
    }
}
