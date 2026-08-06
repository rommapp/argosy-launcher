package com.nendo.argosy.data.download

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExtContentOrganizer"
private const val EDEN_BASE_ID = "eden"
private val ADDON_CATEGORIES = setOf(VariantCategory.UPDATE, VariantCategory.DLC)

/**
 * Eden auto-discovers updates and DLC from an `extcontent/` folder beside the base rom, so every
 * path that puts Switch add-on content on disk asks here where it belongs. Family variants (forks,
 * nightlies, early-access packages) collapse to their base id before the check, since only the
 * canonical package carries the bare id.
 */
@Singleton
class ExtContentOrganizer @Inject constructor(
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val emulatorResolver: EmulatorResolver
) {

    /**
     * Combine Content: this platform keeps every base rom directly in the platform folder and
     * every update and DLC in one shared `extcontent/` beside them, so Eden sees the whole
     * library at once and controls activation itself. Switch only, off unless the user asks.
     */
    suspend fun usesCombinedLayout(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false
        if (!usesExtcontent(gameId, game.platformSlug)) return false
        return usesCombinedLayout(game.platformId, game.platformSlug)
    }

    suspend fun usesCombinedLayout(platformId: Long, platformSlug: String): Boolean {
        if (!ZipExtractor.isNswPlatform(platformSlug)) return false
        return platformDao.getCombineContent(platformId) == true
    }

    fun isAddonCategory(category: String?): Boolean =
        VariantCategory.fromKey(category?.lowercase()) in ADDON_CATEGORIES

    suspend fun placesInExtcontent(gameId: Long, platformSlug: String, category: String?): Boolean =
        isAddonCategory(category) && usesExtcontent(gameId, platformSlug)

    suspend fun usesExtcontent(gameId: Long, platformSlug: String): Boolean {
        if (!ZipExtractor.isNswPlatform(platformSlug)) return false
        return try {
            val game = gameDao.getById(gameId) ?: return false
            val emulatorId = emulatorResolver
                .getEmulatorIdForGame(gameId, game.platformId, platformSlug) ?: return false
            isExtcontentEmulator(emulatorId, platformSlug)
        } catch (e: Exception) {
            Logger.warn(TAG, "usesExtcontent: resolve failed for game $gameId: ${e.message}")
            false
        }
    }

    fun isExtcontentEmulator(emulatorId: String, platformSlug: String): Boolean =
        ZipExtractor.isNswPlatform(platformSlug) &&
            EmulatorRegistry.familyBaseIdFor(emulatorId) == EDEN_BASE_ID

    /**
     * Flattens one game into the Combine Content layout: the base rom moves to [platformDir], its
     * updates and DLC move to the shared `extcontent/`, and every row naming an old path follows.
     * A name already taken at either destination aborts the whole game rather than overwriting,
     * so a collision leaves the existing folder untouched instead of half-moved.
     */
    suspend fun enforceCombinedLayout(game: GameEntity, platformDir: File): File? {
        if (game.isMultiDisc) return null
        val romPath = game.localPath ?: return null
        val romFile = File(romPath)
        if (!romFile.isFile) return null
        val gameFolder = romFile.parentFile ?: return null
        if (!isOwnGameFolder(gameFolder, platformDir)) return null

        val sharedExtcontent = File(platformDir, ZipExtractor.EXTCONTENT_FOLDER)
        val baseFile = electBase(game, romFile, gameFolder) ?: return null
        val moves = gameFolder.walkTopDown()
            .filter { it.isFile && !it.name.startsWith("._") }
            .mapNotNull { file ->
                when {
                    file.parentFile?.name?.lowercase() in ZipExtractor.ADDON_FOLDERS ->
                        file to File(sharedExtcontent, file.name)
                    file.absolutePath == baseFile.absolutePath -> file to File(platformDir, file.name)
                    else -> null
                }
            }
            .toList()

        val blocked = moves.firstOrNull { (_, target) -> target.exists() }?.second
            ?: moves.groupBy { (_, target) -> target.absolutePath }
                .values.firstOrNull { it.size > 1 }?.first()?.second
        if (blocked != null) {
            Logger.warn(
                TAG,
                "enforceCombinedLayout: ${blocked.name} is already claimed in " +
                    "${blocked.parentFile?.name}; leaving ${game.title} in ${gameFolder.name}"
            )
            return null
        }

        if (moves.any { (_, target) -> target.parentFile == sharedExtcontent }) sharedExtcontent.mkdirs()

        val completed = mutableListOf<Pair<File, File>>()
        for (move in moves) {
            if (move.first.renameTo(move.second)) {
                completed += move
                continue
            }
            Logger.warn(
                TAG,
                "enforceCombinedLayout: could not move ${move.first.name}; rolling back ${game.title}"
            )
            for ((source, target) in completed) target.renameTo(source)
            return null
        }

        for ((source, target) in completed) {
            gameFileDao.updateLocalPathByOldPath(source.absolutePath, target.absolutePath)
        }

        val movedBase = File(platformDir, baseFile.name)
        gameDao.updateLocalPath(game.id, movedBase.absolutePath, game.source)

        val kept = gameFolder.walkTopDown().filter { it.isFile && !it.name.startsWith("._") }.count()
        if (kept == 0) {
            gameFolder.deleteRecursively()
        } else {
            Logger.info(
                TAG,
                "enforceCombinedLayout: keeping ${gameFolder.name} for $kept non-game file(s)"
            )
        }
        Logger.info(
            TAG,
            "enforceCombinedLayout: flattened ${game.title} into ${platformDir.name} " +
                "(${moves.size} files)"
        )
        return movedBase
    }

    /**
     * Which file the flattened game launches from. Once the rom sits in the platform folder,
     * [GameLauncher.resolveBaseRomFile] refuses to re-elect - "largest file here" would pick a
     * neighbour's rom - so the base has to be right at the moment of the move. The game's own
     * `game` row wins; otherwise the file it already launched from, unless that is a playlist this
     * platform cannot boot, in which case the largest non-add-on file we just moved.
     */
    private suspend fun electBase(game: GameEntity, currentBase: File, gameFolder: File): File? {
        val inFolder = { file: File -> file.isFile && file.parentFile == gameFolder }

        gameFileDao.getFilesForGame(game.id)
            .firstOrNull { VariantCategory.fromKey(it.category) == VariantCategory.GAME }
            ?.localPath
            ?.let(::File)
            ?.takeIf(inFolder)
            ?.let { return it }

        val currentIsPlaylist = currentBase.extension.equals("m3u", ignoreCase = true) &&
            !M3uManager.supportsM3u(game.platformSlug)
        if (!currentIsPlaylist && inFolder(currentBase)) return currentBase

        return gameFolder.listFiles()
            ?.filter { it.isFile && !it.name.startsWith("._") }
            ?.filterNot { it.extension.equals("m3u", ignoreCase = true) }
            ?.maxByOrNull { it.length() }
    }

    /**
     * Games the Combine Content layout is actually holding flat: the base rom sits in
     * [platformDir] itself and at least one of the game's own updates or DLC is pooled in the
     * shared `extcontent/`. A single-file game, or one whose only extra content is a soundtrack or
     * other non-game media, has nothing to gain from its own folder and is left where it is.
     */
    suspend fun gamesHoldingCombinedLayout(platformId: Long, platformDir: File): List<GameEntity> {
        val shared = File(platformDir, ZipExtractor.EXTCONTENT_FOLDER)
        if (!shared.isDirectory) return emptyList()
        return gameDao.getDownloadedByPlatform(platformId).filter { game ->
            val base = game.localPath?.let { File(it) } ?: return@filter false
            if (!base.isFile || base.parentFile?.absolutePath != platformDir.absolutePath) return@filter false
            pooledAddonsOf(game, shared).isNotEmpty()
        }
    }

    private suspend fun pooledAddonsOf(game: GameEntity, shared: File): List<File> =
        gameFileDao.getFilesForGame(game.id)
            .filter { isAddonCategory(it.category) }
            .mapNotNull { it.localPath?.let(::File) }
            .filter { it.isFile && it.parentFile?.absolutePath == shared.absolutePath }

    /**
     * Undoes the flatten for one game: the base rom moves back into its own folder and the add-ons
     * this game owns follow it into that folder's `extcontent/`. Only paths the game's own rows
     * name are moved, so add-ons pooled by other games stay put. Returns files moved.
     */
    suspend fun restoreFromCombinedLayout(game: GameEntity, platformDir: File): Int {
        val basePath = game.localPath ?: return 0
        val baseFile = File(basePath)
        if (!baseFile.isFile || baseFile.parentFile?.absolutePath != platformDir.absolutePath) return 0

        val shared = File(platformDir, ZipExtractor.EXTCONTENT_FOLDER)
        val addons = pooledAddonsOf(game, shared)
        if (addons.isEmpty()) return 0

        val folderName = (game.rommFileName?.takeIf { it.isNotBlank() } ?: game.title)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
        val gameFolder = File(platformDir, folderName)
        if (gameFolder.exists()) {
            Logger.warn(TAG, "restoreFromCombinedLayout: ${gameFolder.name} already exists; skipping ${game.title}")
            return 0
        }
        val gameExtcontent = File(gameFolder, ZipExtractor.EXTCONTENT_FOLDER)
        if (!gameExtcontent.mkdirs()) {
            Logger.warn(TAG, "restoreFromCombinedLayout: could not create ${gameFolder.name}")
            return 0
        }

        val moves = listOf(baseFile to File(gameFolder, baseFile.name)) +
            addons.map { it to File(gameExtcontent, it.name) }

        val completed = mutableListOf<Pair<File, File>>()
        for (move in moves) {
            if (move.first.renameTo(move.second)) {
                completed += move
                continue
            }
            Logger.warn(TAG, "restoreFromCombinedLayout: could not move ${move.first.name}; rolling back ${game.title}")
            for ((source, target) in completed) target.renameTo(source)
            gameFolder.deleteRecursively()
            return 0
        }

        for ((source, target) in completed) {
            gameFileDao.updateLocalPathByOldPath(source.absolutePath, target.absolutePath)
        }
        gameDao.updateLocalPath(game.id, File(gameFolder, baseFile.name).absolutePath, game.source)
        Logger.info(TAG, "restoreFromCombinedLayout: ${game.title} back in ${gameFolder.name} (${moves.size} files)")
        return moves.size
    }

    /**
     * True only for a folder sitting directly inside [platformDir]. Anything else - the platform
     * root itself, a game found under an alternate rom root, a locally scanned folder elsewhere on
     * disk - is not ours to empty and delete, however much it looks like a game folder.
     */
    private fun isOwnGameFolder(gameFolder: File, platformDir: File): Boolean {
        val folder = runCatching { gameFolder.canonicalFile }.getOrDefault(gameFolder.absoluteFile)
        val root = runCatching { platformDir.canonicalFile }.getOrDefault(platformDir.absoluteFile)
        return folder != root && folder.parentFile == root
    }

    /**
     * Moves the per-category add-on folders beside [romPath] into `extcontent/` and repoints every
     * file row that named the old location. Returns the number of files moved.
     */
    suspend fun consolidate(romPath: String, platformDir: File? = null): Int {
        val rom = File(romPath)
        val gameFolder = (if (rom.isDirectory) rom else rom.parentFile) ?: return 0
        if (!gameFolder.isDirectory) return 0
        if (platformDir != null && !isOwnGameFolder(gameFolder, platformDir)) return 0

        val sourceFolders = gameFolder.listFiles { file ->
            file.isDirectory && file.name.lowercase() in ZipExtractor.ADDON_SOURCE_FOLDERS
        } ?: return 0
        if (sourceFolders.isEmpty()) return 0

        val extcontent = File(gameFolder, ZipExtractor.EXTCONTENT_FOLDER).apply { mkdirs() }
        var movedCount = 0

        for (folder in sourceFolders) {
            val files = folder.listFiles() ?: continue
            for (file in files) {
                if (!file.isFile) continue
                val source = file.absolutePath
                val target = File(extcontent, file.name)
                if (file.renameTo(target)) {
                    gameFileDao.updateLocalPathByOldPath(source, target.absolutePath)
                    movedCount++
                } else {
                    Logger.warn(TAG, "consolidate: failed to move ${file.name}")
                }
            }
            if (folder.listFiles().isNullOrEmpty()) folder.delete()
        }

        if (movedCount > 0) {
            Logger.info(TAG, "consolidate: moved $movedCount files to extcontent/ in ${gameFolder.name}")
        }
        return movedCount
    }
}
