package com.nendo.argosy.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory record of save locations that exist on-system but cannot be read, surfaced as a
 * passive notice on the save sync screen. Populated only by secure-saves-OFF flows.
 */
@Singleton
class SaveAccessNotices @Inject constructor() {

    data class InaccessibleLocation(val dirPath: String, val emulatorId: String)

    private val _locations = MutableStateFlow<List<InaccessibleLocation>>(emptyList())
    val locations: StateFlow<List<InaccessibleLocation>> = _locations.asStateFlow()

    fun record(dirPath: String, emulatorId: String) {
        _locations.update { current ->
            if (current.any { it.dirPath == dirPath }) current
            else current + InaccessibleLocation(dirPath, emulatorId)
        }
    }

    fun publishPass(entries: List<InaccessibleLocation>) {
        _locations.value = entries.distinctBy { it.dirPath }
    }
}
