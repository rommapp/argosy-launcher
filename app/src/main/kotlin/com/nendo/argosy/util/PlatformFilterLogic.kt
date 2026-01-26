package com.nendo.argosy.util

object PlatformFilterLogic {
    enum class SortMode {
        DEFAULT,
        NAME_ASC,
        NAME_DESC,
        MOST_GAMES,
        LEAST_GAMES
    }

    fun <T> filterAndSort(
        items: List<T>,
        searchQuery: String,
        hasGames: Boolean,
        sortMode: SortMode,
        nameSelector: (T) -> String,
        countSelector: (T) -> Int,
        defaultSortSelector: ((T) -> Comparable<*>?)? = null
    ): List<T> {
        val query = searchQuery.trim()
        return items.filter { item ->
            if (hasGames && countSelector(item) <= 0) return@filter false
            if (query.isNotEmpty() && !nameSelector(item).contains(query, ignoreCase = true)) return@filter false
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
