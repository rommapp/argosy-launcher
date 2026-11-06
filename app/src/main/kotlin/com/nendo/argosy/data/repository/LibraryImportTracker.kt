package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.scanner.RomScanner
import com.nendo.argosy.data.steam.LibrarySyncState
import com.nendo.argosy.data.steam.SteamLibraryManager
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * True while a RomM library sync, a local ROM scan or a Steam library sync is writing games.
 */
@Singleton
class LibraryImportTracker internal constructor(
    scope: CoroutineScope,
    sources: List<Flow<Boolean>>
) {

    @Inject constructor(
        romMRepository: RomMRepository,
        romScanner: RomScanner,
        steamLibraryManager: SteamLibraryManager
    ) : this(
        SafeCoroutineScope(Dispatchers.Default, TAG),
        listOf(
            romMRepository.syncProgress.map { it.isSyncing },
            romScanner.progress.map { it.isScanning },
            steamLibraryManager.syncState.map { it.isRunning() }
        )
    )

    val importing: StateFlow<Boolean> = combine(sources) { running -> running.any { it } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private companion object {
        const val TAG = "LibraryImportTracker"

        fun LibrarySyncState.isRunning(): Boolean = when (this) {
            LibrarySyncState.SyncingLicenses,
            is LibrarySyncState.FetchingPackages,
            is LibrarySyncState.FetchingApps -> true
            LibrarySyncState.Idle,
            is LibrarySyncState.Complete,
            is LibrarySyncState.Error -> false
        }
    }
}
