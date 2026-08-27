package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
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
import com.nendo.argosy.ui.common.labelRes
import androidx.compose.ui.platform.LocalContext

internal const val LIBRARY_SOURCE_ALL = "ALL"

/**
 * Head of the stored default-platform list, matched back by index against the saved preference,
 * so it stays a value rather than copy.
 */
internal const val LIBRARY_PLATFORM_ALL = "All Platforms"

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

    class Header(key: String, section: String, val titleRes: Int) : LibraryItem(key, section)

    data object GridDensityItem : LibraryItem("libraryGridDensity", "layout")
    data object DefaultSort : LibraryItem("libraryDefaultSort", "defaults")
    data object InstalledFirst : LibraryItem("sortInstalledFirst", "defaults")
    data object FavoritesFirst : LibraryItem("sortFavoritesFirst", "defaults")
    data object DefaultPlatform : LibraryItem("libraryDefaultPlatform", "defaults")
    data object DefaultSource : LibraryItem("libraryDefaultSource", "defaults")

    companion object {
        val ALL: List<LibraryItem>
            get() = listOf(
                Header("libraryLayoutHeader", "layout", R.string.settings_library_section_layout),
                GridDensityItem,
                Header("libraryDefaultsHeader", "defaults", R.string.settings_library_section_defaults),
                DefaultSort, InstalledFirst, FavoritesFirst, DefaultPlatform, DefaultSource
            )
    }
}

private val libraryLayout = SettingsLayout<LibraryItem, LibraryLayoutState>(
    allItems = LibraryItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "layout" -> R.string.settings_library_section_layout
            "defaults" -> R.string.settings_library_section_defaults
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
internal fun librarySortLabel(
    context: android.content.Context,
    option: SortOption,
    descending: Boolean
): String = context.getString(
    if (descending) {
        R.string.settings_library_default_sort_value_descending
    } else {
        R.string.settings_library_default_sort_value_ascending
    },
    context.getString(option.labelRes)
)

internal fun librarySortOptions(context: android.content.Context): List<String> =
    SortOption.entries.flatMap { option ->
        listOf(librarySortLabel(context, option, false), librarySortLabel(context, option, true))
    }

internal fun libraryPlatformOptions(state: LibraryLayoutState): List<String> =
    listOf(LIBRARY_PLATFORM_ALL) + state.platformNames

internal fun librarySourceOptions(context: android.content.Context): List<String> = listOf(
    context.getString(R.string.source_filter_all),
    context.getString(R.string.source_filter_playable),
    context.getString(R.string.source_filter_favorites)
)

private fun gridDensityLabelRes(density: GridDensity): Int = when (density) {
    GridDensity.COMPACT -> R.string.settings_library_grid_density_compact
    GridDensity.NORMAL -> R.string.settings_library_grid_density_normal
    GridDensity.SPACIOUS -> R.string.settings_library_grid_density_spacious
}

internal fun librarySourceKeys(): List<String> = listOf(LIBRARY_SOURCE_ALL, "PLAYABLE", "FAVORITES")

@Composable
fun LibrarySection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val context = LocalContext.current
    val layoutState = remember(uiState.emulators.platforms) { LibraryLayoutState.from(uiState) }

    val visibleItems = remember(layoutState) { libraryLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { libraryLayout.buildSections(layoutState, context) }

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
                com.nendo.argosy.ui.screens.settings.components.SectionHeader(
                    stringResource(item.titleRes)
                )

            LibraryItem.GridDensityItem -> CyclePreference(
                title = stringResource(R.string.settings_library_grid_density_title),
                value = stringResource(gridDensityLabelRes(display.gridDensity)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleGridDensity(1) },
                onPrev = { viewModel.cycleGridDensity(-1) },
                options = remember(context) {
                    GridDensity.entries.map { d -> context.getString(gridDensityLabelRes(d)) }
                },
                onSelect = { viewModel.setGridDensity(GridDensity.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.DefaultSort -> CyclePreference(
                title = stringResource(R.string.settings_library_default_sort_title),
                value = librarySortLabel(context, sortOption, sortDescending),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultSort(1) },
                onPrev = { viewModel.cycleLibraryDefaultSort(-1) },
                options = librarySortOptions(context),
                onSelect = { viewModel.setLibraryDefaultSortIndex(it) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.InstalledFirst -> SwitchPreference(
                title = stringResource(R.string.settings_library_installed_first_title),
                subtitle = stringResource(R.string.settings_library_installed_first_subtitle),
                isEnabled = display.sortInstalledFirst,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSortInstalledFirst(!display.sortInstalledFirst) }
            )

            LibraryItem.FavoritesFirst -> SwitchPreference(
                title = stringResource(R.string.settings_library_favorites_first_title),
                subtitle = stringResource(R.string.settings_library_favorites_first_subtitle),
                isEnabled = display.sortFavoritesFirst,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSortFavoritesFirst(!display.sortFavoritesFirst) }
            )

            LibraryItem.DefaultPlatform -> CyclePreference(
                title = stringResource(R.string.settings_library_default_platform_title),
                value = platformOptions.getOrElse(platformIndex) { LIBRARY_PLATFORM_ALL },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultPlatform(1, platformOptions) },
                onPrev = { viewModel.cycleLibraryDefaultPlatform(-1, platformOptions) },
                options = platformOptions,
                onSelect = { viewModel.setLibraryDefaultPlatform(if (it == 0) "" else platformOptions[it]) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.DefaultSource -> CyclePreference(
                title = stringResource(R.string.settings_library_default_source_title),
                value = librarySourceOptions(context).getOrElse(sourceIndex) {
                    stringResource(R.string.source_filter_all)
                },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultSource(1) },
                onPrev = { viewModel.cycleLibraryDefaultSource(-1) },
                options = remember(context) { librarySourceOptions(context) },
                onSelect = { viewModel.setLibraryDefaultSource(librarySourceKeys()[it]) },
                pickerRequestToken = pickerToken(item)
            )
        }
    }
}
