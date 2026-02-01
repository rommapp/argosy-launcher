package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveSyncRepository: SaveSyncRepository,
    private val romMRepository: RomMRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isServiceRunning = false

    fun start() {
        SyncNotificationChannel.create(context)
        observeSyncState()
    }

    private fun observeSyncState() {
        scope.launch {
            combine(
                saveSyncRepository.syncQueueState,
                romMRepository.syncProgress
            ) { saveState, libraryProgress ->
                val hasSaveWork = saveState.operations.any {
                    it.status == SyncStatus.PENDING || it.status == SyncStatus.IN_PROGRESS
                }
                val hasLibraryWork = libraryProgress.isSyncing
                hasSaveWork || hasLibraryWork
            }
                .distinctUntilChanged()
                .collect { hasActiveWork ->
                    if (hasActiveWork && !isServiceRunning) {
                        isServiceRunning = true
                        SyncForegroundService.start(context)
                    } else if (!hasActiveWork && isServiceRunning) {
                        isServiceRunning = false
                    }
                }
        }
    }
}
