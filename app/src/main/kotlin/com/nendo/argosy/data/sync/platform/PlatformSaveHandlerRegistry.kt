package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.sync.ArchiveRoot
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for picking a [PlatformSaveHandler] from a (platformSlug, emulatorId,
 * config) triple. Replaces the four ad-hoc dispatch sites that used to live in
 * [com.nendo.argosy.data.repository.SaveSyncApiClient.getHandler],
 * [com.nendo.argosy.data.sync.SavePathResolver]'s `when (platformSlug)` blocks, and the duplicate
 * platform switches in `SavePathValidator`.
 *
 * Adding a new folder-based platform = register one entry in [folderHandlers] (plus a slug
 * mapping in [PlatformDefinitions] aliases if needed). Adding a new file-based platform = inject
 * the handler and add a branch to [getHandler].
 */
@Singleton
class PlatformSaveHandlerRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver,
    private val switchSaveHandler: SwitchSaveHandler,
    private val gciSaveHandler: GciSaveHandler,
    private val retroArchSaveHandler: RetroArchSaveHandler,
    private val dreamcastSaveHandler: DreamcastSaveHandler,
    private val defaultSaveHandler: DefaultSaveHandler
) {
    /**
     * Folder-bundle handlers keyed by canonical platform slug. Adding a new platform: drop a
     * line here. Special-cases (PSP prefix match, PS2 BA-prefix normalization, 3DS id0/id1
     * traversal) live as small subclasses of [FolderSaveHandler] declared inline.
     */
    private val folderHandlers: Map<String, FolderSaveHandler> = listOf(
        FolderSaveHandler(context, fal, saveArchiver, platformSlug = "vita"),
        PspFolderHandler(context, fal, saveArchiver),
        FolderSaveHandler(context, fal, saveArchiver, platformSlug = "wii"),
        FolderSaveHandler(context, fal, saveArchiver, platformSlug = "wiiu"),
        N3dsFolderHandler(context, fal, saveArchiver),
        Ps2FolderHandler(context, fal, saveArchiver),
        Ps3FolderHandler(context, fal, saveArchiver),
        Xbox360FolderHandler(context, fal, saveArchiver),
        XboxSaveHandler(context, fal, saveArchiver)
    ).associateBy { it.platformSlug }

    private fun canonicalSlug(platformSlug: String): String =
        PlatformDefinitions.getCanonicalSlug(platformSlug)

    /**
     * Resolve the handler for a save dispatch. Order matches the legacy `when` in
     * [com.nendo.argosy.data.repository.SaveSyncApiClient.getHandler]: RetroArch first by
     * emulator id, then GCI by config, then platform-keyed folder handlers, finally the
     * fallback file-based default.
     */
    fun getHandler(
        config: SavePathConfig?,
        platformSlug: String,
        emulatorId: String
    ): PlatformSaveHandler {
        // Config-driven format wins before the RetroArch shortcut so libretro cores that bypass SAVE_RAM (ppsspp/citra/dolphin) route to folder/GCI handlers instead of .srm.
        if (config?.usesGciFormat == true) return gciSaveHandler
        val canonical = canonicalSlug(platformSlug)
        if (config?.usesFolderBasedSaves == true) {
            if (canonical == "switch") return switchSaveHandler
            folderHandlers[canonical]?.let { return it }
        }
        if (emulatorId in RETROARCH_EMULATOR_IDS) return retroArchSaveHandler
        if (canonical == "switch") return switchSaveHandler
        if (canonical == "dreamcast") return dreamcastSaveHandler
        return folderHandlers[canonical] ?: defaultSaveHandler
    }

    /**
     * Folder handler for [platformSlug], or null when the platform isn't a per-title folder
     * layout. Used by [com.nendo.argosy.data.sync.SavePathResolver] for `findSaveFolderBySaveId`,
     * `resolveBasePath`, and `constructSavePath` dispatches.
     */
    fun getFolderHandler(platformSlug: String): FolderSaveHandler? =
        folderHandlers[canonicalSlug(platformSlug)]

    /**
     * The folder handler an emulator's saves go through, or null when it isn't a folder-based
     * platform. Multi-platform emulators (RetroArch, builtin) resolve to nothing on purpose:
     * they have no single folder layout to normalize a path against.
     */
    fun getFolderHandlerForEmulator(emulatorId: String): FolderSaveHandler? {
        val platforms = EmulatorRegistry.getById(emulatorId)?.supportedPlatforms
            ?: return null
        val handlers = platforms.mapNotNull { folderHandlers[canonicalSlug(it)] }.distinct()
        return handlers.singleOrNull()
    }

    /**
     * Resolve a path the user picked to the root their platform actually scans from, so
     * choosing a parent or a child of it lands in the same place. Returns the path unchanged
     * for platforms that do not define a layout.
     */
    fun normalizeUserChosenSavePath(emulatorId: String, path: String): String {
        val handler = getFolderHandlerForEmulator(emulatorId) ?: return path
        val config = SavePathRegistry.getConfig(emulatorId) ?: return path
        val resolved = handler.resolveBasePath(config, path) ?: return path
        if (resolved != path) {
            Logger.debug(TAG, "normalizeUserChosenSavePath: chosen=$path, resolved=$resolved, emulator=$emulatorId")
        }
        return resolved
    }

    /**
     * Whether a path stored on a sync row still has its platform's shape, so a caller can tell a
     * stale row from a live one before trusting it. Layouts that nest a save under a per-install
     * identifier answer this themselves; every other platform answers true, because for them
     * existence on disk is the whole question.
     */
    fun isValidCachedSavePath(platformSlug: String, path: String): Boolean {
        val canonical = canonicalSlug(platformSlug)
        if (canonical == "switch") return switchSaveHandler.isValidCachedSavePath(path)
        return folderHandlers[canonical]?.isValidCachedSavePath(path) ?: true
    }

    fun pathIsPresent(path: String): Boolean = fal.exists(path) && fal.isDirectory(path)

    fun listPs2FolderMemcards(basePath: String): List<MemcardInfo> {
        val handler = folderHandlers["ps2"] as? Ps2FolderHandler ?: return emptyList()
        return handler.listFolderMemcards(basePath)
    }

    fun listPs2FolderMemcardsForEmulator(
        emulatorId: String,
        emulatorPackage: String?,
        basePathOverride: String? = null
    ): List<MemcardInfo> {
        val config = emulatorPackage?.let { SavePathRegistry.getConfigByPackage(it) }
            ?: SavePathRegistry.getConfig(emulatorId)
            ?: return emptyList()
        val basePath = basePathOverride?.takeIf { it.isNotBlank() }
            ?: SavePathRegistry.resolvePathWithPackage(config, emulatorPackage, context.filesDir.absolutePath).firstOrNull()
            ?: return emptyList()
        return listPs2FolderMemcards(basePath)
    }

    companion object {
        private const val TAG = "PlatformSaveHandlerRegistry"
        private val RETROARCH_EMULATOR_IDS = setOf("retroarch", "retroarch_64", "retroarch_32")
    }
}

/**
 * Whether [needle] appears verbatim in the bytes. PARAM.SFO keeps its key names in a plain
 * ASCII key table, so presence of a key is a substring test rather than a parse.
 */
private fun ByteArray.containsAscii(needle: String): Boolean {
    val target = needle.toByteArray(Charsets.US_ASCII)
    if (target.isEmpty() || target.size > size) return false
    outer@ for (start in 0..size - target.size) {
        for (i in target.indices) {
            if (this[start + i] != target[i]) continue@outer
        }
        return true
    }
    return false
}

/**
 * PSP saves are folders under `PSP/SAVEDATA/` named `<DISC_ID><SAVE_NAME>` where the 9-char
 * disc id (e.g. `ULUS10064`) is shared across all of a game's profile/system folders. A single
 * game commonly produces several siblings (`ULUS10064DATA00`, `ULUS10064SETTINGS`, ...), so the
 * "save unit" spans every prefix-matched folder under the parent.
 *
 * Bundling, discovery and restore are [PrefixBundleFolderHandler]'s; the one thing PSP adds is
 * what to leave out.
 *
 * Game-data installs land in the same place under the same prefix (PPSSPP's
 * `PSPGamedataInstallDialog` writes to `PSP/SAVEDATA/<gameName><dataName>/`), but they are
 * copies of disc content, not progress. They are told apart by their PARAM.SFO: every savedata
 * write emits `SAVEDATA_PARAMS` and `SAVEDATA_FILE_LIST`, and the install path emits neither.
 * Excluding them keeps hundreds of megabytes of disc data out of an upload and, just as
 * importantly, off the deletion list a restore runs before it extracts.
 */
private class PspFolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : PrefixBundleFolderHandler(context, fal, saveArchiver, platformSlug = "psp", tag = TAG) {

    companion object {
        private const val TAG = "PspFolderHandler"
        private const val PARAM_SFO = "PARAM.SFO"
        private const val MAX_SFO_BYTES = 64 * 1024L
        private const val SAVEDATA_DIR = "SAVEDATA"
        private const val PSP_DIR = "PSP"
        private val SAVEDATA_KEYS = listOf("SAVEDATA_PARAMS", "SAVEDATA_FILE_LIST")
    }

    /**
     * PPSSPP's memory stick, its `PSP` folder and the `SAVEDATA` folder inside it are all places a
     * user pointing at "the PSP saves" lands, so a chosen path is walked down to `SAVEDATA`. A
     * path already at or below it is trimmed back to it.
     */
    override fun normalizeBasePath(path: String): String {
        val trimmed = path.trimEnd('/')
        val segments = trimmed.split('/')
        val savedataIndex = segments.indexOfLast { it.equals(SAVEDATA_DIR, ignoreCase = true) }
        if (savedataIndex >= 0) {
            return segments.take(savedataIndex + 1).joinToString("/")
        }

        val candidates = listOf(
            "$trimmed/$SAVEDATA_DIR",
            "$trimmed/$PSP_DIR/$SAVEDATA_DIR"
        )
        val resolved = candidates.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
        if (resolved != null) {
            Logger.debug(TAG, "normalizeBasePath: resolved SAVEDATA below the chosen path | chosen=$path, base=$resolved")
            return resolved
        }
        return trimmed
    }

    override fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> =
        super.findAllSaveFoldersBySaveId(basePath, saveId).filterNot { path ->
            isGameDataInstall(path).also { skipped ->
                if (skipped) {
                    Logger.debug(TAG, "Skipping installed game data | path=$path, saveId=$saveId")
                }
            }
        }

    /**
     * A prefix-matched folder holding installed disc data rather than a save. Only a folder
     * whose PARAM.SFO is readable and carries neither savedata key is refused: a folder with no
     * PARAM.SFO, or one we cannot read, stays in the save unit rather than being dropped on a
     * guess.
     */
    private fun isGameDataInstall(folderPath: String): Boolean {
        val sfo = fal.getTransformedFile("$folderPath/$PARAM_SFO")
        if (!sfo.isFile || sfo.length() > MAX_SFO_BYTES) return false
        val bytes = runCatching { sfo.readBytes() }.getOrElse { return false }
        return SAVEDATA_KEYS.none { bytes.containsAscii(it) }
    }
}

/**
 * PS3 saves are directories under `dev_hdd0/home/<user>/savedata/` named `<titleId><suffix>`,
 * where the 9-character title id from PARAM.SFO is shared across a game's artifacts
 * (`BCUS99086GAMEDATA`, `BCUS99086-AUTOSAVE`). Same shape as PSP, so the prefix bundle carries
 * all of it; nothing here needs to tell one artifact from another.
 *
 * aPS3e hardcodes the user to `00000001`, which is why the config names it rather than
 * discovering it. Desktop RPCS3 holds several users and would have to.
 */
private class Ps3FolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : PrefixBundleFolderHandler(context, fal, saveArchiver, platformSlug = "ps3", tag = TAG) {

    companion object {
        private const val TAG = "Ps3FolderHandler"
        private const val SAVEDATA_DIR = "savedata"
        private const val APS3E_USER = "00000001"
    }

    /**
     * The user directory and the `savedata` directory under it are both places a user pointing at
     * "the PS3 saves folder" lands, so a chosen path is walked down to the one saves are actually
     * filed in. A path already at or below `savedata` is trimmed back to it.
     */
    override fun normalizeBasePath(path: String): String {
        val trimmed = path.trimEnd('/')
        val segments = trimmed.split('/')
        val savedataIndex = segments.indexOfLast { it.equals(SAVEDATA_DIR, ignoreCase = true) }
        if (savedataIndex >= 0) {
            return segments.take(savedataIndex + 1).joinToString("/")
        }

        val candidates = listOf(
            "$trimmed/$SAVEDATA_DIR",
            "$trimmed/$APS3E_USER/$SAVEDATA_DIR"
        )
        val resolved = candidates.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
        if (resolved != null) {
            Logger.debug(TAG, "normalizeBasePath: resolved savedata below the chosen path | chosen=$path, base=$resolved")
            return resolved
        }
        return trimmed
    }
}

/**
 * Xbox 360 layout: `{baseDir}/{XUID}/{saveId}/00000001/{package}/`. Content is keyed by profile
 * before title, so the save id is the SECOND segment and the 16-hex XUID above it belongs to
 * whoever is signed in. That level is enumerated, never constructed - it names a profile the
 * emulator created, and inventing one produces a directory XenDroid never reads.
 *
 * `00000001` is the saved-game content type. `00000002` is DLC and `000B0000` is title updates,
 * all three sitting under the same title id, so a handler that matched on the title id alone
 * would sync add-on content as though it were progress. The save unit therefore stops at the
 * content-type directory rather than at the title.
 *
 * Non-profile content lives under the machine XUID `0000000000000000` and holds no saved games,
 * so a tree containing only that XUID is a miss rather than a hit. The layout is Xenia's
 * `ResolvePackageRoot()`, which is why desktop Xenia matches below its own content root.
 */
private class Xbox360FolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "xbox360", tag = TAG) {

    companion object {
        private const val TAG = "Xbox360FolderHandler"
        private const val SAVED_GAME_CONTENT_TYPE = "00000001"
        private const val MACHINE_XUID = "0000000000000000"
        private const val CONTENT_DIR = "content"
        private const val XUID_LENGTH = 16
        private const val SAVE_ID_LENGTH = 8
    }

    /**
     * Saves hang off `content`, one level inside the emulator's own directory, so a chosen path at
     * either level resolves to the same root. A path already inside the tree is trimmed back to
     * `content` rather than treated as a base holding profiles.
     */
    override fun normalizeBasePath(path: String): String {
        val trimmed = path.trimEnd('/')
        val segments = trimmed.split('/')
        val contentIndex = segments.indexOfLast { it.equals(CONTENT_DIR, ignoreCase = true) }
        if (contentIndex >= 0) {
            return segments.take(contentIndex + 1).joinToString("/")
        }

        val below = "$trimmed/$CONTENT_DIR"
        if (fal.exists(below) && fal.isDirectory(below)) {
            Logger.debug(TAG, "normalizeBasePath: resolved content below the chosen path | chosen=$path, base=$below")
            return below
        }
        return trimmed
    }

    /**
     * The save unit is the content-type directory, whose name is the same for every title on the
     * console. It confirms nothing about which save an archive holds, so the resolved destination
     * is what places it.
     */
    override val unidentifiedArchiveRoots: Set<String> = setOf(SAVED_GAME_CONTENT_TYPE)

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "Base path does not exist | path=$basePath")
            return null
        }

        var bestMatchPath: String? = null
        var bestModTime = -1L

        profileXuidDirs(basePath).forEach { xuidDir ->
            val titleDir = fal.listFiles(xuidDir.path)?.firstOrNull {
                it.isDirectory && it.name.equals(saveId, ignoreCase = true)
            } ?: return@forEach

            val savedGames = "${titleDir.path}/$SAVED_GAME_CONTENT_TYPE"
            if (!fal.exists(savedGames) || !fal.isDirectory(savedGames)) {
                Logger.debug(TAG, "Title present without saved games | path=${titleDir.path}")
                return@forEach
            }

            val modTime = newestFileTime(savedGames)
            if (modTime > bestModTime) {
                bestModTime = modTime
                bestMatchPath = savedGames
            }
        }

        if (bestMatchPath == null) {
            Logger.debug(TAG, "No save found | basePath=$basePath, saveId=$saveId")
        }
        return bestMatchPath
    }

    override fun constructSavePath(baseDir: String, saveId: String): String? {
        val xuidDir = profileXuidDirs(baseDir).maxByOrNull { newestFileTime(it.path) }
        if (xuidDir == null) {
            Logger.debug(TAG, "No signed-in profile to restore into | baseDir=$baseDir, saveId=$saveId")
            return null
        }
        return "${xuidDir.path}/${saveId.uppercase()}/$SAVED_GAME_CONTENT_TYPE"
    }

    override fun isValidCachedSavePath(path: String): Boolean {
        val parts = path.trimEnd('/').split("/")
        if (parts.size < 3) return false

        val contentType = parts[parts.size - 1]
        val cachedSaveId = parts[parts.size - 2]
        val xuid = parts[parts.size - 3]

        val isValid = contentType == SAVED_GAME_CONTENT_TYPE &&
            isHex(cachedSaveId, SAVE_ID_LENGTH) &&
            isProfileXuid(xuid)

        if (!isValid) {
            Logger.debug(TAG, "isValidCachedSavePath: invalid | path=$path, xuid=$xuid, saveId=$cachedSaveId, type=$contentType")
        }
        return isValid
    }

    private fun profileXuidDirs(basePath: String): List<FileInfo> =
        fal.listFiles(basePath).orEmpty().filter { it.isDirectory && isProfileXuid(it.name) }

    private fun isProfileXuid(name: String): Boolean =
        isHex(name, XUID_LENGTH) && name != MACHINE_XUID

    private fun isHex(value: String, length: Int): Boolean =
        value.length == length && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}

/**
 * 3DS save layout, two trees under the same id1 root:
 * `{baseDir}/{id0}/{id1}/title/{category}/{lowId}/data` holds the title's save and
 * `{baseDir}/{id0}/{id1}/extdata/00000000/{extdataId}` holds its extra data. Some titles keep
 * their progress only in extdata (Fantasy Life writes nothing under `data`), so the save unit is
 * both directories and either one alone counts as a save. The id0/id1 folders are randomized per
 * console install, so the tree is walked to find them. Includes a 3DS-specific basePathOverride
 * normalization (mounting `sdmc/Nintendo 3DS` if missing).
 *
 * Category and low id are the two halves of the 16-hex save id. sigil reports the same split
 * as `save_path`; splitting it here stays equivalent for every 16-hex id. See
 * docs/save-id-to-path.md.
 */
private class N3dsFolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "3ds") {

    private val appContext = context

    companion object {
        private const val TAG = "N3dsFolderHandler"
        private const val BASE_CATEGORY = "00040000"
        private const val SDMC_DIR = "sdmc"
        private const val SD_ROOT = "Nintendo 3DS"
        private const val TITLE_DIR = "title"
        private const val TITLE_DATA_ROOT = "data"
        private const val EXTDATA_DIR = "extdata"
        private const val EXTDATA_HIGH = "00000000"
        private const val ID_HEX_LENGTH = 8
        private const val FULL_ID_HEX_LENGTH = 16
        private const val EXTDATA_ID_SHIFT = 8
        private const val HEX_RADIX = 16

        /**
         * The extdata directory a retail title writes: its low title id shifted right by 8 bits,
         * rendered as 8 lowercase hex under `extdata/00000000/`. `00113200` becomes `00001132`.
         * Null when the low id is not hex, in which case no extdata location exists to derive.
         */
        fun extdataIdFor(lowId: String): String? = lowId.toLongOrNull(HEX_RADIX)?.let {
            java.lang.Long.toHexString(it shr EXTDATA_ID_SHIFT).padStart(ID_HEX_LENGTH, '0')
        }
    }

    /**
     * Category, low id and the extdata id the low id derives, all lowercase because that is the
     * case the emulator writes and internal storage is case-sensitive.
     */
    private data class TitleKey(val category: String, val lowId: String, val extdataId: String?)

    /**
     * The two directories one title's save can occupy under an id1 root. [titleData] is where
     * a `data` root lands whether or not it exists yet; [extdata] is null only when no extdata
     * id could be derived.
     */
    private data class SaveUnit(val titleData: String, val extdata: String?)

    /**
     * A 3DS archive is rooted at `data` for the title tree and `extdata` for the extdata tree,
     * so the title never appears inside it. Every archive, current ones included, lands here;
     * the destination is what identifies the save.
     */
    override val unidentifiedArchiveRoots: Set<String> = setOf(TITLE_DATA_ROOT, EXTDATA_DIR)

    /**
     * Saves live under `<userDir>/sdmc/Nintendo 3DS/<id0>/<id1>/title/...`, and which part of
     * that a user considers "the save path" varies by emulator. Any level is accepted: a path
     * containing the SD root is trimmed back to it, and one above it is walked down.
     */
    override fun normalizeBasePath(path: String): String {
        val trimmed = path.trimEnd('/')
        val segments = trimmed.split('/')
        val rootIndex = segments.indexOfLast { it.equals(SD_ROOT, ignoreCase = true) }
        if (rootIndex >= 0) {
            return segments.take(rootIndex + 1).joinToString("/")
        }

        val candidates = listOf(
            "$trimmed/$SD_ROOT",
            "$trimmed/$SDMC_DIR/$SD_ROOT"
        )
        val resolved = candidates.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
        if (resolved != null) {
            Logger.debug(TAG, "normalizeBasePath: resolved SD root below the chosen path | chosen=$path, base=$resolved")
            return resolved
        }
        return trimmed
    }

    /**
     * The resolved path stays the title `data` directory whenever it exists, so sync rows written
     * before extdata joined the unit keep their paths; a title with extdata only resolves to the
     * extdata directory. Across id1 trees the unit written most recently wins.
     */
    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        if (!isDir(basePath)) {
            Logger.debug(TAG, "Base path does not exist | path=$basePath")
            return null
        }

        val key = titleKeyFor(saveId)
        Logger.debug(TAG, "Searching for save | baseDir=$basePath, category=${key.category}, lowId=${key.lowId}, extdataId=${key.extdataId}")

        val unit = discoverUnit(basePath, key) ?: return null
        val resolved = resolvedPathOf(unit)
        if (resolved != null) {
            Logger.debug(TAG, "Save found | path=$resolved, titleData=${unit.titleData}, extdata=${unit.extdata}")
        }
        return resolved
    }

    override fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> {
        val key = titleKeyFor(saveId)
        val unit = unitAt(basePath, key) ?: discoverUnit(basePath, key) ?: return emptyList()
        return existingComponents(unit).map { it.folder.path }
    }

    /**
     * Both id segments are written lowercase because that is the case the emulator writes, and
     * internal storage is case-sensitive: an uppercase segment creates a second directory the
     * emulator never reads. Normalizing here rather than trusting the caller covers the save ids
     * already cached on game rows in the case they were first extracted with.
     */
    override fun constructSavePath(baseDir: String, saveId: String): String? {
        val key = titleKeyFor(saveId)

        val id0Folder = fal.listFiles(baseDir)?.firstOrNull { it.isDirectory }
        if (id0Folder == null) {
            Logger.debug(TAG, "No id0 folder found | baseDir=$baseDir")
            return null
        }

        val id1Folder = fal.listFiles(id0Folder.path)?.firstOrNull { it.isDirectory }
        if (id1Folder == null) {
            Logger.debug(TAG, "No id1 folder found | id0=${id0Folder.path}")
            return null
        }

        val savePath = titleDataPath(id1Folder.path, key.category, key.lowId)
        Logger.debug(TAG, "Constructed save path | path=$savePath")
        return savePath
    }

    override fun namedArchiveRoots(savePath: String, saveId: String): List<ArchiveRoot>? =
        unitAt(savePath, titleKeyFor(saveId))?.let(::existingComponents)

    override suspend fun sourcePathsFor(
        localPath: String,
        context: SaveContext
    ): List<String> = withContext(Dispatchers.IO) {
        val saveId = context.saveId ?: return@withContext listOf(localPath)
        val unit = unitAt(localPath, titleKeyFor(saveId)) ?: return@withContext listOf(localPath)
        existingComponents(unit).map { it.folder.path }
    }

    /**
     * Bundles every component of the unit the resolved path belongs to, `data` and `extdata`,
     * so a title whose progress lives in extdata is uploaded whichever directory was resolved.
     */
    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val saveId = context.saveId
            ?: return@withContext super.prepareForUpload(localPath, context)
        val unit = unitAt(localPath, titleKeyFor(saveId))
            ?: return@withContext super.prepareForUpload(localPath, context)

        val roots = existingComponents(unit)
        if (roots.isEmpty()) {
            Logger.debug(TAG, "prepareForUpload: neither save tree exists | path=$localPath, saveId=$saveId")
            return@withContext null
        }
        Logger.debug(TAG, "prepareForUpload: bundling ${roots.size} tree(s) | saveId=$saveId, roots=${roots.map { it.name }}")

        val outputFile = File(appContext.cacheDir, "${saveId.asArchiveName()}.zip")
        if (!saveArchiver.zipNamedFolders(roots, outputFile)) {
            Logger.error(TAG, "prepareForUpload: failed to zip save trees | saveId=$saveId")
            return@withContext null
        }
        PreparedSave(outputFile, isTemporary = true, roots.map { it.folder.path })
    }

    /**
     * A pre-restore clear may only remove a component the archive is about to replace. With the
     * archive unread nothing is cleared here, because [unpackArchive] clears each component it
     * places; with it read, only components whose root the archive carries go.
     */
    override fun pathsClearedBeforeRestore(paths: List<String>, archiveRoots: Set<String>?): List<String> {
        if (archiveRoots == null) return emptyList()
        return paths.filter { componentRootOf(it) in archiveRoots }
    }

    /**
     * A `data` root lands in the title tree and an `extdata` root in the extdata tree whichever
     * component the target names, so an archive holding only extdata restores to the right place
     * even when the resolved path is the title `data` directory. Each component the archive
     * carries is replaced, not overlaid, and a component it does not carry is left untouched, so
     * a legacy archive rooted at `data` alone lands exactly where it always has and never costs
     * the extdata beside it. A target that is not a component of any unit is unpacked as the
     * plain folder it was resolved to.
     */
    override fun unpackArchive(tempFile: File, targetFolder: File, saveId: String?): Boolean {
        val unit = saveId?.let { unitAt(targetFolder.path, titleKeyFor(it)) }
            ?: return super.unpackArchive(tempFile, targetFolder, saveId)

        val destinations = buildMap {
            put(TITLE_DATA_ROOT, unit.titleData)
            unit.extdata?.let { put(EXTDATA_DIR, it) }
        }
        val archiveRoots = saveArchiver.peekRootEntryNames(tempFile)
        Logger.debug(TAG, "unpackArchive: placing roots | roots=$archiveRoots, titleData=${unit.titleData}, extdata=${unit.extdata}")

        destinations.filterKeys { it in archiveRoots }.values.filter(::isDir).forEach { component ->
            Logger.debug(TAG, "unpackArchive: replacing component the archive carries | path=$component")
            fal.deleteRecursively(component)
        }
        return saveArchiver.unzipRootsTo(tempFile, destinations.mapValues { fal.getTransformedFile(it.value) })
    }

    private fun componentRootOf(path: String): String? {
        val segments = path.trimEnd('/').split('/')
        val n = segments.size
        return when {
            n >= 4 && segments[n - 1].equals(TITLE_DATA_ROOT, ignoreCase = true) &&
                segments[n - 4].equals(TITLE_DIR, ignoreCase = true) -> TITLE_DATA_ROOT
            n >= 3 && segments[n - 3].equals(EXTDATA_DIR, ignoreCase = true) -> EXTDATA_DIR
            else -> null
        }
    }

    private fun titleKeyFor(saveId: String): TitleKey {
        val flat = saveId.replace("/", "").trim().lowercase()
        val category = if (flat.length >= FULL_ID_HEX_LENGTH) flat.take(ID_HEX_LENGTH) else BASE_CATEGORY
        val lowId = flat.takeLast(ID_HEX_LENGTH)
        return TitleKey(category, lowId, extdataIdFor(lowId))
    }

    private fun titleDataPath(id1Root: String, category: String, lowId: String): String =
        "$id1Root/$TITLE_DIR/$category/$lowId/$TITLE_DATA_ROOT"

    private fun extdataPath(id1Root: String, key: TitleKey): String? =
        key.extdataId?.let { "$id1Root/$EXTDATA_DIR/$EXTDATA_HIGH/$it" }

    private fun isDir(path: String): Boolean = fal.exists(path) && fal.isDirectory(path)

    private fun resolvedPathOf(unit: SaveUnit): String? =
        unit.titleData.takeIf(::isDir) ?: unit.extdata?.takeIf(::isDir)

    private fun existingComponents(unit: SaveUnit): List<ArchiveRoot> = listOfNotNull(
        unit.titleData.takeIf(::isDir)?.let { ArchiveRoot(TITLE_DATA_ROOT, fal.getTransformedFile(it)) },
        unit.extdata?.takeIf(::isDir)?.let { ArchiveRoot(EXTDATA_DIR, fal.getTransformedFile(it)) }
    )

    private fun unitNewestTime(unit: SaveUnit): Long =
        existingComponents(unit).maxOfOrNull { newestFileTime(it.folder.path) } ?: 0L

    private fun id1Roots(basePath: String): List<String> =
        fal.listFiles(basePath).orEmpty()
            .filter { it.isDirectory }
            .flatMap { id0 -> fal.listFiles(id0.path).orEmpty().filter { it.isDirectory } }
            .map { it.path }

    private fun discoverUnit(basePath: String, key: TitleKey): SaveUnit? =
        id1Roots(basePath)
            .map { unitUnder(it, key) }
            .filter { resolvedPathOf(it) != null }
            .maxByOrNull { unitNewestTime(it) }

    private fun unitUnder(id1Root: String, key: TitleKey): SaveUnit {
        val titleData = existingTitleData(id1Root, key) ?: titleDataPath(id1Root, key.category, key.lowId)
        return SaveUnit(titleData, extdataPath(id1Root, key))
    }

    /**
     * The `data` directory for this low id under whichever category holds one. The base category
     * wins over an update or DLC tree carrying the same low id, then the id's own category, then
     * whichever was written most recently.
     */
    private fun existingTitleData(id1Root: String, key: TitleKey): String? {
        val candidates = fal.listFiles("$id1Root/$TITLE_DIR").orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { categoryDir ->
                val titleDir = fal.listFiles(categoryDir.path).orEmpty()
                    .firstOrNull { it.isDirectory && it.name.equals(key.lowId, ignoreCase = true) }
                    ?: return@mapNotNull null
                "${titleDir.path}/$TITLE_DATA_ROOT".takeIf(::isDir)?.let { categoryDir.name to it }
            }
        return candidates
            .sortedWith(
                compareBy<Pair<String, String>> { categoryRank(it.first, key) }
                    .thenByDescending { newestFileTime(it.second) }
            )
            .firstOrNull()?.second
    }

    private fun categoryRank(category: String, key: TitleKey): Int = when {
        category.equals(BASE_CATEGORY, ignoreCase = true) -> 0
        category.equals(key.category, ignoreCase = true) -> 1
        else -> 2
    }

    /**
     * Reads the unit back out of a resolved path, which is one of its components: the title
     * `data` directory or the extdata directory. The id1 root above it locates the other. Null
     * for a path that is neither, which callers treat as a plain folder.
     */
    private fun unitAt(path: String, key: TitleKey): SaveUnit? {
        val trimmed = path.trimEnd('/')
        val segments = trimmed.split('/')
        val n = segments.size

        val atTitleData = n >= 5 &&
            segments[n - 1].equals(TITLE_DATA_ROOT, ignoreCase = true) &&
            segments[n - 2].equals(key.lowId, ignoreCase = true) &&
            segments[n - 4].equals(TITLE_DIR, ignoreCase = true)
        if (atTitleData) {
            val id1Root = segments.dropLast(4).joinToString("/")
            return SaveUnit(trimmed, extdataPath(id1Root, key))
        }

        val atExtdata = key.extdataId != null && n >= 4 &&
            segments[n - 1].equals(key.extdataId, ignoreCase = true) &&
            segments[n - 2] == EXTDATA_HIGH &&
            segments[n - 3].equals(EXTDATA_DIR, ignoreCase = true)
        if (atExtdata) {
            val id1Root = segments.dropLast(3).joinToString("/")
            val titleData = existingTitleData(id1Root, key) ?: titleDataPath(id1Root, key.category, key.lowId)
            return SaveUnit(titleData, trimmed)
        }
        return null
    }
}

private class Ps2FolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "ps2") {

    private val appContext = context

    companion object {
        private const val TAG = "Ps2FolderHandler"
        private const val CARD_SUFFIX = ".ps2"
        private const val SUPERBLOCK_FILE = "_pcsx2_superblock"
        private val REGION_PREFIXED = Regex("^B[AEI][A-Z]{4}")
        private val BARE_SERIAL = Regex("^([A-Z]{4})(\\d+.*)$")
    }

    /**
     * The save unit is the card, so an upload is rooted at the card's own name and that name
     * identifies nothing. The game's folder sits one level below it, which is what has to be
     * matched. Archives written before the save unit became the card are rooted at that
     * folder directly, and both shapes are on servers now.
     */
    override fun matchArchive(tempFile: File, saveId: String): ArchiveRootMatch? {
        super.matchArchive(tempFile, saveId)?.let { return it }
        val holdsGameFolder = saveArchiver.peekFolderNames(tempFile).any { folderMatches(it, saveId) }
        return if (holdsGameFolder) ArchiveRootMatch.CONTAINS else null
    }

    /**
     * The target is the card. Stripping the archive's root is only correct when that root is
     * the card itself; doing it to a game-folder-rooted archive would empty the folder's
     * contents loose into the card, which is not a save layout any emulator reads.
     */
    override fun unpackArchive(tempFile: File, targetFolder: File, saveId: String?): Boolean {
        val roots = saveArchiver.peekRootEntryNames(tempFile)
        val rootIsGameFolder = saveId != null && roots.isNotEmpty() &&
            roots.all { folderMatches(it, saveId) }

        if (rootIsGameFolder) {
            Logger.debug(TAG, "unpackArchive: archive is rooted at the game folder, keeping it | roots=$roots")
            repairLooseCardEntries(targetFolder, roots)
            return saveArchiver.unzipToFolder(tempFile, targetFolder)
        }

        if (saveId == null) {
            Logger.error(TAG, "unpackArchive: card-rooted archive with no save id; refusing to unpack | roots=$roots")
            return false
        }

        Logger.debug(TAG, "unpackArchive: archive is rooted at the card, extracting only this game | roots=$roots, saveId=$saveId")
        return saveArchiver.unzipSelectedRootChildren(tempFile, targetFolder) { folderMatches(it, saveId) }
    }

    /**
     * A card that a previous build restored by stripping the game folder has that folder's
     * files sitting loose beside the real entries. They are put back under the folder they
     * came from before the restore overwrites them, so a card repairs itself by being synced
     * rather than by hand. Anything that cannot be attributed to exactly one folder is left
     * where it is.
     */
    private fun repairLooseCardEntries(card: File, gameFolders: Set<String>) {
        val owner = gameFolders.singleOrNull() ?: return
        if (!isFolderCard(card.path)) return

        val loose = fal.listFiles(card.path).orEmpty()
            .filter { it.isFile && it.name != SUPERBLOCK_FILE }
        if (loose.isEmpty()) return

        val ownerDir = File(card, owner)
        if (!ownerDir.exists() && !ownerDir.mkdirs()) return

        loose.forEach { entry ->
            val moved = runCatching { File(entry.path).renameTo(File(ownerDir, entry.name)) }.getOrDefault(false)
            Logger.warn(
                TAG,
                "repairLooseCardEntries: relocating a stray card entry | file=${entry.name}, into=$owner, moved=$moved"
            )
        }
    }

    override fun ensureContainerPrepared(targetFolder: File) {
        var dir: File? = targetFolder
        while (dir != null && !isFolderCard(dir.path)) {
            dir = dir.parentFile
        }
        val card = dir ?: return
        val superblock = File(card, SUPERBLOCK_FILE)
        if (!superblock.exists()) {
            val created = runCatching { superblock.createNewFile() }.getOrDefault(false)
            if (created) {
                Logger.debug(TAG, "ensureContainerPrepared: wrote $SUPERBLOCK_FILE | card=${card.path}")
            }
        }
    }

    /**
     * The save unit is the card, not a single folder: a game owns every entry whose name
     * starts with sigil's stem, so callers get the card and bundle the matches from it.
     */
    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        Logger.debug(TAG, "findSaveFolderBySaveId: Searching | basePath=$basePath, stem=$saveId")

        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "findSaveFolderBySaveId: Base path does not exist | path=$basePath")
            return null
        }

        if (isFolderCard(basePath)) {
            val entries = findInCard(basePath, saveId)
            Logger.debug(TAG, "findSaveFolderBySaveId: basePath is a folder card | matches=${entries.map { File(it).name }}")
            return if (entries.isEmpty()) null else basePath
        }

        val folderCards = listCardDirsIn(basePath)
        Logger.debug(TAG, "findSaveFolderBySaveId: Found ${folderCards.size} memory card(s) | cards=${folderCards.map { it.name }}")

        val matches = folderCards.mapNotNull { card ->
            findInCard(card.path, saveId).takeIf { it.isNotEmpty() }?.let { card to it }
        }
        when {
            matches.isEmpty() -> {
                Logger.debug(TAG, "findSaveFolderBySaveId: No match | stem=$saveId")
                return null
            }
            matches.size == 1 -> {
                val (card, entries) = matches[0]
                Logger.debug(TAG, "findSaveFolderBySaveId: Match found | card=${card.name}, entries=${entries.map { File(it).name }}")
                return card.path
            }
            else -> {
                Logger.warn(
                    TAG,
                    "findSaveFolderBySaveId: AMBIGUOUS -- ${matches.size} cards contain a folder " +
                        "for $saveId (${matches.map { it.first.name }}). Refusing to pick to avoid " +
                        "overwriting the wrong card. User must select a specific memcard for this " +
                        "PS2 emulator in Settings."
                )
                return null
            }
        }
    }

    override fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) return emptyList()
        if (isFolderCard(basePath)) return findInCard(basePath, saveId)
        return listCardDirsIn(basePath)
            .map { findInCard(it.path, saveId) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    override fun isCanonicalFolderPath(savePath: String, saveId: String): Boolean =
        isFolderCard(savePath) && findInCard(savePath, saveId).isNotEmpty()

    /**
     * Bundles only the folders this game owns, not the whole card. The resolved path is the card
     * directory because that is where a game's entries live, but the card holds every game's
     * saves, and an upload rooted there ships all of them under one game's save.
     */
    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val saveId = context.saveId
        if (saveId == null) {
            Logger.debug(TAG, "prepareForUpload: no save id, refusing to bundle a whole card | path=$localPath")
            return@withContext null
        }
        val matchedPaths = findAllSaveFoldersBySaveId(localPath, saveId)
        if (matchedPaths.isEmpty()) {
            Logger.debug(TAG, "prepareForUpload: no matches | card=$localPath, saveId=$saveId")
            return@withContext null
        }
        val matchedFolders = matchedPaths.map { fal.getTransformedFile(it) }
        Logger.debug(
            TAG,
            "prepareForUpload: bundling ${matchedFolders.size} folder(s) | saveId=$saveId, names=${matchedFolders.map { it.name }}"
        )
        val outputFile = File(appContext.cacheDir, "${saveId.asArchiveName()}.zip")
        if (!saveArchiver.zipFolders(matchedFolders, outputFile)) {
            Logger.error(TAG, "prepareForUpload: failed to zip folders | saveId=$saveId")
            return@withContext null
        }
        PreparedSave(outputFile, isTemporary = true, matchedPaths)
    }

    override suspend fun sourcePathsFor(
        localPath: String,
        context: SaveContext
    ): List<String> = withContext(Dispatchers.IO) {
        val saveId = context.saveId ?: return@withContext emptyList()
        findAllSaveFoldersBySaveId(localPath, saveId)
    }

    /**
     * Restores land in the card itself; the archive carries the emulator's own entry names,
     * so nothing here invents one.
     */
    override fun constructSavePath(baseDir: String, saveId: String): String? {
        if (isFolderCard(baseDir)) {
            return baseDir
        }

        val folderCards = listCardDirsIn(baseDir)
        val cardDir = when (folderCards.size) {
            0 -> "$baseDir/Shared.ps2"
            1 -> folderCards[0].path
            else -> {
                val active = listFolderMemcards(baseDir).firstOrNull()?.path ?: folderCards[0].path
                Logger.warn(
                    TAG,
                    "constructSavePath: $baseDir has ${folderCards.size} folder memcards " +
                        "(${folderCards.map { it.name }}); no preferred memcard set, defaulting to the " +
                        "most-recently-written card=${File(active).name}. Set a preferred memcard to pin one."
                )
                active
            }
        }
        return cardDir
    }

    fun listFolderMemcards(basePath: String): List<MemcardInfo> {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "listFolderMemcards: base path missing or not a directory | path=$basePath, exists=${fal.exists(basePath)}")
            return emptyList()
        }
        if (isFolderCard(basePath)) return listOf(memcardInfoFor(basePath))
        val cards = listCardDirsIn(basePath)
        Logger.debug(TAG, "listFolderMemcards: found ${cards.size} card(s) | path=$basePath, names=${cards.map { it.name }}")
        if (cards.isEmpty()) {
            val children = fal.listFilesUnion(basePath)
            Logger.debug(TAG, "listFolderMemcards: raw children | count=${children.size}, names=${children.take(20).map { it.name + if (it.isDirectory) "/" else "" }}")
        }
        return cards
            .map { memcardInfoFor(it.path) }
            .sortedByDescending { it.lastModified }
    }

    private fun isFolderCard(path: String): Boolean {
        if (!fal.isDirectory(path)) return false
        if (path.trimEnd('/').endsWith(CARD_SUFFIX, ignoreCase = true)) return true
        return fal.exists("${path.trimEnd('/')}/$SUPERBLOCK_FILE")
    }

    /**
     * The cards directly under [parent], or when it holds none, the cards exactly one level
     * below it. ARMSX2 nests them as `<base>/memcards/<CardName>`, so a base pointed at the
     * parent of the cards directory resolves to the same cards every other lookup here uses.
     * The descent stops at one level and only when the direct listing is empty, so a base that
     * already holds cards behaves exactly as before and no deeper tree is ever searched.
     */
    private fun listCardDirsIn(parent: String): List<FileInfo> {
        val children = fal.listFilesUnion(parent).filter { it.isDirectory }
        val direct = children.filter { isCardDir(it) }
        if (direct.isNotEmpty()) return direct

        val nested = children.flatMap { child ->
            fal.listFilesUnion(child.path).filter { it.isDirectory && isCardDir(it) }
        }
        if (nested.isNotEmpty()) {
            Logger.debug(
                TAG,
                "listCardDirsIn: no cards directly under the base, using ${nested.size} card(s) " +
                    "one level below | base=$parent, cards=${nested.map { it.path }}"
            )
        }
        return nested
    }

    private fun isCardDir(entry: FileInfo): Boolean =
        entry.name.endsWith(CARD_SUFFIX, ignoreCase = true) ||
            fal.exists("${entry.path}/$SUPERBLOCK_FILE")

    private fun findInCard(cardPath: String, saveId: String): List<String> =
        fal.listFilesUnion(cardPath)
            .filter { it.isDirectory && folderMatches(it.name, saveId) }
            .map { it.path }

    private fun memcardInfoFor(cardPath: String): MemcardInfo {
        val name = File(cardPath).name
        val children = fal.listFiles(cardPath)?.filter { it.isDirectory } ?: emptyList()
        val lastModified = children.maxOfOrNull { it.lastModified } ?: 0L
        return MemcardInfo(
            name = name,
            path = cardPath,
            gameFolderCount = children.size,
            lastModified = lastModified
        )
    }

    /**
     * Sigil reports `save_id` as a stem with usage folder-prefix: a game owns every card
     * entry starting with it (`BASLUS-20152AC04`, `BASLUS-20152SYS`, ...). A stem that
     * already carries its region prefix is taken as given; a bare serial still gets one
     * derived, because callers fall back to the raw title id when no save id was extracted.
     */
    private fun normalizeForMatch(value: String): String =
        value.replace("-", "").replace("_", "").uppercase()

    private fun resolveStem(saveId: String): String {
        val cleaned = normalizeForMatch(saveId)
        if (REGION_PREFIXED.containsMatchIn(cleaned)) return cleaned
        val bare = BARE_SERIAL.find(cleaned) ?: return cleaned
        val (code, rest) = bare.destructured
        return "${territoryPrefixFor(code)}$code$rest"
    }

    private fun territoryPrefixFor(code: String): String = when (code[2]) {
        'E' -> "BE"
        'P', 'J', 'K' -> "BI"
        else -> "BA"
    }

    /**
     * Region-agnostic form, used only as a fallback: a `save_id` extracted by an older
     * sigil can carry the wrong region prefix, and dropping it still identifies the disc.
     */
    private fun withoutRegionPrefix(stem: String): String =
        if (REGION_PREFIXED.containsMatchIn(stem)) stem.substring(2) else stem

    override fun folderMatches(folderName: String, saveId: String): Boolean {
        val folder = normalizeForMatch(folderName)
        val stem = resolveStem(saveId)
        if (folder.startsWith(stem)) return true
        return withoutRegionPrefix(folder).startsWith(withoutRegionPrefix(stem))
    }
}
