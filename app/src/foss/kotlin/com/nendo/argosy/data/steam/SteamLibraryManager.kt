package com.nendo.argosy.data.steam

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor no-op stub. The `full` flavor syncs the user's Steam library via the JavaSteam
 * library. The FOSS build ships without Steam, so sync is inert; this exposes the same public
 * surface consumed by shared code (sync state flow and control methods) as no-ops.
 */
@Singleton
class SteamLibraryManager @Inject constructor() {
    private val _syncState = MutableStateFlow<LibrarySyncState>(LibrarySyncState.Idle)
    val syncState: StateFlow<LibrarySyncState> = _syncState

    fun forceSync() {
        reportUnavailable()
    }

    fun forceSyncWithOverwrite() {
        reportUnavailable()
    }

    suspend fun resetLibrary(): Int = 0

    fun requestLibrarySync() {
        reportUnavailable()
    }

    fun cleanup() {}

    private fun reportUnavailable() {
        _syncState.value = LibrarySyncState.Error("Steam is not available in this build")
    }
}
