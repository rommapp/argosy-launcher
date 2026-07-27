package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.storage.FileAccessLayer
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
        Ps2FolderHandler(context, fal, saveArchiver)
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
 * PSP saves are folders under `PSP/SAVEDATA/` named `<DISC_ID><SAVE_NAME>` where the 9-char
 * disc id (e.g. `ULUS10064`) is shared across all of a game's profile/system folders. A single
 * game commonly produces several siblings (`ULUS10064DATA00`, `ULUS10064SETTINGS`, ...), so the
 * "save unit" spans every prefix-matched folder under the parent.
 *
 * Mirrors the GameCube GCI handler's pattern — bundle all matches on upload, delete all matches
 * on download before extracting back into the parent.
 */
private class PspFolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "psp") {

    private val appContext = context

    companion object {
        private const val TAG = "PspFolderHandler"
    }

    override fun folderMatches(folderName: String, saveId: String): Boolean =
        folderName.startsWith(saveId, ignoreCase = true)

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) return null
        val matches = findAllSaveFoldersBySaveId(basePath, saveId)
        if (matches.isEmpty()) return null
        return basePath
    }

    override fun constructSavePath(baseDir: String, saveId: String): String? = baseDir

    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val saveId = context.saveId
        val parent = fal.getTransformedFile(localPath)
        if (!parent.exists() || !parent.isDirectory) {
            Logger.debug(TAG, "prepareForUpload: parent folder missing | path=$localPath")
            return@withContext null
        }

        val matchedPaths = if (saveId != null) {
            findAllSaveFoldersBySaveId(localPath, saveId)
        } else {
            emptyList()
        }
        if (matchedPaths.isEmpty()) {
            Logger.debug(TAG, "prepareForUpload: no matches | parent=$localPath, saveId=$saveId")
            return@withContext null
        }
        val matchedFolders = matchedPaths.map { fal.getTransformedFile(it) }

        Logger.debug(TAG, "prepareForUpload: bundling ${matchedFolders.size} folder(s) | saveId=$saveId, names=${matchedFolders.map { it.name }}")

        val outputFile = File(appContext.cacheDir, "${saveId ?: parent.name}.zip")
        if (!saveArchiver.zipFolders(matchedFolders, outputFile)) {
            Logger.error(TAG, "prepareForUpload: failed to zip folders | saveId=$saveId")
            return@withContext null
        }

        PreparedSave(outputFile, isTemporary = true, matchedPaths)
    }

    override suspend fun extractDownload(
        tempFile: File,
        context: SaveContext
    ): ExtractResult = withContext(Dispatchers.IO) {
        val saveId = context.saveId
            ?: return@withContext ExtractResult(false, null, "No title ID for PSP save")

        val parentPath = context.localSavePath
            ?: resolveBasePath(context.config, null)
            ?: return@withContext ExtractResult(false, null, "No base path for PSP saves")

        val parentFolder = File(parentPath)
        parentFolder.mkdirs()

        val existing = findAllSaveFoldersBySaveId(parentPath, saveId)
        if (existing.isNotEmpty()) {
            Logger.debug(TAG, "extractDownload: clearing ${existing.size} existing folder(s) | saveId=$saveId")
            existing.forEach { fal.deleteRecursively(it) }
        }

        if (!saveArchiver.unzipToFolder(tempFile, parentFolder)) {
            Logger.error(TAG, "extractDownload: unzip failed | parent=$parentPath")
            return@withContext ExtractResult(false, null, "Failed to extract PSP save")
        }

        val restored = findAllSaveFoldersBySaveId(parentPath, saveId)
        Logger.debug(TAG, "extractDownload: complete | parent=$parentPath, restored=${restored.size}")
        ExtractResult(true, parentPath)
    }
}

/**
 * 3DS save layout: `{baseDir}/{id0}/{id1}/title/{category}/{shortTitleId}/data`. The id0/id1
 * folders are randomized per console install, so we walk the tree to find them. Includes a
 * 3DS-specific basePathOverride normalization (mounting `sdmc/Nintendo 3DS` if missing).
 *
 * Category and short title id are the two halves of the 16-hex save id. sigil reports the
 * same split as `save_path`; splitting it here stays equivalent for every 16-hex id. See
 * docs/save-id-to-path.md.
 */
private class N3dsFolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "3ds") {

    companion object {
        private const val TAG = "N3dsFolderHandler"
        private const val DEFAULT_CATEGORY = "00040000"
        private const val SDMC_DIR = "sdmc"
        private const val SD_ROOT = "Nintendo 3DS"
        private const val TITLE_DATA_ROOT = "data"
    }

    /**
     * A 3DS save unit is the `data` directory under `title/<high8>/<low8>`, so that is what
     * an archive is rooted at and the title never appears inside it. Every archive, current
     * ones included, lands here; the destination is what identifies the save.
     */
    override val unidentifiedArchiveRoots: Set<String> = setOf(TITLE_DATA_ROOT)

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

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "Base path does not exist | path=$basePath")
            return null
        }

        val normalizedTitleId = saveId.uppercase()
        val shortTitleId = if (normalizedTitleId.length > 8) {
            normalizedTitleId.takeLast(8)
        } else {
            normalizedTitleId
        }

        Logger.debug(TAG, "Searching for save | baseDir=$basePath, fullId=$normalizedTitleId, shortId=$shortTitleId")

        var bestMatchPath: String? = null
        var bestModTime = 0L

        fal.listFiles(basePath)?.filter { it.isDirectory }?.forEach { id0Folder ->
            fal.listFiles(id0Folder.path)?.filter { it.isDirectory }?.forEach { id1Folder ->
                val titleBasePath = "${id1Folder.path}/title"
                if (!fal.exists(titleBasePath) || !fal.isDirectory(titleBasePath)) return@forEach

                fal.listFiles(titleBasePath)?.filter { it.isDirectory }?.forEach { categoryDir ->
                    val matchingFolder = fal.listFiles(categoryDir.path)?.firstOrNull {
                        it.isDirectory && it.name.equals(shortTitleId, ignoreCase = true)
                    }
                    if (matchingFolder != null) {
                        val dataPath = "${matchingFolder.path}/data"
                        if (fal.exists(dataPath) && fal.isDirectory(dataPath)) {
                            val modTime = newestFileTime(dataPath)
                            Logger.debug(TAG, "Found candidate | path=$dataPath, modTime=$modTime")
                            if (modTime > bestModTime) {
                                bestModTime = modTime
                                bestMatchPath = dataPath
                            }
                        }
                    }
                }
            }
        }

        if (bestMatchPath != null) {
            Logger.debug(TAG, "Save found | path=$bestMatchPath")
        }
        return bestMatchPath
    }

    override fun constructSavePath(baseDir: String, saveId: String): String? {
        val category = if (saveId.length >= 16) saveId.take(8) else DEFAULT_CATEGORY
        val shortTitleId = if (saveId.length > 8) saveId.takeLast(8) else saveId

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

        val savePath = "${id1Folder.path}/title/$category/$shortTitleId/data"
        Logger.debug(TAG, "Constructed save path | path=$savePath")
        return savePath
    }

    private fun newestFileTime(folderPath: String): Long {
        var newest = 0L
        fal.listFiles(folderPath)?.forEach { child ->
            if (child.isFile) {
                if (child.lastModified > newest) newest = child.lastModified
            } else if (child.isDirectory) {
                val childNewest = newestFileTime(child.path)
                if (childNewest > newest) newest = childNewest
            }
        }
        return newest
    }
}

private class Ps2FolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "ps2") {

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

        Logger.debug(TAG, "unpackArchive: archive is rooted at the card, unwrapping it | roots=$roots")
        return saveArchiver.unzipSingleFolder(tempFile, targetFolder)
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

    private fun listCardDirsIn(parent: String) =
        fal.listFilesUnion(parent).filter {
            it.isDirectory &&
                (it.name.endsWith(CARD_SUFFIX, ignoreCase = true) || fal.exists("${it.path}/$SUPERBLOCK_FILE"))
        }

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
