package com.nendo.argosy.domain.usecase

import android.util.Log
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.storage.GameFileRelocator
import com.nendo.argosy.data.storage.RelocationOutcome
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
    private val platformRepository: PlatformRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val attributionRepository: StorageAttributionRepository,
    private val relocator: GameFileRelocator
) {
    suspend operator fun invoke(
        oldPath: String,
        newPath: String,
        onProgress: ((current: Int, total: Int, gameTitle: String) -> Unit)? = null
    ): MigrationResult = withContext(Dispatchers.IO) {
        val gamesWithPaths = gameRepository.getGamesWithLocalPaths()
        val totalGames = gamesWithPaths.size
        val oldRoot = File(oldPath)
        val newRoot = File(newPath)

        onProgress?.invoke(0, totalGames, "")

        var migrated = 0
        var failed = 0
        var skipped = 0

        Log.d(TAG, "Migration: starting, oldPath=$oldPath, newPath=$newPath, games=${gamesWithPaths.size}")

        gamesWithPaths.forEachIndexed { index, game ->
            Log.d(TAG, "Migration: processing ${index + 1}/$totalGames - ${game.title}")

            onProgress?.invoke(index + 1, totalGames, game.title)

            try {
                val current = gameRepository.getById(game.id) ?: game
                val localPath = current.localPath
                if (localPath == null) {
                    skipped++
                    return@forEachIndexed
                }

                val platformFolder = gameRepository.getDownloadDirForPlatformId(current.platformId)
                if (platformFolder == null) {
                    Log.d(TAG, "Migration: ${current.title} has no platform folder to move from")
                    skipped++
                    return@forEachIndexed
                }
                if (keepsOwnFolder(current.platformId, File(localPath), platformFolder)) {
                    Log.d(TAG, "Migration: ${current.title} stays in its platform's own folder")
                    skipped++
                    return@forEachIndexed
                }
                val outcome = relocator.relocate(current, platformFolder, newRoot) { file ->
                    GameFileRelocator.mapped(file, oldRoot, newRoot, platformFolder, File(newRoot, current.platformSlug))
                }
                when (outcome) {
                    RelocationOutcome.MOVED, RelocationOutcome.ALREADY_MOVED -> {
                        migrated++
                        Log.d(TAG, "Migration: success for ${current.title} ($outcome)")
                    }
                    RelocationOutcome.MISSING -> {
                        val cleared = gameRepository.clearLocalPathIfGenuinelyAbsent(current.id, localPath)
                        Log.d(TAG, "Migration: file missing, localPath cleared=$cleared")
                        skipped++
                    }
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

    private suspend fun keepsOwnFolder(platformId: Long, launch: File, platformFolder: File): Boolean =
        !platformRepository.getById(platformId)?.customRomPath.isNullOrBlank() &&
            GameFileRelocator.isUnder(launch, platformFolder)
}
