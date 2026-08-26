package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "N3dsSaveCaseRepair"

/**
 * Reunites the two directories a 3DS save can be split across.
 *
 * Argosy wrote `title/00040000/0011C500` while the emulator writes `title/00040000/0011c500`, and
 * internal storage is case-sensitive, so both directories exist independently and hold different
 * progress. Correcting the case of new save ids stops the split growing; it does not move what is
 * already on disk, which is what this does.
 *
 * The emulator's own directory is authoritative whenever it exists, because that is the copy the
 * game read and wrote. Nothing is ever deleted: a copy that has to give way is renamed to a
 * [DISPLACED_SUFFIX] sibling beside itself, which no lookup matches and the user can still open.
 * Scoped to 3DS, the one platform whose save id nests as two case-bearing hex path segments.
 */
@Singleton
class N3dsSaveCaseRepair @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val gameDao: GameDao,
    private val saveSyncDao: SaveSyncDao
) {

    /**
     * Reconciles every 3DS save tree this game could live under. Safe to call on every launch: a
     * device with one directory per save does nothing and writes nothing.
     */
    suspend fun repairIfNeeded(
        gameId: Long,
        emulatorId: String,
        emulatorPackage: String?
    ) = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext
        if (PlatformDefinitions.getCanonicalSlug(game.platformSlug) != PLATFORM_SLUG) return@withContext

        val saveId = splitSaveId(game.saveId ?: game.titleId) ?: return@withContext
        val handler = saveHandlerRegistry.getFolderHandler(PLATFORM_SLUG) ?: return@withContext
        val config = emulatorPackage?.let { SavePathRegistry.getConfigForPlatformByPackage(it, game.platformSlug) }
            ?: SavePathRegistry.getConfigForPlatform(emulatorId, game.platformSlug)
            ?: return@withContext

        val override = emulatorSaveConfigRepository.resolveUserSavePath(config.emulatorId, game.platformSlug)
        val basePaths = buildList {
            if (override != null) handler.resolveBasePath(config, override)?.let { add(it) }
            addAll(
                SavePathRegistry.resolvePathWithPackage(
                    config,
                    emulatorPackage,
                    context.filesDir.absolutePath,
                    fal.externalStorageRoots()
                )
            )
        }.distinct()

        var changed = false
        for (basePath in basePaths) {
            for (titleRoot in titleRootsUnder(basePath)) {
                if (reconcile(titleRoot, saveId, game.id, game.title)) changed = true
            }
        }

        if (changed) {
            saveSyncDao.clearLocalPathsForGame(gameId)
            Logger.info(
                TAG,
                "[SaveSync] MIGRATE | Dropped the recorded save path so the next pass rediscovers it | " +
                    "gameId=$gameId, game=${game.title}"
            )
        }
    }

    private fun titleRootsUnder(basePath: String): List<String> {
        if (!isDirectory(basePath)) return emptyList()
        return fal.listFiles(basePath).orEmpty()
            .filter { it.isDirectory }
            .flatMap { id0 -> fal.listFiles(id0.path).orEmpty().filter { it.isDirectory } }
            .map { "${it.path}/$TITLE_DIR" }
            .filter { isDirectory(it) }
    }

    /**
     * The four states one title root can be in, and what each does. Only ours: it is carried into
     * the name the emulator reads. Only the emulator's: nothing happens. Both, identical: ours is
     * moved aside as a duplicate. Both, diverged: the emulator's stays and ours is moved aside
     * under its own name, so the progress it holds survives and can be restored by hand.
     */
    private fun reconcile(titleRoot: String, saveId: SaveIdSplit, gameId: Long, gameTitle: String): Boolean {
        val canonicalPath = "$titleRoot/${saveId.category}/${saveId.shortId}"
        val variants = variantsUnder(titleRoot, saveId).filterNot { it == canonicalPath }
        if (variants.isEmpty()) return false

        if (!holdsSaveData(canonicalPath)) {
            return promote(variants, canonicalPath, gameId, gameTitle)
        }

        var moved = false
        for (variant in variants) {
            if (displace(variant, compareReason(variant, canonicalPath), gameId, gameTitle)) moved = true
        }
        return moved
    }

    private fun variantsUnder(titleRoot: String, saveId: SaveIdSplit): List<String> =
        fal.listFiles(titleRoot).orEmpty()
            .filter { it.isDirectory && it.name.equals(saveId.category, ignoreCase = true) }
            .flatMap { categoryDir ->
                fal.listFiles(categoryDir.path).orEmpty()
                    .filter { it.isDirectory && it.name.equals(saveId.shortId, ignoreCase = true) }
                    .map { it.path }
            }
            .filter { holdsSaveData(it) }

    /**
     * Only Argosy's directory exists, so it becomes the emulator's. When several case variants
     * exist and none is the canonical one, the newest is carried forward and the rest are moved
     * aside rather than any of them being dropped.
     */
    private fun promote(
        variants: List<String>,
        canonicalPath: String,
        gameId: Long,
        gameTitle: String
    ): Boolean {
        val winner = variants.maxByOrNull { savePathResolver.findNewestFileTime("$it/$SAVE_DATA_DIR") }
            ?: return false
        val parentPath = File(canonicalPath).parent ?: return false
        if (!isDirectory(parentPath) && !fal.mkdirs(parentPath)) {
            Logger.warn(
                TAG,
                "[SaveSync] MIGRATE | Could not create the emulator's category directory, leaving the " +
                    "save where it is | gameId=$gameId, path=$parentPath"
            )
            return false
        }

        val carried = rename(winner, canonicalPath)
        if (!carried) {
            Logger.warn(
                TAG,
                "[SaveSync] MIGRATE | Could not carry the 3DS save into the emulator's directory | " +
                    "gameId=$gameId, game=$gameTitle, from=$winner, to=$canonicalPath"
            )
            return false
        }

        Logger.info(
            TAG,
            "[SaveSync] MIGRATE | Carried the 3DS save into the directory the emulator reads | " +
                "gameId=$gameId, game=$gameTitle, from=$winner, to=$canonicalPath"
        )
        SaveDebugLogger.logCustom(
            event = REPAIR_EVENT,
            gameId = gameId,
            gameName = gameTitle,
            channel = null,
            details = "carried=${File(winner).name} to=${File(canonicalPath).name}"
        )

        variants.filterNot { it == winner }
            .forEach { displace(it, REASON_SUPERSEDED, gameId, gameTitle) }
        return true
    }

    private fun displace(path: String, reason: String, gameId: Long, gameTitle: String): Boolean {
        val target = "$path$DISPLACED_SUFFIX-${System.currentTimeMillis()}"
        if (!rename(path, target)) {
            Logger.warn(
                TAG,
                "[SaveSync] MIGRATE | Could not move a 3DS save copy aside, both copies left in place | " +
                    "gameId=$gameId, game=$gameTitle, path=$path, reason=$reason"
            )
            return false
        }
        Logger.info(
            TAG,
            "[SaveSync] MIGRATE | Moved a 3DS save copy aside, nothing deleted | " +
                "gameId=$gameId, game=$gameTitle, from=$path, to=$target, reason=$reason"
        )
        SaveDebugLogger.logCustom(
            event = REPAIR_EVENT,
            gameId = gameId,
            gameName = gameTitle,
            channel = null,
            details = "displaced=${File(path).name} to=${File(target).name} reason=$reason"
        )
        return true
    }

    /**
     * Compares the save units rather than the title directories, because a folder hash is taken
     * over the folder's own name and the two names differ by exactly the case this repairs.
     */
    private fun compareReason(variantPath: String, canonicalPath: String): String {
        val variantHash = saveDataHash(variantPath)
        val canonicalHash = saveDataHash(canonicalPath)
        return when {
            variantHash == null || canonicalHash == null -> REASON_UNCOMPARED
            variantHash == canonicalHash -> REASON_IDENTICAL
            else -> REASON_DIVERGED
        }
    }

    private fun saveDataHash(titlePath: String): String? = runCatching {
        saveArchiver.calculateFolderAsZipHash(fal.getTransformedFile("$titlePath/$SAVE_DATA_DIR"))
    }.getOrNull()

    private fun rename(from: String, to: String): Boolean = runCatching {
        fal.getTransformedFile(from).renameTo(fal.getTransformedFile(to))
    }.getOrDefault(false)

    private fun holdsSaveData(titlePath: String): Boolean = isDirectory("$titlePath/$SAVE_DATA_DIR")

    private fun isDirectory(path: String): Boolean = fal.exists(path) && fal.isDirectory(path)

    private data class SaveIdSplit(val category: String, val shortId: String)

    /**
     * Accepts both shapes a 3DS id reaches here in: sigil's nested `00040000/0011c500` save id and
     * the flat 16-hex title id kept on older game rows. Anything else is not a 3DS save location
     * and is left alone.
     */
    private fun splitSaveId(raw: String?): SaveIdSplit? {
        val hex = raw?.replace("/", "")?.trim().orEmpty()
        if (hex.length != SAVE_ID_HEX_LENGTH) return null
        if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return SaveIdSplit(hex.take(8).lowercase(), hex.takeLast(8).lowercase())
    }

    companion object {
        private const val PLATFORM_SLUG = "3ds"
        private const val TITLE_DIR = "title"
        private const val SAVE_DATA_DIR = "data"
        private const val SAVE_ID_HEX_LENGTH = 16
        private const val DISPLACED_SUFFIX = ".argosy-displaced"
        private const val REPAIR_EVENT = "N3DS_CASE_REPAIR"
        private const val REASON_IDENTICAL = "duplicate of the emulator's copy"
        private const val REASON_DIVERGED = "diverged from the emulator's copy, which the game actually read"
        private const val REASON_UNCOMPARED = "could not be compared with the emulator's copy"
        private const val REASON_SUPERSEDED = "an older case variant of the same save"
    }
}
