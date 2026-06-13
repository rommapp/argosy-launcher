package com.nendo.argosy.util

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.screens.settings.PlatformFilterItem

object PlatformFilterLogic {
    enum class SortMode {
        DEFAULT,
        NAME_ASC,
        NAME_DESC,
        MOST_GAMES,
        LEAST_GAMES
    }

    enum class FilterMode {
        ALL,
        HAS_GAMES,
        ENABLED
    }

    fun filterAndSortPlatformEntities(
        items: List<PlatformEntity>,
        searchQuery: String,
        filterMode: FilterMode,
        sortMode: SortMode
    ): List<PlatformEntity> = filterAndSortBy(
        items = items,
        searchQuery = searchQuery,
        filterMode = filterMode,
        sortMode = sortMode,
        nameOf = { it.name },
        countOf = { it.gameCount },
        isEnabled = { it.syncEnabled },
        defaultCompare = { a, b -> a.sortOrder.compareTo(b.sortOrder) }
    )

    fun filterAndSortPlatformFilterItems(
        items: List<PlatformFilterItem>,
        searchQuery: String,
        filterMode: FilterMode,
        sortMode: SortMode
    ): List<PlatformFilterItem> = filterAndSortBy(
        items = items,
        searchQuery = searchQuery,
        filterMode = filterMode,
        sortMode = sortMode,
        nameOf = { it.name },
        countOf = { it.romCount },
        isEnabled = { it.syncEnabled },
        defaultCompare = { _, _ -> 0 }
    )

    private fun <T> filterAndSortBy(
        items: List<T>,
        searchQuery: String,
        filterMode: FilterMode,
        sortMode: SortMode,
        nameOf: (T) -> String,
        countOf: (T) -> Int,
        isEnabled: (T) -> Boolean,
        defaultCompare: (T, T) -> Int
    ): List<T> {
        val query = searchQuery.trim()

        val filtered = items.filter { item ->
            val matchesFilter = when (filterMode) {
                FilterMode.ALL -> true
                FilterMode.HAS_GAMES -> countOf(item) > 0
                FilterMode.ENABLED -> isEnabled(item)
            }
            val matchesQuery = query.isEmpty() || nameOf(item).contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }

        return filtered.sortedWith { a, b ->
            when (sortMode) {
                SortMode.NAME_ASC -> nameOf(a).compareTo(nameOf(b), ignoreCase = true)
                SortMode.NAME_DESC -> nameOf(b).compareTo(nameOf(a), ignoreCase = true)
                SortMode.MOST_GAMES -> {
                    val countCompare = countOf(b).compareTo(countOf(a))
                    if (countCompare != 0) countCompare
                    else nameOf(a).compareTo(nameOf(b), ignoreCase = true)
                }
                SortMode.LEAST_GAMES -> {
                    val countCompare = countOf(a).compareTo(countOf(b))
                    if (countCompare != 0) countCompare
                    else nameOf(a).compareTo(nameOf(b), ignoreCase = true)
                }
                SortMode.DEFAULT -> defaultCompare(a, b)
            }
        }
    }
}
