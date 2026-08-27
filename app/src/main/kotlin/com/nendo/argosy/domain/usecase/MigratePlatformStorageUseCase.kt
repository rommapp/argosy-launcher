package com.nendo.argosy.domain.usecase

import android.util.Log
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "MigratePlatformStorage"

/**
 * Per-platform storage migration. Progress is reported through onProgress so the caller can
 * render it; this use case does not localize or display text. [current] is 0 for the initial
 * call before any game has been processed, and the platform name is passed back via the
 * returned [MigrationResult]'s caller context, not through this use case.
 */
class MigratePlatformStorageUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val platformDao: PlatformDao,
    private val attributionRepository: StorageAttributionRepository
) {
    suspend operator fun invoke(
        platformId: Long,
        oldPath: String,
        newPath: String,
        isResetToGlobal: Boolean = false,
        onProgress: ((current: Int, total: Int, gameTitle: String) -> Unit)? = null
    ): MigrationResult = withContext(Dispatchers.IO) {
        val gamesWithPaths = gameRepository.getGamesWithLocalPathsForPlatform(platformId)
        val totalGames = gamesWithPaths.size

        if (totalGames == 0) {
            platformDao.updateCustomRomPath(platformId, if (isResetToGlobal) null else newPath)
            return@withContext MigrationResult(0, 0, 0)
        }

        onProgress?.invoke(0, totalGames, "")

        var migrated = 0
        var failed = 0
        var skipped = 0

        Log.d(TAG, "Migration: starting for $platformId, oldPath=$oldPath, newPath=$newPath, games=$totalGames")

        gamesWithPaths.forEachIndexed { index, game ->
            Log.d(TAG, "Migration: processing ${index + 1}/$totalGames - ${game.title}")

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
                    if (newFile.exists()) newFile.deleteRecursively()
                    val copied = oldFile.copyRecursively(newFile, overwrite = true)
                    if (!copied) {
                        throw java.io.IOException("Recursive copy failed: ${oldFile.absolutePath} -> ${newFile.absolutePath}")
                    }
                    gameRepository.updateLocalPath(game.id, newFile.absolutePath)
                    oldFile.deleteRecursively()
                    migrated++
                    Log.d(TAG, "Migration: success for ${game.title}")
                } else {
                    val cleared = gameRepository.clearLocalPathIfGenuinelyAbsent(game.id, localPath)
                    Log.d(TAG, "Migration: file missing, localPath cleared=$cleared")
                    skipped++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Migration: FAILED for ${game.title}", e)
                failed++
            }
        }

        Log.d(TAG, "Migration: complete - migrated=$migrated, skipped=$skipped, failed=$failed")

        platformDao.updateCustomRomPath(platformId, if (isResetToGlobal) null else newPath)

        attributionRepository.markDirty(StorageCategory.GAMES)
        MigrationResult(migrated, skipped, failed)
    }
}
