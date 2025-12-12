package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import javax.inject.Inject

sealed class DownloadResult {
    data object Queued : DownloadResult()
    data class MultiDiscQueued(val discCount: Int) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

class DownloadGameUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val romMRepository: RomMRepository,
    private val downloadManager: DownloadManager
) {
    suspend operator fun invoke(gameId: Long): DownloadResult {
        val game = gameDao.getById(gameId)
            ?: return DownloadResult.Error("Game not found")

        val rommId = game.rommId
            ?: return DownloadResult.Error("Game not synced from RomM")

        if (game.isMultiDisc) {
            return downloadMultiDiscGame(gameId, game.title, game.coverPath, game.platformId)
        }

        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val fileName = rom.fileName ?: "${game.title}.rom"

                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in INVALID_ROM_EXTENSIONS) {
                    return DownloadResult.Error("Invalid ROM file type: .$ext")
                }

                downloadManager.enqueueDownload(
                    gameId = gameId,
                    rommId = rommId,
                    fileName = fileName,
                    gameTitle = game.title,
                    platformSlug = rom.platformSlug,
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
        platformId: String
    ): DownloadResult {
        val discs = gameDiscDao.getDiscsForGame(gameId)
        if (discs.isEmpty()) {
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
                    platformSlug = rom.platformSlug,
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

        return downloadMultiDiscGame(gameId, game.title, game.coverPath, game.platformId)
    }

    companion object {
        private val INVALID_ROM_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp",
            "html", "htm", "txt", "md", "pdf"
        )
    }
}
