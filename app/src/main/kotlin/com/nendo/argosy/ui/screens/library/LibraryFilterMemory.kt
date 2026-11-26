package com.nendo.argosy.ui.screens.library

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The library filters the person last left in place, for the life of the process. The configured
 * defaults seed the first library visit only; every later visit opens on what was chosen, so
 * clearing a default filter sticks until Argosy restarts.
 *
 * Other game lists that host the filter panel keep their own choices under a [FilterMemoryScope],
 * apart from [filters].
 */
@Singleton
class LibraryFilterMemory @Inject constructor() {
    @Volatile
    var filters: ActiveFilters? = null

    private val scoped = ConcurrentHashMap<String, ActiveFilters>()

    fun filtersFor(scope: FilterMemoryScope): ActiveFilters? = scoped[scope.key]

    fun remember(scope: FilterMemoryScope, filters: ActiveFilters) {
        scoped[scope.key] = filters
    }
}

@JvmInline
value class FilterMemoryScope private constructor(val key: String) {
    companion object {
        fun collection(collectionId: Long) = FilterMemoryScope("collection:$collectionId")

        fun category(type: String, name: String) = FilterMemoryScope("category:$type:$name")
    }
}
