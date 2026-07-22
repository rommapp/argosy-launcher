package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.model.VersionGroups
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private fun pathOnDisk(path: String?): Boolean = path != null && File(path).exists()

private val GameFileEntity.isOnDisk: Boolean get() = pathOnDisk(localPath)

data class VariantOption(
    val fileId: Long?,
    val fileName: String,
    val category: String,
    val isDownloaded: Boolean,
    val isMultiDisc: Boolean,
    val fileSizeBytes: Long
)

@Singleton
class VariantResolver @Inject constructor(
    private val gameFileDao: GameFileDao
) {
    suspend fun resolveVariant(game: GameEntity): GameFileEntity? {
        val excluded = game.platformSlug in VariantCategory.VARIANT_EXCLUDED_PLATFORMS

        game.activeVariantFileId?.let { fileId ->
            val file = gameFileDao.getById(fileId)
            if (file != null && file.isOnDisk &&
                (!excluded || file.versionGroup != null)
            ) return file
        }

        game.lastPlayedFileId?.let { fileId ->
            val file = gameFileDao.getById(fileId)
            if (file != null && file.isOnDisk &&
                (!excluded || file.versionGroup != null)
            ) return file
        }

        return null
    }

    suspend fun getVariantOptions(game: GameEntity): List<VariantOption>? {
        val excluded = game.platformSlug in VariantCategory.VARIANT_EXCLUDED_PLATFORMS
        val allFiles = gameFileDao.getFilesForGame(game.id)
        val grouped = allFiles.filter { it.versionGroup != null }.groupBy { it.versionGroup!! }

        val versionOptions = grouped.mapNotNull { (_, groupFiles) ->
            val launch = VersionGroups.launchFile(groupFiles) ?: return@mapNotNull null
            VariantOption(
                fileId = launch.id,
                fileName = groupFiles.firstOrNull { it.regions != null }?.regions
                    ?: launch.fileName,
                category = VariantCategory.GAME.key,
                isDownloaded = launch.isOnDisk,
                isMultiDisc = groupFiles.any { it.isMultiDisc },
                fileSizeBytes = groupFiles.sumOf { it.fileSize }
            )
        }.sortedBy { it.fileName }

        if (excluded) return versionOptions.takeIf { it.size > 1 }

        val primaryFileName = game.rommFileName ?: game.localPath?.substringAfterLast('/')
        val variants = gameFileDao.getVariantsForGame(game.id)
            .filter { it.versionGroup == null }
            .filterNot { primaryFileName != null && it.fileName == primaryFileName }

        if (versionOptions.isEmpty() && variants.isEmpty()) return null

        val head = versionOptions.ifEmpty {
            listOf(
                VariantOption(
                    fileId = null,
                    fileName = primaryFileName ?: game.title,
                    category = VariantCategory.GAME.key,
                    isDownloaded = pathOnDisk(game.localPath),
                    isMultiDisc = game.isMultiDisc,
                    fileSizeBytes = game.fileSizeBytes ?: 0
                )
            )
        }

        val variantOptions = variants.map { file ->
            VariantOption(
                fileId = file.id,
                fileName = file.fileName,
                category = file.category,
                isDownloaded = file.isOnDisk,
                isMultiDisc = file.isMultiDisc,
                fileSizeBytes = file.fileSize
            )
        }

        return (head + variantOptions).takeIf { it.size > 1 }
    }
}
