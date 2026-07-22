package com.nendo.argosy.data.scanner

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VariantScanner"

private val PATCH_EXTENSIONS = setOf("ips", "bps", "ups", "xdelta", "ppf")
private val DISC_COMPONENT_EXTENSIONS = setOf("bin", "raw", "wav", "ogg", "img", "sub")

@Singleton
class VariantScanner @Inject constructor(
    private val gameFileDao: GameFileDao
) {
    suspend fun scanForVariants(game: GameEntity): Int {
        if (game.platformSlug in VariantCategory.VARIANT_EXCLUDED_PLATFORMS) return 0

        val primaryPath = game.localPath ?: return 0
        val primaryFile = File(primaryPath)
        val parentDir = primaryFile.parentFile ?: return 0
        if (!parentDir.exists() || !parentDir.isDirectory) return 0
        if (PlatformDefinitions.getBySlug(parentDir.name) != null) return 0

        val platformDef = PlatformDefinitions.getBySlug(game.platformSlug) ?: return 0
        val validExtensions = platformDef.extensions + setOf("m3u")

        val categoryDirs = parentDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val category = VariantCategory.fromKey(dir.name.lowercase())
                if (category.isLaunchTarget && category != VariantCategory.GAME && category != VariantCategory.UNKNOWN) {
                    dir to category
                } else null
            }
            ?: emptyList()

        val candidates = categoryDirs.flatMap { (dir, category) ->
            dir.listFiles()?.filter { file ->
                val ext = file.extension.lowercase()
                file.isFile &&
                    !file.name.startsWith("._") &&
                    ext !in PATCH_EXTENSIONS &&
                    ext !in DISC_COMPONENT_EXTENSIONS &&
                    ext in validExtensions
            }?.map { it to category } ?: emptyList()
        }.sortedBy { it.first.name }

        val existingVariants = gameFileDao.getVariantsForGame(game.id)
        val launchTargetPaths = candidates.map { it.first.absolutePath }.toSet()
        for (variant in existingVariants) {
            if (variant.rommFileId != null) continue
            val path = variant.localPath
            if (path == null || path !in launchTargetPaths) {
                gameFileDao.deleteById(variant.id)
                Logger.debug(TAG, "Removed stale local variant: ${variant.fileName} for game ${game.title}")
            }
        }

        var added = 0
        for ((file, category) in candidates) {
            val matches = gameFileDao.getByGameIdAndFileName(game.id, file.name)
            val synced = matches.firstOrNull { it.rommFileId != null }
            val localRows = matches.filter { it.rommFileId == null }

            if (synced != null) {
                val syncedPathLive = synced.localPath != null && File(synced.localPath).exists()
                if (!syncedPathLive) {
                    gameFileDao.updateLocalPath(synced.id, file.absolutePath, synced.downloadedAt ?: java.time.Instant.now())
                    Logger.debug(TAG, "Adopted local file into synced variant: ${file.name} for game ${game.title}")
                }
                for (orphan in localRows) {
                    gameFileDao.deleteById(orphan.id)
                    Logger.debug(TAG, "Folded duplicate local variant into synced row: ${orphan.fileName} for game ${game.title}")
                }
                continue
            }

            if (localRows.isNotEmpty()) {
                val keeper = localRows.first()
                if (keeper.localPath != file.absolutePath) {
                    gameFileDao.updateLocalPath(keeper.id, file.absolutePath, keeper.downloadedAt ?: java.time.Instant.now())
                }
                for (extra in localRows.drop(1)) {
                    gameFileDao.deleteById(extra.id)
                }
                continue
            }

            val isM3u = file.extension.equals("m3u", ignoreCase = true)
            gameFileDao.insert(
                GameFileEntity(
                    gameId = game.id,
                    fileName = file.name,
                    filePath = file.absolutePath,
                    category = category.key,
                    fileSize = file.length(),
                    localPath = file.absolutePath,
                    downloadedAt = java.time.Instant.now(),
                    isLaunchTarget = true,
                    isMultiDisc = isM3u,
                    m3uPath = if (isM3u) file.absolutePath else null
                )
            )
            added++
            Logger.debug(TAG, "Found local variant: ${file.name} (${category.key}) in ${file.parentFile?.name} for game ${game.title}")
        }

        return added
    }
}
