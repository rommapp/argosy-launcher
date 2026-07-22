package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.OrphanedFileDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.OrphanedFileEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.repository.SteamResult
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "DeleteGameUseCase"

class DeleteGameUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameRepository: GameRepository,
    private val downloadQueueDao: DownloadQueueDao,
    private val gameFileDao: GameFileDao,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val orphanedFileDao: OrphanedFileDao,
    private val steamRepository: SteamRepository,
    private val payloadCodec: SyncPayloadCodec,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val attributionRepository: StorageAttributionRepository
) {
    suspend operator fun invoke(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false

        if (game.source == GameSource.STEAM) {
            return deleteSteamGame(game.steamAppId)
        }

        val path = game.localPath ?: return false
        val platformFolder = gameRepository.getDownloadDirForPlatform(game.platformSlug)

        gameRepository.clearLocalPath(gameId)
        downloadQueueDao.deleteByGameId(gameId)
        val musicDirPrefix = musicDirectoryManager.resolveMusicDir().absolutePath + File.separator
        gameFileDao.clearLocalPathsByGameIdExcludingPrefix(gameId, musicDirPrefix)

        saveCacheManager.deleteAllCachesForGame(gameId)
        saveSyncDao.deleteByGame(gameId)
        deleteQueuedScreenshotFiles(gameId)
        pendingSyncQueueDao.deleteByGameId(gameId)
        gameDao.updateActiveSaveChannel(gameId, null)
        gameDao.updateActiveSaveTimestamp(gameId, null)

        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) return@withContext

                val deleted = if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    val parent = file.parentFile
                    val platformCanonical = platformFolder.canonicalPath
                    val parentCanonical = parent?.canonicalPath

                    val isPlatformFolder = parentCanonical == platformCanonical
                    val isInsidePlatformFolder = parentCanonical?.startsWith(platformCanonical) == true

                    if (parent != null && !isPlatformFolder && isInsidePlatformFolder) {
                        parent.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }

                if (!deleted) {
                    Logger.warn(TAG, "Failed to delete $path, adding to orphan index")
                    orphanedFileDao.insert(OrphanedFileEntity(path = path))
                }
            } catch (e: Exception) {
                Logger.warn(TAG, "Error deleting file $path: ${e.message}")
                orphanedFileDao.insert(OrphanedFileEntity(path = path))
            }
        }

        attributionRepository.markDirty(StorageCategory.GAMES)
        attributionRepository.markDirty(StorageCategory.SAVE_STATE_CACHE)
        Logger.debug(TAG, "Deleted local file and all save data for game $gameId")
        return true
    }

    private suspend fun deleteQueuedScreenshotFiles(gameId: Long) {
        val rows = pendingSyncQueueDao.getByGameId(gameId)
            .filter { it.syncType == SyncType.SCREENSHOT }
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            rows.forEach { row ->
                payloadCodec.decodeScreenshot(row.payloadJson)?.let { File(it.localPath).delete() }
            }
        }
    }

    private suspend fun deleteSteamGame(steamAppId: Long?): Boolean {
        if (steamAppId == null) {
            Logger.warn(TAG, "Cannot delete Steam game without steamAppId")
            return false
        }
        val result = steamRepository.removeGame(steamAppId)
        return when (result) {
            is SteamResult.Success -> {
                Logger.debug(TAG, "Deleted Steam game $steamAppId")
                true
            }
            is SteamResult.Error -> {
                Logger.warn(TAG, "Failed to delete Steam game: ${result.message}")
                false
            }
        }
    }
}
