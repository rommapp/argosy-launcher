package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
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
 * mapping in [slugAliases] if needed). Adding a new file-based platform = inject the handler
 * and add a branch to [getHandler].
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

    private val slugAliases: Map<String, String> = mapOf(
        "psvita" to "vita"
    )

    private fun canonicalSlug(platformSlug: String): String =
        slugAliases[platformSlug] ?: platformSlug

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
        if (emulatorId in RETROARCH_EMULATOR_IDS) return retroArchSaveHandler
        if (config?.usesGciFormat == true) return gciSaveHandler

        val canonical = canonicalSlug(platformSlug)
        if (canonical == "switch") return switchSaveHandler
        return folderHandlers[canonical] ?: defaultSaveHandler
    }

    /**
     * Folder handler for [platformSlug], or null when the platform isn't a per-title folder
     * layout. Used by [com.nendo.argosy.data.sync.SavePathResolver] for `findSaveFolderByTitleId`,
     * `resolveBasePath`, and `constructSavePath` dispatches.
     */
    fun getFolderHandler(platformSlug: String): PlatformSaveHandler? =
        folderHandlers[canonicalSlug(platformSlug)]

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
        private val RETROARCH_EMULATOR_IDS = setOf("retroarch", "retroarch_64")
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
    private val saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "psp") {

    private val appContext = context

    companion object {
        private const val TAG = "PspFolderHandler"
    }

    override fun folderMatches(folderName: String, titleId: String): Boolean =
        folderName.startsWith(titleId, ignoreCase = true)

    override fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) return null
        val matches = findAllSaveFoldersByTitleId(basePath, titleId)
        if (matches.isEmpty()) return null
        return basePath
    }

    override fun constructSavePath(baseDir: String, titleId: String): String? = baseDir

    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val titleId = context.titleId
        val parent = fal.getTransformedFile(localPath)
        if (!parent.exists() || !parent.isDirectory) {
            Logger.debug(TAG, "prepareForUpload: parent folder missing | path=$localPath")
            return@withContext null
        }

        val matchedPaths = if (titleId != null) {
            findAllSaveFoldersByTitleId(localPath, titleId)
        } else {
            emptyList()
        }
        if (matchedPaths.isEmpty()) {
            Logger.debug(TAG, "prepareForUpload: no matches | parent=$localPath, titleId=$titleId")
            return@withContext null
        }
        val matchedFolders = matchedPaths.map { fal.getTransformedFile(it) }

        Logger.debug(TAG, "prepareForUpload: bundling ${matchedFolders.size} folder(s) | titleId=$titleId, names=${matchedFolders.map { it.name }}")

        val outputFile = File(appContext.cacheDir, "${titleId ?: parent.name}.zip")
        if (!saveArchiver.zipFolders(matchedFolders, outputFile)) {
            Logger.error(TAG, "prepareForUpload: failed to zip folders | titleId=$titleId")
            return@withContext null
        }

        PreparedSave(outputFile, isTemporary = true, matchedPaths)
    }

    override suspend fun extractDownload(
        tempFile: File,
        context: SaveContext
    ): ExtractResult = withContext(Dispatchers.IO) {
        val titleId = context.titleId
            ?: return@withContext ExtractResult(false, null, "No title ID for PSP save")

        val parentPath = context.localSavePath
            ?: resolveBasePath(context.config, null)
            ?: return@withContext ExtractResult(false, null, "No base path for PSP saves")

        val parentFolder = File(parentPath)
        parentFolder.mkdirs()

        val existing = findAllSaveFoldersByTitleId(parentPath, titleId)
        if (existing.isNotEmpty()) {
            Logger.debug(TAG, "extractDownload: clearing ${existing.size} existing folder(s) | titleId=$titleId")
            existing.forEach { fal.deleteRecursively(it) }
        }

        if (!saveArchiver.unzipToFolder(tempFile, parentFolder)) {
            Logger.error(TAG, "extractDownload: unzip failed | parent=$parentPath")
            return@withContext ExtractResult(false, null, "Failed to extract PSP save")
        }

        val restored = findAllSaveFoldersByTitleId(parentPath, titleId)
        Logger.debug(TAG, "extractDownload: complete | parent=$parentPath, restored=${restored.size}")
        ExtractResult(true, parentPath)
    }
}

/**
 * 3DS save layout: `{baseDir}/{id0}/{id1}/title/{category}/{shortTitleId}/data`. The id0/id1
 * folders are randomized per console install, so we walk the tree to find them. Includes a
 * 3DS-specific basePathOverride normalization (mounting `sdmc/Nintendo 3DS` if missing).
 */
private class N3dsFolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "3ds") {

    companion object {
        private const val TAG = "N3dsFolderHandler"
        private const val DEFAULT_CATEGORY = "00040000"
    }

    override fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "Base path does not exist | path=$basePath")
            return null
        }

        val normalizedTitleId = titleId.uppercase()
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

    override fun constructSavePath(baseDir: String, titleId: String): String? {
        val category = if (titleId.length >= 16) titleId.take(8) else DEFAULT_CATEGORY
        val shortTitleId = if (titleId.length > 8) titleId.takeLast(8) else titleId

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

    override fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? {
        if (basePathOverride != null) {
            return if (basePathOverride.endsWith("/sdmc/Nintendo 3DS") || basePathOverride.endsWith("/sdmc/Nintendo 3DS/")) {
                basePathOverride.trimEnd('/')
            } else {
                "$basePathOverride/sdmc/Nintendo 3DS"
            }
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, "3ds", null)
        return resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
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
        private const val BA_PREFIX = "BA"
        private const val CARD_SUFFIX = ".ps2"
    }

    override fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        Logger.debug(TAG, "findSaveFolderByTitleId: Searching | basePath=$basePath, serial=$titleId, normalized=${toFolderName(titleId)}")

        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "findSaveFolderByTitleId: Base path does not exist | path=$basePath")
            return null
        }

        if (isFolderCard(basePath)) {
            Logger.debug(TAG, "findSaveFolderByTitleId: basePath is a folder card, searching subfolders directly")
            return findInCard(basePath, titleId)
        }

        val folderCards = listCardDirsIn(basePath)
        Logger.debug(TAG, "findSaveFolderByTitleId: Found ${folderCards.size} memory card(s) | cards=${folderCards.map { it.name }}")

        val matches = folderCards.mapNotNull { card ->
            findInCard(card.path, titleId)?.let { card to it }
        }
        when {
            matches.isEmpty() -> {
                val exactMatch = titleId.replace("-", "")
                Logger.debug(TAG, "findSaveFolderByTitleId: No match | serial=$titleId, tried=${toFolderName(titleId)}, withoutHyphens=$exactMatch.")
                return null
            }
            matches.size == 1 -> {
                val (card, match) = matches[0]
                Logger.debug(TAG, "findSaveFolderByTitleId: Match found | card=${card.name}, path=$match")
                return match
            }
            else -> {
                Logger.warn(
                    TAG,
                    "findSaveFolderByTitleId: AMBIGUOUS -- ${matches.size} cards contain a folder " +
                        "for $titleId (${matches.map { it.first.name }}). Refusing to pick to avoid " +
                        "overwriting the wrong card. User must select a specific memcard for this " +
                        "PS2 emulator in Settings."
                )
                return null
            }
        }
    }

    override fun constructSavePath(baseDir: String, titleId: String): String? {
        if (isFolderCard(baseDir)) {
            return "$baseDir/${toFolderName(titleId)}"
        }

        val folderCards = listCardDirsIn(baseDir)
        val cardDir = when (folderCards.size) {
            0 -> "$baseDir/Shared.ps2"
            1 -> folderCards[0].path
            else -> {
                Logger.warn(
                    TAG,
                    "constructSavePath: $baseDir contains ${folderCards.size} folder memcards " +
                        "(${folderCards.map { it.name }}). Cannot pick a target -- set a preferred " +
                        "memcard for PS2 save sync, or remove the unused cards."
                )
                return null
            }
        }
        return "$cardDir/${toFolderName(titleId)}"
    }

    fun listFolderMemcards(basePath: String): List<MemcardInfo> {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) return emptyList()
        if (isFolderCard(basePath)) return listOf(memcardInfoFor(basePath))
        return listCardDirsIn(basePath)
            .map { memcardInfoFor(it.path) }
            .sortedByDescending { it.lastModified }
    }

    private fun isFolderCard(path: String): Boolean =
        path.trimEnd('/').endsWith(CARD_SUFFIX, ignoreCase = true) && fal.isDirectory(path)

    private fun listCardDirsIn(parent: String) =
        fal.listFiles(parent)?.filter {
            it.isDirectory && it.name.endsWith(CARD_SUFFIX, ignoreCase = true)
        } ?: emptyList()

    private fun findInCard(cardPath: String, titleId: String): String? {
        val folders = fal.listFiles(cardPath)?.filter { it.isDirectory } ?: emptyList()
        val match = folders.firstOrNull { matchesFolderName(it.name, titleId) }
        return match?.path
    }

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
     * Normalizes a PS2 disc serial into the on-disk folder form NetherSX2 / PCSX2 use
     * (`BA` + 4-letter region prefix + `-` + 5-digit serial, e.g. `BASLUS-21050`). Accepts
     * inputs with or without a leading BA prefix and with `-`/`_` separators stripped.
     */
    private fun toFolderName(serial: String): String {
        val cleaned = serial.replace("-", "").replace("_", "")
        val withoutBa = if (cleaned.startsWith(BA_PREFIX, ignoreCase = true)) {
            cleaned.substring(BA_PREFIX.length)
        } else cleaned
        val match = Regex("^([A-Za-z]{4})(\\d+)$").find(withoutBa)
        return if (match != null) {
            "$BA_PREFIX${match.groupValues[1].uppercase()}-${match.groupValues[2]}"
        } else {
            "$BA_PREFIX$withoutBa"
        }
    }

    private fun matchesFolderName(folderName: String, serial: String): Boolean {
        val stripped = serial.replace("-", "")
        val baSerial = if (stripped.startsWith(BA_PREFIX, ignoreCase = true)) stripped else "$BA_PREFIX$stripped"
        val folderStripped = folderName.replace("-", "")
        return folderStripped.startsWith(baSerial, ignoreCase = true)
    }
}
