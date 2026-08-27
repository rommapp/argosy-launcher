package com.nendo.argosy.domain.usecase

import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "PurgePlatformUseCase"

data class PurgeResult(
    val gamesDeleted: Int,
    val filesDeleted: Int,
    val bytesFree: Long
)

/**
 * Deletes a platform's downloaded files and library rows. Progress is reported through
 * onProgress so the caller can render it; this use case does not localize or display text.
 * [current] is 0 for the initial call before any file has been processed.
 */
class PurgePlatformUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val gameRepository: GameRepository,
    private val saveCacheDao: SaveCacheDao,
    private val stateCacheDao: StateCacheDao,
    private val stateOwnershipDao: com.nendo.argosy.data.local.dao.StateOwnershipDao,
    private val stateTombstoneDao: com.nendo.argosy.data.local.dao.StateTombstoneDao,
    private val saveSyncDao: SaveSyncDao,
    private val attributionRepository: StorageAttributionRepository,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository
) {
    suspend operator fun invoke(
        platformId: Long,
        deleteLocalFiles: Boolean = true,
        onProgress: ((current: Int, total: Int, gameTitle: String) -> Unit)? = null
    ): PurgeResult = withContext(Dispatchers.IO) {
        val gamesWithPaths = gameRepository.getGamesWithLocalPathsForPlatform(platformId)
        val totalFiles = gamesWithPaths.size

        onProgress?.invoke(0, totalFiles, "")

        var filesDeleted = 0
        var bytesFreed = 0L

        if (deleteLocalFiles && totalFiles > 0) {
            Log.d(TAG, "Purge: deleting $totalFiles local files for $platformId")

            gamesWithPaths.forEachIndexed { index, game ->
                onProgress?.invoke(index + 1, totalFiles, game.title)

                try {
                    val localPath = game.localPath ?: return@forEachIndexed
                    val file = File(localPath)
                    if (file.exists()) {
                        bytesFreed += file.length()
                        file.delete()
                        filesDeleted++
                        Log.d(TAG, "Purge: deleted ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Purge: failed to delete file for ${game.title}", e)
                }
            }
        }

        stateCacheDao.deleteByPlatform(platformId)
        stateOwnershipDao.deleteByPlatform(platformId)
        stateTombstoneDao.deleteByPlatform(platformId)
        saveCacheDao.deleteByPlatform(platformId)
        saveSyncDao.deleteByPlatform(platformId)

        val gamesCount = gameDao.countByPlatform(platformId, syncPreferencesRepository.getRommUserId())
        gameDao.deleteByPlatform(platformId)
        Log.d(TAG, "Purge: deleted $gamesCount game records for $platformId")

        platformDao.updateGameCount(platformId, 0)
        platformDao.updateSyncEnabled(platformId, false)
        Log.d(TAG, "Purge: disabled sync for $platformId")

        attributionRepository.markDirty(StorageCategory.GAMES)
        PurgeResult(gamesCount, filesDeleted, bytesFreed)
    }
}
