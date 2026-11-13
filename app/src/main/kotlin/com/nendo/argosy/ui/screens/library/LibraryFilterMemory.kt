package com.nendo.argosy.ui.screens.library

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The library filters the person last left in place, for the life of the process. The configured
 * defaults seed the first library visit only; every later visit opens on what was chosen, so
 * clearing a default filter sticks until Argosy restarts.
 */
@Singleton
class LibraryFilterMemory @Inject constructor() {
    @Volatile
    var filters: ActiveFilters? = null
}
