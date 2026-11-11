package com.nendo.argosy.domain.usecase.download

import android.util.Log
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import java.io.File
import javax.inject.Inject

private const val TAG = "DownloadGameUseCase"

sealed class DownloadResult {
    data object Queued : DownloadResult()
    data object AlreadyDownloaded : DownloadResult()
    data class MultiDiscQueued(val discCount: Int) : DownloadResult()
    data class Error(val reason: DownloadGameFailureReason) : DownloadResult()
    data class ExtractionFailed(
        val gameId: Long,
        val fileName: String,
        val errorReason: com.nendo.argosy.data.download.DownloadFailureReason
    ) : DownloadResult()
}

/**
 * Why [DownloadGameUseCase] could not start or continue a download. `Extraction` wraps the
 * data-layer [com.nendo.argosy.data.download.DownloadFailureReason] rather than duplicating its
 * vocabulary: a sealed class's direct subclasses must share its package, so a retry failure from
 * [com.nendo.argosy.data.download.DownloadManager] cannot itself become a case here.
 */
sealed class DownloadGameFailureReason {
    data object GameNotFound : DownloadGameFailureReason()
    data object GameNotSynced : DownloadGameFailureReason()
    data class InvalidFileType(val extension: String) : DownloadGameFailureReason()
    data class RomInfoFetchFailed(val serverMessage: String) : DownloadGameFailureReason()
    data object NoDiscsFound : DownloadGameFailureReason()
    data object AllDiscsAlreadyDownloaded : DownloadGameFailureReason()
    data object NoDiscsQueued : DownloadGameFailureReason()
    data object GameNotFoundForRepair : DownloadGameFailureReason()
    data object NotMultiDisc : DownloadGameFailureReason()
    data class Extraction(val reason: com.nendo.argosy.data.download.DownloadFailureReason) : DownloadGameFailureReason()
}

class DownloadGameUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val romMRepository: RomMRepository,
    private val downloadManager: DownloadManager,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val gameRepository: GameRepository,
    private val preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    suspend operator fun invoke(
        gameId: Long,
        selectedFileIds: List<Long>? = null
    ): DownloadResult {
        val game = gameDao.getById(gameId)
            ?: return DownloadResult.Error(DownloadGameFailureReason.GameNotFound)

        val rommId = game.rommId
            ?: return DownloadResult.Error(DownloadGameFailureReason.GameNotSynced)

        if (gameRepository.validateAndDiscoverGame(gameId)) {
            Log.d(TAG, "Game already downloaded: ${game.title}")
            return DownloadResult.AlreadyDownloaded
        }

        val failedEntry = if (selectedFileIds == null) {
            val ownerUserId = syncPreferencesRepository.getRommUserId()
            downloadQueueDao.getRecoverableDownloads().filter {
                it.gameId == gameId && it.discId == null && it.gameFileId == null &&
                    it.ownerUserId == ownerUserId
            }.singleOrNull()?.takeIf { it.state == DownloadState.FAILED.name }
        } else null
        val decodedFailure = com.nendo.argosy.data.download.DownloadFailureReasonCodec.decode(failedEntry?.errorReason)
        if (failedEntry != null &&
            decodedFailure is com.nendo.argosy.data.download.DownloadFailureReason.ExtractionFailed
        ) {
            val targetFile = downloadManager.getDownloadPath(gameId, failedEntry.platformSlug, failedEntry.fileName)
            if (targetFile.exists()) {
                return DownloadResult.ExtractionFailed(
                    gameId = gameId,
                    fileName = failedEntry.fileName,
                    errorReason = decodedFailure
                )
            }
        }

        Log.d(TAG, "invoke: game=${game.title}, id=$gameId, rommId=$rommId, isMultiDisc=${game.isMultiDisc}, localPath=${game.localPath}")

        val coverPath = gameRepository.getArt(gameId).coverPath

        if (game.isMultiDisc) {
            Log.d(TAG, "invoke: taking multi-disc download path")
            return downloadMultiDiscGame(
                gameId, game.title, game.rommFileName, coverPath, game.platformSlug, selectedFileIds
            )
        }

        Log.d(TAG, "invoke: taking single ROM download path")

        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val allFiles = rom.files?.filter { it.isGameContent } ?: emptyList()
                val selection = resolveSelection(allFiles, selectedFileIds, game.platformSlug)

                var fileName: String
                val expectedSize: Long

                if (selection != null && selection.size == 1) {
                    val only = allFiles.first { it.id == selection.first() }
                    fileName = only.fileName
                    expectedSize = only.fileSizeBytes
                } else if (selection != null || rom.needsServerBuiltZipExtraction) {
                    val folderName = rom.fileName ?: game.title
                    fileName = if (selection == null && folderName.contains('.')) folderName
                        else "$folderName.zip"
                    expectedSize = if (selection != null) {
                        allFiles.filter { it.id in selection }.sumOf { it.fileSizeBytes }
                    } else rom.fileSize
                } else {
                    val mainFile = allFiles.maxByOrNull { it.fileSizeBytes }
                    fileName = mainFile?.fileName
                        ?: rom.fileName
                        ?: "${game.title}.rom"
                    expectedSize = mainFile?.fileSizeBytes ?: rom.fileSize
                }

                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in INVALID_ROM_EXTENSIONS && !isPico8Cart(fileName, game.platformSlug)) {
                    return DownloadResult.Error(DownloadGameFailureReason.InvalidFileType(ext))
                }

                fileName = applyExtensionPreference(fileName, game.platformId, game.platformSlug)

                downloadManager.enqueueDownload(
                    gameId = gameId,
                    rommId = rommId,
                    fileName = fileName,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    coverPath = coverPath,
                    expectedSizeBytes = expectedSize,
                    isMultiFileRom = rom.needsServerBuiltZipExtraction && selection == null,
                    selectedFileIds = selection
                )
                DownloadResult.Queued
            }
            is RomMResult.Error -> {
                DownloadResult.Error(DownloadGameFailureReason.RomInfoFetchFailed(result.message))
            }
        }
    }

    /**
     * Explicit picker selection wins; otherwise category defaults trim the file
     * set. Null means download everything exactly as before.
     *
     * A root-level file the server left uncategorised is content unless it is a companion, which
     * falls to the same Other default a nested one already uses. Selecting by depth alone made a
     * scene note beside the rom count as content, which sent a single-file game down the
     * server-built zip path.
     */
    private suspend fun resolveSelection(
        allFiles: List<com.nendo.argosy.data.remote.romm.RomMRomFile>,
        explicit: List<Long>?,
        platformSlug: String
    ): List<Long>? {
        if (explicit != null) {
            return explicit.filter { id -> allFiles.any { it.id == id } }
                .takeIf { it.isNotEmpty() }
        }
        if (allFiles.size <= 1) return null
        val defaults = preferencesRepository.getEffectiveDownloadDefaults(platformSlug)
        val rootLen = allFiles.minOf { it.filePath.length }
        val selected = allFiles.filter { f ->
            val cat = f.category
            when {
                cat == com.nendo.argosy.data.model.VariantCategory.GAME.key -> true
                cat == null && f.filePath.length == rootLen &&
                    !com.nendo.argosy.data.download.ZipExtractor.isCompanionFileName(f.fileName) -> true
                cat == null ->
                    defaults[com.nendo.argosy.data.preferences.DownloadDefaults.OTHER_KEY] ?: false
                else -> defaults[cat] ?: false
            }
        }
        if (selected.size == allFiles.size || selected.isEmpty()) return null
        return selected.map { it.id }
    }

    private suspend fun downloadMultiDiscGame(
        gameId: Long,
        gameTitle: String,
        gameFolderName: String?,
        coverPath: String?,
        platformSlug: String,
        selectedFileIds: List<Long>? = null
    ): DownloadResult {
        val discs = gameDiscDao.getDiscsForGame(gameId)
        Log.d(TAG, "downloadMultiDiscGame: gameId=$gameId, title=$gameTitle, discs.size=${discs.size}")
        if (discs.isEmpty()) {
            Log.w(TAG, "downloadMultiDiscGame: no discs found!")
            return DownloadResult.Error(DownloadGameFailureReason.NoDiscsFound)
        }

        val discsToDownload = discs.filter { disc -> disc.localPath?.let { File(it).exists() } != true }
        if (discsToDownload.isEmpty()) {
            return DownloadResult.Error(DownloadGameFailureReason.AllDiscsAlreadyDownloaded)
        }

        val filesByName = gameFileDao.getFilesForGame(gameId).associateBy { it.fileName }
        var queuedCount = 0
        for (disc in discsToDownload) {
            val folderFile = disc.parentRommId?.let { filesByName[disc.fileName] }
            if (folderFile?.rommFileId != null) {
                if (selectedFileIds != null && folderFile.rommFileId !in selectedFileIds) continue
                downloadManager.enqueueGameFileDownload(
                    gameId = gameId,
                    gameFileId = folderFile.id,
                    rommFileId = folderFile.rommFileId,
                    fileName = folderFile.fileName,
                    category = folderFile.category,
                    gameTitle = gameTitle,
                    platformSlug = platformSlug,
                    coverPath = coverPath,
                    expectedSizeBytes = folderFile.fileSize,
                    gameFolderName = gameFolderName
                )
                queuedCount++
                continue
            }
            if (selectedFileIds != null) continue
            val romResult = romMRepository.getRom(disc.rommId)
            if (romResult is RomMResult.Success) {
                val rom = romResult.data
                val fileName = rom.fileName ?: disc.fileName

                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in INVALID_ROM_EXTENSIONS && !isPico8Cart(fileName, platformSlug)) continue

                downloadManager.enqueueDiscDownload(
                    gameId = gameId,
                    discId = disc.id,
                    discNumber = disc.discNumber,
                    rommId = disc.rommId,
                    fileName = fileName,
                    gameTitle = gameTitle,
                    gameFolderName = gameFolderName,
                    platformSlug = platformSlug,
                    coverPath = coverPath,
                    expectedSizeBytes = rom.fileSize
                )
                queuedCount++
            }
        }

        return if (queuedCount > 0) {
            DownloadResult.MultiDiscQueued(queuedCount)
        } else {
            DownloadResult.Error(DownloadGameFailureReason.NoDiscsQueued)
        }
    }

    suspend fun repairMissingDiscs(gameId: Long): DownloadResult {
        val game = gameDao.getById(gameId)
            ?: return DownloadResult.Error(DownloadGameFailureReason.GameNotFoundForRepair)

        if (!game.isMultiDisc) {
            return DownloadResult.Error(DownloadGameFailureReason.NotMultiDisc)
        }

        val coverPath = gameRepository.getArt(gameId).coverPath
        return downloadMultiDiscGame(gameId, game.title, game.rommFileName, coverPath, game.platformSlug)
    }

    suspend fun retryExtraction(gameId: Long): DownloadResult {
        return when (val result = downloadManager.retryExtraction(gameId)) {
            is DownloadManager.ExtractionResult.Success -> DownloadResult.Queued
            is DownloadManager.ExtractionResult.Failure ->
                DownloadResult.Error(DownloadGameFailureReason.Extraction(result.reason))
        }
    }

    suspend fun redownload(gameId: Long): DownloadResult {
        downloadManager.deleteFileAndRedownload(gameId)
        return invoke(gameId)
    }

    private suspend fun applyExtensionPreference(
        fileName: String,
        platformId: Long,
        platformSlug: String
    ): String {
        if (platformSlug.lowercase() != "3ds") return fileName

        val preferredExt = emulatorConfigDao.getPreferredExtension(platformId)
            ?.ifEmpty { null }
            ?: return fileName

        val currentExt = fileName.substringAfterLast('.', "").lowercase()
        val group = INTERCHANGEABLE_3DS_EXTENSIONS.firstOrNull { currentExt in it } ?: return fileName
        if (preferredExt.lowercase() !in group) return fileName
        if (currentExt == preferredExt.lowercase()) return fileName

        return fileName.replaceAfterLast('.', preferredExt)
    }

    private fun isPico8Cart(fileName: String, platformSlug: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".p8.png") ||
            (PlatformDefinitions.getCanonicalSlug(platformSlug) == "pico8" &&
                lower.substringAfterLast('.', "") == "png")
    }

    companion object {
        private val INVALID_ROM_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp",
            "html", "htm", "txt", "pdf"
        )

        /**
         * Extensions that name the same container, so one may be swapped for another. Compressed
         * and plain forms are separate groups: renaming across them would describe bytes the file
         * does not hold.
         */
        private val INTERCHANGEABLE_3DS_EXTENSIONS = listOf(
            setOf("3ds", "cci"),
            setOf("z3ds", "zcci")
        )
    }
}
