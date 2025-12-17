package com.nendo.argosy.domain.usecase

import android.util.Log
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationProgress
import com.nendo.argosy.ui.notification.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "MigratePlatformStorage"
private const val NOTIFICATION_KEY = "platform-migration"

class MigratePlatformStorageUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val platformDao: PlatformDao,
    private val notificationManager: NotificationManager
) {
    suspend operator fun invoke(
        platformId: String,
        oldPath: String,
        newPath: String,
        isResetToGlobal: Boolean = false,
        onProgress: ((current: Int, total: Int, title: String) -> Unit)? = null
    ): MigrationResult = withContext(Dispatchers.IO) {
        val platform = platformDao.getById(platformId)
        val platformName = platform?.name ?: platformId
        val gamesWithPaths = gameRepository.getGamesWithLocalPathsForPlatform(platformId)
        val totalGames = gamesWithPaths.size

        if (totalGames == 0) {
            platformDao.updateCustomRomPath(platformId, if (isResetToGlobal) null else newPath)
            return@withContext MigrationResult(0, 0, 0)
        }

        notificationManager.showPersistent(
            key = NOTIFICATION_KEY,
            title = "Moving $platformName ROMs",
            subtitle = "0 / $totalGames",
            progress = NotificationProgress(0, totalGames)
        )

        var migrated = 0
        var failed = 0
        var skipped = 0

        Log.d(TAG, "Migration: starting for $platformId, oldPath=$oldPath, newPath=$newPath, games=$totalGames")

        gamesWithPaths.forEachIndexed { index, game ->
            Log.d(TAG, "Migration: processing ${index + 1}/$totalGames - ${game.title}")

            notificationManager.updatePersistent(
                key = NOTIFICATION_KEY,
                subtitle = "${index + 1}/$totalGames: ${game.title}",
                progress = NotificationProgress(index, totalGames)
            )
            onProgress?.invoke(index + 1, totalGames, game.title)

            try {
                val localPath = game.localPath
                if (localPath == null) {
                    skipped++
                    return@forEachIndexed
                }

                val oldFile = File(localPath)

                if (oldFile.exists()) {
                    val newFile = File(newPath, oldFile.name)

                    newFile.parentFile?.mkdirs()
                    oldFile.copyTo(newFile, overwrite = true)
                    gameRepository.updateLocalPath(game.id, newFile.absolutePath)
                    oldFile.delete()
                    migrated++
                    Log.d(TAG, "Migration: success for ${game.title}")
                } else {
                    Log.d(TAG, "Migration: file missing, clearing localPath")
                    gameRepository.clearLocalPath(game.id)
                    skipped++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Migration: FAILED for ${game.title}", e)
                failed++
            }
        }

        Log.d(TAG, "Migration: complete - migrated=$migrated, skipped=$skipped, failed=$failed")

        platformDao.updateCustomRomPath(platformId, if (isResetToGlobal) null else newPath)

        val message = buildString {
            append("Moved $migrated")
            if (skipped > 0) append(", $skipped missing")
            if (failed > 0) append(", $failed failed")
        }

        notificationManager.completePersistent(
            key = NOTIFICATION_KEY,
            title = "$platformName migration complete",
            subtitle = message,
            type = if (failed > 0) NotificationType.WARNING else NotificationType.SUCCESS
        )

        MigrationResult(migrated, skipped, failed)
    }
}
