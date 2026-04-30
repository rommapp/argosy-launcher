package com.nendo.argosy.domain.usecase

import android.util.Log
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationProgress
import com.nendo.argosy.core.notification.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "MigrateStorageUseCase"
private const val NOTIFICATION_KEY = "migration"

data class MigrationResult(
    val migrated: Int,
    val skipped: Int,
    val failed: Int
)

class MigrateStorageUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: NotificationManager
) {
    suspend operator fun invoke(
        oldPath: String,
        newPath: String,
        onProgress: ((current: Int, total: Int, title: String) -> Unit)? = null
    ): MigrationResult = withContext(Dispatchers.IO) {
        val gamesWithPaths = gameRepository.getGamesWithLocalPaths()
        val totalGames = gamesWithPaths.size

        notificationManager.showPersistent(
            key = NOTIFICATION_KEY,
            title = "Moving games",
            subtitle = "0 / $totalGames",
            progress = NotificationProgress(0, totalGames)
        )

        var migrated = 0
        var failed = 0
        var skipped = 0

        Log.d(TAG, "Migration: starting, oldPath=$oldPath, newPath=$newPath, games=${gamesWithPaths.size}")

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
                    val relativePath = if (oldFile.absolutePath.startsWith(oldPath)) {
                        oldFile.absolutePath.removePrefix(oldPath).trimStart('/')
                    } else {
                        "${game.platformSlug}/${oldFile.name}"
                    }
                    val newFile = File(newPath, relativePath)

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

        preferencesRepository.setRomStoragePath(newPath)

        val message = buildString {
            append("Moved $migrated")
            if (skipped > 0) append(", $skipped missing")
            if (failed > 0) append(", $failed failed")
        }

        notificationManager.completePersistent(
            key = NOTIFICATION_KEY,
            title = "Migration complete",
            subtitle = message,
            type = if (failed > 0) NotificationType.WARNING else NotificationType.SUCCESS
        )

        MigrationResult(migrated, skipped, failed)
    }
}
