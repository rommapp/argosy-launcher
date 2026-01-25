package com.nendo.argosy.data.cheats

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CheatsDownloadObserver"

@Singleton
class CheatsDownloadObserver @Inject constructor(
    private val downloadManager: DownloadManager,
    private val cheatsRepository: dagger.Lazy<CheatsRepository>,
    private val gameDao: GameDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            downloadManager.completionEvents.collect { event ->
                handleDownloadCompletion(event)
            }
        }
        Logger.debug(TAG, "Started observing download completions for cheats sync")
    }

    private suspend fun handleDownloadCompletion(
        event: com.nendo.argosy.data.download.DownloadCompletionEvent
    ) {
        if (event.isDiscDownload) {
            Logger.debug(TAG, "Skipping disc download for game ${event.gameId}")
            return
        }

        val game = gameDao.getById(event.gameId)
        if (game == null) {
            Logger.error(TAG, "Game ${event.gameId} not found")
            return
        }

        if (game.cheatsFetched) {
            Logger.debug(TAG, "Cheats already fetched for game ${event.gameId}")
            return
        }

        Logger.debug(TAG, "Syncing cheats for game ${event.gameId} (${game.title})")
        try {
            cheatsRepository.get().syncCheatsForGame(game)
        } catch (e: Exception) {
            Logger.error(TAG, "Cheats sync failed for game ${event.gameId}", e)
        }
    }
}
