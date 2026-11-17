package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.LibraryListStyle
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.MultiSelectPreference
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens

internal const val LIBRARY_SOURCE_ALL = "ALL"

internal data class LibraryPlatformOption(val id: Long, val name: String)

internal data class LibraryLayoutState(
    val platforms: List<LibraryPlatformOption>,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val offersBoxArt3d: Boolean = false
) {
    companion object {
        fun from(state: SettingsUiState) = LibraryLayoutState(
            platforms = state.emulators.platforms
                .filter { it.platform.syncEnabled }
                .map { LibraryPlatformOption(it.platform.id, it.platform.getDisplayName()) }
                .sortedBy { it.name },
            libraryLayout = state.display.libraryLayout,
            offersBoxArt3d = state.display.box3dCapableGames > 0 || state.display.libraryBoxArt3d
        )
    }
}

internal sealed class LibraryItem(
    val key: String,
    val section: String,
    val visibleWhen: (LibraryLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int) : LibraryItem(key, section)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (LibraryLayoutState) -> Boolean = { true }
    ) : LibraryItem(key, section, visibleWhen), SearchableSetting

    data object LayoutItem : Row("libraryLayout", "layout", R.string.settings_library_layout_title)
    data object ListStyleItem : Row(
        key = "libraryListStyle",
        section = "layout",
        labelRes = R.string.settings_library_list_style_title,
        visibleWhen = { it.libraryLayout == LibraryLayout.LIST }
    )
    data object GridDensityItem : Row(
        key = "libraryGridDensity",
        section = "layout",
        labelRes = R.string.settings_library_grid_density_title,
        visibleWhen = { it.libraryLayout == LibraryLayout.GRID }
    )
    data object BoxArtItem : Row(
        key = "libraryBoxArt",
        section = "layout",
        labelRes = R.string.settings_library_box_art_title,
        visibleWhen = { it.libraryLayout == LibraryLayout.GRID && it.offersBoxArt3d }
    )
    data object DefaultSort :
        Row("libraryDefaultSort", "defaults", R.string.settings_library_default_sort_title)
    data object InstalledFirst :
        Row("sortInstalledFirst", "defaults", R.string.settings_library_installed_first_title)
    data object FavoritesFirst :
        Row("sortFavoritesFirst", "defaults", R.string.settings_library_favorites_first_title)
    data object DefaultPlatform :
        Row("libraryDefaultPlatform", "defaults", R.string.settings_library_default_platform_title)
    data object DefaultSource :
        Row("libraryDefaultSource", "defaults", R.string.settings_library_default_source_title)
    data object DefaultRegion :
        Row("libraryDefaultRegion", "defaults", R.string.settings_library_default_region_title)
    data object DefaultPlayers :
        Row("libraryDefaultPlayers", "defaults", R.string.settings_library_default_players_title)

    companion object {
        val ALL: List<LibraryItem>
            get() = listOf(
                Header("libraryLayoutHeader", "layout", R.string.settings_library_section_layout),
                LayoutItem,
                ListStyleItem,
                GridDensityItem,
                BoxArtItem,
                Header("libraryDefaultsHeader", "defaults", R.string.settings_library_section_defaults),
                DefaultSort, InstalledFirst, FavoritesFirst, DefaultPlatform, DefaultSource,
                DefaultRegion, DefaultPlayers
            )
    }
}

internal val libraryLayout = SettingsLayout<LibraryItem, LibraryLayoutState>(
    allItems = LibraryItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
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

internal fun libraryPlatformTokens(state: LibraryLayoutState): List<Long?> =
    listOf(null) + state.platforms.map { it.id }

private fun libraryPlatformLabels(context: android.content.Context, state: LibraryLayoutState): List<String> =
    listOf(context.getString(R.string.settings_library_default_platform_all)) + state.platforms.map { it.name }

internal fun libraryPlayerTokens(): List<PlayerCountBucket?> = listOf(null) + PlayerCountBucket.entries

private fun libraryPlayersLabelRes(bucket: PlayerCountBucket?): Int = when (bucket) {
    null -> R.string.settings_library_default_players_any
    PlayerCountBucket.ONE -> R.string.settings_library_default_players_one
    PlayerCountBucket.TWO -> R.string.settings_library_default_players_two
    PlayerCountBucket.THREE -> R.string.settings_library_default_players_three
    PlayerCountBucket.FOUR_PLUS -> R.string.settings_library_default_players_four_plus
}

internal fun librarySourceOptions(context: android.content.Context): List<String> = listOf(
    context.getString(R.string.source_filter_all),
    context.getString(R.string.source_filter_playable),
    context.getString(R.string.source_filter_favorites)
)

private fun libraryLayoutLabelRes(layout: LibraryLayout): Int = when (layout) {
    LibraryLayout.GRID -> R.string.settings_library_layout_grid
    LibraryLayout.LIST -> R.string.settings_library_layout_list
}

private fun gridDensityLabelRes(density: GridDensity): Int = when (density) {
    GridDensity.COMPACT -> R.string.settings_library_grid_density_compact
    GridDensity.NORMAL -> R.string.settings_library_grid_density_normal
    GridDensity.SPACIOUS -> R.string.settings_library_grid_density_spacious
}

internal fun librarySourceKeys(): List<String> = listOf(LIBRARY_SOURCE_ALL, "PLAYABLE", "FAVORITES")

@Composable
private fun regionSummary(selected: Set<String>): String = when (selected.size) {
    0 -> stringResource(R.string.settings_library_default_region_any)
    1 -> selected.first()
    else -> pluralStringResource(R.plurals.settings_library_default_region_count, selected.size, selected.size)
}

@Composable
fun LibrarySection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val context = LocalContext.current
    val layoutState = remember(
        uiState.emulators.platforms,
        display.libraryLayout,
        display.box3dCapableGames,
        display.libraryBoxArt3d
    ) {
        LibraryLayoutState.from(uiState)
    }

    val visibleItems = remember(layoutState) { libraryLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { libraryLayout.buildSections(layoutState, context) }

    fun isFocused(item: LibraryItem): Boolean =
        uiState.focusedIndex == libraryLayout.focusIndexOf(item, layoutState)

    fun pickerToken(item: LibraryItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    val sortOption = SortOption.entries.firstOrNull { it.name == display.libraryDefaultSort }
        ?: SortOption.TITLE
    val sortDescending = display.libraryDefaultSortDescending ?: sortOption.defaultDescending
    val platformTokens = remember(layoutState) { libraryPlatformTokens(layoutState) }
    val platformLabels = remember(layoutState, context) { libraryPlatformLabels(context, layoutState) }
    val platformIndex = platformTokens.indexOf(display.libraryDefaultPlatformId).coerceAtLeast(0)
    val sourceIndex = librarySourceKeys().indexOf(display.libraryDefaultSource).coerceAtLeast(0)
    val regionOptions = display.libraryRegionOptions
    val playerTokens = libraryPlayerTokens()

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

            is LibraryItem.LayoutItem -> SegmentedPreference(
                title = stringResource(item.labelRes),
                options = LibraryLayout.entries.map { l -> stringResource(libraryLayoutLabelRes(l)) },
                selectedIndex = LibraryLayout.entries.indexOf(display.libraryLayout),
                isFocused = isFocused(item),
                onSelect = { viewModel.setLibraryLayout(LibraryLayout.entries[it]) }
            )

            is LibraryItem.ListStyleItem -> SegmentedPreference(
                title = stringResource(item.labelRes),
                options = listOf(
                    stringResource(R.string.settings_library_list_style_compact),
                    stringResource(R.string.settings_library_list_style_relaxed)
                ),
                selectedIndex = LibraryListStyle.entries.indexOf(display.libraryListStyle),
                isFocused = isFocused(item),
                onSelect = { viewModel.setLibraryListStyle(LibraryListStyle.entries[it]) }
            )

            is LibraryItem.GridDensityItem -> SegmentedPreference(
                title = stringResource(item.labelRes),
                options = GridDensity.entries.map { d -> stringResource(gridDensityLabelRes(d)) },
                selectedIndex = GridDensity.entries.indexOf(display.gridDensity),
                isFocused = isFocused(item),
                onSelect = { viewModel.setGridDensity(GridDensity.entries[it]) }
            )

            is LibraryItem.BoxArtItem -> SegmentedPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_library_box_art_subtitle),
                options = listOf(
                    stringResource(R.string.settings_library_box_art_option_2d),
                    stringResource(R.string.settings_library_box_art_option_3d)
                ),
                selectedIndex = if (display.libraryBoxArt3d) 1 else 0,
                isFocused = isFocused(item),
                onSelect = { viewModel.setLibraryBoxArt3d(it == 1) }
            )

            is LibraryItem.DefaultSort -> CyclePreference(
                title = stringResource(item.labelRes),
                value = librarySortLabel(context, sortOption, sortDescending),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultSort(1) },
                onPrev = { viewModel.cycleLibraryDefaultSort(-1) },
                options = librarySortOptions(context),
                onSelect = { viewModel.setLibraryDefaultSortIndex(it) },
                pickerRequestToken = pickerToken(item)
            )

            is LibraryItem.InstalledFirst -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_library_installed_first_subtitle),
                isEnabled = display.sortInstalledFirst,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSortInstalledFirst(!display.sortInstalledFirst) }
            )

            is LibraryItem.FavoritesFirst -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_library_favorites_first_subtitle),
                isEnabled = display.sortFavoritesFirst,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSortFavoritesFirst(!display.sortFavoritesFirst) }
            )

            is LibraryItem.DefaultPlatform -> CyclePreference(
                title = stringResource(item.labelRes),
                value = platformLabels[platformIndex],
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultPlatform(1, platformTokens) },
                onPrev = { viewModel.cycleLibraryDefaultPlatform(-1, platformTokens) },
                options = platformLabels,
                onSelect = { viewModel.setLibraryDefaultPlatform(platformTokens[it]) },
                pickerRequestToken = pickerToken(item)
            )

            is LibraryItem.DefaultSource -> SegmentedPreference(
                title = stringResource(item.labelRes),
                options = remember(context) { librarySourceOptions(context) },
                selectedIndex = sourceIndex,
                isFocused = isFocused(item),
                onSelect = { viewModel.setLibraryDefaultSource(librarySourceKeys()[it]) }
            )

            is LibraryItem.DefaultRegion -> MultiSelectPreference(
                title = stringResource(item.labelRes),
                value = regionSummary(display.libraryDefaultRegions),
                isFocused = isFocused(item),
                options = regionOptions,
                selected = remember(regionOptions, display.libraryDefaultRegions) {
                    regionOptions.indices.filter { regionOptions[it] in display.libraryDefaultRegions }.toSet()
                },
                onToggle = { viewModel.toggleLibraryDefaultRegion(regionOptions[it]) },
                emptyText = stringResource(R.string.settings_library_default_region_none_available),
                pickerRequestToken = pickerToken(item)
            )

            is LibraryItem.DefaultPlayers -> CyclePreference(
                title = stringResource(item.labelRes),
                value = stringResource(libraryPlayersLabelRes(display.libraryDefaultPlayers)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultPlayers(1) },
                onPrev = { viewModel.cycleLibraryDefaultPlayers(-1) },
                options = remember(context) {
                    playerTokens.map { context.getString(libraryPlayersLabelRes(it)) }
                },
                onSelect = { viewModel.setLibraryDefaultPlayers(playerTokens[it]) },
                pickerRequestToken = pickerToken(item)
            )
        }
    }
}
