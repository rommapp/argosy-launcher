package com.nendo.argosy.ui.screens.library

import android.content.Context
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.ui.common.labelRes

internal const val FILTER_OPTION_PAGE = 5

data class FilterPanelState(
    val activeFilters: ActiveFilters = ActiveFilters(),
    val filterOptions: FilterOptions = FilterOptions(),
    val categoryIndex: Int = 0,
    val optionIndex: Int = 0,
    val recentSearches: List<String> = emptyList(),
    val hiddenGameCount: Int = 0
) {
    val currentFilterCategory: FilterCategory
        get() = FilterCategory.entries.getOrElse(categoryIndex) { FilterCategory.SOURCE }

    val filterLeadingStops: Int
        get() = if (currentFilterCategory == FilterCategory.SEARCH) 1 else 0

    val isFilterSearchFieldFocused: Boolean
        get() = currentFilterCategory == FilterCategory.SEARCH && optionIndex == 0

    val currentOptionCount: Int
        get() = when (currentFilterCategory) {
            FilterCategory.SORT -> SortOption.entries.size
            FilterCategory.SEARCH -> recentSearches.size
            FilterCategory.SOURCE -> SourceFilter.entries.size
            FilterCategory.PLATFORM -> filterOptions.platforms.size
            FilterCategory.GENRE -> filterOptions.genres.size
            FilterCategory.REGION -> filterOptions.regions.size
            FilterCategory.PLAYERS -> PlayerCountBucket.entries.size
            FilterCategory.SERIES -> filterOptions.series.size
        }

    fun currentCategoryOptions(context: Context): List<String> =
        when (currentFilterCategory) {
            FilterCategory.SORT -> SortOption.entries.map { option ->
                val directionIndicator = if (option == activeFilters.sort.option) {
                    if (activeFilters.sort.descending) " v" else " ^"
                } else ""
                context.getString(option.labelRes) + directionIndicator
            }
            FilterCategory.SEARCH -> recentSearches
            FilterCategory.SOURCE -> SourceFilter.entries.map { filter ->
                val label = context.getString(filter.labelRes)
                if (filter == SourceFilter.HIDDEN && hiddenGameCount > 0) "$label ($hiddenGameCount)" else label
            }
            FilterCategory.PLATFORM -> filterOptions.platforms.map { it.label }
            FilterCategory.GENRE -> filterOptions.genres
            FilterCategory.REGION -> filterOptions.regions
            FilterCategory.PLAYERS -> PlayerCountBucket.entries.map { context.getString(it.labelRes) }
            FilterCategory.SERIES -> filterOptions.series
        }

    val isCurrentCategoryMultiSelect: Boolean
        get() = currentFilterCategory !in SINGLE_SELECT_CATEGORIES

    val selectedSourceIndex: Int
        get() = activeFilters.source.ordinal

    val selectedSortIndex: Int
        get() = activeFilters.sort.option.ordinal

    val selectedPlayersIndex: Int
        get() = activeFilters.players?.ordinal ?: -1

    val selectedIndicesInCurrentCategory: Set<Int>
        get() = when (currentFilterCategory) {
            FilterCategory.PLATFORM -> {
                val ids = activeFilters.platforms.map { it.id }.toSet()
                filterOptions.platforms.indices.filter { filterOptions.platforms[it].id in ids }.toSet()
            }
            FilterCategory.GENRE -> indicesOf(filterOptions.genres, activeFilters.genres)
            FilterCategory.REGION -> indicesOf(filterOptions.regions, activeFilters.regions)
            FilterCategory.SERIES -> indicesOf(filterOptions.series, activeFilters.series)
            FilterCategory.SORT,
            FilterCategory.SEARCH,
            FilterCategory.SOURCE,
            FilterCategory.PLAYERS -> emptySet()
        }

    val availableCategories: List<FilterCategory>
        get() = FilterCategory.entries.filter { category ->
            when (category) {
                FilterCategory.SORT,
                FilterCategory.SEARCH,
                FilterCategory.SOURCE,
                FilterCategory.PLAYERS -> true
                FilterCategory.PLATFORM -> filterOptions.platforms.isNotEmpty()
                FilterCategory.GENRE -> filterOptions.genres.isNotEmpty()
                FilterCategory.REGION -> filterOptions.regions.isNotEmpty()
                FilterCategory.SERIES -> filterOptions.series.isNotEmpty()
            }
        }

    fun opened(): FilterPanelState = copy(categoryIndex = 0, optionIndex = 0)

    fun movedCategory(delta: Int): FilterPanelState {
        val categories = availableCategories
        val current = categories.indexOfFirst { it == currentFilterCategory }
        val target = categories.getOrElse((current + delta).coerceIn(0, categories.size - 1)) {
            FilterCategory.SOURCE
        }
        return withCategory(target)
    }

    fun withCategory(category: FilterCategory): FilterPanelState =
        copy(categoryIndex = FilterCategory.entries.indexOf(category), optionIndex = 0)

    fun movedOption(delta: Int): FilterPanelState {
        val stopCount = currentOptionCount + filterLeadingStops
        if (stopCount == 0) return this
        return copy(optionIndex = (optionIndex + delta).coerceIn(0, stopCount - 1))
    }

    /**
     * The filters after the option under the cursor is chosen, or null when the cursor rests on
     * nothing choosable. The search field is not an option; the host opens the keyboard for it.
     */
    fun confirmedFilters(): ActiveFilters? {
        if (isFilterSearchFieldFocused) return null
        val index = optionIndex - filterLeadingStops
        val filters = activeFilters
        return when (currentFilterCategory) {
            FilterCategory.SORT -> {
                val selected = SortOption.entries.getOrNull(index) ?: return null
                val current = filters.sort
                val sort = if (selected == current.option) {
                    current.copy(descending = !current.descending)
                } else {
                    ActiveSort(option = selected, descending = selected.defaultDescending)
                }
                filters.copy(sort = sort)
            }
            FilterCategory.SEARCH -> filters.copy(searchQuery = recentSearches.getOrNull(index) ?: return null)
            FilterCategory.SOURCE -> filters.copy(source = SourceFilter.entries.getOrElse(index) { SourceFilter.ALL })
            FilterCategory.PLATFORM -> {
                val platform = filterOptions.platforms.getOrNull(index) ?: return null
                filters.copy(platforms = filters.platforms.toggled(platform))
            }
            FilterCategory.GENRE -> filters.copy(
                genres = filters.genres.toggled(filterOptions.genres.getOrNull(index) ?: return null)
            )
            FilterCategory.REGION -> filters.copy(
                regions = filters.regions.toggled(filterOptions.regions.getOrNull(index) ?: return null)
            )
            FilterCategory.PLAYERS -> {
                val bucket = PlayerCountBucket.entries.getOrNull(index) ?: return null
                filters.copy(players = if (bucket == filters.players) null else bucket)
            }
            FilterCategory.SERIES -> filters.copy(
                series = filters.series.toggled(filterOptions.series.getOrNull(index) ?: return null)
            )
        }
    }

    fun clearedCategoryFilters(): ActiveFilters = when (currentFilterCategory) {
        FilterCategory.SORT -> activeFilters.copy(sort = ActiveSort())
        FilterCategory.SEARCH -> activeFilters.copy(searchQuery = "")
        FilterCategory.SOURCE -> activeFilters.copy(source = SourceFilter.ALL)
        FilterCategory.PLATFORM -> activeFilters.copy(platforms = emptySet())
        FilterCategory.GENRE -> activeFilters.copy(genres = emptySet())
        FilterCategory.REGION -> activeFilters.copy(regions = emptySet())
        FilterCategory.PLAYERS -> activeFilters.copy(players = null)
        FilterCategory.SERIES -> activeFilters.copy(series = emptySet())
    }

    private fun indicesOf(options: List<String>, selected: Set<String>): Set<Int> =
        options.indices.filter { options[it] in selected }.toSet()

    private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

    private companion object {
        val SINGLE_SELECT_CATEGORIES = setOf(
            FilterCategory.SORT,
            FilterCategory.SOURCE,
            FilterCategory.SEARCH,
            FilterCategory.PLAYERS
        )
    }
}
