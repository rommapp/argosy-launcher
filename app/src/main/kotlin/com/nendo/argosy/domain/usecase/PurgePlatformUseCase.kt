package com.nendo.argosy.domain.usecase

import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationProgress
import com.nendo.argosy.ui.notification.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "PurgePlatformUseCase"
private const val NOTIFICATION_KEY = "platform-purge"

data class PurgeResult(
    val gamesDeleted: Int,
    val filesDeleted: Int,
    val bytesFree: Long
)

class PurgePlatformUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val gameRepository: GameRepository,
    private val notificationManager: NotificationManager,
    private val saveCacheDao: SaveCacheDao,
    private val stateCacheDao: StateCacheDao,
    private val saveSyncDao: SaveSyncDao
) {
    suspend operator fun invoke(
        platformId: Long,
        deleteLocalFiles: Boolean = true,
        onProgress: ((current: Int, total: Int, title: String) -> Unit)? = null
    ): PurgeResult = withContext(Dispatchers.IO) {
        val platform = platformDao.getById(platformId)
        val platformName = platform?.name ?: "Platform $platformId"
        val gamesWithPaths = gameRepository.getGamesWithLocalPathsForPlatform(platformId)
        val totalFiles = gamesWithPaths.size

        notificationManager.showPersistent(
            key = NOTIFICATION_KEY,
            title = "Purging $platformName",
            subtitle = "Preparing...",
            progress = NotificationProgress(0, totalFiles.coerceAtLeast(1))
        )

        var filesDeleted = 0
        var bytesFreed = 0L

        if (deleteLocalFiles && totalFiles > 0) {
            Log.d(TAG, "Purge: deleting $totalFiles local files for $platformId")

            gamesWithPaths.forEachIndexed { index, game ->
                notificationManager.updatePersistent(
                    key = NOTIFICATION_KEY,
                    subtitle = "${index + 1}/$totalFiles: ${game.title}",
                    progress = NotificationProgress(index, totalFiles)
                )
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
        saveCacheDao.deleteByPlatform(platformId)
        saveSyncDao.deleteByPlatform(platformId)

        val gamesCount = gameDao.countByPlatform(platformId)
        gameDao.deleteByPlatform(platformId)
        Log.d(TAG, "Purge: deleted $gamesCount game records for $platformId")

        platformDao.updateGameCount(platformId, 0)
        platformDao.updateSyncEnabled(platformId, false)
        Log.d(TAG, "Purge: disabled sync for $platformId")

        val message = buildString {
            append("Deleted $gamesCount games")
            if (filesDeleted > 0) {
                val mb = bytesFreed / (1024 * 1024)
                append(", freed ${mb}MB")
            }
        }

        notificationManager.completePersistent(
            key = NOTIFICATION_KEY,
            title = "$platformName purged",
            subtitle = message,
            type = NotificationType.SUCCESS
        )

        PurgeResult(gamesCount, filesDeleted, bytesFreed)
    }
}
