package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.util.Logger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a ROM's file list - discs, updates, DLC and soundtrack tracks - into `game_files`.
 *
 * Shared by the library sync, which asks for files with `with_files`, and the per-game refresh,
 * which gets them from the single-ROM endpoint.
 *
 * A consolidated game holds every absorbed sibling's files under one gameId, so this only ever
 * prunes and rewrites the rows belonging to the rom it was handed. `versionGroup` and `regions`
 * carry over from the stored row because only version consolidation knows them.
 *
 * Every file the server reports is recorded. Which of them a platform offers or downloads by
 * default is a decision for the download and variant layers; dropping references here left
 * title-id platforms with no soundtrack rows at all, so nothing could play a game's theme.
 */
@Singleton
class RomMGameFileSync @Inject constructor(
    private val gameFileDao: GameFileDao,
    private val musicDirectoryManager: MusicDirectoryManager
) {
    companion object {
        private const val TAG = "RomMGameFileSync"
    }

    /**
     * An absent file list means the response did not carry one; it is not a statement that
     * the game has no files. Only a response that actually enumerated files may remove rows.
     *
     * A root game file is reported with no category on RomM 4.9 and as `game` from 5.0, so
     * shape alone cannot classify it. Depth does: anything below the rom's own directory
     * that the server did not categorise is content we do not model, never a launch target.
     */
    suspend fun sync(gameId: Long, rom: RomMRom, platformSlug: String, fileListIsAuthoritative: Boolean) {
        val files = rom.files?.filter { file ->
            !file.fileName.startsWith(".")
        } ?: return
        val rootPathLength = files.minOfOrNull { it.filePath.length }

        if (files.isEmpty()) {
            if (!fileListIsAuthoritative) {
                Logger.debug(TAG, "sync: no files in this response, keeping existing rows | gameId=$gameId")
                return
            }
            gameFileDao.deleteByGameId(gameId)
            return
        }

        val validIds = files.mapNotNull { if (it.id > 0) it.id else null }
        if (validIds.isNotEmpty() && fileListIsAuthoritative) {
            gameFileDao.deleteInvalidFilesForRom(gameId, rom.id, validIds)
        }

        val entities = files.map { file ->
            val existing = gameFileDao.getByRommFileId(file.id)
            val isNested = rootPathLength != null && file.filePath.length > rootPathLength
            val category = when {
                file.category != null -> VariantCategory.fromKey(file.category)
                isNested -> VariantCategory.UNKNOWN
                else -> VariantCategory.GAME
            }
            val localPath = existing?.localPath ?: recoverMusicLocalPath(file, category, rom)
            GameFileEntity(
                id = existing?.id ?: 0,
                gameId = gameId,
                rommFileId = file.id,
                romId = file.romId,
                fileName = file.fileName,
                filePath = file.filePath,
                category = category.key,
                fileSize = file.fileSizeBytes,
                localPath = localPath,
                downloadedAt = existing?.downloadedAt ?: localPath?.let { Instant.now() },
                isLaunchTarget = category.isLaunchTarget && !(isNested && file.category == null),
                isMultiDisc = existing?.isMultiDisc ?: false,
                m3uPath = existing?.m3uPath,
                regions = existing?.regions,
                versionGroup = existing?.versionGroup,
                trackTitle = file.trackMeta?.title ?: existing?.trackTitle,
                trackNumber = file.trackMeta?.track ?: existing?.trackNumber,
                durationSeconds = file.trackMeta?.durationSeconds ?: existing?.durationSeconds
            )
        }
        gameFileDao.insertAll(entities)
    }

    private suspend fun recoverMusicLocalPath(
        file: RomMRomFile,
        category: VariantCategory,
        rom: RomMRom
    ): String? {
        if (category != VariantCategory.SOUNDTRACK) return null
        val target = musicDirectoryManager.targetFileFor(
            platformName = rom.platformName ?: rom.platformSlug,
            gameName = rom.name,
            trackNumber = file.trackMeta?.track,
            title = file.trackMeta?.title,
            fileName = file.fileName
        )
        return target.takeIf { it.exists() }?.absolutePath
    }
}
