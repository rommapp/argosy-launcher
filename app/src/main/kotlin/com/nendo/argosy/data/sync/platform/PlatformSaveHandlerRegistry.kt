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

    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val titleId = context.titleId
        val seed = fal.getTransformedFile(localPath)
        if (!seed.exists() || !seed.isDirectory) {
            Logger.debug(TAG, "prepareForUpload: seed folder missing | path=$localPath")
            return@withContext null
        }

        val parentPath = seed.parentFile?.absolutePath
            ?: return@withContext null.also {
                Logger.debug(TAG, "prepareForUpload: no parent | path=$localPath")
            }

        val matchedPaths = if (titleId != null) {
            findAllSaveFoldersByTitleId(parentPath, titleId)
        } else {
            listOf(seed.absolutePath)
        }
        if (matchedPaths.isEmpty()) {
            Logger.debug(TAG, "prepareForUpload: no matches | parent=$parentPath, titleId=$titleId")
            return@withContext null
        }
        val matchedFolders = matchedPaths.map { fal.getTransformedFile(it) }

        Logger.debug(TAG, "prepareForUpload: bundling ${matchedFolders.size} folder(s) | titleId=$titleId, names=${matchedFolders.map { it.name }}")

        val outputFile = File(appContext.cacheDir, "${titleId ?: seed.name}.zip")
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

        val parentPath = context.localSavePath?.let { File(it).parent }
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
        ExtractResult(true, restored.firstOrNull() ?: parentPath)
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

/**
 * PS2 saves live inside .ps2 memory-card folders, with per-game subdirectories whose names start
 * with the BA-normalized serial (e.g. `BASLUS-21495` for serial `SLUS-21495`).
 */
private class Ps2FolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = "ps2") {

    companion object {
        private const val TAG = "Ps2FolderHandler"
        private const val BA_PREFIX = "BA"
    }

    override fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        Logger.debug(TAG, "findSaveFolderByTitleId: Searching | basePath=$basePath, serial=$titleId, normalized=${toFolderName(titleId)}")

        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "findSaveFolderByTitleId: Base path does not exist | path=$basePath")
            return null
        }

        val folderCards = fal.listFiles(basePath)?.filter {
            it.isDirectory && it.name.endsWith(".ps2", ignoreCase = true)
        } ?: emptyList()

        Logger.debug(TAG, "findSaveFolderByTitleId: Found ${folderCards.size} memory card(s) | cards=${folderCards.map { it.name }}")

        for (card in folderCards) {
            val folders = fal.listFiles(card.path)?.filter { it.isDirectory } ?: emptyList()
            Logger.debug(TAG, "findSaveFolderByTitleId: Scanning card=${card.name} | folders=${folders.map { it.name }}")

            val match = folders.firstOrNull { matchesFolderName(it.name, titleId) }
            if (match != null) {
                Logger.debug(TAG, "findSaveFolderByTitleId: Match found | card=${card.name}, folder=${match.name}, path=${match.path}")
                return match.path
            }
        }

        val exactMatch = titleId.replace("-", "")
        Logger.debug(TAG, "findSaveFolderByTitleId: No match | serial=$titleId, tried=${toFolderName(titleId)}, withoutHyphens=$exactMatch.")
        return null
    }

    override fun constructSavePath(baseDir: String, titleId: String): String? {
        val folderCards = fal.listFiles(baseDir)?.filter {
            it.isDirectory && it.name.endsWith(".ps2", ignoreCase = true)
        } ?: emptyList()

        val cardDir = when (folderCards.size) {
            0 -> "$baseDir/Shared.ps2"
            1 -> folderCards[0].path
            else -> {
                Logger.warn(
                    TAG,
                    "constructSavePath: $baseDir contains ${folderCards.size} folder memcards " +
                        "(${folderCards.map { it.name }}). Cannot pick a target — set a preferred " +
                        "memcard for PS2 save sync, or remove the unused cards."
                )
                return null
            }
        }
        return "$cardDir/${toFolderName(titleId)}"
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
