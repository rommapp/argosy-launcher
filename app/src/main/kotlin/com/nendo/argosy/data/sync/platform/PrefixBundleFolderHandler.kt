package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A save id is the on-disk save location, so it may carry path separators where a platform
 * nests it (3DS reports `00040000/00033500`). Naming a temp archive after one would resolve
 * into a directory that does not exist, so the separators collapse for filename use only.
 */
internal fun String.asArchiveName(): String = replace('/', '_')

/**
 * Folder layout where one game owns SEVERAL sibling directories that share the save id as a
 * prefix, rather than a single directory named for it. PSP writes `ULUS10064DATA00` beside
 * `ULUS10064SETTINGS`; aPS3e writes `BCUS99086GAMEDATA` beside `BCUS99086-AUTOSAVE`.
 *
 * The consequence runs through every method here: the save unit is the PARENT directory plus a
 * prefix predicate, not one folder. Discovery answers the parent, an upload bundles every
 * sibling that matches, and a restore clears the matched siblings before unpacking back into the
 * parent. A handler that acted on the resolved path alone would upload one folder out of several
 * and leave the rest of the save behind.
 */
open class PrefixBundleFolderHandler(
    context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver,
    platformSlug: String,
    private val tag: String
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug, tag) {

    private val appContext = context

    override fun folderMatches(folderName: String, saveId: String): Boolean =
        folderName.startsWith(saveId, ignoreCase = true)

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) return null
        if (findAllSaveFoldersBySaveId(basePath, saveId).isEmpty()) return null
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
            Logger.debug(tag, "prepareForUpload: parent folder missing | path=$localPath")
            return@withContext null
        }

        val matchedPaths = if (saveId != null) {
            findAllSaveFoldersBySaveId(localPath, saveId)
        } else {
            emptyList()
        }
        if (matchedPaths.isEmpty()) {
            Logger.debug(tag, "prepareForUpload: no matches | parent=$localPath, saveId=$saveId")
            return@withContext null
        }
        val matchedFolders = matchedPaths.map { fal.getTransformedFile(it) }

        Logger.debug(tag, "prepareForUpload: bundling ${matchedFolders.size} folder(s) | saveId=$saveId, names=${matchedFolders.map { it.name }}")

        val outputFile = File(appContext.cacheDir, "${saveId?.asArchiveName() ?: parent.name}.zip")
        if (!saveArchiver.zipFolders(matchedFolders, outputFile)) {
            Logger.error(tag, "prepareForUpload: failed to zip folders | saveId=$saveId")
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

    override suspend fun extractDownload(
        tempFile: File,
        context: SaveContext
    ): ExtractResult = withContext(Dispatchers.IO) {
        val saveId = context.saveId
            ?: return@withContext ExtractResult(false, null, "No title ID for $platformSlug save")

        val parentPath = context.localSavePath
            ?: resolveBasePath(context.config, null)
            ?: return@withContext ExtractResult(false, null, "No base path for $platformSlug saves")

        val parentFolder = File(parentPath)
        parentFolder.mkdirs()

        val existing = findAllSaveFoldersBySaveId(parentPath, saveId)
        if (existing.isNotEmpty()) {
            Logger.debug(tag, "extractDownload: clearing ${existing.size} existing folder(s) | saveId=$saveId")
            existing.forEach { fal.deleteRecursively(it) }
        }

        if (!saveArchiver.unzipToFolder(tempFile, parentFolder)) {
            Logger.error(tag, "extractDownload: unzip failed | parent=$parentPath")
            return@withContext ExtractResult(false, null, "Failed to extract $platformSlug save")
        }

        val restored = findAllSaveFoldersBySaveId(parentPath, saveId)
        Logger.debug(tag, "extractDownload: complete | parent=$parentPath, restored=${restored.size}")
        ExtractResult(true, parentPath)
    }
}
