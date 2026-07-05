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
    val syncState: StateFlow<LibrarySyncState> = MutableStateFlow(LibrarySyncState.Idle)

    fun forceSync() {}

    fun forceSyncWithOverwrite() {}

    suspend fun resetLibrary(): Int = 0

    fun requestLibrarySync() {}

    fun cleanup() {}
}
