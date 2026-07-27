package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Generic folder-bundle save handler used for platforms whose saves are a per-title directory
 * tree zipped on upload and unzipped on download. Replaces the near-verbatim PSP / Vita / Wii /
 * Wii U / 3DS / PS2 handler classes that diverged only in slug and folder-match predicate.
 *
 * Override hooks let platforms with quirks (3DS id0/id1, PS2 BA-prefixed memory-card folders)
 * supply their own logic without spawning a new handler file.
 */
open class FolderSaveHandler(
    private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver,
    val platformSlug: String,
    private val tag: String = "FolderSaveHandler[$platformSlug]"
) : PlatformSaveHandler {

    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val saveFolder = fal.getTransformedFile(localPath)
        if (!saveFolder.exists() || !saveFolder.isDirectory) {
            Logger.debug(tag, "prepareForUpload: Save folder does not exist | path=$localPath")
            return@withContext null
        }

        val outputFile = File(this@FolderSaveHandler.context.cacheDir, "${saveFolder.name}.zip")
        if (!saveArchiver.zipFolder(saveFolder, outputFile)) {
            Logger.error(tag, "prepareForUpload: Failed to zip folder | source=$localPath")
            return@withContext null
        }

        PreparedSave(outputFile, isTemporary = true, listOf(localPath))
    }

    override suspend fun extractDownload(
        tempFile: File,
        context: SaveContext
    ): ExtractResult = withContext(Dispatchers.IO) {
        val targetPath = context.localSavePath ?: run {
            val basePath = resolveBasePath(context.config, null)
                ?: return@withContext ExtractResult(false, null, "No base path for $platformSlug saves")
            val saveId = context.saveId
                ?: return@withContext ExtractResult(false, null, "No save ID for $platformSlug save")
            constructSavePath(basePath, saveId)
                ?: return@withContext ExtractResult(false, null, "Cannot construct $platformSlug save path")
        }

        val saveId = context.saveId
        if (saveId != null) {
            val roots = saveArchiver.peekRootEntryNames(tempFile)
            val tier = roots.firstNotNullOfOrNull { matchArchiveRoot(it, saveId) }
            if (tier == null) {
                Logger.error(
                    tag,
                    "extractDownload: archive does not hold this save | saveId=$saveId, " +
                        "roots=$roots, target=$targetPath. Refusing to unpack it."
                )
                return@withContext ExtractResult(
                    false,
                    null,
                    "Archive contents do not match $saveId (top level: ${roots.joinToString().ifEmpty { "no directories" }})"
                )
            }
            if (tier != ArchiveRootMatch.EXACT) {
                Logger.debug(tag, "extractDownload: archive matched on $tier | saveId=$saveId, roots=$roots")
            }
        }

        val targetFolder = File(targetPath)
        targetFolder.mkdirs()
        ensureContainerPrepared(targetFolder)

        saveId?.let { pruneNonCanonicalSiblings(targetFolder, it) }

        val archiveRoot = saveArchiver.peekRootFolderName(tempFile)
        val success = try {
            saveArchiver.unzipSingleFolder(tempFile, targetFolder)
        } catch (e: com.nendo.argosy.data.sync.CorruptZipException) {
            Logger.error(tag, "extractDownload: Server zip is corrupt | target=$targetPath, archiveRoot=$archiveRoot, tempFile=${tempFile.name}, ${e.message}")
            return@withContext ExtractResult(false, null, "Corrupt server zip: ${e.message}", corruptZip = true)
        }
        if (!success) {
            Logger.error(tag, "extractDownload: Unzip failed | target=$targetPath, archiveRoot=$archiveRoot, tempFile=${tempFile.name}, tempSize=${tempFile.length()}")
            return@withContext ExtractResult(false, null, "Failed to extract $platformSlug save")
        }

        Logger.debug(tag, "extractDownload: Complete | target=$targetPath")
        ExtractResult(true, targetPath)
    }

    /**
     * Default folder lookup uses case-insensitive equality. Platforms with prefix or normalized-
     * folder matching override [folderMatches].
     */
    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(tag, "Base path does not exist | path=$basePath")
            return null
        }

        val match = fal.listFiles(basePath)?.firstOrNull { folder ->
            folder.isDirectory && folderMatches(folder.name, saveId)
        }

        if (match != null) {
            Logger.debug(tag, "Save found | path=${match.path}")
            return match.path
        }

        Logger.debug(tag, "No save found | basePath=$basePath, saveId=$saveId")
        return null
    }

    override fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) return emptyList()
        return fal.listFiles(basePath).orEmpty()
            .filter { it.isDirectory && folderMatches(it.name, saveId) }
            .map { it.path }
    }

    override fun constructSavePath(baseDir: String, saveId: String): String? = "$baseDir/$saveId"

    /** True when [folderName] is one of [saveId]'s own per-game entries per this platform's match rule. */
    fun isEntryForSaveId(folderName: String, saveId: String): Boolean = folderMatches(folderName, saveId)

    /** How strongly an archive's top-level entry corresponds to the save it claims to be. */
    enum class ArchiveRootMatch {
        EXACT,
        PREFIX,
        CONTAINS,

        /**
         * A root that names no title, because the platform's save unit is a fixed directory
         * below the title rather than the title folder itself. It cannot confirm or deny the
         * save, so it is placed on the strength of the resolved destination alone.
         */
        UNIDENTIFIED
    }

    /**
     * Sigil reports whether a save id addresses its folder exactly or as a prefix, so an
     * archive is accepted on the same terms, weakest tier last. Anything that matches on
     * none of them is not this save and must not be unpacked over it.
     */
    fun matchArchiveRoot(rootName: String, saveId: String): ArchiveRootMatch? {
        val root = normalizeSaveId(rootName)
        val id = normalizeSaveId(saveId)
        if (root.isEmpty() || id.isEmpty()) return null
        return when {
            root == id -> ArchiveRootMatch.EXACT
            folderMatches(rootName, saveId) || root.startsWith(id) -> ArchiveRootMatch.PREFIX
            root.contains(id) -> ArchiveRootMatch.CONTAINS
            rootName.trimEnd('/') in unidentifiedArchiveRoots -> ArchiveRootMatch.UNIDENTIFIED
            else -> null
        }
    }

    /**
     * Fixed directory names this platform's saves are rooted at instead of a title. Accepting
     * one means trusting the resolved destination rather than the archive, so it stays an
     * explicit per-platform opt-in.
     */
    protected open val unidentifiedArchiveRoots: Set<String> = emptySet()

    private fun normalizeSaveId(value: String): String =
        value.replace("-", "").replace("_", "").uppercase()


    override fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? {
        if (basePathOverride != null) return normalizeBasePath(basePathOverride)

        val resolvedPaths = SavePathRegistry.resolvePath(config, platformSlug, null)
        return resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
    }

    /**
     * Lets a platform accept a base the user pointed at a parent or a child of the root it
     * actually scans from, so picking the emulator's folder, its `sdmc`, or somewhere deeper
     * all land on the same place. Default is to take the path as given.
     */
    protected open fun normalizeBasePath(path: String): String = path

    /**
     * Per-platform folder-name match predicate. Default is case-insensitive equality. PSP
     * overrides to use prefix matching; PS2 uses normalized BA-prefix matching.
     */
    protected open fun folderMatches(folderName: String, saveId: String): Boolean =
        folderName.equals(saveId, ignoreCase = true)

    /** Hook after the restore target dir is created; PS2 marks a fresh folder card as formatted. */
    protected open fun ensureContainerPrepared(targetFolder: File) {}

    private fun pruneNonCanonicalSiblings(canonicalTarget: File, saveId: String) {
        val parent = canonicalTarget.parentFile ?: return
        fal.listFiles(parent.path).orEmpty()
            .filter { it.isDirectory && it.path != canonicalTarget.path }
            .filter { folderMatches(it.name, saveId) && !isCanonicalFolderPath(it.path, saveId) }
            .forEach {
                Logger.warn(tag, "extractDownload: removing non-canonical sibling save folder | path=${it.path}, saveId=$saveId")
                File(it.path).deleteRecursively()
            }
    }
}
