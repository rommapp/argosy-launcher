package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TitleIdDownloadObserver"

private val ADDON_SUBFOLDERS = setOf(
    "mods", "mod",
    "updates", "update",
    "dlc",
    "hacks", "hack",
    "patches", "patch",
    "demos", "demo",
    "translations", "translation",
    ZipExtractor.EXTCONTENT_FOLDER
)

private fun hasTitleId(platformSlug: String) =
    com.nendo.argosy.data.platform.PlatformDefinitions.hasTitleId(platformSlug)

sealed interface TitleIdRecheck {
    data class Found(val titleId: String, val replaced: String?) : TitleIdRecheck
    data class NotFound(val fileName: String) : TitleIdRecheck
    data object NoFile : TitleIdRecheck
    data object Unsupported : TitleIdRecheck
}

@Singleton
class TitleIdDownloadObserver @Inject constructor(
    private val downloadManager: DownloadManager,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val titleIdExtractor: TitleIdExtractor,
    private val emulatorResolver: EmulatorResolver,
    private val baseRomFileResolver: BaseRomFileResolver,
    private val cartFeatureScanner: CartFeatureScanner,
    private val texturePackInstaller: TexturePackInstaller
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "TitleIdDownloadObserver")

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

        val isAddonDownload = isAddonDownloadPath(event.localPath)
        if (!isAddonDownload && cartFeatureScanner.appliesTo(game.platformSlug)) {
            cartFeatureScanner.scan(game.copy(localPath = game.localPath ?: event.localPath))
        }

        val extractedTitleId = extractTitleIdOnDownload(event, game, isAddonDownload)
        texturePackInstaller.invalidateFailedInstallsForGame(event.gameId)
        texturePackInstaller.installPendingModFilesForGame(event.gameId, extractedTitleId)
    }

    private suspend fun isAddonDownloadPath(localPath: String): Boolean {
        val parentName = File(localPath).parentFile?.name?.lowercase(Locale.ROOT)
        if (parentName != null && parentName in ADDON_SUBFOLDERS) return true
        val recordedCategory = gameFileDao.getByLocalPath(localPath)?.category
        return recordedCategory != null && VariantCategory.fromKey(recordedCategory) != VariantCategory.GAME
    }

    private suspend fun extractTitleIdOnDownload(
        event: com.nendo.argosy.data.download.DownloadCompletionEvent,
        game: GameEntity,
        isAddonDownload: Boolean
    ): TitleIdResult? {
        if (!hasTitleId(game.platformSlug)) {
            Logger.debug(TAG, "Platform ${game.platformSlug} doesn't use title IDs, skipping")
            return null
        }

        if (gameDao.isTitleIdLocked(event.gameId)) {
            Logger.debug(TAG, "Title ID already locked for game ${event.gameId}, skipping extraction")
            return null
        }

        if (!game.titleId.isNullOrBlank() && !game.saveId.isNullOrBlank()) {
            Logger.debug(TAG, "Title ID and save ID already present for game ${event.gameId}, skipping extraction")
            return null
        }

        val romFile = resolveGameFile(game)
            ?: File(event.localPath).takeUnless { isAddonDownload }
        if (romFile == null || !romFile.exists()) {
            Logger.warn(TAG, "ROM file not found: ${event.localPath}")
            return null
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            event.gameId,
            game.platformId,
            game.platformSlug
        )

        return extractAndStoreTitleId(event.gameId, romFile, game.platformSlug, emulatorPackage)
    }

    suspend fun extractTitleIdForGame(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false

        if (!hasTitleId(game.platformSlug)) {
            return false
        }

        if (gameDao.isTitleIdLocked(gameId)) {
            Logger.debug(TAG, "Title ID already locked for game $gameId, skipping")
            return true
        }

        val romFile = resolveGameFile(game) ?: return false

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            gameId,
            game.platformId,
            game.platformSlug
        )

        return extractAndStoreTitleId(gameId, romFile, game.platformSlug, emulatorPackage) != null
    }

    /**
     * Reads the title id off the game's own file again, past an established lock.
     *
     * Extraction gains platforms and formats over time, so the id a game carries is only as good
     * as the reader that produced it, and a recheck has to be able to replace one.
     */
    suspend fun recheckTitleId(gameId: Long): TitleIdRecheck {
        val game = gameDao.getById(gameId) ?: return TitleIdRecheck.Unsupported

        if (!hasTitleId(game.platformSlug)) {
            return TitleIdRecheck.Unsupported
        }

        val romFile = resolveGameFile(game) ?: return TitleIdRecheck.NoFile

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            gameId,
            game.platformId,
            game.platformSlug
        )

        val result = extractAndStoreTitleId(gameId, romFile, game.platformSlug, emulatorPackage)
            ?: return TitleIdRecheck.NotFound(romFile.name)
        return TitleIdRecheck.Found(result.titleId, game.titleId?.takeIf { it != result.titleId })
    }

    private suspend fun resolveGameFile(game: GameEntity): File? {
        if (game.isMultiDisc) return firstDiscOnDisk(game.id)
        val recorded = File(game.localPath ?: return null)
        if (!recorded.exists()) return null
        return baseRomFileResolver.resolve(game, recorded).takeIf { it.exists() }
    }

    private suspend fun firstDiscOnDisk(gameId: Long): File? {
        val firstDisc = gameDiscDao.getDiscsForGame(gameId).minByOrNull { it.discNumber } ?: return null
        return firstDisc.localPath?.let(::File)
            ?.takeIf { it.isFile && !it.extension.equals("m3u", ignoreCase = true) }
    }

    private suspend fun extractAndStoreTitleId(
        gameId: Long,
        romFile: File,
        platformSlug: String,
        emulatorPackage: String?
    ): TitleIdResult? {
        val result = titleIdExtractor.extractTitleIdWithSource(romFile, platformSlug, emulatorPackage)
        if (result == null) {
            Logger.debug(TAG, "No title ID extracted for game $gameId from ${romFile.name}")
            return null
        }

        Logger.info(TAG, "Extracted title ID for game $gameId: ${result.titleId} (saveId=${result.saveId}, fromBinary=${result.fromBinary})")
        gameDao.setTitleAndSaveIdWithLock(gameId, result.titleId, result.saveId, result.fromBinary)
        return result
    }
}
