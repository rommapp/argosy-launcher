package com.nendo.argosy.domain.usecase.game

import android.util.Log
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSaveSyncDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "DeleteGameUseCase"

class DeleteGameUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameRepository: GameRepository,
    private val downloadQueueDao: DownloadQueueDao,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSaveSyncDao: PendingSaveSyncDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend operator fun invoke(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false
        val path = game.localPath ?: return false

        gameRepository.clearLocalPath(gameId)
        downloadQueueDao.deleteByGameId(gameId)

        saveCacheManager.deleteAllCachesForGame(gameId)
        saveSyncDao.deleteByGame(gameId)
        pendingSaveSyncDao.deleteByGame(gameId)
        gameDao.updateActiveSaveChannel(gameId, null)
        gameDao.updateActiveSaveTimestamp(gameId, null)
        Log.d(TAG, "Deleted local file and all save data for game $gameId")

        scope.launch {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
                // DB already updated, orphaned file is acceptable
            }
        }

        return true
    }
}
