package com.nendo.argosy.ui.screens.setup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SetupPosition(val page: SetupPage, val focused: SetupTarget?)

/**
 * The page and target of the setup run in progress, held for the life of the process. Only the
 * setup on screen records it, and a setup composed on the other screen after a role swap opens
 * from it. Cleared when the run finishes.
 */
@Singleton
class SetupHandoff @Inject constructor() {
    private val _position = MutableStateFlow<SetupPosition?>(null)
    val position: StateFlow<SetupPosition?> = _position.asStateFlow()

    fun record(position: SetupPosition) {
        _position.value = position
    }

    fun clear() {
        _position.value = null
    }
}
