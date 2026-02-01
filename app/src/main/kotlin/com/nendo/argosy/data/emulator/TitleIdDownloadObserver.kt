package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TitleIdDownloadObserver"

private val TITLE_ID_PLATFORMS = setOf(
    "switch", "vita", "psvita", "psp", "3ds", "wiiu", "wii"
)

@Singleton
class TitleIdDownloadObserver @Inject constructor(
    private val downloadManager: DownloadManager,
    private val gameDao: GameDao,
    private val titleIdExtractor: TitleIdExtractor,
    private val emulatorResolver: EmulatorResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            downloadManager.completionEvents.collect { event ->
                handleDownloadCompletion(event)
            }
        }
        Logger.debug(TAG, "Started observing download completions for title ID extraction")
    }

    private suspend fun handleDownloadCompletion(
        event: com.nendo.argosy.data.download.DownloadCompletionEvent
    ) {
        if (event.isDiscDownload) {
            Logger.debug(TAG, "Skipped disc download for game ${event.gameId}")
            return
        }

        val game = gameDao.getById(event.gameId)
        if (game == null) {
            Logger.error(TAG, "Game ${event.gameId} not found")
            return
        }

        if (game.platformSlug !in TITLE_ID_PLATFORMS) {
            Logger.debug(TAG, "Platform ${game.platformSlug} doesn't use title IDs, skipping")
            return
        }

        if (gameDao.isTitleIdLocked(event.gameId)) {
            Logger.debug(TAG, "Title ID already locked for game ${event.gameId}, skipping extraction")
            return
        }

        val romFile = File(event.localPath)
        if (!romFile.exists()) {
            Logger.warn(TAG, "ROM file not found: ${event.localPath}")
            return
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            event.gameId,
            game.platformId,
            game.platformSlug
        )

        extractAndStoreTitleId(event.gameId, romFile, game.platformSlug, emulatorPackage)
    }

    suspend fun extractTitleIdForGame(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false

        if (game.platformSlug !in TITLE_ID_PLATFORMS) {
            return false
        }

        if (gameDao.isTitleIdLocked(gameId)) {
            Logger.debug(TAG, "Title ID already locked for game $gameId, skipping")
            return true
        }

        val romPath = game.localPath ?: return false
        val romFile = File(romPath)
        if (!romFile.exists()) {
            return false
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            gameId,
            game.platformId,
            game.platformSlug
        )

        return extractAndStoreTitleId(gameId, romFile, game.platformSlug, emulatorPackage)
    }

    private suspend fun extractAndStoreTitleId(
        gameId: Long,
        romFile: File,
        platformSlug: String,
        emulatorPackage: String?
    ): Boolean {
        val result = titleIdExtractor.extractTitleIdWithSource(romFile, platformSlug, emulatorPackage)
        if (result == null) {
            Logger.debug(TAG, "No title ID extracted for game $gameId from ${romFile.name}")
            return false
        }

        Logger.info(TAG, "Extracted title ID for game $gameId: ${result.titleId} (fromBinary=${result.fromBinary})")
        gameDao.setTitleIdWithLock(gameId, result.titleId, result.fromBinary)
        return true
    }
}
