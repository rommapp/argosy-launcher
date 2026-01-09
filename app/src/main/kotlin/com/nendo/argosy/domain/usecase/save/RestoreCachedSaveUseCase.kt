package com.nendo.argosy.domain.usecase.save

import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import javax.inject.Inject

class RestoreCachedSaveUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao
) {
    private val TAG = "RestoreCachedSaveUseCase"

    sealed class Result {
        data object Restored : Result()
        data object RestoredAndSynced : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(
        entry: UnifiedSaveEntry,
        gameId: Long,
        emulatorId: String,
        syncToServer: Boolean
    ): Result {
        val game = gameDao.getById(gameId)
            ?: return Result.Error("Game not found")

        val targetPath = saveSyncRepository.discoverSavePath(
            emulatorId, game.title, game.platformSlug, game.localPath, game.titleId
        ) ?: saveSyncRepository.constructSavePath(
            emulatorId, game.title, game.platformSlug, game.localPath
        ) ?: return Result.Error("Cannot determine save location")

        val restoreSuccess = when (entry.source) {
            UnifiedSaveEntry.Source.LOCAL,
            UnifiedSaveEntry.Source.BOTH -> {
                val cacheId = entry.localCacheId
                    ?: return Result.Error("No local cache ID")
                saveCacheManager.restoreSave(cacheId, targetPath)
            }
            UnifiedSaveEntry.Source.SERVER -> {
                val serverSaveId = entry.serverSaveId
                    ?: return Result.Error("No server save ID")
                downloadServerSave(serverSaveId, targetPath, emulatorId)
            }
        }

        if (!restoreSuccess) {
            return Result.Error("Failed to restore save")
        }

        // Switch to the target entry's channel context
        val targetChannel = entry.channelName
        gameDao.updateActiveSaveChannel(gameId, targetChannel)

        if (syncToServer && game.rommId != null) {
            return when (val uploadResult = saveSyncRepository.uploadSave(gameId, emulatorId, targetChannel)) {
                is SaveSyncResult.Success -> Result.RestoredAndSynced
                is SaveSyncResult.Error -> {
                    Log.w(TAG, "Restored but failed to sync: ${uploadResult.message}")
                    Result.Restored
                }
                else -> Result.Restored
            }
        }

        return Result.Restored
    }

    private suspend fun downloadServerSave(
        serverSaveId: Long,
        targetPath: String,
        emulatorId: String
    ): Boolean {
        return saveSyncRepository.downloadSaveById(serverSaveId, targetPath, emulatorId)
    }
}
