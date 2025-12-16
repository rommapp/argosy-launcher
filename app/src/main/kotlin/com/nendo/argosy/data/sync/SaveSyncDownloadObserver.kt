package com.nendo.argosy.data.sync

import android.util.Log
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SaveSyncDownloadObserver"

@Singleton
class SaveSyncDownloadObserver @Inject constructor(
    private val downloadManager: DownloadManager,
    private val saveSyncRepository: dagger.Lazy<SaveSyncRepository>,
    private val gameDao: GameDao,
    private val emulatorResolver: EmulatorResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            downloadManager.completionEvents.collect { event ->
                handleDownloadCompletion(event)
            }
        }
        Log.d(TAG, "Started observing download completions")
    }

    private suspend fun handleDownloadCompletion(
        event: com.nendo.argosy.data.download.DownloadCompletionEvent
    ) {
        if (event.isDiscDownload) {
            Log.d(TAG, "handleDownloadCompletion: skipped - disc download for game ${event.gameId}")
            return
        }

        val game = gameDao.getById(event.gameId)
        if (game == null) {
            Log.e(TAG, "handleDownloadCompletion: game ${event.gameId} not found")
            return
        }

        val emulatorId = emulatorResolver.getEmulatorIdForGame(event.gameId, game.platformId)
        if (emulatorId == null) {
            Log.d(TAG, "handleDownloadCompletion: no emulator for platform ${game.platformId}")
            return
        }

        Log.d(TAG, "handleDownloadCompletion: triggering save sync for game ${event.gameId} with emulator $emulatorId")
        try {
            saveSyncRepository.get().syncSavesForNewDownload(event.gameId, event.rommId, emulatorId)
        } catch (e: Exception) {
            Log.e(TAG, "handleDownloadCompletion: save sync failed", e)
        }
    }
}
