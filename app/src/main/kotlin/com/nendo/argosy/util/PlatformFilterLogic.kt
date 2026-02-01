package com.nendo.argosy.util

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

    fun <T> filterAndSort(
        items: List<T>,
        searchQuery: String,
        filterMode: FilterMode,
        sortMode: SortMode,
        nameSelector: (T) -> String,
        countSelector: (T) -> Int,
        enabledSelector: ((T) -> Boolean)? = null,
        defaultSortSelector: ((T) -> Comparable<*>?)? = null
    ): List<T> {
        val query = searchQuery.trim()
        val queryLower = query.lowercase()
        return items.filter { item ->
            when (filterMode) {
                FilterMode.HAS_GAMES -> if (countSelector(item) <= 0) return@filter false
                FilterMode.ENABLED -> if (enabledSelector != null && !enabledSelector(item)) return@filter false
                FilterMode.ALL -> {}
            }
            if (query.isNotEmpty() && !nameSelector(item).contains(queryLower, ignoreCase = true)) return@filter false
            true
        }.sortedWith { a, b ->
            when (sortMode) {
                SortMode.NAME_ASC -> String.CASE_INSENSITIVE_ORDER.compare(nameSelector(a), nameSelector(b))
                SortMode.NAME_DESC -> String.CASE_INSENSITIVE_ORDER.compare(nameSelector(b), nameSelector(a))
                SortMode.MOST_GAMES -> {
                    val countCompare = countSelector(b).compareTo(countSelector(a))
                    if (countCompare != 0) countCompare
                    else String.CASE_INSENSITIVE_ORDER.compare(nameSelector(a), nameSelector(b))
                }
                SortMode.LEAST_GAMES -> {
                    val countCompare = countSelector(a).compareTo(countSelector(b))
                    if (countCompare != 0) countCompare
                    else String.CASE_INSENSITIVE_ORDER.compare(nameSelector(a), nameSelector(b))
                }
                SortMode.DEFAULT -> {
                    if (defaultSortSelector != null) {
                        compareValues(defaultSortSelector(a), defaultSortSelector(b))
                    } else {
                        0
                    }
                }
            }
        }
    }
}
