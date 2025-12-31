package com.nendo.argosy.domain.usecase.download

import android.util.Log
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import javax.inject.Inject

private const val TAG = "DownloadGameUseCase"

sealed class DownloadResult {
    data object Queued : DownloadResult()
    data class MultiDiscQueued(val discCount: Int) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
    data class ExtractionFailed(
        val gameId: Long,
        val fileName: String,
        val errorReason: String
    ) : DownloadResult()
}

class DownloadGameUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val romMRepository: RomMRepository,
    private val downloadManager: DownloadManager,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val downloadQueueDao: DownloadQueueDao
) {
    suspend operator fun invoke(gameId: Long): DownloadResult {
        val game = gameDao.getById(gameId)
            ?: return DownloadResult.Error("Game not found")

        val rommId = game.rommId
            ?: return DownloadResult.Error("Game not synced from RomM")

        val failedEntry = downloadQueueDao.getByGameId(gameId)
        if (failedEntry != null &&
            failedEntry.state == DownloadState.FAILED.name &&
            failedEntry.errorReason?.contains("Extraction failed") == true
        ) {
            val targetFile = downloadManager.getDownloadPath(failedEntry.platformSlug, failedEntry.fileName)
            if (targetFile.exists()) {
                return DownloadResult.ExtractionFailed(
                    gameId = gameId,
                    fileName = failedEntry.fileName,
                    errorReason = failedEntry.errorReason
                )
            }
        }

        Log.d(TAG, "invoke: game=${game.title}, id=$gameId, rommId=$rommId, isMultiDisc=${game.isMultiDisc}, localPath=${game.localPath}")

        if (game.isMultiDisc) {
            Log.d(TAG, "invoke: taking multi-disc download path")
            return downloadMultiDiscGame(gameId, game.title, game.coverPath, game.platformSlug)
        }

        Log.d(TAG, "invoke: taking single ROM download path")

        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                var fileName = rom.fileName ?: "${game.title}.rom"

                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in INVALID_ROM_EXTENSIONS) {
                    return DownloadResult.Error("Invalid ROM file type: .$ext")
                }

                fileName = applyExtensionPreference(fileName, game.platformId, game.platformSlug)

                downloadManager.enqueueDownload(
                    gameId = gameId,
                    rommId = rommId,
                    fileName = fileName,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    coverPath = game.coverPath,
                    expectedSizeBytes = rom.fileSize
                )
                DownloadResult.Queued
            }
            is RomMResult.Error -> {
                DownloadResult.Error("Failed to get ROM info: ${result.message}")
            }
        }
    }

    private suspend fun downloadMultiDiscGame(
        gameId: Long,
        gameTitle: String,
        coverPath: String?,
        platformSlug: String
    ): DownloadResult {
        val discs = gameDiscDao.getDiscsForGame(gameId)
        Log.d(TAG, "downloadMultiDiscGame: gameId=$gameId, title=$gameTitle, discs.size=${discs.size}")
        if (discs.isEmpty()) {
            Log.w(TAG, "downloadMultiDiscGame: no discs found!")
            return DownloadResult.Error("No discs found for multi-disc game")
        }

        val discsToDownload = discs.filter { it.localPath == null }
        if (discsToDownload.isEmpty()) {
            return DownloadResult.Error("All discs already downloaded")
        }

        var queuedCount = 0
        for (disc in discsToDownload) {
            val romResult = romMRepository.getRom(disc.rommId)
            if (romResult is RomMResult.Success) {
                val rom = romResult.data
                val fileName = rom.fileName ?: disc.fileName

                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in INVALID_ROM_EXTENSIONS) continue

                downloadManager.enqueueDiscDownload(
                    gameId = gameId,
                    discId = disc.id,
                    discNumber = disc.discNumber,
                    rommId = disc.rommId,
                    fileName = fileName,
                    gameTitle = gameTitle,
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
            DownloadResult.Error("Failed to queue any disc downloads")
        }
    }

    suspend fun repairMissingDiscs(gameId: Long): DownloadResult {
        val game = gameDao.getById(gameId)
            ?: return DownloadResult.Error("Game not found")

        if (!game.isMultiDisc) {
            return DownloadResult.Error("Not a multi-disc game")
        }

        return downloadMultiDiscGame(gameId, game.title, game.coverPath, game.platformSlug)
    }

    suspend fun retryExtraction(gameId: Long): DownloadResult {
        return when (val result = downloadManager.retryExtraction(gameId)) {
            is DownloadManager.ExtractionResult.Success -> DownloadResult.Queued
            is DownloadManager.ExtractionResult.Failure -> DownloadResult.Error(result.reason)
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
        if (currentExt !in CONVERTIBLE_3DS_EXTENSIONS) return fileName
        if (currentExt == preferredExt.lowercase()) return fileName

        return fileName.replaceAfterLast('.', preferredExt)
    }

    companion object {
        private val INVALID_ROM_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp",
            "html", "htm", "txt", "pdf"
        )

        private val CONVERTIBLE_3DS_EXTENSIONS = setOf("3ds", "cci")
    }
}
