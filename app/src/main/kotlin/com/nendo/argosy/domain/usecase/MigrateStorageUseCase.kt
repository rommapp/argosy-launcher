package com.nendo.argosy.domain.usecase

import android.util.Log
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "MigrateStorageUseCase"

data class MigrationResult(
    val migrated: Int,
    val skipped: Int,
    val failed: Int
)

/**
 * Migration progress, reported through [MigrateStorageUseCase]'s onProgress callback so the
 * caller can render it (this use case does not know how to localize or display text).
 * [current] is 0 for the initial call before any game has been processed.
 */
class MigrateStorageUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val attributionRepository: StorageAttributionRepository
) {
    suspend operator fun invoke(
        oldPath: String,
        newPath: String,
        onProgress: ((current: Int, total: Int, gameTitle: String) -> Unit)? = null
    ): MigrationResult = withContext(Dispatchers.IO) {
        val gamesWithPaths = gameRepository.getGamesWithLocalPaths()
        val totalGames = gamesWithPaths.size

        onProgress?.invoke(0, totalGames, "")

        var migrated = 0
        var failed = 0
        var skipped = 0

        Log.d(TAG, "Migration: starting, oldPath=$oldPath, newPath=$newPath, games=${gamesWithPaths.size}")

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
                    val relativePath = if (oldFile.absolutePath.startsWith(oldPath)) {
                        oldFile.absolutePath.removePrefix(oldPath).trimStart('/')
                    } else {
                        "${game.platformSlug}/${oldFile.name}"
                    }
                    val newFile = File(newPath, relativePath)

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

        preferencesRepository.setRomStoragePath(newPath)

        attributionRepository.markDirty(StorageCategory.GAMES)
        MigrationResult(migrated, skipped, failed)
    }
}
